/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc;

import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.customcontactactions.*;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.service.resources.ResourceManagementService;

import java.util.*;

/**
 * Implements <tt>CustomContactActionsService</tt> for MUC contact source.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class MUCCustomContactActionService implements CustomContactActionsService<SourceContact>
{
    /**
     * List of custom menu items.
     */
    private final List<ContactActionMenuItem<SourceContact>> mucActionMenuItems = new LinkedList<>();

    /**
     * List of custom actions.
     */
    private final List<ContactAction<SourceContact>> mucActions = new LinkedList<>();

    /**
     *
     */
    private static final String OWNER_CANT_REMOVE_CHATROOM_PROPERTY = "muc.OWNER_CANT_REMOVE_CHATROOM";

    /**
     * Array of names for the custom actions.
     */
    private String[] actionsNames = {"leave", "join", "autojoin", "autojoin_pressed", "destroy_chatroom"};

    /**
     * Array of labels for the custom actions.
     */
    private String[] actionsLabels = {"service.gui.LEAVE", "service.gui.JOIN", "service.gui.JOIN_AUTOMATICALLY",
            "service.gui.JOIN_AUTOMATICALLY", "service.gui.DESTROY_CHATROOM"};

    /**
     * Array of icons for the custom actions.
     */
    private String[] actionsIcons = {"leave_room", "join_room", "auto_join_on", "auto_join_off", "destroy_chat"};

    /**
     * Array of rollover icons for the custom actions.
     */
    private String[] actionsIconsRollover = {"leave_room_over", "join_room_over",
            "auto_join_on_over", "auto_join_off_over", "auto_join_off_over"};

    /**
     * Array of pressed icons for the custom actions.
     */
    private String[] actionsIconsPressed = {"leave_room_pressed", "join_room_pressed",
            "auto_join_on_pressed", "auto_join_off_pressed", "destroy_chat_pressed"};

    /**
     * Array of names for the custom menu items.
     */
    private String[] menuActionsNames = {"open", "join", "join_as", "leave", "remove", "change_nick",
            "auto_join", "auto_join_pressed", "open_automatically", "destroy_chatroom"};

    /**
     * Array of labels for the custom menu items.
     */
    private String[] menuActionsLabels = {"service.gui.OPEN", "service.gui.JOIN", "service.gui.JOIN_AS",
            "service.gui.LEAVE", "service.gui.REMOVE", "service.gui.CHANGE_NICK", "service.gui.JOIN_AUTOMATICALLY",
            "service.gui.DONT_JOIN_AUTOMATICALLY", "service.gui.OPEN_AUTOMATICALLY", "service.gui.DESTROY_CHATROOM"};

    /**
     * Array of icons for the custom menu items.
     */
    private String[] menuActionsIcons = {"chat_room", "join", "join_as", "leave", "remove_chat", "rename",
            "auto_join_on", "auto_join_off", "opening_chatroom_settings", "destroy_chat"};

    /**
     * A runnable that leaves the chat room.
     */
    private MUCCustomActionRunnable leaveRunnable = new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            ChatRoomWrapper leavedRoomWrapped = MUCActivator.getMUCService().leaveChatRoom(chatRoomWrapper);
            if (leavedRoomWrapped != null)
                MUCActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
        }
    };

    /**
     * A runnable that joins the chat room.
     */
    private MUCCustomActionRunnable joinRunnable = new MUCCustomActionRunnable()
    {
        @Override
        public void run()
        {
            String[] joinOptions;
            String subject = null;
            String nickName = null;

            nickName = ConfigurationUtils.getChatRoomProperty(chatRoomWrapper.getParentProvider().getProtocolProvider(),
                    chatRoomWrapper.getChatRoomID(), ChatRoom.USER_NICK_NAME);
            if (nickName == null) {
                // joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(
                // chatRoomWrapper.getParentProvider().getProtocolProvider(),
                // chatRoomWrapper.getChatRoomID(),
                // MUCActivator.getGlobalDisplayDetailsService().getDisplayName(
                // chatRoomWrapper.getParentProvider().getProtocolProvider()));
                // nickName = joinOptions[0];
                // subject = joinOptions[1];
            }

            if (nickName != null)
                MUCActivator.getMUCService().joinChatRoom(chatRoomWrapper, nickName, null, subject);
        }
    };

    /**
     * A runnable that sets / unsets auto join setting of the chat room.
     */
    private MUCCustomActionRunnable autoJoinRunnable = new MUCCustomActionRunnable()
    {
        @Override
        public void run()
        {
            chatRoomWrapper.setAutoJoin(!chatRoomWrapper.isAutoJoin());

        }
    };

    /**
     * A runnable that destroys the chat room.
     */
    private MUCCustomActionRunnable destroyActionRunnable = new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            // String destroyOptions[] = ChatRoomDestroyReasonDialog.getDestroyOptions();
            // if (destroyOptions == null)
            // return;

            // MUCActivator.getMUCService().destroyChatRoom(chatRoomWrapper, destroyOptions[0], destroyOptions[1]);

        }
    };

    /**
     * Array of <tt>MUCCustomActionRunnable</tt> objects for the custom menu items. They will be executed when the item is pressed.
     */
    private MUCCustomActionRunnable[] actionsRunnable
            = {leaveRunnable, joinRunnable, autoJoinRunnable, autoJoinRunnable, destroyActionRunnable};

    /**
     * Array of <tt>MUCCustomActionRunnable</tt> objects for the custom menu items. They will be executed when the item is pressed.
     */
    private MUCCustomActionRunnable[] menuActionsRunnable = {new MUCCustomActionRunnable()
    {
        @Override
        public void run()
        {
            MUCActivator.getMUCService().openChatRoom(chatRoomWrapper);
        }
    }, joinRunnable, new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            String[] joinOptions;
            // joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(chatRoomWrapper.getParentProvider().getProtocolProvider(),
            // chatRoomWrapper.getChatRoomID(),
            // MUCActivator.getGlobalDisplayDetailsService().getDisplayName(chatRoomWrapper.getParentProvider().getProtocolProvider()));
            // if (joinOptions[0] == null)
            // return;
            // MUCActivator.getMUCService().joinChatRoom(chatRoomWrapper, joinOptions[0], null, joinOptions[1]);
        }
    }, leaveRunnable, new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
            if (chatRoom != null) {
                ChatRoomWrapper leavedRoomWrapped = MUCActivator.getMUCService().leaveChatRoom(chatRoomWrapper);
                if (leavedRoomWrapped != null)
                    MUCActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
            }
            MUCActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
            MUCActivator.getMUCService().removeChatRoom(chatRoomWrapper);
        }
    }, new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            // ChatRoomJoinOptionsDialog.getJoinOptions(true, chatRoomWrapper.getParentProvider().getProtocolProvider(),
            // chatRoomWrapper.getChatRoomID(),
            // MUCActivator.getGlobalDisplayDetailsService().getDisplayName(chatRoomWrapper.getParentProvider().getProtocolProvider()));
        }
    }, autoJoinRunnable, autoJoinRunnable, new MUCCustomActionRunnable()
    {

        @Override
        public void run()
        {
            MUCActivator.getUIService().showChatRoomAutoOpenConfigDialog(
                    chatRoomWrapper.getParentProvider().getProtocolProvider(), chatRoomWrapper.getChatRoomID());
        }
    }, destroyActionRunnable};

    /**
     * Array of <tt>EnableChecker</tt> objects for the custom menu items. They are used to check if the item is enabled or disabled.
     */
    private EnableChecker[] actionsEnabledCheckers = {null, new JoinEnableChecker(), new JoinEnableChecker(),
            new LeaveEnableChecker(), null, null, null, null, null, null};

    /**
     * The resource management service instance.
     */
    ResourceManagementService resources = MUCActivator.getResources();

    /**
     * Constructs the custom actions.
     */
    public MUCCustomContactActionService()
    {
        for (int i = 0; i < menuActionsLabels.length; i++) {
            MUCActionMenuItems item = new MUCActionMenuItems(menuActionsNames[i], menuActionsLabels[i],
                    menuActionsIcons[i], menuActionsRunnable[i]);
            mucActionMenuItems.add(item);
            if (actionsEnabledCheckers[i] != null)
                item.setEnabled(actionsEnabledCheckers[i]);
        }

        for (int i = 0; i < actionsLabels.length; i++) {
            MUCAction item = new MUCAction(actionsNames[i], actionsLabels[i], actionsIcons[i], actionsIconsRollover[i],
                    actionsIconsPressed[i], actionsRunnable[i]);
            mucActions.add(item);
        }

    }

    /**
     * Returns the template class that this service has been initialized with
     *
     * @return the template class
     */
    public Class<SourceContact> getContactSourceClass()
    {
        return SourceContact.class;
    }

    @Override
    public Iterator<ContactActionMenuItem<SourceContact>> getCustomContactActionsMenuItems()
    {
        return mucActionMenuItems.iterator();
    }

    @Override
    public Iterator<ContactAction<SourceContact>> getCustomContactActions()
    {
        return mucActions.iterator();
    }

    /**
     * Implements the MUC custom action.
     */
    private class MUCAction implements ContactAction<SourceContact>
    {
        /**
         * The text of the action.
         */
        private String text;

        /**
         * The icon of the action
         */
        private byte[] icon;

        /**
         * The icon that is shown when the action is pressed.
         */
        private byte[] iconPressed;

        /**
         * The runnable that is executed when the action is pressed.
         */
        private MUCCustomActionRunnable actionPerformed;

        /**
         * The icon that is shown when the mouse is over the action.
         */
        private byte[] iconRollover;

        /**
         * The name of the action.
         */
        private String name;

        /**
         * Constructs <tt>MUCAction</tt> instance.
         *
         * @param textKey the key used to retrieve the label for the action.
         * @param iconKey the key used to retrieve the icon for the action.
         * @param actionPerformed the action executed when the action is pressed.
         * @param iconRolloverKey the key used to retrieve the rollover icon for the action.
         * @param iconPressedKey the key used to retrieve the pressed icon for the action.
         */
        public MUCAction(String name, String textKey, String iconKey, String iconRolloverKey, String iconPressedKey, MUCCustomActionRunnable actionPerformed)
        {
            this.name = name;
            this.text = resources.getI18NString(textKey);
            this.icon = resources.getImageInBytes(iconKey);
            this.iconRollover = resources.getImageInBytes(iconRolloverKey);
            this.iconPressed = resources.getImageInBytes(iconPressedKey);
            this.actionPerformed = actionPerformed;
        }

        @Override
        public void actionPerformed(SourceContact actionSource, int x, int y)
                throws OperationFailedException
        {
            if (!(actionSource instanceof ChatRoomSourceContact))
                return;
            actionPerformed.setContact(actionSource);
            new Thread(actionPerformed).start();
        }

        @Override
        public byte[] getIcon()
        {
            return icon;
        }

        @Override
        public byte[] getRolloverIcon()
        {
            return iconRollover;
        }

        @Override
        public byte[] getPressedIcon()
        {
            return iconPressed;
        }

        @Override
        public String getToolTipText()
        {
            return text;
        }

        @Override
        public boolean isVisible(SourceContact actionSource)
        {
            if (actionSource instanceof ChatRoomSourceContact) {
                switch (name) {
                    case "leave":
                        return actionsEnabledCheckers[3].check(actionSource);
                    case "join":
                        return actionsEnabledCheckers[1].check(actionSource);
                    case "destroy_chatroom": {
                        ChatRoomSourceContact contact = (ChatRoomSourceContact) actionSource;
                        ChatRoomWrapper room = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
                        if (room == null || room.getChatRoom() == null)
                            return false;
                        return room.getChatRoom().getUserRole().equals(ChatRoomMemberRole.OWNER);
                    }
                    default: {
                        ChatRoomSourceContact contact = (ChatRoomSourceContact) actionSource;
                        ChatRoomWrapper room = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
                        if (room == null)
                            return false;

                        if (name.equals("autojoin"))
                            return room.isAutoJoin();
                        else if (name.equals("autojoin_pressed"))
                            return !room.isAutoJoin();
                        break;
                    }
                }
            }
            return false;
        }

    }

    /**
     * Implements the MUC custom menu items.
     */
    private class MUCActionMenuItems implements ContactActionMenuItem<SourceContact>
    {
        /**
         * The label for the menu item.
         */
        private String text;

        /**
         * The the icon for the menu item.
         */
        private byte[] image;

        /**
         * The action executed when the menu item is pressed.
         */
        private MUCCustomActionRunnable actionPerformed;

        /**
         * Object that is used to check if the item is enabled or disabled.
         */
        private EnableChecker enabled;

        /**
         * The name of the custom action menu item.
         */
        private String name;

        /**
         * The mnemonic for the action.
         */
        private char mnemonics;

        /**
         * Constructs <tt>MUCActionMenuItems</tt> instance.
         *
         * @param textKey the key used to retrieve the label for the menu item.
         * @param imageKey the key used to retrieve the icon for the menu item.
         * @param actionPerformed the action executed when the menu item is pressed.
         */
        public MUCActionMenuItems(String name, String textKey, String imageKey, MUCCustomActionRunnable actionPerformed)
        {
            this.text = resources.getI18NString(textKey);
            this.image = (imageKey == null) ? null : resources.getImageInBytes(imageKey);
            this.actionPerformed = actionPerformed;
            this.enabled = new EnableChecker();
            this.name = name;
            this.mnemonics = resources.getI18nMnemonic(textKey);
        }

        @Override
        public void actionPerformed(SourceContact actionSource)
                throws OperationFailedException
        {
            if (!(actionSource instanceof ChatRoomSourceContact))
                return;
            actionPerformed.setContact(actionSource);
            new Thread(actionPerformed).start();
        }

        @Override
        public byte[] getIcon()
        {
            return image;
        }

        @Override
        public String getText(SourceContact actionSource)
        {
            if (!(actionSource instanceof ChatRoomSourceContact))
                return "";

            if (!name.equals("open_automatically"))
                return text;

            String openAutomaticallyValue = MUCService.getChatRoomAutoOpenOption(((ChatRoomSourceContact) actionSource).getProvider(),
                    ((ChatRoomSourceContact) actionSource).getChatRoomID());
            if (openAutomaticallyValue == null)
                openAutomaticallyValue = MUCService.DEFAULT_AUTO_OPEN_BEHAVIOUR;
            String openAutomaticallyKey = MUCService.autoOpenConfigValuesTexts.get(openAutomaticallyValue);
            return "<html>" + text + "...<br><font size=\"2\"><center> ("
                    + resources.getI18NString(openAutomaticallyKey) + ")</center></font></html>";
        }

        @Override
        public boolean isVisible(SourceContact actionSource)
        {
            if (!(actionSource instanceof ChatRoomSourceContact))
                return false;

            ChatRoomSourceContact contact = (ChatRoomSourceContact) actionSource;
            ChatRoomWrapper room = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
            if (name.equals("autojoin") || name.equals("autojoin_pressed")) {
                if (room == null)
                    return true;

                if (name.equals("autojoin"))
                    return !room.isAutoJoin();

                if (name.equals("autojoin_pressed"))
                    return room.isAutoJoin();
            }
            else if (name.equals("remove")) {
                if (room == null || room.getChatRoom() == null)
                    return true;

                boolean ownerCannotRemoveRoom = MUCActivator.getConfigurationService()
                        .getBoolean(OWNER_CANT_REMOVE_CHATROOM_PROPERTY, false);

                // when joined role will be owner or member
                // when not joined and if we never has entered the room role
                // will be guest, if we joined and left the room the role
                // will be owner or member
                if (room.getChatRoom().getUserRole().equals(ChatRoomMemberRole.MEMBER)) {
                    return true;
                }
                else {
                    return !ownerCannotRemoveRoom;
                }
            }
            else if (name.equals("destroy_chatroom")) {
                if (room == null || room.getChatRoom() == null)
                    return false;
                return room.getChatRoom().getUserRole().equals(ChatRoomMemberRole.OWNER);
            }
            return true;
        }

        @Override
        public char getMnemonics()
        {
            return mnemonics;
        }

        @Override
        public boolean isEnabled(SourceContact actionSource)
        {
            return enabled.check(actionSource);
        }

        /**
         * Sets <tt>EnabledChecker</tt> instance that will be used to check if the item should be enabled or disabled.
         *
         * @param enabled the <tt>EnabledChecker</tt> instance.
         */
        public void setEnabled(EnableChecker enabled)
        {
            this.enabled = enabled;
        }

        @Override
        public boolean isCheckBox()
        {
            return false;
        }

        @Override
        public boolean isSelected(SourceContact contact)
        {
            ChatRoomWrapper chatRoomWrapper = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
            return (chatRoomWrapper != null) && chatRoomWrapper.isAutoJoin();
        }

    }

    /**
     * Checks if the menu item should be enabled or disabled. This is default implementation. Always returns
     * that the item should be enabled.
     */
    private static class EnableChecker
    {
        /**
         * Checks if the menu item should be enabled or disabled.
         *
         * @param contact the contact associated with the menu item.
         * @return always <tt>true</tt>
         */
        public boolean check(SourceContact contact)
        {
            return true;
        }
    }

    /**
     * Implements <tt>EnableChecker</tt> for the join menu items.
     */
    private static class JoinEnableChecker extends EnableChecker
    {
        /**
         * Checks if the menu item should be enabled or disabled.
         *
         * @param contact the contact associated with the menu item.
         * @return <tt>true</tt> if the item should be enabled and <tt>false</tt> if not.
         */
        public boolean check(SourceContact contact)
        {
            ChatRoomWrapper chatRoomWrapper = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
            ChatRoom chatRoom = null;
            if (chatRoomWrapper != null) {
                chatRoom = chatRoomWrapper.getChatRoom();
            }
            return (chatRoom == null) || !chatRoom.isJoined();
        }
    }

    /**
     * Implements <tt>EnableChecker</tt> for the leave menu item.
     */
    private static class LeaveEnableChecker extends JoinEnableChecker
    {
        /**
         * Checks if the menu item should be enabled or disabled.
         *
         * @param contact the contact associated with the menu item.
         * @return <tt>true</tt> if the item should be enabled and <tt>false</tt> if not.
         */
        public boolean check(SourceContact contact)
        {
            return !super.check(contact);
        }
    }

    /**
     * Implements base properties for the MUC menu items.These properties are used when the menu item is pressed.
     */
    private abstract class MUCCustomActionRunnable implements Runnable
    {
        /**
         * The contact associated with the menu item.
         */
        protected SourceContact contact;

        /**
         * The contact associated with the menu item.
         */
        protected ChatRoomWrapper chatRoomWrapper;

        /**
         * Sets the source contact.
         *
         * @param contact the contact to set
         */
        public void setContact(SourceContact contact)
        {
            this.contact = contact;
            chatRoomWrapper = MUCActivator.getMUCService().findChatRoomWrapperFromSourceContact(contact);
        }
    }
}
