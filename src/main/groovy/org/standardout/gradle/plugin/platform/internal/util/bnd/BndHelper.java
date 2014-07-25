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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;

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
	 * @return if the target file was created, it will not be created if the source
	 *   Jar is empty or invalid
	 * @throws Exception if wrapping the Jar fails
	 */
	public static boolean wrap(File source, Collection<File> classpath, File target,
			Map<String, String> properties, boolean removeSignature) throws Exception {
		File file = source;
		
		// test file
		try (ZipFile zip = new ZipFile(file)) {
			// (actually we never get here if it is empty)
			if (!zip.entries().hasMoreElements()) {
				// empty Zip file
				return false;
			}
		} catch (ZipException e) {
			// empty or corrupt Zip file
			return false;
		}

		Analyzer wrapper = new Analyzer();
		wrapper.setProperty(Analyzer.NOEXTRAHEADERS, "true"); // prevent adding e.g. last modified header
		try {
			if (classpath != null) {
				for (File f : classpath) {
					wrapper.addClasspath(f);
				}
			}

			wrapper.setJar(file); // fails for empty JARs!
			
			if (removeSignature) {
				doRemoveSignature(wrapper.getJar());
			}

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
		
		return true;
	}
	
	private static void doRemoveSignature(Jar jar) {
		Map<String, Resource> metaInf = jar.getDirectories().get("META-INF");
		Set<String> toRemove = new HashSet<String>();
		if (metaInf != null) {
			for (String resource : metaInf.keySet()) {
				String upper = resource.toUpperCase();
				if (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")) {
					toRemove.add(resource);
				}
			}
		}
		for (String resource : toRemove) {
			jar.remove(resource);
		}
	}

}
