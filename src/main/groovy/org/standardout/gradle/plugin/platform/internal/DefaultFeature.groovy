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

import org.gradle.api.Project
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;


class DefaultFeature implements Feature {

	String id
	String label
	String version
	String providerName
	String license
	String description
	String copyright
	String plugin
	List<BundleArtifact> bundles = []
	List<Feature> includedFeatures = []
	Project project

	private String finalVersion

	@Override
	public String getVersion() {
		if (!finalVersion) {
			finalVersion = VersionUtil.addQualifier(version?:'0.0.0', this, project)
		}
		finalVersion
	}

	void setVersion(String version) {
		this.version = VersionUtil.toOsgiVersion(version).toString()
	}

	String getProviderName() {
		providerName?:'Generated with bnd-platform'
	}

	@Override
	public Iterable<BundleArtifact> getBundles() {
		bundles == null ? [] : bundles
	}

	@Override
	public Iterable<Feature> getIncludedFeatures() {
		includedFeatures == null ? [] : includedFeatures
	}

	@Override
	public Iterable<RequiredFeature> getRequiredFeatures()
	{
		[]
	}

}
