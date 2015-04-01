package org.standardout.gradle.plugin.platform.internal.util;

import org.osgi.framework.Version;

public interface VersionQualifierMap {
	
	/**
	 * Get the qualifier to use for a specific artifact.
	 * 
	 * @param type the artifact type, e.g. bundle or feature
	 * @param name the artifact name
	 * @param version the artifact version
	 * @param ident the identifier for a specific instance of the artifact, e.g. the bnd configuration hash
	 * @return the qualifier to use
	 */
	public String getQualifier(String type, String name, Version version, String ident);

}
