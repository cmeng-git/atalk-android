/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.view.ViewGroup;

import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.event.ChatListener;

import java.util.List;

import androidx.fragment.app.*;
import androidx.viewpager.widget.PagerAdapter;

/**
 * A pager adapter used to display active chats.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatPagerAdapter extends FragmentStatePagerAdapter implements ChatListener
{
    /**
     * The list of contained chat session ids.
     */
    private final List<String> chats;

    /**
     * Parent <tt>ChatActivity</tt>.
     */
    private final ChatActivity parent;

    /**
     * Remembers currently displayed <tt>ChatFragment</tt>.
     */
    private ChatFragment mPrimaryItem;

    /**
     * Creates an instance of <tt>ChatPagerAdapter</tt> by specifying the parent
     * <tt>ChatActivity</tt> and its <tt>FragmentManager</tt>.
     *
     * @param fm the parent <tt>FragmentManager</tt>
     */
    public ChatPagerAdapter(FragmentManager fm, ChatActivity parent)
    {
        super(fm);
        this.chats = ChatSessionManager.getActiveChatsIDs();
        this.parent = parent;
        ChatSessionManager.addChatListener(this);
    }

    /**
     * Releases resources used by this instance. Once called this instance is considered invalid.
     */
    public void dispose()
    {
        ChatSessionManager.removeChatListener(this);
    }

    public ChatFragment getCurrentChatFragment()
    {
        return mPrimaryItem;
    }

    /**
     * Returns chat id corresponding to the given position.
     *
     * @param pos the position of the chat we're looking for
     * @return chat id corresponding to the given position
     */
    public String getChatId(int pos)
    {
        synchronized (chats) {
            if (chats.size() <= pos)
                return null;
            return chats.get(pos);
        }
    }

    /**
     * Returns index of the <tt>ChatPanel</tt> in this adapter identified by given
     * <tt>sessionId</tt>.
     *
     * @param sessionId chat session identifier.
     * @return index of the <tt>ChatPanel</tt> in this adapter identified by given
     * <tt>sessionId</tt>.
     */
    public int getChatIdx(String sessionId)
    {
        if (sessionId == null)
            return -1;

        for (int i = 0; i < chats.size(); i++) {
            if (getChatId(i).equals(sessionId))
                return i;
        }
        return -1;
    }

    /**
     * Removes the given chat session id from this pager.
     *
     * @param chatId the chat id to remove from this pager
     */
    public void removeChatSession(String chatId)
    {
        synchronized (chats) {
            chats.remove(chatId);
        }
        notifyDataSetChanged();
    }

    /**
     * Removes all <tt>ChatFragment</tt>s from this pager.
     */
    public void removeAllChatSessions()
    {
        synchronized (chats) {
            chats.clear();
        }
        notifyDataSetChanged();
    }

    /**
     * Returns the position of the given <tt>object</tt> in this pager.
     * cmeng - Seem this is not call by PagerAdapter at all
     *
     * @return the position of the given <tt>object</tt> in this pager
     */
    @Override
    public int getItemPosition(Object object)
    {
        String id = ((ChatFragment) object).getChatPanel().getChatSession().getChatId();
        synchronized (chats) {
            if (chats.contains(id))
                return chats.indexOf(id);
        }
        return PagerAdapter.POSITION_NONE;
    }

    /**
     * Returns the <tt>Fragment</tt> at the given position in this pager.
     *
     * @return the <tt>Fragment</tt> at the given position in this pager
     */
    @Override
    public Fragment getItem(int pos)
    {
        return ChatFragment.newInstance(chats.get(pos));
    }

    /**
     * Instantiate the <tt>ChatFragment</tt> in the given container, at the given position.
     *
     * @param container the parent <tt>ViewGroup</tt>
     * @param position the position in the <tt>ViewGroup</tt>
     * @return the created <tt>ChatFragment</tt>
     */
    @Override
    public Object instantiateItem(ViewGroup container, final int position)
    {
        return super.instantiateItem(container, position);
    }

    /**
     * Returns the count of contained <tt>ChatFragment</tt>s.
     *
     * @return the count of contained <tt>ChatFragment</tt>s
     */
    @Override
    public int getCount()
    {
        synchronized (chats) {
            return chats.size();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object)
    {
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
    public void chatClosed(final Chat chat)
    {
        parent.runOnUiThread(() -> removeChatSession(((ChatPanel) chat).getChatSession().getChatId()));
    }

    @Override
    public void chatCreated(final Chat chat)
    {
        parent.runOnUiThread(() -> {
            synchronized (chats) {
                chats.add(((ChatPanel) chat).getChatSession().getChatId());
                notifyDataSetChanged();
            }
        });
    }
}