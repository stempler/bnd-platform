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

package org.standardout.gradle.plugin.platform.internal.util

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.standardout.gradle.plugin.platform.internal.BundleArtifact
import org.standardout.gradle.plugin.platform.internal.Feature

class FeatureUtil {
	
	static void createFeatureXml(Feature feature, File target) {
		target.parentFile.mkdirs()
		
		target.withWriter('UTF-8'){ Writer w ->
			createFeatureXml(feature, w)
		}
	}
	
	static void createFeatureXml(Feature feature, OutputStream target) {
		Writer w = target.newWriter('UTF-8')
		createFeatureXml(feature, w)
	}
	
	static void createFeatureXml(Feature feature, Writer target) {
		def xml = new groovy.xml.MarkupBuilder(target)
		xml.setDoubleQuotes(true)
		xml.mkp.xmlDeclaration(version:'1.0', encoding: 'UTF-8')
		
		xml.feature(
			id: feature.id,
			label: feature.label,
			version: feature.version,
			'provider-name': feature.providerName
			// plugin: branding_plugin_id
		) {
			// included features
			for (Feature included : feature.includedFeatures) {
				def version = included.version?:'0.0.0'
				includes(id: included.id, version: version)
			}
		
			// included bundles
			for (BundleArtifact artifact : feature.bundles) {
				// define each plug-in
				plugin(
					id: artifact.symbolicName,
					'download-size': 0,
					'install-size': 0,
					version: artifact.modifiedVersion,
					unpack: false)
			}
		}
	}
	
	static void createJar(Feature feature, def jarFile) {
		File target = jarFile as File
		target.parentFile.mkdirs()
		
		// create feature jar
		target.withOutputStream {
			ZipOutputStream zipStream = new ZipOutputStream(it)
			zipStream.putNextEntry(new ZipEntry('feature.xml'))
			createFeatureXml(feature, zipStream)
			zipStream.closeEntry()
			zipStream.close()
		}
	}

}
