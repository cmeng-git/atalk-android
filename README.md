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
* Instant messaging in plain text and End-to-End encryption with [OMEMO](https://conversations.im/omemo/) or [OTR](https://otr.cypherpunks.ca/)
* SSL Certificate authentication, DNSSEC and DANE Security implementation for enhanced secure Connection Establishment
* OMEMO encryption in group chat session enhancing privacy and security
* OMEMO Media File Sharing for all files including Stickers, Bitmoji and Emoji rich contents
* Support http file upload for file sharing with offline contact and in group chat
* Support Stickers, Bitmoji and Emoji rich content sharing via Google Gboard
* Send and receive files for all document types and images with thumbnail preview and gif animation
* Auto accept file transfer with max file size option
* Implement fault-tolerance file transfer algorithm, via HttpFileUpload, Jingle File Transfer, and Legacy In-Band/SOCK5 Bytestreams protocols to ease and enhance file sharing reliability
* Support secured file transfer via HttpFileUpload using aesgcm://, or via Jingle Encrypted Transport (JET)
* Enhance and harmonize UI for file sharing in chat and chatRoom
* Support share, quote and forward of messages and media with previews prior to sending
* Support multi-instances of audio media player with functions: Play, Pause, Resume, Stop and Seek
* Support unread message badges in contact and chatRoom list views
* User selectable option for heads-up notification and quiet hours
* Share of social media links are tagged with thumbnail and title
* Support Text to Speech and speech recognition UI with enable option per chat session (contact and group chat)
* XEP-0012: Last Activity time associated with contacts
* XEP-0048: Bookmarks for conference room and autoJoin on login
* XEP-0070: Verifying HTTP Requests via XMPP entity for user authentication without password entry
* XEP-0071: XHTML-IM Support chat messages containing lightweight text markup
* XEP-0085: Chat State Notifications
* XEP-0124: Bidirectional-streams Over Synchronous HTTP (BOSH) with Proxy support
* XEP-0178: Use of SASL EXTERNAL with TLS Certificates for client authentication
* XEP-0184: Message Delivery Receipts with user enable/disable option
* XEP-0215: External Service Discovery
* XEP-0251: Jingle Session Transfer: Support both Unattended and Attended call transfer
* XEP-0313: Message Archive Management
* XEP-0391: Jingle Encrypted Transports for OMEMO encrypted media file sharing
* Implement call waiting to accept a second incoming call by placing the in-progress call on hold; and allow switching between calls
* Implement Jabber VoIP-PBX gateway Telephony, allowing PBX phone call via service gateway
* Join or create room with full room configuration support for owner
* Integrated captcha protected room user interface with retry on failure
* Support both voice and video call with ZRTP, SDES and DTLS SRTP encryption modes
* ZRTP crypto algorithms uses SHA-2 384, 256bit ciphers AES256 & TWOFISH-256, enabling elliptic with Curve 25519
* SDES encryption with AES256 and AES192, acceleration using OpenSSL
* Support simultaneous media call and message chat sessions
* Unique GPS-Location implementation as standalone tool, send locations to your desired buddy for real-time tracking or playback animation
* A 360° street view of your current location use for self-guided tour. The street view tracks and follows your direction of sight
* Built-in demo for GPS-Location features
* Integrated photo editor with zooming and cropping, user can update the avatar with ease
* Last message correction, message carbons and offline messages (OMEMO)
* Stream Management and mobile network ping interval self-tune optimization support
* In-Band Registration with captcha option support
* Multiple accounts creation
* User selectable Themes support
* Multi-language UI support (Arabic, Bahasa Indonesia, Chinese Simplified, English, German, Portuguese (Brazil), Russian, Slovak and Spanish)

## XMPP Standards Implemented

aTalk works seamlessly with almost every XMPP servers available on network, limited only by servers features supported. 
See [atalk.doap](https://xmpp.org/doap/atalk.doap) for list of supported XEP's, standards for XMPP clients.


## Acknowledgements

Libraries used in this project:

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/index.html)
* [android-betterpickers](https://github.com/code-troopers/android-betterpickers)
* [Android-EasyLocation](https://github.com/akhgupta/Android-EasyLocation)
* [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player)
* [annotations-java5](https://mvnrepository.com/artifact/org.jetbrains/annotations)
* [apache-mime4j-core](https://github.com/apache/james-mime4j)
* [bouncycastle](https://github.com/bcgit/bc-java)
* [ckChangeLog](https://github.com/cketti/ckChangeLog)
* [commons-text](https://commons.apache.org/proper/commons-text/)
* [Dexter](https://github.com/Karumi/Dexter)
* [ExoPlayer](https://github.com/google/ExoPlayer)
* [FFmpeg](https://github.com/FFmpeg/FFmpeg)
* [glide](https://github.com/bumptech/glide)
* [Google Play Services](https://developers.google.com/android/guides/overview)
* [httpcore](https://hc.apache.org/httpcomponents-client-4.5.x/)
* [IPAddress](https://github.com/seancfoley/IPAddress)
* [ice4j](https://github.com/jitsi/ice4j)
* [jitsi](https://github.com/jitsi/jitsi)
* [jitsi-android](https://github.com/jitsi/jitsi-android)
* [jmdns](https://github.com/jmdns/jmdns)
* [js-evaluator-for-android](https://github.com/evgenyneu/js-evaluator-for-android)
* [jxmpp-jid](https://github.com/igniterealtime/jxmpp)
* [libjitsi](https://github.com/jitsi/libjitsi)
* [libphonenumber](https://github.com/googlei18n/libphonenumber)
* [libvpx](https://github.com/webmproject/libvpx)
* [miniDNS](https://github.com/MiniDNS/minidns)
* [Noembed](https://noembed.com/)
* [osmdroid](https://github.com/osmdroid/osmdroid)
* [otr4j](https://github.com/jitsi/otr4j)
* [opensles](https://github.com/openssl/openssl )
* [openSSL](https://www.openssl.org/source/)
* [Opus](https://opus-codec.org/)
* [osgi.core](http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0)
* [sdes4j](https://github.com/ibauersachs/sdes4j)
* [sdp-api](https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api)
* [Smack](https://github.com/igniterealtime/Smack)
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
