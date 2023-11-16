/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration;

import org.atalk.util.xml.XMLException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;

/**
 * @author Lyubomir Marinov
 */
@SuppressWarnings("rawtypes")
public abstract class DatabaseConfigurationStore extends HashtableConfigurationStore<Hashtable>
{
	/**
	 * Initializes a new <code>DatabaseConfigurationStore</code> instance.
	 */
	protected DatabaseConfigurationStore()
	{
		this(new Hashtable());
	}

	/**
	 * Initializes a new <code>DatabaseConfigurationStore</code> instance with a specific runtime
	 * <code>HashTable</code> storage.
	 *
	 * @param properties
	 * 		the <code>HashTable</code> which is to become the runtime storage of the new instance
	 */
	protected DatabaseConfigurationStore(Hashtable properties)
	{
		super(properties);
	}

	/**
	 * Removes all property name-value associations currently present in this
	 * <code>ConfigurationStore</code> instance and de-serializes new property name-value
	 * associations from its underlying database (storage).
	 *
	 * @throws IOException
	 * 		if there is an input error while reading from the underlying database (storage)
	 */
	protected abstract void reloadConfiguration()
			throws IOException;

	/**
	 * Removes all property name-value associations currently present in this
	 * <code>ConfigurationStore</code> and de-serializes new property name-value associations from a
	 * specific <code>File</code> which presumably is in the format represented by this instance.
	 *
	 * @param file
	 * 		the <code>File</code> to be read and to deserialize new property name-value associations
	 * 		from into this instance
	 * @throws IOException
	 * 		if there is an input error while reading from the specified <code>file</code>
	 * @throws XMLException
	 * 		if parsing the contents of the specified <code>file</code> fails
	 * @see ConfigurationStore#reloadConfiguration(File)
	 */
	public void reloadConfiguration(File file)
			throws IOException, XMLException
	{
		properties.clear();
		reloadConfiguration();
	}

	/**
	 * Stores/serializes the property name-value associations currently present in this
	 * <code>ConfigurationStore</code> instance into its underlying database (storage).
	 *
	 * @throws IOException
	 * 		if there is an output error while storing the properties managed by this
	 * 		<code>ConfigurationStore</code>
	 * 		instance into its underlying database (storage)
	 */
	protected void storeConfiguration()
			throws IOException
	{
	}

	/**
	 * Stores/serializes the property name-value associations currently present in this
	 * <code>ConfigurationStore</code> into a specific <code>OutputStream</code> in the format
	 * represented by this instance.
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
		storeConfiguration();
	}
}
