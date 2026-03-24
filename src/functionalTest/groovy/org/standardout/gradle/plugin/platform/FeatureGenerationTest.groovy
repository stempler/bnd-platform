package org.standardout.gradle.plugin.platform

import org.junit.jupiter.api.Test

import java.util.jar.JarFile
import groovy.xml.XmlSlurper

import static org.assertj.core.api.Assertions.assertThat

class FeatureGenerationTest extends AbstractFunctionalTest {

	@Test
	void bundleFeaturesCreatesFeatureJar() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('bundleFeatures')

		assertThat(featuresDir).as('build/features should exist').isDirectory()
		def featureJars = featuresDir.listFiles()
		assertThat(featureJars).as('at least one feature JAR should be created').isNotEmpty()

		def platformFeature = featureJars.find { it.name.contains('platform.feature') }
		assertThat(platformFeature).as('platform.feature JAR should exist').isNotNull()

		new JarFile(platformFeature).withCloseable { jar ->
			assertThat(jar.getEntry('feature.xml'))
				.as('feature JAR should contain feature.xml')
				.isNotNull()
		}
	}

	@Test
	void generateCategoryCreatesXml() {
		writeBuildFile("""
			bundle 'com.google.code.gson:gson:2.10.1'
		""")

		runTask('generateCategory')

		def categoryFile = new File(projectDir, 'build/category.xml')
		assertThat(categoryFile).as('category.xml should exist').isFile()

		def content = categoryFile.text
		assertThat(content)
			.contains('<site>')
			.contains('category-def')
	}

	@Test
	void featureWithPluginKeyword() {
		writeBuildFile("""
			generatePlatformFeature = false
			feature(id: 'test.plugin.feature', name: 'Test Feature', version: '1.0.0') {
				plugin 'com.google.code.gson:gson:2.10.1'
			}
		""")

		runTask('bundleFeatures')

		def featureJar = findFeature('test.plugin.feature')
		assertThat(featureJar)
			.as('feature JAR using plugin keyword should be created')
			.isNotNull()

		new JarFile(featureJar).withCloseable { jar ->
			def featureXmlEntry = jar.getEntry('feature.xml')
			assertThat(featureXmlEntry).isNotNull()

			def xml = new XmlSlurper().parse(jar.getInputStream(featureXmlEntry))
			assertThat(xml.plugin.size() as int)
				.as('feature.xml should contain the gson plugin entry')
				.isGreaterThan(0)
		}
	}

	@Test
	void featureWithIncludes() {
		writeBuildFile("""
			generatePlatformFeature = false
			feature(id: 'base.feature', name: 'Base Feature', version: '1.0.0') {
				bundle 'com.google.code.gson:gson:2.10.1'
			}
			feature(id: 'composite.feature', name: 'Composite Feature', version: '1.0.0') {
				includes << 'base.feature'
			}
		""")

		runTask('bundleFeatures')

		def compositeJar = findFeature('composite.feature')
		assertThat(compositeJar)
			.as('composite feature JAR should be created')
			.isNotNull()

		new JarFile(compositeJar).withCloseable { jar ->
			def featureXmlEntry = jar.getEntry('feature.xml')
			assertThat(featureXmlEntry).isNotNull()

			def xml = new XmlSlurper().parse(jar.getInputStream(featureXmlEntry))
			assertThat(xml.includes.size() as int)
				.as('feature.xml should contain an includes entry for base.feature')
				.isGreaterThan(0)
		}
	}

	@Test
	void platformFeatureCanBeDisabled() {
		writeBuildFile("""
			generatePlatformFeature = false
			bundle 'com.google.code.gson:gson:2.10.1'
			feature(id: 'my.custom.feature', name: 'My Feature', version: '1.0.0') {
				bundle 'com.google.code.gson:gson:2.10.1'
			}
		""")

		runTask('bundleFeatures')

		def featureJars = featuresDir.listFiles()
		assertThat(featureJars).as('exactly one feature JAR should exist').hasSize(1)
		assertThat(featureJars[0].name)
			.as('only the custom feature should be generated')
			.contains('my.custom.feature')
	}
}
