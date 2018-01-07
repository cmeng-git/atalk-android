/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * The supported proxy types and properties used to store the values in the configuration service.
 *
 * @author Damian Minkov
 */
public class ProxyInfoX
{
	/**
	 * Enum which stores possible proxy types
	 */
	public static enum ProxyType {
		/**
		 * Proxy is not used.
		 */
		NONE,
		/**
		 * HTTP proxy type.
		 */
		HTTP,
		/**
		 * Proxy type socks4.
		 */
		SOCKS4,
		/**
		 * Proxy type socks5.
		 */
		SOCKS5
	}

	/**
	 * Stores in the configuration the connection proxy type.
	 */
	public final static String CONNECTION_PROXY_TYPE_PROPERTY_NAME = "connectionProxyType";

	/**
	 * Stores in the configuration the connection proxy address.
	 */
	public final static String CONNECTION_PROXY_ADDRESS_PROPERTY_NAME = "connectionProxyAddress";

	/**
	 * Stores in the configuration the connection proxy port.
	 */
	public final static String CONNECTION_PROXY_PORT_PROPERTY_NAME = "connectionProxyPort";

	/**
	 * Stores in the configuration the connection proxy username.
	 */
	public final static String CONNECTION_PROXY_USERNAME_PROPERTY_NAME = "connectionProxyUsername";

	/**
	 * Stores in the configuration the connection proxy password.
	 */
	public final static String CONNECTION_PROXY_PASSWORD_PROPERTY_NAME = "connectionProxyPassword";

	/**
	 * Stores in the configuration the connection dns forwarding is it enabled.
	 */
	public final static String CONNECTION_PROXY_FORWARD_DNS_PROPERTY_NAME
			= "connectionProxyForwardDNS";

	/**
	 * Stores in the configuration the connection dns forwarding address.
	 */
	public final static String CONNECTION_PROXY_FORWARD_DNS_ADDRESS_PROPERTY_NAME
			= "connectionProxyForwardDNSAddress";

	/**
	 * Stores in the configuration the connection dns forwarding port.
	 */
	public final static String CONNECTION_PROXY_FORWARD_DNS_PORT_PROPERTY_NAME
			= "connectionProxyForwardDNSPort";
}
