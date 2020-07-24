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
package org.atalk.android.gui.chat.chatsession;

import org.jxmpp.jid.EntityBareJid;

import java.util.Date;

/**
 * Add Source call to the CallRecord
 *
 * @author Eng Chong Meng
 */
public class ChatSessionRecord
{
    /**
     * The id that uniquely identifies the chat session record.
     */
    protected String sessionUuid;

    /**
     * The owner (from) of the chat session record.
     */
    protected String accountUid;

    /**
     * The receiver of the chat session: contact bareJid or conference entity
     */
    protected EntityBareJid entityBareJid;

    /**
     * 0 = 1:1 chat or 1 = multi chat session.
     *
     * @see org.atalk.android.gui.chat.ChatSession#MODE_XXX
     */
    protected int chatMode;

    /**
     * Chat encryption mode: ChatSession.STATUS to store ChatFragment#chatType
     *
     * @see org.atalk.android.gui.chat.ChatFragment#MSGTYPE_XXX
     */
    protected int chatType;

    /**
     * The chat session creation date.
     */
    protected Date dateCreate;

    /**
     * Creates Call Record
     *
     * @param direction String
     * @param startTime Date
     * @param endTime Date
     */
    public ChatSessionRecord(String sessionUuid, String accountUid, EntityBareJid entityBareJid, int chatMode, int chatType, Date createTime)
    {
        this.sessionUuid = sessionUuid;
        this.accountUid = accountUid;
        this.entityBareJid = entityBareJid;
        this.chatMode = chatMode;
        this.chatType = chatType;
        this.dateCreate = createTime;
    }

    /**
     * The Session Uuid of this record
     *
     * @return sessionUuid
     */
    public String getSessionUuid()
    {
        return sessionUuid;
    }

    public String getAccountUid()
    {
        return accountUid;
    }

    public String getAccountUserId()
    {
        return accountUid.split(":")[1];
    }

    public String getEntityId()
    {
        return entityBareJid.toString();
    }

    public EntityBareJid getEntityBareJid()
    {
        return entityBareJid;
    }

    public int getChatMode()
    {
        return chatMode;
    }

    public int getChatType()
    {
        return chatType;
    }

    public Date getDateCreate()
    {
        return dateCreate;
    }
}
