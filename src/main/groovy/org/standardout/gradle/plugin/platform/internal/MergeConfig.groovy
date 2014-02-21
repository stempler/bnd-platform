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
 * Merge configuration.
 * 
 * @author Simon Templer
 */
class MergeConfig {

	private StoredConfig bundleConfig
	def StoredConfig getBundleConfig() {
		bundleConfig
	}
	
	private Closure matchClosure
	def Closure getMatchClosure() {
		matchClosure
	}
	
	final Map<String, Object> properties
	
	private final Project project
	
	MergeConfig(Project project, Map<String, Object> properties, Closure mergeClosure) {
		this.project = project
		this.properties = properties 
		mergeClosure.delegate = this
		mergeClosure()
	}
	
	def bnd(Closure bndClosure) {
		this.bundleConfig = new StoredConfig(bndClosure)
	}
	
	def match(Closure matchClosure) {
		this.matchClosure = matchClosure
	}
	
	//TODO bundle / include calls
	
}
