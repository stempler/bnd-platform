/*
 * Copyright 2026 the original author or authors.
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
package org.standardout.gradle.plugin.platform.internal.util

import groovy.json.JsonOutput
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import java.util.zip.ZipFile

class VersionFile {

	/**
	 * Read an XML file from a Jar/Zip file using XMLSlurper.
	 *
	 * @param jarFile the Jar file the XML is contained in
	 * @param xmlFileName the name of the XML file in the Jar file
	 * @return the parsed XML file or null if the file did not exist
	 */
	private static GPathResult readXmlFromJar(File jarFile, String xmlFileName) {
		def zipFile = new ZipFile(jarFile)
		def entry = zipFile.getEntry(xmlFileName)

		if (entry) {
			zipFile.getInputStream(entry).withStream {
				new XmlSlurper().parse(it)
			}
		} else {
			null
		}
	}

	/**
	 * Create version files for features in a p2 repository.
	 *
	 * @param updateSiteDir the location of the p2 repository
	 */
	static def createFeatureVersionFiles(File updateSiteDir) {
		//TODO delete any previously existing version files?

		GPathResult artifactsXml

		def artifactsJar = new File(updateSiteDir, 'artifacts.jar')
		if (artifactsJar.exists()) {
			artifactsXml = readXmlFromJar(artifactsJar, 'artifacts.xml')
		}

		if (artifactsXml == null) {
			def artifactsXmlFile = new File(updateSiteDir, 'artifacts.xml')
			artifactsXml = new XmlSlurper().parse(artifactsXmlFile)
		}

		if (artifactsXml != null) {
			createFeatureVersionFiles(updateSiteDir, artifactsXml)
		}
	}

	static def createFeatureVersionFiles(File updateSiteDir, GPathResult artifactsXml) {
		def featureVersions = collectFeatureVersions(artifactsXml)

		featureVersions.forEach { featureId, versions ->
			def versionFile = new File(updateSiteDir, "${featureId}_versions.json")
			writeVersionFile(versionFile, versions)
		}
	}

	static Map<String, List<String>> collectFeatureVersions(GPathResult artifactsXml) {
		def features = artifactsXml.artifacts.artifact.findAll{ a -> a.@classifier == 'org.eclipse.update.feature' }

		features
			.findResults { feature ->
				def id = feature.@id as String
				def version = feature.@version as String
				if (id && version) {
					[id: id, version: version]
				}
				else {
					null
				}
			}
			.groupBy { it.id }
			.collectEntries { id, objects ->
				[
					id,
					objects.collect { obj ->
						obj.version
					}
				]
			}
	}

	/**
	 * Write a version file with the given versions.
	 * The format used is a Json file that is compatible with custom datasources in Renovate.
	 * See https://docs.renovatebot.com/modules/datasource/custom/#usage
	 *
	 * @param versionFile the file to write
	 * @param versions the versions
	 */
	static void writeVersionFile(File versionFile, List<String> versions) {
		versionFile.text = JsonOutput.toJson([releases: versions.collect { [version: it] }])
	}
}
