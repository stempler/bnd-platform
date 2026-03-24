package org.standardout.gradle.plugin.platform

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

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
