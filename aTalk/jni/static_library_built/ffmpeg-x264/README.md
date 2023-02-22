## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scripts are used to build ffmpeg-x264 for aTalk >= v2.6.1 i.e. libjnffmpeg.so<br/>
aTalk v2.9.4 and above are compatible with ffmpeg-v4.4.2 and libx264-163.3060<br/>
aTalk v3.1.4 and above are compatible with ffmpeg-v5.1.2 and libx264-164.3095

### Source files:
The ffmpeg-v5.1.2 and libx264-v164 source are downloaded using the init_libs_ffmpeg_x264.sh scripts:<br/>
  a. wget https://www.ffmpeg.org/releases/ffmpeg-5.1.2.tar.bz2<br/>
  b. git clone https://code.videolan.org/videolan/x264.git --branch stable<br/>
     contains source for the x264-164.3095 library build at the time of writing;

### ffmpeg build instructions:
* Run build-ffmpeg4android.sh to build all ABIS architectures as defined in _settings.sh &lt;ABIS> OR<br/>
  e.g. build-ffmpeg4android.sh arm64-v8a for the specific 'arm64-v8a' architecture built
* Optional - execute script below to fetch libraries sources for both ffmpeg and x264 i.e. ./init_libs_ffmpeg_x264.sh. <br/>
  Note: You only need to run this script manually if you want to update the existing ffmpeg and/or x264 sources<br/>
* All the final built static libs *.a and include files are installed in the ./aTalk/jni/ffmpeg/android/&lt;ABI>
* Note:<br/>
 a. Before building, build-ffmpeg4android.sh fetches the source if none is found or with an incorrect version.<br/>
    The versions are defined in the file i.e.:<br/>
    VERSION_FFMPEG=5.1<br/>
    VERSION_X264=164<br/>
  b. Applies the required patches if any to the libraries sources if any.<br/>
  b. Current aTalk android_a.mk script for ffmpeg-x264 support uses only static *.a libraries<br/>
  c. You may edit _settings.sh ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64") that you wish to omit OR<br/>
  set STANDALONE_TOOLCHAINS to 0:SDK toolchains OR 1:standalone toolchains<br/>
  Add option --enable-shared in _x264_build.sh and _ffmpeg_build.sh if you need .so library.<br/>

## Linking with versioned shared library in Android NDK
* Automatic patches for .so have been included in the x264 build script - below steps are kept for reference only.
* Android has an issue with loading versioned .so libraries for x264 e.g.:<br/>
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
* Current aTalk >=v3.0.0 ffmpeg support have updated all deprecated functions when using ffmpeg version >= 5.1
* If a higher ffmpeg version is required, you may need to update both the following two files:
  - .jni\ffmpeg\FFmpeg.c and
  - atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java
* Building of arch64-bit required api-21 and must apply throughout all scripts and in AS JNI application.mk<br/>
i.e. APP_PLATFORM := android-21

===============================================================

## Other information:

* The scripts have been modified and enhanced using info from site:
* https://github.com/IljaKosynkin/FFmpeg-Development-Kit
* https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e
android built script is designed based on ffmpeg-3.3.3 source building on ubuntu
* Other relevant information<br/>
https://yesimroy.gitbooks.io/android-note/content/compile_x264_for_android.html
[Document](https://yesimroy.gitbooks.io/android-note/content/ffmpeg_build_process.html)
