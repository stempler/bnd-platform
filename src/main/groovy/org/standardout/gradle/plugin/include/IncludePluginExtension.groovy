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
import org.gradle.api.Project

/**
 * Extension that allows including build scripts based on methods defined in
 * such a script.
 *
 * @author Simon Templer
 */
class IncludePluginExtension {
	
	private final Project project
	
	IncludePluginExtension(Project project) {
		this.project = project
	}
	
	def location(def file, Closure closure) {
		// determine script location
		def loc = file
		if (!(loc instanceof File)) {
			loc = project.file(loc as String)
		}
		
		// load script
		Binding binding = new Binding()
		binding.setVariable('project', project)
		GroovyShell shell = new GroovyShell(binding)
		Script script = shell.parse(loc)
		
		closure.delegate = new IncludeDelegate(script)
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure()
	}
	
	private class IncludeDelegate {
		private final Script script
		IncludeDelegate(Script script) {
			this.script = script
		}
		
		def invokeMethod(String name, def args) {
			// invoke script method
			InvokerHelper.invokeMethod(script, name, args)
		}
	}
	
}
