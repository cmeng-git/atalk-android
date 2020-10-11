## Build openssl for Android
####
<table>
<thead>
<tr><td>library</td><td>version</td><td>platform support</td><td>arch support</td></tr>
</thead>
<tr><td>libopenssl</td><td>1.0.2</td><td>android</td><td>armeabi-v7a arm64-v8a x86 x86_64</td></tr>
</table>

### Build For Android
- Follow the instructions below to build libopenssl for android
- aTalk v1.8.2 release is compatible with libopenssl-1.0.2<br/>
- When you first exec build-libopenssl4android.sh, it applies the required patches to libopenssl<br/>
  Note: the patches defined in libopenssl_patch.sh is for libopenssl-1.0.2<br/>
  
The ./build-openssl4android.sh script build the static libssl.a for the various architectures as defined in ./_settings.sh<br/>
i.e. ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")<br/>
All the built libopenssl.a and *.h are installed in the ./output/android/\<ABI>/lib and ./output/android/\<ABI>/include respectively

**Android libopenssl build instructions:**
``
## Use Android NDK: android-ndk-r15c
export ANDROID_NDK=/opt/android/android-ndk-r15c

## setup the required libopenssl; default "libopenssl-1.0.2u" or change LIB_OPENSSL_GIT in ./init_libopenssl.sh
./init_libopenssl.sh

## use one of the following to build libopenssl i.e.
If the './openssl' source directory is mssing, the next command will fetch the source using ./init_libopenssl.sh

# a. for all the ABI's defined in _settings.sh
  ./build-libopenssl4android.sh

# b. for a specific <ABI>
  ./build-libopenssl4android.sh <ABI> 
```

Copy the static libs and includes in `./output/android/<ABI>` directories to the android project
jni directory i.e. aTalk/jni/openssl/android/\<ABI>

All information given below is for reference only. See aTalk/jni for its implementation.

##### ============================================
#### Android.mk makefile - for other project
Add libopenssl include path to `jni/Android.mk`. 

```
#Android.mk

LOCAL_PATH := $(call my-dir)

############### crypto_static ########################
# target static library
include $(CLEAR_VARS)
LOCAL_MODULE := crypto_static
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)

############### ssl_static ########################
# target static library
include $(CLEAR_VARS)
LOCAL_MODULE := ssl_static
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libssl.a
include $(PREBUILT_STATIC_LIBRARY)

# ========== jnopenssl (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnopenssl
LOCAL_LDLIBS := -llog -lz
LOCAL_STATIC_LIBRARIES := crypto_static ssl_static
LOCAL_SRC_FILES := \
 Hmac.c \
 OpenSslWrapperLoader.c \
 SrtpCipherCtrOpenSsl.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)
	
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




