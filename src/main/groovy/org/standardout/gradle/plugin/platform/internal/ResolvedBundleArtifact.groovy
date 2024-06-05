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

package org.standardout.gradle.plugin.platform.internal

import groovy.util.slurpersupport.GPathResult;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.jar.JarFile
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Adler32;

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.osgi.framework.Constants
import org.osgi.framework.Version
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfigImpl;
import org.standardout.gradle.plugin.platform.internal.config.UnmodifiableStoredConfig;
import org.standardout.gradle.plugin.platform.internal.util.VersionUtil;
import org.standardout.gradle.plugin.platform.internal.util.bnd.JarInfo;
import org.standardout.gradle.plugin.platform.internal.util.gradle.DependencyHelper;

import aQute.bnd.osgi.Analyzer;


class ResolvedBundleArtifact implements BundleArtifact, DependencyArtifact {
	
	private final File file
	File getFile() { file }
	
	final String classifier
	
	final String extension
	
	final String group
	
	final String name
	
	final PomInfo pomInfo
	
	BundleArtifact sourceBundle
	
	private String version
	String getVersion() { version }
	
	private final String os
	@Override public String getOs() { os }
	
	private final String arch
	@Override public String getArch() { arch }
	
	private final String ws
	@Override public String getWs() { ws }
	
	private final boolean source
	boolean isSource() { source }
	
	private final String bundleName
	String getBundleName() { bundleName }
	
	private final String symbolicName
	String getSymbolicName() { symbolicName }
	
	/**
	 * Should the bundle be wrapped?
	 */
	private final boolean wrap
	boolean isWrap() { wrap }
	
	private final String noWrapReason
	String getNoWrapReason() { noWrapReason }
	
	private final BndConfig bndConfig
	BndConfig getBndConfig() { bndConfig }
	
	final String unifiedName
	
	private final String id
	String getId() { id }
	
	private final String modifiedVersion
	String getModifiedVersion() { modifiedVersion }
	
	String getTargetFileName() {
		"${getSymbolicName()}_${getModifiedVersion()}.$extension"
	}
	
	private final ResolvedDependency dependency
	ResolvedDependency getDependency() {
		dependency
	}
	
	private final ResolvedArtifact artifact
	ResolvedArtifact getArtifact() {
		artifact
	}
	
	/**
	 * Create a bundle artifact from a resolved artifact.
	 */
	ResolvedBundleArtifact(ResolvedArtifact artifact, ResolvedDependency dependency,
			Project project, final boolean aux = false) {
		this.dependency = dependency
		this.artifact = artifact
		// extract information from artifact
		this.file = artifact.file
		this.classifier = artifact.classifier
		this.extension = artifact.extension
		this.group = artifact.moduleVersion.id.group
		this.name = artifact.moduleVersion.id.name
		this.version = artifact.moduleVersion.id.version
		def bundleVersion = this.version
		
		// derived information
		
		// is this a source bundle
		source = artifact.classifier == 'sources'

		// bundle and symbolic name
		def bundleName = group + '.' + name
		def symbolicName = getDefaultSymbolicName(file, group, name)
		if (!source && classifier) {
			// avoid collision for artifacts with same name, group and version,
			// but different classifier
			symbolicName += '.' + classifier
		}
		
		// Platform-Filter (if available)
		String platformFilter = null
				
		// reason why a bundle is not wrapped
		JarInfo jarInfo = null
		boolean wrap
		if (source || extension != 'jar') {
			// never wrap
			wrap = false
			noWrapReason = 'artifact type not supported'
			if (source) {
				symbolicName += '.source'
				bundleName += ' Sources'
			}
		}
		else {
			// check if already a bundle
			jarInfo = new JarInfo(file)
			if (jarInfo.symbolicName && jarInfo.version) {
				// assume it's already a bundle
				wrap = false
				noWrapReason = 'jar already constains OSGi manifest entries'

				// determine bundle names
				symbolicName = jarInfo.symbolicName
				bundleName = jarInfo.bundleName
				// ... and version
				bundleVersion = jarInfo.version
			}
			else {
				// not a bundle yet
				wrap = true
				noWrapReason = ''
			}
		}
		
		// the unified name (that is equal for corresponding source and normal jars)
		// it also is the key for the bundle dependency (if any)
		def unifiedName = "$group:$name:$version"
		// the qualified id (including classifier, unique)
		if (classifier) {
			id = unifiedName + ":$classifier"
		}
		else {
			id = unifiedName
		}
		this.unifiedName = unifiedName
		
		// determine osgi version
		Version osgiVersion = VersionUtil.toOsgiVersion(bundleVersion) {
			project.logger.warn "Replacing illegal OSGi version $bundleVersion by ${it} for artifact $name"
		}
		
		// resolve bundle configuration
		StoredConfig config = new StoredConfigImpl()
		// only include default configuration if not yet a bundle
		StoredConfig bundleConfig = project.platform.configurations.getConfiguration(group, name, version, classifier, wrap,
			this)
		config << bundleConfig
		
		// determine additional configuration from information in POM
		StoredConfig pomConfig = null
		if (!source && project.platform.extractPomInformation) {
			pomInfo = extractPomInfo(group: group, name: name, version: version, project)
			if (pomInfo) {
				pomConfig = pomInfo.toStoredConfig()
				if (pomConfig) {
					// prepend configuration
					pomConfig >> config
				}
			} 
		}
		else {
			pomInfo = null
		}
		
		// an eventually modified version
		def modifiedVersion = osgiVersion.toString()
		// a qualifier to add
		boolean addQualifier = false
		
		bndConfig = config.evaluate(project, group, name, modifiedVersion, file, jarInfo?.instructions)
		if (bndConfig) {
			if (!wrap && !source) {
				wrap = true // must be wrapped to apply configuration
				if (bundleConfig != null && !bundleConfig.bndClosures.empty) {
					project.logger.warn "Bnd configuration found for existing bundle $symbolicName, so it is wrapped even though a bundle manifest seems to be already present"
				}
				else {
					project.logger.warn "Existing bundle $symbolicName may be augmented with additional information from the POM"
				}
			}
			
			// override symbolic name or bundle name
			if (bndConfig.symbolicName) {
				symbolicName = JarInfo.extractSymbolicName(bndConfig.symbolicName) // stripped symbolic name
			}
			if (bndConfig.bundleName) {
				bundleName = bndConfig.bundleName
			}
			if (bndConfig.version && bndConfig.version != modifiedVersion) {
				def bndOsgiVersion = VersionUtil.toOsgiVersion(bndConfig.version)
				if (bndOsgiVersion) {
					modifiedVersion = bndConfig.version
					osgiVersion = bndOsgiVersion
				}
			}
			
			addQualifier = !source // by default don't add qualifiers for source bundles
			
			platformFilter = bndConfig.getInstruction('Eclipse-PlatformFilter')
		}
		else if (wrap) {
			addQualifier = !source // by default don't add qualifiers for source bundles
		}
		if (bndConfig?.addQualifier) {
			addQualifier = true // forced qualifier
		}
		
		if (addQualifier) {
			modifiedVersion = VersionUtil.addQualifier(modifiedVersion, symbolicName, bndConfig, project)
		}
		
		// adapt symbolic names for bundles in platformAux (if enabled)
		if (aux && project.platform.auxVersionedSymbolicNames) {
			symbolicName = symbolicName + '-' + version // augment with (original) version
			wrap = true // force wrap
		}
		
		// Extract target platform constraints if present
		if(platformFilter) {
			ws = (platformFilter =~ /.*\(osgi\.ws\=(.*?)\).*/)[ 0 ][ 1 ]
			os = (platformFilter =~ /.*\(osgi\.os\=(.*?)\).*/)[ 0 ][ 1 ]
			arch = (platformFilter =~ /.*\(osgi\.arch\=(.*?)\).*/)[ 0 ][ 1 ]
		}
		
		this.modifiedVersion = modifiedVersion
		
		this.bundleName = bundleName
		this.symbolicName = symbolicName
		this.wrap = wrap
	}
	
	@Override
	public Iterable<ResolvedDependency> getRepresentedDependencies() {
		dependency == null ? [] : [dependency]
	}

	@Override
	public Set<ResolvedArtifact> getDirectDependencies(Project project) {
		/*
		 * Get the direct dependencies that represent the original dependencies,
		 * not the resolved platform, as we want package imports to be based on
		 * the original versions.
		 */
		DependencyHelper.getDirectDependencies(project, id)
	}

	private static String getDefaultSymbolicName(File file, String group, String name) {
		int i = group.lastIndexOf('.')
		if (i < 0) {
			if (group == name) {
				name
			}
			else {
				group + '.' + name
			}
		}
		else {
			String lastSection = group.substring( ++i );
			if (name == lastSection) {
				group
			}
			else if (name.startsWith(lastSection)) {
				String id = name.substring(lastSection.length())
				if (Character.isLetterOrDigit(id.charAt(0)))
				{
					group + '.' + id
				}
				else
				{
					group + '.' + id.substring(1)
				}
			}
			else {
				group + '.' + name
			}
		}
	}
	
	/**
	 * Represents license information retrieved from a POM file.
	 */
	public static class LicenseInfo {
		LicenseInfo(String licenseName, String licenseUrl) {
			this.licenseName = licenseName
			this.licenseUrl = licenseUrl
		}
		final String licenseName
		final String licenseUrl
	}
	
	/**
	 * Represents information retrieved from a POM file.
	 */
	public static class PomInfo {
		final List<LicenseInfo> licenses = []
		String organization
		
		boolean isEmpty() {
			licenses.empty && !organization
		}
		
		/**
		 * Convert to stored configuration.
		 * @return the represented configuration or <code>null</code>
		 */
		StoredConfig toStoredConfig() {
			if (empty) {
				null
			}
			else {
				def licenseStrings = []
				licenses.each {
					LicenseInfo license ->
					if (license.licenseUrl) {
						if (license.licenseName) {
							licenseStrings << "${license.licenseUrl};description=\"${license.licenseName}\""
						}
						else {
							licenseStrings << license.licenseUrl
						}
					}
					else if (license.licenseName) {
						licenseStrings << license.licenseName
					}
				}
				
				def bndClosure = {
					if (organization && !properties['Bundle-Vendor']) {
						instruction 'Bundle-Vendor', organization
					}
					if (licenseStrings && !properties['Bundle-License']) {
						instruction 'Bundle-License', licenseStrings.join(',')
					}
				}
				new UnmodifiableStoredConfig(new StoredConfigImpl(bndClosure))
			}
		}
	}
	
	/**
	 * Extract information from the POM file of the given dependency.
	 */
	private static PomInfo extractPomInfo(Map dependencyNotation, Project project) {
		String pom = "${dependencyNotation.group}:${dependencyNotation.name}:${dependencyNotation.version}@pom"
		File pomFile = null
		try {
			pomFile = DependencyHelper.getDetachedDependency(project, pom, 'pom')
		} catch (e) {
			project.logger.warn "Could not retrieve POM $pom"
		}
		
		PomInfo result = new PomInfo()
		if (pomFile) {
			def xml = new XmlSlurper().parse(pomFile)
	
	        xml.licenses.license.each {
	            def license = new LicenseInfo(it.name.text().trim(), it.url.text().trim())
	            result.licenses << license
	        }
			
			def orgName = xml.organization.name.find()
			if (orgName) {
				result.organization = orgName.text().trim()
			}
		}
		
		result
	}

}
