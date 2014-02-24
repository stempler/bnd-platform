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

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project

/**
 * Extension that allows including build scripts based on methods defined in
 * such a script.
 *
 * @author Simon Templer
 */
class IncludePluginExtension {
	
	private static final List<String> DEF_EXTENSIONS = ['.groovy', '.gradle'].asImmutable()
	
	private final Project project
	
	IncludePluginExtension(Project project) {
		this.project = project
	}
	
	def location(def file, Closure closure = null) {
		// determine script location
		def loc = file
		if (!(loc instanceof File)) {
			loc = project.file(loc as String)
		}
		
		if (loc.isDirectory()) {
			// look for a script with the same name as the directory
			def candidate = DEF_EXTENSIONS.collect {
				new File(loc, loc.name + it)
			}.find {
				File c ->
				c.exists() && !c.isDirectory()
			}
			if (candidate) {
				// use the candidate
				loc = candidate
			}
			else {
				// call with all valid script files
				boolean found = false
				loc.eachFile {
					File f ->
					if (DEF_EXTENSIONS.any { f.name.endsWith(it) } && !f.isDirectory()) {
						location(f, closure?.clone())
						found = true
					}
				}
				if (!found) {
					throw new IllegalStateException("Could not find any script files to include in $loc")
				}
				return
			}
		}
		
		project.logger.info "Including script from $loc"
		
		// load script
		Binding binding = new Binding()
		binding.setVariable('project', project)
		binding.setVariable('thisFile', loc)
		binding.setVariable('thisDir', loc.parentFile)
		CompilerConfiguration compilerConf = new CompilerConfiguration()
		compilerConf.scriptBaseClass = IncludeScript.name
		GroovyShell shell = new GroovyShell(IncludeScript.classLoader, binding, compilerConf)
		Script script = shell.parse(loc.text) // use file content instead of file as source, to avoid restrictions on the file name
		
		if (closure == null) {
			closure = {
				script.run()
			}
		}
		closure.delegate = new IncludeDelegate(script, project)
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure()
	}
	
	// alias for location
	def from(def file, Closure closure = null) {
		location(file, closure)
	}
	
	private static class IncludeDelegate {
		private final Script script
		private final Project project
		
		IncludeDelegate(Script script, Project project) {
			this.script = script
			this.project = project
		}
		
		def invokeMethod(String name, def args) {
			// invoke script method
			InvokerHelper.invokeMethod(script, name, args)
		}
		
		def getProperty(String name) {
			if (name == 'project') {
				this.project
			}
			else {
				// delegate properties to project
				this.project."$name"
			}
		}
	}
	
}
