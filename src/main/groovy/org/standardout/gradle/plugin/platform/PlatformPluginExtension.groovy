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

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.zip.Adler32;

import groovy.lang.Closure;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.osgi.framework.Version;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.Feature
import org.standardout.gradle.plugin.platform.internal.config.ArtifactFeature;
import org.standardout.gradle.plugin.platform.internal.config.BundleDependency;
import org.standardout.gradle.plugin.platform.internal.config.Configurations;
import org.standardout.gradle.plugin.platform.internal.config.MergeConfig;
import org.standardout.gradle.plugin.platform.internal.config.SourceFeature;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfigImpl
import org.standardout.gradle.plugin.platform.internal.util.gradle.DependencyHelper;
import org.standardout.gradle.plugin.platform.internal.util.gradle.DummyDependency

/**
 * Extension for the platform plugin.
 *  
 * @author Simon Templer
 */
class PlatformPluginExtension {
	/**
	 * Version strategy that uses no version constraint for imports.
	 */
	public static final Closure NONE = {
		Version v ->
		null
	}
	/**
	 * Version strategy that uses the given version as minimum version.
	 */
	public static final Closure MINIMUM = {
		Version v ->
		"${v.major}.${v.minor}"
	}
	/**
	 * Version strategy that requires a minimum version and extends
	 * to (not including) the next major version.
	 */
	public static final Closure MAJOR = {
		Version v ->
		def min = MINIMUM.call(v)
		"[${min},${v.major + 1}.0)"
	}
	/**
	 * Version strategy that requires a minimum version and extends
	 * to (not including) the next minor version.
	 */
	public static final Closure MINOR = {
		Version v ->
		def min = MINIMUM.call(v)
		"[${min},${v.major}.${v.minor + 1})"
	}
	
	/**
	 * Hash calculator using the Adler32 checksum algorithm.
	 */
	public static final Closure ADLER32 = {
		String config ->
		// calculate the checksum
		def adler = new Adler32()
		adler.update(config.bytes)
		// only return the last 4 bytes (as it's 32 bit only)
		def bb = ByteBuffer.allocate(8).putLong(adler.value)
		def bytes = new byte[4]
		for (i in 0..3) {
			bytes[i] = bb.get(i + 4)
		}
		bytes
	}
	
	PlatformPluginExtension(Project project) {
		this.project = project
		this.configurations = new Configurations(project)
		
		// update site directory default
		updateSiteDir = new File(project.buildDir, 'updatesite')
		
		// update site zip default
		updateSiteZipFile = new File(project.buildDir, 'updatesite.zip')
		
		importIgnorePackages = new HashSet<String>()
		importIgnorePackages << 'javax'
		importIgnorePackages << 'java'
		importIgnorePackages << 'license'
	}
	
	final Project project
	
	/**
	 * States if additional configuration information from POM is desired.
	 */
	boolean extractPomInformation = true

	/**
	 * States if source for external dependencies should be fetched and corresponding source bundles created. 
	 */
	boolean fetchSources = true
	
	/**
	 * States if the package import versions for automatically wrapped bundles should be determined automatically.
	 * This also will by default make package imports optional that are not found in dependencies.
	 */
	boolean determineImportVersions = false
	
	/**
	 * States if bnd-platform specific manifest headers should be added.
	 * Currently those headers serve the following purposes:
	 * <ul>
	 *   <li>Reconstruct the original maven artifact group, name and version</li>
	 * </ul>
	 */
	boolean addBndPlatformManifestHeaders = false
	
	/**
	 * Defines the global import version strategy.
	 * 
	 * The strategy is a closure taking an OSGi version number and returning a version assignment for bnd as String.
	 */
	Closure importVersionStrategy = MAJOR
	
	/**
	 * Packages to ignore when analyzing packages of dependencies to determine
	 * package import version numbers.
	 */
	final Set<String> importIgnorePackages

	/**
	 * The default version qualifier to use for wrapped bundles. If a qualifier is already
	 * present the default will be appended, separated by a dash.
	 * 
	 * Please note that this does not apply to file based dependencies automatically
	 * (otherwise it might mess with existing source bundle associations). Set addQualifier
	 * to <code>true</code> in the corresponding bnd configuration to enable it for a
	 * file based dependency. 
	 */
	String defaultQualifier = 'autowrapped'
		
	/**
	 * States if bundle version qualifiers should be tagged with hashes calculated from the
	 * bnd configuration. Overrides the default qualifier for bundles where a bnd configuration
	 * is present.
	 * 
	 * Please note that this does not apply to file based dependencies (otherwise it might
	 * mess with existing source bundle associations).
	 */
	boolean useBndHashQualifiers = true
	
	/**
	 * States if for features a qualifier should be added based on the included plugins
	 * and features. This serves to prevent caching issues when there are minor changes
	 * to a feature. 
	 */
	boolean useFeatureHashQualifiers = true
	
	/**
	 * Map that is used to have reproducable increasing version numbers for bundles and features
	 * when using hash qualifiers. The hash qualifiers are mapped to a date and time based qualifier
	 * to ensure that changes to bundles and features that have the same version number are not
	 * only reflected in the qualifier but also produce versions that are interpreted as newer
	 * than the previous versions in the OSGi sense.
	 * 
	 * The map needs to be persisted, thus it may be specified as
	 * <ul>
	 *   <li>as String or File specifying the file location the map is persisted to / from (Json file)</li>
	 *   <li>a class implementing the VersionQualifierMap interface</li>
	 * </ul>
	 */
	def hashQualifierMap
	
	// values for default qualifier map base date
	public static final int YEAR = 0
	public static final int MONTH = 1
	public static final int DAY = 2
	public static final int MINUTE = 3
	public static final int SECOND = 4
	public static final int MILLISECOND = 5
	
	/**
	 * Default qualifier map configuration.
	 */
	def defaultQualifierMap = [
		/**
		 * Prefix for qualifiers generated by the default qualifier map.
		 */
		prefix: 'i',
		
		/**
		 * The default qualifier map tries to assign a time based qualifier. Through this method you
		 * can indicate what should be the format of the shortest qualifier that is assigned.
		 *
		 * The levels are
		 * <ul>
		 *   <li><code>YEAR</code></li>
		 *   <li><code>MONTH</code></li>
		 *   <li><code>DAY</code></li>
		 *   <li><code>MINUTE</code></li>
		 *   <li><code>SECOND</code></li>
		 *   <li><code>MILLISECOND</code></li>
		 * </ul>
		 */
		baseDate: MONTH,
		
		/**
		 * Provides a fixed pattern for formatting the current date for use as part of the qualifier.
		 * Provide the pattern in a form suitable for SimpleDataFormat that ensures that the order of
		 * those dates as String is the same as the date order (e.g. 'yyyyMMddHHmm').
		 *
		 * Usually, using an adapted {@link #defaultQualifierStartLevel} is preferred.
		 */
		fixedDatePattern: null
	]
	
	/**
	 * Defines the hash calculator used for calculating hash qualifiers from bnd configuration.
	 * 
	 * The closure takes a String and should return the hash as byte array. The qualifier will then be
	 * the base 64 encoded hash.
	 */
	Closure hashCalculator = ADLER32
	
	/**
	 * States if the symbolic names for bundles created via the platformaux configuration should
	 * be adapted to include the version number. This is useful when dealing with systems that have
	 * problems when there actually are bundles with the same name but different versions.
	 * 
	 * An example is Eclipse RCP plugin-based products - they can include only one version of a bundle
	 * with the same name.
	 */
	boolean auxVersionedSymbolicNames = false
	
	/**
	 * States if signatures should be removed from wrapped bundles.
	 */
	boolean removeSignaturesFromWrappedBundles = true
	
	/**
	 * States if a general feature should be created. By default it's turned on and called Generated platform feature.
	 * In case custom features are generated you might not want to have an additional "generated platform feature"
	 * besides your own features.
	 */
	boolean generatePlatformFeature = true
	
	/**
	 * The ID for the platform feature.
	 */
	String featureId = 'platform.feature'
	
	/**
	 * The name for the platform feature.
	 */
	String featureName = 'Generated platform feature'
	
	/**
	 * Feature provider name.
	 */
	String featureProvider = 'Generated with bnd-platform'
	
	/**
	 * The platform feature version, defaults to the project version if possible, otherwise to 1.0.0.
	 */
	String featureVersion
	
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
	 * The target file for the 'updateSiteZip' task.
	 */
	File updateSiteZipFile
	
	/**
	 * The directory of a local Eclipse installation. If none is specified the
	 * <code>ECLIPSE_HOME</code> system property is checked, if it is not given as
	 * well it is tried to download Eclipse based on the URLs defined in {@link #eclipseMirror}
	 */
	File eclipseHome

	/**
	 * The directory of a JDK installation to used for running Equinox Publisher. If none is specified,
	 * the `java` binary available on the path is used.
	 */
	File javaHome

	/**
	 * The directory to store the downloaded Eclipse installation on local, 
	 * this works if <code>eclipseHome</code> is not specified.
	 * Default to <code>project.buildDir/eclipse-downloads</code>.
	 */
	File downloadsDir

	/**
	 * 4.8 MB *.tar.gz file, which contains the minimal setup of eclipse plug-ins,
	 * which are necessary to build an eclipse p2 update site.
	 */
	def eclipseMirror = 'http://dl.bintray.com/simon-scholz/eclipse-apps/eclipse-p2-minimal.tar.gz'
	
	/**
	 * Call feature to create a feature configuration.
	 * 
	 * @param featureNotation feature notation is either a feature ID (String) or a map
	 *   containing with at least the <code>id</code> key set to the desired feature ID.
	 *   Optional additional keys are <code>name</code> (the human readable feature name),
	 *   <code>version</code> (the feature version, default is the main feature version)
	 *   and <code>provider</code> (the feature provider name)
	 * @param featureClosure the closure configuring the feature content
	 */
	def feature(def featureNotation, Closure featureClosure) {
		// create the feature
		def feature = new ArtifactFeature(
			project,
			featureNotation,
			featureClosure
		)
		
		if (fetchSources) {
			// also create a source feature
			def sourceFeature = new SourceFeature(feature, project)
			features[sourceFeature.id] = sourceFeature
		}
		
		feature
	}
	
	/**
	 * Call bundle to add a dependency.
	 * 
	 * @param dependencyNotation a dependency notation as supported by Gradle
	 * @param configClosure a closure that performs dependency configuration
	 * @return
	 */
	def bundle(def dependencyNotation, Closure configClosure = null) {
		new BundleDependency(
			project,
			dependencyNotation,
			configClosure,
			true // create dependency
		)
	}
	
	/**
	 * Call to configure the behaviour for other bundles importing a given dependency.
	 * 
	 * @param dependencyNotation the dependency notation
	 * @param importsClosure the imports configuration closure
	 * @return
	 */
	void imports(def dependencyNotation, Closure importsClosure) {
		StoredConfigImpl config = new StoredConfigImpl()
		config.importsClosures << importsClosure
		
		// create detached dependency
		Dependency dependency
		if (dependencyNotation instanceof Map) {
			dependency = new DummyDependency(dependencyNotation)
		}
		else {
			dependency = project.dependencies.create(dependencyNotation)
		}
		
		// save dependency configuration
		project.platform.configurations.putConfiguration(
			dependency.group,
			dependency.name,
			dependency.version,
			DependencyHelper.getClassifier(dependency),
			config)
	}
	
	/**
	 * Call bnd to configure artifact, but don't add as dependency.
	 *
	 * @param dependencyNotation a dependency notation as supported by Gradle
	 * @param bndClosure a closure that specifying the custom bnd configuration
	 * @return
	 */
	def bnd(def dependencyNotation, Closure bndClosure) {
		new BundleDependency(
			project,
			dependencyNotation,
			bndClosure,
			false // don't create dependency
		)
	}
	
	/**
	 * Call bnd to extend/overwrite the default bnd configuration for all bundles.
	 * Note that the default configuration does not apply to Jars that already were bundles.
	 */
	def bnd(Closure bndClosure) {
		// warn as the user should be able to check if this is intended
		project.logger.warn 'Adding custom configuration to default bnd configuration'
		if (bndClosure != null) {
			configurations.addDefaultConfig(new StoredConfigImpl(bndClosure))
		}
	}
	
	/**
	 * Call to override the behavior for all created bundles, even existing bundles.
	 * Use with care. 
	 */
	def override(Closure bndClosure) {
		// warn as the user should be able to check if this is intended
		project.logger.warn 'Adding custom configuration to bnd override configuration'
		if (bndClosure != null) {
			configurations.addOverrideConfig(new StoredConfigImpl(bndClosure))
		}
	}
	
	/**
	 * Call merge to create bundle that is merged from different dependencies.
	 * @param mergeClosure the merge closure, specifying a match and bnd configuration
	 * @return
	 */
	def merge(Map<String, Object> properties = [failOnDuplicate: true, collectServices: true], Closure mergeClosure) {
		MergeConfig config = new MergeConfig(project, properties, mergeClosure)
		configurations.addMerge(config)
		config
	}
	
	// for internal use
	
	/**
	 * Stores bnd configurations.
	 */
	final Configurations configurations
	
	/**
	 * Maps artifact IDs to {@link BundleArtifact}s
	 */
	final Map<String, BundleArtifact> artifacts = [:]
	
	/**
	 * Maps feature IDs to Features
	 */
	final Map<String, Feature> features = [:]
}