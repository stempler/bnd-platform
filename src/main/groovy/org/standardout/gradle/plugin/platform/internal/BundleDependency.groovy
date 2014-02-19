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

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.standardout.gradle.plugin.platform.PlatformPlugin


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
			dependency = project.dependencies.create(dependencyNotation)
			bndClosure = configClosure
		}
		
		bndConfig = new BndConfig(project, dependency)
		if (bndClosure) {
			// store bnd configuration
			bndClosure.delegate = bndConfig
			bndClosure.resolveStrategy = Closure.DELEGATE_FIRST
			bndClosure()
		}
		
		// add to bundle index
		if (dependencyNotation instanceof FileCollection) {
			// add per file, with file name as key
			dependencyNotation.each {
				project.platform.bundleIndex[it as String] = this
			}
		}
		else {
			// add with unified name as key
			String id = dependency.group + ':' + dependency.name + ':' + dependency.version
			project.platform.bundleIndex[id] = this
		}
	}
	
	/**
	 * Custom bnd configuration. 
	 */
	final BndConfig bndConfig
	
	/**
	 * The project dependency once it was registered using registerDependency.
	 */
	final Dependency dependency
	
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
				
				bndClosure = argList[0]
			}
			else {
				// delegate to original delegate
				orgDelegate."$name"(args)
			}
		}
	}
	
}
