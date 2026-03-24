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
package org.standardout.gradle.plugin.platform.util

import static org.assertj.core.api.Assertions.*

import groovy.xml.XmlSlurper

import org.junit.jupiter.api.Test
import org.standardout.gradle.plugin.platform.internal.util.VersionFile

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
