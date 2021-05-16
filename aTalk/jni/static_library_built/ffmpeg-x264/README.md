## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scripts are used to build ffmpeg-x264 for aTalk >= v2.6.1 i.e. libjnffmpeg.so<br/>
aTalk v2.6.1 and above are compatible with ffmpeg-v4.4 and libx264-161.3049

### Source files:
The ffmpeg-v4.4 and libx264-v161 source are downloaded from:<br/>
  a. wget https://www.ffmpeg.org/releases/ffmpeg-4.4.tar.bz2<br/>
  b. git clone https://code.videolan.org/videolan/x264.git --branch stable<br/>
     contains source for the x264-161.3049 library build at the time of writting;

### ffmpeg build instructions:
* Optional - execute script below to fetch libraries sources for both ffmpeg and x264<br/>
  ./init_update_libs.sh<br/>
  Note: You must run this script if you want to update the existing ffmpeg and/or x264 sources<br/>
* Run ffmpeg-atalk_build.sh to build all ABIS architectures as defined in _settings.sh &lt;ABIS> OR<br/>
  e.g. ffmpeg-atalk_build.sh arm64-v8a for a specific 'arm64-v8a' architecture built
* All the final built static libs *.a and include files are installed in the ./aTalk/jni/ffmpeg/android/&lt;ABI>
* Note:<br/>
 a. Before building, ffmpeg-atalk_build.sh fetches source if not found and applies the required patches to the libraries sources<br/>
 b. aTalk current android_a.mk script for ffmpeg-x264 support uses only static .a libraries<br/>
 c. You may edit _settings.sh ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64") that you wish to omit OR<br/>
 set STANDALONE_TOOLCHAINS to 0:SDK toolchains OR 1:standalone toolchains<br/>
 Add option --enable-shared in _x264_build.sh and _ffmpeg_build.sh if you need .so library.<br/>

## Linking with versioned shared library in Android NDK
* Automatic patches for .so have been included in the x264 build script - below steps are kept for reference only.
* Android has an issue with loading versioned .so libraries for x264:<br/>
  i.e. java.lang.UnsatisfiedLinkError: dlopen failed: library "libx264.so.147" not found
* Perform the following patches if you want to link with shared .so libraries for x264.
  - x264_build.all.sh to build all libx264.so
  - use GHex to change file content "libx264.so.147" to "libx264_147.so"
  - change filename from libx264.so.147 to libx264_147.so
  - Note: the .so filename must match with the file changed content and must be changed before ffmpeg_build_all.sh
  - scanelf -qT *.so to ensure all shared libraries do not contain text relocations - rejected by >= android-23

## Note:
* The scripts in this folder are not compatible with Unified Headers:
* See https://android.googlesource.com/platform/ndk/+/master/docs/UnifiedHeaders.md#supporting-unified-headers-in-your-build-system
* Current aTalk v2.6.1 ffmpeg support have updated all deprecated functions when using ffmpeg version >= 4.4
* If a higher ffmpeg version is required, you need to update both the following two files:
  - .jni\ffmpeg\FFmpeg.c and
  - atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java
* Building of arch64-bit required api-21 and must apply throughout all scripts and in AS JNI application.mk<br/>
i.e. APP_PLATFORM := android-21

===============================================================
## See below links for more information

### The scripts have been modified and enhanced using info from site:
* https://github.com/IljaKosynkin/FFmpeg-Development-Kit

* https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e
android built script is designed based on ffmpeg-3.3.3 source building on ubuntu

### Other relevant information
https://yesimroy.gitbooks.io/android-note/content/compile_x264_for_android.html
[Document](https://yesimroy.gitbooks.io/android-note/content/ffmpeg_build_process.html)
