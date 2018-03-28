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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Instruction;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;
import aQute.libg.tuple.Pair;

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
	 * Build the bnd configuration present in the given builder and write the bundle
	 * to the target file.
	 * 
	 * @param b      the builder
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
			} else {
				throw new IllegalStateException("Failed to build bnd configuration");
			}
		} finally {
			b.close();
		}
	}

	/**
	 * Wrap a Jar as it is, only changing the manifest.
	 * 
	 * @param source     the source jar
	 * @param classpath  the class path
	 * @param target     the target file
	 * @param properties the bnd properties
	 * @return if the target file was created, it will not be created if the source
	 *         Jar is empty or invalid
	 * @throws Exception if wrapping the Jar fails
	 */
	public static boolean wrap(File source, Collection<File> classpath, File target, Map<String, String> properties,
			boolean removeSignature) throws Exception {
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
		/*
		 * FIXME Current bndlib versions (at least 3.3-3.5) seem create wrong errors
		 * mentioning that the default package is referred to.
		 */
		wrapper.setProperty(Analyzer.FAIL_OK, "true");
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
				if (wrapper.getErrors() != null && !wrapper.getErrors().isEmpty()) {
					String name = wrapper.getBundleSymbolicName().getKey();
					for (String error : wrapper.getErrors()) {

						System.out.println(
								"Error reported by bundle wrapper for bundle " + name + " is ignored:\n" + error);
					}
				}

				wrapper.getJar().setManifest(m);
				wrapper.save(outputFile, true);
				if (!wrapper.isOk() || !outputFile.exists()) {
					throw new IllegalStateException("Failed creating a wrapped bundle");
				}
			} else {
				throw new IllegalStateException(
						"Failed calculating the manifest for a wrapped bundle: " + wrapper.getErrors());
			}
		} finally {
			wrapper.close();
		}

		return true;
	}

	/**
	 * Creates a {@link Pair} with the {@value Constants#BUNDLE_SYMBOLICNAME} as
	 * first value and a list of imported packages as second value of the given
	 * bundle, but without meta data like version and others .
	 * 
	 * @param bundle {@link File} which is an OSGi bundle
	 * @return {@link Pair} containing the bundle's Bundle-SymbolicName and a list
	 *         of imported packages
	 * @throws Exception
	 */
	public static Pair<String, List<String>> getSymbolicNameAndPackageImports(File bundle) throws Exception {
		Jar jar = null;
		try {
			jar = new Jar(bundle);
			Manifest manifest = jar.getManifest();

			String bundleSymbolicname = manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);

			String importPackages = manifest.getMainAttributes().getValue(Constants.IMPORT_PACKAGE);
			if (null == importPackages) {
				List<String> emptyList = Collections.emptyList();
				return Pair.newInstance(bundleSymbolicname, emptyList);
			}

			Instructions instructions = new Instructions(importPackages);
			List<String> imports = new ArrayList<>(instructions.size());
			Set<Instruction> instructionsKeySet = instructions.keySet();
			for (Instruction instruction : instructionsKeySet) {
				String input = instruction.getInput();
				imports.add(input);
			}

			return Pair.newInstance(bundleSymbolicname, imports);
		} finally {
			if (jar != null) {
				jar.close();
			}
		}
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
