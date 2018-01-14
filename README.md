## aTalk (Jabber / XMPP)
- an encrypted instant messaging with video call and GPS features for android

<p align="center">
    <a href="http://atalk.sytes.net">
        <img src="./art/atalk.png" alt="aTalk">
    </a>
    <a href="https://play.google.com/store/apps/details?id=org.atalk.android&hl=en">
        <img src="./art/google_play.png" alt="Google PlayStore">
    </a>
    <a href="https://www.youtube.com/watch?v=9w5WwphzgBc">
        <img src="./art/youtube.png" alt="YouTube">
    </a>
</p>

## Features
aTalk is an xmpp client designed for android and supports the following features:
* Instant messaging in plain text and End-to-End encryption with [OMEMO](http://conversations.im/omemo/) or [OTR](https://otr.cypherpunks.ca/)
* Omemo encryption is available during multi-user group chat session, giving users' maximum privacy and security
* Supports both voice and video call with ZRTP encryption
* Send and receive files for all document types and images. For image file, a thumb nail is first sent for preview and acceptance before the actual file is sent
* Unique feature to send GPS locations to chosen buddy, requesting real-time guidance to reach desired destination
* A 360° street view of your current location, may use for self-guided tour. The street view tracks and follows your direction of sight.
* Integrated photo editor with zooming and cropping, user can update his/her avatar with ease
* Multiple accounts support


### XMPP Features

aTalk works seemlessly with almost every XMPP servers availabe on network, limited only by servers features supported.
It supports the following XEP's, standards for XMPP client devices.

* [XEP-0030: Service Discovery](https://xmpp.org/extensions/xep-0030.html)
* [XEP-0045: Multi-User Chat](https://xmpp.org/extensions/xep-0045.html)
* [XEP-0047: In-Band Bytestreams](https://xmpp.org/extensions/xep-00047.html)
* [XEP-0054: vcard-temp](https://xmpp.org/extensions/xep-0054.html)
* [XEP-0060: Publish-Subscribe](https://xmpp.org/extensions/xep-0060.html)
* [XEP-0065: SOCKS5 Bytestreams](https://xmpp.org/extensions/xep-0065.html)
* [XEP-0077: In-Band Registration](https://xmpp.org/extensions/xep-0077.html)
* [XEP-0084: User Avatar](https://xmpp.org/extensions/xep-0084.html)
* [XEP-0085: Chat State Notifications](https://xmpp.org/extensions/xep-0085.html)
* [XEP-0092: Software Version](https://xmpp.org/extensions/xep-0092.html)
* [XEP-0095: Stream Initiation](https://xmpp.org/extensions/xep-0095.html)
* [XEP-0096: SI File Transfer](https://xmpp.org/extensions/xep-0096.html)
* [XEP-0115: Entity Capabilities](https://xmpp.org/extensions/xep-0115.html)
* [XEP-0138: Stream Compression](https://xmpp.org/extensions/xep-0138.html)
* [XEP-0153: vCard-Based Avatar](https://xmpp.org/extensions/xep-0153.html)
* [XEP-0163: Personal Eventing Protocol (avatars and nicks)](https://xmpp.org/extensions/xep-0163.html)
* [XEP-0166: Jingle](https://xmpp.org/extensions/xep-0166.html)
* [XEP-0167: Jingle RTP Sessions](https://xmpp.org/extensions/xep-0167.html)
* [XEP-0172: User Nickname](https://xmpp.org/extensions/xep-0172.html)
* [XEP-0176: Jingle ICE-UDP Transport Method](https://xmpp.org/extensions/xep-0176.html)
* [XEP-0177: Jingle Raw UDP Transport Method](https://xmpp.org/extensions/xep-0177.html)
* [XEP-0184: Message Delivery Receipts (NI)](https://xmpp.org/extensions/xep-0184.html)
* [XEP-0191: Blocking command (NI)](https://xmpp.org/extensions/xep-0191.html)
* [XEP-0198: Stream Management](https://xmpp.org/extensions/xep-0198.html)
* [XEP-0199: XMPP Ping](https://xmpp.org/extensions/xep-0199.html)
* [XEP-0203: Delayed Delivery](https://xmpp.org/extensions/xep-0203.html)
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
* [XEP-0352: Client State Indication](https://xmpp.org/extensions/xep-052.html)
* [XEP-0364: Off-the-Record Messaging (V2/3)](https://xmpp.org/extensions/xep-0364.html)

Libraries used in this project
------------------------------
* [XEP-0384: OMEMO Encryption](https://xmpp.org/extensions/xep-0384.html)

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/index.html)
* [android-betterpickers](https://github.com/code-troopers/android-betterpickers)
* [Android-EasyLocation](https://github.com/akhgupta/Android-EasyLocation)
* [annotations-java5](https://mvnrepository.com/artifact/org.jetbrains/annotations)
* [bouncycastle](https://github.com/bcgit/bc-java)
* [butterknife](https://github.com/JakeWharton/butterknife)
* [ckChangeLog](https://github.com/cketti/ckChangeLog)
* [commons-lang](http://commons.apache.org/proper/commons-lang/)
* [Dexter](https://github.com/Karumi/Dexter)
* [dhcp4java](https://github.com/ggrandes-clones/dhcp4java)
* [dnsjava](https://github.com/dnsjava/dnsjava)
* [dnssecjava](https://github.com/ibauersachs/dnssecjava)
* [ews-android-api](https://github.com/alipov/ews-android-api)
* [FFmpeg](https://github.com/FFmpeg/FFmpeg)
* [Google Play Services](https://developers.google.com/android/guides/overview)
* [guava](https://mvnrepository.com/artifact/com.google.guava/guava")
* [httpclient-android](https://github.com/smarek/httpclient-android)
* [ice4j](https://github.com/jitsi/ice4j)
* [jitsi](https://github.com/jitsi/jitsi)
* [jitsi-android](https://github.com/jitsi/jitsi-android)
* [jmdns](https://github.com/jmdns/jmdns)
* [json-simple](https://github.com/fangyidong/json-simple)
* [jxmpp-jid](https://github.com/igniterealtime/jxmpp)
* [libjitsi](https://github.com/jitsi/libjitsi)
* [libphonenumber](https://github.com/googlei18n/libphonenumber)
* [libvpx](https://github.com/webmproject/libvpx)
* [otr4j](https://github.com/jitsi/otr4j)
* [opensles](https://github.com/openssl/openssl )
* [osgi.core](http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0)
* [sdes4j](https://github.com/ibauersachs/sdes4j)
* [sdp-api](https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api)
* [Smack](https://github.com/igniterealtime/Smack)
* [speex](https://github.com/xiph/speex)
* [uCrop](https://github.com/Yalantis/uCrop)
* [weupnp](https://github.com/bitletorg/weupnp)
* [x264](http://git.videolan.org/git/x264.git)
* [zrtp4j-light](https://github.com/jitsi/zrtp4j)

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


[Privacy Policy](http://atalk.sytes.net/privacypolicy.html) 
