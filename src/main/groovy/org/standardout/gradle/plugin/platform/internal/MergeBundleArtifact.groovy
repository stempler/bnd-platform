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

import java.io.File;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;

class MergeBundleArtifact extends FileBundleArtifact implements DependencyArtifact {
	
	private final Iterable<ResolvedDependency> representedDependencies
	
	private final Set<ResolvedArtifact> directDependencies
	
	/**
	 * Create a bundle artifact represented by a Jar.
	 */
	MergeBundleArtifact(File artifactFile, Project project, StoredConfig config,
			String customId, Set<ResolvedArtifact> directDependencies,
			Iterable<ResolvedDependency> representedDependencies) {
		super(artifactFile, project, config, customId)
		this.directDependencies = directDependencies.asImmutable()
		this.representedDependencies = representedDependencies
	}

	@Override
	public Set<ResolvedArtifact> getDirectDependencies(Project project) {
		directDependencies
	}

	@Override
	public Iterable<ResolvedDependency> getRepresentedDependencies() {
		representedDependencies
	}

}
