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

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

/**
 * Stores the configuration of a bundle concerning bnd.
 */
class BndConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param project the gradle project
	 * @param dependency the dependency to configure
	 */
	BndConfig(Project project, Dependency dependency) {
		this.project = project
		
		group = dependency.group
		name = dependency.name
		version = dependency.version
	}
	
	final Project project
	
	final String group
	
	final String name
	
	/**
	 * Version that is either provided or can be set for file dependencies.
	 */
	String version
	
	/**
	 * Custom symbolic name (currently only for file dependencies)
	 */
	String symbolicName
	
	/**
	 * Custom bundle name (currently only for file dependencies)
	 */
	String bundleName
	
	/**
	 * Map of bnd instruction names to instructions.
	 */
	final Map<String, String> properties = [:]
	
	/**
	 * Create a bnd instruction.
	 */
	def instruction(String name, def value) {
		properties[name] = (value as String).trim()
		this
	}
	
}
