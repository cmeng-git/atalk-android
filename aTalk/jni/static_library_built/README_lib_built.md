The static_library_built directory contains the scripts to build all the necessary jni static libraries used in aTalk.
The built static libraries are used in Android Studio NDK built process to generate the shared libraries

Each static library is built using the shell scripts in each module sub-directory.<br/>
The scripts are written to run on linux/Ubuntu OS with proper development environment setup i.e.
* sudo apt-get --quiet --yes install build-essential git autoconf libtool pkg-config gperf gettext yasm python-lxml
* The compiled static libraries for each module are installed into the aTalk/jin/&lt;module>/android/&lt;ABI>.

Please refer to each sub-directory for more information.

jni/ffmpeg, jni/vpx, jni/openssl etc
-----------------------------------------
* Contain the interface files for java to native libraries; and android_a.mk to build libjnXXX.so
* The following 3 libraries are required to be built externally for integration into aTalk i.e.:

1. ffmpeg-x264:<br/>
   Build static libraries for the various architectures used for aTalk/jin/ffmpeg
2. libvpx:<br/>
   Build static libraries for the various architectures used for aTalk/jin/vpx
3. openssl:<br/>
   Build static libraries for the various architectures used for aTalk/jin/openssl

Built all
--------------------------------------
* Instead of build each static library separately, you may execute the build-jnilibs4atalk.sh<br/>
to build all the above three static libraries
* You may also run the task "initJniLibs" from within the gradle to build the 3 static libraries.