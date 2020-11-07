## aTalk (Jabber / XMPP)
- an encrypted instant messaging with video call and GPS features for android

<p align="center">
    <a href="https://cmeng-git.github.io/atalk">
        <img src="./art/atalk.png" alt="aTalk">
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
* Implement fault-tolerance file transfer to ease and enhance file sharing reliability
* Enhance and harmonize UI for file sharing in chat and chatRoom
* Support share, quote and forward of messages and media with previews prior to sending
* Support multi-instances of audio media player with functions: Play, Pause, Resume, Stop and Seek
* Support unread message badges in contact and chatRoom list views
* User selectable option for heads-up notification and quite hours
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
* Implement Jabber VoIP-PBX gateway Telephony, allowing PBX phone call via service gateway
* Join or create room with full room configuration support for owner
* Integrated captcha protected room user interface with retry on failure
* Support both vo to algorithms uses SHA-2 384, 256bit ciphers AES256 & TWOFISH-256, enabling elliptic with Curve 25519
* SDES encice and video call with ZRTP, SDES and DTLS SRTP (DTLSv12) encryption modes
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
* Multi-language UI support (Bahasa Indonesia, English, German, Russian, Slovak and Spanish)

## XMPP Standards Implemented

aTalk works seamlessly with almost every XMPP servers available on network, limited only by servers features supported.<br/>
It supports the following XEP's, standards for XMPP clients.

* [XEP-0012: Last Activity](https://xmpp.org/extensions/xep-0012.html)
* [XEP-0030: Service Discovery](https://xmpp.org/extensions/xep-0030.html)
* [XEP-0045: Multi-User Chat](https://xmpp.org/extensions/xep-0045.html)
* [XEP-0047: In-Band Bytestreams](https://xmpp.org/extensions/xep-00047.html)
* [XEP-0048: Bookmarks](https://xmpp.org/extensions/xep-0048.html)
* [XEP-0054: vcard-temp](https://xmpp.org/extensions/xep-0054.html)
* [XEP-0060: Publish-Subscribe](https://xmpp.org/extensions/xep-0060.html)
* [XEP-0065: SOCKS5 Bytestreams](https://xmpp.org/extensions/xep-0065.html)
* [XEP-0070: Verifying HTTP Requests via XMPP](https://xmpp.org/extensions/xep-0070.html)
* [XEP-0071: XHTML-IM](https://xmpp.org/extensions/xep-0071.html)
* [XEP-0077: In-Band Registration](https://xmpp.org/extensions/xep-0077.html)
* [XEP-0084: User Avatar](https://xmpp.org/extensions/xep-0084.html)
* [XEP-0085: Chat State Notifications](https://xmpp.org/extensions/xep-0085.html)
* [XEP-0092: Software Version](https://xmpp.org/extensions/xep-0092.html)
* [XEP-0095: Stream Initiation](https://xmpp.org/extensions/xep-0095.html)
* [XEP-0096: SI File Transfer](https://xmpp.org/extensions/xep-0096.html)
* [XEP-0100: Gateway Interaction](https://xmpp.org/extensions/xep-0100.html)
* [XEP-0115: Entity Capabilities](https://xmpp.org/extensions/xep-0115.html)
* [XEP-0124: Bidirectional-streams Over Synchronous HTTP (BOSH)](https://xmpp.org/extensions/xep-0124.html),
* [XEP-0138: Stream Compression](https://xmpp.org/extensions/xep-0138.html)
* [XEP-0153: vCard-Based Avatar](https://xmpp.org/extensions/xep-0153.html)
* [XEP-0158: CAPTCHA Forms](https://xmpp.org/extensions/xep-0158.html)
* [XEP-0163: Personal Eventing Protocol (avatars and nicks)](https://xmpp.org/extensions/xep-0163.html)
* [XEP-0166: Jingle](https://xmpp.org/extensions/xep-0166.html)
* [XEP-0167: Jingle RTP Sessions](https://xmpp.org/extensions/xep-0167.html)
* [XEP-0172: User Nickname](https://xmpp.org/extensions/xep-0172.html)
* [XEP-0176: Jingle ICE-UDP Transport Method](https://xmpp.org/extensions/xep-0176.html)
* [XEP-0177: Jingle Raw UDP Transport Method](https://xmpp.org/extensions/xep-0177.html)
* [XEP-0178: Best Practices for Use of SASL EXTERNAL with Certificates](https://xmpp.org/extensions/xep-0178.html)
* [XEP-0184: Message Delivery Receipts](https://xmpp.org/extensions/xep-0184.html)
* [XEP-0191: Blocking command (NI)](https://xmpp.org/extensions/xep-0191.html)
* [XEP-0198: Stream Management](https://xmpp.org/extensions/xep-0198.html)
* [XEP-0199: XMPP Ping](https://xmpp.org/extensions/xep-0199.html)
* [XEP-0203: Delayed Delivery](https://xmpp.org/extensions/xep-0203.html)
* [XEP-0206: XMPP Over BOSH](https://xmpp.org/extensions/xep-0206.html)
* [XEP-0231: Bits of Binary](https://xmpp.org/extensions/xep-0231.html)
* [XEP-0234: Jingle File Transfer](https://xmpp.org/extensions/xep-0234.html)
* [XEP-0237: Roster Versioning](https://xmpp.org/extensions/xep-0237.html)
* [XEP-0249: Direct MUC Invitations](https://xmpp.org/extensions/xep-0249.html)
* [XEP-0251: Jingle Session Transfer](https://xmpp.org/extensions/xep-0251.html)
* [XEP-0260: Jingle SOCKS5 Bytestreams Transport Method](https://xmpp.org/extensions/xep-0260.html)
* [XEP-0261: Jingle In-Band Bytestreams Transport Method](https://xmpp.org/extensions/xep-0261.html)
* [XEP-0262: Use of ZRTP in Jingle RTP Sessions](https://xmpp.org/extensions/xep-0262.html)
* [XEP-0264: File Transfer Thumbnails](https://xmpp.org/extensions/xep-0264.html)
* [XEP-0278: Jingle Relay Nodes](https://xmpp.org/extensions/xep-0278.html)
* [XEP-0280: Message Carbons](https://xmpp.org/extensions/xep-0280.html)
* [XEP-0294: Jingle RTP Header Extensions Negotiation](https://xmpp.org/extensions/xep-0294.html)
* [XEP-0298: Delivering Conference Information to Jingle Participants (Coin)](https://xmpp.org/extensions/xep-0298.html)
* [XEP-0308: Last Message Correction](https://xmpp.org/extensions/xep-0308.html)
* [XEP-0319: Last User Interaction in Presence](https://xmpp.org/extensions/xep-0319.html)
* [XEP-0320: Use of DTLS-SRTP in Jingle Sessions](https://xmpp.org/extensions/xep-0320.html)
* [XEP-0352: Client State Indication](https://xmpp.org/extensions/xep-0352.html)
* [XEP-0353: Jingle Message Initiation](https://xmpp.org/extensions/xep-0353.html)
* [XEP-0363: HTTP File Upload](https://xmpp.org/extensions/xep-0363.html)
* [XEP-0364: Off-the-Record Messaging (V2/3)](https://xmpp.org/extensions/xep-0364.html)
* [XEP-0384: OMEMO Encryption](https://xmpp.org/extensions/xep-0384.html)
* [XEP-xxxx: OMEMO Media sharing](https://xmpp.org/extensions/inbox/omemo-media-sharing.html)

## Acknowledgments

Libraries used in this project:

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/index.html)
* [android-betterpickers](https://github.com/code-troopers/android-betterpickers)
* [Android-EasyLocation](https://github.com/akhgupta/Android-EasyLocation)
* [annotations-java5](https://mvnrepository.com/artifact/org.jetbrains/annotations)
* [apache-mime4j-core](https://github.com/apache/james-mime4j)
* [bouncycastle](https://github.com/bcgit/bc-java)
* [butterknife](https://github.com/JakeWharton/butterknife)
* [ckChangeLog](https://github.com/cketti/ckChangeLog)
* [commons-text](https://commons.apache.org/proper/commons-text/)
* [Dexter](https://github.com/Karumi/Dexter)
* [FFmpeg](https://github.com/FFmpeg/FFmpeg)
* [glide](https://github.com/bumptech/glide)
* [Google Play Services](https://developers.google.com/android/guides/overview)
* [httpcore](https://hc.apache.org/httpcomponents-client-4.5.x/)
* [ice4j](https://github.com/jitsi/ice4j)
* [jitsi](https://github.com/jitsi/jitsi)
* [jitsi-android](https://github.com/jitsi/jitsi-android)
* [jmdns](https://github.com/jmdns/jmdns)
* [jxmpp-jid](https://github.com/igniterealtime/jxmpp)
* [libjitsi](https://github.com/jitsi/libjitsi)
* [libphonenumber](https://github.com/googlei18n/libphonenumber)
* [libvpx](https://github.com/webmproject/libvpx)
* [miniDNS](https://github.com/MiniDNS/minidns)
* [Noembed](https://noembed.com/)
* [otr4j](https://github.com/jitsi/otr4j)
* [opensles](https://github.com/openssl/openssl )
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

## Feedback and Contributions
Cannot found an UI language and would like to help; translate the content in [strings.xml](https://github.com/cmeng-git/atalk-android/blob/master/art/values-xlate/strings.xml). Create a pull request or forward the file to the developer.

If you have found bug, wish for new feature, or have other questions, [file an issue](https://github.com/cmeng-git/atalk-android/issues).

License
-------

    aTalk, android VoIP and Instant Messaging client
    
    Copyright 2014 Eng Chong Meng
        
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
