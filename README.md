## aTalk (Jabber / XMPP)
- an encrypted instant messaging with video call and GPS features for android

<p align="center">
    <a href="https://cmeng-git.github.io/atalk">
        <img src="./art/atalk.png" alt="aTalk">
    </a>
    &nbsp;
    <a href="https://cmeng-git.github.io/atalk/faq.html">
        <img src="./art/help.png" alt="FAQ">
    </a>
    &nbsp;
    <a href="https://play.google.com/store/apps/details?id=org.atalk.android&hl=en">
        <img src="./art/google_play.png" alt="Google PlayStore">
    </a>
    &nbsp;
    <a href="https://f-droid.org/en/packages/org.atalk.android/">
        <img src="./art/fdroid-logo.png" alt="F-Droid">
    </a>
    &nbsp;
    <a href="https://www.youtube.com/watch?v=9w5WwphzgBc">
        <img src="./art/youtube.png" alt="YouTube">
    </a>
</p>


## Features
aTalk is an xmpp client designed for android and supports the following features:
* See <a href="https://xmpp.org/doap/atalk.doap">atalk.doap</a> for list of XEP's standards supported by aTalk XMPP client.
* Instant messaging via End-to-End [OMEMO](https://conversations.im/omemo/) encryption.
* SSL Certificate authentication, DNSSEC and DANE Security implementation for enhanced secure Connection Establishment.
* OMEMO encryption in chat or multi-user chat session enhancing privacy and security.
* Support Stickers, Bitmoji and Emoji rich contents sending via Google Gboard.
* Support secured file sharing via HttpFileUpload, or via Jingle Encrypted Transport (JET).
* Fault-tolerance file transfer algorithm, via HttpFileUpload, Jingle File Transfer, and Legacy In-Band/SOCK5 Bytestreams protocols.
* Auto accept file transfer with max file size option.
* Support share, quote and forward of messages and media with previews prior to sending.
* Support multi-instances of audio media player with functions: Play, Pause, Resume, Stop and Seek.
* Support unread message badges in contact and chatroom list views.
* User selectable option for heads-up notification, and user defined quiet hours.
* Support Text to Speech and speech recognition UI with enable option per chat session (contact and group chat).
* Support 50 Languages Translation for sent/received messages in chat/multi-user chat sessions.
* Language Translation server may use aTalk default or user defined URL.
* Support Call transfer, and call waiting to hold or answer second incoming call.
* Implement Jabber VoIP-PBX gateway Telephony, allowing PBX phone call via service gateway.
* Support video media call with SDES, DTLS SRTP, and ZRTP crypto algorithms using SHA-2 384, 256bit ciphers encryptions.
* Media call setup via either XEP-0353: Jingle Message Initiation, or XEP-0167:  Jingle RTP Sessions protocol.
* Message Archive Management, and Message Carbons synchronizing chat history across all instances of the account.
* GPS-Location implementation as standalone tool, send locations to buddy for real-time tracking or playback animation.
* A 360° street view of your current location use for self-guided tour. The street view tracks and follows your direction of sight.
* Update user avatar with integrated photo editor, zooming and cropping with ease. 
* Support both XEP-0153: vCard-Based Avatar, and XEP-0084: User Avatar protocol Implementations.
* Support XEP-0048: Bookmarks for multi-user chatroom.
* Support XEP-0070: Verifying HTTP Requests via XMPP.
* Retract of sent messages, and Last message correction in chat/multi-user chat sessions.
* Multiple accounts creation, server registration via XMPP or BOSH protocol.
* In-Band Registration with CAPTCHA option; needs support from server.
* Support for XEP-0191: Blocking Command to block communications with a specific contact and domain
* Stream Management and mobile network ping interval self-tune optimization support.
* Multi-language UI support (Arabic, Indonesian, Chinese, English, Finnish, German, Portuguese (Brazil), Russian, Slovak and Spanish).


## Acknowledgements

Libraries used in this project:

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/index.html)
* [Android-EasyLocation](https://github.com/akhgupta/Android-EasyLocation)
* [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player)
* [apache-mime4j-core](https://github.com/apache/james-mime4j)
* [Argos Translate](https://github.com/argosopentech/argos-translate)
* [bcg729](https://github.com/BelledonneCommunications/bcg729)
* [bouncycastle](https://github.com/bcgit/bc-java)
* [ckChangeLog](https://github.com/cketti/ckChangeLog)
* [commons-codec](https://github.com/apache/commons-codec)
* [commons-text](https://commons.apache.org/proper/commons-text/)
* [Dexter](https://github.com/Karumi/Dexter)
* [FFmpeg](https://github.com/FFmpeg/FFmpeg)
* [fmj-jitsi](https://github.com/jitsi/fmj)
* [glide](https://github.com/bumptech/glide)
* [Google Play Services](https://developers.google.com/android/guides/overview)
* [httpcore](https://hc.apache.org/httpcomponents-client-4.5.x/)
* [IPAddress](https://github.com/seancfoley/IPAddress)
* [ice4j](https://github.com/jitsi/ice4j)
* [jbosh](https://github.com/igniterealtime/jbosh)
* [jitsi](https://github.com/jitsi/jitsi)
* [jitsi-android](https://github.com/jitsi/jitsi-android)
* [jmdns](https://github.com/jmdns/jmdns)
* [jxmpp-jid](https://github.com/igniterealtime/jxmpp)
* [libjitsi](https://github.com/jitsi/libjitsi)
* [LibreTranslate Java Restful Client](https://github.com/stokito/libretranslate-java)
* [libvpx](https://github.com/webmproject/libvpx)
* [media3-exoplayer](https://github.com/androidx/media)
* [Mime4j](https://james.apache.org/mime4j/)
* [miniDNS](https://github.com/MiniDNS/minidns)
* [Noembed](https://noembed.com/)
* [okhttp"](https://github.com/square/okhttp)
* [opensles](https://github.com/openssl/openssl )
* [openSSL](https://www.openssl.org/source/)
* [Opus](https://opus-codec.org/)
* [osgi.core](http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0)
* [osmdroid](https://github.com/osmdroid/osmdroid)
* [sdes4j](https://github.com/ibauersachs/sdes4j)
* [sdp-api](https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api)
* [signal-protocol-java](https://github.com/signalapp/libsignal-protocol-java)
* [smack](https://github.com/igniterealtime/Smack)
* [speex](https://github.com/xiph/speex)
* [Timber](https://github.com/JakeWharton/timber)
* [TokenAutoComplete](https://github.com/splitwise/TokenAutoComplete)
* [uCrop](https://github.com/Yalantis/uCrop)
* [weupnp](https://github.com/bitletorg/weupnp)
* [x264](https://git.videolan.org/git/x264.git)
* [zrtp4j-light](https://github.com/jitsi/zrtp4j)

Other contributors:
* [Others](https://github.com/cmeng-git/atalk-android/graphs/contributors)

## Documentation
* [aTalk site](https://cmeng-git.github.io/atalk)
* [FAQ](https://cmeng-git.github.io/atalk/faq.html)
* [Release Notes](https://github.com/cmeng-git/atalk-android/blob/master/aTalk/ReleaseNotes.txt)

## aTalk apk build for android
* Following development environment setups are used to build aTalk.apk<br/>
  a. Android Studio Narwhal | 2025.1.1<br/>
  b. distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
  c. classpath 'com.android.tools.build:gradle:8.11.1'
  d. Ubuntu 24.04 with proper environment setup for development<br>
  Note: all shell scripts in aTalk are written to run on linux OS only e.g. Ubuntu
* aTalk uses native jni libraries, required to be downloaded and built manually prior to android NDK build.<br/>
  The static jni libraries must be built prior to android studio apk build process.
* You must run the gradle task 'buildJniStaticLibs' to build all the jni static libraries used in aTalk.<br/>
  See aTalk/jni/static_library_build directory readme files for more information<br/>
  on linux environment setup, and the execution of jni libraries build scripts.
* You must run the gradle task 'initJniLibs' to fetch the jni libraries prior to android ndk build process.<br/>
  These libraries sources include: opus, speex, ogg and g729

## Feedback and Contributions
If you can't find your language in UI and would like to help then translate the app on https://toolate.othing.xyz/projects/atalk/.
Or copy the [strings.xml](./aTalk/src/main/res/values/strings.xml) to `values-LOCALE` where the `LOCALE` is language-COUNTRY e.g. `pt-rBR`. 
Then create a pull request or forward the file to the developer.

If you have found bug, wish for new feature, or have other questions, [file an issue](https://github.com/cmeng-git/atalk-android/issues).

License
-------

    aTalk, android VoIP and Instant Messaging client
    
    Copyright 2014-2022 Eng Chong Meng
        
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[Privacy Policy](https://cmeng-git.github.io/atalk/privacypolicy.html)
