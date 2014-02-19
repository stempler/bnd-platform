/*
 * Copyright 2014 the original author or authors.
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

package org.standardout.gradle.plugin.include;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin that allows including build scripts similar to apply from,
 * but allows providing parameters.
 * 
 * @author Simon Templer
 */
public class IncludePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getExtensions().create("include", IncludePluginExtension.class, project);
	}

}
