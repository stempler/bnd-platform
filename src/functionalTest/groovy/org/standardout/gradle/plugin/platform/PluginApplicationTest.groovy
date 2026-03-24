package org.standardout.gradle.plugin.platform

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

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
