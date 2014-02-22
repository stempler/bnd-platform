/*
 * Copyright 2010 the original author or authors.
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

package org.standardout.gradle.plugin.platform.internal.util.gradle

import groovy.transform.EqualsAndHashCode;

import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.HasInternalProtocol;

/**
 * Dummy dependency class (that may have a <code>null</code> name).
 *  
 * @author Simon Templer
 */
@EqualsAndHashCode
class DummyDependency implements Dependency {
	
	private final String group
	private final String name
	private final String version

	DummyDependency(String group, String name, String version) {
		super();
		this.group = group;
		this.name = name;
		this.version = version;
	}
	
	DummyDependency(Map properties) {
		this(properties.group, properties.name, properties.version)
	}

	@Override
	public String getGroup() {
		return group;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public boolean contentEquals(Dependency paramDependency) {
		equals(paramDependency)
	}

	@Override
	public Dependency copy() {
		return this;
	}

}
