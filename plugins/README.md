EWS Android API
===============

A java client library to access Exchange web services, packaged for Android. The API works against Office 365 Exchange Online as well as on premises Exchange.
For API details, guidelines and examples go to [ews-java-api][ews-java-url].

Building
--------

Check-out the ews-java-api submodule:

```
git submodule init
git submodule update
```

The following command creates a fat jar under ews-android-api/build/libs called **ews-android-api.jar**.

```
./gradlew copySources ews-android-api:build
```

Download
--------
Not available at this stage.  
  
Currently you have to manually build the project, copy the ews-android-api.jar to your module's libs folder and then add those dependencies to your build.gradle script:
```groovy
compile files('libs/ews-android-api.jar')
compile 'joda-time:joda-time:2.8'
```

Usage (example)
---------------

```java
ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
ExchangeCredentials credentials = new WebCredentials("emailAddress", "password");
service.setCredentials(credentials);
service.setUrl(new URI("http://some-ews-server.com/EWS/Exchange.asmx"));

EmailMessage message = new EmailMessage(service);
message.getToRecipients().add("administrator@some-ews-server.com");
message.setSubject("Hello world!");
message.setBody(MessageBody.getMessageBodyFromText("Sent using the EWS Android API."));
message.send();
```

License
-------
[ews-java-api][ews-java-license] is licensed under MIT:

    The MIT License
    Copyright (c) 2012 Microsoft Corporation
    
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.

Check also the licenses of dependency libraries that are bundled into a fat jar.

 [ews-java-url]: https://github.com/OfficeDev/ews-java-api
 [ews-java-license]: https://github.com/OfficeDev/ews-java-api/blob/master/license.txt
