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
import aQute.bnd.header.Parameters
import aQute.bnd.header.OSGiHeader
import org.gradle.api.artifacts.Dependency;
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;

import aQute.bnd.osgi.Constants

/**
 * Represents the configuration of a bundle concerning bnd.
 */
class BndConfig {
	
	/**
	 * Constructor.
	 * 
	 * @param project the gradle project
	 */
	BndConfig(Project project, String group, String name, String version, File file,
		Map<String, String> initialProperties) {
		
		this.project = project
		
		this.group = group
		this.name = name
		this.version = version
		this.file = file
		
		if (initialProperties) {
			properties.putAll(initialProperties)
		}
	}
	
	final Project project
	
	final String group
	
	final String name
	
	final File file
	
	/**
	 * Version that is either provided or can be set for file dependencies.
	 */
	void setVersion(String version) {
		// ensure set version is a valid OSGi version
		properties[Constants.BUNDLE_VERSION] = VersionUtil.toOsgiVersion(version) {
			project.logger.warn "Replacing illegal OSGi version $version by ${it} in bnd configuration"
		}.toString()
	}
	def getVersion() {
		properties[Constants.BUNDLE_VERSION]
	}
	
	/**
	 * Custom symbolic name.
	 */
	void setSymbolicName(String symbolicName) {
		properties[Constants.BUNDLE_SYMBOLICNAME] = symbolicName
	}
	def getSymbolicName() {
		properties[Constants.BUNDLE_SYMBOLICNAME]
	}
	
	/**
	 * Custom bundle name.
	 */
	void setBundleName(String bundleName) {
		properties[Constants.BUNDLE_NAME] = bundleName
	}
	def getBundleName() {
		properties[Constants.BUNDLE_NAME]
	}
	
	/**
	 * Map of bnd instruction names to instructions.
	 */
	final Map<String, String> properties = [:]
	
	/**
	 * Create a bnd instruction.
	 */
	def instruction(String name, def value) {
		properties[name] = (value as String).trim()
		this
	}
	
	/**
	 * Add packages for optional import.
	 */
	def optionalImport(String... packages) {
		def list = packages as List
		def options = list.collect { it + ';resolution:=optional' }
		prependImport(options)
	}
	
	/**
	 * Prepend imported packages. Removes conflicting existing package declarations.
	 */
	def prependImport(String... instructions) {
		prependImport(instructions as List)
	}
		
	/**
	 * Prepend imported packages. Removes conflicting existing package declarations.
	 */
	def prependImport(List<String> instructions) {
		// extract packages (may contain wildcards)
		def packages = instructions.collect {
			String pkg ->
			def pos = pkg.indexOf(';')
			if (pos > 0) {
				pkg[0..pos-1]
			}
			else {
				pkg
			}
		}
		
		String imports = (properties['Import-Package']?:'*').trim()
		
		/*
		 * If a package is already contained in the import package
		 * instruction it may appear repeatedly through this, this
		 * will lead to illegal bundles - so we need to remove those
		 * references, at least fully qualified packages.
		 */
		def packageMatchers = packages.collect {
			String packageExpr ->
			// create a regex from the package expression
			String pRegex = packageExpr
			pRegex = pRegex.replaceAll(/\.\*/, '(\\.[^,]+)?') // dot and wildcard is optional
			pRegex = pRegex.replaceAll(/\./, '\\.') // escape other dots
			pRegex = pRegex.replaceAll(/\*/, '[^,]+') // wildcards match anything save comma and wildcards
			'^' + pRegex + '$' // must match a full package
			
			//XXX possible to use the bnd API for matching?
		}
		
		// check for each package entry if it is OK

		// retrieve packages currently specified
		Parameters pkgs = OSGiHeader.parseHeader(imports)
		//TODO do something w/ previous attrs?
		def importList = pkgs.keySet().collect{ it.trim() }
		def accepted = importList.findAll {
			String pkg ->
			boolean match = packageMatchers.any {
				pkg ==~ it
			}
			!match
		}
		imports = accepted.join(',') // keep all that were accepted (meaning where there was no match)
		
		instruction 'Import-Package', instructions.join(',') + ',' + imports
	}
	
}


