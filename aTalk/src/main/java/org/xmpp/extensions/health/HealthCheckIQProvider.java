/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xmpp.extensions.health;

import java.io.IOException;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.*;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

/**
 * The <tt>IQProvider</tt> for {@link HealthCheckIQ}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class HealthCheckIQProvider extends IQProvider<HealthCheckIQ>
{
	/**
	 * Registers <tt>HealthCheckIQProvider</tt> as an <tt>IQProvider</tt>
	 */
	public static void registerIQProvider()
	{
		// ColibriStatsIQ
		ProviderManager.addIQProvider(HealthCheckIQ.ELEMENT, HealthCheckIQ.NAMESPACE, new HealthCheckIQProvider());
	}

	/**
	 * Parses <tt>HealthCheckIQ</tt>.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public HealthCheckIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
	{
		String namespace = parser.getNamespace();
		HealthCheckIQ iq = null;

		if (HealthCheckIQ.ELEMENT.equals(parser.getName())
			&& HealthCheckIQ.NAMESPACE.equals(namespace)) {
			String rootElement = parser.getName();

			iq = new HealthCheckIQ();
			boolean done = false;

			while (!done) {
				switch (parser.next()) {
					case END_ELEMENT: {
						String name = parser.getName();

						if (rootElement.equals(name)) {
							done = true;
						}
						break;
					}
				}
			}
		}
		return iq;
	}
}
