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
package org.atalk.android.gui.chatroomslist.model;

import android.graphics.drawable.Drawable;

/**
 * Interface used to obtain data required to display chatRoom information. Implementing classes can
 * expect to receive their implementation specific objects in calls to any method of this interface.
 *
 * @author Eng Chong Meng
 */
public interface UIChatRoomRenderer
{
    /**
     * Return <tt>true</tt> if given contact is considered to be currently selected.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return <tt>true</tt> if given chatRoomWrapper is considered to be currently selected.
     */
    boolean isSelected(Object chatRoomWrapper);

    /**
     * Returns chatRoomWrapper display name.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return chatRoomWrapper display name.
     */
    String getDisplayName(Object chatRoomWrapper);

    /**
     * Returns chatRoomWrapper status message.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return chatRoomWrapper status message.
     */
    String getStatusMessage(Object chatRoomWrapper);

    /**
     * Returns <tt>true</tt> if given chatRoomWrapper name should be displayed in bold.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return <tt>true</tt> if given chatRoomWrapper name should be displayed in bold.
     */
    boolean isDisplayBold(Object chatRoomWrapper);

    /**
     * Returns chatRoomWrapper Icon image.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return chatRoomWrapper avatar image.
     */
    Drawable getChatRoomIcon(Object chatRoomWrapper);

    /**
     * Returns chatRoomID that can be used to establish an outgoing connection.
     *
     * @param chatRoomWrapper chatRoomWrapper instance.
     * @return chatRoomID that can be used to establish an outgoing connection.
     */
    String getChatRoomID(Object chatRoomWrapper);

    boolean isAutoJoin(Object chatRoomWrapper);
    boolean isBookmark(Object chatRoomWrapper);
}
