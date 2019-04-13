## Build libvpx for Android
####
<table>
<thead>
<tr><td>library</td><td>version</td><td>platform support</td><td>arch support</td></tr>
</thead>
<tr><td>libvpx</td><td>1.7.0</td><td>android</td><td>armeabi-v7a arm64-v8a x86 x86_64</td></tr>
</table>

### Build For Android
- Follow the instructions below to build libvpx for android
- aTalk v1.8.1 release is only compatible with libvpx-1.6.1+ (./sources/master-20171013.tar.gz)<br/>
Note: The compiled libjnvpx.so for aTalk has problem when exec on x86_64 android platform (libvpx asm source errors):<br/>
  org.atalk.android A/libc: Fatal signal 31 (SIGSYS), code 1 in tid 5833 (Loop thread: ne), pid 4781 (g.atalk.android)<br/>
  Use video codec x264 if you are running aTalk on X86/x86_64 platforms
- When you first exec build-libvpx4android.sh, it applies the required patches to libvpx<br/>
  Note: the patches defined in patch_libvpx.sh is for libvpx-1.7.0 and libvpx-1.6.1+ (master-20171013.tar.gz for aTalk)<br/>
  
The ./build-libvpx4android.sh script build the static libvpx.a for the various architectures as defined in ./_settings.sh<br/>
i.e. ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")<br/>
All the built libvpx.a and *.h will be placed in the ./output/android/\<ABI>/lib and ./output/android/\<ABI>/include respectively

**Android libvpx build instructions:**
```
### git clone vpx-android directory into your linux working directory.
git clone https://github.com/cmeng-git/vpx-android.git ./vpx-android
cd vpx-android

### Use Android NDK: android-ndk-r15c
export ANDROID_NDK=/opt/android/android-ndk-r15c

### skip below step and use ./sources/master-20171013.tar.gz instead for aTalk v1.8.1 
### setup the required libvpx e.g. "libvpx-1.7.0"; must also change the LIB_VPX variable in ./build-libvpx4android.sh
./init_libvpx.sh libvpx-1.7.0

# use one of the following to build libvpx i.e.
# for all the ABI's defined in _share.sh
./build-libvpx4android.sh

# for all the specific <ABI>
./build-libvpx4android.sh <ABI> 
```

Copy `.output/android/lib/*`, `./output/include/*` directories to the android project
jni directory e.g. aTalk/jni/vpx/android

Similarly copy all the include directories to the android project jni directory.

Note: All information given below is for reference only. See aTalk/jni for its implementation.

##### ============================================
#### Android.mk makefile - for other project
Add libvpx include path to `jni/Android.mk`. 

```
#Android.mk

include $(CLEAR_VARS)
LOCAL_MODULE := libvpx
LOCAL_SRC_FILES := Your libVPX Library Path/$(TARGET_ARCH_ABI)/libvpx.a
include $(PREBUILT_STATIC_LIBRARY)

LOCAL_C_INCLUDES := $(LOCAL_PATH)/Your libvpx Include Path/libvpx
LOCAL_STATIC_LIBRARIES := libvpx
LOCAL_LDLIBS := -lz
	
```

License
-------

    ffmpeg, android static library for aTalk VoIP and Instant Messaging client
    
    Copyright 2016 Eng Chong Meng
        
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.




