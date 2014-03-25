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

package org.standardout.gradle.plugin.platform.internal.config

import org.gradle.api.Project;

/**
 * Represents the configuration of a library concerning the import of packages to other bundles.
 */
class ImportsConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param project the gradle project
	 */
	ImportsConfig(Project project, String group, String name, String version) {
		this.project = project
		
		this.group = group
		this.name = name
		this.version = version
	}
	
	final Project project
	
	final String group
	
	final String name
	
	final String version
	
	Closure versionStrategy
	
}
