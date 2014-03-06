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

package org.standardout.gradle.plugin.platform.internal.util

import java.util.regex.Matcher

import org.osgi.framework.Version


class VersionUtil {
	
	/**
	 * Convert a version string to a valid OSGi version.
	 * 
	 * @param version the version string
	 * @param onInvalid closure that is called with the OSGi version if the
	 *   original version string yielded no valid OSGi version
	 * @return the OSGi version, the string representation may differ from
	 *   the original version string
	 */
	static Version toOsgiVersion(String version, Closure onInvalid = null) {
		Version osgiVersion
		try {
			osgiVersion = Version.parseVersion(version)
		} catch (NumberFormatException e) {
			// try again with adapted versions
			Matcher matcher = version =~/^(\d+)(\.(\d+))?(\.(\d+))?(\.|-)?(.*)$/
			if (matcher.count > 0) {
				def match = matcher[0]
				String qualifier = match[7]
				if (qualifier != null) {
					qualifier = qualifier.replaceAll(/[^0-9a-zA-Z\-_]/, '_')
				}
				
				osgiVersion = new Version(
					match[1] as int,
					(match[3] as Integer)?:0,
					(match[5] as Integer)?:0,
					qualifier)
			}
			else {
				String strippedVersion = version.replaceAll(/[^0-9\.]/, '')
				osgiVersion = Version.parseVersion(strippedVersion)
			}

			// invalid callback			
			if (onInvalid != null) {
				onInvalid(osgiVersion)
			}
		}
		
		osgiVersion
	}
	
}