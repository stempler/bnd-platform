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

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.standardout.gradle.plugin.platform.PlatformPlugin
import org.standardout.gradle.plugin.platform.internal.util.gradle.DummyDependency;


/**
 * Represents the configuration of a bundle dependency.
 * 
 * @author Simon Templer
 */
class BundleDependency {
	
	BundleDependency(Project project, def dependencyNotation,
		Closure configClosure, boolean createDependency) {
		
		// support single files
		if (dependencyNotation instanceof File) {
			// create file collection
			dependencyNotation = project.files(dependencyNotation)
		}
		
		Closure bndClosure
		
		if (createDependency) {
			// add as platform dependency
			Closure maskedConfig = null
			CustomConfigDelegate maskingDelegate = null
			if (configClosure) {
				maskedConfig = {
					maskingDelegate = new CustomConfigDelegate(delegate)
					configClosure.delegate = maskingDelegate
					// ensure that not the wrong bnd method is called
					configClosure.resolveStrategy = Closure.DELEGATE_FIRST
					configClosure()
				}
			}
		
			dependency = project.dependencies.add(PlatformPlugin.CONF_PLATFORM, dependencyNotation, maskedConfig)
			if (maskingDelegate) {
				bndClosure = maskingDelegate.bndClosure
			}
		}
		else {
			// create detached dependency
			if (dependencyNotation instanceof Map) {
				dependency = new DummyDependency(dependencyNotation)
			}
			else {
				dependency = project.dependencies.create(dependencyNotation)
			}
			bndClosure = configClosure
		}
		
		// determine matchClosure
		if (dependencyNotation instanceof FileCollection) {
			def files = dependencyNotation as List
			// match based on files
			matchClosure = {
				File toMatch = it.file
				files.any { it == toMatch }
			}
		}
		else {
			// match based on group, name and version
			matchClosure = {
				(dependency.group == null || dependency.group == it.group) &&
				(dependency.name == null || dependency.name == it.name) &&
				(dependency.version == null || dependency.version == it.version)
			}
		}
		
		if (bndClosure) {
			StoredConfig config = new StoredConfigImpl(bndClosure)
			
			// add to configurations
			if (dependencyNotation instanceof FileCollection) {
				def files = dependencyNotation as List
				// save configuration for file
				if (files.size() == 1) {
					project.platform.configurations.putConfiguration(files[0], config)
				}
				else {
					// multiple files
					project.logger.warn 'Bnd configuration for a multi file dependency found, applying to all - make sure you know what you are doing'
					files.each { file ->
						StoredConfig clone = new StoredConfigImpl()
						clone << config
						project.platform.configurations.putConfiguration(file, clone)
					}
				}
			}
			else {
				// save dependency configuration
				project.platform.configurations.putConfiguration(
					dependency.group, 
					dependency.name, 
					dependency.version, 
					config)
			}
		}
	}
	
	/**
	 * The project dependency once it was registered using registerDependency.
	 */
	final Dependency dependency
	
	/**
	 * A closure that matches this dependency against a given {@link BundleArtifact}.
	 */
	final Closure matchClosure
	
	/**
	 * Delegate for the configuration closure to intercept calls
	 * for the bundle configuration.
	 */
	private static class CustomConfigDelegate {
		private final def orgDelegate
		CustomConfigDelegate(def orgDelegate) {
			this.orgDelegate = orgDelegate
		}

		/**
		 * Extracted bnd closure		
		 */
		Closure bndClosure
		
		@Override
		def invokeMethod(String name, def args) {
			if (name == 'bnd') {
				// bnd configuration
				def argList = InvokerHelper.asList(args)
				assert argList.size() == 1
				assert argList[0] instanceof Closure
				
				this.bndClosure = argList[0]
			}
			else {
				// delegate to original delegate
				orgDelegate."$name"(args)
			}
		}
		
		@Override
		def getProperty(String name) {
			if (name == 'bndClosure') {
				return this.bndClosure
			}
			orgDelegate."$name"
		}
		
		@Override
		void setProperty(String name, def value) {
			orgDelegate."$name" = value
		}
	}
	
}
