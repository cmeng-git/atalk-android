/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.records;

import java.util.*;

/**
 * @author Alexander Pelov
 */
public class HistoryRecord
{
	/**
	 * Map containing the property-value pair
	 */
	private final Map<String, String> mProperties;

	private final Date mTimestamp;
	private final String[] mPropertyNames;
	private final String[] mPropertyValues;

	public HistoryRecord(Map<String, String> properties, String timestamp)
	{
		mProperties = properties;

		Long time = Long.parseLong(timestamp);
		mTimestamp = new Date(time);

		mPropertyNames = properties.keySet().toArray(new String[properties.size()]);
		mPropertyValues = properties.values().toArray(new String[properties.size()]);;
	}

	/**
	 * Constructs an entry containing multiple name-value pairs, where the names are taken from
	 * the defined structure. The timestamp is set to the time this object is created.
	 *
	 * @param entryStructure
	 * @param propertyValues
	 */
	public HistoryRecord(HistoryRecordStructure entryStructure, String[] propertyValues)
	{
		this(entryStructure.getPropertyNames(), propertyValues, new Date());
	}

	/**
	 * Constructs an entry containing multiple name-value pairs, where the name is not unique. The
	 * timestamp is set to the time this object is created.
	 *
	 * @param propertyNames
	 * @param propertyValues
	 */
	public HistoryRecord(String[] propertyNames, String[] propertyValues)
	{
		this(propertyNames, propertyValues, new Date());
	}

	/**
	 * Constructs an entry containing multiple name-value pairs, where the names are taken from
	 * the defined structure.
	 *
	 * @param entryStructure
	 * @param propertyValues
	 * @param timestamp
	 */
	public HistoryRecord(HistoryRecordStructure entryStructure, String[] propertyValues,
			Date timestamp)
	{
		this(entryStructure.getPropertyNames(), propertyValues, timestamp);
	}


	/**
	 * Constructs an entry containing multiple name-value pairs, where the name is not unique.
	 *
	 * @param propertyNames
	 * @param propertyValues
	 * @param timestamp
	 */
	public HistoryRecord(String[] propertyNames, String[] propertyValues, Date timestamp)
	{
		// TODO: Validate: Assert.assertNonNull(propertyNames, "The property names should be
		// non-null.");
		// TODO: Validate: Assert.assertNonNull(mPropertyValues, "The property values should be
		// non-null.");
		// TODO: Validate: Assert.assertNonNull(timestamp, "The timestamp should be non-null.");

		// TODO: Validate Assert.assertTrue(propertyNames.length == mPropertyValues.length,
		// "The length of the property names and property values should be equal.");

		mPropertyNames = propertyNames;
		mPropertyValues = propertyValues;
		mTimestamp = timestamp;

		mProperties = new Hashtable<>();
		for (int i= 0; i < propertyNames.length; i++) {
			mProperties.put(propertyNames[i], propertyValues[i]);
		}
	}

	public Map<String, String> getProperties()
	{
		return mProperties;
	}

	public String[] getPropertyNames()
	{
		return mPropertyNames;
	}

	public String[] getPropertyValues()
	{
		return this.mPropertyValues;
	}

	public Date getTimestamp()
	{
		return mTimestamp;
	}

	/**
	 * Returns the String representation of this HistoryRecord.
	 *
	 * @return the String representation of this HistoryRecord
	 */
	@Override
	public String toString()
	{
		StringBuilder s = new StringBuilder("History Record: ");

		for (int i = 0; i < mPropertyNames.length; i++) {
			s.append(mPropertyNames[i]);
			s.append('=');
			s.append(mPropertyValues[i]);
			s.append('\n');
		}
		return s.toString();
	}
}
