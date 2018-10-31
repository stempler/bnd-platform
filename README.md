bnd-platform
============

Using OSGi and having trouble to get all the dependencies as proper OSGi bundles?
Even worse, sometimes you need to adapt bundles due to class loading issues or make an annoying dependency optional?

*bnd-platform* can help you solve that problem - it builds OSGi bundles and even Eclipse Update Sites from existing JARs, for instance retrieved from Maven repositories together with transitive dependencies. If needed you can adapt the creation of bundles via general or individual configuration options.
*bnd-platform* is a [Gradle](http://www.gradle.org/) plugin and uses [bnd](http://www.aqute.biz/Bnd/Bnd) to create bundles and [Eclipse](http://www.eclipse.org/) for the creation of p2 repositories.

For a quick start, check out the [sample project on GitHub](https://github.com/stempler/bnd-platform-sample) or use [this minimal example](https://github.com/stempler/bnd-platform-minimal) as a template for your build.

**What *bnd-platform* can do:**
* Create bundles for any JARs that can be defined as dependencies using Gradle (e.g. local JARs, JARs from Maven repositories) and their transitive dependencies
* Download dependency sources and create source bundles (with *Eclipse-SourceBundle* manifest header)
* Add *Bundle-License* and *Bundle-Vendor* headers based on information from associated POM files
* Merge multiple JARs/dependencies into one bundle, e.g. where needed due to duplicate packages or classloader issues
* Adapt the configuration for wrapping JARs or adapting existing bundles, e.g. to influence the imported packages
* Create an Eclipse Update Site / p2 repository from the created bundles
* Automatically associate version numbers to imported packages (experimental)


Usage
-----

The simplest way to apply the plugin to your Gradle build is using the latest release hosted on Maven Central:

```groovy
buildscript {
	repositories {
		jcenter()
	}
	dependencies {
		classpath 'org.standardout:bnd-platform:1.7.0'
		// if using Gradle 1.x uncomment the following line
//		classpath 'org.codehaus.groovy:groovy-backports-compat23:2.3+'
	}
}

apply plugin: 'org.standardout.bnd-platform'
```

Alternatives are including the repository content in the **buildSrc** folder as done in the [sample project](https://github.com/stempler/bnd-platform-sample) or by installing the plugin to your local Maven repository using `gradlew install` and adding it as dependency to your build script via `mavenLocal()` repository.

*bnd-platform* has been tested with Gradle 1.11 and Gradle 2.0. In Gradle 2.0 I observed resolving dependencies may take very long compared to previous versions (more than 20 min for a build that previously took roughly 3 min). Let's hope this will be fixed in future Gradle versions - for now I recommend using an earlier version of Gradle.

### Tasks

The **platform** plugin comes with several Gradle tasks - the following are the main tasks and build upon each other:

* ***bundles*** - create bundles and write them to **build/plugins**
* ***potentialOptionalImports*** Creates a potentialOptionalImports.txt file of imported packages of all generated bundles with the optionalImport instruction (See "Optional Dependencies" section below) 
* ***updateSite*** - create a p2 repository from the bundles and write it to **build/updatesite** (default)
* ***updateSiteZip*** - create a ZIP archive from the p2 repository and write it to **build/updatesite.zip** (default)

In addition, the ***clean*** task deletes all previously created bundles or update site artifacts. Usually you will want to clean the created bundles when building an update site, e.g. `gradle clean updateSite`.

Be aware that for building the p2 repository Eclipse is used. If no path to a local Eclipse installation is configured (see the settings section later on) the plugin will by default download Eclipse Indigo and use it for that purpose.

### Adding dependencies

*bnd-platform* adds a configuration named **platform** to a Gradle build. You can add dependencies to the **platform** configuration and configure them like you would with any other Gradle build - for example:

```groovy
// add Maven Central so the dependency can be resolved
repositories {
	mavenCentral()
}

dependencies {
    // add pegdown as dependency to the platform configuration
    platform 'org.pegdown:pegdown:1.4.2'
}
```

That's it - if you combine the previous code snippet with this one you have your first *bnd-platform* build script. A call `gradle updateSite` would create a p2 repository containing bundles for the [pegdown](https://github.com/sirthias/pegdown) library and its dependencies.

Please see the Gradle documentation on [basic](http://www.gradle.org/docs/current/userguide/artifact_dependencies_tutorial.html) and [advanced](http://www.gradle.org/docs/current/userguide/dependency_management.html) dependency management for more details on the dependency configration and advanced issues like resolving version conflicts or dealing with transitive dependencies.

As an alternative to the conventional dependency declaration you can use the *platform* extension to add a dependency using the **bundle** keyword:

```groovy
platform {
    // add pegdown as dependency to the platform configuration
    bundle 'org.pegdown:pegdown:1.4.2'
}
```

Both notations support adapting the dependency configuration as supported by Gradle, e.g. excluding specific transitive dependencies. However, adapting the OSGi bundle creation is only possible with the second notation, as it supports an additional **bnd** configuration.

### Bundle configuration

The *bnd* library is used as a tool to create OSGi bundles from JARs. *bnd* analyzes the class files in a JAR to determine packages to import and export. *bnd* also allows to configure its behavior through a set of header instructions that resemble the OSGi manifest headers (see the [bnd website](http://www.aqute.biz/Bnd/Format) for a detailed description).

The bundle configuration can be applied when adding a dependency:

```groovy
platform {
    bundle(group: 'net.sf.ehcache', name: 'ehcache-core', version:'2.6.6') {
    	bnd {
			// make hibernate packages optional
			optionalImport 'org.hibernate', 'org.hibernate.*'
		}
	}
}
```

Or independently - just registering the configuration without adding a dependency. The configuration is only applied if the dependency is either added directly or as a transitive dependency at another point in the script.

```groovy
platform {
    bnd(group: 'net.sf.ehcache', name: 'ehcache-core') {
		// make hibernate packages optional
		optionalImport 'org.hibernate', 'org.hibernate.*'
	}
}
```

Note that in the example above the version was omitted - the configuration applies to any dependency matching the group and name, regardless of its version.

The configuration options inside the **bnd** call are sketched below:

```groovy
platform {
    bnd(<DependencyNotation>) {
        // override/set the symbolic name
        symbolicName = <SymbolicNameString>
        // override/set the bundle name
        bundleName = <BundleNameString>
        // override/set bundle version
        version = <VersionString>
        // generic bnd header instruction
        instruction <Header>, <Instruction>
        // adapt the Import-Package instruction to import the given packages optionally
        optionalImport <Package1>, <Package2>, ...
        // adapt the Import-Package instruction to add the given package instruction
        prependImport  <PackageInstruction1>, <PackageInstruction2>, ...

        // override the default behavior, if a (generated) qualifier should be added to
        // the version of wrapped bundles (see plugin settings)
        addQualifier = true | false
    }
}
```

### Automatic package import versioning (experimental)

You can enable auto-determining versions for package imports by enabling the `determineImportVersions` plugin setting. For each bundle to be created from a JAR retrieved via Maven/Ivy, their direct dependencies are analysed in turn and package imports are determined by the packages present there and the version of the dependency modules. This works for most cases, but is not as good as if the information would be determined based on the packages exported by the dependencies. What comes as a bonus is that for packages that are not found in the direct dependencies the imports are made optional automatically.

A default strategy defines how the versions are represented for the **Import-Package** instructions, i.e. what lower and upper bounds are allowed for an imported package. Pre-defined strategies that can be used are:
* **MINIMUM** - the module version is the minimum version for the package import, there is no upper boundary
* **MAJOR** - like MINIMUM, but with the next major version (excluded) as upper boundary  (default)
* **MINOR** - like MINIMUM, but with the next minor version (excluded) as upper boundary
* **NONE** - no version constraint for package imports

Set the version strategy for the whole platform like this:

```groovy
platform {
    determineImportVersions = true
    importVersionStrategy = MINIMUM
}
```

You can also adapt the configuration for specific dependencies, or define a custom version strategy using a Closure. See the definitions of the pre-defined strategies in the sources for `PlatformPluginExtension` for more information on how this can be done. The following example demonstrates both:

```groovy
platform {
    imports(group: 'com.google.inject', name: 'guice') {
        versionStrategy = {
            // guice uses a strange versioning scheme for its package exports
            // e.g. version 2.0 of exports packages with version 1.2, version 3.0 with 1.3 etc.
            "[1.${it.major},1.${it.major + 1})"
        }
    }
}
```

The above example influences the package imports for the packages provided by *guice* for all bundles that have *guice* as their dependency. This is needed in this case as *guice* already is provided as OSGi bundle with the exported package versions differing from the module version - and because *bnd-platform* currently only uses the information of the module version and applies it to all imports instead of using available package export information. This might be improved in the future if there is need.

### Default configuration

*bnd-platform* will by default leave JARs that are recognized as bundles (meaning they have a *Bundle-SymbolicName* header already defined) as they are, except to eventually added *Bundle-License* and *Bundle-Vendor* headers if not yet present. If an existing bundle is wrapped because a bundle configuration applies to it, the configration from the bundle manifest applies as default configuration.

To other JARs the global default configuration applies, which exports packages with the dependency's version number and imports any identified packages as mandatory import. You can override or extend the global default configuration by adding a **bnd** configuration without giving a dependency notation, for example:

```groovy
platform {
    bnd {
        // make the package junit.framework an optional import
        // for all JARs that were not bundles already
        optionalImport 'junit.framework'
    }
}
```

But be careful what you put into the default configuration - setting the *symbolicName* or *version* here will not be seen as error, but does not make any sense and may lead to unpredicatable behavior (as there can't be two bundles with the same symbolic name and version).

### Configuration priority

A bundle configuration that is more concrete will always override/extend a more general configuration. A configuration applied to a dependency group takes precedence over the default configuration, while a configuration specified for a combination of group and name in turn takes precedence over a group configuration.
If configurations are defined on the same level, the configuration that is defined later in the script will override/extend a previous one.

Please note that in addition, in the combined configurations for a bundle, all assignments (e.g. `version = '1.0.0'`) take precedence over the method calls like `instruction` and `optionalImport`. This allows for instance to both override the version of a bundle in a concrete configuration and making use of it in a more general configuration:

```groovy
platform {
    bnd(group: 'org.standardout') {
        // packages with version number (uses the version provided further below)
        instruction 'Export-Package', "org.*;version=$version"
    }
    bnd(group: 'org.standardout', name: 'bnd-platform', version: '0.1') {
        // override the version
        version = '0.1.0.RELEASE'
    }
}
```

### Override any configuration

Starting with version 0.3 it is possible to override all bundle configurations at once. This also applies to dependencies that already are an OSGi bundle. You can use any instructions you would use inside **bnd** to apply them to all dependencies in a call to **override**:

```groovy
platform {
    override {
        // JUnit optional everywhere - so we can exclude it from products
        optionalImport 'junit.framework.*', 'org.junit.*'
    }
}
```

### Optional Dependencies

Many third party libraries have optional dependencies being specified by using `<optional>true</optional>` in a pom.xml file.

For example the [Retrofit](https://mvnrepository.com/artifact/com.squareup.retrofit2/retrofit/2.4.0 "Retrofit 2.4.0 on Maven Central") library has android as optional dependency:

```xml
<dependency>
  <groupId>com.google.android</groupId>
  <artifactId>android</artifactId>
  <optional>true</optional>
</dependency>
```
Since it is unlikely to happen that you want an android dependency in your OSGi application it can be marked as optional:

```groovy
plugin('com.squareup.retrofit2:retrofit:2.4.0'){
	bnd {
		optionalImport 'android.os'
		optionalImport 'android.net'
    	}
}
```
Unfortunately the information about the `<optional>true</optional>` instruction is lost during a Gradle build because Gradle's dependency management does not support it yet. (FYI https://docs.gradle.org/4.6/release-notes.html#support-for-optional-dependencies-in-pom-consumption)
So the bnd-platform plug-in cannot add the optionalImport instructions automatically, yet.

For the [Retrofit](https://mvnrepository.com/artifact/com.squareup.retrofit2/retrofit/2.4.0 "Retrofit 2.4.0 on Maven Central") library it is fairly easy to write the optionalImport instructions like above, but there are other libraries, which have plenty of these `<optional>true</optional>` instruction.

In order to generate the list of potential optional imports a _potentialOptionalImports_ task has been created. This task  creates a _potentialOptionalImports.txt_ file, which lists potential optional imports of each and every bundle, which is created during a build.

### Sharing configurations

Even though setting up an extensive platform of OSGi bundles can be done quite fast using *bnd-platform*, in many cases additional configuration is necessary. It comes naturally that it should be possible to reuse and share those configurations.

With Gradle you can use `apply from: 'someScript.gradle'` to include other build scripts. In those you can define dependencies, bnd configuration or remote repositories like you would do in the main build script.

An alternative is using the [gradle-include-plugin](https://github.com/stempler/gradle-include-plugin) which allows you to include specific methods from an external script and thus provide parameters to the include. In context of *bnd-platform* it often makes sense to provide a version number as parameter. See the [sample project](https://github.com/stempler/bnd-platform-sample) for some nice examples, e.g. the [logging](https://github.com/stempler/bnd-platform-sample/blob/master/modules/logging.groovy) or [geotools](https://github.com/stempler/bnd-platform-sample/blob/master/modules/geotools.groovy) platform modules defined there. Using the *include* plugin these modules are applied to the sample build like this:

```groovy
include {
	from('modules/logging.groovy') {
		slf4jAndLogback '1.7.2', '1.0.10' // slf4j and logback with given versions
	}

	from('modules/geotools.groovy') {
		geotools() // include geotools with default modules and version
	}
}
```

We have created a repository on GitHub to collect the platform modules and configurations we use for our projects and you are welcome to fork and contribute: [shared-platform](https://github.com/igd-geo/shared-platform). The repository is designed to be used with the [gradle-include-plugin](https://github.com/stempler/gradle-include-plugin) and to enable the sharing of configurations without imposing them on you - just include the configuration that makes sense for you and augment it with your own.

### Defining features *(since 1.1)*

You can combine dependencies to an Eclipse feature. The feature will contain the dependencies, as well as their transitive dependencies. You can use the features for more fine-grained control in you target platform or in a feature based Eclipse product. The platform feature contains all features you define. If fetching sources is enabled, a matching source feature will be created for each feature.

A feature includes all plugins (bundles) defined in its context, here an example:

```groovy
platform {
	// define a feature
	feature(id: 'platform.restclient', name: 'REST client dependencies', version: '1.0.0') {
		// define what's in the feature

		plugin 'org.codehaus.groovy.modules.http-builder:http-builder:0.6', {
			// exclude this transitive dependency
			exclude group: 'net.sourceforge.nekohtml', module: 'nekohtml'
		}
		plugin 'commons-io:commons-io:2.4'

		// include a feature that is defined elsewhere given its ID
		includes << 'platform.geotools'
	}
}
```

Providing  a feature ID is mandatory, but *name* and *version* may be omitted (the version defaults to the platform feature version). `plugin` and `bundle` can be used synonymously inside the `feature` block, but when using `bundle` you may experience problems when used in inner closures.

### Local dependencies

You can easily add local JARs to the platform. **If the JAR is not an OSGi bundle yet**, you have add it on its own and at least provide **symbolicName** and **version**:

```groovy
platform {
	bundle file('someLibrary.jar'), {
		bnd {
			version = '1.0.0' // mandatory
			symbolicName = 'com.example.library.some' // mandatory
			bundleName = 'Some Library'
			instruction 'Export-Package', "com.example.library.some.*;version=$version"
		}
	}

	// depends on groovy
	bundle 'org.codehaus.groovy:groovy:1.8.5'
}
```

As in the example above, you should make sure to add additional dependencies that might be needed by the JAR. Please note that for a JAR `filename.jar` sources provided in a `filename-sources.jar` will be wrapped automatically in a corresponding source bundle.

**JARs that are already OSGi bundles** you can include en masse, and without the need for additional configuration, for example:

```groovy
platform {
	// all bundles in a directory
	bundle fileTree(dir: 'lib') {
		include '*.jar'
	}

	// specific bundles
	bundle files('someBundle.jar', 'someOtherBundle.jar')
}
```

### Multiple versions of a bundle/dependency

When resolving the **platform** configuration with **bnd-platform**, **Gradle** will only include one version for each dependency. This is intentional as even though OSGi is designed to deal with issues such as multiple versions of a bundle, often it will lead to problems - namely package uses conflicts (please note that you can get package uses conflicts even with only one version of each bundle, as some may conflict with packages provided by the system bundle).

However, if there is the need to have multiple versions of a bundle, these are your options:
* use multiple **bnd-platform** builds, each will resolve its dependencies independent of the others
* add dependencies to the **platformaux** configuration - they will be added in addition to the resolved platform configuration (but w/o their transitive dependencies)
* add the additional versions as local dependencies

Following is an example using the **platformaux** configuration:

```groovy
dependencies {
    // need old version (pre 4) of asm for some bundles
    platformaux 'asm:asm:3.3.1'
}
```

### Merged bundles

Sometimes it is necessary to create a bundle out of multiple JARs, most often due to class loading issues. The [Geotools](http://www.geotools.org/) library is a famous example of that. It uses [Java SPI](http://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) as extension mechanism, which does only recognize extensions from the same class loader. One way [suggested to cope with this](http://docs.geotools.org/stable/userguide/welcome/application.html#osgi) is creating a monolithic bundle that includes all the needed geotools modules (which I prefer because with Geotools otherwise you have different bundles partly exporting the same packages). Doing this with *bnd-platform* is quite easy:

```groovy
platform {
	def geotoolsVersion = '10.4'

	// define the merged bundle
	merge {
		// the match closure is applied to all dependencies/artifacts encountered
		// if true, an artifact is included in the bundle
		match {
			// merge all artifacts in org.geotools group, but not gt-opengis
			it.group == 'org.geotools' && it.name != 'gt-opengis'
		}

		bnd {
			symbolicName = 'org.geotools'
			bundleName = 'Geotools'
			version = geotoolsVersion
			instruction 'Export-Package', "org.geotools.*;version=$version"
			instruction 'Private-Package', '*'
		}
	}

	// add geotools modules as dependencies
	bundle "org.geotools:gt-shapefile:$geotoolsVersion"
	// etc.
}
```

Providing the **symbolicName** and **version** as part of the *bnd* configuration is mandatory for merged bundles, as the information from the original JARs' manifests is discarded.
Please note that the example above misses the Maven repositories needed to actually retrieve those artifacts, see the sample project for a [more complete example](https://github.com/stempler/bnd-platform-sample/blob/master/modules/geotools.groovy).

If you use `match { ... }` to merge bundles, it is called for each artifact. The artifact can be accessed via the variable **it**. An artifact has the following properties that can be useful to check against in a *match*:

* **group** - the group name of the artifact, e.g. *'org.geotools'*
* **name** - the name (artifact ID) of the artifact, e.g. *'gt-shapefile'*
* **version** - the version of the artifact
* **file** - the local or downloaded file of the artifact, as File object

As alternative to **match** or in combination with it you can add bundles to merge via **bundle** or **include**. The syntax is the same as when adding dependencies. However, using **include** you just specify an artifact to be included if it is a dependency defined somewhere else, it does not add it as dependency.

```groovy
platform {
	merge {
		bundle 'someGroup:someArtifact:1.0.0' // also added as dependency
		include group: 'someGroup', name: 'someOtherArtifact' // not added as dependency

		bnd {
			...
		}
	}
}

```

#### Merge settings

You can supply parameters to **merge**, currently those are:

* **failOnDuplicate** - fail if the same file occurs in more than one JAR (not taking into account the manifest)  (default: **true**)
* **collectServices** - combines files in `META-INF/services` defining extensions via SPI (default: **true**)

You can specify them as named parameters, e.g.:

```groovy
platform {
	merge(failOnDuplicate: false, collectServices: true) {
		...
	}
}
```

Plugin settings
---------------

Via the platform extension there are several settings you can provide:

* **fetchSources** - if sources for external dependencies should be fetched and source bundles created (default: **true**)
* **updateSiteDir** - the directory the generated p2 repository is written to (default: `new File(buildDir, 'updatesite')`)
* **updateSiteZipFile** - the target file for the zipped p2 repository (default: `new File(buildDir, 'updatesite.zip')`)
* **eclipseHome** - File object pointing to the directory of a local Eclipse installation to be used for generating the p2 repository (default: `null`)
* **eclipseMirror** - Eclipse download URLs to be used when no local installation is provided via *eclipseHome*. Uses https://dl.bintray.com/simon-scholz/eclipse-apps/eclipse-p2-minimal.tar.gz by default.
* **downloadsDir** -  the directory to store the downloaded Eclipse installation on local, this works if *eclipseHome* is not specified. (default: `new File(buildDir, 'eclipse-downloads')`)
* **generatePlatformFeature** - States if a general feature should be created. In case custom features are generated you might not want to have an additional "generated platform feature" besides your own features. (default: **true**)
* **featureId** - the identifier of the feature including the platform bundles that will be available in the created update site (default: **'platform.feature'**)
* **featureName** - the name of the feature including the platform bundles that will be available in the created update site (default: **'Generated platform feature'**)
* **featureVersion** - the version number for the feature including the platform bundles that will be available in the created update site (defaults to the project version)
* **featureProvider** - the provider name to be used for features (default: **'Generated with bnd-platform'**)
* **categoryId** - the identifier of the feature's category (default: **'platform'**)
* **categoryName** - the name of the feature's category (default: **'Target platform'**)
* **determineImportVersions** - automatically determine package import versions (default: `false`)
* **importVersionStrategy** = global strategy for import versions (default: `MAJOR`)
* **importIgnorePackages** - set of packages to ignore when analyzing packages of dependencies to determine package import versions
* **defaultQualifier** - the default version qualifier to use for wrapped bundles. If a qualifier is already
	 * present the default will be appended, separated by a dash. Does by default not apply to file based dependencies (default: **'autowrapped'**)
* **useBndHashQualifiers** - if a hash calculated from the bnd configuration should be used as version qualifier for wrapped bundles. It replaces the default qualifier where applicable (default: `true`)
* **useFeatureHashQualifiers** - if a hash based on the feature content should be appended as qualifier to feature versions (default: `true`)
* **hashCalculator** - hash calculator for determining the hash qualifier from a bundle's bnd configuration, can be replaced by a custom closure (default: `ADLER32`)
* **hashQualifierMap** - for bundles/features that would have hash based qualifiers, map those to qualifiers that ensure a specific behavior. The default qualifier map is based on version history persisted to a file and date based qualifiers to ensure increasing version qualifiers (can be important for update mechanisms). To use the default qualifier map, simply provide a file or file path for the version history to be stored in (will be stored as Json).
* **defaultQualifierMap.prefix** - the prefix to use for version qualifiers provided via the default qualifier map (default: `'i'`)
* **defaultQualifierMap.baseDate** - configures the base level for time based qualifiers generated by the default qualifier map. Valid values are `YEAR`, `MONTH`, `DAY`, `MINUTE`, `SECOND`, `MILLISECOND` (default: `MONTH`)
* **auxVersionedSymbolicNames** - states if the symbolic names for bundles created via the platformaux configuration should be adapted to include the version number. This is useful when dealing with systems that have problems when there actually are bundles with the same name but different versions. An example is Eclipse RCP plugin-based products - they can include only one version of a bundle with the same name. (default: `false`)
* **removeSignaturesFromWrappedBundles** - if signatures should be removed from signed jars that are wrapped using bnd (default: `true`)
* **addBndPlatformManifestHeaders** - if *bnd-platform* specific manifest headers should be added. Adds information to the manifest that allows reconstructing the original Maven artifact identifiers (default: `false`)
* **extractPomInformation** - if additional configuration information from POM is desired (default: `true`)

<!--- * **defaultQualifierMap.fixedDatePattern** - a fixed pattern for formatting the current date for use as part of the qualifier. Provide the pattern in a form suitable for SimpleDataFormat that ensures that the order of those dates as String is the same as the date order (e.g. `'yyyyMMddHHmm'`) -->

For example:

```groovy
platform {
	fetchSources = false
	featureVersion = '3.1.0'
	eclipseHome = new File('/opt/eclipse')
	eclipseMirror = 'http://myeclipsedownload.com/eclipse.tar.gz'
}
```



Using the current SNAPSHOT
--------------------------

If you want to test the latest version with changes that have not been released yet, you can configure your project to use the latest SNAPSHOT:

```groovy
buildscript {
  repositories {
    maven {
      url 'http://oss.sonatype.org/content/repositories/snapshots/'
    }
    jcenter()
  }
  dependencies {
    classpath 'org.standardout:bnd-platform:1.8.0-SNAPSHOT'
  }
}

apply plugin: 'org.standardout.bnd-platform'
```


License
-------

This software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
