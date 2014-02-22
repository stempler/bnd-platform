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

package org.standardout.gradle.plugin.platform.internal.util.bnd

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import aQute.bnd.osgi.Resource;

class ByteArrayResource implements Resource {

	final byte[] data
	final long lastMod
	String extra
	
	ByteArrayResource(Resource resource) {
		data = resource.openInputStream().withStream {
			InputStream input ->
			IOUtils.toByteArray(input)
		}
		lastMod = resource.lastModified()
		extra = resource.extra
	}
	
	ByteArrayResource(byte[] data, long lastMod, String extra = null) {
		this.data = data
		this.lastMod = lastMod
		this.extra = extra
	}
	
	@Override
	public InputStream openInputStream() throws Exception {
		return new ByteArrayInputStream(data);
	}

	@Override
	public void write(OutputStream out) throws Exception {
		out.write(data)
	}

	@Override
	public long lastModified() {
		return lastMod;
	}

	@Override
	public long size() throws Exception {
		return data.length;
	}

}
