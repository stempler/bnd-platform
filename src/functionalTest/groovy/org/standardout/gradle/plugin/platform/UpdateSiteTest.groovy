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
package org.standardout.gradle.plugin.platform

import static org.assertj.core.api.Assertions.assertThat

import groovy.xml.XmlSlurper

import java.util.zip.ZipFile

import org.junit.jupiter.api.Test

class UpdateSiteTest extends AbstractFunctionalTest {

	@Test
	void updateSiteProducesP2RepositoryAndZip() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('updateSite')
		runTask('updateSiteZip')

		def updateSiteDir = new File(projectDir, 'build/updatesite')
		def pluginsOut    = new File(updateSiteDir, 'plugins')
		def featuresOut   = new File(updateSiteDir, 'features')
		def updateSiteZip = new File(projectDir, 'build/updatesite.zip')

		assertThat(updateSiteDir).as('build/updatesite should exist').isDirectory()
		assertThat(new File(updateSiteDir, 'artifacts.jar').exists()
			|| new File(updateSiteDir, 'artifacts.xml').exists())
			.as('artifacts.jar or artifacts.xml should exist').isTrue()
		def contentJar = new File(updateSiteDir, 'content.jar')
		def contentXml = new File(updateSiteDir, 'content.xml')
		assertThat(contentJar.exists() || contentXml.exists())
			.as('content.jar or content.xml should exist').isTrue()

		assertThat(pluginsOut).as('plugins/ should exist').isDirectory()
		assertThat(featuresOut).as('features/ should exist').isDirectory()
		assertThat(pluginsOut.listFiles()?.find {
			it.name.startsWith('com.google.gson_') && it.name.endsWith('.jar')
		}).as('gson bundle JAR in plugins/').isNotNull()
		assertThat(featuresOut.listFiles()?.find {
			it.name.startsWith('platform.feature_') && it.name.endsWith('.jar')
		}).as('platform.feature JAR in features/').isNotNull()

		def contentXmlText = contentJar.exists()
			? new ZipFile(contentJar).withCloseable { zip ->
				zip.getInputStream(zip.getEntry('content.xml')).getText('UTF-8')
			}
			: contentXml.text
		def unitIds = new XmlSlurper().parseText(contentXmlText)
			.'**'.findAll { it.name() == 'unit' }*.@id*.text() as Set
		assertThat(unitIds)
			.as('content.xml should advertise the gson bundle IU')
			.contains('com.google.gson')
		assertThat(unitIds)
			.as('content.xml should advertise the platform feature group IU')
			.contains('platform.feature.feature.group')

		assertThat(updateSiteZip).as('build/updatesite.zip should exist').isFile()
		new ZipFile(updateSiteZip).withCloseable { zip ->
			def names = zip.entries().collect { it.name } as Set
			assertThat(names).anyMatch { it == 'artifacts.jar' || it == 'artifacts.xml' }
			assertThat(names).anyMatch { it == 'content.jar'   || it == 'content.xml' }
			assertThat(names).anyMatch { it.startsWith('plugins/com.google.gson_') }
			assertThat(names).anyMatch { it.startsWith('features/platform.feature_') }
		}
	}
}
