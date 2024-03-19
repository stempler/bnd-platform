/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.standardout.gradle.plugin.platform

import org.standardout.gradle.plugin.platform.internal.util.VersionFile

import java.util.jar.*

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.osgi.framework.Version
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.BundlesAction;
import org.standardout.gradle.plugin.platform.internal.DefaultFeature
import org.standardout.gradle.plugin.platform.internal.Feature
import org.standardout.gradle.plugin.platform.internal.ResolvedBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.osdetect.SwtPlatform
import org.standardout.gradle.plugin.platform.internal.util.FeatureUtil;
import org.standardout.gradle.plugin.platform.internal.util.bnd.BndHelper

import groovy.io.FileType
import groovy.json.JsonOutput

/**
 * OSGi platform plugin for Gradle.
 * 
 * @author Robert Gregor
 * @author Simon Templer
 */
public class PlatformPlugin implements Plugin<Project> {

	public static final String TASK_BUNDLES = 'bundles'
	public static final String CONF_PLATFORM = 'bndplatform'
	public static final String CONF_AUX = 'platformaux'

	private Project project

	private File bundlesDir
	private File categoryFile
	private File featuresDir

	@Override
	public void apply(Project project) {
		this.project = project

		// use BasePlugin to derive the clean task from it
		project.getPluginManager().apply(BasePlugin.class);

		configureEnvironment(project)

		// ensure download-task plugin is applied
		project.apply(plugin: 'de.undercouch.download')

		// register extension
		project.extensions.create('platform', PlatformPluginExtension, project)

		// initialize file/directory members
		// names are fixed because of update site conventions
		bundlesDir = new File(project.buildDir, 'plugins')
		categoryFile = new File(project.buildDir, 'category.xml')
		featuresDir = new File(project.buildDir, 'features')

		// create configuration
		project.configurations.maybeCreate CONF_PLATFORM
		project.configurations.maybeCreate CONF_AUX

		project.afterEvaluate {
			// feature version default
			if (project.platform.featureVersion == null) {
				if (project.version) {
					try {
						project.platform.featureVersion = Version.parseVersion(project.version).toString()
					} catch (e) {
						// ignore
					}
				}
			}
			if (project.platform.featureVersion == null) {
				project.platform.featureVersion = '1.0.0'
			}

			if (project.platform.downloadsDir == null) {
				// use gradleUserHomeDir to store the minimal p2 eclipse distribution for generating p2 update sites
				// See https://docs.gradle.org/current/dsl/org.gradle.api.invocation.Gradle.html#org.gradle.api.invocation.Gradle:gradleUserHomeDir
				project.platform.downloadsDir = new File(project.gradle.gradleUserHomeDir, 'bnd-platform')
			}
			if (!project.platform.downloadsDir.exists()) {
				project.platform.downloadsDir.mkdirs()
			}
		}

		// create bundles task
		Task bundlesTask = project.task(TASK_BUNDLES) {
			group 'bnd-platform'
			description 'Create specified bundles and write them to build/plugins'
		}

		// depend on the artifacts (rather than a task)
		//XXX not sure if this really has any effect
		bundlesTask.dependsOn(project.configurations.getByName(CONF_PLATFORM).allArtifacts.buildDependencies)

		// define bundles task
		bundlesTask.doFirst(new BundlesAction(project, bundlesDir))

		/*
		 * Generate a default feature definition for the platform feature.
		 */
		Task platformFeatureTask = project.task('platformFeature', dependsOn: bundlesTask).doFirst {
			// create platform feature.xml
			generatePlatformFeature();
		}

		/*
		 * Create JARs for all features. 
		 */
		Task bundleFeaturesTask = project.task('bundleFeatures', dependsOn: bundlesTask).doFirst {
			featuresDir.mkdirs()

			if(project.platform.generatePlatformFeature) {
				generatePlatformFeature();
			}

			project.platform.features.values().each { Feature feature ->
				File featureJar = new File(featuresDir, "${feature.id}_${feature.version}.jar")

				use(FeatureUtil) { feature.createJar(featureJar) }
			}
		}

		/*
		 * Generate category.xml.
		 */
		Task generateCategoryTask = project.task('generateCategory', dependsOn: bundleFeaturesTask).doFirst {
			categoryFile.parentFile.mkdirs()

			categoryFile.withWriter('UTF-8'){ w ->
				def xml = new groovy.xml.MarkupBuilder(w)
				xml.setDoubleQuotes(true)
				xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')

				xml.site{
					// all features
					project.platform.features.values().each { Feature f ->
						feature(url: "features/${f.id}_${f.version}.jar",
						id: f.id,
						version: f.version) {
							// associate the feature to the category
							category(name: project.platform.categoryId)
						}
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
				eclipseHome = checkDownloadedEclipse(project.platform.downloadsDir)
				if (!eclipseHome) {
					downloadAndExtractEclipse(project.platform.downloadsDir)
					eclipseHome = checkDownloadedEclipse(project.platform.downloadsDir)
				}
			}

			if (eclipseHome) {
				project.platform.eclipseHome = eclipseHome as File
			}
			else {
				throw new GradleException('no eclipseHome found.')
			}
		}

		/*
		 * Build a p2 repository with all the bundles
		 */
		Task updateSiteTask = project.task('updateSite', dependsOn: [
			bundleFeaturesTask,
			generateCategoryTask,
			checkEclipseTask
		]) {
			group 'bnd-platform'
			description 'Create a p2 repository from the bundles and write it to build/updatesite'
			doFirst {
				project.platform.updateSiteDir.mkdirs()
	
				assert project.platform.eclipseHome
				def eclipseHome = project.platform.eclipseHome.absolutePath

				def javaHome = project.platform.javaHome?.absolutePath
				def javaBin
				if (javaHome) {
					javaBin = "${javaHome}/bin/java"
				}
				else {
					javaBin = "java"
				}

				// find launcher jar
				def launcherFiles = project.ant.fileScanner {
					fileset(dir: eclipseHome) { include(name: 'plugins/org.eclipse.equinox.launcher_*.jar') }
				}
				def launcherJar = launcherFiles.iterator().next()
				assert launcherJar
	
				project.logger.info "Using Java at $javaHome and Eclipse at $eclipseHome for p2 repository generation."

				def appendToSite = project.platform.appendUpdateSite
				if (appendToSite) {
					project.logger.info "Appending to update site is enabled."
				}
	
				/*
				 * Documentation on Publisher:
				 * http://help.eclipse.org/juno/index.jsp?topic=/org.eclipse.platform.doc.isv/guide/p2_publisher.html
				 * http://wiki.eclipse.org/Equinox/p2/Publisher
				 */
	
				// launch Publisher for Features and Bundles
				def repoDirUri = URLDecoder.decode(project.platform.updateSiteDir.toURI().toString(), 'UTF-8')
				def categoryFileUri = URLDecoder.decode(categoryFile.toURI().toString(), 'UTF-8')
				project.exec {
					def args = ["${javaBin}", '-jar', launcherJar,
							'-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher',
							'-metadataRepository', repoDirUri,
							'-artifactRepository', repoDirUri,
							'-source', project.buildDir,
							'-configs', 'ANY', '-publishArtifacts', '-compress']
					if (appendToSite) {
						args.add('-append')
					}
					commandLine = args
				}
	
				// launch Publisher for category / site.xml
				project.exec {
					def args = ["${javaBin}", '-jar', launcherJar,
							'-application', 'org.eclipse.equinox.p2.publisher.CategoryPublisher',
							'-metadataRepository', repoDirUri,
							'-categoryDefinition', categoryFileUri,
							'-compress']
					if (appendToSite) {
						args.add('-append')
					}
					commandLine = args
				}
	
				project.logger.info 'Built p2 repository.'

				def createFeatureVersionFiles = project.platform.createFeatureVersionFiles
				if (createFeatureVersionFiles) {
					VersionFile.createFeatureVersionFiles(project.platform.updateSiteDir)
				}
			}
		}

		/*
		 * Archive update site.
		 */
		Task siteArchiveTask = project.task('updateSiteZip', dependsOn: [updateSiteTask]) {
			group 'bnd-platform'
			description 'Create a ZIP archive from the p2 repository and write it to build/updatesite.zip'
			doFirst {
				project.ant.zip(destfile: project.platform.updateSiteZipFile) {
					fileset(dir: project.platform.updateSiteDir) { include(name: '**') }
				}
			}
		}

		/*
		 * Task that creates a Json file with a mapping of bundle name to  
		 */
		Task artifactMapTask = project.task('artifactMap', dependsOn: bundlesTask).doFirst {
			Map<String, BundleArtifact> artifacts = project.platform.artifacts

			def report = [:]
			artifacts.values().each { BundleArtifact artifact ->
				if (!artifact.isSource() && artifact instanceof ResolvedBundleArtifact) {
					// artifact that has a Maven dependency as it's direct source

					def info = report[artifact.symbolicName]
					if (!info) {
						info = [:]
						info.group = artifact.group
						info.name = artifact.name
						report[artifact.symbolicName] = info
					}

					if (!info.versions) {
						info.versions = [:]
					}
					info.versions[artifact.modifiedVersion] = artifact.version
				}
			}

			File reportFile = new File(project.buildDir, 'bundleArtifactMap.json')
			reportFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(report))
		}

		/**
		 * Creates a potentialOptionalImports.txt file in the build directory of potential optional imports.
		 * 
		 * Unfortunately optional dependencies specified in a pom.xml file get lost in Gradle,
		 * therefore optionalImport instructions for the bnd configuration are used quite frequently.
		 * This task should help to generate the optional import statements, 
		 * but please be careful since this task simply creates the optionalImport statements for each and every imported package.
		 * This means that YOU have to check yourself if the dependency is really optional.
		 * 
		 * See https://github.com/stempler/bnd-platform/issues/19#issuecomment-253797523
		 */
		Task potentialOptionalImports = project.task('potentialOptionalImports', dependsOn: bundlesTask) {
			group 'bnd-platform'
			description 'Creates a potentialOptionalImports.txt file of imported packages of all generated bundles with the optionalImport instruction'

			doFirst {
				def reportFile = new File(project.buildDir, 'potentialOptionalImports.txt').newWriter()
				reportFile << '''Unfortunately optional dependencies specified in a pom.xml file get lost in Gradle,
therefore optionalImport instructions for the bnd configuration are used quite frequently.
This task should help to generate the optional import statements, 
but please be careful since this task simply creates the optionalImport statements for each and every imported package.
This means that YOU have to check yourself if the dependency is really optional.

See https://github.com/stempler/bnd-platform/issues/19#issuecomment-253797523

'''
				def bundlesWithoutImports = []

				bundlesDir.eachFileRecurse (FileType.FILES) { bundle ->
					def symbolicNameAndPackageImports = BndHelper.getSymbolicNameAndPackageImports(bundle);

					if(!symbolicNameAndPackageImports.second.empty) {
						reportFile << "\n# ${symbolicNameAndPackageImports.first} \n\n"
						symbolicNameAndPackageImports.second.each { reportFile << "optionalImport '${it}'\n" }
					} else if(!symbolicNameAndPackageImports.first.endsWith('source')){
						bundlesWithoutImports << symbolicNameAndPackageImports.first
					}
				}

				if(!bundlesWithoutImports.empty) {
					reportFile << "\n----------------------------------------------------"
					reportFile << "\nBundles without imported packages \n\n"
					bundlesWithoutImports.each { reportFile << "   ${it}\n" }
				}

				reportFile.close()
			}
		}

	}

	/**
	 * Guess current environment and store information in project.ext.
	 */
	def configureEnvironment(Project project) {
		project.with {
			SwtPlatform swt = SwtPlatform.getRunning();
			if (!ext.properties.containsKey('osgiOS')) {
				ext.osgiOS = swt.getOs()
			}
			if (!ext.properties.containsKey('osgiWS')) {
				ext.osgiWS = swt.getWs()
			}
			if (!ext.properties.containsKey('osgiArch')) {
				ext.osgiArch = swt.getArch()
			}
		}
	}

	private void downloadAndExtractEclipse(File downloadsDir) {
		// Download artifact
		def artifactDownloadUrl = project.platform.eclipseMirror
		def filename = artifactDownloadUrl.substring(artifactDownloadUrl.lastIndexOf('/') + 1)
		def artifactZipPath = new File(downloadsDir, filename)
		def artifactZipPathPart = new File(downloadsDir, filename + '.part')
		if (!artifactZipPath.exists()) {
			project.download.run {
				src artifactDownloadUrl
				dest artifactZipPathPart
				overwrite true
			}
			artifactZipPathPart.renameTo(artifactZipPath)
		}

		// Unzip artifact
		println('Copying ' + artifactZipPath + ' ...')
		def artifactInstallPath = downloadsDir
		project.ant.untar(src: artifactZipPath, dest: artifactInstallPath, compression: 'gzip')
	}

	private File checkDownloadedEclipse(File downloadsDir) {
		for (String subDir in [
			'eclipse',
			'Eclipse.app/Contents/Eclipse'
		]) {
			File downloadedEclipse = new File(downloadsDir, subDir)
			if (downloadedEclipse.exists()) {
				return downloadedEclipse
			}
		}

		return null;
	}

	/**
	 * Generate a default feature definition for the platform feature.
	 */
	private void generatePlatformFeature() {
		Feature feature = new DefaultFeature(
			id: project.platform.featureId,
			label: project.platform.featureName,
			version: project.platform.featureVersion,
			providerName: project.platform.featureProvider,
			bundles: project.platform.artifacts.values().toList(),
			includedFeatures: project.platform.features.values().toList(),
			project: project
			)

		project.platform.features[feature.id] = feature
	}

}
