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

package org.standardout.gradle.plugin.platform.internal.util.bnd;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Manifest;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;

/**
 * Utilities for working with bnd.
 * 
 * @author Simon Templer
 */
public class BndHelper {
	
	/**
	 * Create a default builder.
	 */
	public static Builder createBuilder() {
		Builder b = new Builder();
		b.setTrace(false);
		b.setPedantic(true);
		b.setFailOk(false);
		
		return b;
	}
	
	/**
	 * Build the bnd configuration present in the given builder and write the
	 * bundle to the target file.
	 *  
	 * @param b the builder
	 * @param target the target file
	 * @throws Exception if creating the bundle fails
	 */
	public static void buildAndClose(Builder b, File target) throws Exception {
		try {
			b.build();
	
			if (b.isOk()) {
				b.save(target, true);
				if (!b.isOk()) {
					target.delete();
					throw new IllegalStateException("Failed to save bundled jar");
				}
			}
			else {
				throw new IllegalStateException("Failed to build bnd configuration");
			}
		} finally {
			b.close();
		}
	}
	
	/**
	 * Wrap a Jar as it is, only changing the manifest. 
	 * @param source the source jar
	 * @param classpath the class path
	 * @param target the target file 
	 * @param properties the bnd properties
	 * @throws Exception if wrapping the Jar fails
	 */
	public static void wrap(File source, Collection<File> classpath, File target, Map<String, String> properties) throws Exception {
		File file = source;

		Analyzer wrapper = new Analyzer();
		try {
			if (classpath != null) {
				for (File f : classpath) {
					wrapper.addClasspath(f);
				}
			}

			wrapper.setJar(file);

			File outputFile = target;
			outputFile.delete();

			// defaults
			wrapper.setImportPackage("*;resolution:=optional");
			wrapper.setExportPackage("*");
			
			// custom properties
			wrapper.addProperties(properties);

			Manifest m = wrapper.calcManifest();

			if (wrapper.isOk()) {
				wrapper.getJar().setManifest(m);
				wrapper.save(outputFile, true);
				if (!wrapper.isOk()) {
					throw new IllegalStateException("Failed creating a wrapped bundle");
				}
			}
			else {
				throw new IllegalStateException("Failed calculating the manifest for a wrapped bundle");
			}
		}
		finally {
			wrapper.close();
		}
	}

}
