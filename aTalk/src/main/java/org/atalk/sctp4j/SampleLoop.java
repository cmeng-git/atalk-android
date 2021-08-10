/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.sctp4j;

import java.io.IOException;

import timber.log.Timber;

/**
 * Sample that uses two <tt>SctpSocket</tt>s with {@link DirectLink}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SampleLoop
{
    public static void main(String[] args)
            throws Exception
    {
        Sctp.init();

        final SctpSocket server = Sctp.createSocket(5001);
        final SctpSocket client = Sctp.createSocket(5002);

        DirectLink link = new DirectLink(server, client);
        server.setLink(link);
        client.setLink(link);

        // Make server passive
        server.listen();

        // Client thread
        new Thread(() -> {
            try {
                client.connect(server.getPort());
                Timber.i("Client: connect");

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
                int sent = client.send(new byte[200], false, 0, 0);
                Timber.i("Client sent: %s", sent);

            } catch (IOException e) {
                Timber.e("%s", e.getMessage());
            }
        }
        ).start();

        server.setDataCallback( (data, sid, ssn, tsn, ppid, context, flags)
                -> Timber.i("Server got some data: %s stream: %s payload protocol id: %s",
                        data.length, sid, ppid)
        );
        Thread.sleep(5 * 1000);

        server.close();
        client.close();
        Sctp.finish();
    }
}
