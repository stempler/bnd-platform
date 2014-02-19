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


/**
 * Bundle represented by a single file w/o dependency information.
 */
class FileBundleArtifact implements BundleArtifact {
	
	final File file
	
	final String version

	final String bundleName
	
	final String symbolicName
	
	final BundleDependency dependency
	
	final String id
	
	final String modifiedVersion
	
	final String targetFileName
	
	/**
	 * Create a bundle artifact from a resolved artifact.
	 */
	FileBundleArtifact(File artifactFile, Project project) {
		this.file = artifactFile
		this.targetFileName = artifactFile.name
		this.id = artifactFile as String
		
		// resolve bundle dependency
		dependency = project.platform.bundleIndex[id]
		
		assert dependency : "No bnd configuration for file dependency: $file"
		assert dependency.bndConfig.version : "No version specified for file dependency: $file"
		version = modifiedVersion = dependency.bndConfig.version
		
		assert dependency.bndConfig.symbolicName : "No symbolic name specified for file dependency: $file"
		symbolicName = dependency.bndConfig.symbolicName
		if (dependency.bndConfig.bundleName) {
			bundleName = dependency.bndConfig.bundleName
		}
		else {
			bundleName = symbolicName
		}
	}
	
	@Override
	public boolean isSource() {
		false
	}
	@Override
	public boolean isWrap() {
		true
	}
	@Override
	public String getNoWrapReason() {
		''
	}

}
