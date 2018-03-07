Note the script is not compatible with Unified Headers:
* https://android.googlesource.com/platform/ndk/+/master/docs/UnifiedHeaders.md#supporting-unified-headers-in-your-build-system

android built script is designed based on ffmpeg-3.3.4 source building on ubuntu 16.04
Note: v3.3.4 is currently not compatible with present atalk ffmpeg API call unless changes are made
to the api org_atalk_impl_neomedia_codec_FFmpeg.c and org_atalk_impl_neomedia_codec_FFmpeg.h files
The build script is used to build ffmpeg-x264 based on ffmpeg v1.0.10 for aTalk

You may use individual e.g. build_android_aremabi-v7a.sh script or build_android_all.sh
* commented out those architecture you wish to omit in build_all.sh
* add option --enable-shared if you need .so library in build_all.sh#COMMON. aTalk use only static .a libraries
* all libs *.a and *.so and include files are in the predefined prefix

Note:
current aTalk FFmpeg have many deprecated functions etc for ffmpeg version 0.9.0
need clean up on .jni\ffmpeg\org_atalk_impl_neomedia_codec_FFmpeg.c and
atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java


See link for more information:
* https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e

