/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chatroomslist;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import net.java.sip.communicator.service.muc.ChatRoomWrapper;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.gui.util.MultiSelectionSpinner;
import org.atalk.android.gui.util.ViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xdata.BooleanFormField;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.FormField.Option;
import org.jivesoftware.smackx.xdata.ListMultiFormField;
import org.jivesoftware.smackx.xdata.ListSingleFormField;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jivesoftware.smackx.xdata.form.Form;

import timber.log.Timber;

/**
 * The user interface that allows user to configure the room properties.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomConfiguration extends BaseFragment {
    /**
     * Declare as static to support rotation, otherwise crash when user rotate
     * Instead of using save Bundle approach
     */
    private static ChatRoomWrapper mChatRoomWrapper;
    private static ChatRoomConfigListener mCrcListener = null;

    private MultiUserChat multiUserChat;

    /**
     * The list of form fields in the room configuration stanza
     */
    private List<FormField> formFields = new ArrayList<>();

    /**
     * Room configuration reply submit form
     */
    private FillableForm replyForm;

    /**
     * Map contains a list of the user selected/changed room properties
     */
    private final Map<String, Object> configUpdates = new HashMap<>();

    /**
     * The Room configuration list view adapter for user selection
     */
    private ConfigListAdapter configListAdapter;

    /**
     * View for room configuration title description from the room configuration form
     */
    private TextView mTitle;

    /**
     * Constructs the <code>ChatRoomConfiguration</code>.
     *
     * @param chatRoomWrapper user joined ChatRoomWrapper for the <code>Chat Session</code>
     * @param crcListener ChatRoomConfigListener
     */
    public static ChatRoomConfiguration getInstance(ChatRoomWrapper chatRoomWrapper, ChatRoomConfigListener crcListener) {
        ChatRoomConfiguration fragment = new ChatRoomConfiguration();
        mChatRoomWrapper = chatRoomWrapper;
        mCrcListener = crcListener;
        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.chatroom_config, container, false);
        mTitle = contentView.findViewById(R.id.config_title);

        ListView configListView = contentView.findViewById(R.id.formListView);
        configListAdapter = new ConfigListAdapter(inflater);
        configListView.setAdapter(configListAdapter);

        Button cancelButton = contentView.findViewById(R.id.rcb_Cancel);
        cancelButton.setOnClickListener(v -> handleBackPressed());

        Button submitButton = contentView.findViewById(R.id.rcb_Submit);
        submitButton.setOnClickListener(v -> {
            if (processRoomConfiguration())
                handleBackPressed();
        });

        return contentView;
    }

    /**
     * Use locally or call from ChatActivity. Fragment does not support onBackPressed method.
     */
    public void handleBackPressed() {
        FragmentManager fm = getParentFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();

            if (mCrcListener != null)
                mCrcListener.onConfigComplete(configUpdates);
        }
    }

    /**
     * Process the user selected room configurations (in configAnswers) and submit them to server.
     * Note: any properties for no persistent room will be reset to default when last participant left.
     */
    private boolean processRoomConfiguration() {
        Map<String, Object> updates = Collections.synchronizedMap(configUpdates);
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String variable = entry.getKey();
            Object value = entry.getValue();

            try {
                if (value instanceof Boolean) {
                    replyForm.setAnswer(variable, (Boolean) value);
                }
                else if (value instanceof String) {
                    replyForm.setAnswer(variable, (String) value);
                }
                else if (value instanceof ArrayList<?>) {
                    replyForm.setAnswer(variable, (ArrayList) value);
                }
                else {
                    Timber.w("UnSupported argument type: %s -> %s", variable, value);
                }
            } catch (IllegalArgumentException e) {
                Timber.w("Illegal Argument Exception: %s -> %s; %s", variable, value, e.getMessage());
            }
        }
        // submit the room configuration to server
        if (!updates.isEmpty() && (multiUserChat != null)) {
            try {
                multiUserChat.sendConfigurationForm(replyForm);
            } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                     | SmackException.NotConnectedException | InterruptedException e) {
                Timber.w("Room configuration submit exception: %s", e.getMessage());
            }
        }
        return true;
    }

    /**
     * Adapter displaying all the available room configuration properties for user selection.
     */
    private class ConfigListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;

        private ConfigListAdapter(LayoutInflater inflater) {
            mInflater = inflater;
            new RoomConfigInfo().execute();
        }

        @Override
        public int getCount() {
            return formFields.size();
        }

        @Override
        public Object getItem(int position) {
            return formFields.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            int viewType = -1;

            FormField formField = formFields.get(position);
            if (formField != null) {
                viewType = formField.getType().ordinal();
            }
            return viewType;
        }

        @Override
        public int getViewTypeCount() {
            return FormField.Type.values().length;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            List<Option> ffOptions;
            List<String> optionList = new ArrayList<>();
            List<String> valueList = new ArrayList<>();
            Map<String, String> mapOption = new HashMap<>();

            TextView textLabel;
            EditText editText;

            FormField ff = formFields.get(position);
            if (ff != null) {
                String fieldName = ff.getFieldName();
                String label = ff.getLabel();
                String firstValue = ff.getFirstValue();
                Object objValue = configUpdates.get(fieldName);

                FormField.Type formType = ff.getType();
                try {
                    switch (formType) {
                        case bool:
                            convertView = mInflater.inflate(R.layout.chatroom_config_boolean, parent, false);

                            CheckBox cb = convertView.findViewById(R.id.cb_formfield);
                            cb.setText(label);

                            if (objValue instanceof Boolean) {
                                cb.setChecked((Boolean) objValue);
                            }
                            else {
                                cb.setChecked(((BooleanFormField) ff).getValueAsBoolean());
                            }
                            cb.setOnCheckedChangeListener((cb1, isChecked) -> configUpdates.put(fieldName, isChecked));
                            break;

                        case list_multi:
                            convertView = mInflater.inflate(R.layout.chatroom_config_list_multi, parent, false);

                            textLabel = convertView.findViewById(R.id.cr_attr_label);
                            textLabel.setText(label);

                            if (objValue instanceof ArrayList<?>) {
                                valueList = (List<String>) objValue;
                            }
                            else {
                                valueList = ff.getValuesAsString();
                            }

                            // Create both optionList and valueList both using optLabels as keys
                            ffOptions = ((ListMultiFormField) ff).getOptions();
                            for (Option option : ffOptions) {
                                String optLabel = option.getLabel();
                                String optValue = option.getValueString();

                                mapOption.put(optLabel, optValue);
                                int index = valueList.indexOf(optValue);
                                if (index != -1)
                                    valueList.set(index, optLabel);
                                optionList.add(optLabel);
                            }

                            MultiSelectionSpinner multiSelectionSpinner = convertView.findViewById(R.id.cr_Spinner);
                            multiSelectionSpinner.setItems(optionList, (multiSelectionSpinner1, selected) -> {
                                List<String> selection = new ArrayList<>();
                                for (int i = 0; i < optionList.size(); ++i) {
                                    if (selected[i]) {
                                        String optSelected = optionList.get(i);
                                        selection.add(mapOption.get(optSelected));
                                    }
                                }
                                configUpdates.put(fieldName, selection);
                            });

                            multiSelectionSpinner.setSelection(valueList);
                            break;

                        case list_single:
                            convertView = mInflater.inflate(R.layout.chatroom_config_list_single, parent, false);

                            textLabel = convertView.findViewById(R.id.cr_attr_label);
                            textLabel.setText(label);

                            ffOptions = ((ListSingleFormField) ff).getOptions();
                            for (Option option : ffOptions) {
                                String optLabel = option.getLabel();
                                String optValue = option.getValueString();

                                mapOption.put(optLabel, optValue);
                                valueList.add(optValue);
                                optionList.add(optLabel);
                            }

                            Spinner spinner = convertView.findViewById(R.id.cr_Spinner);
                            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(mContext, R.layout.simple_spinner_item, optionList);
                            arrayAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
                            spinner.setAdapter(arrayAdapter);

                            if (objValue instanceof String) {
                                firstValue = (String) objValue;
                            }

                            spinner.setSelection(valueList.indexOf(firstValue), false);
                            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                                    String optSelected = optionList.get(position);
                                    configUpdates.put(fieldName, mapOption.get(optSelected));
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> parentView) {
                                    // your code here
                                }
                            });
                            break;

                        case text_private:
                            convertView = mInflater.inflate(R.layout.chatroom_config_text_private, parent, false);

                            textLabel = convertView.findViewById(R.id.cr_attr_label);
                            textLabel.setText(label);

                            if (objValue instanceof String) {
                                firstValue = (String) objValue;
                            }

                            editText = convertView.findViewById(R.id.passwordField);
                            editText.setText(firstValue);

                            editText.addTextChangedListener(new TextWatcher() {
                                @Override
                                public void afterTextChanged(Editable s) {
                                    if (s != null) {
                                        configUpdates.put(fieldName, s.toString());
                                    }
                                }

                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                }

                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {
                                }
                            });

                            CheckBox pwdCheckBox = convertView.findViewById(R.id.show_password);
                            pwdCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                                    -> ViewUtil.showPassword(editText, isChecked));
                            break;

                        case text_single:
                        case text_multi:
                            convertView = mInflater.inflate(R.layout.chatroom_config_text_single, parent, false);

                            textLabel = convertView.findViewById(R.id.cr_attr_label);
                            textLabel.setText(label);

                            if (objValue instanceof String) {
                                firstValue = (String) objValue;
                            }

                            editText = convertView.findViewById(R.id.cr_attr_value);
                            editText.setText(firstValue);

                            editText.addTextChangedListener(new TextWatcher() {
                                @Override
                                public void afterTextChanged(Editable s) {
                                    if (s != null) {
                                        configUpdates.put(fieldName, s.toString());
                                    }
                                }

                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                }

                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {
                                }
                            });
                            break;

                        case fixed:
                        case jid_multi:
                        case jid_single:
                            Timber.w("Unhandled formField type: %s; variable: %s; %s=%s", formType.toString(),
                                    fieldName, label, firstValue);
                        case hidden:
                            // convertView cannot be null, so just return an empty view
                            convertView = mInflater.inflate(R.layout.chatroom_config_none, parent, false);
                            break;
                    }
                    convertView.setTag(fieldName);
                } catch (Exception e) {
                    Timber.w("Exception in get View for variable %s; %s=%s; %s %s", fieldName, label, firstValue,
                            configUpdates.get(fieldName), e.getMessage());
                }
            }
            return convertView;
        }

        /**
         * Retrieve the chatRoom configuration fields from the server and init the default replyFrom
         * Populate the fragment with the available options in getView()
         */
        private class RoomConfigInfo {
            public RoomConfigInfo() {
                MultiUserChatManager mMucMgr
                        = MultiUserChatManager.getInstanceFor(mChatRoomWrapper.getProtocolProvider().getConnection());
                multiUserChat = mMucMgr.getMultiUserChat(mChatRoomWrapper.getEntityBareJid());
            }

            public void execute() {
                Executors.newSingleThreadExecutor().execute(() -> {
                    final Form initForm = doInBackground();

                    runOnUiThread(() -> {
                        if (initForm != null) {
                            mTitle.setText(initForm.getTitle());

                            formFields = initForm.getDataForm().getFields();
                            replyForm = initForm.getFillableForm();
                        }
                        configListAdapter.notifyDataSetChanged();
                    });
                });
            }

            private Form doInBackground() {
                Form initForm = null;
                try {
                    initForm = multiUserChat.getConfigurationForm();
                } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                         | SmackException.NotConnectedException | InterruptedException e) {
                    Timber.w("Exception in get room configuration form %s", e.getMessage());
                }
                return initForm;
            }
        }
    }

    public interface ChatRoomConfigListener {
        void onConfigComplete(Map<String, Object> configUpdates);
    }
}
