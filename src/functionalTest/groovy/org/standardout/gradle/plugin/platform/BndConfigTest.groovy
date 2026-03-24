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

class BndConfigTest extends AbstractFunctionalTest {

	@Test
	void defaultBndConfigApplied() {
		// asm:asm:3.3.1 is a non-OSGi jar, so bnd wraps it and applies the default bnd config
		writeBuildFile("""
			bnd {
				instruction 'Bundle-Vendor', 'Default Vendor'
			}
			bundle 'asm:asm:3.3.1'
		""")

		runTask('bundles')

		def asmJar = findBundle('asm')
		assertThat(asmJar).as('asm JAR should exist in build/plugins').isNotNull()

		def manifest = readManifest(asmJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Vendor'))
			.as('Bundle-Vendor should be set by default bnd config on non-OSGi bundles')
			.isEqualTo('Default Vendor')
	}

	@Test
	void overrideConfigApplied() {
		writeBuildFile("""
			override {
				instruction 'Bundle-Vendor', 'Override Vendor'
			}
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Bundle-Vendor'))
			.as('Bundle-Vendor should be set by override config')
			.isEqualTo('Override Vendor')
	}

	@Test
	void optionalImportApplied() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
			bnd('com.google.code.gson:gson') {
				optionalImport 'org.missing.package'
			}
		""")

		runTask('bundles')

		def gsonJar = findBundle('gson')
		assertThat(gsonJar).isNotNull()

		def manifest = readManifest(gsonJar)
		assertThat(manifest.mainAttributes.getValue('Import-Package'))
			.as('Import-Package should list the package as optional')
			.contains('org.missing.package;resolution:=optional')
	}
}
