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

import org.junit.jupiter.api.Test

class MergeTest extends AbstractFunctionalTest {

	@Test
	void mergeWithMatchAndBnd() {
		writeBuildFile("""
			merge {
				match { it.name == 'gson' }
				bnd {
					symbolicName = 'com.merged.gson'
					instruction 'Bundle-Name', 'Merged Gson'
				}
			}
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('bundles')

		def mergedJar = findBundle('com.merged.gson')
		assertThat(mergedJar)
			.as('merged bundle with custom symbolic name should exist in build/plugins')
			.isNotNull()

		def manifest = readManifest(mergedJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-SymbolicName'))
			.as('Bundle-SymbolicName should be the merged symbolic name')
			.startsWith('com.merged.gson')
		assertThat(manifest.mainAttributes.getValue('Bundle-Name'))
			.as('Bundle-Name should be set from bnd instruction')
			.isEqualTo('Merged Gson')
	}
}
