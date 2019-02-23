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
package org.atalk.persistance.migrations.initsqldb;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.msghistory.MessageSourceService;
import net.java.sip.communicator.service.history.HistoryService;

import org.atalk.android.aTalkApp;
import org.atalk.util.xml.XMLUtils;
import org.w3c.dom.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;

import timber.log.Timber;

public class RecentMessageMigrate
{
    private static String ATTR_PROVIDER = "provider";
    private static String ATTR_CONTACT = "contact";
    private static String ATTR_TS = "timestamp";
    private static String ATTR_VER = "ver";

    /**
     * The XML Document containing the messages file.
     */
    private static Document xmlDocument = null;
    private static SQLiteDatabase mDB;
    private static ContentValues msgValues = new ContentValues();

    private static SimpleDateFormat simpleDateFormat;

    /**
     * Initializes the storage manager and makes it do initial load and parsing of the contact
     * list file.
     */
    public static void migrateRecentMessage(SQLiteDatabase db)
    {
        String recentMessagesDir = "history_ver1.0/recent_messages";

        mDB = db;
        DocumentBuilder builder = null;
        Context ctx = aTalkApp.getGlobalContext();
        simpleDateFormat = new SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US);

        File recentMessagesFP = new File(ctx.getFilesDir() + "/" + recentMessagesDir);
        try {
            builder = XMLUtils.newDocumentBuilderFactory().newDocumentBuilder();

            // loop all the call record xml files
            File[] xmlFPs = recentMessagesFP.listFiles();
            if (xmlFPs != null) {
                for (File xmlFP : xmlFPs) {
                    if ((builder != null) && xmlFP.getName().contains(".xml")
                            && (xmlFP.length() != 0)) {
                        Timber.i("Processing record xml file: %s", xmlFP);
                        xmlDocument = builder.parse(xmlFP);
                        processXmlNode();
                    }
                    else if (!xmlFP.getName().contains(".dat")) {
                        Timber.w("Error parsing recent messages file: %s", xmlFP);
                    }
                }
            }
        } catch (Exception ex) {
            Timber.e("Couldn't parse message records!!. %s", ex.getMessage());
        }
    }

    /**
     * Parses <tt>groupNode</tt> and all of its subNodes, creating corresponding instances through
     * <tt>mclServiceImpl</tt> as children of <tt>parentGroup</tt>
     */
    public static void processXmlNode()
    {
        Long sqTimeStamp;
        Date date;
        String content;
        String accountUid = "";
        String timeDate;
        String Uuid;

        Node historyNode = xmlDocument.getFirstChild();
        NodeList recentMessageRecords = (historyNode == null) ? null : historyNode.getChildNodes();

        // loop through all the records and extract each record details
        for (int i = 0; (recentMessageRecords != null) && (i < recentMessageRecords.getLength());
             i++) {
            Node recordNode = recentMessageRecords.item(i);
            if (recordNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            msgValues.clear();
            try {
                timeDate = ((Element) recordNode).getAttribute(ATTR_TS);
                date = simpleDateFormat.parse(timeDate);
                Uuid = String.valueOf(date.getTime())
                        + String.valueOf(Math.abs(timeDate.hashCode()));
                msgValues.put(MessageSourceService.UUID, Uuid);

                accountUid
                        = XMLUtils.findChild((Element) recordNode, ATTR_PROVIDER).getTextContent();
                if (accountUid.split("@").length > 2)
                    accountUid = accountUid.substring(0, accountUid.lastIndexOf("@"));
                msgValues.put(MessageSourceService.ACCOUNT_UID, accountUid);

                content = XMLUtils.findChild((Element) recordNode, ATTR_CONTACT).getTextContent();
                msgValues.put(MessageSourceService.ENTITY_JID, content);

                timeDate = XMLUtils.findChild((Element) recordNode, ATTR_TS).getTextContent();
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                msgValues.put(MessageSourceService.TIME_STAMP, sqTimeStamp);

                content = XMLUtils.findChild((Element) recordNode, ATTR_VER).getTextContent();
                msgValues.put(MessageSourceService.VERSION, content);

                mDB.insert(MessageSourceService.TABLE_NAME, null, msgValues);
            } catch (Exception ex) {
                Timber.w(ex, "Failed to parse record: %s", accountUid);
            }
        }
    }
}
