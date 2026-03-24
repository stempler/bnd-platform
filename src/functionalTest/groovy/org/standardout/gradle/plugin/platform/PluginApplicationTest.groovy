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

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class PluginApplicationTest extends AbstractFunctionalTest {

	@Test
	void pluginCanBeApplied() {
		writeBuildFile()

		def result = runTask('tasks')

		assertThat(result.output).contains('Bnd-platform tasks')
	}

	@Test
	void pluginRegistersExpectedTasks() {
		writeBuildFile()

		def result = runTask('tasks', '--all')

		assertThat(result.output)
			.contains('bundles')
			.contains('bundleFeatures')
			.contains('generateCategory')
			.contains('updateSite')
			.contains('updateSiteZip')
			.contains('artifactMap')
			.contains('potentialOptionalImports')
	}

	@Test
	void bundlesTaskSucceedsWithNoDependencies() {
		writeBuildFile()

		def result = runTask('bundles')

		assertThat(result.task(':bundles').outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
	}
}
