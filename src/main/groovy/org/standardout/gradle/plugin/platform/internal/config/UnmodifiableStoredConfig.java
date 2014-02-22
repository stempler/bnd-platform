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

package org.standardout.gradle.plugin.platform.internal.config;

import groovy.lang.Closure;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;

class UnmodifiableStoredConfig implements StoredConfig {
	
	private final StoredConfig decoratee;

	public UnmodifiableStoredConfig(StoredConfig decoratee) {
		super();
		this.decoratee = decoratee;
	}

	@SuppressWarnings("rawtypes")
	public List<Closure> getBndClosures() {
		return Collections.unmodifiableList(decoratee.getBndClosures());
	}

	public BndConfig evaluate(Project project, File file, Map<String, String> initialProperties) {
		return decoratee.evaluate(project, file, initialProperties);
	}

	public BndConfig evaluate(Project project, String group, String name,
			String version, Map<String, String> initialProperties) {
		return decoratee.evaluate(project, group, name, version, initialProperties);
	}

	public BndConfig evaluate(Project project, String group, String name,
			String version, File file, Map<String, String> initialProperties) {
		return decoratee.evaluate(project, group, name, version, file, initialProperties);
	}

	public void leftShift(StoredConfig other) {
		throw new UnsupportedOperationException("Immutable configuration");
	}

	public void rightShift(StoredConfig other) {
		decoratee.rightShift(other);
	}

	public boolean isEmpty() {
		return decoratee.isEmpty();
	}
	
}
