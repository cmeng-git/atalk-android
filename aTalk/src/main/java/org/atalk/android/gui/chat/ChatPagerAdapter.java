/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.event.ChatListener;

import java.util.List;

/**
 * A pager adapter used to display active chats.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatPagerAdapter extends FragmentStatePagerAdapter implements ChatListener {
    /**
     * The list of contained chat session ids.
     */
    private final List<String> chats;

    /**
     * Parent <code>ChatActivity</code>.
     */
    private final ChatActivity parent;

    /**
     * Remembers currently displayed <code>ChatFragment</code>.
     */
    private ChatFragment mPrimaryItem;

    /**
     * Creates an instance of <code>ChatPagerAdapter</code> by specifying the parent
     * <code>ChatActivity</code> and its <code>FragmentManager</code>.
     *
     * @param fm the parent <code>FragmentManager</code>
     */
    public ChatPagerAdapter(FragmentManager fm, ChatActivity parent) {
        super(fm);
        this.chats = ChatSessionManager.getActiveChatsIDs();
        this.parent = parent;
        ChatSessionManager.addChatListener(this);
    }

    /**
     * Releases resources used by this instance. Once called this instance is considered invalid.
     */
    public void dispose() {
        ChatSessionManager.removeChatListener(this);
    }

    public ChatFragment getCurrentChatFragment() {
        return mPrimaryItem;
    }

    /**
     * Returns chat id corresponding to the given position.
     *
     * @param pos the position of the chat we're looking for
     *
     * @return chat id corresponding to the given position
     */
    public String getChatId(int pos) {
        synchronized (chats) {
            if (chats.size() <= pos)
                return null;
            return chats.get(pos);
        }
    }

    /**
     * Returns index of the <code>ChatPanel</code> in this adapter identified by given <code>sessionId</code>.
     *
     * @param sessionId chat session identifier.
     *
     * @return index of the <code>ChatPanel</code> in this adapter identified by given <code>sessionId</code>.
     */
    public int getChatIdx(String sessionId) {
        if (sessionId == null)
            return -1;

        for (int i = 0; i < chats.size(); i++) {
            if (getChatId(i).equals(sessionId))
                return i;
        }
        return -1;
    }

    /**
     * Removes the given chat session id from this pager if exist.
     *
     * @param chatId the chat id to remove from this pager
     */
    public void removeChatSession(String chatId) {
        synchronized (chats) {
            if (chats.remove(chatId)) {
                notifyDataSetChanged();
            }
        }
    }

    /**
     * Removes all <code>ChatFragment</code>s from this pager.
     */
    public void removeAllChatSessions() {
        synchronized (chats) {
            chats.clear();
        }
        notifyDataSetChanged();
    }

    /**
     * Returns the position of the given <code>object</code> in this pager.
     * cmeng - Seem this is not call by PagerAdapter at all
     *
     * @return the position of the given <code>object</code> in this pager
     */
    @Override
    public int getItemPosition(Object object) {
        String id = ((ChatFragment) object).getChatPanel().getChatSession().getChatId();
        synchronized (chats) {
            if (chats.contains(id))
                return chats.indexOf(id);
        }
        return PagerAdapter.POSITION_NONE;
    }

    /**
     * Returns the <code>Fragment</code> at the given position in this pager.
     *
     * @return the <code>Fragment</code> at the given position in this pager
     */
    @Override
    public Fragment getItem(int pos) {
        return ChatFragment.newInstance(chats.get(pos));
    }

    /**
     * Instantiate the <code>ChatFragment</code> in the given container, at the given position.
     *
     * @param container the parent <code>ViewGroup</code>
     * @param position the position in the <code>ViewGroup</code>
     *
     * @return the created <code>ChatFragment</code>
     */
    @Override
    public Object instantiateItem(ViewGroup container, final int position) {
        return super.instantiateItem(container, position);
    }

    /**
     * Returns the count of contained <code>ChatFragment</code>s.
     *
     * @return the count of contained <code>ChatFragment</code>s
     */
    @Override
    public int getCount() {
        synchronized (chats) {
            return chats.size();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);

        /*
         * Notifies ChatFragments about their visibility state changes. This method is invoked
         * many times with the same parameter, so we keep track of last item and notify only on changes.
         *
         * This is required, because normal onResume/onPause fragment cycle doesn't work
         * as expected with pager adapter.
         */
        ChatFragment newPrimary = (ChatFragment) object;
        if (newPrimary != mPrimaryItem) {
            if (mPrimaryItem != null)
                mPrimaryItem.setPrimarySelected(false);
            if (newPrimary != null)
                newPrimary.setPrimarySelected(true);
        }
        mPrimaryItem = newPrimary;
    }

    @Override
    public void chatClosed(final Chat chat) {
        parent.runOnUiThread(() -> removeChatSession(((ChatPanel) chat).getChatSession().getChatId()));
    }

    @Override
    public void chatCreated(final Chat chat) {
        parent.runOnUiThread(() -> {
            synchronized (chats) {
                chats.add(((ChatPanel) chat).getChatSession().getChatId());
                notifyDataSetChanged();
            }
        });
    }
}