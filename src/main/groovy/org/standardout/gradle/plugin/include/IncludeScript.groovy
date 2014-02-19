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

package org.standardout.gradle.plugin.include

import org.codehaus.groovy.runtime.InvokerHelper;

import groovy.lang.Script

/**
 * Base class for included scripts. Delegates missing methods or properties to
 * the project. This allows direct calls to project within the script or script
 * methods.
 * 
 * @author Simon Templer
 */
abstract class IncludeScript extends Script {

	def methodMissing(String name, def args) {
		InvokerHelper.invokeMethod(this.binding.project, name, args)
	}
	
	def propertyMissing(String name, value) { 
		this.binding.project."$name" = value
	}
	
	def propertyMissing(String name) {
		this.binding.project."$name"
	}
	
}
