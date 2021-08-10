/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.gui;

import android.graphics.Point;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.event.ChatListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.LoginManager;

import org.atalk.android.util.java.awt.Dimension;

import java.util.*;

/**
 * The <tt>UIService</tt> offers generic access to the graphical user interface
 * for all modules that would like to interact with the user.
 * <p>
 * Through the <tt>UIService</tt> all modules can add their own components in
 * different menus, toolbars, etc. within the ui. Each <tt>UIService</tt>
 * implementation should export its supported "pluggable" containers - a set of
 * <tt>Container</tt>s corresponding to different "places" in the application,
 * where a module can add a component.
 * <p>
 * The <tt>UIService</tt> provides also methods that would allow to other
 * modules to control the visibility, size and position of the main application
 * window. Some of these methods are: setVisible, minimize, maximize, resize,
 * move, etc.
 * <p>
 * A way to show different types of simple windows is provided to allow other
 * modules to show different simple messages, like warning or error messages. In
 * order to show a simple warning message, a module should invoke the
 * getPopupDialog method and then one of the showXXX methods, which corresponds
 * best to the required dialog.
 * <p>
 * Certain components within the GUI, like "AddContact" window for example,
 * could be also shown from outside the UI bundle. To make one of these
 * component exportable, the <tt>UIService</tt> implementation should attach to
 * it an <tt>WindowID</tt>. A window then could be shown, by invoking
 * <code>getExportedWindow(WindowID)</code> and then <code>show</code>. The
 * <tt>WindowID</tt> above should be obtained from
 * <code>getSupportedExportedWindows</code>.
 *
 * @author Yana Stamcheva
 * @author Dmitri Melnikov
 * @author Adam Netocny
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface UIService
{
    /**
     * Returns TRUE if the application is visible and FALSE otherwise. This
     * method is meant to be used by the systray service in order to detect the
     * visibility of the application.
     *
     * @return <code>true</code> if the application is visible and
     * <code>false</code> otherwise.
     * @see #setVisible(boolean)
     */
    boolean isVisible();

    /**
     * Shows or hides the main application window depending on the value of
     * parameter <code>visible</code>. Meant to be used by the systray when it
     * needs to show or hide the application.
     *
     * @param visible if <code>true</code>, shows the main application window;
     * otherwise, hides the main application window.
     * @see #isVisible()
     */
    void setVisible(boolean visible);

    /**
     * Returns the current location of the main application window. The returned
     * point is the top left corner of the window.
     *
     * @return The top left corner coordinates of the main application window.
     */
    Point getLocation();

    /**
     * Locates the main application window to the new x and y coordinates.
     *
     * @param x The new x coordinate.
     * @param y The new y coordinate.
     */
    void setLocation(int x, int y);

    /**
     * Returns the size of the main application window.
     *
     * @return the size of the main application window.
     */
    Dimension getSize();

    /**
     * Sets the size of the main application window.
     *
     * @param width The width of the window.
     * @param height The height of the window.
     */
    void setSize(int width, int height);

    /**
     * Minimizes the main application window.
     */
    void minimize();

    /**
     * Maximizes the main application window.
     */
    void maximize();

    /**
     * Restores the main application window.
     */
    void restore();

    /**
     * Resizes the main application window with the given width and height.
     *
     * @param width The new width.
     * @param height The new height.
     */
    void resize(int width, int height);

    /**
     * Moves the main application window to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    void move(int x, int y);

    /**
     * Brings the focus to the main application window.
     */
    void bringToFront();

    /**
     * Sets the exitOnClose property. When TRUE, the user could exit the
     * application by simply closing the main application window (by clicking
     * the X button or pressing Alt-F4). When set to FALSE the main application
     * window will be only hidden.
     *
     * @param exitOnClose When TRUE, the user could exit the application by
     * simply closing the main application window (by clicking the X
     * button or pressing Alt-F4). When set to FALSE the main
     * application window will be only hidden.
     */
    void setExitOnMainWindowClose(boolean exitOnClose);

    /**
     * Returns TRUE if the application could be exited by closing the main
     * application window, otherwise returns FALSE.
     *
     * @return Returns TRUE if the application could be exited by closing the
     * main application window, otherwise returns FALSE
     */
    boolean getExitOnMainWindowClose();

    /**
     * Returns an exported window given by the <tt>WindowID</tt>. This could be
     * for example the "Add contact" window or any other window within the
     * application. The <tt>windowID</tt> should be one of the WINDOW_XXX
     * obtained by the <tt>getSupportedExportedWindows</tt> method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified <tt>windowID</tt> is
     * not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    ExportedWindow getExportedWindow(WindowID windowID)
            throws IllegalArgumentException;

    /**
     * Returns an exported window given by the <tt>WindowID</tt>. This could be
     * for example the "Add contact" window or any other window within the
     * application. The <tt>windowID</tt> should be one of the WINDOW_XXX
     * obtained by the <tt>getSupportedExportedWindows</tt> method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @param params The parameters to be passed to the returned exported window.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified <tt>windowID</tt> is
     * not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    ExportedWindow getExportedWindow(WindowID windowID, Object[] params)
            throws IllegalArgumentException;

    /**
     * Returns a configurable popup dialog, that could be used to show either a
     * warning message, error message, information message, etc. or to prompt
     * user for simple one field input or to question the user.
     *
     * @return a <code>PopupDialog</code>.
     * @see PopupDialog
     */
    PopupDialog getPopupDialog();

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     *
     * @param contact the <tt>Contact</tt> for which the searched chat is about.
     * @return the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     */
    Chat getChat(Contact contact);

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     *
     * @param contact the <tt>Contact</tt> for which the searched chat is about.
     * @param escapedMessageID the message ID of the message that should be
     * excluded from the history when the last one is loaded in the chat
     * @return the <tt>Chat</tt> corresponding to the given <tt>Contact</tt>.
     */
    Chat getChat(Contact contact, String escapedMessageID);

    /**
     * Returns the <tt>Chat</tt> corresponding to the given <tt>ChatRoom</tt>.
     *
     * @param chatRoom the <tt>ChatRoom</tt> for which the searched chat is about.
     * @return the <tt>Chat</tt> corresponding to the given <tt>ChatRoom</tt>.
     */
    Chat getChat(ChatRoom chatRoom);

    /**
     * Returns a list of all open Chats
     *
     * @return A list of all open Chats
     */
    List<Chat> getChats();

    /**
     * Get the MetaContact corresponding to the chat.
     * The chat must correspond to a one on one conversation. If it is a
     * group chat an exception will be thrown.
     *
     * @param chat The chat to get the MetaContact from
     * @return The MetaContact corresponding to the chat.
     */
    MetaContact getChatContact(Chat chat);

    /**
     * Returns the selected <tt>Chat</tt>.
     *
     * @return the selected <tt>Chat</tt>.
     */
    Chat getCurrentChat();

    /**
     * Returns the phone number currently entered in the phone number field.
     * This method is meant to be used by plugins that are interested in
     * operations with the currently entered phone number.
     *
     * @return the phone number currently entered in the phone number field.
     */
    String getCurrentPhoneNumber();

    /**
     * Sets the phone number in the phone number field. This method is meant to
     * be used by plugins that are interested in operations with the currently
     * entered phone number.
     *
     * @param phoneNumber the phone number to enter.
     */
    void setCurrentPhoneNumber(String phoneNumber);

    /**
     * Returns a default implementation of the <tt>SecurityAuthority</tt>
     * interface that can be used by non-UI components that would like to launch
     * the registration process for a protocol provider. Initially this method
     * was meant for use by the systray bundle and the protocol URI handlers.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> for which
     * the authentication window is about.
     * @return a default implementation of the <tt>SecurityAuthority</tt>
     * interface that can be used by non-UI components that would like
     * to launch the registration process for a protocol provider.
     */
    SecurityAuthority getDefaultSecurityAuthority(ProtocolProviderService protocolProvider);

    /**
     * Returns an iterator over a set of windowID-s. Each <tt>WindowID</tt>
     * points to a window in the current UI implementation. Each
     * <tt>WindowID</tt> in the set is one of the constants in the
     * <tt>ExportedWindow</tt> interface. The method is meant to be used by
     * bundles that would like to have access to some windows in the gui - for
     * example the "Add contact" window, the "Settings" window, the "Chat window", etc.
     *
     * @return Iterator An iterator to a set containing WindowID-s representing
     * all exported windows supported by the current UI implementation.
     */
    Iterator<WindowID> getSupportedExportedWindows();

    /**
     * Checks if a window with the given <tt>WindowID</tt> is contained in the
     * current UI implementation.
     *
     * @param windowID one of the <tt>WindowID</tt>-s, defined in the
     * <tt>ExportedWindow</tt> interface.
     * @return <code>true</code> if the component with the given
     * <tt>WindowID</tt> is contained in the current UI implementation,
     * <code>false</code> otherwise.
     */
    boolean isExportedWindowSupported(WindowID windowID);

    /**
     * Returns an iterator over a set containing containerID-s pointing to
     * containers supported by the current UI implementation. Each containerID
     * in the set is one of the CONTAINER_XXX constants. The method is meant to
     * be used by plugins or bundles that would like to add components to the
     * user interface. Before adding any component they should use this method
     * to obtain all possible places, which could contain external components,
     * like different menus, toolbars, etc.
     *
     * @return Iterator An iterator to a set containing containerID-s
     * representing all containers supported by the current UI implementation.
     */
    Iterator<Container> getSupportedContainers();

    /**
     * Checks if the container with the given <tt>Container</tt> is supported
     * from the current UI implementation.
     *
     * @param containderID One of the CONTAINER_XXX Container-s.
     * @return <code>true</code> if the container with the given
     * <tt>Container</tt> is supported from the current UI
     * implementation, <code>false</code> otherwise.
     */
    boolean isContainerSupported(Container containderID);

    /**
     * Determines whether the Mac OS X screen menu bar is being used by the UI for
     * its main menu instead of the Windows-like menu bars at the top of the windows.
     * <p>
     * A common use of the returned indicator is for the purposes of
     * platform-sensitive UI since Mac OS X employs a single screen menu bar,
     * Windows and Linux/GTK+ use per-window menu bars and it is inconsistent on
     * Mac OS X to have the Window-like menu bars.
     * </p>
     *
     * @return <tt>true</tt> if the Mac OS X screen menu bar is being used by
     * the UI for its main menu instead of the Windows-like menu bars at
     * the top of the windows; otherwise, <tt>false</tt>
     */
    boolean useMacOSXScreenMenuBar();

    /**
     * Provides all currently instantiated <tt>Chats</tt>.
     *
     * @return all active <tt>Chats</tt>.
     */
    Collection<Chat> getAllChats();

    /**
     * Registers a <tt>NewChatListener</tt> to be informed when new <tt>Chats</tt> are created.
     *
     * @param listener listener to be registered
     */
    void addChatListener(ChatListener listener);

    /**
     * Removes the registration of a <tt>NewChatListener</tt>.
     *
     * @param listener listener to be unregistered
     */
    void removeChatListener(ChatListener listener);

    /**
     * Repaints and revalidates the whole UI. This method is meant to be used
     * to runtime apply a skin and refresh automatically the user interface.
     */
    void repaintUI();

    /**
     * Creates a new <tt>Call</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the
     * participants to be included into the newly created <tt>Call</tt>
     */
    void createCall(String[] participants);

    /**
     * Starts a new <tt>Chat</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the
     * participants to be included into the newly created <tt>Chat</tt>
     */
    void startChat(String[] participants);

    /**
     * Starts a new <tt>Chat</tt> with a specific set of participants.
     *
     * @param participants an array of <tt>String</tt> values specifying the
     * participants to be included into the newly created <tt>Chat</tt>
     * @param isSmsEnabled whether sms option should be enabled if possible
     */
    void startChat(String[] participants, boolean isSmsEnabled);

    /**
     * Returns a collection of all currently in progress calls.
     *
     * @return a collection of all currently in progress calls.
     */
    Collection<Call> getInProgressCalls();

    /**
     * Returns the login manager used by the current UI implementation.
     *
     * @return the login manager used by the current UI implementation
     */
    LoginManager getLoginManager();

    /**
     * Opens a chat room window for the given <tt>ChatRoomWrapper</tt> instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    void openChatRoomWindow(ChatRoomWrapper chatRoom);

    /**
     * Closes the chat room window for the given <tt>ChatRoomWrapper</tt>
     * instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    void closeChatRoomWindow(ChatRoomWrapper chatRoom);

    /**
     * Shows Add chat room dialog.
     */
    void showAddChatRoomDialog();

    /**
     * Shows chat room open automatically configuration dialog.
     *
     * @param chatRoomId the chat room id of the chat room associated with the
     * dialog
     * @param pps the protocol provider service of the chat room
     */
    void showChatRoomAutoOpenConfigDialog(ProtocolProviderService pps, String chatRoomId);
}
