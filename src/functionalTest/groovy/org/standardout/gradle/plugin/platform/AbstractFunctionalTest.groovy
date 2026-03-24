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

import java.util.jar.JarFile
import java.util.jar.Manifest

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

abstract class AbstractFunctionalTest {

	@TempDir
	File projectDir

	File buildFile
	File settingsFile

	@BeforeEach
	void setup() {
		settingsFile = new File(projectDir, 'settings.gradle')
		settingsFile.text = "rootProject.name = 'test-project'"
		buildFile = new File(projectDir, 'build.gradle')
	}

	protected void writeBuildFile(String platformBlock = '') {
		buildFile.text = """
			plugins {
				id 'org.standardout.bnd-platform'
			}

			repositories {
				mavenCentral()
			}

			platform {
				fetchSources = false
				${platformBlock}
			}
		""".stripIndent()
	}

	protected BuildResult runTask(String... arguments) {
		GradleRunner.create()
			.withProjectDir(projectDir)
			.withPluginClasspath()
			.withArguments(arguments.toList() + ['--stacktrace'])
			.forwardOutput()
			.build()
	}

	protected BuildResult runTaskAndFail(String... arguments) {
		GradleRunner.create()
			.withProjectDir(projectDir)
			.withPluginClasspath()
			.withArguments(arguments.toList() + ['--stacktrace'])
			.forwardOutput()
			.buildAndFail()
	}

	protected File getBundlesDir() {
		new File(projectDir, 'build/plugins')
	}

	protected File getFeaturesDir() {
		new File(projectDir, 'build/features')
	}

	protected File findBundle(String nameSubstring) {
		bundlesDir.exists() ? bundlesDir.listFiles()?.find { it.name.contains(nameSubstring) } : null
	}

	protected File findFeature(String nameSubstring) {
		featuresDir.exists() ? featuresDir.listFiles()?.find { it.name.contains(nameSubstring) } : null
	}

	protected Manifest readManifest(File jarFile) {
		new JarFile(jarFile).withCloseable { jar -> jar.manifest }
	}
}
