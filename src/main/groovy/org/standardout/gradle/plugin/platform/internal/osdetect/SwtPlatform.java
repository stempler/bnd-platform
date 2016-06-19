/*
 * Copied from DurianSwt
 * 
 * Copyright 2016 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.standardout.gradle.plugin.platform.internal.osdetect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Models the platforms that SWT binaries are built for, useful for build tools that interact with SWT jars. */
public class SwtPlatform {
	/** Windowing system. */
	private final String ws;
	/** Operating system. */
	private final String os;
	/** CPU architecture. */
	private final String arch;

	/** Arguments go from most-specific (windowing-system) to least-specific (CPU architecture). */
	private SwtPlatform(String ws, String os, String arch) {
		this.ws = ws;
		this.os = os;
		this.arch = arch;
	}

	/** Returns the windowing system. */
	public String getWs() {
		return ws;
	}

	/** Returns the operating system. */
	public String getOs() {
		return os;
	}

	/** Returns the CPU architecture. */
	public String getArch() {
		return arch;
	}

	@Override
	public boolean equals(Object otherRaw) {
		if (otherRaw instanceof SwtPlatform) {
			SwtPlatform other = (SwtPlatform) otherRaw;
			return ws.equals(other.ws) && os.equals(other.os) && arch.equals(other.arch);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(ws, os, arch);
	}

	/** Returns "ws.os.arch", which is how SWT bundles are named. */
	@Override
	public String toString() {
		return ws + "." + os + "." + arch;
	}

	/** Returns a string appropriate as an Eclipse-PlatformFilter in a MANIFEST.MF */
	public String platformFilter() {
		return "(& " + "(osgi.ws=" + ws + ") " + "(osgi.os=" + os + ") " + "(osgi.arch=" + arch + ")" + " )";
	}

	/** Returns a map containing the platform properties. */
	public Map<String, String> platformProperties() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("osgi.ws", ws);
		map.put("osgi.os", os);
		map.put("osgi.arch", arch);
		return map;
	}

	/** Returns the folder name that <a href="https://github.com/akhikhl/wuff">wuff</a> uses for this SwtPlatform. */
	public String getWuffString() {
		// @formatter:off
		Map<SwtPlatform, String> wuffMap = new HashMap<>();
		wuffMap.put(parseWsOsArch("cocoa.macosx.x86_64"),	"macosx-x86_64");
		wuffMap.put(parseWsOsArch("gtk.linux.x86"),			"linux-x86_32");
		wuffMap.put(parseWsOsArch("gtk.linux.x86_64"),		"linux-x86_64");
		wuffMap.put(parseWsOsArch("win32.win32.x86"),		"windows-x86_32");
		wuffMap.put(parseWsOsArch("win32.win32.x86_64"),	"windows-x86_64");
		// @formatter:on
		return Objects.requireNonNull(wuffMap.get(this));
	}

	/** Parses ws.os.arch strings (which is how SWT bundles are specified). */
	public static SwtPlatform parseWsOsArch(String unparsed) {
		String[] pieces = unparsed.split("\\.");
		if (pieces.length != 3) {
			throw new IllegalArgumentException(unparsed + " should have the form 'ws.os.arch'.");
		}
		String ws = pieces[0];
		String os = pieces[1];
		String arch = pieces[2];
		return new SwtPlatform(ws, os, arch);
	}

	/** Returns the SwtPlatform for the native platform: 32-bit JVM on 64-bit Windows returns x86_64. */
	public static SwtPlatform getNative() {
		return fromOS(OS.getNative());
	}

	/** Returns the SwtPlatform for the running platform: 32-bit JVM on 64-bit Windows returns x86. */
	public static SwtPlatform getRunning() {
		return fromOS(OS.getRunning());
	}

	/** Converts an OS to an SwtPlatform. */
	public static SwtPlatform fromOS(OS raw) {
		String ws = raw.winMacLinux("win32", "cocoa", "gtk");
		String os = raw.winMacLinux("win32", "macosx", "linux");
		String arch = raw.getArch().x86x64("x86", "x86_64");
		return new SwtPlatform(ws, os, arch);
	}

	/** Returns all of the platforms. */
	public static List<SwtPlatform> getAll() {
		List<SwtPlatform> platforms = new ArrayList<SwtPlatform>();
		for (OS os : OS.values()) {
			platforms.add(SwtPlatform.fromOS(os));
		}
		return platforms;
	}
}
