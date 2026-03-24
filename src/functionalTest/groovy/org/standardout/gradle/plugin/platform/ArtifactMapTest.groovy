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

import groovy.json.JsonSlurper

import org.junit.jupiter.api.Test

class ArtifactMapTest extends AbstractFunctionalTest {

	@Test
	void artifactMapGeneratesJson() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('artifactMap')

		def reportFile = new File(projectDir, 'build/bundleArtifactMap.json')
		assertThat(reportFile).as('bundleArtifactMap.json should exist').isFile()

		def json = new JsonSlurper().parse(reportFile)
		assertThat(json).as('JSON should be a non-empty map').isInstanceOf(Map).isNotEmpty()

		def gsonEntry = json.values().find { it.name == 'gson' }
		assertThat(gsonEntry)
			.as('should contain an entry for gson')
			.isNotNull()
		assertThat(gsonEntry.group as String)
			.as('gson group should be correct')
			.isEqualTo('com.google.code.gson')
	}
}
