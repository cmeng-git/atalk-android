## Build scripts for ffmpeg-x264 with android-ndk for aTalk
The scripts are used to build ffmpeg-x264 based on ffmpeg and x264 for aTalk

## Source files
* Only ffmpeg source v1.0.10 is compatible with aTalk v1.7.3 (ffmpeg.java);
  libvpx-152 is max version that is compatible with ffmpeg v1.0.10<br/>
  a. http://ffmpeg.org/releases/ffmpeg-1.0.10.tar.gz<br/>
  b. https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20180806-2245-stable.tar.bz2<br/>
     contains source for the x264 v1.5.2 library build; 

## Build instruction
* Fetch and apply patches to the sources into the respectively sub-directories i.e. ffmpeg and x264 i.e.<br/>
  a. ./init_update_libs.sh<br/>
  b. ./ffmpeg-x264_patch.sh
* You need to build x264 libraries before building the ffmpeg-x264 libraries
* Edit ABIS in x264_build_all.sh that you wish to omit 
* Add option --enable-shared in x264_build_settings.sh#COMMON if you need .so library .
* Run x264_build_all.sh to build all cpu architectures as defined in \<ABIS> OR
* You may use e.g. x264_build_aremabi-v7a.sh for a specific cpu architecture built
* All the final built libs *.a, *.so and include files are in the ./android/\<ABI>
* aTalk NDK android_x.mk build script for ffmpeg-x264 support use of either static .a or shared .so libraries
* Repeat the above steps for ffmpeg-x264 build
* Copy all the ./android/\<ABI> static libraries and include files to aTalk jni/ffmpeg/\<ABI>
* Note: ffmpeg configure has been patched to allow build with x264-157 but only for undefined symbols testing only.<br/>
  Have the following errors if run:
  <br/>net.sf.fmj.media.Log.error() Failed to realize: net.sf.fmj.media.ProcessEngine@584fa51
  <br/>net.sf.fmj.media.Log.error()   Cannot build a flow graph with the customized options:
  <br/>net.sf.fmj.media.Log.error()     Unable to transcode format: YUV Video Format: Size = [width=720, height=960] MaxDataLength = -1 DataType = class [B yuvType = -1 StrideY = -1 StrideUV = -1 OffsetY = -1 OffsetU = -1 OffsetV = -1
  <br/>net.sf.fmj.media.Log.error()       to: H264/RTP, fmtps={packetization-mode=1}
  <br/>net.sf.fmj.media.Log.error()       outputting to: raw.rtp

## Linking with versioned shared library in Android NDK
* Automatic patches have been included in the x264 build script - below steps are kept for reference only.
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
* Current aTalk v1.7.3 ffmpeg support have many deprecated functions when using ffmpeg version > 1.0.10
* If a higher ffmpeg version is required, you need to update both the following two files:
  - .jni\ffmpeg\org_atalk_impl_neomedia_codec_FFmpeg.c and
  - atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java
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
