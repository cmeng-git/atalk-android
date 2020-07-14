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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.IMessage;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.xml.XMLUtils;
import org.json.JSONObject;
import org.w3c.dom.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;

import timber.log.Timber;

import static org.atalk.android.gui.chat.ChatMessage.STATUS_RECEIVED;
import static org.atalk.android.gui.chat.ChatMessage.STATUS_SEND;

public class ChatHistoryMigrate extends OSGiActivity
{
    private static String ATTR_DIR = "dir";
    private static String ATTR_MSG = "msg";
    private static String ATTR_MSG_TYPE = "msgTyp";
    private static String ATTR_ENC = "enc";
    private static String ATTR_UID = "uid";
    private static String ATTR_ID = "id";
    private static String ATTR_SUB = "sub";
    private static String ATTR_TS = "receivedTimestamp";
    private static String ATTR_MSG_SUBTYPE = "msgSubTyp";

    private static String ATTR_FILE = "file";
    private static String ATTR_DATE = "date";
    private static String ATTR_STATUS = "status";

    private static String LAT_LNG = "LatLng:";

    private static int Type_Chat = 0;
    private static int Type_File = 1;
    private static int Type_Muc = 2;

    /**
     * The XML Document containing the messages file.
     */
    private static Document xmlDocument = null;
    private static SQLiteDatabase mDB;
    private static ContentValues sessionValues = new ContentValues();
    private static ContentValues msgValues = new ContentValues();

    private static boolean sessionRecordCreated;

    private static String sessionUuid;
    private static String accountUuid;
    private static String accountUid;
    private static String userId;
    private static String userNick;
    private static String entityJid; // Contact or ChatRoom BareJid
    private static String timeDate;

    private static SimpleDateFormat simpleDateFormat;

    /**
     * Migrate all the chat, file and muc message record into chatSessions and messages tables
     */
    public static void migrateChatHistory(SQLiteDatabase db)
    {
        // The directories where messages and fileHistory records are stored
        String messagesDir = "history_ver1.0/messages/default";
        String fileHistoryDir = "history_ver1.0/filehistory/default";

        // directories to scan through for xml files
        List<String> dirs = new ArrayList<>();
        dirs.add(Type_Chat, messagesDir);
        dirs.add(Type_File, fileHistoryDir);

        int idx;
        int chatType;
        String fpName;
        String property, value;

        mDB = db;
        DocumentBuilder builder = null;
        Context ctx = aTalkApp.getGlobalContext();
        simpleDateFormat = new SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US);

        for (String messageDir : dirs) {
            File messagesFP = new File(ctx.getFilesDir() + "/" + messageDir);
            try {
                builder = XMLUtils.newDocumentBuilderFactory().newDocumentBuilder();

                File[] filesList = messagesFP.listFiles();
                // proceed to next messageDir if none need to be processed
                if (filesList == null)
                    continue;

                for (File sessionFN : filesList) {
                    fpName = sessionFN.getName();
                    // strip accountUid service part if any
                    if (fpName.split("@").length == 3)
                        idx = fpName.lastIndexOf("@");
                    else
                        idx = fpName.lastIndexOf("$");
                    if (idx != -1) {
                        // Create the account information based on the directory owner name
                        accountUid = fpName.substring(0, idx).replace("_", ":");
                        accountUuid = PropertiesMigrate.accountValues.get(accountUid);
                        // Ignore obsolete or stray xml message files crated during migration
                        if (StringUtils.isEmpty(accountUuid)) {
                            Timber.w("Skip processing obsolete xml message file: %s", fpName);
                            continue;
                        }
                        userId = PropertiesMigrate.accountValues.get(accountUuid);
                    }

                    // Loop all the chat contact directories; Merge fileHistory and chat messages
                    // using the same sessionUuid if both have  the same accountUuid && contactJid
                    File contactChatDir = new File(messagesFP + "/" + sessionFN.getName());
                    File[] messages = contactChatDir.listFiles();
                    for (File messageFp : messages) {
                        JSONObject attributes = new JSONObject();
                        sessionValues.clear();

                        entityJid = messageFp.getName();
                        if (entityJid.split("@").length > 2) {
                            chatType = Type_Muc;
                            idx = entityJid.lastIndexOf("@");
                            entityJid = entityJid.substring(0, idx);
                            sessionValues.put(ChatSession.MODE, ChatSession.MODE_MULTI);
                        }
                        else {
                            chatType = dirs.indexOf(messageDir);
                            sessionValues.put(ChatSession.MODE, ChatSession.MODE_SINGLE);
                        }

                        // use mcUid for chat message sessionID, but generate new for muc
                        sessionUuid
                                = PropertiesMigrate.accountValues.get(accountUuid + entityJid);
                        // create new sessionID with a string of length = 22.
                        if (StringUtils.isEmpty(sessionUuid)) {
                            sessionUuid = String.valueOf(messageFp.lastModified())
                                    + String.valueOf(Math.abs(entityJid.hashCode()));
                            // Keep the newly created session UUID for later reference
                            PropertiesMigrate.accountValues.put(accountUuid + entityJid,
                                    sessionUuid);
                        }

                        // check if the session has already been created before
                        sessionRecordCreated = findSessionUuid(sessionUuid);

                        sessionValues.put(ChatSession.SESSION_UUID, sessionUuid);
                        sessionValues.put(ChatSession.ACCOUNT_UUID, accountUuid);
                        sessionValues.put(ChatSession.ACCOUNT_UID, accountUid);
                        sessionValues.put(ChatSession.ENTITY_JID, entityJid);

                        // retrieve all the muc chatRoom information
                        if (chatType == Type_Muc) {
                            String roomID = PropertiesMigrate.accountValues.get(entityJid);
                            if (StringUtils.isNotEmpty(roomID)) {
                                userNick = PropertiesMigrate.accountValues.get(roomID + "."
                                        + ChatRoom.USER_NICK_NAME);
                                for (String key : PropertiesMigrate.accountValues.keySet()) {
                                    if (key.contains(roomID)) {
                                        if ((idx = key.lastIndexOf(".")) != -1) {
                                            property = key.substring(idx + 1);
                                            value = PropertiesMigrate.accountValues.get(key);
                                            attributes.put(property, value);
                                        }
                                    }
                                }
                            }
                            if (StringUtils.isEmpty(userNick)) {
                                userNick = userId.split("@")[0];
                            }
                        }
                        sessionValues.put(ChatSession.ATTRIBUTES, attributes.toString());

                        // loop all the chat record xml files and retrieve all chat records
                        File[] xmlFPs = messageFp.listFiles();
                        for (File xmlFP : xmlFPs) {
                            if ((builder != null) && xmlFP.getName().contains(".xml")
                                    && (xmlFP.length() != 0)) {
                                Timber.i("Processing record xml file: %s: %s", userId, xmlFP);
                                xmlDocument = builder.parse(xmlFP);
                                processXmlNode(chatType);
                            }
                            else if (!xmlFP.getName().contains(".dat")) {
                                Timber.w("Error parsing chat history file: %s", xmlFP);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Timber.e("Couldn't parse message records!!. %s", ex.getMessage());
            }
        }
    }

    /**
     * Parses <tt>groupNode</tt> and all of its subNodes, creating corresponding instances through
     * <tt>mclServiceImpl</tt> as children of <tt>parentGroup</tt>
     */
    public static void processXmlNode(int chatType)
    {
        int msgType = ChatMessage.MESSAGE_IN;
        String content, nick, status;
        Node historyNode = xmlDocument.getFirstChild();
        NodeList msgRecords = (historyNode == null) ? null : historyNode.getChildNodes();

        // loop through all the records and extract each record details
        for (int i = 0; (msgRecords != null) && (i < msgRecords.getLength()); i++) {
            Node recordNode = msgRecords.item(i);
            if (recordNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            msgValues.clear();
            try {
                msgValues.put(ChatMessage.SESSION_UUID, sessionUuid);

                String direction
                        = XMLUtils.findChild((Element) recordNode, ATTR_DIR).getTextContent();
                msgValues.put(ChatMessage.DIRECTION, direction);

                // default setting for IMessage and File History in/out
                msgValues.put(ChatMessage.ENTITY_JID, entityJid);

                if ((chatType == Type_Chat) || (chatType == Type_Muc)) {
                    content = XMLUtils.findChild((Element) recordNode, ATTR_UID).getTextContent();
                    msgValues.put(ChatMessage.UUID, content);

                    String msgBody = XMLUtils.findChild((Element) recordNode, ATTR_MSG)
                            .getTextContent();
                    msgValues.put(ChatMessage.MSG_BODY, msgBody);

                    //					content = XMLUtils.findChild((Element) recordNode, ATTR_ENC).getTextContent();
                    //					msgValues.put(ChatMessage.ENC_TYPE, content);
                    msgValues.put(ChatMessage.ENC_TYPE, IMessage.ENCODE_PLAIN);

                    timeDate = XMLUtils.findChild((Element) recordNode, ATTR_TS).getTextContent();

                    if (direction.equals(ChatMessage.DIR_IN))
                        msgValues.put(ChatMessage.STATUS, STATUS_RECEIVED);
                    else
                        msgValues.put(ChatMessage.STATUS, STATUS_SEND);

                    msgValues.put(ChatMessage.FILE_PATH, "");

                    if (chatType == Type_Chat) {
                        if (direction.equals(ChatMessage.DIR_IN)) {
                            if (msgBody.contains(LAT_LNG)) // location type if it has LatLng:
                                msgType = ChatMessage.MESSAGE_LOCATION_IN;
                            else
                                msgType = ChatMessage.MESSAGE_IN;
                        }
                        else {
                            if (msgBody.contains(LAT_LNG)) // location type if it has LatLng:
                                msgType = ChatMessage.MESSAGE_LOCATION_OUT;
                            else
                                msgType = ChatMessage.MESSAGE_OUT;
                        }
                    }
                    else if (chatType == Type_Muc) {
                        if (direction.equals(ChatMessage.DIR_IN)) {
                            msgType = ChatMessage.MESSAGE_MUC_IN;
                            nick = XMLUtils.findChild((Element) recordNode, ATTR_SUB).getTextContent();
                            // old database does not contain the info for muc messages
                            // msgValues.putNull(ChatMessage.CONTACT_JID);
                        }
                        else {
                            msgType = ChatMessage.MESSAGE_MUC_OUT;
                            nick = userNick;
                            msgValues.put(ChatMessage.JID, userId);
                        }
                        // msgValues.put(ChatMessage.ENTITY_JID, entityJid + "/" + nick);
                        msgValues.put(ChatMessage.ENTITY_JID, nick);
                    }
                }
                else if (chatType == Type_File) {
                    msgType = ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY;

                    content = XMLUtils.findChild((Element) recordNode, ATTR_ID).getTextContent();
                    msgValues.put(ChatMessage.UUID, content);
                    timeDate = XMLUtils.findChild((Element) recordNode, ATTR_DATE).getTextContent();
                    status = XMLUtils.findChild((Element) recordNode, ATTR_STATUS).getTextContent();
                    // msgValues.put(ChatMessage.MSG_BODY,
                    // 		FileHistoryConversation.getStatusMessage(direction, status));
                    msgValues.put(ChatMessage.MSG_BODY, status);
                    msgValues.put(ChatMessage.STATUS, status);
                    msgValues.put(ChatMessage.ENC_TYPE, IMessage.ENCODE_PLAIN);

                    content = XMLUtils.findChild((Element) recordNode, ATTR_FILE).getTextContent();
                    msgValues.put(ChatMessage.FILE_PATH, content);
                }
                msgValues.put(ChatMessage.MSG_TYPE, msgType);

                Date date = simpleDateFormat.parse(timeDate);
                Long sqTimeStamp = date.getTime();
                msgValues.put(ChatMessage.TIME_STAMP, sqTimeStamp);

                if (!sessionRecordCreated) {
                    sessionRecordCreated = true;
                    sessionValues.put(ChatSession.CREATED, sqTimeStamp);
                    mDB.insert(ChatSession.TABLE_NAME, null, sessionValues);
                }
                mDB.insert(ChatMessage.TABLE_NAME, null, msgValues);
            } catch (Exception ex) {
                Timber.w("Failed to parse record: %s. Exception: %s", accountUid, ex.getMessage());
            }
        }
    }

    private static boolean findSessionUuid(String chatSessionUuid)
    {
        String[] args = {chatSessionUuid};
        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, null, ChatSession.SESSION_UUID + "=?",
                args, null, null, null);
        boolean found = cursor.getCount() > 0;
        cursor.close();
        return found;
    }
}