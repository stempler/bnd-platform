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
	
	private final List<Closure> matchClosures = []
	def List<Closure> getMatchClosures() {
		matchClosures
	}
	
	private Set<String> bundleIds = new HashSet<String>()
	
	final Map<String, Object> properties
	
	private final Project project
	
	MergeConfig(Project project, Map<String, Object> properties, Closure mergeClosure) {
		this.project = project
		this.properties = properties 
		mergeClosure.delegate = this
		mergeClosure.resolveStrategy = Closure.DELEGATE_FIRST
		mergeClosure()
	}
	
	def bnd(Closure bndClosure) {
		this.bundleConfig = new StoredConfig(bndClosure)
	}

	/**
	 * Defines a closure that matches against {@link BundleArtifact}s to merge.
	 */
	def match(Closure matchClosure) {
		this.matchClosures << matchClosure
	}
	
	/**
	 * Add a bundle to merge and as dependency.
	 */
	def bundle(def dependencyNotation) {
		BundleDependency dep = new BundleDependency(
			project,
			dependencyNotation,
			null,
			true // create dependency
		)
		if (dep.matchClosure != null) {
			this.matchClosures << dep.matchClosure
		}
	}
	
	/**
	 * Include a bundle to merge, but not as dependency.
	 */
	def include(def dependencyNotation) {
		BundleDependency dep = new BundleDependency(
			project,
			dependencyNotation,
			null,
			false // don't create dependency
		)
		if (dep.matchClosure != null) {
			this.matchClosures << dep.matchClosure
		}
	}
}