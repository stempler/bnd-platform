package org.standardout.gradle.plugin.platform
class PlatformPluginExtension {
	boolean fetchSources = true
	String featureId = 'platform.feature'
	String featureName = 'Generated platform feature'
	String featureVersion = '1.0.0'
	String categoryId = 'platform'
	String categoryName = 'Target platform'
	File updateSiteDir
	File eclipseHome
	def eclipseMirror = [
		win32: [
			win32: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-win32.zip',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-win32-x86_64.zip'
			]
		],
		linux: [
			gtk: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-linux-gtk.tar.gz',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-linux-gtk-x86_64.tar.gz'
			]
		],
		macosx: [
			cocoa: [
				x86: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-macosx-cocoa.tar.gz',
				x86_64: 'http://ftp.fau.de/eclipse/technology/epp/downloads/release/indigo/SR2/eclipse-rcp-indigo-SR2-macosx-cocoa-x86_64.tar.gz'
			]
		]
	]
}