## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scripts are used to build ffmpeg-x264 for aTalk > v1.8.1 i.e. libjnffmpeg.so
aTalk v1.8.1 and above are compatible with ffmpeg-v3.4.6 and libx264-157

## Source files
The ffmpeg-v3.4.6 and libx264-v157 source:<br/>
  a. URL: https://www.ffmpeg.org/releases/ffmpeg-3.4.6.tar.bz2<br/>
  b. URL: https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20190407-2245-stable.tar.bz2<br/>
     contains source for the x264-157 library build; 

## Build instruction
* Fetch and apply patches to the sources in the respectively sub-directories i.e. ffmpeg and x264 i.e.<br/>
  a. to fetch: ./init_update_libs.sh<br/>
  b. apply patch: ./ffmpeg-x264_patch.sh
* You need to build x264 libraries before building the ffmpeg-x264 libraries
* Edit ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64") in x264_build_all.sh that you wish to omit 
* Add option --enable-shared in x264_build_settings.sh#COMMON if you need .so library.<br/>
  Note: aTalk current setup uses only the static *.a libraries.
* Run x264_build_all.sh to build all ABI architectures as defined in \<ABIS> OR
* You may use e.g. x264_build_aremabi-v7a.sh for a specific ABI architecture built
* All the final built libs *.a, *.so and include files are in the ./android/\<ABI>
* aTalk NDK android_a.mk build script for ffmpeg-x264 support use of static .a libraries
* Repeat the above steps for ffmpeg-x264 build
* Copy all the ./android/\<ABI> static libraries and include files to aTalk jni/ffmpeg/\<ABI>

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
* Current aTalk v1.8.1 ffmpeg support may have deprecated functions when using ffmpeg version > 3.4
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
