## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scriptd are used to build ffmpeg-x264 based on ffmpeg and x264 for aTalk

## Source files
* ffmpeg source is 1.0.10 for aTalk compatibility (ffmpeg.java)
* FFMpeg_1.0.10_aTalk.tar.gz is the patched version of FFmpeg-release-1.0.zip
* x264.tar.gz is for contains source for the x264 v1.4.7 library build

## Build instruction
* Extract the sources into the respectively sub-directories i.e. ffmpeg and x264
* You need to build x264 libraries before building the ffmpeg-x264 libraries
* You can use e.g. x264_build_aremabi-v7a.sh script for specific cpu architecture built OR
* x264_build_all.sh to build all cpu architectures
* commented out those architecture you wish to omit in x264_build_settings.sh
* add option --enable-shared if you need .so library in x264_build_settings.sh#COMMON.
* aTalk NDK build script supoort use of either static .a or shared .so libraries
* All the final built libs *.a, *.so and include files are in the ./android/$CPU
* The above description equally applied to ffmpeg-x264 build

## Linking with versioned shared library in Android NDK
* Android has an issue with loading versioned .so libraries for x264:
* i.e. java.lang.UnsatisfiedLinkError: dlopen failed: library "libx264.so.147" not found
* Following patches have been included in the x264 build script - keep here for reference.

* Perform the following patches if you want to link with shared .so libraries for x264.
1. x264_build.all.sh to build all libx264.so
2. use GHex to change file content "libx264.so.147" to "libx264_147.so"
3. change filename from libx264.so.147 to libx264_147.so
4. Note: the .so filename must match with the file changed content and must be changed before ffmpeg_build_all.sh
5. scanelf -qT *.so to ensure all shared libraries do not contain text relocations - rejected by android >=API-23

## Note:
* The scripts in this folder are not compatible with Unified Headers:
* See https://android.googlesource.com/platform/ndk/+/master/docs/UnifiedHeaders.md#supporting-unified-headers-in-your-build-system
* Current aTalk v1.4.5 ffmpeg support have many deprecated functions when using ffmpeg version > 1.0.10
* If a higher ffmpeg version is required, you need clean up both the .jni\ffmpeg\org_atalk_impl_neomedia_codec_FFmpeg.c and
atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java
* There is a problem with ffmpeg-x264_build_arm64-v8a.sh, it fails due to undefined references in pre-built x264 libraries 
* Building of arch64-bit required api-21 and must apply throughout all scripts and in AS JNI application.mk
i.e. APP_PLATFORM := android-21

===============================================================
## See below links for more information

### The scripts have been modified and enhanced using info from site:
* https://github.com/IljaKosynkin/FFmpeg-Development-Kit

* https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e
android built script is designed based on ffmpeg-3.3.3 source building on ubuntu
Note: v3.3.3 is currently not compatible with atalk ffmpeg API call unless changes are made
to the api org_atalk_impl_neomedia_codec_FFmpeg.c and org_atalk_impl_neomedia_codec_FFmpeg.h files

### Other relevant information
https://yesimroy.gitbooks.io/android-note/content/compile_x264_for_android.html
[Document](https://yesimroy.gitbooks.io/android-note/content/ffmpeg_build_process.html)

