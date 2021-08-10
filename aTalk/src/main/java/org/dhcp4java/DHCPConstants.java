/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 * (c) 2006 Stephan Hadinger
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.dhcp4java;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class holding all DHCP constants.
 * 
 * @author Stephan Hadinger
 * @version 1.00
 */
public final class DHCPConstants {

    // Suppresses default constructor, ensuring non-instantiability.
	private DHCPConstants() {
		throw new UnsupportedOperationException();
	}
	
    // ========================================================================
    // DHCP Constants

    /** DHCP BOOTP CODES **/
    public static final byte BOOTREQUEST    = 1;
    public static final byte BOOTREPLY	    = 2;

    /** DHCP HTYPE CODES **/
    public static final byte HTYPE_ETHER	= 1;
    public static final byte HTYPE_IEEE802	= 6;
    public static final byte HTYPE_FDDI		= 8;
    public static final byte HTYPE_IEEE1394	= 24;	// rfc 2855

    /** DHCP MESSAGE CODES **/
    public static final byte DHCPDISCOVER   =  1;
    public static final byte DHCPOFFER      =  2;
    public static final byte DHCPREQUEST    =  3;
    public static final byte DHCPDECLINE    =  4;
    public static final byte DHCPACK        =  5;
    public static final byte DHCPNAK        =  6;
    public static final byte DHCPRELEASE    =  7;
    public static final byte DHCPINFORM     =  8;
    public static final byte DHCPFORCERENEW =  9;
    public static final byte DHCPLEASEQUERY = 10; // RFC 4388
    public static final byte DHCPLEASEUNASSIGNED = 11; // RFC 4388
    public static final byte DHCPLEASEUNKNOWN = 12; // RFC 4388
    public static final byte DHCPLEASEACTIVE = 13; // RFC 4388

    /** DHCP OPTIONS CODE **/
    public static final byte DHO_PAD                          =   0;
    public static final byte DHO_SUBNET_MASK                  =   1;
    public static final byte DHO_TIME_OFFSET                  =   2;
    public static final byte DHO_ROUTERS                      =   3;
    public static final byte DHO_TIME_SERVERS                 =   4;
    public static final byte DHO_NAME_SERVERS                 =   5;
    public static final byte DHO_DOMAIN_NAME_SERVERS          =   6;
    public static final byte DHO_LOG_SERVERS                  =   7;
    public static final byte DHO_COOKIE_SERVERS               =   8;
    public static final byte DHO_LPR_SERVERS                  =   9;
    public static final byte DHO_IMPRESS_SERVERS              =  10;
    public static final byte DHO_RESOURCE_LOCATION_SERVERS    =  11;
    public static final byte DHO_HOST_NAME                    =  12;
    public static final byte DHO_BOOT_SIZE                    =  13;
    public static final byte DHO_MERIT_DUMP                   =  14;
    public static final byte DHO_DOMAIN_NAME                  =  15;
    public static final byte DHO_SWAP_SERVER                  =  16;
    public static final byte DHO_ROOT_PATH                    =  17;
    public static final byte DHO_EXTENSIONS_PATH              =  18;
    public static final byte DHO_IP_FORWARDING                =  19;
    public static final byte DHO_NON_LOCAL_SOURCE_ROUTING     =  20;
    public static final byte DHO_POLICY_FILTER                =  21;
    public static final byte DHO_MAX_DGRAM_REASSEMBLY         =  22;
    public static final byte DHO_DEFAULT_IP_TTL               =  23;
    public static final byte DHO_PATH_MTU_AGING_TIMEOUT       =  24;
    public static final byte DHO_PATH_MTU_PLATEAU_TABLE       =  25;
    public static final byte DHO_INTERFACE_MTU                =  26;
    public static final byte DHO_ALL_SUBNETS_LOCAL            =  27;
    public static final byte DHO_BROADCAST_ADDRESS            =  28;
    public static final byte DHO_PERFORM_MASK_DISCOVERY       =  29;
    public static final byte DHO_MASK_SUPPLIER                =  30;
    public static final byte DHO_ROUTER_DISCOVERY             =  31;
    public static final byte DHO_ROUTER_SOLICITATION_ADDRESS  =  32;
    public static final byte DHO_STATIC_ROUTES                =  33;
    public static final byte DHO_TRAILER_ENCAPSULATION        =  34;
    public static final byte DHO_ARP_CACHE_TIMEOUT            =  35;
    public static final byte DHO_IEEE802_3_ENCAPSULATION      =  36;
    public static final byte DHO_DEFAULT_TCP_TTL              =  37;
    public static final byte DHO_TCP_KEEPALIVE_INTERVAL       =  38;
    public static final byte DHO_TCP_KEEPALIVE_GARBAGE        =  39;
    public static final byte DHO_NIS_SERVERS                  =  41;
    public static final byte DHO_NTP_SERVERS                  =  42;
    public static final byte DHO_VENDOR_ENCAPSULATED_OPTIONS  =  43;
    public static final byte DHO_NETBIOS_NAME_SERVERS         =  44;
    public static final byte DHO_NETBIOS_DD_SERVER            =  45;
    public static final byte DHO_NETBIOS_NODE_TYPE            =  46;
    public static final byte DHO_NETBIOS_SCOPE                =  47;
    public static final byte DHO_FONT_SERVERS                 =  48;
    public static final byte DHO_X_DISPLAY_MANAGER            =  49;
    public static final byte DHO_DHCP_REQUESTED_ADDRESS       =  50;
    public static final byte DHO_DHCP_LEASE_TIME              =  51;
    public static final byte DHO_DHCP_OPTION_OVERLOAD         =  52;
    public static final byte DHO_DHCP_MESSAGE_TYPE            =  53;
    public static final byte DHO_DHCP_SERVER_IDENTIFIER       =  54;
    public static final byte DHO_DHCP_PARAMETER_REQUEST_LIST  =  55;
    public static final byte DHO_DHCP_MESSAGE                 =  56;
    public static final byte DHO_DHCP_MAX_MESSAGE_SIZE        =  57;
    public static final byte DHO_DHCP_RENEWAL_TIME            =  58;
    public static final byte DHO_DHCP_REBINDING_TIME          =  59;
    public static final byte DHO_VENDOR_CLASS_IDENTIFIER      =  60;
    public static final byte DHO_DHCP_CLIENT_IDENTIFIER       =  61;
    public static final byte DHO_NWIP_DOMAIN_NAME             =  62; // rfc 2242
    public static final byte DHO_NWIP_SUBOPTIONS              =  63; // rfc 2242
    public static final byte DHO_NISPLUS_DOMAIN               =  64;
    public static final byte DHO_NISPLUS_SERVER               =  65;
    public static final byte DHO_TFTP_SERVER                  =  66;
    public static final byte DHO_BOOTFILE                     =  67;
    public static final byte DHO_MOBILE_IP_HOME_AGENT         =  68;
    public static final byte DHO_SMTP_SERVER                  =  69;
    public static final byte DHO_POP3_SERVER                  =  70;
    public static final byte DHO_NNTP_SERVER                  =  71;
    public static final byte DHO_WWW_SERVER                   =  72;
    public static final byte DHO_FINGER_SERVER                =  73;
    public static final byte DHO_IRC_SERVER                   =  74;
    public static final byte DHO_STREETTALK_SERVER            =  75;
    public static final byte DHO_STDA_SERVER                  =  76;
    public static final byte DHO_USER_CLASS                   =  77; // rfc 3004
    public static final byte DHO_FQDN                         =  81;
    public static final byte DHO_DHCP_AGENT_OPTIONS           =  82; // rfc 3046
    public static final byte DHO_NDS_SERVERS                  =  85; // rfc 2241
    public static final byte DHO_NDS_TREE_NAME                =  86; // rfc 2241
    public static final byte DHO_NDS_CONTEXT					 =  87; // rfc 2241
    public static final byte DHO_CLIENT_LAST_TRANSACTION_TIME =  91; // rfc 4388
    public static final byte DHO_ASSOCIATED_IP				 =  92; // rfc 4388
    public static final byte DHO_USER_AUTHENTICATION_PROTOCOL =  98;
    public static final byte DHO_AUTO_CONFIGURE               = 116;
    public static final byte DHO_NAME_SERVICE_SEARCH          = 117; // rfc 2937
    public static final byte DHO_SUBNET_SELECTION             = 118; // rfc 3011
    public static final byte DHO_DOMAIN_SEARCH	             = 119; // rfc 3397
    public static final byte DHO_CLASSLESS_ROUTE				 = 121;	// rfc 3442
    public static final byte DHO_END                          =  -1;

    /** Any address */
    public static final InetAddress INADDR_ANY = getInaddrAny();
    /** Broadcast Address */
    public static final InetAddress INADDR_BROADCAST = getInaddrBroadcast();

    private static final InetAddress getInaddrAny() {
    	try {
    		final byte[] rawAddr = { (byte)0, (byte)0, (byte)0, (byte)0 };
    		return InetAddress.getByAddress(rawAddr);
    	} catch (UnknownHostException e) {
    		// bad luck
    		throw new IllegalStateException("Unable to generate INADDR_ANY");
    	}
    }
    private static final InetAddress getInaddrBroadcast() {
    	try {
            final byte[] rawAddr = { (byte) -1, (byte) -1, (byte) -1, (byte) -1 };
    		return InetAddress.getByAddress(rawAddr);
    	} catch (UnknownHostException e) {
    		// bad luck
    		throw new IllegalStateException("Unable to generate INADDR_BROADCAST");
    	}
    }
    
    /**
     * Returns a map associating a BootCode and the user-readable name.
     * 
     * <P>Currently:<br>
     * 	1=BOOTREQUEST<br>
     * 	2=BOOTREPLY
     * @return the map
     */
    public static final Map<Byte, String> getBootNamesMap() {
    	return _BOOT_NAMES;
    }
    
    /**
     * Returns a map associating a HType and the user-readable name.
     * 
     * <p>Ex: 1=HTYPE_ETHER
     * @return the map
     */
    public static final Map<Byte, String> getHtypesMap() {
    	return _HTYPE_NAMES;
    }

    /**
     * Returns a map associating a DHCP code and the user-readable name.
     * 
     * <p>ex: 1=DHCPDISCOVER
     * @return the map
     */
    public static final Map<Byte, String> getDhcpCodesMap() {
    	return _DHCP_CODES;
    }

    /**
     * Returns a map associating a DHCP option code and the user-readable name.
     * 
     * <p>ex: 1=DHO_SUBNET_MASK, 51=DHO_DHCP_LEASE_TIME, 
     * @return the map
     */
    public static final Map<Byte, String> getDhoNamesMap() {
    	return _DHO_NAMES;
    }

    /**
     * Returns a map associating a user-readable DHCP option name and the option code.
     * 
     * <p>ex: "DHO_SUBNET_MASK"=1, "DHO_DHCP_LEASE_TIME"=51 
     * @return the map
     */
    public static final Map<String, Byte> getDhoNamesReverseMap() {
    	return _DHO_NAMES_REV;
    }

    /**
     * Converts a DHCP option name into the option code.
     * @param name user-readable option name
     * @return the option code
     * @throws NullPointerException name is <tt>null</t>.
     */
    public static final Byte getDhoNamesReverse(String name) {
    	if (name == null) {
    		throw new NullPointerException();
    	}
    	return _DHO_NAMES_REV.get(name);
    }

    /**
     * Converts a DHCP code into a user-readable DHCP option name.
     * @param code DHCP option code
     * @return user-readable DHCP option name
     */
    public static final String getDhoName(byte code) {
    	return _DHO_NAMES.get(code);
    }
    
    // sanity check values
    static final int _DHCP_MIN_LEN           = 548;
    static final int _DHCP_DEFAULT_MAX_LEN   = 576;	// max default size for client
    static final int _BOOTP_ABSOLUTE_MIN_LEN = 236;
    static final int _DHCP_MAX_MTU           = 1500;
    static final int _DHCP_UDP_OVERHEAD      = 14 + 20 + 8;
    static final int _BOOTP_VEND_SIZE        = 64;
    
    // Magic cookie
    static final int _MAGIC_COOKIE = 0x63825363;
    
    public static final int BOOTP_REQUEST_PORT = 67;
    public static final int BOOTP_REPLY_PORT   = 68;

    // Maps for "code" to "string" conversion
    static final Map<Byte, String> _BOOT_NAMES;
    static final Map<Byte, String> _HTYPE_NAMES;
    static final Map<Byte, String> _DHCP_CODES;
    static final Map<Byte, String> _DHO_NAMES;
    static final Map<String, Byte> _DHO_NAMES_REV;

    /*
     * preload at startup Maps with constants
     * allowing reverse lookup
     */
    static {
    	Map<Byte, String> bootNames  = new LinkedHashMap<Byte, String>();
    	Map<Byte, String> htypeNames = new LinkedHashMap<Byte, String>();
        Map<Byte, String> dhcpCodes  = new LinkedHashMap<Byte, String>();
        Map<Byte, String> dhoNames   = new LinkedHashMap<Byte, String>();
        Map<String, Byte> dhoNamesRev = new LinkedHashMap<String, Byte>();
        
        // do some introspection to list constants
        Field[] fields = DHCPConstants.class.getDeclaredFields();

        // parse internal fields
        try {
            for (Field field : fields) {
                int    mod  = field.getModifiers();
                String name = field.getName();

                // parse only "public final static byte"
                if (Modifier.isFinal(mod) && Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
                    field.getType().equals(byte.class)) {
                    byte code = field.getByte(null);

                    if (name.startsWith("BOOT")) {
                        bootNames.put(code, name);
                    } else if (name.startsWith("HTYPE_")) {
                        htypeNames.put(code, name);
                    } else if (name.startsWith("DHCP")) {
                        dhcpCodes.put(code, name);
                    } else if (name.startsWith("DHO_")) {
                        dhoNames.put(code, name);
                        dhoNamesRev.put(name, code);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            // we have a problem
            throw new IllegalStateException("Fatal error while parsing internal fields");
        }
        _BOOT_NAMES = Collections.unmodifiableMap(bootNames);
        _HTYPE_NAMES = Collections.unmodifiableMap(htypeNames);
        _DHCP_CODES = Collections.unmodifiableMap(dhcpCodes);
        _DHO_NAMES = Collections.unmodifiableMap(dhoNames);
        _DHO_NAMES_REV = Collections.unmodifiableMap(dhoNamesRev);
    }
}
