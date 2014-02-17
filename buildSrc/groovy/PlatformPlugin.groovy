
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.jar.*

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedArtifact

import org.eclipse.core.runtime.internal.adaptor.EclipseEnvironmentInfo

import org.osgi.framework.Version
import org.osgi.framework.Constants

import aQute.bnd.main.bnd
import aQute.lib.osgi.Analyzer


public class PlatformPlugin implements Plugin<Project> {

	public static final String TASK_BUNDLES = 'bundles'
	public static final String CONF_PLATFORM = 'platform'
	
	private Project project
	
	private File bundlesDir
	private File featureFile
	private File categoryFile
	private File featuresDir
	private File downloadsDir
	
	@Override
	public void apply(Project project) {
		this.project = project
		
		configureEnvironment(project)
		
		// ensure download-task plugin is applied
		project.apply(plugin: 'download-task')

		// register extension
		project.extensions.create('platform', PlatformPluginExtension)
		
		// initialize file/directory members
		// names are fixed because of update site conventions
		bundlesDir = new File(project.buildDir, 'plugins')
		featureFile = new File(project.buildDir, 'feature.xml')
		categoryFile = new File(project.buildDir, 'category.xml')
		featuresDir = new File(project.buildDir, 'features')
		downloadsDir = new File(project.buildDir, 'eclipse-downloads')
		
		// create configuration
		project.configurations.create CONF_PLATFORM
		
		project.afterEvaluate {
			// update site directory default
			if (project.platform.updateSiteDir == null) {
				project.platform.updateSiteDir = new File(project.buildDir, 'updatesite')
			}
		}

		// create bundles task
		Task bundlesTask = project.task(TASK_BUNDLES)
		
		// depend on the artifacts (rather than a task)
		//XXX not sure if this really has any effect
		bundlesTask.dependsOn(project.configurations.getByName(CONF_PLATFORM).allArtifacts.buildDependencies)
		
		// define bundles task
		bundlesTask.doFirst {
			assert project

			Configuration config = project.getConfigurations().getByName(CONF_PLATFORM)
			ResolvedConfiguration resolved = config.resolvedConfiguration
			
			if (project.logger.infoEnabled) {
				// output some debug information on the configuration
				configInfo(config, project.logger.&info)
				resolvedConfigInfo(resolved.resolvedArtifacts, project.logger.&info)
			}

			// create artifact info representations
			// qualified name is mapped to artifact infos
			// artifact info stored in project.ext (plugin instance doesn't seem to be shared between all task executions)
			project.ext.platform_artifacts = [:]
			def artifacts = project.ext.platform_artifacts 
			resolved.resolvedArtifacts.each {
				def info = collectArtifactInfo(it)
				artifacts[info.qname] = info
			}
			
			// source artifacts
			if (project.platform.fetchSources) {
				def sourceArtifacts = DependencyHelper.resolveSourceArtifacts(config, project.configurations)
				sourceArtifacts.each {
					def info = collectArtifactInfo(it)
					artifacts[info.qname] = info
					
					// check if associated bundle is found
					if (artifacts[info.uname]) {
						def bundle = artifacts[info.uname]
						// change info to resemble original bundle
						info.bundlename = bundle.bundlename + ' Sources'
						info.symbolicname = bundle.symbolicname + '.source'
					}
				}
				
				// output info
				resolvedConfigInfo('Source artifacts', sourceArtifacts, project.logger.&info)
			}
			
			File targetDir = bundlesDir
			targetDir.mkdirs()

			if(!artifacts) {
				project.logger.warn "${getClass().getSimpleName()}: no dependency artifacts could be found"
				return
			} else {
				project.logger.info "Processing ${artifacts.size()} dependency artifacts:"
			}
			
			def jarFiles = artifacts.values().collect {
				it.file
			}

			/*
			 * Currently referring to bnd version 1.50.0
			 * https://github.com/bndtools/bnd/blob/74cb2aabc743e5d3c22cc40905fe4cd6867176da/biz.aQute.bnd/src/aQute/bnd/main/bnd.java 
			 */
			def bnd = new bnd()

			artifacts.values().each {
				def outputFile = new File(targetDir, it.targetname)
				it.outfile = outputFile
					
				if(it.source) {
					// source jar
					
					// find corresponding bundle
					def bundle = artifacts[it.uname]
					if (bundle) {
						// wrap as source bundle
						def sourceBundleDef = "${bundle.symbolicname};version=\"${bundle.modversion}\";roots:=\".\"" as String
						
						project.logger.info "-> Creating source bundle ${it.qname}..."
						bnd.doWrap(null, it.file, outputFile, jarFiles as File[], 0, [
							(Analyzer.BUNDLE_VERSION): it.modversion,
							(Analyzer.BUNDLE_NAME): it.bundlename,
							(Analyzer.BUNDLE_SYMBOLICNAME): it.symbolicname,
							(Analyzer.PRIVATE_PACKAGE): '*', // sources as private packages
							(Analyzer.EXPORT_PACKAGE): '', // no exports
							'Eclipse-SourceBundle': sourceBundleDef
						])
					}
					else {
						project.logger.warn "Ignoring source jar $it.qname as no associated jar was found"
					}
				} else if (it.wrap) {
					// normal jar
					project.logger.info "-> Wrapping jar ${it.qname} as OSGi bundle using bnd..."
					bnd.doWrap(null, it.file, outputFile, jarFiles as File[], 0, [
						(Analyzer.BUNDLE_VERSION): it.modversion,
						(Analyzer.BUNDLE_NAME): it.bundlename,
						(Analyzer.BUNDLE_SYMBOLICNAME): it.symbolicname
					])
				}
				else {
					project.logger.info "-> Copying artifact $it.qname; ${it.reason}..."
					project.ant.copy ( file : it.file , tofile : outputFile )
				}
			}
		}
		
		/*
		 * Clean task.
		 */
		project.task('clean').doLast {
			featureFile.delete()
			categoryFile.delete()
			featuresDir.deleteDir()
			bundlesDir.deleteDir()
			// don't delete download in default clean
//			downloadsDir.deleteDir()
			project.platform.updateSiteDir.deleteDir()
		}
		
		/*
		 * Generate a feature.xml from the target file.
		 */
		Task generateFeatureTask = project.task('generateFeature', dependsOn: bundlesTask).doFirst {
			featureFile.parentFile.mkdirs()
			def artifacts = project.ext.platform_artifacts
			
			featureFile.withWriter('UTF-8'){
				w ->
				def xml = new groovy.xml.MarkupBuilder(w)
				xml.setDoubleQuotes(true)
				xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')
				
				xml.feature(
					id: project.platform.featureId, 
					label: project.platform.featureName,
					version: project.platform.featureVersion
				) {
					for (def artifact : artifacts.values()) {
						// define each plug-in
						plugin(
							id: artifact.symbolicname,
							'download-size': 0,
							'install-size': 0,
							version: artifact.modversion,
							unpack: false)
					}
				}
			}
			
			project.logger.info 'Generated feature.xml.'
		}
		
		/*
		 * Create Feature JAR.
		 */
		Task bundleFeatureTask = project.task('bundleFeature', dependsOn: generateFeatureTask).doFirst {
			featuresDir.mkdirs()
			// create feature jar
			def target = new File(featuresDir,
				"${project.platform.featureId}_${project.platform.featureVersion}.jar")
			project.ant.zip(destfile: target) {
				fileset(dir: project.buildDir) {
					include(name: 'feature.xml')
				}
			}
			
			project.logger.info 'Packaged feature.'
		}
		
		/*
		 * Generate category.xml.
		 */
		Task generateCategoryTask = project.task('generateCategory').doFirst {
			categoryFile.parentFile.mkdirs()
			
			categoryFile.withWriter('UTF-8'){
				w ->
				def xml = new groovy.xml.MarkupBuilder(w)
				xml.setDoubleQuotes(true)
				xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')
				
				xml.site{
					// the feature
					feature(url: "features/${project.platform.featureId}_${project.platform.featureVersion}.jar",
							id: project.platform.featureId,
							version: project.platform.featureVersion) {
						// associate the feature to the category
						category(name: project.platform.categoryId)
					}
					// define the category
					'category-def'(name: project.platform.categoryId, label: project.platform.categoryName)
				}
			}
			
			project.logger.info 'Generated category.xml.'
		}
		
		/*
		 * Task that checks if Eclipse is there / Eclipse home is specified.
		 */
		Task checkEclipseTask = project.task('checkEclipse').doFirst {
			// path to Eclipse provided in extension
			if (project.platform.eclipseHome != null) {
				return
			}
			
			// from system property
			def eclipseHome = System.properties['ECLIPSE_HOME']
			
			if (!eclipseHome) {
				File downloadedEclipse = new File(downloadsDir, 'eclipse')
				if (downloadedEclipse.exists()) {
					// downloaded Eclipse already exists
					eclipseHome = downloadedEclipse
				}
				else {
					// download and extract Eclipse
					def artifacts = project.platform.eclipseMirror
					if (artifacts.containsKey(project.ext.osgiOS) && artifacts[project.ext.osgiOS].containsKey(project.ext.osgiWS) &&
						artifacts[project.ext.osgiOS][project.ext.osgiWS].containsKey(project.ext.osgiArch)) {
						
						// Download artifact
						String artifactDownloadUrl = artifacts[project.ext.osgiOS][project.ext.osgiWS][project.ext.osgiArch]
						def filename = artifactDownloadUrl.substring(artifactDownloadUrl.lastIndexOf('/') + 1)
						def artifactZipPath = new File(downloadsDir, filename)
						def artifactZipPathPart = new File(downloadsDir, filename + '.part')
						if (!artifactZipPath.exists()) {
							project.download {
								src artifactDownloadUrl
								dest artifactZipPathPart
								overwrite true
							}
							artifactZipPathPart.renameTo(artifactZipPath)
						}
				
						// Unzip artifact
						println('Copying ' + name + ' ...')
						def artifactInstallPath = downloadsDir
						if (artifactZipPath.name.endsWith('.zip')) {
							project.ant.unzip(src: artifactZipPath, dest: artifactInstallPath)
						} else {
							project.ant.untar(src: artifactZipPath, dest: artifactInstallPath, compression: 'gzip')
						}
						if (downloadedEclipse.exists()) {
							eclipseHome = downloadedEclipse
						}
						else {
							project.logger.error 'Could not find "eclipse" directory in extracted artifact'
						}
					}
					else {
						project.logger.error 'Unable to download eclipse artifact'
					}
					
				}
			}
			
			if (eclipseHome) {
				project.platform.eclipseHome = eclipseHome as File
			}
		}
		
		/*
		 * Build a p2 repository with all the bundles
		 */
		Task updateSiteTask = project.task('updateSite', dependsOn: [bundleFeatureTask, generateCategoryTask, checkEclipseTask]).doFirst {
			project.platform.updateSiteDir.mkdirs()
			
			assert project.platform.eclipseHome
			def eclipseHome = project.platform.eclipseHome.absolutePath
			
			// find launcher jar
			def launcherFiles = project.ant.fileScanner {
				fileset(dir: eclipseHome) {
					include(name: 'plugins/org.eclipse.equinox.launcher_*.jar')
				}
			}
			def launcherJar = launcherFiles.iterator().next()
			assert launcherJar
			
			project.logger.info "Using Eclipse at $eclipseHome for p2 repository generation."
			
			/*
			 * Documentation on Publisher:
			 * http://help.eclipse.org/juno/index.jsp?topic=/org.eclipse.platform.doc.isv/guide/p2_publisher.html
			 * http://wiki.eclipse.org/Equinox/p2/Publisher
			 */
			
			// launch Publisher for Features and Bundles
			def repoDirUri = URLDecoder.decode(project.platform.updateSiteDir.toURI().toString(), 'UTF-8')
			def categoryFileUri = URLDecoder.decode(categoryFile.toURI().toString(), 'UTF-8')
			project.exec {
				commandLine 'java', '-jar', launcherJar,
					'-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
					'-metadataRepository', repoDirUri,
					'-artifactRepository', repoDirUri,
					'-source', project.buildDir,
					'-configs', 'ANY', '-publishArtifacts', '-compress'
			}
			
			// launch Publisher for category / site.xml
			project.exec {
				commandLine 'java', '-jar', launcherJar,
					'-application', 'org.eclipse.equinox.p2.publisher.CategoryPublisher',
					'-metadataRepository', repoDirUri,
					'-categoryDefinition', categoryFileUri,
					'-compress'
			}
			
			project.logger.info 'Built p2 repository.'
		}
	}
	
	/**
	 * Collect artifact/bundle information based on a given resolved artifact.
	 */
	protected def collectArtifactInfo(ResolvedArtifact artifact) {
		// extract information from artifact
		def file = artifact.file
		def classifier = artifact.classifier
		def extension = artifact.extension
		def group = artifact.moduleVersion.id.group
		def name = artifact.moduleVersion.id.name
		def version = artifact.moduleVersion.id.version
		
		// derived information
		
		// is this a source bundle
		def source = artifact.classifier == 'sources'

		// bundle and symbolic name
		def bundlename = group + '.' + name
		def symbolicname = bundlename
				
		// should the bundle be wrapped?
		def wrap
		// reason why a bundle is not wrapped
		def reason = ''
		if (source || extension != 'jar') {
			// never wrap
			wrap = false
			reason = 'artifact type not supported'
			if (source) {
				symbolicname += '.source'
				bundlename += ' Sources'
			}
		}
		else {
			JarFile jar = new JarFile(file)
			String symName = jar.manifest.mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME)
			
			if (symName) {
				// assume it's already a bundle
				wrap = false
				reason = 'jar already constains OSGi manifest entries'

				// determine bundle names
				symbolicname = symName
				bundlename = jar.manifest.mainAttributes.getValue(Constants.BUNDLE_NAME)
			}
			else {
				// not a bundle yet
				wrap = true
			}
		}
		
		// the unified name (that is equal for corresponding source and normal jars)
		def uname = "$group:$name:$version"
		// the qualified name (including classifier, unique)
		def qname
		if (classifier) {
			qname = uname + ":$classifier"
		}
		else {
			qname = uname
		}
		
		// an eventually modified version
		def modversion = version
		if (wrap) {
			// if the bundle is wrapped, create a modified version to mark this
			Version v = Version.parseVersion(version)
			def qualifier = v.qualifier
			if (qualifier) {
				qualifier += 'autowrapped'
			}
			else {
				qualifier = 'autowrapped'
			}
			Version mv = new Version(v.major, v.minor, v.micro, qualifier)
			modversion = mv.toString()
		}
		
		// name of the target file to create
		def targetname = "${group}.${name}-${modversion}"
		if (classifier) {
			targetname += "-$classifier"
		}
		targetname += ".$extension"
		
		[
			qname: qname,
			uname: uname,
			source: source,
			file: file,
			classifier: classifier,
			extension: extension,
			group: group,
			name: name,
			version: version,
			modversion: modversion,
			wrap: wrap,
			reason: reason,
			targetname: targetname,
			bundlename: bundlename,
			symbolicname: symbolicname
		]
	}
	
	/**
	 * Guess current environment and store information in project.ext.
	 */
	def configureEnvironment(Project project) {
		project.with {
			def eei = EclipseEnvironmentInfo.getDefault()
			if (!ext.properties.containsKey('osgiOS')) {
				ext.osgiOS = eei.getOS()
			}
			if (!ext.properties.containsKey('osgiWS')) {
				ext.osgiWS = eei.getWS()
			}
			if (!ext.properties.containsKey('osgiArch')) {
				ext.osgiArch = eei.getOSArch()
			}
		}
	}
	
	// methods logging information for easier debugging
	
	protected void configInfo(Configuration config, def log) {
		log("Configuration: $config.name")
		
		log('  Dependencies:')
		config.allDependencies.each {
			log("    - $it.group $it.name $it.version")
//			it.properties.each {
//				k, v ->
//				log("    $k: $v")
//			}
		}
		
		log('  Files:')
		config.collect().each {
			log("    - ${it}")
		}
	}
	
	protected void resolvedConfigInfo(String title = 'Resolved configuration', Iterable<ResolvedArtifact> resolvedArtifacts, def log) {
		log(title)
		
		log('  Artifacts:')
		resolvedArtifacts.each {
			log("    ${it.name}:")
			log("      File: $it.file")
			log("      Classifier: $it.classifier")
			log("      Extension: $it.extension")
			log("      Group: $it.moduleVersion.id.group")
			log("      Name: $it.moduleVersion.id.name")
			log("      Version: $it.moduleVersion.id.version")
//			it.properties.each {
//				k, v ->
//				log("      $k: $v")
//			}
		}
	}
}
