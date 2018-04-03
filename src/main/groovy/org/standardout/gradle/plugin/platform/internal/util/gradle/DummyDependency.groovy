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

import groovy.transform.EqualsAndHashCode

import org.gradle.api.artifacts.Dependency
import org.gradle.internal.HasInternalProtocol

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
	private final String classifier
	private String because

	DummyDependency(String group, String name, String version, String classifier = null) {
		super()
		this.group = group
		this.name = name
		this.version = version
		this.classifier = classifier
	}
	
	DummyDependency(Map properties) {
		this(properties.group, properties.name, properties.version, properties.classifier)
	}

	@Override
	public String getGroup() {
		group
	}

	@Override
	public String getName() {
		name
	}

	@Override
	public String getVersion() {
		version
	}
	
	public String getClassifier() {
		classifier
	}

	@Override
	public boolean contentEquals(Dependency paramDependency) {
		equals(paramDependency)
	}

	@Override
	public Dependency copy() {
		this
	}

	@Override
	public void because(String because) {
		this.because = because
	}

	@Override
	public String getReason() {
		because
	}
}
