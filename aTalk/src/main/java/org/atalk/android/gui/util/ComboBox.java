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
package org.atalk.android.gui.util;

import android.content.Context;
import android.database.Cursor;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import org.atalk.android.R;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Custom ComboBox for Android
 *
 * @author Eng Chong Meng
 */
public class ComboBox extends LinearLayout
{
    protected AutoCompleteTextView _text;
    protected List<String> spinnerList;

    private int unit = TypedValue.COMPLEX_UNIT_SP;
    private float fontSize = 15;
    private int fontBlack = getResources().getColor(R.color.textColorBlack);

    private Context mContext;
    private LayoutInflater inflater;

    public ComboBox(Context context)
    {
        super(context);
        this.createChildControls(context);
    }

    public ComboBox(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        this.createChildControls(context);
    }

    private void createChildControls(Context context)
    {
        mContext = context;
        inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        this.setOrientation(HORIZONTAL);
        this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        _text = new AutoCompleteTextView(context);
        _text.setDropDownWidth(-1); // set the dropdown width to match screen
        _text.setTextSize(unit, fontSize);
        _text.setTextColor(fontBlack);
        _text.setSingleLine();
        _text.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        _text.setRawInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        this.addView(_text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1));

        ImageButton _button = new ImageButton(context);
        _button.setImageResource(android.R.drawable.arrow_down_float);
        _button.setOnClickListener(v -> {
            if (!spinnerList.contains(getText())) {
                ViewUtil.hideKeyboard(mContext, _text);
                setSuggestionSource(spinnerList); // rest to user supplied list
            }
            _text.showDropDown();
        });
        this.addView(_button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * Sets the source for DDLB suggestions. Cursor MUST be managed by supplier!!
     *
     * @param source Source of suggestions.
     * @param column Which column from source to show.
     */
    public void setSuggestionSource(Cursor source, String column)
    {
        String[] from = new String[]{column};
        int[] to = new int[]{android.R.id.text1};
        SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(this.getContext(),
                R.layout.simple_spinner_dropdown_item, source, from, to);

        // this is to ensure that when suggestion is selected it provides the value to the textBox
        cursorAdapter.setStringConversionColumn(source.getColumnIndex(column));
        _text.setAdapter(cursorAdapter);
    }

    public void setSuggestionSource(List<String> list)
    {
        spinnerList = list;

        // Create an ArrayAdapter using the string array and custom spinner item with radio button
        ArrayAdapter<String> mAdapter = new ArrayAdapter<String>(this.getContext(), R.layout.simple_spinner_item, list)
        {
            // Allow to change font style in dropdown vew
            public View getView(int position, View convertView, @NonNull ViewGroup parent)
            {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.adapter_radio_item, null);
                }
                TextView name = convertView.findViewById(R.id.item_name);
                RadioButton radio = convertView.findViewById(R.id.item_radio);

                final String variation = list.get(position);
                name.setText(variation);

                int mSelected = list.indexOf(getText());
                radio.setChecked(position == mSelected);

                return convertView;
            }
        };

        // Specify the layout to use when the list of choices appears
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the ComboBox
        _text.setAdapter(mAdapter);
    }

    /**
     * Gets the text in the combo box.
     *
     * @return Text.
     */
    public String getText()
    {
        return ViewUtil.toString(_text);
    }

    /**
     * Sets the text in combo box.
     */
    public void setText(String text)
    {
        _text.setText(text);
    }

    /**
     * Sets the textSize in comboBox.
     */
    public void setTextSize(float size)
    {
        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /**
     * Sets the unit and textSize in comboBox.
     */
    public void setTextSize(int unit, float size)
    {
        _text.setTextSize(unit, size);
    }

    /**
     * Set the call back when an item in the combo box dropdown list item is selected
     *
     * @param l AdapterView OnItemClickListener
     */
    public void setOnItemClickListener(AdapterView.OnItemClickListener l)
    {
        _text.setOnItemClickListener(l);
    }
}
