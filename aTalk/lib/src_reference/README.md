## lib/bundles directory
contain Jitsi jar libraries that are used in aTalk project built. These jar libraries are
available online, however not release to jcenter() or maven(), which is required for gradle fetching
during project built. Also custom repository maven-metadata.xml not compatible

The jars sources are manually downloaded from the following sites and placed in src_reference
directory for reference. If the ./lib/bundles/*.jar are required to be built from source, the
generated jar libraries must be placed in the ./lib/bundles for post process by build.gradle
so as to be compatible with aTalk interface.

a. fmj-1.0-20170519.163031-24.jar
https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/fmj/1.0-SNAPSHOT

b. ice4j-2.0.0-20181024.160538-12.jar
https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/ice4j/2.0.0-SNAPSHOT

c. zrtp4j-light-4.1.0.jar
https://github.com/wernerd/ZRTP4J


### Jitsi reponsitory maven-metadata.xml not compaitible with gradle
Could not resolve all files for configuration ':aTalk:playstoreDebugCompileClasspath'.
> Could not resolve org.jitsi:ice4j:2.0.0-SNAPSHOT.
  Required by: project :aTalk
   > Could not resolve org.jitsi:ice4j:2.0.0-SNAPSHOT.
      > Unable to load Maven meta-data from https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/ice4j/2.0.0-SNAPSHOT/maven-metadata.xml.
         > org.xml.sax.SAXParseException; lineNumber: 44; columnNumber: 89; Attribute name "data-pjax-transient" associated with an element type "meta" must be followed by the ' = ' character.
