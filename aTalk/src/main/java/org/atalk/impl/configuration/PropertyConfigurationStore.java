/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Implements a <code>ConfigurationStore</code> which stores property name-value associations in a
 * <code>Properties</code> instance and supports its serialization format for the configuration file of
 * <code>ConfigurationServiceImpl</code>. Because of the <code>Properties</code> backend which can
 * associate names only <code>String</code> values, instances of <code>PropertyConfigurationStore</code>
 * convert property values to <code>String</code> using <code>Object#toString()</code>.
 *
 * @author Lyubomir Marinov
 */
public class PropertyConfigurationStore extends HashtableConfigurationStore<Properties>
{
	/**
	 * Initializes a new <code>PropertyConfigurationStore</code> instance.
	 */
	public PropertyConfigurationStore()
	{
		super(new SortedProperties());
	}

	/**
	 * Implements {@link ConfigurationStore#reloadConfiguration(File)}. Removes all property
	 * name-value associations currently present in this <code>ConfigurationStore</code> and
	 * de-serializes new property name-value associations from a specific <code>File</code> which
	 * presumably is in the format represented by this instance.
	 *
	 * @param file
	 * 		the <code>File</code> to be read and to deserialize new property name-value associations
	 * 		from into this instance
	 * @throws IOException
	 * 		if there is an input error while reading from the specified <code>file</code>
	 * @see ConfigurationStore#reloadConfiguration(File)
	 */
	public void reloadConfiguration(File file)
			throws IOException
	{
		properties.clear();

		InputStream in = new BufferedInputStream(new FileInputStream(file));
		try {
			properties.load(in);
		} finally {
			in.close();
		}
	}

	/**
	 * Overrides {@link HashtableConfigurationStore#setNonSystemProperty(String, Object)}. As the
	 * backend of this instance is a <code>Properties</code> instance, it can only store
	 * <code>String</code> values and the specified value to be associated with the specified
	 * property name is converted to a <code>String</code>.
	 *
	 * @param name
	 * 		the name of the non-system property to be set to the specified value in this
	 * 		<code>ConfigurationStore</code>
	 * @param value
	 * 		the value to be assigned to the non-system property with the specified name in this
	 * 		<code>ConfigurationStore</code>
	 * @see ConfigurationStore#setNonSystemProperty(String, Object)
	 */
	@Override
	public void setNonSystemProperty(String name, Object value)
	{
		properties.setProperty(name, value.toString());
	}

	/**
	 * Implements {@link ConfigurationStore#storeConfiguration(OutputStream)}. Stores/serializes
	 * the property name-value associations currently present in this <code>ConfigurationStore</code>
	 * into a specific <code>OutputStream</code> in the format represented by this instance.
	 *
	 * @param out
	 * 		the <code>OutputStream</code> to receive the serialized form of the property name-value
	 * 		associations currently present in this <code>ConfigurationStore</code>
	 * @throws IOException
	 * 		if there is an output error while storing the properties managed by this
	 * 		<code>ConfigurationStore</code> into the specified <code>file</code>
	 * @see ConfigurationStore#storeConfiguration(OutputStream)
	 */
	public void storeConfiguration(OutputStream out)
			throws IOException
	{
		properties.store(out, null);
	}
}
