/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import android.app.Activity;
import android.graphics.Point;
import android.view.Display;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.gui.event.ChatListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.LoginManager;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.conference.ConferenceChatManager;
import org.atalk.android.util.java.awt.Dimension;

import java.util.*;

/**
 * Android <tt>UIService</tt> stub. Currently used only for supplying the
 * <tt>SecurityAuthority</tt> to the reconnect plugin.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidUIServiceImpl implements UIService
{
    private final ConferenceChatManager conferenceChatManager = new ConferenceChatManager();
    /**
     * Default security authority.
     */
    private SecurityAuthority defaultSecurityAuthority;

    /**
     * private LoginManager loginManager; Creates new instance of <tt>AndroidUIService</tt>.
     *
     * @param defaultSecurityAuthority default security authority that will be used.
     */
    public AndroidUIServiceImpl(SecurityAuthority defaultSecurityAuthority)
    {
        this.defaultSecurityAuthority = defaultSecurityAuthority;
    }

    /**
     * Returns TRUE if the application is visible and FALSE otherwise. This method is meant to be
     * used by the systray service in order to detect the visibility of the application.
     *
     * @return {@code true</code> if the application is visible and <code>false} otherwise.
     * @see #setVisible(boolean)
     */
    @Override
    public boolean isVisible()
    {
        return (aTalkApp.getCurrentActivity() != null);
    }

    /**
     * Shows or hides the main application window depending on the value of parameter
     * {@code visible}. Meant to be used by the systray when it needs to show or hide the
     * application.
     *
     * @param visible if {@code true}, shows the main application window; otherwise, hides the main
     * application window.
     * @see #isVisible()
     */
    @Override
    public void setVisible(boolean visible)
    {
    }

    /**
     * Returns the current location of the main application window. The returned point is the top
     * left corner of the window.
     *
     * @return The top left corner coordinates of the main application window.
     */
    @Override
    public Point getLocation()
    {
        return new Point(0, 0);
    }

    /**
     * Locates the main application window to the new x and y coordinates.
     *
     * @param x The new x coordinate.
     * @param y The new y coordinate.
     */
    @Override
    public void setLocation(int x, int y)
    {

    }

    /**
     * Returns the size of the main application window.
     *
     * @return the size of the main application window.
     */
    @Override
    public Dimension getSize()
    {
        Activity activity = aTalkApp.getCurrentActivity();
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point outSize = new Point();
        display.getSize(outSize);
        return new Dimension(outSize.x, outSize.y);
    }

    /**
     * Sets the size of the main application window.
     *
     * @param width The width of the window.
     * @param height The height of the window.
     */
    @Override
    public void setSize(int width, int height)
    {
    }

    /**
     * Minimizes the main application window.
     */
    @Override
    public void minimize()
    {
    }

    /**
     * Maximizes the main application window.
     */
    @Override
    public void maximize()
    {
    }

    /**
     * Restores the main application window.
     */
    @Override
    public void restore()
    {
    }

    /**
     * Resize the main application window with the given width and height.
     *
     * @param width The new width.
     * @param height The new height.
     */
    @Override
    public void resize(int width, int height)
    {
    }

    /**
     * Moves the main application window to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    @Override
    public void move(int x, int y)
    {
    }

    /**
     * Brings the focus to the main application window.
     */
    @Override
    public void bringToFront()
    {
    }

    /**
     * Returns TRUE if the application could be exited by closing the main application window,
     * otherwise returns FALSE.
     *
     * @return Returns TRUE if the application could be exited by closing the main application
     * window, otherwise returns FALSE
     */
    @Override
    public boolean getExitOnMainWindowClose()
    {
        return false;
    }

    /**
     * Sets the exitOnClose property. When TRUE, the user could exit the application by simply
     * closing the main application window (by clicking the X button or pressing Alt-F4). When
     * set to FALSE the main application window will be only hidden.
     *
     * @param exitOnClose When TRUE, the user could exit the application by simply closing the main application
     * window (by clicking the X button or pressing Alt-F4). When set to FALSE the main
     * application window will be only hidden.
     */
    @Override
    public void setExitOnMainWindowClose(boolean exitOnClose)
    {
    }

    /**
     * Returns an exported window given by the <tt>WindowID</tt>. This could be for example the
     * "Add contact" window or any other window within the application. The <tt>windowID</tt>
     * should be one of the WINDOW_XXX obtained by the <tt>getSupportedExportedWindows</tt>
     * method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified <tt>windowID</tt> is not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Override
    public ExportedWindow getExportedWindow(WindowID windowID)
            throws IllegalArgumentException
    {
        return null;
    }

    /**
     * Returns an exported window given by the <tt>WindowID</tt>. This could be for example the
     * "Add contact" window or any other window within the application. The <tt>windowID</tt>
     * should be one of the WINDOW_XXX obtained by the <tt>getSupportedExportedWindows</tt>
     * method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @param params The parameters to be passed to the returned exported window.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified <tt>windowID</tt> is not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Override
    public ExportedWindow getExportedWindow(WindowID windowID, Object[] params)
            throws IllegalArgumentException
    {
        return null;
    }

    /**
     * Returns a configurable popup dialog, that could be used to show either a warning message,
     * error message, information message, etc. or to prompt user for simple one field input or
     * to question the user.
     *
     * @return a {@code PopupDialog}.
     * @see net.java.sip.communicator.service.gui.PopupDialog
     */
    @Override
    public PopupDialog getPopupDialog()
    {
        return null;
    }

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     *
     * @param contact the <tt>Contact</tt> for which the searched chat is about.
     * @return the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     */
    @Override
    public Chat getChat(Contact contact)
    {
        return ChatSessionManager.createChatForContact(contact);
    }

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     *
     * @param contact the <tt>Contact</tt> for which the searched chat is about.
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     * @return the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     */
    @Override
    public Chat getChat(Contact contact, String escapedMessageID)
    {
        return getChat(contact);
    }

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>ChatRoom</tt>.
     *
     * @param chatRoom the <tt>ChatRoom</tt> for which the searched chat is about.
     * @return the <tt>Chat</tt> corresponding to the given <tt>ChatRoom</tt>.
     */
    @Override
    public Chat getChat(ChatRoom chatRoom)
    {
        return ChatSessionManager.getMultiChat(chatRoom, true);
    }

    /**
     * Returns a list of all open Chats
     *
     * @return A list of all open Chats
     */
    @Override
    public List<Chat> getChats()
    {
        return ChatSessionManager.getActiveChats();
    }

    /**
     * Get the MetaContact corresponding to the chat. The chat must correspond to a one on one
     * conversation. If it is a group chat an exception will be thrown.
     *
     * @param chat The chat to get the MetaContact from
     * @return The MetaContact corresponding to the chat.
     */
    @Override
    public MetaContact getChatContact(Chat chat)
    {
        ChatPanel chatPanel = (ChatPanel) chat;
        return (MetaContact) chatPanel.getChatSession().getDescriptor();
    }

    /**
     * Returns the selected <tt>Chat</tt>.
     *
     * @return the selected <tt>Chat</tt>.
     */
    @Override
    public Chat getCurrentChat()
    {
        return ChatSessionManager.getCurrentChatPanel();
    }

    /**
     * Returns the phone number currently entered in the phone number field. This method is meant
     * to be used by plugins that are interested
     * in operations with the currently entered phone number.
     *
     * @return the phone number currently entered in the phone number field.
     */
    @Override
    public String getCurrentPhoneNumber()
    {
        return null;
    }

    /**
     * Sets the phone number in the phone number field. This method is meant to be used by plugins
     * that are interested in operations with
     * the currently entered phone number.
     *
     * @param phoneNumber the phone number to enter.
     */
    @Override
    public void setCurrentPhoneNumber(String phoneNumber)
    {
    }

    /**
     * Returns a default implementation of the <tt>SecurityAuthority</tt> interface that can be
     * used by non-UI components that would like to
     * launch the registration process for a protocol provider. Initially this method was meant
     * for use by the systray bundle and the
     * protocol URI handlers.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> for which the authentication window is about.
     * @return a default implementation of the <tt>SecurityAuthority</tt> interface that can be
     * used by non-UI components that would like to
     * launch the registration process for a protocol provider.
     */
    @Override
    public SecurityAuthority getDefaultSecurityAuthority(ProtocolProviderService protocolProvider)
    {
        return defaultSecurityAuthority;
    }

    /**
     * Returns an iterator over a set of windowID-s. Each <tt>WindowID</tt> points to a window in
     * the current UI implementation. Each
     * <tt>WindowID</tt> in the set is one of the constants in the <tt>ExportedWindow</tt>
     * interface. The method is meant to be used by
     * bundles that would like to have access to some windows in the gui - for example the "Add
     * contact" window, the "Settings" window, the
     * "Chat window", etc.
     *
     * @return Iterator An iterator to a set containing WindowID-s representing all exported
     * windows supported by the current UI
     * implementation.
     */
    @Override
    public Iterator<WindowID> getSupportedExportedWindows()
    {
        return null;
    }

    /**
     * Checks if a window with the given <tt>WindowID</tt> is contained in the current UI
     * implementation.
     *
     * @param windowID one of the <tt>WindowID</tt>-s, defined in the <tt>ExportedWindow</tt> interface.
     * @return {@code true} if the component with the given <tt>WindowID</tt> is contained in
     * the current UI implementation,
     * {@code false} otherwise.
     */
    @Override
    public boolean isExportedWindowSupported(WindowID windowID)
    {
        return false;
    }

    /**
     * Returns an iterator over a set containing containerID-s pointing to containers supported by
     * the current UI implementation. Each
     * containerID in the set is one of the CONTAINER_XXX constants. The method is meant to be
     * used by plugins or bundles that would like to
     * add components to the user interface. Before adding any component they should use this
     * method to obtain all possible places, which
     * could contain external components, like different menus, toolbars, etc.
     *
     * @return Iterator An iterator to a set containing containerID-s representing all containers
     * supported by the current UI
     * implementation.
     */
    @Override
    public Iterator<Container> getSupportedContainers()
    {
        return null;
    }

    /**
     * Checks if the container with the given <tt>Container</tt> is supported from the current UI
     * implementation.
     *
     * @param containderID One of the CONTAINER_XXX Container-s.
     * @return {@code true} if the container with the given <tt>Container</tt> is supported
     * from the current UI implementation,
     * {@code false} otherwise.
     */
    @Override
    public boolean isContainerSupported(Container containderID)
    {
        return false;
    }

    /**
     * Determines whether the Mac OS X screen menu bar is being used by the UI for its main menu
     * instead of the Windows-like menu bars at the top of the windows.
     * <p>
     * A common use of the returned indicator is for the purposes of platform-sensitive UI since
     * Mac OS X employs a single screen menu bar, Windows and Linux/GTK+ use per-window menu bars
     * and it is inconsistent on Mac OS X to have the Window-like menu bars.
     * </p>
     *
     * @return <tt>true</tt> if the Mac OS X screen menu bar is being used by the UI for its main
     * menu instead of the Windows-like menu bars at the top of the windows; otherwise,
     * <tt>false</tt>
     */
    @Override
    public boolean useMacOSXScreenMenuBar()
    {
        return false;
    }

    /**
     * Provides all currently instantiated <tt>Chats</tt>.
     *
     * @return all active <tt>Chats</tt>.
     */
    @Override
    public Collection<Chat> getAllChats()
    {
        return ChatSessionManager.getActiveChats();
    }

    /**
     * Registers a <tt>NewChatListener</tt> to be informed when new <tt>Chats</tt> are created.
     *
     * @param listener listener to be registered
     */
    @Override
    public void addChatListener(ChatListener listener)
    {
        ChatSessionManager.addChatListener(listener);
    }

    /**
     * Removes the registration of a <tt>NewChatListener</tt>.
     *
     * @param listener listener to be unregistered
     */
    @Override
    public void removeChatListener(ChatListener listener)
    {
        ChatSessionManager.removeChatListener(listener);
    }

    /**
     * Repaints and revalidates the whole UI. This method is meant to be used to runtime apply a
     * skin and refresh automatically the user interface.
     */
    @Override
    public void repaintUI()
    {
    }

    /**
     * Creates a new <tt>Call</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the participants to be included into the
     * newly created <tt>Call</tt>
     */
    @Override
    public void createCall(String[] participants)
    {
    }

    /**
     * Starts a new <tt>Chat</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the participants to be included into the
     * newly created <tt>Chat</tt>
     */
    @Override
    public void startChat(String[] participants)
    {
    }

    /**
     * Starts a new <tt>Chat</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the participants to be included into the
     * newly created <tt>Chat</tt>
     * @param isSmsEnabled whether sms option should be enabled if possible
     */
    @Override
    public void startChat(String[] participants, boolean isSmsEnabled)
    {
    }

    /**
     * Returns a collection of all currently in progress calls.
     *
     * @return a collection of all currently in progress calls.
     */
    @Override
    public Collection<Call> getInProgressCalls()
    {
        return CallManager.getActiveCalls();
    }

    /**
     * Returns the login manager used by the current UI implementation.
     *
     * @return the login manager used by the current UI implementation
     */
    @Override
    public LoginManager getLoginManager()
    {
        return AndroidGUIActivator.getLoginManager();
    }

    /**
     * Returns the chat conference manager.
     *
     * @return the chat conference manager.
     */
    public ConferenceChatManager getConferenceChatManager()
    {
        return conferenceChatManager;
    }

    /**
     * Opens a chat room window for the given <tt>ChatRoomWrapper</tt> instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    @Override
    public void openChatRoomWindow(ChatRoomWrapper chatRoom)
    {
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoom, true);
        // ChatSessionManager.openChat(chatPanel, true);
        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
    }

    /**
     * Closes the chat room window for the given <tt>ChatRoomWrapper</tt> instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    @Override
    public void closeChatRoomWindow(ChatRoomWrapper chatRoom)
    {
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoom, false);
        if (chatPanel != null) {
            ChatSessionManager.removeActiveChat(chatPanel);
        }
    }

    /**
     * Shows Add chat room dialog.
     */
    @Override
    public void showAddChatRoomDialog()
    {
        // ChatRoomTableDialog.showChatRoomTableDialog();
    }

    /**
     * Shows chat room open automatically configuration dialog.
     *
     * @param chatRoomId the chat room id of the chat room associated with the dialog
     * @param pps the protocol provider service of the chat room
     */
    @Override
    public void showChatRoomAutoOpenConfigDialog(ProtocolProviderService pps, String chatRoomId)
    {
        // ChatRoomAutoOpenConfigDialog.showChatRoomAutoOpenConfigDialog(pps, chatRoomId);
    }
}
