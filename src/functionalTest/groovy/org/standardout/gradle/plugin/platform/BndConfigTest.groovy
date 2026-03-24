package org.standardout.gradle.plugin.platform

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

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
