## lib/bundles directory
contain Jitsi jar libraries that are used in aTalk project built. These jar libraries are
available online, however not release to jcenter() or maven(), which is required for gradle fetching
during project built.

The jars sources are manually downloaded from the following sites and placed in src_reference
directory for reference. If the ./lib/bundles/*.jar are required to be built from source, the
generated jar libraries must be placed in the ./lib/bundles for post process by build.gradle
so as to be compatible with aTalk interface.

a. dhcp4java-1.00.jar
https://sourceforge.net/projects/dhcp4java/files/dhcp4java/dhcp4java-1.00/

b. fmj-1.0-20170519.163031-24.jar
https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/fmj/1.0-SNAPSHOT

c. ice4j-2.0.0-20181024.160538-12.jar
https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/ice4j/2.0.0-SNAPSHOT

d. json-simple-1.1.1.jar
https://mvnrepository.com/artifact/com.googlecode.json-simple/json-simple/1.1.1
Note: this jar is actually available on maven(), but direct fetching by gradle leads to duplication error

e. zrtp4j-light-4.1.0.jar
https://github.com/wernerd/ZRTP4J


## lib/samck directory
aTalk source has been modified to work with smack v4.3.2 release. Later aTalk ports to use
smack 4.4.0 to take advantages on new features implemented in v4.4.0. However aTalk earlier 
source modification for v4.4.3 is only compatible to smack v4.4.0-alpha2 which is available on
https://igniterealtime.org/repo/org/igniterealtime/smack/smack-core/4.4.0-alpha2-SNAPSHOT/

but yet to be released to maven() repository. Hence some of required alpha2 jar releases are
temporary downloaded from the above until smack has released the alpha2 into the maven() repository.