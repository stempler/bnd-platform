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

import org.gradle.api.Project;

/**
 * Bundle configuration index.
 */
class Configurations {
	
	private final Project project
	
	private final Map<File, StoredConfig> fileConfigurations = [:]
	
	private final Map<String, Map<String, Map<String, StoredConfig>>> dependencyConfigurations = [:]
	
	private final List<MergeConfig> merges = []
	
	Configurations(Project project) {
		this.project = project
	}
	
	void addMerge(MergeConfig merge) {
		merges << merge
	}
	
	/**
	 * Create bundles for the given artifacts.
	 */
	void createBundles(Iterable<BundleArtifact> artifacts, File targetDir) {
		List<List<BundleArtifact>> mergeBuckets = new ArrayList<List<BundleArtifact>>(merges.size())
		List<BundleArtifact> remaining = []
		
		artifacts.each {
			BundleArtifact art ->
			if (!art.isSource()) { // ignore source bundles
				boolean added = false
				def matchAgainst = new LaxPropertyDecorator(art)
				// check for each merge if the bundle is part of the merge
				merges.eachWithIndex {
					MergeConfig merge, int index ->
					// call match closure(s) with artifact
					// any match will result in the bundle being merged
					if (merge.matchClosures.any { it(matchAgainst) }) {
						List<BundleArtifact> bucket = mergeBuckets[index]
						if (bucket == null) {
							bucket = []
							mergeBuckets[index] = bucket
						}
						bucket << art
						added = true
					}
				}
				
				if (!added) {
					remaining << art
				}
			}
		}
		
		// collect all jars for classpath
//		def jarFiles = artifacts.collect {
//			it.file
//		}
		
		// merged bundles
		mergeBuckets.eachWithIndex {
			def bundles, int index ->
			if (bundles) {
				BundleHelper.merge(project, merges[index], bundles, targetDir)
			}
			else {
				project.logger.warn 'No bundles match merge'
			}
		}
		
		// other bundles
		remaining.each {
			BundleArtifact art ->
			BundleHelper.bundle(project, art, targetDir)
		}
	}
	
	/**
	 * Add configuration for a file based dependency.
	 */
	void putConfiguration(File file, StoredConfig config) {
		assert file
		
		StoredConfig current = fileConfigurations[file] 
		if (current != null) {
			// append configuration
			current << config
			project.logger.warn "Multiple bnd configurations for file $file, later definitions may override previous"
		}
		else {
			fileConfigurations[file] = config
		}
	}
	
	/**
	 * Get configuration for a file based dependency.
	 */
	StoredConfig getConfiguration(File file) {
		fileConfigurations[file]
	}
	
	/**
	 * Add configuration for a dependency.
	 */
	void putConfiguration(String group = null, String name = null, String version = null, StoredConfig config) {
		assert group || name: 'At least group or name must be specified for bnd configuration'
		
		Map<String, StoredConfig> nameConfig = dependencyConfigurations.get(group, [:]).get(name, [:])
		StoredConfig current = nameConfig.get(version)
		if (current != null) {
			// append configuration
			current << config
			project.logger.warn "Multiple bnd configurations for group:$group, name:$name, version:$version, later definitions may override previous"
		}
		else {
			nameConfig.put(version, config)
		}
	}
	
	/**
	 * Get the (combined) configuration for the given parameters defining a dependency.
	 * @return the configuration, may be <code>null</code>
	 */
	StoredConfig getConfiguration(String group, String name, String version) {
		final StoredConfig res = new StoredConfig()
		StoredConfig tmp
		
		// fully qualified
		if (group && name && version) {
			tmp = dependencyConfigurations.get(group)?.get(name)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// w/o version
		if (group && name) {
			tmp = dependencyConfigurations.get(group)?.get(name)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
			
		// w/o group
		if (name && version) {
			tmp = dependencyConfigurations.get(null)?.get(name)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// w/o name
		if (group && version) {
			tmp = dependencyConfigurations.get(group)?.get(null)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// only name
		if (name) {
			tmp = dependencyConfigurations.get(null)?.get(name)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// only group
		if (group) {
			tmp = dependencyConfigurations.get(group)?.get(null)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		if (res.empty) {
			null
		}
		else {
			res
		}
	}

}
