/**
 *
 * Copyright 2003-2006 Jive Software.
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
package net.java.sip.communicator.impl.protocol.jabber.extensions.si.provider;

import net.java.sip.communicator.impl.protocol.jabber.extensions.si.packet.SiThumb;
import net.java.sip.communicator.impl.protocol.jabber.extensions.si.packet.SiThumb.FileElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.ThumbnailElement;

import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;
import org.jxmpp.util.XmppDateTime;
import org.xmlpull.v1.XmlPullParser;

import java.text.ParseException;
import java.util.Date;
import java.util.logging.*;

/**
 * The SiThumbProvider parses SiThumb packets.
 * 
 * @author Alexander Wenckus
 * @author Eng Chong Meng
 * 
 */
public class SiThumbProvider extends IQProvider<SiThumb>
{
	private static final Logger LOGGER = Logger.getLogger(SiThumbProvider.class.getName());

	@Override
	public SiThumb parse(XmlPullParser parser, int initialDepth)
			throws Exception
	{
		boolean done = false;
		SiThumb initiation = new SiThumb();
		String id = parser.getAttributeValue("", "id");
		String mimeType = parser.getAttributeValue("", "mime-type");

		// file
		boolean isRanged = false;
		String name = null;
		String size = null;
		String hash = null;
		String date = null;
		String desc = null;
		ThumbnailElement thumbnail = null;

		// feature
		DataForm form = null;
		DataFormProvider dataFormProvider = new DataFormProvider();

		int eventType;
		String elementName;
		String namespace;
		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();
			namespace = parser.getNamespace();
			if (eventType == XmlPullParser.START_TAG) {
				if (elementName.equals("file")) {
					name = parser.getAttributeValue("", "name");
					size = parser.getAttributeValue("", "size");
					hash = parser.getAttributeValue("", "hash");
					date = parser.getAttributeValue("", "date");
				}
				else if (elementName.equals("desc")) {
					desc = parser.nextText();
				}
				else if (elementName.equals("range")) {
					isRanged = true;
				}
				else if (elementName.equals("x") && namespace.equals("jabber:x:data")) {
					form = dataFormProvider.parse(parser);
				}
				else if (elementName.equals("thumbnail")) {
					thumbnail = new ThumbnailElement(parser);
				}
			}
			else if (eventType == XmlPullParser.END_TAG) {
				if (elementName.equals("si")) {
					done = true;
				}
				// The name-attribute is required per XEP-0096, so ignore the IQ if the name is not
				// set to avoid exceptions. Particularly,
				// the SI response of Empathy contains an invalid, empty file-tag.
				else if (elementName.equals("file") && (name != null)) {
					long fileSize = 0;
					if (size != null && size.trim().length() != 0) {
						try {
							fileSize = Long.parseLong(size);
						}
						catch (NumberFormatException e) {
							LOGGER.log(Level.SEVERE, "Failed to parse file size from " + fileSize,
								e);
						}
					}
					FileElement file = new FileElement(name, fileSize);
					file.setHash(hash);

					Date fileDate = new Date();
					if (date != null) {
						try {
							fileDate = XmppDateTime.parseDate(date);
						}
						catch (ParseException e) {
							// couldn't parse date, use current date-time
						}
					}
					if (thumbnail != null)
						file.setThumbnailElement(thumbnail);

					file.setHash(hash);
					file.setDate(fileDate);
					file.setDesc(desc);
					file.setRanged(isRanged);
					initiation.setFile(file);
				}
			}
		}
		initiation.setSessionID(id);
		initiation.setMimeType(mimeType);
		initiation.setFeatureNegotiationForm(form);
		return initiation;
	}
}
