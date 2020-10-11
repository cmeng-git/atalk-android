/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.*;
import android.text.*;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.call.notification.CallNotificationManager;
import org.atalk.android.gui.share.Attachment;
import org.atalk.android.gui.share.MediaPreviewAdapter;
import org.atalk.android.gui.util.*;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.android.plugin.audioservice.SoundMeter;
import org.atalk.persistance.FilePathHelper;
import org.jivesoftware.smackx.chatstates.ChatState;

import java.util.*;

import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

/**
 * Class is used to separate the logic of send message editing process from <tt>ChatFragment</tt>.
 * It handles last messages correction, editing, sending messages and chat state notifications.
 * It also restores edit state when the chat fragment is scrolled in view again.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatController implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener,
        TextWatcher, ContentEditText.CommitListener
{
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
     * Correction indicator / cancel button.
     */
    private View cancelCorrectionBtn;
    /**
     * Send button's View.
     */
    private View sendBtn;
    /**
     * media call button's View.
     */
    private View callBtn;
    /**
     * Audio recording button.
     */
    private View audioBtn;
    /**
     * Message <tt>EditText</tt>.
     */
    private ContentEditText msgEdit;
    /**
     * Message editing area background.
     */
    private View msgEditBg;
    /**
     * Chat chatPanel used by this controller and its parent chat fragment.
     */

    private RecyclerView mediaPreview;
    private ImageView imagePreview;

    private View chatReplyCancel;
    private TextView chatMessageReply;
    private String quotedMessage;

    private ChatPanel chatPanel;
    /**
     * Current Chat Transport associates with this Chat Controller.
     */
    private ChatTransport mChatTransport = null;
    /**
     * Typing state control thread that goes from composing to stopped state.
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

    private View msgRecordView;
    private TextView mRecordTimer;
    private TextView mdBTextView;
    private ImageView mTrash;
    private SoundMeter mSoundMeter;

    private AnimationDrawable mTtsAnimate;
    private AnimationDrawable mTrashAnimate;
    private Animation animBlink, animZoomOut, animSlideUp;

    // Constant to detect slide left to cancel audio recording
    private static final int min_distance = 100;
    private float downX;

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
        isAudioAllowed = ContextCompat.checkSelfPermission(parent,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Method called by the <tt>ChatFragment</tt> when it is displayed to the user and its <tt>View</tt> is created.
     */
    public void onShow()
    {
        if (!isAttached) {
            this.isAttached = true;

            // Timber.d("ChatController attached to %s", chatFragment.hashCode());
            chatPanel = chatFragment.getChatPanel();

            // Gets message edit view
            msgEdit = parent.findViewById(R.id.chatWriteText);
            msgEdit.setCommitListener(this);
            msgEdit.setFocusableInTouchMode(true);

            // Restore edited text
            msgEdit.setText(chatPanel.getEditedText());
            msgEdit.addTextChangedListener(this);

            // Message typing area background
            msgEditBg = parent.findViewById(R.id.chatTypingArea);
            msgRecordView = parent.findViewById(R.id.recordView);

            // Gets the cancel correction button and hooks on click action
            cancelCorrectionBtn = parent.findViewById(R.id.cancelCorrectionBtn);
            cancelCorrectionBtn.setOnClickListener(this);

            // Quoted reply message view
            chatMessageReply = parent.findViewById(R.id.chatMsgReply);
            chatMessageReply.setVisibility(View.GONE);
            chatReplyCancel = parent.findViewById(R.id.chatReplyCancel);
            chatReplyCancel.setVisibility(View.GONE);
            chatReplyCancel.setOnClickListener(this);

            imagePreview = parent.findViewById(R.id.imagePreview);
            mediaPreview = parent.findViewById(R.id.media_preview);

            // Gets the send message button and hooks on click action
            sendBtn = parent.findViewById(R.id.sendMessageButton);
            sendBtn.setOnClickListener(this);

            // Gets the send audio button and hooks on click action if permission allowed
            audioBtn = parent.findViewById(R.id.audioMicButton);
            if (isAudioAllowed) {
                audioBtn.setOnClickListener(this);
                audioBtn.setOnLongClickListener(this);
                audioBtn.setOnTouchListener(this);
            }
            else {
                Timber.w("Audio recording is not allowed - permission denied!");
            }

            // Gets the call switch button
            callBtn = parent.findViewById(R.id.chatBackToCallButton);
            callBtn.setOnClickListener(this);

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

            updateCorrectionState();
            initChatController();
            updateSendModeState();
        }
    }

    /**
     * Init to correct mChatTransport; if chatTransPort allows, then enable chatState
     * notifications thread. Perform only if the chatFragment is really visible to user
     *
     * Otherwise the non-focus chatFragment will cause out-of-sync between chatFragment and
     * chatController i.e. entered msg display in wrong chatFragment
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initChatController()
    {
        if (!chatFragment.isVisible()) {
            Timber.w("Skip init current Chat Transport to: %s; with visible State: %s",
                    mChatTransport, chatFragment.isVisible());
            return;
        }

        mChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
        allowsChatStateNotifications = (mChatTransport.allowsChatStateNotifications()
                && ConfigurationUtils.isSendChatStateNotifications());

        if (allowsChatStateNotifications) {
            msgEdit.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    onTouchAction();
                }
                return false;
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
     * This happens when user scroll pagerAdapter, and the chat window is out of view
     */
    public void onHide()
    {
        if (isAttached) {
            isAttached = false;

            // Remove text listener
            msgEdit.removeTextChangedListener(this);
            // Store edited text in chatPanel
            if ((chatPanel != null) && (msgEdit.getText() != null))
                chatPanel.setEditedText(msgEdit.getText().toString());

            mediaPreview.setVisibility(View.GONE);
        }
    }

    /**
     * Sends the chat message or corrects the last message if the chatPanel has correction UID set.
     *
     * @param message the text string to be sent
     * @param encType The encType of the message to be sent: RemoteOnly | 1=text/html or 0=text/plain.
     */
    public void sendMessage(String message, int encType)
    {
        String correctionUID = chatPanel.getCorrectionUID();

        int encryption = IMessage.ENCRYPTION_NONE;
        if (chatPanel.isOmemoChat())
            encryption = IMessage.ENCRYPTION_OMEMO;
        else if (chatPanel.isOTRChat())
            encryption = IMessage.ENCRYPTION_OTR;

        if (correctionUID == null) {
            try {
                mChatTransport.sendInstantMessage(message, encryption | encType);
            } catch (Exception ex) {
                Timber.w("Send instant message exception: %s", ex.getMessage());
                aTalkApp.showToastMessage(ex.getMessage());
            }
        }
        // Last message correction
        else {
            mChatTransport.sendInstantMessage(message, encryption | encType, correctionUID);
            // Clears correction UI state
            chatPanel.setCorrectionUID(null);
            updateCorrectionState();
        }

        // must run on UiThread when access view
        parent.runOnUiThread(() -> {
            // Clears edit text field
            msgEdit.setText("");

            // just update chat state to active but not sending notifications
            mChatState = ChatState.active;
            if (chatStateCtrlThread == null) {
                chatStateCtrlThread = new ChatStateControl();
                chatStateCtrlThread.start();
            }
            chatStateCtrlThread.initChatState();
        });
    }

    /**
     * Method fired when the chat message is clicked. {@inheritDoc}
     * Trigger from @see ChatFragment#
     */
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

        if (mChatTransport instanceof MetaContactChatTransport) {
            editText(adapter, chatMessage, position);
        }
        // Just put the last message in edit box for Omemo send error
        else {
            msgEdit.setText(chatMessage.getContentForCorrection());
        }
    }

    public void editText(AdapterView adapter, ChatMessage chatMessage, int position)
    {
        // ListView cListView = chatFragment.getChatListView();
        String uidToCorrect = chatMessage.getUidForCorrection();
        String content = chatMessage.getContentForCorrection();

        if (!TextUtils.isEmpty(content)) {
            // Sets corrected message content and show the keyboard
            msgEdit.setText(content);
            msgEdit.requestFocus();

            // Not send message - uidToCorrect is null
            if (!TextUtils.isEmpty(uidToCorrect)) {
                // Change edit text bg colors and show cancel button
                chatPanel.setCorrectionUID(uidToCorrect);
                updateCorrectionState();

                InputMethodManager inputMethodManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null)
                    inputMethodManager.showSoftInput(msgEdit, InputMethodManager.SHOW_IMPLICIT);

                // Select corrected message
                // TODO: it doesn't work when keyboard is displayed for the first time
                adapter.setSelection(position);
            }
        }
    }

    public void setQuoteMessage(ChatMessage replyMessage)
    {
        if (replyMessage != null) {
            chatMessageReply.setVisibility(View.VISIBLE);
            chatReplyCancel.setVisibility(View.VISIBLE);

            Html.ImageGetter imageGetter = new HtmlImageGetter();
            String body = replyMessage.getMessage();
            if (!body.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?")) {
                body = body.replace("\n", "<br/>");
            }
            quotedMessage = aTalkApp.getResString(R.string.service_gui_CHAT_REPLY,
                    replyMessage.getSender(), body);
            chatMessageReply.setText(Html.fromHtml(quotedMessage, imageGetter, null));
        }
        else {
            quotedMessage = null;
            chatMessageReply.setVisibility(View.GONE);
            chatReplyCancel.setVisibility(View.GONE);
        }
    }

    /**
     * Method fired when send a message or cancel correction button is clicked.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.sendMessageButton:
                if (chatPanel.getProtocolProvider().isRegistered()) {
                    if (mediaPreview.getVisibility() == View.VISIBLE) {
                        MediaPreviewAdapter mpAdapter = (MediaPreviewAdapter) mediaPreview.getAdapter();
                        if (mpAdapter != null) {
                            List<Attachment> mediaPreviews = mpAdapter.getAttachments();
                            if (!mediaPreviews.isEmpty()) {
                                for (Attachment attachment : mediaPreviews) {
                                    String filePath = FilePathHelper.getPath(parent, attachment);
                                    if (StringUtils.isNotEmpty(filePath))
                                        chatPanel.addFTRequest(filePath, ChatMessage.MESSAGE_FILE_TRANSFER_SEND);
                                }
                                mpAdapter.clearPreviews();
                            }
                        }
                    }
                    else {
                        // allow last message correction to send empty string to clear last sent text
                        String correctionUID = chatPanel.getCorrectionUID();
                        String textEdit = ViewUtil.toString(msgEdit);
                        if ((textEdit == null) && (correctionUID != null)) {
                            textEdit = " ";
                        }
                        if ((textEdit == null) && (quotedMessage == null)) {
                            return;
                        }

                        if (quotedMessage != null) {
                            textEdit = quotedMessage + textEdit;
                        }
                        // Send http link as xhtml to avoid being interpreted by the receiver as http file download link
                        else if (textEdit.matches("(?s)^http[s]:.*") && !textEdit.contains("\\s")) {
                            textEdit = aTalkApp.getResString(R.string.service_gui_CHAT_LINK, textEdit, textEdit);
                        }

                        // if text contains markup tag then send message as ENCODE_HTML mode
                        if (textEdit.matches("(?s).*?<[A-Za-z]+>.*?</[A-Za-z]+>.*?")) {
                            Timber.d("HTML text entry detected: %s", textEdit);
                            msgEdit.setText(textEdit);
                            sendMessage(textEdit, IMessage.ENCODE_HTML);
                        }
                        else
                            sendMessage(textEdit, IMessage.ENCODE_PLAIN);
                    }
                    updateSendModeState();
                }
                else {
                    aTalkApp.showToastMessage(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
                }
                if (quotedMessage == null)
                    break;
                // else continue to cleanup quotedMessage after sending
            case R.id.chatReplyCancel:
                quotedMessage = null;
                chatMessageReply.setVisibility(View.GONE);
                chatReplyCancel.setVisibility(View.GONE);
                break;

            case R.id.cancelCorrectionBtn:
                cancelCorrection();
                // Clear last message text
                msgEdit.setText("");
                break;

            case R.id.chatBackToCallButton:
                if (CallManager.getActiveCallsCount() > 0) {
                    CallNotificationManager.get().backToCall();
                }
                else
                    updateSendModeState();
                break;

            case R.id.audioMicButton:
                if (chatPanel.isChatTtsEnable()) {
                    speechToText();
                }
                break;
        }
    }

    /**
     * Audio sending is disabled if permission.RECORD_AUDIO is denied.
     * Audio chat message is allowed even for offline contact and in conference
     */
    @Override
    public boolean onLongClick(View v)
    {
        if (v.getId() == R.id.audioMicButton) {
            Timber.d("Current Chat Transport for audio: %s", mChatTransport.toString());
            Timber.d("Audio recording started!!!");
            isRecording = true;
            // Hide normal edit text view
            msgEdit.setVisibility(View.GONE);

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
            return true;
        }
        return false;
    }

    /**
     * onTouch is disabled if permission.RECORD_AUDIO is denied
     */
    @SuppressLint("ClickableViewAccessibility")
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
                float upX = event.getX();
                float deltaX = downX - upX;

                //Swipe horizontal detected
                if (Math.abs(deltaX) > min_distance) {
                    if (isRecording && (deltaX > 0)) { // right to left
                        Timber.d("Audio recording cancelled!!!");
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
                        Timber.d("Audio recording sending!!!");
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

    /**
     * Handling of KeyCode in ChatController, called from ChatActivity
     * Note: KeyEvent.Callback is only available in Activity
     */
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (event.isCtrlPressed()) {
                    if (chatFragment != null) {
                        sendBtn.performClick();
                    }
                    return true;
                }
        }
        return false;
    }

    // Need to wait on a new thread for animation to end
    private void onAnimationEnd(final int wait)
    {
        new Thread(() -> {
            try {
                Thread.sleep(wait);
                parent.runOnUiThread(() -> {
                    mTrashAnimate.stop();
                    mTrashAnimate.selectDrawable(0);

                    msgEdit.setVisibility(View.VISIBLE);

                    msgRecordView.setVisibility(View.GONE);
                    mSoundMeter.clearAnimation();
                    mdBTextView.clearAnimation();
                    mRecordTimer.clearAnimation();
                    audioBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                Timber.e("Exception: %s", ex.getMessage());
            }
        }).start();
    }

    private void startAudioService(String mAction)
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
                Timber.i("Sending audio recorded file!!!");
                LocalBroadcastManager.getInstance(parent).unregisterReceiver(mReceiver);
                String filePath = intent.getStringExtra(AudioBgService.URI);
                if (StringUtils.isNotEmpty(filePath)) {
                    chatPanel.addFTRequest(filePath, ChatMessage.MESSAGE_FILE_TRANSFER_SEND);
                }
                parent.stopService(new Intent(parent, AudioBgService.class));
            }
        }
    };

    /**
     * Built-in speech to text recognition without a soft keyboard popup.
     * To use the soft keyboard mic, click on text entry and then click on mic.
     */
    private void speechToText()
    {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(parent);

        RecognitionListener listener = new RecognitionListener()
        {
            @Override
            public void onResults(Bundle results)
            {
                ArrayList<String> voiceResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (voiceResults == null) {
                    Timber.w("No voice results");
                    updateSendModeState();
                }
                else {
                    // Contains multiple text strings for selection
                    // StringBuffer spkText = new StringBuffer();
                    // for (String match : voiceResults) {
                    //    spkText.append(match).append("\n");
                    // }
                    msgEdit.setText(voiceResults.get(0));
                }
            }

            @Override
            public void onReadyForSpeech(Bundle params)
            {
                Timber.d("Ready for speech");
                audioBtn.setBackgroundResource(R.drawable.ic_tts_mic_play);
                mTtsAnimate = (AnimationDrawable) audioBtn.getBackground();
                mTtsAnimate.start();
            }

            /**
             * ERROR_NETWORK_TIMEOUT = 1;
             * ERROR_NETWORK = 2;
             * ERROR_AUDIO = 3;
             * ERROR_SERVER = 4;
             * ERROR_CLIENT = 5;
             * ERROR_SPEECH_TIMEOUT = 6;
             * ERROR_NO_MATCH = 7;
             * ERROR_RECOGNIZER_BUSY = 8;
             * ERROR_INSUFFICIENT_PERMISSIONS = 9;
             *
             * @param error code is defined in
             * @see SpeechRecognizer()
             */
            @Override
            public void onError(int error)
            {
                Timber.e("Error listening for speech: %s ", error);
                updateSendModeState();
            }

            @Override
            public void onBeginningOfSpeech()
            {
                Timber.d("Speech starting");
            }

            @Override
            public void onBufferReceived(byte[] buffer)
            {
                // TODO Auto-generated method stub
            }

            @Override
            public void onEndOfSpeech()
            {
                mTtsAnimate.stop();
                mTtsAnimate.selectDrawable(0);
            }

            @Override
            public void onEvent(int eventType, Bundle params)
            {
                // TODO Auto-generated method stub
            }

            @Override
            public void onPartialResults(Bundle partialResults)
            {
                // TODO Auto-generated method stub
            }

            @Override
            public void onRmsChanged(float rmsdB)
            {
                // TODO Auto-generated method stub
            }
        };

        recognizer.setRecognitionListener(listener);
        recognizer.startListening(intent);
    }

    /**
     * Cancels last message correction mode.
     */
    private void cancelCorrection()
    {
        // Reset correction status
        if (chatPanel.getCorrectionUID() != null) {
            chatPanel.setCorrectionUID(null);
            updateCorrectionState();
            msgEdit.setText("");
        }
    }

    /**
     * Updates visibility state of cancel correction button and toggles bg color of the message edit field.
     */
    private void updateCorrectionState()
    {
        boolean correctionMode = (chatPanel.getCorrectionUID() != null);
        int bgColorId = correctionMode ? R.color.msg_input_correction_bg : R.color.msg_input_bar_bg;

        msgEditBg.setBackgroundColor(parent.getResources().getColor(bgColorId));
        cancelCorrectionBtn.setVisibility(correctionMode ? View.VISIBLE : View.GONE);
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
     *
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
        updateSendModeState();
    }

    /**
     * Update the view states of all send buttons based on the current available send contents.
     * Send text button has higher priority over attachment if msgEdit is not empty
     */
    public void updateSendModeState()
    {
        boolean hasAttachments = (mediaPreview.getAdapter() != null)
                && ((MediaPreviewAdapter) mediaPreview.getAdapter()).hasAttachments();
        mediaPreview.setVisibility(View.GONE);
        imagePreview.setVisibility(View.GONE);
        imagePreview.setImageDrawable(null);

        callBtn.setVisibility(View.INVISIBLE);
        audioBtn.setVisibility(View.INVISIBLE);
        msgEdit.setVisibility(View.VISIBLE);

        // Enabled send text button if text entry box contains text or in correction mode
        // Sending Text before attachment
        if (!TextUtils.isEmpty(msgEdit.getText()) || (chatPanel.getCorrectionUID() != null)) {
            sendBtn.setVisibility(View.VISIBLE);
        }
        else if (hasAttachments) {
            msgEdit.setVisibility(View.GONE);
            mediaPreview.setVisibility(View.VISIBLE);
            imagePreview.setVisibility(View.VISIBLE);
            sendBtn.setVisibility(View.VISIBLE);
        }
        else {
            sendBtn.setVisibility(View.INVISIBLE);
            if (CallManager.getActiveCallsCount() > 0) {
                callBtn.setVisibility(View.VISIBLE);
            }
            else if (isAudioAllowed) {
                audioBtn.setBackgroundResource(R.drawable.ic_voice_mic);
                audioBtn.setVisibility(View.VISIBLE);
            }
            else {
                sendBtn.setVisibility(View.VISIBLE);
            }
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
     * Update that user is no longer in this chat session and end state ctrl thread
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
        // Timber.w("Chat state changes from: " + mChatState + " => " + newState);
        if (mChatState != newState) {
            mChatState = newState;

            if (allowsChatStateNotifications)
                mChatTransport.sendChatStateNotification(newState);
        }
    }

    @Override
    public void onCommitContent(InputContentInfoCompat info)
    {
        if (chatPanel.getProtocolProvider().isRegistered()) {
            Uri contentUri = info.getContentUri();
            String filePath = FilePathHelper.getPath(parent, contentUri);
            if (StringUtils.isNotEmpty(filePath)) {
                sendSticker(filePath);
            }
            else
                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
        }
        else {
            aTalkApp.showToastMessage(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
        }
    }

    private void sendSticker(String filePath)
    {
        UIService uiService = AndroidGUIActivator.getUIService();
        if (uiService != null) {
            chatPanel.addFTRequest(filePath, ChatMessage.MESSAGE_STICKER_SEND);
        }
    }

    /**
     * The thread lowers chat state from composing to inactive state. When
     * <tt>refreshChatState</tt> is called checks for eventual chat state refreshComposing.
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

                // Timber.d("Chat State changes %s (%s)", newState, mChatState);
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

    public Activity getParent()
    {
        return parent;
    }
}
