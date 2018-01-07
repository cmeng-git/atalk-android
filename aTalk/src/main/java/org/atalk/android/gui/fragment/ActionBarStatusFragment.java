/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.fragment;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService;
import net.java.sip.communicator.service.globaldisplaydetails.event.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.globalstatus.*;
import net.java.sip.communicator.util.StatusUtil;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.menu.*;
import org.atalk.android.gui.util.ActionBarUtil;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.gui.widgets.ActionMenuItem;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.StringUtils;

import java.util.Collection;

/**
 * Fragment when added to Activity will display global display details like avatar, display name
 * and status. External events will also trigger a change to the contents.
 * When status is clicked a popup menu is displayed allowing user to set global
 * presence status.
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
	 * The do not disturb status.
	 */
	private static final int DND = 5;

	/**
	 * The global status menu.
	 */
	private GlobalStatusMenu globalStatusMenu;
	private ActionBar actionBar;

	private static GlobalDisplayDetailsService displayDetailsService;
	private static AndroidLoginRenderer loginRenderer;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Create custom ActionBar View
		actionBar = getActivity().getActionBar();
		if (actionBar != null) {
			actionBar.setCustomView(R.layout.action_bar);
			actionBar.setDisplayUseLogoEnabled(true);
		}
		this.globalStatusMenu = createGlobalStatusMenu();
		TextView tv = (TextView) getActivity().findViewById(R.id.actionBarStatusText);
		tv.setSelected(true);

		loginRenderer = AndroidGUIActivator.getLoginRenderer();
		displayDetailsService = AndroidGUIActivator.getGlobalDisplayDetailsService();

		final RelativeLayout actionBarView = (RelativeLayout) getActivity().findViewById(R.id.actionBarView);
		actionBarView.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				globalStatusMenu.show(actionBarView);
				globalStatusMenu.setAnimStyle(GlobalStatusMenu.ANIM_REFLECT);
			}
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
		ActionMenuItem ffcItem = new ActionMenuItem(FFC,
				getResources().getString(R.string.service_gui_FFC_STATUS),
				getResources().getDrawable(R.drawable.global_ffc));
		ActionMenuItem onlineItem = new ActionMenuItem(ONLINE,
				getResources().getString(R.string.service_gui_ONLINE),
				getResources().getDrawable(R.drawable.global_online));
		ActionMenuItem offlineItem = new ActionMenuItem(OFFLINE,
				getResources().getString(R.string.service_gui_OFFLINE),
				getResources().getDrawable(R.drawable.global_offline));
		ActionMenuItem awayItem = new ActionMenuItem(AWAY,
				getResources().getString(R.string.service_gui_AWAY_STATUS),
				getResources().getDrawable(R.drawable.global_away));
		ActionMenuItem dndItem = new ActionMenuItem(DND,
				getResources().getString(R.string.service_gui_DND_STATUS),
				getResources().getDrawable(R.drawable.global_dnd));

		final GlobalStatusMenu globalStatusMenu = new GlobalStatusMenu(getActivity());

		globalStatusMenu.addActionItem(ffcItem);
		globalStatusMenu.addActionItem(onlineItem);
		globalStatusMenu.addActionItem(offlineItem);
		globalStatusMenu.addActionItem(awayItem);
		globalStatusMenu.addActionItem(dndItem);

		globalStatusMenu.setOnActionItemClickListener(
				new GlobalStatusMenu.OnActionItemClickListener()
				{
					@Override
					public void onItemClick(GlobalStatusMenu source, int pos, int actionId)
					{
						publishGlobalStatus(actionId);
					}
				});

		globalStatusMenu.setOnDismissListener(new GlobalStatusMenu.OnDismissListener()
		{
			public void onDismiss()
			{
				// TODO: Add a dismiss action.
			}
		});
		return globalStatusMenu;
	}

	/**
	 * Publishes global status on separate thread to prevent <tt>NetworkOnMainThreadException</tt>.
	 *
	 * @param newStatus
	 * 		new global status to set.
	 */
	private void publishGlobalStatus(final int newStatus)
	{
		/**
		 * Runs publish status on separate thread to prevent NetworkOnMainThreadException
		 */
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				GlobalStatusService globalStatusService
						= AndroidGUIActivator.getGlobalStatusService();

				switch (newStatus) {
					case ONLINE:
						globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
						break;
					case OFFLINE:
						globalStatusService.publishStatus(GlobalStatusEnum.OFFLINE);
						break;
					case FFC:
						globalStatusService.publishStatus(GlobalStatusEnum.FREE_FOR_CHAT);
						break;
					case AWAY:
						globalStatusService.publishStatus(GlobalStatusEnum.AWAY);
						break;
					case DND:
						globalStatusService.publishStatus(GlobalStatusEnum.DO_NOT_DISTURB);
						break;
				}
			}
		}).start();
	}

	@Override
	public void onChangeEvent(final PresenceStatus presenceStatus)
	{
		final Activity activity = getActivity();
		if ((presenceStatus == null) || (activity == null))
			return;

		activity.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				String mStatus = presenceStatus.getStatusName();
				ActionBarUtil.setSubtitle(activity, mStatus);
				ActionBarUtil.setStatus(activity, StatusUtil.getStatusIcon(presenceStatus));
				// ActionBarUtil.setStatus(activity, StatusUtil.getContactStatusIcon(presenceStatus));

				MenuItem mOnOffLine = ((MainMenuActivity) activity).getMenuItemOnOffLine();
				// Proceed only if mOnOffLine has been initialized
				if (mOnOffLine != null) {
					boolean isOffline = GlobalStatusEnum.OFFLINE_STATUS.equals(mStatus);
					int itemId = isOffline
							? R.string.service_gui_SIGN_IN
							: R.string.service_gui_SIGN_OUT;
					mOnOffLine.setTitle(getString(itemId));
				}
			}
		});
	}

	/**
	 * Indicates that the global avatar has been changed.
	 */
	@Override
	public void globalDisplayAvatarChanged(final GlobalAvatarChangeEvent evt)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				setGlobalAvatar(evt.getNewAvatar());
			}
		});

	}

	/**
	 * Indicates that the global display name has been changed.
	 */
	@Override
	public void globalDisplayNameChanged(final GlobalDisplayNameChangeEvent evt)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				setGlobalDisplayName(evt.getNewDisplayName());
			}
		});
	}

	/**
	 * Sets the global avatar in the action bar.
	 *
	 * @param avatar
	 * 		the byte array representing the avatar to set
	 */
	private void setGlobalAvatar(final byte[] avatar)
	{
		if (avatar != null && avatar.length > 0) {
			ActionBarUtil.setAvatar(getActivity(), avatar);
		}
		else {
			actionBar.setLogo(R.drawable.ic_icon);
		}
	}

	/**
	 * Sets the global display name in the action bar.
	 *
	 * @param name
	 * 		the display name to set
	 */
	private void setGlobalDisplayName(final String name)
	{
		String displayName = name;

		if (StringUtils.isNullOrEmpty(displayName)) {
			Collection<ProtocolProviderService> pProviders = AccountUtils.getRegisteredProviders();

			if (pProviders.size() == 1)
				displayName = pProviders.iterator().next().getAccountID().getUserID();
			else if (pProviders.size() > 1)
				displayName = getString(R.string.service_gui_ACCOUNT_ME);
		}
		ActionBarUtil.setTitle(getActivity(), displayName);
	}
}
