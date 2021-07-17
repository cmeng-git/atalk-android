/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.actionbar;

import android.app.ActionBar;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.globaldisplaydetails.event.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.StatusUtil;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.menu.GlobalStatusMenu;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.gui.widgets.ActionMenuItem;
import org.atalk.service.osgi.OSGiFragment;

import java.util.Collection;

import androidx.fragment.app.FragmentActivity;

/**
 * Fragment when added to Activity will display global display details like avatar, display name
 * and status. External events will also trigger a change to the contents.
 * When status is clicked a popup menu is displayed allowing user to set global presence status.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ActionBarStatusFragment extends OSGiFragment
        implements EventListener<PresenceStatus>, GlobalDisplayDetailsListener
{
    /**
     * The online status.
     */
    private static final int ONLINE = 1;

    /**
     * The offline status.
     */
    private static final int OFFLINE = 2;

    /**
     * The free for chat status.
     */
    private static final int FFC = 3;

    /**
     * The away status.
     */
    private static final int AWAY = 4;

    /**
     * The away status.
     */
    private static final int EXTENDED_AWAY = 5;

    /**
     * The do not disturb status.
     */
    private static final int DND = 6;

    private static int ACTION_ID = DND + 1;

    /**
     * The global status menu.
     */
    private GlobalStatusMenu globalStatusMenu;
    private ActionBar mActionBar;
    private FragmentActivity fragmentActivity;

    private static GlobalDisplayDetailsService displayDetailsService;
    private static AndroidLoginRenderer loginRenderer;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        fragmentActivity = getActivity();

        // Create custom ActionBar View
        mActionBar = fragmentActivity.getActionBar();
        if (mActionBar != null) {
            mActionBar.setCustomView(R.layout.action_bar);
            mActionBar.setDisplayUseLogoEnabled(true);
        }
        loginRenderer = AndroidGUIActivator.getLoginRenderer();
        displayDetailsService = AndroidGUIActivator.getGlobalDisplayDetailsService();

        globalStatusMenu = createGlobalStatusMenu();
        TextView tv = fragmentActivity.findViewById(R.id.actionBarStatus);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

        final RelativeLayout actionBarView = fragmentActivity.findViewById(R.id.actionBarView);
        actionBarView.setOnClickListener(v -> {
            globalStatusMenu.show(actionBarView);
            globalStatusMenu.setAnimStyle(GlobalStatusMenu.ANIM_REFLECT);
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        loginRenderer.addGlobalStatusListener(this);
        onChangeEvent(loginRenderer.getGlobalStatus());

        displayDetailsService.addGlobalDisplayDetailsListener(this);
        setGlobalAvatar(displayDetailsService.getDisplayAvatar(null));
        setGlobalDisplayName(displayDetailsService.getDisplayName(null));
    }

    @Override
    public void onPause()
    {
        super.onPause();
        loginRenderer.removeGlobalStatusListener(this);
        displayDetailsService.removeGlobalDisplayDetailsListener(this);
    }

    /**
     * Creates the <tt>GlobalStatusMenu</tt>.
     *
     * @return the newly created <tt>GlobalStatusMenu</tt>
     */
    private GlobalStatusMenu createGlobalStatusMenu()
    {
        Resources res = getResources();
        ActionMenuItem ffcItem = new ActionMenuItem(FFC,
                res.getString(R.string.service_gui_FFC_STATUS),
                res.getDrawable(R.drawable.global_ffc));
        ActionMenuItem onlineItem = new ActionMenuItem(ONLINE,
                res.getString(R.string.service_gui_ONLINE),
                res.getDrawable(R.drawable.global_online));
        ActionMenuItem offlineItem = new ActionMenuItem(OFFLINE,
                res.getString(R.string.service_gui_OFFLINE),
                res.getDrawable(R.drawable.global_offline));
        ActionMenuItem awayItem = new ActionMenuItem(AWAY,
                res.getString(R.string.service_gui_AWAY_STATUS),
                res.getDrawable(R.drawable.global_away));
        ActionMenuItem extendedAwayItem = new ActionMenuItem(EXTENDED_AWAY,
                res.getString(R.string.service_gui_EXTENDED_AWAY_STATUS),
                res.getDrawable(R.drawable.global_extended_away));
        ActionMenuItem dndItem = new ActionMenuItem(DND,
                res.getString(R.string.service_gui_DND_STATUS),
                res.getDrawable(R.drawable.global_dnd));

        final GlobalStatusMenu globalStatusMenu = new GlobalStatusMenu(fragmentActivity);
        globalStatusMenu.addActionItem(ffcItem);
        globalStatusMenu.addActionItem(onlineItem);
        globalStatusMenu.addActionItem(offlineItem);
        globalStatusMenu.addActionItem(awayItem);
        globalStatusMenu.addActionItem(extendedAwayItem);
        globalStatusMenu.addActionItem(dndItem);

        // Add all registered PPS users to the presence status menu
        Collection<ProtocolProviderService> registeredProviders = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : registeredProviders) {
            AccountID accountId = pps.getAccountID();
            String userJid = accountId.getAccountJid();
            Drawable icon = res.getDrawable(R.drawable.jabber_status_online);

            ActionMenuItem actionItem = new ActionMenuItem(ACTION_ID++, userJid, icon);
            globalStatusMenu.addActionItem(actionItem, pps);
        }

        globalStatusMenu.setOnActionItemClickListener((source, pos, actionId) -> {
            if (actionId <= DND)
                publishGlobalStatus(actionId);
        });

        globalStatusMenu.setOnDismissListener(() -> {
            // TODO: Add a dismiss action.
        });
        return globalStatusMenu;
    }

    /**
     * Publishes global status on separate thread to prevent <tt>NetworkOnMainThreadException</tt>.
     *
     * @param newStatus new global status to set.
     */
    private void publishGlobalStatus(final int newStatus)
    {
        /*
         * Runs publish status on separate thread to prevent NetworkOnMainThreadException
         */
        new Thread(() -> {
            GlobalStatusService globalStatusService = AndroidGUIActivator.getGlobalStatusService();
            switch (newStatus) {
                case FFC:
                    globalStatusService.publishStatus(GlobalStatusEnum.FREE_FOR_CHAT);
                    break;
                case ONLINE:
                    globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
                    break;
                case OFFLINE:
                    globalStatusService.publishStatus(GlobalStatusEnum.OFFLINE);
                    break;
                case AWAY:
                    globalStatusService.publishStatus(GlobalStatusEnum.AWAY);
                    break;
                case EXTENDED_AWAY:
                    globalStatusService.publishStatus(GlobalStatusEnum.EXTENDED_AWAY);
                    break;
                case DND:
                    globalStatusService.publishStatus(GlobalStatusEnum.DO_NOT_DISTURB);
                    break;
            }
        }).start();
    }

    @Override
    public void onChangeEvent(final PresenceStatus presenceStatus)
    {
        if ((presenceStatus == null) || (fragmentActivity == null))
            return;

        runOnUiThread(() -> {
            String mStatus = presenceStatus.getStatusName();
            ActionBarUtil.setSubtitle(fragmentActivity, mStatus);
            ActionBarUtil.setStatus(fragmentActivity, StatusUtil.getStatusIcon(presenceStatus));

            MenuItem mOnOffLine = ((MainMenuActivity) fragmentActivity).getMenuItemOnOffLine();
            // Proceed only if mOnOffLine has been initialized
            if (mOnOffLine != null) {
                boolean isOffline = GlobalStatusEnum.OFFLINE_STATUS.equals(mStatus);
                int itemId = isOffline
                        ? R.string.service_gui_SIGN_IN
                        : R.string.service_gui_SIGN_OUT;
                mOnOffLine.setTitle(itemId);
            }
        });
    }

    /**
     * Indicates that the global avatar has been changed.
     */
    @Override
    public void globalDisplayAvatarChanged(final GlobalAvatarChangeEvent evt)
    {
        runOnUiThread(() -> setGlobalAvatar(evt.getNewAvatar()));
    }

    /**
     * Indicates that the global display name has been changed.
     */
    @Override
    public void globalDisplayNameChanged(final GlobalDisplayNameChangeEvent evt)
    {
        runOnUiThread(() -> setGlobalDisplayName(evt.getNewDisplayName()));
    }

    /**
     * Sets the global avatar in the action bar.
     *
     * @param avatar the byte array representing the avatar to set
     */
    private void setGlobalAvatar(final byte[] avatar)
    {
        if (avatar != null && avatar.length > 0) {
            ActionBarUtil.setAvatar(fragmentActivity, avatar);
        }
        else {
            mActionBar.setLogo(R.drawable.ic_icon);
        }
    }

    /**
     * Sets the global display name in the action bar as 'Me' if multiple accounts are involved, otherwise UserJid.
     *
     * @param name the display name to set
     */
    private void setGlobalDisplayName(final String name)
    {
        String displayName = name;
        Collection<ProtocolProviderService> pProviders = AccountUtils.getRegisteredProviders();

        if (StringUtils.isEmpty(displayName) && (pProviders.size() == 1)) {
            displayName = pProviders.iterator().next().getAccountID().getUserID();
        }
        if (pProviders.size() > 1)
            displayName = getString(R.string.service_gui_ACCOUNT_ME);
        ActionBarUtil.setTitle(fragmentActivity, displayName);
    }
}
