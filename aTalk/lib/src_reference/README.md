The info herein is only kept for reference. All the libraries mentioned here have officially released to maven.
Therefore they are not further for aTalk built.

## lib/bundles directory (has been removed)
contain Jitsi jar libraries that are used in aTalk project built. These jar libraries are <br/>
available online, however not release to jcenter() nor maven(), to allow standard gradle <br/>
fetch during project built. Gradle has problem fetching the libraries from jitsi custom <br/>
repository (maven-metadata.xml not working)

If the ./lib/bundles/*.jar are required to be built from source, the generated jar libraries must <br/>
be placed in the './lib/bundles' for pre-process by build.gradle so they are compatible with aTalk <br/>
interfaces. The jars sources are placed in src_reference directory for reference. <br/>

#### a. zrtp4j-light-4.1.0-jitsi-1-SNAPSHOT.jar
status: auto build with JitPack failed (https://jitpack.io/)<br/>
[![](https://jitpack.io/v/jitsi/zrtp4j.svg)](https://jitpack.io/#jitsi/zrtp4j)

Manual build from source: <br/>
* Fetch the source from https://github.com/jitsi/zrtp4j i.e.
* git clone https://github.com/jitsi/zrtp4j.git zrtp4j, i.e. the working directory
* From Eclipse: Import 'Existing Projects into Workspace'
* Check 'Search for nested projects' => Finish<br/>
You should have two projects imported i.e. 'zrtp4j.git' and 'zrtp4j-light'
* From Project Explorer, locate 'zrtp4j-light/pom.xml'
* Right click 'pom.xml' and select {Run As | Maven build...}
* With configuration prompt enter 'compile' in 'Goals' entry => RUN
* Repeat the above two steps but with 'package' in 'Goals' entry => RUN
* Right-click on the project and perform 'refresh' to see the generated files
* Upon build completed: copy './tartget/zrtp4j-light-4.1.0-jitsi-1-SNAPSHOT.jar' into '${projectDir}/aTalk/lib/bundles' 

#### b. fmj-1.0-SNAPSHOT.jar
Manual build from source: <br/>
* Site: https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/fmj/1.0-SNAPSHOT
* Download two files sources i.e. <br/>
[fmj-1.0-20170519.163031-24-sources.jar](https://github.com/jitsi/jitsi-maven-repository/blob/master/snapshots/org/jitsi/fmj/1.0-SNAPSHOT/fmj-1.0-20170519.163031-24-sources.jar) <br/>
[fmj-1.0-20170519.163031-24.pom from](https://github.com/jitsi/jitsi-maven-repository/blob/master/snapshots/org/jitsi/fmj/1.0-SNAPSHOT/fmj-1.0-20170519.163031-24.pom) <br/>
* Create a working directory e.g. 'fmj' and a subdirectory 'fmj/src'
* Extract the fmj-1.0-20170519.163031-24-sources.jar to 'fmj/src' subdirectory
* Copy the content of the fmj-1.0-20170519.163031-24.pom into a newly created file 'fmj/pom.xml,
* Edit 'pom.xml' and change one of the line \<sourceDirectory>../../src\</sourceDirectory> to \<sourceDirectory>./src\</sourceDirectory>
* From Eclipse: Import 'Existing Projects into Workspace'
* You should see the imported project as fmj
* From Project Explorer, locate 'fmj/pom.xml'
* Right click 'pom.xml' and select {Run As | Maven build...}
* With configuration prompt enter 'compile' in 'Goals' entry => RUN
* Repeat the above two steps but with 'package' in 'Goals' entry => RUN
* Right-click on the project and perform 'refresh' to see the generated files
* Upon build completed: copy './tartget/fmj-1.0-SNAPSHOT.jar' into '${projectDir}/aTalk/lib/bundles' 

#### c. ice4j-2.0.0-SNAPSHOT.jar
Auto build by build.gradle with JitPack from online source repository
Ref: ice4j-2.0.0-20181024.160538-12.jar
https://github.com/jitsi/jitsi-maven-repository/tree/master/snapshots/org/jitsi/ice4j/2.0.0-SNAPSHOT

Note: jitsi/zrtp4j is forked from<br/>
https://github.com/wernerd/ZRTP4J

C++ Implementation of ZRTP protocol - GNU ZRTP C++<br/>
https://github.com/wernerd/ZRTPCPP


