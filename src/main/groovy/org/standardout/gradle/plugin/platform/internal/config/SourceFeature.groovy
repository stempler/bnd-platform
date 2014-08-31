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
import org.standardout.gradle.plugin.platform.internal.BundleArtifact
import org.standardout.gradle.plugin.platform.internal.Feature;
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;

class SourceFeature implements Feature {
	
	private final @Delegate Feature feature
	
	private final Project project
	
	private String finalVersion

	public SourceFeature(Feature feature, Project project) {
		super();
		this.feature = feature;
		this.project = project;
	}

	@Override
	public String getId() {
		feature.id + '.source'
	}

	@Override
	public String getLabel() {
		feature.label + ' sources'
	}
	
	@Override
	public Iterable<BundleArtifact> getBundles() {
		// source bundles
		feature.bundles.collect { BundleArtifact ba ->
			def bundle = ba.isSource() ? ba : ba.sourceBundle
			// only accept bundle if it still exists in the artifact map
			// if it has been removed from there, it was an empty source bundle
			(bundle && project.platform.artifacts[bundle.id]) ? bundle : null
		}.findAll()
	}

	@Override
	public Iterable<Feature> getIncludedFeatures() {
		def features = feature.includedFeatures
		features.collect {
			project.platform.features["${it.id}.source"]
		}.findAll()
	}

}
