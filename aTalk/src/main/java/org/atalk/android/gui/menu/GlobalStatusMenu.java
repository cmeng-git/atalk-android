package org.atalk.android.gui.menu;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import android.widget.PopupWindow.OnDismissListener;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.StatusListAdapter;
import org.atalk.android.gui.widgets.ActionMenuItem;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.*;

import java.beans.PropertyChangeEvent;
import java.util.*;

import androidx.fragment.app.FragmentActivity;
import timber.log.Timber;

public class GlobalStatusMenu extends OSGiActivity
        implements OnDismissListener, ServiceListener, ProviderPresenceStatusListener
{
    private FragmentActivity mActivity;
    private LayoutInflater mInflater;
    private PopupWindow mWindow;
    private WindowManager mWindowManager;
    private View mRootView;
    private Drawable mBackground = null;

    private boolean mDidAction;
    private int mAnimStyle;
    private int mInsertPos;
    private int mChildPos;
    private ViewGroup mTrack;
    private ImageView mArrowUp;
    private ImageView mArrowDown;
    private ScrollView mScroller;
    private OnActionItemClickListener mItemClickListener;
    private OnDismissListener mDismissListener;
    private int rootWidth = 0;

    private GlobalStatusService globalStatus;

    private List<ActionMenuItem> actionItems = new ArrayList<>();
    private static Map<ProtocolProviderService, View> accountSpinner = new HashMap<>();

    private static final int ANIM_GROW_FROM_LEFT = 1;
    private static final int ANIM_GROW_FROM_RIGHT = 2;
    private static final int ANIM_GROW_FROM_CENTER = 3;
    public static final int ANIM_REFLECT = 4;
    private static final int ANIM_AUTO = 5;

    public GlobalStatusMenu(FragmentActivity activity)
    {
        mActivity = activity;
        mWindow = new PopupWindow(activity);
        mWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                mWindow.dismiss();
                return true;
            }
            return false;
        });

        mWindowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setRootViewId(R.layout.status_menu);
        mAnimStyle = ANIM_AUTO;
        mChildPos = 0;
        globalStatus = ServiceUtils.getService(AndroidGUIActivator.bundleContext, GlobalStatusService.class);

        // start listening for newly register or removed protocol providers
        // cmeng: bundleContext can be null from field ??? can have problem in status update when blocked
        // This happens when Activity is recreated by the system after OSGi service has been
        // killed (and the whole process)
        if (AndroidGUIActivator.bundleContext == null) {
            Timber.e("OSGi service probably not initialized");
            return;
        }
        AndroidGUIActivator.bundleContext.addServiceListener(this);
    }

    /**
     * Get action item at an index
     *
     * @param index Index of item (position from callback)
     * @return Action Item at the position
     */
    private ActionMenuItem getActionItem(int index)
    {
        return actionItems.get(index);
    }

    /**
     * On dismiss
     */
    public void onDismiss()
    {
        if (!mDidAction && mDismissListener != null) {
            mDismissListener.onDismiss();
        }
    }

    /**
     * Set root view.
     *
     * @param id Layout resource id
     */
    private void setRootViewId(int id)
    {
        mRootView = mInflater.inflate(id, null);
        mTrack = mRootView.findViewById(R.id.tracks);

        mArrowDown = mRootView.findViewById(R.id.arrow_down);
        mArrowUp = mRootView.findViewById(R.id.arrow_up);

        mScroller = mRootView.findViewById(R.id.scroller);

        // This was previously defined on show() method, moved here to prevent force close
        // that occurred when tapping fastly on a view to show quick action dialog.
        // Thank to zammbi (github.com/zammbi)
        mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setContentView(mRootView);
    }

    /**
     * Set listener for action item clicked.
     *
     * @param listener Listener
     */
    public void setOnActionItemClickListener(OnActionItemClickListener listener)
    {
        mItemClickListener = listener;
    }

    /**
     * Set animation style
     *
     * @param mAnimStyle animation style, default is set to ANIM_AUTO
     */
    public void setAnimStyle(int mAnimStyle)
    {
        this.mAnimStyle = mAnimStyle;
    }

    /**
     * On show
     */
    private void onShow()
    {
    }

    /**
     * On pre show
     */
    private void preShow()
    {
        if (mRootView == null)
            throw new IllegalStateException("setContentView was not called with a view to display.");

        onShow();
        if (mBackground == null)
            mWindow.setBackgroundDrawable(new BitmapDrawable(null, (Bitmap) null));
        else
            mWindow.setBackgroundDrawable(mBackground);

        mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow.setTouchable(true);
        mWindow.setFocusable(true);
        mWindow.setOutsideTouchable(true);
        mWindow.setContentView(mRootView);
    }

    /**
     * Set background drawable.
     *
     * @param background Background drawable
     */
    public void setBackgroundDrawable(Drawable background)
    {
        mBackground = background;
    }

    /**
     * Set content view.
     *
     * @param root Root view
     */
    public void setContentView(View root)
    {
        mRootView = root;
        mWindow.setContentView(root);
    }

    /**
     * Set content view.
     *
     * @param layoutResID Resource id
     */
    public void setContentView(int layoutResID)
    {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setContentView(inflater.inflate(layoutResID, null));
    }

    /**
     * Set listener for window dismissed. This listener will only be fired if the quick action
     * dialog is dismissed by clicking outside the dialog or clicking on sticky item.
     */
    public void setOnDismissListener(GlobalStatusMenu.OnDismissListener listener)
    {
        mDismissListener = listener;
    }

    /**
     * Dismiss the popup window.
     */
    public void dismiss()
    {
        mWindow.dismiss();
    }

    /**
     * Listener for item click
     */
    public interface OnActionItemClickListener
    {
        void onItemClick(GlobalStatusMenu source, int pos, int actionId);
    }

    /**
     * Listener for window dismiss
     */
    public interface OnDismissListener
    {
        void onDismiss();
    }

    /**
     * Add action item
     *
     * @param action {@link ActionMenuItem}
     */
    public void addActionItem(ActionMenuItem action)
    {
        actionItems.add(action);
        String title = action.getTitle();
        Drawable icon = action.getIcon();
        View container = mInflater.inflate(R.layout.status_menu_item, null);

        ImageView img = container.findViewById(R.id.iv_icon);
        final TextView text = container.findViewById(R.id.tv_title);

        if (icon != null)
            img.setImageDrawable(icon);
        else
            img.setVisibility(View.GONE);

        if (title != null)
            text.setText(title);
        else
            text.setVisibility(View.GONE);

        final int pos = mChildPos;
        final int actionId = action.getActionId();

        container.setOnClickListener(v -> {
            if (mItemClickListener != null)
                mItemClickListener.onItemClick(GlobalStatusMenu.this, pos, actionId);

            if (!getActionItem(pos).isSticky()) {
                mDidAction = true;
                dismiss();
            }
        });

        container.setFocusable(true);
        container.setClickable(true);
        mTrack.addView(container, mInsertPos);
        mChildPos++;
        mInsertPos++;
    }

    /**
     * Add action item for protocolProvider
     *
     * @param action {@link ActionMenuItem}
     */
    public void addActionItem(ActionMenuItem action, final ProtocolProviderService pps)
    {
        actionItems.add(action);
        String title = action.getTitle();
        Drawable icon = action.getIcon();
        View container = mInflater.inflate(R.layout.status_menu_item_spinner, null);

        ImageView img = container.findViewById(R.id.iv_icon);
        TextView text = container.findViewById(R.id.tv_title);
        accountSpinner.put(pps, container);

        if (icon != null)
            img.setImageDrawable(icon);
        else
            img.setVisibility(View.GONE);

        if (title != null)
            text.setText(title);
        else
            text.setVisibility(View.GONE);

        // WindowManager$BadTokenException
        final OperationSetPresence accountPresence = pps.getOperationSet(OperationSetPresence.class);
        List<PresenceStatus> presenceStatuses = accountPresence.getSupportedStatusSet();

        // Create spinner with presence status list for the given pps
        // Note: xml layout has forced to use Spinner.MODE_DIALOG, other Note-5 crashes when use MODE_DROPDOWN
        final Spinner statusSpinner = container.findViewById(R.id.presenceSpinner);
        StatusListAdapter statusAdapter
                = new StatusListAdapter(mActivity, R.layout.account_presence_status_row, presenceStatuses);
        statusAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        // Default state to offline
        PresenceStatus offline = accountPresence.getPresenceStatus(JabberStatusEnum.OFFLINE);
        statusSpinner.setSelection(presenceStatuses.indexOf(offline), false);

        // Setup adapter listener for onItemSelected
        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                final PresenceStatus selectedStatus = (PresenceStatus) statusSpinner.getSelectedItem();
                final String statusMessage = selectedStatus.getStatusName();

                new Thread(() -> {
                    // Try to publish selected status
                    try {
                        // cmeng: set state to false to force it to execute offline->online
                        if (globalStatus != null) {
                            globalStatus.publishStatus(pps, selectedStatus, false);
                        }
                        if (pps.isRegistered()) {
                            accountPresence.publishPresenceStatus(selectedStatus, statusMessage);
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Account presence publish error.");
                    }
                }).start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                // Should not happen in single selection mode
            }
        });

        container.setFocusable(true);
        container.setClickable(true);

        OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
        presenceOpSet.addProviderPresenceStatusListener(this);

        mTrack.addView(container, mInsertPos);
        mChildPos++;
        mInsertPos++;
    }

    /**
     * Show quick action popup. Popup is automatically positioned, on top or bottom of anchor view.
     */
    public void show(View anchor)
    {
        preShow();
        int xPos, yPos, arrowPos;
        mDidAction = false;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect anchorRect = new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1] + anchor.getHeight());

        // mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mRootView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        int rootHeight = mRootView.getMeasuredHeight();
        if (rootWidth == 0) {
            rootWidth = mRootView.getMeasuredWidth();
        }
        Point screenSize = new Point();
        mWindowManager.getDefaultDisplay().getSize(screenSize);

        // automatically get X coord of popup (top left)
        if ((anchorRect.left + rootWidth) > screenSize.x) {
            xPos = anchorRect.left - (rootWidth - anchor.getWidth());
            xPos = (xPos < 0) ? 0 : xPos;
            arrowPos = anchorRect.centerX() - xPos;

        }
        else {
            if (anchor.getWidth() > rootWidth)
                xPos = anchorRect.centerX() - (rootWidth / 2);
            else
                xPos = anchorRect.left;
            arrowPos = anchorRect.centerX() - xPos;
        }

        int dyTop = anchorRect.top;
        int dyBottom = screenSize.y - anchorRect.bottom;
        boolean onTop = dyTop > dyBottom;
        if (onTop) {
            if (rootHeight > dyTop) {
                yPos = 15;
                LayoutParams l = mScroller.getLayoutParams();
                l.height = dyTop - anchor.getHeight();
            }
            else {
                yPos = anchorRect.top - rootHeight;
            }
        }
        else {
            yPos = anchorRect.bottom;
            if (rootHeight > dyBottom) {
                LayoutParams l = mScroller.getLayoutParams();
                l.height = dyBottom;
            }
        }
        showArrow(((onTop) ? R.id.arrow_down : R.id.arrow_up), arrowPos);
        setAnimationStyle(screenSize.x, anchorRect.centerX(), onTop);
        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
    }

    /**
     * Set animation style
     *
     * @param screenWidth screen width
     * @param requestedX distance from left edge
     * @param onTop flag to indicate where the popup should be displayed. Set TRUE if displayed on top of anchor
     * view and vice versa
     */
    private void setAnimationStyle(int screenWidth, int requestedX, boolean onTop)
    {
        int arrowPos = requestedX - mArrowUp.getMeasuredWidth() / 2;

        switch (mAnimStyle) {
            case ANIM_GROW_FROM_LEFT:
                mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
                break;
            case ANIM_GROW_FROM_RIGHT:
                mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
                break;
            case ANIM_GROW_FROM_CENTER:
                mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
                break;
            case ANIM_REFLECT:
                mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Reflect : R.style.Animations_PopDownMenu_Reflect);
                break;
            case ANIM_AUTO:
                if (arrowPos <= screenWidth / 4) {
                    mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
                }
                else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4)) {
                    mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
                }
                else {
                    mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
                }
                break;
        }
    }

    /**
     * Show arrow
     *
     * @param whichArrow arrow type resource id
     * @param requestedX distance from left screen
     */
    private void showArrow(int whichArrow, int requestedX)
    {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

        final int arrowWidth = mArrowUp.getMeasuredWidth();
        showArrow.setVisibility(View.VISIBLE);
        ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) showArrow.getLayoutParams();
        param.leftMargin = requestedX - arrowWidth / 2;
        hideArrow.setVisibility(View.INVISIBLE);
    }

    @Override
    public void providerStatusChanged(ProviderPresenceStatusChangeEvent evt)
    {
        ProtocolProviderService pps = evt.getProvider();
        // Timber.w("### PPS presence status change: " + pps + " => " + evt.getNewStatus());

        // do not proceed if spinnerContainer is null
        View spinnerContainer = accountSpinner.get(pps);
        if (spinnerContainer == null) {
            Timber.e("No presence status spinner setup for: %s", pps);
            return;
        }

        final PresenceStatus presenceStatus = evt.getNewStatus();
        final Spinner statusSpinner = spinnerContainer.findViewById(R.id.presenceSpinner);
        final StatusListAdapter statusAdapter = (StatusListAdapter) statusSpinner.getAdapter();

        mActivity.runOnUiThread(() -> statusSpinner.setSelection(statusAdapter.getPosition(presenceStatus)));
    }

    @Override
    public void providerStatusMessageChanged(PropertyChangeEvent evt)
    {
        // Timber.w("### PPS Status message change: " + evt.getSource() + " => " + evt.getNewValue());
    }

    /**
     * Implements the <tt>ServiceListener</tt> method. Verifies whether the received event concerning
     * a <tt>ProtocolProviderService</tt> and take the necessary action.
     *
     * @param event The <tt>ServiceEvent</tt> object.
     */
    public void serviceChanged(ServiceEvent event)
    {
        // if the event is caused by a bundle being stopped, we don't want to know
        ServiceReference serviceRef = event.getServiceReference();
        if (serviceRef.getBundle().getState() == Bundle.STOPPING)
            return;

        // bundleContext == null on exit
        BundleContext bundleContext = AndroidGUIActivator.bundleContext;
        if (bundleContext == null)
            return;
        Object service = bundleContext.getService(serviceRef);

        // we don't care if the source service is not a protocol provider
        if (service instanceof ProtocolProviderService) {
            // Timber.w("## ProtocolServiceProvider Add or Remove: " + event.getType());
            ProtocolProviderService pps = (ProtocolProviderService) service;

            switch (event.getType()) {
                case ServiceEvent.REGISTERED:
                    addMenuItemPPS(pps);
                    break;
                case ServiceEvent.UNREGISTERING:
                    removeMenuItemPPS(pps);
                    break;
            }
        }
    }

    /**
     * When the PPS is being registered i.e. enabled on account list
     * 1. Create a new entry in the status menu
     *
     * @param pps new provider to be added to the status menu
     */
    private void addMenuItemPPS(ProtocolProviderService pps)
    {
        if ((!accountSpinner.containsKey(pps))) {
            // Timber.w("## ProtocolServiceProvider Added: " + pps);
            AccountID accountId = pps.getAccountID();
            String userJid = accountId.getAccountJid();
            Drawable icon = aTalkApp.getAppResources().getDrawable(R.drawable.jabber_status_online);
            ActionMenuItem actionItem = new ActionMenuItem(mChildPos++, userJid, icon);
            addActionItem(actionItem, pps);
        }
    }

    /**
     * When a pps is unregister i.e. disabled on account list:
     * 1. Remove ProviderPresenceStatusListener for this pps
     * 2. Remove the spinner view from the status menu
     * 3. Remove entry in the accountSpinner
     * 4. Readjust all the required pointer
     *
     * @param pps provider to be removed
     */
    private void removeMenuItemPPS(ProtocolProviderService pps)
    {
        if (accountSpinner.containsKey(pps)) {
            OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
            presenceOpSet.removeProviderPresenceStatusListener(this);

            View spinnerContainer = accountSpinner.get(pps);
            ((ViewGroup) spinnerContainer.getParent()).removeView(spinnerContainer);

            accountSpinner.remove(pps);
            mChildPos--;
            mInsertPos--;
        }
    }
}
