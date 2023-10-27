### Unreleased

### 3.0.0

 - **BREAKING CHANGE** Rename `platform` configuration to `bndplatform` to resolve name conflict introduced in Gradle 5
 - Change default Eclipse artifact to a full version of Eclipse 2023-09 (because the minimal product used so far produces incomplete update sites in some cases)
 - Update bndlib to 6.4.1 to fix issue with invalid Zip files occurring in recent Java versions (e.g. 17.0.8)

### 2.0.1

 - Fix incompatibility with Gradle download task (that prevented default eclipse artifact download)

### 2.0.0

 - Prevent ClassCastException in case of new artifactVersion - @florianesser
 - Enable specifying ws/os/arch for plugins embedded in features - @Diewi
 - Retain PlatformFilters for wrapped bundles - @Diewi
 - Use all available platform specifiers in the feature generation - @Diewi
 - Make Java binary used to run Publisher configurable - @florianesser
 - Added support for feature license - @sdkrach
 - Add minimal eclipse product to repository - @stempler
 - Omit configuration when creating source artifact dependency (for compatibility to Gradle 6+) - @jona
 - Fix extracting information from pom.xml - @urferr


### 1.7.0

 - Don't fail when merging Jars w/o manifest file - @qqilihq
 - remove 'Export-Package', 'Import-Package', 'Private-Package' for source bundle - @missedone
 - Prevent duplication of SNAPSHOT source bundles - @florianesser
 - If no 'eclipseHome' is defined eclipse indigo is not downloaded any more, but a minimal eclipse app with a size of 4.8 MB. @SimonScholz
 - The build folder does not have a eclipse-downloads folder any more, but the minimal eclipse app is stored in the gradleHomeDir, where the Gradle wrappers and other Gradle configurations are stored. @SimonScholz
 - A new task called 'potentialOptionalImports' has been added. (See README for further information) @SimonScholz
 - Public tasks are now more visible under the bnd-platform group when calling the 'tasks' task. @SimonScholz
 - The Gradle wrapper of this project has been updated to Gradle 4.6 @SimonScholz
 - The bnd-platform plug-in now applies Gradle's BasePlugin and therefore reuses the 'clean' task from Gradle's BasePlugin, therefore it integrates with other plug-ins more smoothly and will support Gradle 5 better, which does not allow to have a custom clean task. @SimonScholz
 - The 'platform' closure now has a 'generatePlatformFeature' which states if a general feature should be created. In case custom features are generated you might not want to have an additional "generated platform feature" besides your own features. (default: **true**) @SimonScholz


### 1.5.0

 - Folder where Eclipse is downloaded to is now configurable - @missedone
 - Support new folder structure for downloaded Eclipse for Mac OS X - @missedone
 - Update dependency to Gradle download plugin

### 1.4.0

 - Compile for Java 7
 - Added option to include manifest headers that allow reconstructing the orginal Maven artifact identifier
 - Gradle and dependencies updates and fixes by @nedtwigg

### 1.3.0

 - Fixed compatibility with current Gradle versions (Gradle 2.6-2.10)
 - The platform and platformaux configurations can now be created elsewhere to allow bnd-platform to be applied lazily to a project (contribution by @nedtwigg)
 - Support for generated qualifiers representing increasing version numbers instead of hashes (see `hashQualifierMap` configuration setting)
 - Don't fail the build if determining imports fails for a dependency

### 1.2.0

 - Qualified plugin ID (`org.standardout.bnd-platform`) for use with new Gradle plugin repository
 - Allow including additional features in a defined feature (using `includes << '<featureId>'` inside a feature)
 - Create a matching source feature for a defined feature (if `fetchSources` is enabled)
 - Define features (Eclipse / p2) that contain specific bundles and their dependencies
 - Diverse bug fixes

### 1.0.0

 - Changed default version strategies to ignore micro/PATCH
 - Allow bnd configurations for multi-file dependencies

### 0.4.0

 - Added alternative method for adding bnd instructions (`instructions`, taking a Map / named instruction parameters)
 - Remove signatures from Jars that are wrapped as bundles (configurable)

### 0.3.0

 - Implemented qualifiers based on bnd configuration hash (to prevent issues w/ cached bundles, e.g. in Eclipse)
 - `optionalImport`/`prependImport` retain attributes of other packages
 - Support dependency specific imports configuration
 - Added new **platformaux** configuration
 - Support different import version strategies
 - Add package import versions to bundles (experimental, must be enabled explicitly)
 - Diverse bug fixes and improvements

### 0.2.0 - initial version
