eclipse-p2-minimal.tar.gz
-------------------------

This is the original minimal Eclipse product added for creating update sites with bnd-platform.

**Please note that update sites generated with this product sometimes miss plugins.**

The cause is unclear but it is fixed with newer Eclipse versions.
The attempt to create a working update product was not successful so far (see below).
Thus the current recommendation is to use a current full version of Eclipse (e.g. 2023-09).


Minimal product build
---------------------

Build for minimal Eclipse product adapted to use a newer Eclipse version.

**Status:**

Product can be built, but using it with bnd-platform results in the following errors w/ no update site created:

```
Unable to obtain required service: org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager
Unable to obtain required service: org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager
```

### Building

Run

```
mvn clean install
```

to build the products which you then can find in `product/target/products/`.
