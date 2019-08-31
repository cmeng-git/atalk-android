/**
 *
 * Copyright 2003-2007 Jive Software.
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
package org.atalk.android.gui.chatroomslist;

import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Resourcepart;

/**
 * Represents a Conference Room bookmarked on the server using XEP-0048 Bookmark Storage XEP.
 *
 * @author Eng Chong Meng
 */
public class BookmarkConference
{
    private final EntityBareJid jid;
    private Resourcepart nickname;

    private String name;
    private String password;
    private boolean autoJoin;
    private boolean bookmark;
    private boolean isShared;

    public BookmarkConference(BookmarkedConference bookmark) {
        this.name = bookmark.getName();
        this.jid = bookmark.getJid();
        this.autoJoin = bookmark.isAutoJoin();
        this.nickname = bookmark.getNickname();
        this.password = bookmark.getPassword();
    }

    public BookmarkConference(String name, EntityBareJid jid, boolean autoJoin, Resourcepart nickname, String password) {
        this.name = name;
        this.jid = jid;
        this.autoJoin = autoJoin;
        this.nickname = nickname;
        this.password = password;
    }

    /**
     * Returns the display label representing the Conference room.
     *
     * @return the name of the conference room.
     */
    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the full JID of this conference room. (ex.dev@conference.jivesoftware.com)
     *
     * @return the full JID of  this conference room.
     */
    public EntityBareJid getJid() {
        return jid;
    }

    /**
     * Returns the nickname to use when joining this conference room. This is an optional
     * value and may return null.
     *
     * @return the nickname to use when joining, null may be returned.
     */
    public Resourcepart getNickname() {
        return nickname;
    }

    protected void setNickname(Resourcepart nickname) {
        this.nickname = nickname;
    }

    /**
     * Returns true if this conference room should be auto-joined on startup.
     *
     * @return true if room should be joined on startup, otherwise false.
     */
    public boolean isAutoJoin() {
        return autoJoin;
    }

    protected void setAutoJoin(boolean autoJoin) {
        this.autoJoin = autoJoin;
    }

    protected void setBookmark(boolean bookmark) {
        this.bookmark = bookmark;
    }

    public boolean isBookmark() {
        return bookmark;
    }

    /**
     * Returns the password to use when joining this conference room. This is an optional
     * value and may return null.
     *
     * @return the password to use when joining this conference room, null may be returned.
     */
    public String getPassword() {
        return password;
    }

    protected void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BookmarkedConference)) {
            return false;
        }
        BookmarkedConference conference = (BookmarkedConference) obj;
        return conference.getJid().equals(jid);
    }

    @Override
    public int hashCode() {
        return getJid().hashCode();
    }

    protected void setShared(boolean isShared) {
        this.isShared = isShared;
    }

    public boolean isShared() {
        return isShared;
    }
}
