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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

/**
 * Stores the configuration of a bundle concerning bnd.
 */
class StoredConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param bndClosure the closure representing the bnd configuration
	 */
	StoredConfig(Closure bndClosure) {
		this.bndClosure = bndClosure
	}
	
	final Closure bndClosure

	BndConfig evaluate(Project project, File file) {
		evaluate(project, null, null, null, file)
	}	
	
	BndConfig evaluate(Project project, String group, String name, String version, File file = null) {
		BndConfig res = null
		if (bndClosure) {
			res = new BndConfig(project, group, name, version, file)
			bndClosure.delegate = res
			bndClosure.resolveStrategy = Closure.DELEGATE_FIRST
			bndClosure()
		}
		
		res
	}
	
	boolean isEmpty() {
		bndClosure == null
	}
	
}
