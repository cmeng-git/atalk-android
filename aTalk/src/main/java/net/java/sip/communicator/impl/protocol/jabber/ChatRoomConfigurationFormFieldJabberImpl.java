/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ChatRoomConfigurationFormField;

import org.jivesoftware.smackx.xdata.*;
import org.jivesoftware.smackx.xdata.FormField.Type;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jivesoftware.smackx.xdata.form.Form;

import java.util.*;

/**
 * The Jabber protocol implementation of the <tt>ChatRoomConfigurationFormField</tt>. This
 * implementation is based on the smack Form and FormField types.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatRoomConfigurationFormFieldJabberImpl implements ChatRoomConfigurationFormField
{
    /**
     * The smack library FormField.
     */
    private final FormField smackFormField;

    /**
     * The smack library submit form field. It's the one that will care all values set by user,
     * before submitting the form.
     */
    private FormField smackSubmitFormField;

    /**
     * Creates an instance of <tt>ChatRoomConfigurationFormFieldJabberImpl</tt> by passing to it the
     * smack form field and the smack submit form, which are the base of this implementation.
     *
     * @param formField the smack form field
     * @param submitForm the smack submit form.
     */
    public ChatRoomConfigurationFormFieldJabberImpl(FormField formField, FillableForm submitForm)
    {
        this.smackFormField = formField;

        if (formField.getType() != FormField.Type.fixed)
            this.smackSubmitFormField = submitForm.getField(formField.getFieldName());
        else
            this.smackSubmitFormField = null;
    }

    /**
     * Returns the variable name of the corresponding smack property.
     *
     * @return the variable name of the corresponding smack property.
     */
    public String getName()
    {
        return smackFormField.getFieldName();
    }

    /**
     * Returns the description of the corresponding smack property.
     *
     * @return the description of the corresponding smack property.
     */
    public String getDescription()
    {
        return smackFormField.getDescription();
    }

    /**
     * Returns the label of the corresponding smack property.
     *
     * @return the label of the corresponding smack property.
     */
    public String getLabel()
    {
        return smackFormField.getLabel();
    }

    /**
     * Returns the options of the corresponding smack property.
     *
     * @return the options of the corresponding smack property.
     */
    public Iterator<String> getOptions()
    {
        List<String> options = new ArrayList<>();
        List<FormField.Option> ffOptions = ((FormFieldWithOptions) smackFormField).getOptions();

        for (FormField.Option smackOption : ffOptions) {
            options.add(smackOption.getValueString());
        }
        return Collections.unmodifiableList(options).iterator();
    }

    /**
     * Returns the isRequired property of the corresponding smack property.
     *
     * @return the isRequired property of the corresponding smack property.
     */
    public boolean isRequired()
    {
        return smackFormField.isRequired();
    }

    /**
     * For each of the smack form field types returns the corresponding
     * <tt>ChatRoomConfigurationFormField</tt> type.
     *
     * @return the type of the property
     */
    public String getType()
    {
        Type smackType = smackFormField.getType();
        switch (smackType) {
            case bool:
                return TYPE_BOOLEAN;
            case fixed:
                return TYPE_TEXT_FIXED;
            case text_private:
                return TYPE_TEXT_PRIVATE;
            case text_single:
                return TYPE_TEXT_SINGLE;
            case text_multi:
                return TYPE_TEXT_MULTI;
            case list_single:
                return TYPE_LIST_SINGLE;
            case list_multi:
                return TYPE_LIST_MULTI;
            case jid_single:
                return TYPE_ID_SINGLE;
            case jid_multi:
                return TYPE_ID_MULTI;
            default:
                return TYPE_UNDEFINED;
        }
    }

    /**
     * Returns an Iterator over the list of values of this field.
     *
     * @return an Iterator over the list of values of this field.
     */
    public Iterator<?> getValues()
    {
        List<String> smackValues = smackFormField.getValuesAsString();
        Iterator<?> valuesIter;

        if (smackFormField.getType() == FormField.Type.bool) {
            List<Boolean> values = new ArrayList<>();

            for (String smackValue : smackValues) {
                values.add((smackValue.equals("1") || smackValue.equals("true")) ? Boolean.TRUE : Boolean.FALSE);
            }
            valuesIter = values.iterator();
        }
        else
            valuesIter = smackValues.iterator();
        return valuesIter;
    }

    /**
     * Adds the given value to the list of values of this field.
     *
     * @param value the value to add
     */
    public void addValue(Object value)
    {
        if (value instanceof Boolean)
            value = (Boolean) value ? "1" : "0";

        TextSingleFormField.Builder fieldBuilder = ((TextSingleFormField) smackSubmitFormField).asBuilder();
        fieldBuilder.setValue((String) value);
        smackSubmitFormField = fieldBuilder.build();
    }

    /**
     * Sets the given list of values to this field.
     *
     * @param newValues the list of values to set.
     */
    public void setValues(Object[] newValues)
    {
        List<String> list = new ArrayList<>();
        for (Object value : newValues) {
            String stringValue;
            if (value instanceof Boolean)
                stringValue = (Boolean) value ? "1" : "0";
            else
                stringValue = (value == null) ? null : value.toString();
            list.add(stringValue);
        }
        // smackSubmitFormField.addValues(list);
        ListMultiFormField.Builder fieldBuilder = ((ListMultiFormField) smackSubmitFormField).asBuilder();
        fieldBuilder.addValues(list);
        smackSubmitFormField = fieldBuilder.build();
    }
}
