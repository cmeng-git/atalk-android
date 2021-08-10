/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.avatar.useravatar;

import java.io.*;
import java.net.URL;

/**
 * An AvatarRetriever which retrieve the avatar over HTTP.
 */
public class HttpAvatarRetriever implements AvatarRetriever
{

    private URL mUrl;
    private String mUrlString;

    /**
     * Create a HttpAvatarRetriever.
     *
     * @param url the url of the avatar to download.
     */
    public HttpAvatarRetriever(final URL url)
    {
        mUrl = url;
    }

    /**
     * Create a HttpAvatarRetriever.
     *
     * @param url the url of the avatar to download.
     */
    public HttpAvatarRetriever(final String url)
    {
        mUrlString = url;
    }

    @Override
    public byte[] getAvatar()
            throws IOException
    {
        if (mUrl == null)
            mUrl = new URL(mUrlString);
        InputStream in = mUrl.openStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            byte[] data = new byte[1024];
            int nbread;
            while ((nbread = in.read(data)) != -1) {
                os.write(data, 0, nbread);
            }
        } finally {
            in.close();
            os.close();
        }
        return os.toByteArray();
    }

}
