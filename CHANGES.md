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
