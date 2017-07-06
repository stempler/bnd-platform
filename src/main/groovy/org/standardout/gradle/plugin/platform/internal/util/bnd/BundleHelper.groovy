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

package org.standardout.gradle.plugin.platform.internal.util.bnd

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.osgi.framework.Version;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.DependencyArtifact;
import org.standardout.gradle.plugin.platform.internal.FileBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.MergeBundleArtifact
import org.standardout.gradle.plugin.platform.internal.ResolvedBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.MergeConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfigImpl;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;

class BundleHelper {
	
	public static final MANIFEST_PATH = 'META-INF/MANIFEST.MF'
	
	/**
	 * Bundle an artifact, also create the source bundle if applicable.
	 */
	static void bundle(Project project, BundleArtifact art, File targetDir, List<BundleArtifact> mergedArtifacts = null) {
		if (art.source) {
			// ignore - source bundles must be handled together with their parents
			return
		}
		
		boolean removeSignatures = project.platform.removeSignaturesFromWrappedBundles
		
		if(art.sourceBundle != null) {
			// wrap sources as bundle
			BundleArtifact sourceArt = art.sourceBundle
			def sourceJar = new File(targetDir, sourceArt.targetFileName)
			
			project.logger.info "-> Creating source bundle for ${sourceArt.id}..."
			
			// calculated properties
			def sourceBundleDef = "${art.symbolicName};version=\"${art.modifiedVersion}\";roots:=\".\"" as String
			
			boolean written = BndHelper.wrap(sourceArt.file, null, sourceJar, [
				(Analyzer.BUNDLE_NAME): sourceArt.bundleName,
				(Analyzer.BUNDLE_VERSION): sourceArt.modifiedVersion,
				(Analyzer.BUNDLE_SYMBOLICNAME): sourceArt.symbolicName,
				(Analyzer.REMOVEHEADERS): '*-Package',	// no 'Export-Package', 'Import-Package', 'Private-Package' for source bundle
				'Eclipse-SourceBundle': sourceBundleDef
			], removeSignatures)
			if (!written) {
				project.logger.warn "Skipping creating source bundle for empty or corrupted JAR: $sourceArt.file"
				// remove from artifact map (so it is not included in the update site feature)
				project.platform.artifacts.remove(sourceArt.id)
			}
		}

		def outputFile = new File(targetDir, art.targetFileName)
		if (art.wrap) {
			// normal jar
			project.logger.info "-> Wrapping jar ${art.id} as OSGi bundle using bnd..."
			
			Map<String, String> properties = [:]
			
			// bnd config
			if (art.bndConfig) {
				// use instructions from bnd config
				BndConfig bndConfig = art.bndConfig
				properties.putAll(bndConfig.properties)
			}
			
			// analyze existing symbolic name -> find any attributes, e.g. singleton
			Attrs symbolicNameAttrs = new Attrs()
			if (properties[Analyzer.BUNDLE_SYMBOLICNAME]) {
				Parameters pars = OSGiHeader.parseHeader(properties[Analyzer.BUNDLE_SYMBOLICNAME])
				symbolicNameAttrs = pars.findResult {
					String symbolicName, Attrs attrs ->
					attrs
				}
			}
			// combine with artifact symbolic name
			Parameters symbolicNamePars = new Parameters()
			symbolicNamePars[art.symbolicName] = symbolicNameAttrs
			
			// properties that are fixed (if they should be changed it should happen in BundleArtifact)
			properties.putAll(
				(Analyzer.BUNDLE_VERSION): art.modifiedVersion,
				(Analyzer.BUNDLE_NAME): art.bundleName,
				(Analyzer.BUNDLE_SYMBOLICNAME): symbolicNamePars.toString()
			)
			
			// add BndPlatform specific manifest headers
			if (mergedArtifacts) {
				addBndPlatformHeaders(project, properties, mergedArtifacts)
			}
			else {
				addBndPlatformHeaders(project, properties, [art])
			}
			
			boolean written = BndHelper.wrap(art.file, null, outputFile, properties, removeSignatures)
			if (!written) {
				throw new IllegalStateException("Empty or corrupted JAR cannot be wrapped: $art.file")
			}
		}
		else {
			project.logger.info "-> Copying artifact $art.id; ${art.noWrapReason}..."
			project.ant.copy ( file : art.file , tofile : outputFile )
		}
	}
	
	private static void addBndPlatformHeaders(Project project, Map<String, String> headers, List<BundleArtifact> artifacts) {
		if (project.platform.addBndPlatformManifestHeaders && artifacts) {
			if (artifacts.size() == 1) {
				// single artifact
				if (artifacts[0] instanceof ResolvedBundleArtifact) {
					ResolvedArtifact dep = artifacts[0].artifact
					if (dep) {
						headers.put('BndPlatform-ArtifactGroup', dep.moduleVersion.id.group)
						headers.put('BndPlatform-ArtifactName', dep.moduleVersion.id.name)
						headers.put('BndPlatform-ArtifactVersion', dep.moduleVersion.id.version)
						if (dep.classifier) {
							headers.put('BndPlatform-ArtifactClassifier', dep.classifier)
						}
					}
				}
			}
			else {
				// multiple artifacts
				def resolvedArtifacts = artifacts.findAll {
					(it instanceof ResolvedBundleArtifact) && it.artifact
				}
				
				if (resolvedArtifacts.size() != artifacts.size()) {
					project.logger.warn('Unable to create complete BndPlatform manifest headers for merged bundle')
				}
				
				if (resolvedArtifacts) {
					// sort by symbolic name to have a reproducable order
					resolvedArtifacts = resolvedArtifacts.sort(false) {
						it.symbolicName
					}
					
					headers.put('BndPlatform-MergedArtifacts', resolvedArtifacts.size())
					
					resolvedArtifacts.eachWithIndex { ResolvedBundleArtifact art, index ->
						ResolvedArtifact dep = art.artifact
						headers.put("BndPlatform-MergedArtifact-${index + 1}-Group", dep.moduleVersion.id.group)
						headers.put("BndPlatform-MergedArtifact-${index + 1}-Name", dep.moduleVersion.id.name)
						headers.put("BndPlatform-MergedArtifact-${index + 1}-Version", dep.moduleVersion.id.version)
						if (dep.classifier) {
							headers.put("BndPlatform-MergedArtifact-${index + 1}-Classifier", dep.classifier)
						}
					}
				}
			}
		}
	}
	
	static void merge(Project project, MergeConfig merge, List<BundleArtifact> bundles, File targetDir) {
		if (bundles.empty) {
			return
		}
		
		// collect jars and source jars
		def jars = []
		def sourceJars = [] 
		bundles.each {
			BundleArtifact bundle ->
			jars << bundle.file
			project.platform.artifacts.remove(bundle.id)
			if (bundle.sourceBundle != null) {
				sourceJars << bundle.sourceBundle.file
				project.platform.artifacts.remove(bundle.sourceBundle.id)
			}
		}
		
		project.logger.warn 'Merging jars ' + jars.collect{ it.name }.join(',') + ' - the jars will not be available as separate bundles'
		
		// merge jars
		File tmpJar = File.createTempFile('merge', '.jar')
		File sourceJar = File.createTempFile('merge', '-sources.jar')
		try {
			mergeJars(project, jars, tmpJar, merge.properties)
			
			// make sure to include default configuration for merged Jar
			StoredConfig config = new StoredConfigImpl()
			config << project.platform.configurations.defaultConfig // default config
			
			// collect dependencies for artifact
			Set<ResolvedArtifact> directDeps = new HashSet<ResolvedArtifact>()
			Set<ResolvedDependency> representedDeps = new HashSet<ResolvedDependency>()
			// collect merged artifact dependencies
			bundles.each {
				if (it instanceof DependencyArtifact) {
					directDeps.addAll(it.getDirectDependencies(project))
					representedDeps.addAll(it.representedDependencies.toList())
				}
			}
			
			// import defaults config
			if (project.platform.determineImportVersions) {
				// configuration
				config << project.platform.configurations.defaultImports(directDeps)
			}
			
			config << merge.bundleConfig // merge config
			config << project.platform.configurations.overrideConfig // override config
			// enable adding qualifier by default (must be enabled as default for file bundle artifacts is false)
			config << new StoredConfigImpl({ if (addQualifier == null) addQualifier = true })
			
			FileBundleArtifact artifact = new MergeBundleArtifact(tmpJar, project, config, merge.id,
				directDeps, representedDeps)
			
			// merge sources & associate to bundle artifact
			if (sourceJars) {
				mergeJars(project, sourceJars, sourceJar, [
					failOnDuplicate: false,
					collectServices: true
				])
				FileBundleArtifact sourceArtifact = new FileBundleArtifact(artifact, sourceJar)
				
				// register artifact so it is included in the platform feature
				project.platform.artifacts[sourceArtifact.id] = sourceArtifact
			}
			
			// create bundle (and source bundle)
			bundle(project, artifact, targetDir, bundles)
			
			// register artifact so it is included in the platform feature
			project.platform.artifacts[artifact.id] = artifact
		}
		finally {
			tmpJar.delete()
			sourceJar.delete()
		}
	}
	
	static void mergeJars(Project project, List<File> jarFiles, File targetFile, Map<String, Object> properties) {
		assert !jarFiles.empty : 'Cannot merge no jars'
		
		if (jarFiles.size() == 1) {
			project.ant.copy ( file : jarFiles[0] , tofile : targetFile )
			return
		}
		
		Jar jar = null
		def jars = jarFiles.each {
			if (jar == null) {
				jar = new Jar(it)
			}
			else {
				Jar sub = new Jar(it)
				addAll(jar, sub, properties)
			}
		}
		
		// remove the manifest (we don't want to retain any information)
		if (jar.exists(MANIFEST_PATH)) {
			jar.remove(MANIFEST_PATH)
		}
		
		jar.write(targetFile)
	}
	
	private static void addAll(Jar parent, Jar sub, Map<String, Object> properties) {
		for (String name : sub.getResources().keySet()) {
			if (MANIFEST_PATH == name)
				continue;

			mergeResource(parent, sub, name, properties)
		}
	}
	
	private static void mergeResource(Jar parent, Jar sub, String name, Map<String, Object> properties) {
		if (properties.collectServices && parent.getResource(name) != null && name.startsWith('META-INF/services/')) {
			// append resource
			Resource res = parent.getResource(name)
			parent.putResource(name, combineServices(res, sub.getResource(name)))
		}
		else {
			boolean duplicate = parent.putResource(name, sub.getResource(name), true);
			if (duplicate) {
				if (properties.failOnDuplicate) {
					throw new IllegalStateException("Duplicate resource $name when merging jars, but failOnDuplicate is enabled")
				}
			}
		}
	}
	
	/**
	 * Combine all service classes in a META-INF/services file.
	 */
	private static Resource combineServices(Resource first, Resource second) {
		def lines = []
		lines.addAll(first.openInputStream().withStream {
			InputStream input ->
			input.readLines()
		})
		lines.addAll(second.openInputStream().withStream {
			InputStream input ->
			input.readLines()
		})
		lines = lines.findAll() // all non-empty lines
		String content = lines.join('\n')
		return new ByteArrayResource(
			content.bytes, 
			Math.max(first.lastModified(), second.lastModified()))
	}

}
