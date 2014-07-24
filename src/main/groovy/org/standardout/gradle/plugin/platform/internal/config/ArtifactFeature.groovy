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

import org.gradle.api.Project
import org.standardout.gradle.plugin.platform.internal.ArtifactsMatch;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.Feature


/**
 * Represents the configuration of a platform feature.
 * 
 * @author Simon Templer
 */
class ArtifactFeature implements Feature {
	
	final Project project
	
	final String id
	final String label
	final String version
	final String providerName
	
	/**
	 * List of artifact references
	 */
	final List<ArtifactsMatch> configArtifacts = []
	
	/**
	 * List of included features IDs
	 */
	final List<String> configFeatures = []
	
	ArtifactFeature(Project project, def featureNotation,
		Closure featureClosure) {
		this.project = project
		
		// extract basic feature information from feature notation
		if (featureNotation instanceof Map) {
			id = featureNotation.id
			label = featureNotation.name ?: id
			version = ((featureNotation.version ?: project.platform.featureVersion) ?: project.version) ?: '1.0.0'
			providerName = featureNotation.provider ?: project.platform.featureProvider
		}
		else {
			// assume String id and default values
			String featureString = featureNotation as String
			
			//XXX support some kind of pattern?
			// for now just assume it's the id
			id = featureString
			label = id
			// default to global platform feature version
			version = (project.platform.featureVersion ?: project.version) ?: '1.0.0'
			providerName = project.platform.featureProvider
		}
		
		if (!id) {
			throw new IllegalStateException('A feature ID must be provided when defining a feature')
		}
		
		// create masking delegate to be able to intercept internal call results
		Closure maskedConfig = null
		CustomConfigDelegate maskingDelegate = null
		maskedConfig = {
			maskingDelegate = new CustomConfigDelegate(delegate, this)
			featureClosure.delegate = maskingDelegate
			featureClosure.resolveStrategy = Closure.DELEGATE_FIRST
			featureClosure()
		}
	
		maskedConfig.delegate = project.platform // delegate is the platform extension
		maskedConfig()
		
		// save feature configuration
		project.platform.features[id] = this
	}
		
	Iterable<BundleArtifact> getBundles() {
		/*
		 * Attention: a call to this method can only yield a sensible
		 * result after the artifacts map has been populated by the
		 * respective Gradle task.
		 */
		
		// collect all artifacts that match the respective condition
		project.platform.artifacts.values().findAll { BundleArtifact artifact ->
			configArtifacts.any { ArtifactsMatch match ->
				match.acceptArtifact(artifact)
			}
		}
		
		//TODO also collect transitive dependencies?!
	}
	
	Iterable<Feature> getIncludedFeatures() {
		// resolve feature IDs
		configFeatures.collect {
			project.platform.features[it]
		}.findAll()
	}
	
	/**
	 * Delegate for the configuration closure to intercept calls
	 * for the feature configuration.
	 */
	private static class CustomConfigDelegate {
		private final def orgDelegate
		private final ArtifactFeature feature
		CustomConfigDelegate(def orgDelegate, ArtifactFeature feature) {
			this.orgDelegate = orgDelegate
			this.feature = feature
		}

		@Override
		def invokeMethod(String name, def args) {
			//TODO support manually adding a feature reference
			
			def result = orgDelegate."$name"(*args)
			
			// intercept result
			if (result instanceof ArtifactsMatch) {
				feature.configArtifacts << result
			}
			if (result instanceof Feature) {
				feature.configFeatures << feature.id
			}
			//TODO support for merged bundles
			
			result
		}
		
		@Override
		def getProperty(String name) {
			orgDelegate."$name"
		}
		
		@Override
		void setProperty(String name, def value) {
			orgDelegate."$name" = value
		}
	}
	
}
