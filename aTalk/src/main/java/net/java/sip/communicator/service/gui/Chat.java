/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.service.gui;

import java.util.Date;

/**
 * The <tt>Chat</tt> interface is meant to be implemented by the GUI component class representing
 * a chat. Through the <i>isChatFocused</i> method the other bundles could check the visibility
 * of the chat component. The <tt>ChatFocusListener</tt> is used to inform other bundles
 * when a chat has changed its focus state.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface Chat
{
    /**
     * The size of the buffer that indicates how many messages will be stored in the conversation
     * area in the chat window.
     */
    public static final int CHAT_BUFFER_SIZE = 50000;

    /**
     * Checks if this <tt>Chat</tt> is currently focused.
     *
     * @return TRUE if the chat is focused, FALSE - otherwise
     */
    boolean isChatFocused();

    /**
     * Returns the message written by user in the chat write area.
     *
     * @return the message written by user in the chat write area
     */
    String getMessage();

    /**
     * Bring this chat to front if <tt>b</tt> is true, hide it otherwise.
     *
     * @param isVisible tells if the chat will be made visible or not.
     */
    void setChatVisible(boolean isVisible);

    /**
     * Sets the given message as a message in the chat write area.
     *
     * @param message the text that would be set to the chat write area
     */
    void setMessage(String message);

    /**
     * Adds a message to this <tt>Chat</tt>.
     *
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type of the message
     * @param mimeType the content encode type i.e plain or html
     * @param message the message text
     */
    void addMessage(String contactName, Date date, int messageType, int mimeType, String message);

    /**
     * Adds a new ChatLinkClickedListener. The callback is called for every link whose scheme is
     * <tt>jitsi</tt>. It is the callback's responsibility to filter the action based on the URI.
     * <p>
     * Example:<br>
     * <tt>jitsi://classname/action?query</tt><br>
     * Use the name of the registering class as the host, the action to execute as the path and
     * any parameters as the query.
     *
     * @param listener callback that is notified when a link was clicked.
     */
    void addChatLinkClickedListener(ChatLinkClickedListener listener);
}
