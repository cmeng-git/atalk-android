/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.dialogs;

import android.content.*;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

import java.io.Serializable;
import java.util.*;

/**
 * <code>DialogActivity</code> can be used to display alerts without having parent <code>Activity</code>
 * (from services). <br/> Simple alerts can be displayed using static method <code>showDialog(...)
 * </code>.<br/> Optionally confirm button's text and the listener can be supplied. It allows to
 * react to users actions. For this purpose use method <code>showConfirmDialog(...)</code>.<br/>
 * For more sophisticated use cases content fragment class with it's arguments can be specified
 * in method <code>showCustomDialog()</code>. When they're present the alert message will be replaced
 * by the {@link Fragment}'s <code>View</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class DialogActivity extends OSGiActivity
{
    /**
     * Dialog title extra.
     */
    public static final String EXTRA_TITLE = "title";

    /**
     * Dialog message extra.
     */
    public static final String EXTRA_MESSAGE = "message";

    /**
     * Optional confirm button label extra.
     */
    public static final String EXTRA_CONFIRM_TXT = "confirm_txt";

    /**
     * Dialog id extra used to listen for close dialog broadcast intents.
     */
    private static final String EXTRA_DIALOG_ID = "dialog_id";

    /**
     * Optional listener ID extra(can be supplied only using method static <code>showConfirmDialog</code>.
     */
    public static final String EXTRA_LISTENER_ID = "listener_id";

    /**
     * Optional content fragment's class name that will be used instead of text message.
     */
    public static final String EXTRA_CONTENT_FRAGMENT = "fragment_class";

    /**
     * Optional content fragment's argument <code>Bundle</code>.
     */
    public static final String EXTRA_CONTENT_ARGS = "fragment_args";

    /**
     * Prevents from closing this activity on outside touch events and blocks the back key if set to <code>true</code>.
     */
    public static final String EXTRA_CANCELABLE = "cancelable";

    /**
     * Hide all buttons.
     */
    public static final String EXTRA_REMOVE_BUTTONS = "remove_buttons";

    /**
     * Static map holds listeners for currently displayed dialogs.
     */
    private static final Map<Long, DialogListener> listenersMap = new HashMap<>();

    /**
     * Static list holds existing dialog instances (since onCreate() until onDestroy()). Only
     * dialogs with valid id are listed here.
     */
    private final static List<Long> displayedDialogs = new ArrayList<>();

    /**
     * The dialog listener.
     */
    private DialogListener mListener;

    /**
     * Dialog listener's id used to identify listener in {@link #listenersMap}.
     * The value get be retrieved for use in caller reference when user click the button.
     */
    private long mListenerId;

    /**
     * Flag remembers if the dialog was confirmed.
     */
    private boolean confirmed;

    private static final LocalBroadcastManager localBroadcastManager
            = LocalBroadcastManager.getInstance(aTalkApp.getGlobalContext());

    /**
     * <code>BroadcastReceiver</code> that listens for close dialog action.
     */
    private CommandDialogListener commandIntentListener;

    /**
     * Name of the action which can be used to close dialog with given id supplied in
     * {@link #EXTRA_DIALOG_ID}.
     */
    public static final String ACTION_CLOSE_DIALOG = "org.atalk.gui.close_dialog";

    /**
     * Name of the action which can be used to focus dialog with given id supplied in
     * {@link #EXTRA_DIALOG_ID}.
     */
    public static final String ACTION_FOCUS_DIALOG = "org.atalk.gui.focus_dialog";

    private boolean cancelable;
    private View mContent;

    /**
     * {@inheritDoc}
     * cmeng: the onCreate get retrigger by android when developer option on keep activity is enabled
     * then the new dialog is not disposed.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.alert_dialog);
        mContent = findViewById(android.R.id.content);
        setTitle(intent.getStringExtra(EXTRA_TITLE));

        // Message or custom content
        String contentFragment = intent.getStringExtra(EXTRA_CONTENT_FRAGMENT);
        if (contentFragment != null) {
            // Hide alert text
            ViewUtil.ensureVisible(mContent, R.id.alertText, false);

            // Display content fragment
            if (savedInstanceState == null) {
                try {
                    // Instantiate content fragment
                    Class<?> contentClass = Class.forName(contentFragment);
                    Fragment fragment = (Fragment) contentClass.newInstance();

                    // Set fragment arguments
                    fragment.setArguments(intent.getBundleExtra(EXTRA_CONTENT_ARGS));

                    // Insert the fragment
                    getSupportFragmentManager().beginTransaction().replace(R.id.alertContent, fragment).commit();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else {
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.alertText,
                    intent.getStringExtra(EXTRA_MESSAGE));
        }

        // Confirm button text
        String confirmTxt = intent.getStringExtra(EXTRA_CONFIRM_TXT);
        if (confirmTxt != null) {
            ViewUtil.setTextViewValue(mContent, R.id.okButton, confirmTxt);
        }

        // Show cancel button if confirm label is not null
        ViewUtil.ensureVisible(mContent, R.id.cancelButton, confirmTxt != null);

        // Sets the listener
        mListenerId = intent.getLongExtra(EXTRA_LISTENER_ID, -1);
        if (mListenerId != -1) {
            mListener = listenersMap.get(mListenerId);
        }

        this.cancelable = intent.getBooleanExtra(EXTRA_CANCELABLE, false);
        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(cancelable);

        // Removes the buttons
        if (intent.getBooleanExtra(EXTRA_REMOVE_BUTTONS, false)) {
            ViewUtil.ensureVisible(mContent, R.id.okButton, false);
            ViewUtil.ensureVisible(mContent, R.id.cancelButton, false);
        }

        // Close this dialog on ACTION_CLOSE_DIALOG broadcast
        long dialogId = intent.getLongExtra(EXTRA_DIALOG_ID, -1);
        if (dialogId != -1) {
            commandIntentListener = new CommandDialogListener(dialogId);
            IntentFilter intentFilter = new IntentFilter(ACTION_CLOSE_DIALOG);
            intentFilter.addAction(ACTION_FOCUS_DIALOG);
            localBroadcastManager.registerReceiver(commandIntentListener, intentFilter);

            // Adds this dialog to active dialogs list and notifies all waiting threads.
            synchronized (displayedDialogs) {
                displayedDialogs.add(dialogId);
                displayedDialogs.notifyAll();
            }
        }
    }

    /**
     * Returns the content fragment. It can contain alert message or be the custom fragment class instance.
     *
     * @return dialog content fragment.
     */
    public Fragment getContentFragment()
    {
        return getSupportFragmentManager().findFragmentById(R.id.alertContent);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (!cancelable &&
                keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Fired when the confirm button is clicked.
     *
     * @param v the confirm button view.
     */
    @SuppressWarnings("unused")
    public void onOkClicked(View v)
    {
        if (mListener != null) {
            if (!mListener.onConfirmClicked(this)) {
                return;
            }
        }
        confirmed = true;
        finish();
    }

    /**
     * Fired when cancel button is clicked.
     *
     * @param v the cancel button view.
     */
    @SuppressWarnings("unused")
    public void onCancelClicked(View v)
    {
        finish();
    }

    /**
     * Removes listener from the map.
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        /*
         * cmeng: cannot do here as this is triggered when dialog is obscured by other activity
         * when developer do keep activity is enable
        if (commandIntentListener != null) {
            localBroadcastManager.unregisterReceiver(commandIntentListener);

           // Notify about dialogs list change
           synchronized (displayedDialogs) {
                displayedDialogs.remove(listenerID);
                displayedDialogs.notifyAll();
            }
        }
        */
        // Notify that dialog was cancelled if confirmed == false
        if (mListener != null && !confirmed) {
            mListener.onDialogCancelled(this);
        }

        // Removes the listener from map
        if (mListenerId != -1) {
            listenersMap.remove(mListenerId);
        }
    }

    /**
     * Get the listener Id for the dialog
     * @return the current mListenerId
     */
    public long getListenerID() {
        return mListenerId;
    }

    /**
     * Broadcast Receiver to act on command received in #intent.getExtra()
     */
    class CommandDialogListener extends BroadcastReceiver
    {
        private final long mDialogId;

        CommandDialogListener(long dialogId)
        {
            mDialogId = dialogId;
        }

        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getLongExtra(EXTRA_DIALOG_ID, -1) == mDialogId) {
                if (ACTION_CLOSE_DIALOG.equals(intent.getAction())) {
                    // Unregistered listener and finish this activity with dialogId
                    if (commandIntentListener != null) {
                        localBroadcastManager.unregisterReceiver(commandIntentListener);

                        // Notify about dialogs list change
                        synchronized (displayedDialogs) {
                            displayedDialogs.remove(mListenerId);
                            displayedDialogs.notifyAll();
                        }
                        commandIntentListener = null;
                    }
                    finish();
                }
                else if (ACTION_FOCUS_DIALOG.equals(intent.getAction())) {
                    mContent.bringToFront();
                }
            }
        }
    }

    /**
     * Fires {@link #ACTION_CLOSE_DIALOG} broadcast action in order to close the dialog identified
     * by given <code>dialogId</code>.
     *
     * @param dialogId dialog identifier returned when the dialog was created.
     */
    public static void closeDialog(long dialogId)
    {
        Intent intent = new Intent(ACTION_CLOSE_DIALOG);
        intent.putExtra(EXTRA_DIALOG_ID, dialogId);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Fires {@link #ACTION_FOCUS_DIALOG} broadcast action in order to focus the dialog identified
     * by given <code>dialogId</code>.
     *
     * @param dialogId dialog identifier returned when the dialog was created.
     */
    public static void focusDialog(long dialogId)
    {
        Intent intent = new Intent(ACTION_FOCUS_DIALOG);
        intent.putExtra(EXTRA_DIALOG_ID, dialogId);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Creates an <code>Intent</code> that will display a dialog with given <code>title</code> and content <code>message</code>.
     *
     * @param ctx Android context.
     * @param title dialog title that will be used
     * @param message dialog message that wil be used.
     * @return an <code>Intent</code> that will display a dialog.
     */
    public static Intent getDialogIntent(Context ctx, String title, String message)
    {
        Intent alert = new Intent(ctx, DialogActivity.class);
        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_MESSAGE, message);
        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return alert;
    }

    /**
     * Show simple alert that will be disposed when user presses OK button.
     *
     * @param ctx Android context.
     * @param title the dialog title that will be used.
     * @param message the dialog message that will be used.
     */
    public static void showDialog(Context ctx, String title, String message)
    {
        Intent alert = getDialogIntent(ctx, title, message);
        ctx.startActivity(alert);
    }

    /**
     * Shows a dialog for the given context and a title given by <code>titleId</code> and
     * message given by <code>messageId</code> with its optional arg.
     *
     * @param ctx the android <code>Context</code>
     * @param titleId the title identifier in the resources
     * @param messageId the message identifier in the resources
     * @param arg optional arg for the message expansion.
     */
    public static void showDialog(Context ctx, int titleId, int messageId, Object... arg)
    {
        Intent alert = getDialogIntent(ctx, ctx.getString(titleId), ctx.getString(messageId, arg));
        ctx.startActivity(alert);
    }

    /**
     * Shows confirm dialog allowing to handle confirm action using supplied <code>listener</code>.
     *
     * @param context Android context.
     * @param title dialog title that will be used
     * @param message dialog message that wil be used.
     * @param confirmTxt confirm button label.
     * @param listener the confirm action listener.
     */
    public static long showConfirmDialog(Context context, String title, String message,
            String confirmTxt, DialogListener listener)
    {
        Intent alert = new Intent(context, DialogActivity.class);

        long listenerId = System.currentTimeMillis();
        if (listener != null) {
            listenersMap.put(listenerId, listener);
            alert.putExtra(EXTRA_LISTENER_ID, listenerId);
        }

        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_MESSAGE, message);
        alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt);

        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alert);
        return listenerId;
    }

    /**
     * Shows confirm dialog allowing to handle confirm action using supplied <code>listener</code>.
     *
     * @param context the android context.
     * @param title dialog title Res that will be used
     * @param message the message identifier in the resources
     * @param confirmTxt confirm button label Res.
     * @param listener the <code>DialogInterface.DialogListener</code> to attach to the confirm button
     * @param arg optional arg for the message resource arg.
     */
    public static void showConfirmDialog(Context context, int title, int message,
            int confirmTxt, DialogListener listener, Object... arg)
    {
        Resources res = aTalkApp.getAppResources();
        showConfirmDialog(context, res.getString(title), res.getString(message, arg),
                res.getString(confirmTxt), listener);
    }

    /**
     * Show custom dialog. Alert text will be replaced by the {@link Fragment} created from
     * <code>fragmentClass</code> name. Optional <code>fragmentArguments</code> <code>Bundle</code> will be
     * supplied to created instance.
     *
     * @param context Android context.
     * @param title the title that will be used.
     * @param fragmentClass <code>Fragment</code>'s class name that will be used instead of text message.
     * @param fragmentArguments optional <code>Fragment</code> arguments <code>Bundle</code>.
     * @param confirmTxt the confirm button's label.
     * @param listener listener that will be notified on user actions.
     * @param extraArguments additional arguments with keys defined in {@link DialogActivity}.
     */
    public static long showCustomDialog(Context context, String title, String fragmentClass,
            Bundle fragmentArguments, String confirmTxt,
            DialogListener listener, Map<String, Serializable> extraArguments)
    {
        Intent alert = new Intent(context, DialogActivity.class);
        long dialogId = System.currentTimeMillis();

        alert.putExtra(EXTRA_DIALOG_ID, dialogId);
        if (listener != null) {
            listenersMap.put(dialogId, listener);
            alert.putExtra(EXTRA_LISTENER_ID, dialogId);
        }

        alert.putExtra(EXTRA_TITLE, title);
        alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt);

        alert.putExtra(EXTRA_CONTENT_FRAGMENT, fragmentClass);
        alert.putExtra(EXTRA_CONTENT_ARGS, fragmentArguments);

        if (extraArguments != null) {
            for (String key : extraArguments.keySet()) {
                alert.putExtra(key, extraArguments.get(key));
            }
        }
        alert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alert.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        context.startActivity(alert);
        return dialogId;
    }

    /**
     * Waits until the dialog with given <code>dialogId</code> is opened.
     *
     * @param dialogId the id of the dialog we want to wait for.
     * @return <code>true</code> if dialog has been opened or <code>false</code> if the dialog had not
     * been opened within 10 seconds after call to this method.
     */
    public static boolean waitForDialogOpened(long dialogId)
    {
        synchronized (displayedDialogs) {
            if (!displayedDialogs.contains(dialogId)) {
                try {
                    displayedDialogs.wait(10000);
                    return displayedDialogs.contains(dialogId);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                return true;
            }
        }
    }

    /**
     * The listener that will be notified when user clicks the confirm button or dismisses the dialog.
     */
    public interface DialogListener
    {
        /**
         * Fired when user clicks the dialog's confirm button.
         *
         * @param dialog source <code>DialogActivity</code>.
         */
        boolean onConfirmClicked(DialogActivity dialog);

        /**
         * Fired when user dismisses the dialog.
         *
         * @param dialog source <code>DialogActivity</code>
         */
        void onDialogCancelled(DialogActivity dialog);
    }
}
