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
import org.osgi.framework.Version;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.FileBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.MergeConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfigImpl;

import com.sun.media.sound.JARSoundbankReader;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;

class BundleHelper {
	
	public static final MANIFEST_PATH = 'META-INF/MANIFEST.MF'
	
	/**
	 * Bundle an artifact, also create the source bundle if applicable.
	 */
	static void bundle(Project project, BundleArtifact art, File targetDir) {
		if (art.source) {
			// ignore - source bundles must be handled together with their parents
			return
		}
		
		if(art.sourceBundle != null) {
			// wrap sources as bundle
			BundleArtifact sourceArt = art.sourceBundle
			def sourceJar = new File(targetDir, sourceArt.targetFileName)
			
			project.logger.info "-> Creating source bundle for ${sourceArt.id}..."
			
			// calculated properties
			def sourceBundleDef = "${art.symbolicName};version=\"${art.modifiedVersion}\";roots:=\".\"" as String
			
			BndHelper.wrap(sourceArt.file, null, sourceJar, [
				(Analyzer.BUNDLE_NAME): sourceArt.bundleName,
				(Analyzer.BUNDLE_VERSION): sourceArt.modifiedVersion,
				(Analyzer.BUNDLE_SYMBOLICNAME): sourceArt.symbolicName,
				(Analyzer.PRIVATE_PACKAGE): '*', // sources as private packages
				(Analyzer.EXPORT_PACKAGE): '', // no exports
				'Eclipse-SourceBundle': sourceBundleDef
			])
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
			
			// properties that are fixed (if they should be changed it should happen in BundleArtifact)
			properties.putAll(
				(Analyzer.BUNDLE_VERSION): art.modifiedVersion,
				(Analyzer.BUNDLE_NAME): art.bundleName,
				(Analyzer.BUNDLE_SYMBOLICNAME): art.symbolicName
			)
			
			BndHelper.wrap(art.file, null, outputFile, properties)
		}
		else {
			project.logger.info "-> Copying artifact $art.id; ${art.noWrapReason}..."
			project.ant.copy ( file : art.file , tofile : outputFile )
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
			if (bundle.sourceBundle != null) {
				sourceJars << bundle.sourceBundle.file
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
			config << merge.bundleConfig // merge config
			
			FileBundleArtifact artifact = new FileBundleArtifact(tmpJar, project, config)
			
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
			bundle(project, artifact, targetDir)
			
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
		jar.remove(MANIFEST_PATH)
		
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
