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
package org.standardout.gradle.plugin.platform.internal.util

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.osgi.framework.Version

/**
 * Default implementation of a version qualifier map. Ensures that configuration
 * changes result in increased version numbers. For that purpose needs to keep
 * track of previously used versions. Persistent storage as Json file.
 *
 * @author Simon Templer
 */
class DefaultQualifierMap implements VersionQualifierMap {

	private final File file
	private final String prefix
	private final String fixedDateFormat
	private final int startLevel

	private final def dateLevels = [
		'yyyy',
		// year
		'yyyyMM',
		// month
		'yyyyMMdd',
		// day
		'yyyyMMddHHmm',
		// minute
		'yyyyMMddHHmmss',
		// second
		'yyyyMMddHHmmssSSS'] // millisecond
	private def map

	DefaultQualifierMap(File file, String prefix, int startLevel, String fixedDateFormat) {
		this.file = file
		this.prefix = prefix
		this.fixedDateFormat = fixedDateFormat
		if (startLevel >= dateLevels.size()) {
			this.startLevel = dateLevels.size() - 1
		}
		else if (startLevel < 0) {
			this.startLevel = 0
		}
		else {
			this.startLevel = startLevel
		}

		// load from file
		if (file.exists()) {
			file.withReader {
				map = new TreeMap(new JsonSlurper().parse(it))
			}
		}
		else {
			map = new TreeMap()
		}
	}

	@Override
	public String getQualifier(String type, String name, Version version,
		String ident) {
		// artifacts map
		def artifacts = map[type]
		if (!artifacts) {
			// new map
			artifacts = new TreeMap()
		}
		else {
			// ensure sorting on this level
			artifacts = new TreeMap(artifacts)
		}
		map[type] = artifacts

		// a single artifact
		def artifact = artifacts[name]
		if (!artifact) {
			artifact = [:]
			artifacts[name] = artifact
		}

		// an artifact version
		def versionString = version.toString()
		def artifactVersion = artifact[versionString]
		if (!artifactVersion) {
			artifactVersion = [:]
			artifact[versionString] = artifactVersion
		}

		// sort existing qualifiers, qualifiers mapped to idents
		SortedMap qualifiers = new TreeMap(artifactVersion)
		String lastQualifier = qualifiers.isEmpty() ? null : qualifiers.lastKey()
		String lastIdent = lastQualifier ? qualifiers[lastQualifier] : null
		if (lastIdent == ident) {
			// use the same qualifier that was previously used
			// as they share the same ident
			return lastQualifier
		}
		else {
			// create new qualifier associated to ident

			// create qualifier based on current time (to ensure version is increased)
			def now = new Date()
			// try different candidates (we try to keep the qualifier short)
			String candidate
			if (fixedDateFormat) {
				candidate = prefix + now.format(fixedDateFormat)
				if (qualifiers.containsKey(candidate)) {
					throw new IllegalStateException('Could not create unique qualifier based on fixed data format')
				}
			}
			else {
				// try to generate a short date based qualifier
				for (int i = startLevel; i < dateLevels.size() && (!candidate || qualifiers.containsKey(candidate)); i++) {
					candidate = prefix + now.format(dateLevels[i])
				}
				if (qualifiers.containsKey(candidate)) {
					throw new IllegalStateException('Could not create unique qualifier based on current timestamp')
				}
			}

			artifactVersion[candidate] = ident

			//FIXME persist
			file.text = JsonOutput.prettyPrint(JsonOutput.toJson(map))

			return candidate
		}
	}
}
