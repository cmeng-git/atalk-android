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
 * A direct connection that passes packets between two <tt>SctpSocket</tt> instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class DirectLink implements NetworkLink
{
	/**
	 * Instance "a" of this direct connection.
	 */
	private final SctpSocket a;

	/**
	 * Instance "b" of this direct connection.
	 */
	private final SctpSocket b;

	public DirectLink(SctpSocket a, SctpSocket b)
	{
		this.a = a;
		this.b = b;
	}

	/**
	 * {@inheritDoc}
	 */
	public void onConnOut(final SctpSocket s, final byte[] packet)
			throws IOException
	{
		final SctpSocket dest = s == this.a ? this.b : this.a;
		new Thread(() -> {
            try {
                dest.onConnIn(packet, 0, packet.length);
            }
            catch (IOException e) {
                Timber.e(e, "%s", e.getMessage());
            }
        }).start();
	}
}
