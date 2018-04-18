/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.android.plugin.audioservice.SoundMeter;
import org.atalk.util.Logger;
import org.jivesoftware.smackx.chatstates.ChatState;

import java.util.Locale;

/**
 * Class is used to separate the logic of send message editing process from <tt>ChatFragment</tt>.
 * It handles last messages correction, editing, sending messages and chat state notifications.
 * It also restores edit state when the chat fragment is scrolled in view again.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatController implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener, TextWatcher
{
    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(ChatController.class);
    /**
     * The chat fragment used by this instance.
     */
    private ChatFragment chatFragment;
    /**
     * Parent activity.
     */
    private Activity parent;
    /**
     * Indicates that this controller is attached to the views.
     */
    private boolean isAttached;
    /**
     * Cancel button's View.
     */
    private View cancelBtn;
    /**
     * Correction indicator.
     */
    private View editingImage;
    /**
     * Send button's View.
     */
    private View sendBtn;
    /**
     * Audio recording button.
     */
    private View audioBtn;
    /**
     * Message <tt>EditText</tt>.
     */
    private EditText msgEdit;
    /**
     * Message editing area background.
     */
    private View msgEditBg;
    /**
     * Chat chatPanel used by this controller and its parent chat fragment.
     */
    private ChatPanel chatPanel;
    /**
     * Current Chat Transport associates with this Chat Controller.
     */
    private ChatTransport mChatTransport = null;
    /**
     * Typing state control thread that goes down from composing to stopped state.
     */
    private ChatStateControl chatStateCtrlThread;
    /**
     * Current chat state.
     */
    private ChatState mChatState = ChatState.gone;

    /**
     * Indicate whether sending chat state notifications to the contact is allowed:
     * 1. contact must support XEP-0085: Chat State Notifications
     * 2. User enable the chat state notifications sending option
     */
    private boolean allowsChatStateNotifications = false;

    /**
     * Audio recording variables
     */
    private boolean isAudioAllowed;
    private boolean isRecording;
    private static int animDuration = 1000;

    private View msgRecordView;
    private TextView mRecordTimer;
    private TextView mdBTextView;
    private ImageView mTrash;
    private SoundMeter mSoundMeter;

    private AnimationDrawable mTrashAnimate;
    private Animation animBlink, animZoomOut, animSlideUp;

    // Constant to detect slide left to cancel audio recording
    private static final int min_distance = 100;
    private float downX, upX;

    /**
     * Creates new instance of <tt>ChatController</tt>.
     *
     * @param activity the parent <tt>Activity</tt>.
     * @param fragment the parent <tt>ChatFragment</tt>.
     */
    public ChatController(Activity activity, ChatFragment fragment)
    {
        parent = activity;
        this.chatFragment = fragment;
        isAudioAllowed = ContextCompat.checkSelfPermission(parent, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Method called by the <tt>ChatFragment</tt> when it is displayed to the user and its
     * <tt>View</tt> is created.
     */
    public void onShow()
    {
        if (!isAttached) {
            this.isAttached = true;

            logger.debug("ChatController attached to " + chatFragment.hashCode());
            chatPanel = chatFragment.getChatPanel();

            // Gets message edit view
            msgEdit = parent.findViewById(R.id.chatWriteText);

            // Restore edited text
            msgEdit.setText(chatPanel.getEditedText());
            msgEdit.addTextChangedListener(this);

            // Message typing area background
            msgEditBg = parent.findViewById(R.id.chatTypingArea);
            msgRecordView = parent.findViewById(R.id.recordView);

            // Gets the cancel correction button and hooks on click action
            cancelBtn = parent.findViewById(R.id.cancelCorrectionBtn);
            cancelBtn.setOnClickListener(this);

            // Gets the send message button and hooks on click action
            sendBtn = parent.findViewById(R.id.sendMessageButton);
            sendBtn.setOnClickListener(this);
            sendBtn.setVisibility(View.INVISIBLE);

            // Gets the send audio button and hooks on click action if permission allowed
            audioBtn = parent.findViewById(R.id.audioRecordButton);
            if (isAudioAllowed) {
                audioBtn.setOnClickListener(this);
                audioBtn.setOnLongClickListener(this);
                audioBtn.setOnTouchListener(this);
            }
            else {
                logger.warn("Audio recording is not allowed - permission denied!");
                audioBtn.setVisibility(View.INVISIBLE);
            }

            mSoundMeter = parent.findViewById(R.id.sound_meter);
            mRecordTimer = parent.findViewById(R.id.recordTimer);
            mdBTextView = parent.findViewById(R.id.dBTextView);

            mTrash = parent.findViewById(R.id.ic_mic_trash);
            mTrashAnimate = (AnimationDrawable) mTrash.getBackground();

            animBlink = AnimationUtils.loadAnimation(parent, R.anim.blink);
            animZoomOut = AnimationUtils.loadAnimation(parent, R.anim.zoom_out);
            animZoomOut.setDuration(1000);
            animSlideUp = AnimationUtils.loadAnimation(parent, R.anim.slide_up);
            animSlideUp.setDuration(1000);

            this.editingImage = parent.findViewById(R.id.editingImage);
            updateCorrectionState();
            initChatController();
        }
    }

    /**
     * Init to correct mChatTransport; if chatTransPort allows, then enable chatState
     * notifications thread. Perform only if the chatFragment is really visible to user
     * <p>
     * Otherwise the non-focus chatFragment will cause out-of-sync between chatFragment and
     * chatController i.e. entered msg display in wrong chatFragment
     */
    private void initChatController()
    {
        if (!chatFragment.isVisible()) {
            logger.warn("Skip init current Chat Transport to: " + mChatTransport
                    + "; with visible State: " + chatFragment.isVisible());
            return;
        }

        mChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
        allowsChatStateNotifications = (mChatTransport.allowsChatStateNotifications()
                && ConfigurationUtils.isSendChatStateNotifications());

        if (allowsChatStateNotifications) {
            msgEdit.setOnTouchListener(new EditText.OnTouchListener()
            {
                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        onTouchAction();
                    }
                    return false;
                }
            });

            // Start chat state control thread and give 500mS before sending ChatState.active
            // to take care the fast scrolling of fragment by user.
            if (chatStateCtrlThread == null) {
                mChatState = ChatState.gone;
                chatStateCtrlThread = new ChatStateControl();
                chatStateCtrlThread.start();
            }
        }
    }

    /**
     * Method called by <tt>ChatFragment</tt> when it's no longer displayed to the user.
     * This happens when user scroll pagerAdapter and the chat window is out of view
     */
    public void onHide()
    {
        if (isAttached) {
            isAttached = false;

            // Remove text listener
            msgEdit.removeTextChangedListener(this);
            // Store edited text in chatPanel
            if (chatPanel != null)
                chatPanel.setEditedText(msgEdit.getText().toString());
        }
    }

    /**
     * Sends the chat message or corrects the last message if the chatPanel has correction UID set.
     */
    private void sendMessage()
    {
        String content = msgEdit.getText().toString();
        String correctionUID = chatPanel.getCorrectionUID();

        if (correctionUID == null) {
            // Sends the message
            try {
                if (chatPanel.isOmemoChat())
                    mChatTransport.sendInstantMessage(content, ChatMessage.ENCRYPTION_OMEMO);
                else
                    mChatTransport.sendInstantMessage(content, ChatMessage.ENCODE_PLAIN);
            } catch (Exception ex) {
                logger.warn("Send message failed: " + ex.getMessage());
            }
        }
        // Last message correction
        else {
            if (chatPanel.isOmemoChat())
                mChatTransport.correctInstantMessage(content, ChatMessage.ENCRYPTION_OMEMO, correctionUID);
            else
                mChatTransport.correctInstantMessage(content, ChatMessage.ENCODE_PLAIN, correctionUID);

            // Clears correction UI state
            chatPanel.setCorrectionUID(null);
            updateCorrectionState();
        }
        // Clears edit text field
        msgEdit.setText("");

        // just update chat state to active but not sending notifications
        mChatState = ChatState.active;
        if (chatStateCtrlThread == null) {
            chatStateCtrlThread = new ChatStateControl();
            chatStateCtrlThread.start();
        }
        chatStateCtrlThread.initChatState();
    }

    /**
     * Method fired when the chat message is clicked. {@inheritDoc}
     * Trigger from @see ChatFragment#
     */
    // @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
    {
        // Detect outgoing message area
        if ((view.getId() != R.id.outgoingMessageView) && (view.getId() != R.id.outgoingMessageHolder)) {
            cancelCorrection();
            return;
        }
        ChatFragment.ChatListAdapter chatListAdapter = chatFragment.getChatListAdapter();

        // Position must be aligned to the number of header views included
        int headersCount = ((ListView) adapter).getHeaderViewsCount();
        int cPos = position - headersCount;
        ChatMessage chatMessage = chatListAdapter.getMessage(cPos);

        // Ensure the selected message is really the last outgoing message
        if (cPos != chatListAdapter.getCount() - 1) {
            for (int i = cPos + 1; i < chatListAdapter.getCount(); i++) {
                if (chatListAdapter.getItemViewType(i) == ChatFragment.ChatListAdapter.OUTGOING_MESSAGE_VIEW) {
                    cancelCorrection();
                    return;
                }
            }
        }
        editText(adapter, chatMessage, position);
    }

    public void editText(AdapterView adapter, ChatMessage chatMessage, int position)
    {
        // ListView cListView = chatFragment.getChatListView();
        String uidToCorrect = chatMessage.getUidForCorrection();
        String content = chatMessage.getContentForCorrection();
        if (uidToCorrect != null && content != null) {
            // Change edit text bg colors and show cancel button
            chatPanel.setCorrectionUID(uidToCorrect);
            updateCorrectionState();
            // Sets corrected message content and show the keyboard
            msgEdit.setText(content);
            msgEdit.setFocusableInTouchMode(true);
            msgEdit.requestFocus();

            InputMethodManager inputMethodManager
                    = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(msgEdit, InputMethodManager.SHOW_IMPLICIT);
            // Select corrected message
            // TODO: it doesn't work when keyboard is displayed for the first time
            adapter.setSelection(position);
        }
    }

    /**
     * Method fired when send message or cancel correction button's are clicked.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.sendMessageButton:
                sendMessage();
                sendBtn.setVisibility(View.INVISIBLE);
                if (isAudioAllowed)
                    audioBtn.setVisibility(View.VISIBLE);
                break;

            case R.id.cancelCorrectionBtn:
                cancelCorrection();
                // Clear edited text
                msgEdit.setText("");
                break;

            case R.id.audioRecordButton:
                // No action
                break;
        }
    }

    /**
     * Audio sending is disabled if permission.RECORD_AUDIO is denied
     */
    @Override
    public boolean onLongClick(View v)
    {
        switch (v.getId()) {
            case R.id.audioRecordButton:
                logger.warn("Current Chat Transport for audio: " + mChatTransport.toString());
                if (!mChatTransport.getStatus().isOnline()) {
                    Toast.makeText(parent, R.string.chat_noaudio_buddyOffline, Toast.LENGTH_SHORT).show();
                }
                else {
                    logger.info("Audio recording started!!!");
                    isRecording = true;
                    // Hide normal edit text view
                    msgEdit.setVisibility(View.GONE);
                    cancelBtn.setVisibility(View.GONE);

                    // Show audio record information
                    msgRecordView.setVisibility(View.VISIBLE);
                    mTrash.setImageResource(R.drawable.ic_record);
                    mTrash.startAnimation(animBlink);

                    // Set up audio background service and receiver
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(AudioBgService.ACTION_AUDIO_RECORD);
                    filter.addAction(AudioBgService.ACTION_SMI);
                    LocalBroadcastManager.getInstance(parent).registerReceiver(mReceiver, filter);
                    startAudioService(AudioBgService.ACTION_RECORDING);
                }
                break;
        }
        return true;
    }

    /**
     * onTouch is disabled if permission.RECORD_AUDIO is denied
     */
    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        boolean done = false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                return false;  // to allow long press detection
            }

            case MotionEvent.ACTION_UP: {
                upX = event.getX();
                float deltaX = downX - upX;

                //Swipe horizontal detected
                if (Math.abs(deltaX) > min_distance) {
                    if (isRecording && (deltaX > 0)) { // right to left
                        logger.info("Audio recording cancelled!!!");
                        isRecording = false;
                        audioBtn.setEnabled(false); // disable while in animation
                        LocalBroadcastManager.getInstance(parent).unregisterReceiver(mReceiver);
                        startAudioService(AudioBgService.ACTION_CANCEL);

                        // Start audio sending cancel animation
                        mSoundMeter.startAnimation(animZoomOut);
                        mdBTextView.startAnimation(animSlideUp);
                        mRecordTimer.startAnimation(animSlideUp);

                        mTrash.clearAnimation();
                        mTrash.setImageDrawable(null);
                        mTrashAnimate.start();
                        onAnimationEnd(1200);
                        done = true;
                    }
                }
                else {
                    if (isRecording) {
                        logger.info("Audio recording sending!!!");
                        isRecording = false;
                        startAudioService(AudioBgService.ACTION_SEND);
                        onAnimationEnd(10);
                        done = true;
                    }
                }
            }
        }
        return done;
    }

    // Need to wait on new thread for animation to end
    private void onAnimationEnd(final int wait)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep(wait);
                    parent.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mTrashAnimate.stop();
                            mTrashAnimate.selectDrawable(0);

                            msgEdit.setVisibility(View.VISIBLE);
                            cancelBtn.setVisibility(View.VISIBLE);

                            msgRecordView.setVisibility(View.GONE);
                            mSoundMeter.clearAnimation();
                            mdBTextView.clearAnimation();
                            mRecordTimer.clearAnimation();
                            audioBtn.setEnabled(true);
                        }
                    });
                } catch (Exception ex) {
                    logger.error("Exception: ", ex);
                }
            }
        }).start();
    }

    public void startAudioService(String mAction)
    {
        Intent intent = new Intent(parent, AudioBgService.class);
        intent.setAction(mAction);
        parent.startService(intent);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (isRecording && AudioBgService.ACTION_SMI.equals(intent.getAction())) {
                String mDuration = intent.getStringExtra(AudioBgService.RECORD_TIMER);
                double mdBSpl = intent.getDoubleExtra(AudioBgService.SPL_LEVEL, 1.0);
                double dBspl = (mdBSpl * AudioBgService.mDBRange);
                String sdBSpl = String.format(Locale.US, "%.02f", dBspl) + "dB";

                mSoundMeter.setLevel(mdBSpl);
                mdBTextView.setText(sdBSpl);
                mRecordTimer.setText(mDuration);
            }
            else if (AudioBgService.ACTION_AUDIO_RECORD.equals(intent.getAction())) {
                logger.info("Sending audio recorded file!!!");
                LocalBroadcastManager.getInstance(parent).unregisterReceiver(mReceiver);
                String filePath = intent.getStringExtra(AudioBgService.URI);
                if (filePath != null && filePath.length() > 0) {
                    ((ChatActivity) parent).sendFile(filePath);
                }
                parent.stopService(new Intent(parent, AudioBgService.class));
            }
        }
    };

    /**
     * Cancels last message correction mode.
     */
    private void cancelCorrection()
    {
        // Reset correction status
        if (chatPanel.getCorrectionUID() != null) {
            chatPanel.setCorrectionUID(null);
            updateCorrectionState();
            // Clear edited text
            msgEdit.setText("");
        }
    }

    /**
     * Updates visibility state of cancel correction button and toggles bg color of the message edit field.
     */
    private void updateCorrectionState()
    {
        boolean correction = chatPanel.getCorrectionUID() != null;
        int bgColorId = correction ? R.color.msg_input_correction_bg : R.color.msg_input_bar_bg;

        msgEditBg.setBackgroundColor(parent.getResources().getColor(bgColorId));
        editingImage.setVisibility(correction ? View.VISIBLE : View.GONE);
        chatFragment.getChatListView().invalidateViews();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after)
    {
    }

    @Override
    public void afterTextChanged(Editable s)
    {
    }

    /**
     * Updates chat state.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count)
    {
        if (allowsChatStateNotifications) {
            if (s.length() > 0) {
                // Start or refreshComposing chat state control thread
                if (chatStateCtrlThread == null) {
                    setNewChatState(ChatState.active);
                    setNewChatState(ChatState.composing);
                    chatStateCtrlThread = new ChatStateControl();
                    chatStateCtrlThread.start();
                }
                else
                    chatStateCtrlThread.refreshChatState();
            }
        }
        if (s.length() > 0) {
            sendBtn.setVisibility(View.VISIBLE);
            audioBtn.setVisibility(View.INVISIBLE);
        }
        else {
            sendBtn.setVisibility(View.INVISIBLE);
            if (isAudioAllowed)
                audioBtn.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Method called by <tt>ChatFragment</tt> and <tt>ChatController</tt>. when user touches the
     * display. Re-init chat state to active when user return to chat session
     */
    public void onTouchAction()
    {
        if (mChatState == ChatState.inactive) {
            setNewChatState(ChatState.active);
            if (chatStateCtrlThread == null) {
                chatStateCtrlThread = new ChatStateControl();
                chatStateCtrlThread.start();
            }
        }
    }

    /**
     * Method called by <tt>ChatFragment</tt> when user closes the chat window.
     * Update that user is no logger in this chat session and end state ctrl thread
     */
    public void onChatCloseAction()
    {
        setNewChatState(ChatState.gone);
    }

    /**
     * Sets new chat state and send notification is enabled.
     *
     * @param newState new chat state to set.
     */
    private void setNewChatState(ChatState newState)
    {
        // logger.warn("Chat state changes from: " + mChatState + " => " + newState);
        if (mChatState != newState) {
            mChatState = newState;

            if (allowsChatStateNotifications)
                mChatTransport.sendChatStateNotification(newState);
        }
    }

    /**
     * The thread lowers chat state from composing to inactive state. When
     * <tt>refreshChatState</tt>
     * is called checks for eventual chat state refreshComposing.
     */
    class ChatStateControl extends Thread
    {
        boolean refreshComposing;
        boolean initActive;
        boolean cancel = false;

        @Override
        public void run()
        {
            while (mChatState != ChatState.inactive) {
                refreshComposing = false;
                initActive = false;
                ChatState newState;
                long delay;

                switch (mChatState) {
                    case gone:
                        delay = 500;
                        newState = ChatState.active;
                        break;
                    case composing:
                        delay = 10000;
                        newState = ChatState.paused;
                        break;
                    case paused:
                        delay = 15000;
                        newState = ChatState.inactive;
                        break;
                    default: // active
                        delay = 30000;
                        newState = ChatState.inactive;
                }

                synchronized (this) {
                    try {
                        // Waits the delay to enter newState
                        this.wait(delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (refreshComposing) {
                    newState = ChatState.composing;
                }
                else if (initActive) {
                    newState = ChatState.active;
                }
                else if (cancel) {
                    newState = ChatState.gone;
                }

                // Post new chat state
                setNewChatState(newState);
            }
            chatStateCtrlThread = null;
        }

        /**
         * Refresh the thread's control loop to ChatState.composing.
         */
        void refreshChatState()
        {
            synchronized (this) {
                refreshComposing = true;
                this.notify();
            }
        }

        /**
         * Initialize the thread' control loop to ChatState.active
         */
        void initChatState()
        {
            synchronized (this) {
                initActive = true;
                this.notify();
            }
        }

        /**
         * Cancels (ChatState.gone) and joins the thread.
         */
        void cancel()
        {
            synchronized (this) {
                cancel = true;
                this.notify();
            }
            try {
                this.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
