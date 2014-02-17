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

package org.standardout.gradle.plugin.platform

import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.BundleDependency;

/**
 * Extension for the platform plugin.
 *  
 * @author Simon Templer
 */
class PlatformPluginExtension {
	
	/**
	 * States if source for external dependencies should be fetched and corresponding source bundles created. 
	 */
	boolean fetchSources = true
	
	/**
	 * The ID for the platform feature.
	 */
	String featureId = 'platform.feature'
	
	/**
	 * The name for the platform feature.
	 */
	String featureName = 'Generated platform feature'
	
	/**
	 * The platform feature version.
	 */
	String featureVersion = '1.0.0'
	
	/**
	 * The ID of the feature's category in the update site.
	 */
	String categoryId = 'platform'
	
	/**
	 * The name of the feature's category in the update site. 
	 */
	String categoryName = 'Target platform'
	
	/**
	 * The directory to place the update site in. Will default to 'updatesite' in the build folder.
	 */
	File updateSiteDir
	
	/**
	 * The directory of a local Eclipse installation. If none is specified the
	 * <code>ECLIPSE_HOME</code> system property is checked, if it is not given as
	 * well it is tried to download Eclipse based on the URLs defined in {@link #eclipseMirror}
	 */
	File eclipseHome
	
	/**
	 * Nested map that is checked for Eclipse download URLs, keys are
	 * osgiOS (win32, linux, macosx), osgiWS (win32, gtk, cocoa) and
	 * arch (x86, x86_64) in that order.
	 * Specify an alternate Eclipse mirror like this:
	 * <code>eclipseMirror.win32.win32.x86 = 'http://...'</code>   
	 */
	def eclipseMirror = [
		win32: [
			win32: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-win32.zip',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-win32-x86_64.zip'
			]
		],
		linux: [
			gtk: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-linux-gtk.tar.gz',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-linux-gtk-x86_64.tar.gz'
			]
		],
		macosx: [
			cocoa: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-macosx-cocoa.tar.gz',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-macosx-cocoa-x86_64.tar.gz'
			]
		]
	]
	
	/**
	 * Call bundle to add a dependency.
	 * 
	 * @param dependencyNotation a dependency notation as supported by Gradle
	 * @param configClosure a closure that performs dependency configuration
	 * @return
	 */
	def bundle(def dependencyNotation, Closure configClosure = null) {
		bundles << new BundleDependency(
				dependencyNotation: dependencyNotation,
				configClosure: configClosure
			)
	}
	
	// for internal use
	
	/**
	 * Collects bundle dependencies added with {@link #bundle(def, Closure)}
	 */
	final List<BundleDependency> bundles = []
	
	/**
	 * Maps dependency IDs to {@link BundleDependency}ies
	 */
	final Map<String, BundleDependency> bundleIndex = [:]
	
	/**
	 * Maps artifact IDs to {@link BundleArtifact}s
	 */
	final Map<String, BundleArtifact> artifacts = [:]
}