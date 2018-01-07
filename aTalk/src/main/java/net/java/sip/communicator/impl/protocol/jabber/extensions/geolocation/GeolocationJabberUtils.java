/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.geolocation;

import net.java.sip.communicator.service.protocol.OperationSetGeolocation;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smackx.geoloc.packet.GeoLocation;

import java.lang.reflect.*;
import java.text.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class give some static methods for converting a geolocation message to a different format.
 *
 * @author Guillaume Schreiner
 * @author Eng Chong Meng
 */
public class GeolocationJabberUtils
{
	/**
	 * The logger of this class.
	 */
	private static final Logger logger = Logger.getLogger(GeolocationJabberUtils.class);

	/**
	 * Convert geolocation from GeolocationExtension format to Map format
	 *
	 * @param geoLocation
	 *        the GeolocationExtension XML message
	 * @return a Map with geolocation information
	 */
	public static Map<String, String> convertExtensionToMap(GeoLocation geoLocation)
	{
		Map<String, String> geoLocMap = new Hashtable<String, String>();

		addDoubleToMap(geoLocMap, OperationSetGeolocation.ALT, geoLocation.getAlt());

		addStringToMap(geoLocMap, OperationSetGeolocation.AREA, geoLocation.getArea());

		addDoubleToMap(geoLocMap, OperationSetGeolocation.BEARING, geoLocation.getBearing());

		addStringToMap(geoLocMap, OperationSetGeolocation.BUILDING, geoLocation.getBuilding());

		addStringToMap(geoLocMap, OperationSetGeolocation.COUNTRY, geoLocation.getCountry());

		addStringToMap(geoLocMap, OperationSetGeolocation.DATUM, geoLocation.getDatum());

		addStringToMap(geoLocMap, OperationSetGeolocation.DESCRIPTION, geoLocation.getDescription());

		addDoubleToMap(geoLocMap, OperationSetGeolocation.ERROR, geoLocation.getError());

		addStringToMap(geoLocMap, OperationSetGeolocation.FLOOR, geoLocation.getFloor());

		addDoubleToMap(geoLocMap, OperationSetGeolocation.LAT, geoLocation.getLat());

		addStringToMap(geoLocMap, OperationSetGeolocation.LOCALITY, geoLocation.getLocality());

		addDoubleToMap(geoLocMap, OperationSetGeolocation.LON, geoLocation.getLon());

		addStringToMap(geoLocMap, OperationSetGeolocation.POSTALCODE, geoLocation.getPostalcode());

		addStringToMap(geoLocMap, OperationSetGeolocation.REGION, geoLocation.getRegion());

		addStringToMap(geoLocMap, OperationSetGeolocation.ROOM, geoLocation.getRoom());

		addStringToMap(geoLocMap, OperationSetGeolocation.STREET, geoLocation.getStreet());

		addStringToMap(geoLocMap, OperationSetGeolocation.TEXT, geoLocation.getText());

		addDateToMap(geoLocMap, OperationSetGeolocation.TIMESTAMP, geoLocation.getTimestamp());

		return geoLocMap;
	}

	/**
	 * Utility function for adding a float var to a Map.
	 *
	 * @param map
	 *        the map we're adding a new value to.
	 * @param key
	 *        the key that we're adding the new value against
	 * @param value
	 *        the float var that we're adding to <tt>map</tt> against the <tt>key</tt> key.
	 */
	private static void addDoubleToMap(Map<String, String> map, String key, double value)
	{
		if (value != -1) {
			Double valor = value;
			map.put(key, valor.toString());
		}
	}

	/**
	 * Utility function that we use when adding a String to a map ()
	 * 
	 * @param map
	 *        Map
	 * @param key
	 *        String
	 * @param value
	 *        String
	 */
	private static void addStringToMap(Map<String, String> map, String key, String value)
	{
		if (value != null) {
			map.put(key, value);
		}
	}

	/**
	 * Utility function for adding a float var to a Map.
	 *
	 * @param map
	 *        the map we're adding a new value to.
	 * @param key
	 *        the key that we're adding the new value against
	 * @param value
	 *        the timeStamp var that we're adding to <tt>map</tt> against the <tt>key</tt> key.
	 */
	private static void addDateToMap(Map<String, String> map, String key, Date value)
	{
		DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);

		if (value != null) {
			String date = df.format(value);
			map.put(key, date);
		}
	}

	/**
	 * Convert a geolocation details Map to a GeolocationPacketExtension format
	 *
	 * @param geolocation
	 *        a Map with geolocation information
	 * @return a GeolocationExtension ready to be included into a Jabber message
	 */
	public static GeoLocation convertMapToExtension(Map<String, String> geolocation)
	{
		GeoLocation.Builder geoInfo = new GeoLocation.Builder();
		Set<Entry<String, String>> geoEntries = geolocation.entrySet();

		for (Entry geoEntry: geoEntries) {
			String curParam = (String) geoEntry.getKey();
			String curValue = (String) geoEntry.getValue();

			String prototype = Character.toUpperCase(curParam.charAt(0)) + curParam.substring(1);
			String setterFunction = "set" + prototype;

			try {

				Method toCall = GeoLocation.class.getMethod(setterFunction, String.class);
				Object[] arguments = new Object[] { curValue };

				try {
					toCall.invoke(geoInfo, arguments);
				}
				catch (IllegalArgumentException | IllegalAccessException
						| InvocationTargetException exc) {
					logger.error(exc);
				}
			}
			catch (SecurityException | NoSuchMethodException exc) {
				logger.error(exc);
			}
		}
		return geoInfo.build();
	}
}
