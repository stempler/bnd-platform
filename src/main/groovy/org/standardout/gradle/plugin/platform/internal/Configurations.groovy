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

class Configurations {
	
	private final Project project
	
	private final Map<File, StoredConfig> fileConfigurations = [:]
	
	private final Map<String, Map<String, Map<String, StoredConfig>>> dependencyConfigurations = [:]
	
	Configurations(Project project) {
		this.project = project
	}
	
	void putConfiguration(File file, StoredConfig config) {
		assert file
		
		if (fileConfigurations.containsKey(file)) {
			project.logger.warn "Multiple bnd configurations for file $file, using only the first encountered"
		}
		else {
			fileConfigurations[file] = config
		}
	}
	
	StoredConfig getConfiguration(File file) {
		fileConfigurations[file]
	}
	
	void putConfiguration(String group = null, String name = null, String version = null, StoredConfig config) {
		assert group || name: 'At least group or name must be specified for bnd configuration'
		
		Map<String, StoredConfig> nameConfig = dependencyConfigurations.get(group, [:]).get(name, [:])
		if (nameConfig.containsKey(version)) {
			project.logger.warn "Multiple bnd configurations for group:$group, name:$name, version:$version, using only the first encountered"
		}
		else {
			nameConfig.put(version, config)
		}
	}
	
	StoredConfig getConfiguration(String group, String name, String version) {
		StoredConfig res = null
		
		// fully qualified
		res = dependencyConfigurations.get(group)?.get(name)?.get(version)
		if (res == null) {
			// w/o version
			res = dependencyConfigurations.get(group)?.get(name)?.get(null)
		}
		if (res == null) {
			// w/o group
			res = dependencyConfigurations.get(null)?.get(name)?.get(version)
		}
		if (res == null) {
			// w/o name
			res = dependencyConfigurations.get(group)?.get(null)?.get(version)
		}
		if (res == null) {
			// only name
			res = dependencyConfigurations.get(null)?.get(name)?.get(null)
		}
		if (res == null) {
			// only group
			res = dependencyConfigurations.get(group)?.get(null)?.get(null)
		}
		
		res
	}

}
