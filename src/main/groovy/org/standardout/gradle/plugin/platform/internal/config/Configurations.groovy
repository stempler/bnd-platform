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

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact
import org.osgi.framework.Version;
import org.standardout.gradle.plugin.platform.PlatformPluginExtension;
import org.standardout.gradle.plugin.platform.internal.BundleArtifact;
import org.standardout.gradle.plugin.platform.internal.DependencyArtifact;
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;
import org.standardout.gradle.plugin.platform.internal.util.bnd.BundleHelper;
import org.standardout.gradle.plugin.platform.internal.util.groovy.LaxPropertyDecorator;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Descriptors.PackageRef;

/**
 * Bundle configuration index.
 */
class Configurations {
	
	private final Project project
	
	private final Map<File, StoredConfig> fileConfigurations = [:]
	
	private final Map<String, Map<String, Map<String, StoredConfig>>> dependencyConfigurations = [:]
	
	private final List<MergeConfig> merges = []
	
	private final StoredConfig defaultConfiguration
	
	Configurations(Project project) {
		this.project = project
		
		// default bnd configuration for wrapped bundles
		// does not apply to bundles that are already bundles
		def defaultBndConfig = {
			Version v = VersionUtil.toOsgiVersion(version)
			Version vDigits = new Version(v.major, v.minor, v.micro)
			properties[Analyzer.EXPORT_PACKAGE] = "*;version=${vDigits.toString()}" as String
			properties[Analyzer.IMPORT_PACKAGE] = '*'
		}
		defaultConfiguration = new StoredConfigImpl(defaultBndConfig)
	}
	
	void addMerge(MergeConfig merge) {
		merges << merge
	}
	
	void addDefaultConfig(StoredConfig config) {
		defaultConfiguration << config
	}
	
	StoredConfig getDefaultConfig() {
		new UnmodifiableStoredConfig(defaultConfiguration)
	}
	
	/**
	 * Create bundles for the given artifacts.
	 */
	void createBundles(Iterable<BundleArtifact> artifacts, File targetDir) {
		List<List<BundleArtifact>> mergeBuckets = new ArrayList<List<BundleArtifact>>(merges.size())
		List<BundleArtifact> remaining = []
		
		artifacts.each {
			BundleArtifact art ->
			if (!art.isSource()) { // ignore source bundles
				boolean added = false
				def matchAgainst = new LaxPropertyDecorator(art)
				// check for each merge if the bundle is part of the merge
				merges.eachWithIndex {
					MergeConfig merge, int index ->
					// call match closure(s) with artifact
					// any match will result in the bundle being merged
					if (merge.matchClosures.any { it(matchAgainst) }) {
						List<BundleArtifact> bucket = mergeBuckets[index]
						if (bucket == null) {
							bucket = []
							mergeBuckets[index] = bucket
						}
						bucket << art
						added = true
					}
				}
				
				if (!added) {
					remaining << art
				}
			}
		}
		
		// collect all jars for classpath
//		def jarFiles = artifacts.collect {
//			it.file
//		}
		
		// merged bundles
		mergeBuckets.eachWithIndex {
			def bundles, int index ->
			if (bundles) {
				BundleHelper.merge(project, merges[index], bundles, targetDir)
			}
			else {
				project.logger.warn 'No bundles match merge'
			}
		}
		
		// other bundles
		remaining.each {
			BundleArtifact art ->
			BundleHelper.bundle(project, art, targetDir)
		}
	}
	
	/**
	 * Add configuration for a file based dependency.
	 */
	void putConfiguration(File file, StoredConfig config) {
		assert file
		
		StoredConfig current = fileConfigurations[file] 
		if (current != null) {
			// append configuration
			current << config
			project.logger.warn "Multiple bnd configurations for file $file, later definitions may override previous"
		}
		else {
			fileConfigurations[file] = config
		}
	}
	
	/**
	 * Get configuration for a file based dependency.
	 */
	StoredConfig getConfiguration(File file, boolean includeDefaultConfig) {
		StoredConfig result = new StoredConfigImpl()
		
		if (includeDefaultConfig) {
			// add default configuration to configuration
			result << defaultConfig
		}
		
		StoredConfig fileConfig = fileConfigurations[file]
		if (fileConfig) {
			// add file specific configuration
			result << fileConfig
		}
		
		result
	}
	
	/**
	 * Add configuration for a dependency.
	 */
	void putConfiguration(String group = null, String name = null, String version = null, StoredConfig config) {
		assert group || name: 'At least group or name must be specified for bnd configuration'
		
		Map<String, StoredConfig> nameConfig = dependencyConfigurations.get(group, [:]).get(name, [:])
		StoredConfig current = nameConfig.get(version)
		if (current != null) {
			// append configuration
			current << config
			project.logger.warn "Multiple bnd configurations for group:$group, name:$name, version:$version, later definitions may override previous"
		}
		else {
			nameConfig.put(version, config)
		}
	}
	
	/**
	 * Get the (combined) configuration for the given parameters defining a dependency.
	 */
	StoredConfig getConfiguration(String group, String name, String version, boolean includeDefaultConfig,
		DependencyArtifact artifact) {
		final StoredConfig res = new StoredConfigImpl()
		StoredConfig tmp
		
		// fully qualified
		if (group && name && version) {
			tmp = dependencyConfigurations.get(group)?.get(name)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// w/o version
		if (group && name) {
			tmp = dependencyConfigurations.get(group)?.get(name)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
			
		// w/o group
		if (name && version) {
			tmp = dependencyConfigurations.get(null)?.get(name)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// w/o name
		if (group && version) {
			tmp = dependencyConfigurations.get(group)?.get(null)?.get(version)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// only name
		if (name) {
			tmp = dependencyConfigurations.get(null)?.get(name)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		// only group
		if (group) {
			tmp = dependencyConfigurations.get(group)?.get(null)?.get(null)
			if (tmp != null) {
				tmp >> res // prepend configuration
			}
		}
		
		if (includeDefaultConfig) {
			if (artifact != null && project.platform.determineImportVersions) {
				// import package versions based on dependencies
				defaultImports(artifact.getDirectDependencies(project)) >> res
			}
			
			// prepend default configuration
			defaultConfig >> res
		}
		
		res
	}
		
	StoredConfig defaultImports(Iterable<ResolvedArtifact> deps) {
		def importMap = [:]
		
		final Closure defaultStrategy = project.platform.importVersionStrategy
		
		// create a map of package names to version number and strategies
		deps.each {
			ResolvedArtifact dep ->
			if (dep.extension != 'jar' || dep.classifier) {
				return
			}
			
			def group = dep.moduleVersion.id.group
			def name = dep.moduleVersion.id.name
			def version = dep.moduleVersion.id.version
			def strategy = defaultStrategy
			
			// for the default behavior assuming the module version as default import version for the packages
			def osgiVersion = VersionUtil.toOsgiVersion(version)
			
			// check if there is an artifact specific configuration
			ImportsConfig cfg = getConfiguration(group, name, version, false, null)?.importsConfig(project, group, name, version)
			if (cfg != null) {
				strategy = cfg.versionStrategy?:strategy // specific strategy
			}
			
			// determine packages
			Analyzer analyzer = new Analyzer()
			analyzer.setJar(dep.file);
			analyzer.analyze()
			analyzer.getContained().each {
				PackageRef p, Attrs attrs ->
				String pkg = p.FQN
				if (pkg != '.' && !project.platform.importIgnorePackages.contains(pkg)) {
					if (importMap.containsKey(pkg)) {
						// package present multiple times
						project.logger.warn("Package $pkg provided by multiple dependencies, using minimal version")
						Version otherVersion = importMap[pkg][0]
						if (osgiVersion.compareTo(otherVersion) < 0) {
							// replace version
							importMap[pkg][0] = osgiVersion
						}
						// force minimum strategy
						importMap[pkg][1] = PlatformPluginExtension.MINIMUM
					}
					else {
						// add package w/ artifact strategy
						importMap[pkg] = [osgiVersion, strategy]
					} 
				}
			}
		}
		
		if (importMap.empty) {
			// return an empty configuration that doesn't override anything
			return new StoredConfigImpl()
		}
		
		// turn map in map of package instructions and attributes
		importMap = importMap.collectEntries {
			String pkg, def versionAndStrategy ->
			// determine version assignment
			Version osgiVersion = versionAndStrategy[0]
			Closure strategy = versionAndStrategy[1]
			// apply given strategy
			def version = strategy(osgiVersion) as String
			
			// each package import with wildcard and version
			[pkg + '.*', "version=\"$version\""]
		}
				
		// make other imports optional (as they are not provided through dependencies)
		importMap['*'] = 'resolution:=optional'
		
		// sort the map so longer package names come first (because we use wildcards everywhere)
		importMap = importMap.sort {
			a, b -> -(a.key.length() <=> b.key.length())
		}
		
		Closure bndClosure = {
			// overrides any previous Import-Package configuration!
			instruction 'Import-Package', importMap.collect {
				String p, String attrs ->
				if (attrs) {
					"$p;$attrs"
				}
				else {
					p
				}
			}.join(',')
		}
		
		new StoredConfigImpl(bndClosure)
	}

}
