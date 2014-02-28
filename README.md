bnd-platform
============

Using OSGi and having trouble to get all the dependencies as proper OSGi bundles?
Even worse, sometimes you need to adapt bundles due to class loading issues or make an annoying dependency optional?

*bnd-platform* can help you solve that problem - it builds OSGi bundles and even Eclipse Update Sites from existing JARs, for instance retrieved from Maven repositories together with transitive dependencies. If needed you can adapt the creation of bundles via general or individual configuration options.
*bnd-platform* is a [Gradle](http://www.gradle.org/) plugin and uses [bnd](http://www.aqute.biz/Bnd/Bnd) to create bundles and [Eclipse](http://www.eclipse.org/) for the creation of p2 repositories.

For a quick start, check out the [sample project on GitHub](https://github.com/stempler/bnd-platform-sample).

**What *bnd-platform* can do:**
* Create bundles for any JARs that can be defined as dependencies using Gradle (e.g. local JARs, JARs from Maven repositories) and their transitive dependencies
* Download dependency sources and create source bundles (with *Eclipse-SourceBundle* manifest header)
* Add *Bundle-License* and *Bundle-Vendor* headers based on information from associated POM files
* Merge multiple JARs/dependencies into one bundle, e.g. where needed due to duplicate packages or classloader issues
* Adapt the configuration for wrapping JARs or adapting existing bundles, e.g. to influence the imported packages
* Create an Eclipse Update Site / p2 repository from the created bundles

**What *bnd-platform* does not:**
* Automatically associate version numbers to imported packages
* Create bundles or update sites from source projects (though that would probably not be too complicated - contributions welcome!)

Usage
-----

There is no official release available yet, so applying the plugin to a Gradle project can either be done by including the repository content in the **buildSrc** folder as done in the [sample project](https://github.com/stempler/bnd-platform-sample) or by installing the plugin to your local Maven repository using `gradlew install` and adding it as dependency and plugin to your build.gradle script:

```groovy
buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath 'org.standardout:bnd-platform:0.2'
	}
}

apply plugin: 'platform'
```

*bnd-platform* has been tested with Gradle 1.11.

### Tasks

The **platform** plugin comes with several Gradle tasks - the following are the main tasks and build upon each other:

* ***bundles*** - create bundles and write them to **build/plugins**
* ***updateSite*** - create a p2 repository from the bundles and write it to **build/updatesite** (default)
* ***updateSiteZip*** - create a ZIP archive from the p2 repository and write it to **build/updatesite.zip** (default) 

In addition, the ***clean*** task deletes all previously created bundles or update site artifacts.

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
    }
}
```

### Default configuration and configuration priority

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

### Local dependencies

*TODO: implemented but not documented*

### Merged bundles

*TODO: implemented but not documented*

Include plugin
--------------

*TODO: implemented but not documented*

Platform settings
---------------

*TODO: implemented but not documented*

License
-------

This software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
