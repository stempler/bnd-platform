/*
 * Copyright 2010 the original author or authors.
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

package org.standardout.gradle.plugin.platform.internal.util.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

/**
 * Implementation based on several classes from the gradle ide subproject, mainly
 * <code>DefaultIdeDependencyResolver</code>.
 */
class DependencyHelper {
	
	/**
	 * Spec that accepts only external dependencies.
	 */
	private static class ExternalDependencySpec implements Spec<Dependency> {
		public boolean isSatisfiedBy(Dependency element) {
			return element instanceof ExternalDependency
		}
	}
	
	/**
	 * Spec that accepts all dependencies.
	 */
	private static class SatisfyAllSpec implements Spec<Dependency> {
		public boolean isSatisfiedBy(Dependency element) {
			true
		}
	}
	private static final SatisfyAllSpec SATISFY_ALL = new SatisfyAllSpec()
	
	/**
	 * Retrieve a specific dependency.
	 */
	static File getDetachedDependency(Project project, def dependencyNotation, String requiredExtension = null, boolean nullOnMultiple = false) {
		Dependency dep = project.dependencies.create(dependencyNotation)
		Configuration configuration = project.configurations.detachedConfiguration(dep)
		Set<File> artifacts = configuration.resolve()
		if (requiredExtension != null) {
			artifacts = artifacts.findAll {
				File file ->
				// only accept files w/ specific extension
				file.name.endsWith(requiredExtension)
			}
		}
		if (artifacts.empty) {
			null
		}
		else if (artifacts.size() == 1) {
			artifacts.iterator().next()
		}
		else {
			if (nullOnMultiple) {
				project.logger.warn "Multiple files retrieved for dependency $dependencyNotation, ignoring result"
				null
			}
			else {
				project.logger.warn "Multiple files retrieved for dependency $dependencyNotation, using a random instance"
				artifacts.iterator().next()
			}
		}
	}
	
	/**
	 * Retrieve a specific dependency.
	 */
	static Set<ResolvedArtifact> getDirectDependencies(Project project, def dependencyNotation) {
		Dependency dep = project.dependencies.create(dependencyNotation)
		Configuration configuration = project.configurations.detachedConfiguration(dep)
		
		Set<ResolvedDependency> resolvedDependencies = configuration.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(SATISFY_ALL)
		
		Set<ResolvedArtifact> result = new HashSet<ResolvedArtifact>()
		resolvedDependencies.each {
			ResolvedDependency rd ->
			rd.children.each {
				result.addAll(it.moduleArtifacts)
			}
		}
		
		result
	}
	
	/**
	 * Resolve source artifacts for dependencies in the given configuration.
	 */
	static Set<ResolvedArtifact> resolveSourceArtifacts(Configuration configuration, ConfigurationContainer configurationContainer) {
		// resolve all dependencies (including children)
		Set<ResolvedDependency> allDeps = resolveAllDependencies(configuration)
		// resolve the source artifacts
		retrieveSourcesForDeps(configurationContainer, allDeps)
	}

	/**
	 * Recursively resolves dependencies including their children.
	 */
	static Set<ResolvedDependency> resolveAllDependencies(Configuration configuration) {
		// get all external resource dependencies
		Set<ResolvedDependency> resolvedDependencies = configuration.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(new ExternalDependencySpec())
		// accumulate dependencies including children
		getAllDependencies(resolvedDependencies, new LinkedHashSet<ResolvedDependency>())
	}

	private static Set<ResolvedDependency> getAllDependencies(Collection<ResolvedDependency> deps, Set<ResolvedDependency> allDeps) {
		for(ResolvedDependency resolvedDependency in deps) {
			boolean notSeenBefore = allDeps.add(resolvedDependency)
			if (notSeenBefore) {
				getAllDependencies(resolvedDependency.children, allDeps)
			}
		}

		allDeps
	}

	/**
	 * Retrieves source artifacts for the given resolved dependencies.
	 */
	private static Set<ResolvedArtifact> retrieveSourcesForDeps(ConfigurationContainer configurationContainer, Set<ResolvedDependency> allDeps) {
		List<ExternalDependency> externalDependencies = allDeps.collect {
			ResolvedDependency dep ->
			ExternalModuleDependency sourceDep = new DefaultExternalModuleDependency(
				dep.moduleGroup, 
				dep.moduleName, 
				dep.moduleVersion, 
				dep.configuration)
            sourceDep.transitive = false
			def artifact = new DefaultDependencyArtifact(sourceDep.name, "source", "jar", "sources", null)
            sourceDep.addArtifact(artifact)
		}.toList()
		Configuration detachedConfiguration = configurationContainer.detachedConfiguration(externalDependencies.toArray(new Dependency[externalDependencies.size()]))
		return detachedConfiguration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.satisfyAll())
	}
	
}
