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

import java.util.jar.JarFile
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.osgi.framework.Constants
import org.osgi.framework.Version
import org.standardout.gradle.plugin.platform.internal.config.BndConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfig;
import org.standardout.gradle.plugin.platform.internal.config.StoredConfigImpl;
import org.standardout.gradle.plugin.platform.internal.config.UnmodifiableStoredConfig;
import org.standardout.gradle.plugin.platform.internal.util.bnd.JarInfo;
import org.standardout.gradle.plugin.platform.internal.util.gradle.DependencyHelper;


class ResolvedBundleArtifact implements BundleArtifact {
	
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
	
	/**
	 * Create a bundle artifact from a resolved artifact.
	 */
	ResolvedBundleArtifact(ResolvedArtifact artifact, Project project) {
		// extract information from artifact
		this.file = artifact.file
		this.classifier = artifact.classifier
		this.extension = artifact.extension
		this.group = artifact.moduleVersion.id.group
		this.name = artifact.moduleVersion.id.name
		this.version = artifact.moduleVersion.id.version
		
		// derived information
		
		// is this a source bundle
		source = artifact.classifier == 'sources'

		// bundle and symbolic name
		def bundleName = group + '.' + name
		def symbolicName = getDefaultSymbolicName(file, group, name)
				
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
			if (jarInfo.symbolicName) {
				// assume it's already a bundle
				wrap = false
				noWrapReason = 'jar already constains OSGi manifest entries'

				// determine bundle names
				symbolicName = jarInfo.symbolicName
				bundleName = jarInfo.bundleName
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
		Version osgiVersion
		try {
			osgiVersion = Version.parseVersion(version)
		} catch (NumberFormatException e) {
			// try again with version stripped of anything but dots and digits
		
			Matcher matcher = version =~/^(\d+)(\.(\d+))?(\.(\d+))?(\.|-)?(.*)$/
			def match = matcher[0]
			if (match) {
				osgiVersion = new Version(
					match[1] as int,
					(match[3] as Integer)?:0,
					(match[5] as Integer)?:0,
					match[7])
			}
			else {
				String strippedVersion = version.replaceAll(/[^0-9\.]/, '')
				osgiVersion = Version.parseVersion(strippedVersion)
			}
			project.logger.warn "Replacing illegal OSGi version $version by ${osgiVersion.toString()} for artifact $name"
		}
		
		// an eventually modified version
		def modifiedVersion = osgiVersion.toString()
		if (wrap) {
			// if the bundle is wrapped, create a modified version to mark this
			def qualifier = osgiVersion.qualifier
			if (qualifier) {
				qualifier += '-autowrapped'
			}
			else {
				qualifier = 'autowrapped'
			}
			Version mv = new Version(osgiVersion.major, osgiVersion.minor, osgiVersion.micro, qualifier)
			modifiedVersion = mv.toString()
		}
		
		// resolve bundle configuration
		StoredConfig config = new StoredConfigImpl()
		// only include default configuration if not yet a bundle
		StoredConfig bundleConfig = project.platform.configurations.getConfiguration(group, name, version, wrap)
		config << bundleConfig
		
		// determine additional configuration from information in POM
		StoredConfig pomConfig = null
		if (!source) {
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
		
		bndConfig = config.evaluate(project, group, name, modifiedVersion, file, jarInfo?.instructions)
		if (bndConfig) {
			if (!wrap && !source) {
				wrap = true // must be wrapped to apply configuration
				if (bundleConfig != null && !bundleConfig.empty) {
					project.logger.warn "Bnd configuration found for existing bundle $symbolicName, so it is wrapped even though a bundle manifest seems to be already present"
				}
				else {
					project.logger.warn "Existing bundle $symbolicName will be augmented with additional information from the POM"
				}
			}
			
			// override symbolic name or bundle name
			if (bndConfig.symbolicName) {
				symbolicName = bndConfig.symbolicName
			}
			if (bndConfig.bundleName) {
				bundleName = bndConfig.bundleName
			}
			if (bndConfig.version && bndConfig.version != modifiedVersion) {
				modifiedVersion = bndConfig.version
			}
		}
		
		this.modifiedVersion = modifiedVersion
		
		this.bundleName = bundleName
		this.symbolicName = symbolicName
		this.wrap = wrap
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
					if (organization) {
						instruction 'Bundle-Vendor', organization
					}
					if (licenseStrings) {
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
		File pomFile = DependencyHelper.getDetachedDependency(project, pom, 'pom')
		
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
