package org.standardout.gradle.plugin.platform

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

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
