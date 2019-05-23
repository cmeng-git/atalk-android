## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scripts are used to build ffmpeg-x264 for aTalk > v1.8.1 i.e. libjnffmpeg.so<br/>
aTalk v1.8.1 and above are compatible with ffmpeg-v3.4.6 and libx264-157

### Source files:
The ffmpeg-v3.4.6 and libx264-v157 source:<br/>
  a. URL: https://www.ffmpeg.org/releases/ffmpeg-3.4.6.tar.bz2<br/>
  b. URL: https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20190407-2245-stable.tar.bz2<br/>
     contains source for the x264-157 library build; 

### ffmpeg build instruction:
* Optional - execute script below to fetch libraries sources for both ffmpeg and x264<br/>
  ./init_update_libs.sh<br/>
* Run ffmpeg-atalk_build.sh to build all ABIS architectures as defined in _settings.sh \<ABIS> OR<br/>
  e.g. ffmpeg-atalk_build.sh arm64-v8a for a specific 'arm64-v8a' architecture built
* All the final built libs *.a, *.so and include files are in the ./android/\<ABI>
* Copy all the ./android/\<ABI> static lib and include files to aTalk jni/ffmpeg/\<ABI>
* Note:<br/>
 a. Before build, ffmpeg-atalk_build.sh fetches source if not found and applies the required patches to the libraries sources<br/>
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
* Current aTalk v1.8.2 ffmpeg support may have deprecated functions when using ffmpeg version > 3.4.6
* If a higher ffmpeg version is required, you need to update both the following two files:
  - .jni\ffmpeg\org_atalk_impl_neomedia_codec_FFmpeg.c and
  - atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java
* Building of arch64-bit required api-21 and must apply throughout all scripts and in AS JNI application.mk
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
