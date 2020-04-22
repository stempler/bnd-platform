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

import java.util.Set;

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.standardout.gradle.plugin.platform.internal.ArtifactsMatch;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.DependencyArtifact;
import org.standardout.gradle.plugin.platform.internal.Feature
import org.standardout.gradle.plugin.platform.internal.ResolvedBundleArtifact;
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;


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
	final String license
	
	/**
	 * List of artifact references
	 */
	final List<ArtifactsMatch> configArtifacts = []
	
	/**
	 * List of included features IDs
	 */
	final List<String> configFeatures = []
	
	private String finalVersion
	
	ArtifactFeature(Project project, def featureNotation,
			Closure featureClosure) {
		this.project = project
		
		def id
		def label
		def version
		def providerName
		def license
		
		// extract basic feature information from feature notation
		if (featureNotation instanceof Map) {
			id = featureNotation.id
			label = featureNotation.name
			version = featureNotation.version
			providerName = featureNotation.provider
			license = featureNotation.license
		}
		else {
			// assume String id and default values
			String featureString = featureNotation as String
			
			//XXX support some kind of pattern?
			// for now just assume it's the id
			id = featureString
		}

		if (!id) {
			throw new IllegalStateException('A feature ID must be provided when defining a feature')
		}
			
		// default values and source adaptions	
		this.version = VersionUtil.toOsgiVersion(((version ?: project.platform.featureVersion) ?: project.version) ?: '1.0.0').toString()
		this.providerName = providerName ?: project.platform.featureProvider
		this.id = id
		this.label = label ?: id
		this.license = license ?: ""
		
		// create masking delegate to be able to intercept internal call results
		Closure maskedConfig = null
		CustomConfigDelegate maskingDelegate = null
		maskedConfig = {
			maskingDelegate = new CustomConfigDelegate(delegate, this)
			Closure configClone = featureClosure.clone()
			configClone.delegate = maskingDelegate
			configClone.resolveStrategy = Closure.DELEGATE_FIRST
			configClone()
		}
	
		maskedConfig.delegate = project.platform // delegate is the platform extension
		maskedConfig()
		
		// save feature configuration
		project.platform.features[this.id] = this
	}
		
	@Override	
	public String getVersion() {
		if (!finalVersion) {
			finalVersion = VersionUtil.addQualifier(version, this, project)
		}
		finalVersion
	}
		
	Iterable<BundleArtifact> getBundles() {
		/*
		 * Attention: a call to this method can only yield a sensible
		 * result after the artifacts map has been populated by the
		 * respective Gradle task.
		 */
		
		// collect all artifacts that match the respective condition
		def artifacts = project.platform.artifacts.values().findAll { BundleArtifact artifact ->
			configArtifacts.any { ArtifactsMatch match ->
				match.acceptArtifact(artifact)
			}
		}
		
		// collect transitive dependencies
		transitiveArtifacts(artifacts)
	}
	
	private Iterable<BundleArtifact> transitiveArtifacts(Collection<BundleArtifact> artifacts) {
		Map<String, BundleArtifact> allArtifacts = [:]
		
		artifacts.each { BundleArtifact artifact ->
			if (artifact instanceof DependencyArtifact) {
				artifact.representedDependencies.each { ResolvedDependency dep ->
					// find bundle artifacts for resolved artifacts
					def bundleArts = findArtifacts(dep.allModuleArtifacts)
					bundleArts.each {
						allArtifacts[it.id] = it
					}
				}
			}
			
			// in any case, add the bundle itself
			allArtifacts[artifact.id] = artifact
		}
		
		
		// artifact bundles
		allArtifacts.values().findAll { BundleArtifact ba ->
			!ba.isSource()
		}
	} 
	
	/**
	 * Find bundle artifact representations for resolved artifacts.
	 */
	private Collection<BundleArtifact> findArtifacts(Iterable<ResolvedArtifact> arts) {
		def result = []
		
		arts.each { ResolvedArtifact ra ->
			def id = "${ra.moduleVersion.id.group}:${ra.moduleVersion.id.name}:${ra.moduleVersion.id.version}"
			def ba = project.platform.artifacts[id]
			if (ba != null) {
				result << ba
			}
		}
		
		result
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

			/*
			 * If there are further nested closures inside features
			 * that have OWNER_FIRST resolve strategy, the PlatformPluginExtension
			 * is asked first, and we cannot intercept the call.
			 * Thus as an alternative, 'plugin' can (should) be called instead
			 * of 'bundle' inside feature.
			 * 
			 *  XXX an alternative would be having some kind of feature stack in
			 *  the extension, but this requires then the bundle method in the
			 *  extension to add the bundle to the feature.		
			 */
			if (name == 'plugin') name = 'bundle'
			
			def result = orgDelegate."$name"(*args)
			
			// intercept result
			if (result instanceof ArtifactsMatch) {
				// bundle or merge
				feature.configArtifacts << result
			}
			if (result instanceof Feature) {
				feature.configFeatures << feature.id
			}
			
			result
		}
		
		@Override
		def getProperty(String name) {
			if (name == 'includes') {
				// expose feature list to allow adding feature IDs manually
				feature.configFeatures
			}
			else {
				orgDelegate."$name"
			}
		}
		
		@Override
		void setProperty(String name, def value) {
			orgDelegate."$name" = value
		}
	}
	
}
