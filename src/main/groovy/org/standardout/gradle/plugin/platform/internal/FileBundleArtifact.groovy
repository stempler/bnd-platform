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
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.util.bnd.JarInfo;


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
	
	private final boolean wrap
	
	/**
	 * Create a bundle artifact represented by a Jar.
	 */
	FileBundleArtifact(File artifactFile, Project project, StoredConfig config = null) {
		this.file = artifactFile
		this.id = artifactFile as String
		this.source = false // don't mark as source bundle so it is processed as usual
		
		boolean source = false
		
		JarInfo jarInfo = null
		boolean includeDefaultConfig = true
		jarInfo = new JarInfo(file)
		if (jarInfo.symbolicName) {
			includeDefaultConfig = false
			
			// detect source bundle
			if (jarInfo.instructions['Eclipse-SourceBundle']) {
				source = true
			}
		}
		
		if (config == null) {
			// resolve file dependency configuration
			config = project.platform.configurations.getConfiguration(file, includeDefaultConfig)
		}
		
		// only wrap if there is a configuration (retain existing bundles)
		// and the bundle is not a source bundle already
		wrap = !source && !config.bndClosures.isEmpty()
		
		bndConfig = config?.evaluate(project, file, jarInfo?.instructions)
		
		if (bndConfig && bndConfig.version && bndConfig.symbolicName) {
			// bnd configuration present
			version = modifiedVersion = bndConfig.version
			
			symbolicName = JarInfo.extractSymbolicName(bndConfig.symbolicName) // stripped symbolic name
			if (bndConfig.bundleName) {
				bundleName = bndConfig.bundleName
			}
			else {
				bundleName = symbolicName
			}
		}
		else if (jarInfo && jarInfo.symbolicName && jarInfo.version) {
			// only jar info present (and jar is bundle)
			version = modifiedVersion = jarInfo.version
			
			symbolicName = jarInfo.symbolicName
			if (jarInfo.bundleName) {
				bundleName = jarInfo.bundleName
			}
			else {
				bundleName = symbolicName
			}
		}
		else {
			throw new IllegalStateException('A file dependency must either already be a bundle or a bnd configuration including version and symbolicName must be specified: ' + file)
		}
		
		this.targetFileName = symbolicName + '_' + modifiedVersion + '.jar'
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
		this.wrap = false // wrapping is done implicitly
		
		bndConfig = null
		
		version = modifiedVersion = bundle.version
		
		symbolicName = bundle.symbolicName + '.source'
		bundleName = bundle.bundleName + ' Sources'
		
		this.targetFileName = symbolicName + '_' + modifiedVersion + '.jar'
		
		// associate to bundle
		bundle.sourceBundle = this
	}
	
	@Override
	public boolean isSource() {
		source
	}
	@Override
	public boolean isWrap() {
		wrap
	}
	@Override
	public String getNoWrapReason() {
		'Jar is already a bundle'
	}

}
