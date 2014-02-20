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

package org.standardout.gradle.plugin.platform.internal;

import java.io.File;

/**
 * Represents a bundle artifact represented by a single file.
 */
public interface BundleArtifact {
	
	/**
	 * The associated file.
	 */
	File getFile();
	
	/**
	 * The original version.
	 */
	String getVersion();

	/**
	 * If the bundle is a source bundle.	
	 */
	boolean isSource();
	
	/**
	 * The bundle name.
	 */
	String getBundleName();
	
	/**
	 * The bundle symbolic name.
	 */
	String getSymbolicName();
	
	/**
	 * The modified bundle version to use.
	 */
	String getModifiedVersion();
	
	/**
	 * Should the bundle be wrapped using bnd?
	 */
	boolean isWrap();
	
	/**
	 * The reason why it should not be wrapped
	 */
	String getNoWrapReason();
	
	/**
	 * The associated bnd configuration, if any
	 */
	BndConfig getBndConfig();
	
	/**
	 * The unique identifier.
	 */
	String getId();

	/**
	 * The name of the target file.
	 */
	String getTargetFileName();

}
