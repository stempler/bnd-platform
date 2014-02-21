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
	
	final BndConfig bndConfig
	
	final String id
	
	final String modifiedVersion
	
	final String targetFileName
	
	BundleArtifact sourceBundle
	
	private final boolean source
	
	/**
	 * Create a bundle artifact represented by a Jar.
	 */
	FileBundleArtifact(File artifactFile, Project project, StoredConfig config = null) {
		this.file = artifactFile
		this.id = artifactFile as String
		this.source = false
		
		if (config == null) {
			// resolve file dependency configuration
			config = project.platform.configurations.getConfiguration(file)
		}
		bndConfig = config?.evaluate(project, file)
		
		assert bndConfig : "No bnd configuration for file dependency: $file"
		assert bndConfig.version : "No version specified for file dependency: $file"
		version = modifiedVersion = bndConfig.version
		
		assert bndConfig.symbolicName : "No symbolic name specified for file dependency: $file"
		symbolicName = bndConfig.symbolicName
		if (bndConfig.bundleName) {
			bundleName = bndConfig.bundleName
		}
		else {
			bundleName = symbolicName
		}
		
		this.targetFileName = symbolicName + '-' + modifiedVersion + '.jar'
	}
	
	/**
	 * Create a source bundle artifact from a Jar.
	 * 
	 * @param bundle the bundle the source bundle belongs to
	 * @param sourceBundleFile the source bundle file
	 */
	FileBundleArtifact(BundleArtifact bundle, File sourceBundleFile) {
		this.file = sourceBundleFile
		this.id = sourceBundleFile as String
		this.source = true
		
		bndConfig = null
		
		version = modifiedVersion = bundle.version
		
		symbolicName = bundle.symbolicName + '.source'
		bundleName = bundle.bundleName + ' Sources'
		
		this.targetFileName = symbolicName + '-' + modifiedVersion + '.jar'
		
		// associate to bundle
		bundle.sourceBundle = this
	}
	
	@Override
	public boolean isSource() {
		source
	}
	@Override
	public boolean isWrap() {
		!source
	}
	@Override
	public String getNoWrapReason() {
		''
	}

}
