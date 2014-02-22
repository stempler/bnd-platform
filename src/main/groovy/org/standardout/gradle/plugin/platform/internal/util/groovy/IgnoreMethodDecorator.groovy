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

package org.standardout.gradle.plugin.platform.internal.util.groovy

import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * Decorator for a class that ignores method invocations.
 */
class IgnoreMethodDecorator {

	private final def decoratee
	
	IgnoreMethodDecorator(def decoratee) {
		this.decoratee = decoratee
	}
	
	@Override
	def invokeMethod(String name, def args) {
		// ignore
	}
	
	@Override
	def getProperty(String name) {
		decoratee."$name"
	}
	
	@Override
	void setProperty(String name, def value) {
		decoratee."$name" = value
	}
	
}
