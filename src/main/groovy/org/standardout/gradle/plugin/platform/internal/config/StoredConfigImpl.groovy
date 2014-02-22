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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.standardout.gradle.plugin.platform.internal.util.groovy.IgnoreMethodDecorator;
import org.standardout.gradle.plugin.platform.internal.util.groovy.IgnoreSetPropertyDecorator;

import java.io.File

/**
 * Stores the configuration of a bundle concerning bnd.
 */
class StoredConfigImpl implements StoredConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param bndClosure the closure representing the bnd configuration
	 */
	StoredConfigImpl(Closure bndClosure = null) {
		if (bndClosure != null) {
			bndClosures << bndClosure
		}
	}
	
	final List<Closure> bndClosures = []

	BndConfig evaluate(Project project, File file) {
		evaluate(project, null, null, null, file)
	}	
	
	BndConfig evaluate(Project project, String group, String name, String version, File file = null) {
		BndConfig res = null
		if (bndClosures) {
			res = new BndConfig(project, group, name, version, file)
			
			/*
			 * Evaluate bnd closures in order (later may override properties set in previous)
			 * 
			 * We do it two times, so instructions in early closures may access variables
			 * specified later (e.g. the bundle version through version = ...). The first
			 * run ignores instruction calls, the second ignores setting properties. 
			 */
			
			// first run (no instructions)
			def delegate = new IgnoreMethodDecorator(res)
			callBndClosures(delegate)
			
			// second run (only instructions)
			delegate = new IgnoreSetPropertyDecorator(res)
			callBndClosures(delegate)
		}
		
		res
	}
	
	private void callBndClosures(def delegate) {
		bndClosures.each {
			Closure bndClosure ->
			Closure copy = bndClosure.clone()
			copy.delegate = delegate
			copy.resolveStrategy = Closure.DELEGATE_FIRST
			copy()
		}
	}

	/**
	 * Append the given configuration.	
	 */
	void leftShift(StoredConfig other) {
		if (other != null) {
			bndClosures.addAll(other.bndClosures)
		}
	}
	
	/**
	 * Prepend the own configuration to the given configuration object.
	 */
	void rightShift(StoredConfig other) {
		if (other != null) {
			other.bndClosures.addAll(0, bndClosures)
		}
	}
	
	boolean isEmpty() {
		bndClosures.empty
	}
	
}
