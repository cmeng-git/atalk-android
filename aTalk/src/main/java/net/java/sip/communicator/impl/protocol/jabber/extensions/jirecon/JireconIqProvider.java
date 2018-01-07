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
package net.java.sip.communicator.impl.protocol.jabber.extensions.jirecon;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The IQ provider implementation for {@link JireconIq}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class JireconIqProvider extends IQProvider<JireconIq>
{
	/**
	 * Name space of Jirecon packet extension.
	 */
	public static final String NAMESPACE = "http://jitsi.org/protocol/jirecon";

	/**
	 * Registers this IQ provider into given <tt>ProviderManager</tt>.
	 * 
	 * @param providerManager
	 *        the <tt>ProviderManager</tt> to which this instance will be bound to.
	 */
	public void registerJireconIQs()
	{
		// <recording/>
		ProviderManager.addIQProvider(JireconIq.ELEMENT_NAME, JireconIq.NAMESPACE, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public JireconIq parse(XmlPullParser parser, int depth)
		throws XmlPullParserException, IOException, SmackException
	{
		JireconIq iq = null;
		String namespace = parser.getNamespace();

		// Conform correct namespace before proceed
		if (!NAMESPACE.equals(namespace)) {
			String rootElement = parser.getName();

			if (JireconIq.ELEMENT_NAME.equals(rootElement)) {
				iq = new JireconIq();

				String action = parser.getAttributeValue("", JireconIq.ACTION_ATTR_NAME);
				String mucjid = parser.getAttributeValue("", JireconIq.MUCJID_ATTR_NAME);
				String output = parser.getAttributeValue("", JireconIq.OUTPUT_ATTR_NAME);
				String rid = parser.getAttributeValue("", JireconIq.RID_ATTR_NAME);
				String status = parser.getAttributeValue("", JireconIq.STATUS_ATTR_NAME);

				iq.setAction(JireconIq.Action.parse(action));
				iq.setStatus(JireconIq.Status.parse(status));
				iq.setMucJid(mucjid);
				iq.setOutput(output);
				iq.setRid(rid);

				boolean done = false;
				while (!done) {
					switch (parser.next()) {
						case XmlPullParser.END_TAG: {
							String name = parser.getName();
							if (rootElement.equals(name)) {
								done = true;
							}
							break;
						}
						case XmlPullParser.TEXT: {
							// Parse some text here
							break;
						}
					}
				}
			}
		}
		return iq;
	}
}
