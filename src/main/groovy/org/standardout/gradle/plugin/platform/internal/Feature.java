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

package org.standardout.gradle.plugin.platform.internal;

/**
 * Represents an Eclipse Update Site Feature.
 *
 * The version, bundles and includedFeatures properties may only be accessed
 * after the feature configuration is complete.
 */
public interface Feature {

	public String getId();

	public String getLabel();

	public String getVersion();

	public String getProviderName();

	public String getLicense();

	public String getDescription();

	public String getCopyright();

	public String getPlugin();

	public Iterable<BundleArtifact> getBundles();

	public Iterable<Feature> getIncludedFeatures();

	public Iterable<RequiredFeature> getRequiredFeatures();

	class RequiredFeature {
		public final String featureName;
		public final String version;
		public final String match;


		public RequiredFeature(String featureName, String version, String match) {
			this.featureName = featureName;
			this.version = version;
			this.match = match;
		}
	}
}
