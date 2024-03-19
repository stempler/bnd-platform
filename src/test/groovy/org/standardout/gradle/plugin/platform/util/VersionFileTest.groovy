package org.standardout.gradle.plugin.platform.util

import groovy.xml.XmlSlurper
import org.junit.jupiter.api.Test
import org.standardout.gradle.plugin.platform.internal.util.VersionFile

import static org.assertj.core.api.Assertions.*

class VersionFileTest {

	@Test
	void testCollectFeatureVersions() {
		def xml = getClass().getClassLoader().getResourceAsStream('artifacts-xml/example.xml').withStream {
			new XmlSlurper().parse(it)
		}

		def featureName = 'to.wetransform.offlineresources.feature'

		def versions = VersionFile.collectFeatureVersions(xml)

		assertThat(versions)
			.as('should have exactly one feature')
			.hasSize(1)
			.containsOnlyKeys(featureName)
			.extractingByKey(featureName)
			.asList()
			.hasSize(2) // 2 versions expected
			.containsExactly('2024.3.15.bnd-Ia6g4Q', '2024.3.18.bnd-tNmhEg')
	}

	@Test
	void testCollectFeatureVersionsEmpty() {
		def xml = new XmlSlurper().parseText('<repository />')

		def versions = VersionFile.collectFeatureVersions(xml)

		assertThat(versions)
			.isEmpty()
	}

}
