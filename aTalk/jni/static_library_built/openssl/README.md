## Build openssl for Android
####
<table>
<thead>
<tr><td>library</td><td>version</td><td>platform support</td><td>arch support</td></tr>
</thead>
<tr><td>libopenssl</td><td>1.1.1t</td><td>android</td><td>armeabi-v7a arm64-v8a x86 x86_64</td></tr>
</table>

### Build For Android
- Follow the instructions below to build libopenssl for android
- aTalk v3.1.4 or later release is compatible with libopenssl-1.1.1t<br/>
- When you first exec build-libopenssl4android.sh, it applies the required patches if any to libopenssl<br/>
  Note: the patches defined in libopenssl_patch.sh are for libopenssl-1.0.2<br/>

The ./build-openssl4android.sh script builds both the static libcrypto.a and libssl.a for the various architectures<br/>
as defined in ./_settings.sh i.e. ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")<br/>
All the built libxxx.a and *.h are installed in the ./jni/openssl/android/&lt;ABI>/lib and /include respectively

### Android libopenssl build instructions
- Use prebuilt Android NDK: i.e. <br/>
  export ANDROID_NDK_HOME=/opt/android/android-sdk/ndk/22.1.7171670
- setup the required libopenssl; default "libopenssl-1.1.1t" or<br/>
  change LIB_OPENSSL_GIT value in ./init_libopenssl.sh file if required.<br/>

### use one of the following to build libopenssl i.e.
- If the './openssl' source directory is mssing or with incorrect version, the next command will fetch the source using ./init_libopenssl.sh
- a. for all the ABI's defined in _settings.sh<br/>
  ./build-libopenssl4android.sh
- b. for a specific \<ABI><br/>
  ./build-libopenssl4android.sh \<ABI>

All information given below is for reference only. See aTalk/jni for its implementation.

##============================================##<br/>
Add libopenssl include path to `jni/Android.mk`.

# Android_a.mk
LOCAL_PATH := $(call my-dir)

############### crypto_static ########################<br/>
#target static library libcrypto.a<br/>
include $(CLEAR_VARS)<br/>
LOCAL_MODULE := crypto_static<br/>
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libcrypto.a<br/>
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include<br/>
include $(PREBUILT_STATIC_LIBRARY)<br/>

############### ssl_static ########################
#target static library libssl.a<br/>
include $(CLEAR_VARS)<br/>
LOCAL_MODULE := ssl_static<br/>
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libssl.a<br/>
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include<br/>
include $(PREBUILT_STATIC_LIBRARY)<br/>

#========== jnopenssl (.so library) ==================
include $(CLEAR_VARS)<br/>
LOCAL_MODULE := jnopenssl<br/>
LOCAL_LDLIBS := -llog -lz<br/>
LOCAL_STATIC_LIBRARIES := crypto_static ssl_static<br/>
LOCAL_SRC_FILES := \<br/>
 Hmac.c \<br/>
 OpenSslWrapperLoader.c \<br/>
 SrtpCipherCtrOpenSsl.c<br/>
LOCAL_C_INCLUDES := $(LOCAL_PATH)/android/$(TARGET_ARCH_ABI)/include
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations<br/><br/>

include $(BUILD_SHARED_LIBRARY)<br/>
	
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
