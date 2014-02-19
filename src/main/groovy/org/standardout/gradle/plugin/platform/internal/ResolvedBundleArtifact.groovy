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

package org.standardout.gradle.plugin.platform.internal

import java.util.jar.JarFile

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.osgi.framework.Constants
import org.osgi.framework.Version


class ResolvedBundleArtifact implements BundleArtifact {
	
	private final File file
	File getFile() { file }
	
	final String classifier
	
	final String extension
	
	final String group
	
	final String name
	
	private final String version
	String getVersion() { version }

	private final boolean source
	boolean isSource() { source }
	
	private final String bundleName
	String getBundleName() { bundleName }
	
	private final String symbolicName
	String getSymbolicName() { symbolicName }
	
	/**
	 * Should the bundle be wrapped?
	 */
	private final boolean wrap
	boolean isWrap() { wrap }
	
	private final String noWrapReason
	String getNoWrapReason() { noWrapReason }
	
	private final BundleDependency dependency
	BundleDependency getDependency() { dependency }
	
	final String unifiedName
	
	private final String id
	String getId() { id }
	
	private final String modifiedVersion
	String getModifiedVersion() { modifiedVersion }
	
	private final String targetFileName
	String getTargetFileName() { targetFileName }
	
	/**
	 * Create a bundle artifact from a resolved artifact.
	 */
	ResolvedBundleArtifact(ResolvedArtifact artifact, Project project) {
		// extract information from artifact
		this.file = artifact.file
		this.classifier = artifact.classifier
		this.extension = artifact.extension
		this.group = artifact.moduleVersion.id.group
		this.name = artifact.moduleVersion.id.name
		this.version = artifact.moduleVersion.id.version
		
		// derived information
		
		// is this a source bundle
		source = artifact.classifier == 'sources'

		// bundle and symbolic name
		def bundleName = group + '.' + name
		def symbolicName = bundleName
				
		// reason why a bundle is not wrapped
		if (source || extension != 'jar') {
			// never wrap
			wrap = false
			noWrapReason = 'artifact type not supported'
			if (source) {
				symbolicName += '.source'
				bundleName += ' Sources'
			}
		}
		else {
			// check if already a bundle
			JarFile jar = new JarFile(file)
			String symName = jar.manifest.mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME)
			
			if (symName) {
				// assume it's already a bundle
				wrap = false
				noWrapReason = 'jar already constains OSGi manifest entries'

				// determine bundle names
				symbolicName = symName
				bundleName = jar.manifest.mainAttributes.getValue(Constants.BUNDLE_NAME)
			}
			else {
				// not a bundle yet
				wrap = true
				noWrapReason = ''
			}
		}
		
		// the unified name (that is equal for corresponding source and normal jars)
		// it also is the key for the bundle dependency (if any)
		def unifiedName = "$group:$name:$version"
		// the qualified id (including classifier, unique)
		if (classifier) {
			id = unifiedName + ":$classifier"
		}
		else {
			id = unifiedName
		}
		this.unifiedName = unifiedName
		
		// resolve bundle dependency
		dependency = project.platform.bundleIndex[id]
		
		// an eventually modified version
		def modifiedVersion = version
		if (wrap) {
			// if the bundle is wrapped, create a modified version to mark this
			Version v
			try {
				v = Version.parseVersion(version)
			} catch (NumberFormatException e) {
				// try again with version stripped of anything but dots and digits
				String strippedVersion = version.replaceAll(/[^0-9\.]/, '')
				v = Version.parseVersion(strippedVersion)
			}
			def qualifier = v.qualifier
			if (qualifier) {
				qualifier += 'autowrapped'
			}
			else {
				qualifier = 'autowrapped'
			}
			Version mv = new Version(v.major, v.minor, v.micro, qualifier)
			modifiedVersion = mv.toString()
		}
		this.modifiedVersion = modifiedVersion
		
		// name of the target file to create
		def targetFileName = "${group}.${name}-${modifiedVersion}"
		if (classifier) {
			targetFileName += "-$classifier"
		}
		targetFileName += ".$extension"
		this.targetFileName = targetFileName
		
		this.bundleName = bundleName
		this.symbolicName = symbolicName
	}

}
