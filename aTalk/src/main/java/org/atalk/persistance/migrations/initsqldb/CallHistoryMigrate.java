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

import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.history.HistoryService;

import org.atalk.android.aTalkApp;
import org.atalk.util.xml.XMLUtils;
import org.jxmpp.util.XmppStringUtils;
import org.w3c.dom.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;

import timber.log.Timber;

public class CallHistoryMigrate
{
    private static String ATTR_TS = "timestamp";

    private static String ATTR_UID = "accountUID";
    private static String ATTR_CS = "callStart";
    private static String ATTR_CE = "callEnd";
    private static String ATTR_DIR = "dir";

    private static String ATTR_CID = "callParticipantIDs";

    private static String ATTR_ECS = "callParticipantStart";
    private static String ATTR_ECE = "callParticipantEnd";
    private static String ATTR_CPS = "callParticipantStates";
    private static String ATTR_CER = "callEndReason";
    private static String ATTR_EN = "callParticipantNames";
    private static String ATTR_SEID = "secondaryCallParticipantIDs";

    /**
     * The XML Document containing the messages file.
     */
    private static Document xmlDocument = null;
    private static SQLiteDatabase mDB;
    private static ContentValues callValues = new ContentValues();

    private static String Uuid;
    private static String accountuid;

    private static String timeDate;

    private static SimpleDateFormat simpleDateFormat;

    /**
     * Initializes the storage manager and makes it do initial load and parsing of the contact
     * list file.
     */
    public static void migrateCallHistory(SQLiteDatabase db)
    {
        String callHistoryDir = "history_ver1.0/callhistory/default/default";

        mDB = db;
        DocumentBuilder builder = null;
        Context ctx = aTalkApp.getGlobalContext();
        simpleDateFormat = new SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US);

        File callHistoryFP = new File(ctx.getFilesDir() + "/" + callHistoryDir);
        try {
            builder = XMLUtils.newDocumentBuilderFactory().newDocumentBuilder();

            // loop all the call record xml files
            File[] xmlFPs = callHistoryFP.listFiles();
            if (xmlFPs != null) {
                for (File xmlFP : xmlFPs) {
                    if ((builder != null) && xmlFP.getName().contains(".xml")
                            && (xmlFP.length() != 0)) {
                        Timber.i("Processing record xml file: %s", xmlFP);
                        xmlDocument = builder.parse(xmlFP);
                        processXmlNode();
                    }
                    else if (!xmlFP.getName().contains(".dat")) {
                        Timber.w("Error parsing call history file: %s", xmlFP);
                    }
                }
            }
        } catch (Exception ex) {
            Timber.e("Couldn't parse message records!!. Exception: %s", ex.getMessage());
        }
    }

    /**
     * Parses <tt>groupNode</tt> and all of its subNodes, creating corresponding instances through
     * <tt>mclServiceImpl</tt> as children of <tt>parentGroup</tt>
     */
    public static void processXmlNode()
    {
        String content;
        String accountUid = "";
        Date date;
        int idx;
        Long sqTimeStamp;

        Node historyNode = xmlDocument.getFirstChild();
        NodeList callRecords = (historyNode == null) ? null : historyNode.getChildNodes();

        // loop through all the records and extract each record details
        for (int i = 0; (callRecords != null) && (i < callRecords.getLength()); i++) {
            Node recordNode = callRecords.item(i);
            if (recordNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            callValues.clear();
            try {
                timeDate = ((Element) recordNode).getAttribute(ATTR_TS);
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                Uuid = String.valueOf(sqTimeStamp) + String.valueOf(Math.abs(timeDate.hashCode()));
                callValues.put(CallHistoryService.UUID, Uuid);
                callValues.put(CallHistoryService.TIME_STAMP, sqTimeStamp);

                accountUid = XMLUtils.findChild((Element) recordNode, ATTR_UID).getTextContent();
                if (accountUid.split("@").length > 2)
                    accountUid = accountUid.substring(0, accountUid.lastIndexOf("@"));
                callValues.put(CallHistoryService.ACCOUNT_UID, accountUid);

                timeDate = XMLUtils.findChild((Element) recordNode, ATTR_CS).getTextContent();
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                callValues.put(CallHistoryService.CALL_START, sqTimeStamp);

                timeDate = XMLUtils.findChild((Element) recordNode, ATTR_CE).getTextContent();
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                callValues.put(CallHistoryService.CALL_END, sqTimeStamp);

                content = XMLUtils.findChild((Element) recordNode, ATTR_DIR).getTextContent();
                callValues.put(CallHistoryService.DIRECTION, content);

                content = XMLUtils.findChild((Element) recordNode, ATTR_CID).getTextContent();
                callValues.put(CallHistoryService.ENTITY_FULL_JID, content);

                timeDate = XMLUtils.findChild((Element) recordNode, ATTR_ECS).getTextContent();
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                callValues.put(CallHistoryService.ENTITY_CALL_START, sqTimeStamp);

                timeDate = XMLUtils.findChild((Element) recordNode, ATTR_ECE).getTextContent();
                date = simpleDateFormat.parse(timeDate);
                sqTimeStamp = date.getTime();
                callValues.put(CallHistoryService.ENTITY_CALL_END, sqTimeStamp);

                content = XMLUtils.findChild((Element) recordNode, ATTR_CPS).getTextContent();
                callValues.put(CallHistoryService.ENTITY_CALL_STATE, content);

                content = XMLUtils.findChild((Element) recordNode, ATTR_CER).getTextContent();
                callValues.put(CallHistoryService.CALL_END_REASON, content);

                content = XMLUtils.findChild((Element) recordNode, ATTR_EN).getTextContent();
                content = XmppStringUtils.parseBareJid(content.replace("\"", ""));
                callValues.put(CallHistoryService.ENTITY_JID, content);

                content = XMLUtils.findChild((Element) recordNode, ATTR_SEID).getTextContent();
                callValues.put(CallHistoryService.SEC_ENTITY_ID, content);

                mDB.insert(CallHistoryService.TABLE_NAME, null, callValues);
            } catch (Exception ex) {
                Timber.w("Failed to parse record: %s. Exception: %s", accountUid, ex.getMessage());
            }
        }
    }
}
