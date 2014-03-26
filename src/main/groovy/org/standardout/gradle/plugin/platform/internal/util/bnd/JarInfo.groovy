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

package org.standardout.gradle.plugin.platform.internal.util.bnd

import java.util.jar.JarFile;

import aQute.bnd.osgi.Analyzer;

class JarInfo {
	
	/**
	 * Instruction properties - properties to retain as instructions when
	 * wrapping an existing bundle.
	 */
	private static final Set<String> INSTRUCTION_PROPERTIES = ([
		Analyzer.BUNDLE_SYMBOLICNAME,
		Analyzer.BUNDLE_VERSION,
		Analyzer.BUNDLE_NAME,
		Analyzer.EXPORT_PACKAGE,
		Analyzer.IMPORT_PACKAGE,
		Analyzer.BUNDLE_LICENSE,
		Analyzer.BUNDLE_VENDOR
	] as Set).asImmutable()

	final Map<String, String> instructions
	
	final String symbolicName
	
	final String bundleName
	
	final String version
	
	JarInfo(File file) {
		JarFile jar = new JarFile(file)
		
		def manifest = jar.manifest
		Map<String, String> properties = [:]
		
		if (manifest != null) {
			def main = manifest.mainAttributes
			INSTRUCTION_PROPERTIES.each {
				String value = main.getValue(it)
				if (value) {
					properties[it] = value
				}
			}
			
			bundleName = main.getValue(Analyzer.BUNDLE_NAME)
			version = main.getValue(Analyzer.BUNDLE_VERSION)
			symbolicName = extractSymbolicName(main.getValue(Analyzer.BUNDLE_SYMBOLICNAME))
		}
		else {
			// the Jar has no manifest
			bundleName = null
			version = null
			symbolicName = null
		}
		
		instructions = properties.asImmutable() 
	}
	
	public static String extractSymbolicName(String name) {
		if (name == null) {
			return name
		}
		
		int end = name.indexOf(';')
		if (end > 0) {
			// remove all additional instructions
			name[0..(end - 1)]
		}
		else {
			name
		}
	}
	
}
