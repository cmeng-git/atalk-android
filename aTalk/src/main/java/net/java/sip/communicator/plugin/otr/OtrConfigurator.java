/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import org.atalk.service.configuration.ConfigurationService;
import org.bouncycastle.util.encoders.Base64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class that gets/sets the OTR configuration values. Introduced to assure our
 * configuration is properly written when <code>XMLConfigurationStore</code> is
 * used. Can be seen as a proxy between the {@link ConfigurationService} and the
 * OTR Plugin.
 *
 * @author George Politis
 */
public class OtrConfigurator {
	/**
	 * Gets an XML tag friendly {@link String} from a {@link String}.
	 *
	 * @param s a {@link String}
	 * @return an XML friendly {@link String}
	 */
	private String getXmlFriendlyString(String s) {
		if (s == null || s.length() < 1)
			return s;

		// XML Tags are not allowed to start with digits,
		// insert a dummy "p" char.
		if (Character.isDigit(s.charAt(0)))
			s = "p" + s;

		char[] cId = new char[s.length()];

		for (int i = 0; i < cId.length; i++) {
			char c = s.charAt(i);
			cId[i] = Character.isLetterOrDigit(c) ? c : '_';
		}
		return new String(cId);
	}

	/**
	 * Puts a given property ID under the OTR namespace and makes sure it is XML
	 * tag friendly.
	 *
	 * @param id the property ID.
	 * @return the database defined ID.
	 */
	private String getID(String id) {
		return "otr." + id;
	}

	/**
	 * Returns the value of the property with the specified name or null if no
	 * such property exists ({@link ConfigurationService#getProperty(String)}
	 * proxy).
	 *
	 * @param id of the property that is being queried.
	 * @return the <code>byte[]</code> value of the property with the specified name.
	 */
	public byte[] getPropertyBytes(String id) {
		String value = OtrActivator.configService.getString(getID(id));
		return (value == null) ? null : Base64.decode(value.getBytes());
	}

	/**
	 * Gets the value of a specific property as a boolean (
	 * {@link ConfigurationService#getBoolean(String, boolean)} proxy).
	 *
	 * @param id of the property that is being queried.
	 * @param defaultValue the value to be returned if the specified property
	 * name is not associated with a value.
	 * @return the <code>Boolean</code> value of the property with the specified name.
	 */
	public boolean getPropertyBoolean(String id, boolean defaultValue) {
		return OtrActivator.configService.getBoolean(getID(id), defaultValue);
	}

	/**
	 * Sets the property with the specified name to the specified value (
	 * {@link ConfigurationService#setProperty(String, Object)} proxy). The
	 * value is Base64 encoded.
	 *
	 * @param id the name of the property to change.
	 * @param value the new value of the specified property.
	 */
	public void setProperty(String id, byte[] value) {
		String valueToStore = new String(Base64.encode(value));
		OtrActivator.configService.setProperty(getID(id), valueToStore);
	}

	/**
	 * Sets the property with the specified name to the specified value (
	 * {@link ConfigurationService#setProperty(String, Object)} proxy).
	 *
	 * @param id the name of the property to change.
	 * @param value the new value of the specified property.
	 */
	public void setProperty(String id, Object value) {
		OtrActivator.configService.setProperty(getID(id), value);
	}

	/**
	 * Removes the property with the specified name (
	 * {@link ConfigurationService#removeProperty(String)} proxy).
	 *
	 * @param id the name of the property to change.
	 */
	public void removeProperty(String id) {
		OtrActivator.configService.removeProperty(getID(id));
	}

	/**
	 * Gets the value of a specific property as a signed decimal integer.
	 *
	 * @param id the name of the property to change.
	 * @param defaultValue the value to be returned if the specified property
	 * name is not associated with a value.
	 * @return the <code>int</code> value of the property
	 */
	public int  getPropertyInt(String id, int defaultValue) {
		return OtrActivator.configService.getInt(getID(id), defaultValue);
	}

	/**
	 * Appends <code>value</code> to the old value of the property with the
	 * specified name. The two values will be comma separated.
	 *
	 * @param id the name of the property to append to
	 * @param value the value to append
	 */
	public void appendProperty(String id, Object value) {
		Object oldValue = OtrActivator.configService.getProperty(getID(id));
		String newValue = oldValue == null ? value.toString() : oldValue + "," + value;
		setProperty(id, newValue);
	}

	public List<String> getAppendedProperties(String id) {
		String listProperties = (String) OtrActivator.configService.getProperty(getID(id));

		if (listProperties == null) return new ArrayList<String>();

		return Arrays.asList(listProperties.split(","));
	}
}
