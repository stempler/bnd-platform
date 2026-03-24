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

class BundleWrappingTest extends AbstractFunctionalTest {

	@Test
	void nonOsgiBundleIsWrapped() {
		// asm 3.3.1 is a pre-OSGi jar — bnd must generate all OSGi manifest headers
		writeBuildFile("""
			bundle 'asm:asm:3.3.1'
		""")

		runTask('bundles')

		def asmJar = findBundle('asm')
		assertThat(asmJar).as('asm JAR should exist in build/plugins').isNotNull()

		def manifest = readManifest(asmJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-SymbolicName'))
			.as('bnd should generate a Bundle-SymbolicName for a non-OSGi jar')
			.isNotNull()
			.isNotEmpty()
		assertThat(manifest.mainAttributes.getValue('Bundle-Version'))
			.as('bnd should generate a Bundle-Version for a non-OSGi jar')
			.isNotNull()
			.isNotEmpty()
		assertThat(manifest.mainAttributes.getValue('Export-Package'))
			.as('bnd should generate Export-Package based on the jar contents')
			.isNotNull()
			.contains('org.objectweb.asm')
	}

	@Test
	void existingOsgiBundlePassesThrough() {
		writeBuildFile("""
			bundle 'org.slf4j:slf4j-api:2.0.9'
		""")

		runTask('bundles')

		def slf4jJar = findBundle('slf4j')
		assertThat(slf4jJar).as('slf4j JAR should exist in build/plugins').isNotNull()

		def manifest = readManifest(slf4jJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-SymbolicName'))
			.as('Bundle-SymbolicName should be set')
			.contains('slf4j.api')
	}

	@Test
	void customBndInstructionApplied() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
			bnd('com.google.code.gson:gson') {
				instruction 'Bundle-Name', 'Custom Gson Bundle'
			}
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Name'))
			.isEqualTo('Custom Gson Bundle')
	}

	@Test
	void customBndInstructionMapNotationApplied() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
			bnd group: 'com.google.code.gson', name:'gson', {
				instruction 'Bundle-Name', 'Custom Gson Bundle Map Notation'
			}
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Name'))
			.isEqualTo('Custom Gson Bundle Map Notation')
	}

	@Test
	void customBundleBndInstructionApplied() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1', {
			bnd {
				instruction 'Bundle-Name', 'Custom Gson Bundle'
			}
			}
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Name'))
			.isEqualTo('Custom Gson Bundle')
	}

	@Test
	void defaultQualifierApplied() {
		writeBuildFile("""
			defaultQualifier = 'myqualifier'
			useBndHashQualifiers = false
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Version'))
			.as('Bundle-Version should contain the custom qualifier')
			.contains('myqualifier')
	}
}
