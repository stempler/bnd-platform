
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

import org.osgi.framework.Version
import org.osgi.framework.Constants

import aQute.bnd.main.bnd
import aQute.lib.osgi.Analyzer


public class PlatformPlugin implements Plugin<Project> {

	public static final String TASK_BUNDLES = 'bundles'
	public static final String CONF_PLATFORM = 'platform'
	//public static final String TASK_CLEAN_BUNDLES = "cleanBundles"
	
	private Project project

	@Override
	public void apply(Project project) {
		this.project = project

		// register extension
		project.extensions.create('platform', PlatformPluginExtension)
		
		// create configuration
		project.configurations.create CONF_PLATFORM
		
		project.afterEvaluate {
			if (project.platform.fetchSources) {
				// add source as additional dependency for each registered dependency
				project.dependencies {
					project.configurations.getByName(CONF_PLATFORM).allDependencies.each {
						platform group: it.group, name: it.name, version: it.version, classifier: 'sources'
					}
				}
			}
			
			// bundles directory default
			if (project.platform.bundlesDir == null) {
				project.platform.bundlesDir = new File(project.buildDir, 'bundles')
			}
		}

		// create bundles task
		Task bundlesTask = project.task(TASK_BUNDLES)
		
		// depend on the artifacts (rather than a task)
		//XXX not sure if this really has any effect
		bundlesTask.dependsOn(project.configurations.getByName(CONF_PLATFORM).allArtifacts.buildDependencies)
		
		// define bundles task
		bundlesTask.doFirst(new Action(){
			@Override
			public void execute(Object target) {
				assert project

				Configuration config = project.getConfigurations().getByName(CONF_PLATFORM)
				ResolvedConfiguration resolved = config.resolvedConfiguration
				
				if (project.logger.infoEnabled) {
					// output some debug information on the configuration
					configInfo(config, project.logger.&info)
					resolvedConfigInfo(resolved, project.logger.&info)
				}

				// create artifact info representations
				// qualified name is mapped to artifact infos
				def artifacts = [:]
				resolved.resolvedArtifacts.each {
					def info = collectArtifactInfo(it)
					artifacts[info.qname] = info
				}
				
				File targetDir = project.platform.bundlesDir
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
					def outputFileName = new File(targetDir, it.targetname)
						
					if(it.source) {
						// source jar
						project.logger.info "-> Copying source jar ${it.qname}..."
						project.ant.copy ( file : it.file , tofile : outputFileName )
						//TODO update to include Eclipse source information
					} else if (it.wrap) {
						// normal jar
						project.logger.info "-> Wrapping jar ${it.qname} as OSGi bundle using bnd..."
						bnd.doWrap(null, it.file, outputFileName as File, jarFiles as File[], 0, [
							(Analyzer.BUNDLE_VERSION): it.modversion,
							(Analyzer.BUNDLE_NAME): it.bundlename,
							(Analyzer.BUNDLE_SYMBOLICNAME): it.symbolicname
						])
					}
					else {
						project.logger.info "-> Copying artifact $it.qname; ${it.reason}..."
						project.ant.copy ( file : it.file , tofile : outputFileName )
					}
				}
			}
		})
	}
	
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
	
	protected void resolvedConfigInfo(ResolvedConfiguration config, def log) {
		log('Resolved configuration')
		
		log('  Artifacts:')
		config.resolvedArtifacts.each {
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
