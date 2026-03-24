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
package org.standardout.gradle.plugin.platform.internal.util;

import org.osgi.framework.Version;

public interface VersionQualifierMap {

	/**
	 * Get the qualifier to use for a specific artifact.
	 *
	 * @param type
	 *            the artifact type, e.g. bundle or feature
	 * @param name
	 *            the artifact name
	 * @param version
	 *            the artifact version
	 * @param ident
	 *            the identifier for a specific instance of the artifact, e.g. the bnd configuration hash
	 * @return the qualifier to use
	 */
	public String getQualifier(String type, String name, Version version, String ident);

}
