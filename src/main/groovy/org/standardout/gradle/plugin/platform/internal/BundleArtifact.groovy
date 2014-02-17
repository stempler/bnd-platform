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

class BundleArtifact {
	
	final File file
	
	final String classifier
	
	final String extension
	
	final String group
	
	final String name
	
	final String version

	/**
	 * If the bundle is a source bundle.	
	 */
	boolean source
	
	String bundleName
	
	String symbolicName
	
	/**
	 * Should the bundle be wrapped?
	 */
	boolean wrap
	
	String noWrapReason = ''
	
	final BundleDependency dependency
	
	final String unifiedName
	
	final String id
	
	String modifiedVersion
	
	String targetFileName
	
	/**
	 * Create a bundle artifact from a resolved artifact.
	 */
	BundleArtifact(ResolvedArtifact artifact, Project project) {
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
		bundleName = group + '.' + name
		symbolicName = bundleName
				
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
			}
		}
		
		// the unified name (that is equal for corresponding source and normal jars)
		// it also is the key for the bundle dependency (if any)
		unifiedName = "$group:$name:$version"
		// the qualified id (including classifier, unique)
		if (classifier) {
			id = unifiedName + ":$classifier"
		}
		else {
			id = unifiedName
		}
		
		// resolve bundle dependency
		dependency = project.platform.bundleIndex[id]
		
		// an eventually modified version
		modifiedVersion = version
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
		
		// name of the target file to create
		targetFileName = "${group}.${name}-${modifiedVersion}"
		if (classifier) {
			targetFileName += "-$classifier"
		}
		targetFileName += ".$extension"
	}

}
