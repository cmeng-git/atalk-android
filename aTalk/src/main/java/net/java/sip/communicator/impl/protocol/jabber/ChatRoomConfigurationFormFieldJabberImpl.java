/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.protocol.ChatRoomConfigurationFormField;

import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.FormField.Type;

/**
 * The Jabber protocol implementation of the <tt>ChatRoomConfigurationFormField</tt>. This
 * implementation is based on the smack Form and FormField types.
 *
 * @author Yana Stamcheva
 */
public class ChatRoomConfigurationFormFieldJabberImpl implements ChatRoomConfigurationFormField
{
	/**
	 * The smack library for field.
	 */
	private final FormField smackFormField;

	/**
	 * The smack library submit form field. It's the one that will care all values set by user,
	 * before submitting the form.
	 */
	private final FormField smackSubmitFormField;

	/**
	 * Creates an instance of <tt>ChatRoomConfigurationFormFieldJabberImpl</tt> by passing to it the
	 * smack form field and the smack submit form, which are the base of this implementation.
	 *
	 * @param formField
	 *        the smack form field
	 * @param submitForm
	 *        the smack submit form
	 */
	public ChatRoomConfigurationFormFieldJabberImpl(FormField formField, Form submitForm)
	{
		this.smackFormField = formField;

		if (formField.getType() != FormField.Type.fixed)
			this.smackSubmitFormField = submitForm.getField(formField.getVariable());
		else
			this.smackSubmitFormField = null;
	}

	/**
	 * Returns the variable name of the corresponding smack property.
	 *
	 * @return the variable name of the corresponding smack property
	 */
	public String getName()
	{
		return smackFormField.getVariable();
	}

	/**
	 * Returns the description of the corresponding smack property.
	 *
	 * @return the description of the corresponding smack property
	 */
	public String getDescription()
	{
		return smackFormField.getDescription();
	}

	/**
	 * Returns the label of the corresponding smack property.
	 *
	 * @return the label of the corresponding smack property
	 */
	public String getLabel()
	{
		return smackFormField.getLabel();
	}

	/**
	 * Returns the options of the corresponding smack property.
	 *
	 * @return the options of the corresponding smack property
	 */
	public Iterator<String> getOptions()
	{
		List<String> options = new ArrayList<String>();
		List<FormField.Option> smackOptions = smackFormField.getOptions();

		for (FormField.Option smackOption : smackOptions) {
			options.add(smackOption.getValue());
		}
		return Collections.unmodifiableList(options).iterator();
	}

	/**
	 * Returns the isRequired property of the corresponding smack property.
	 *
	 * @return the isRequired property of the corresponding smack property
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

		if (smackType == Type.bool)
			return TYPE_BOOLEAN;
		else if (smackType == Type.fixed)
			return TYPE_TEXT_FIXED;
		else if (smackType == Type.text_private)
			return TYPE_TEXT_PRIVATE;
		else if (smackType == Type.text_single)
			return TYPE_TEXT_SINGLE;
		else if (smackType == Type.text_multi)
			return TYPE_TEXT_MULTI;
		else if (smackType == Type.list_single)
			return TYPE_LIST_SINGLE;
		else if (smackType == Type.list_multi)
			return TYPE_LIST_MULTI;
		else if (smackType == Type.jid_single)
			return TYPE_ID_SINGLE;
		else if (smackType == Type.jid_multi)
			return TYPE_ID_MULTI;
		else
			return TYPE_UNDEFINED;
	}

	/**
	 * Returns an Iterator over the list of values of this field.
	 *
	 * @return an Iterator over the list of values of this field
	 */
	public Iterator<?> getValues()
	{
		List<String> smackValues = smackFormField.getValues();
		Iterator<?> valuesIter;

		if (smackFormField.getType() == FormField.Type.bool) {
			List<Boolean> values = new ArrayList<Boolean>();

			for (String smackValue : smackValues) {
				values.add((smackValue.equals("1") || smackValue.equals("true")) ? Boolean.TRUE
					: Boolean.FALSE);
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
	 * @param value
	 *        the value to add
	 */
	public void addValue(Object value)
	{
		if (value instanceof Boolean)
			value = ((Boolean) value).booleanValue() ? "1" : "0";

		smackSubmitFormField.addValue(value.toString());
	}

	/**
	 * Sets the given list of values to this field.
	 *
	 * @param newValues
	 *        the list of values to set
	 */
	public void setValues(Object[] newValues)
	{
		List<String> list = new ArrayList<String>();

		for (Object value : newValues) {
			String stringValue;

			if (value instanceof Boolean)
				stringValue = ((Boolean) value).booleanValue() ? "1" : "0";
			else
				stringValue = (value == null) ? null : value.toString();

			list.add(stringValue);
		}
		smackSubmitFormField.addValues(list);
	}
}
