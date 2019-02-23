/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 *	(c) 2006 Stephan Hadinger
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import timber.log.Timber;

import static org.dhcp4java.DHCPConstants.DHO_ALL_SUBNETS_LOCAL;
import static org.dhcp4java.DHCPConstants.DHO_ARP_CACHE_TIMEOUT;
import static org.dhcp4java.DHCPConstants.DHO_ASSOCIATED_IP;
import static org.dhcp4java.DHCPConstants.DHO_AUTO_CONFIGURE;
import static org.dhcp4java.DHCPConstants.DHO_BOOTFILE;
import static org.dhcp4java.DHCPConstants.DHO_BOOT_SIZE;
import static org.dhcp4java.DHCPConstants.DHO_BROADCAST_ADDRESS;
import static org.dhcp4java.DHCPConstants.DHO_CLIENT_LAST_TRANSACTION_TIME;
import static org.dhcp4java.DHCPConstants.DHO_COOKIE_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_DEFAULT_IP_TTL;
import static org.dhcp4java.DHCPConstants.DHO_DEFAULT_TCP_TTL;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_AGENT_OPTIONS;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_LEASE_TIME;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_MAX_MESSAGE_SIZE;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_MESSAGE;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_MESSAGE_TYPE;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_OPTION_OVERLOAD;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_PARAMETER_REQUEST_LIST;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_REBINDING_TIME;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_RENEWAL_TIME;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_REQUESTED_ADDRESS;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_SERVER_IDENTIFIER;
import static org.dhcp4java.DHCPConstants.DHO_DOMAIN_NAME;
import static org.dhcp4java.DHCPConstants.DHO_DOMAIN_NAME_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_DOMAIN_SEARCH;
import static org.dhcp4java.DHCPConstants.DHO_END;
import static org.dhcp4java.DHCPConstants.DHO_EXTENSIONS_PATH;
import static org.dhcp4java.DHCPConstants.DHO_FINGER_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_FONT_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_HOST_NAME;
import static org.dhcp4java.DHCPConstants.DHO_IEEE802_3_ENCAPSULATION;
import static org.dhcp4java.DHCPConstants.DHO_IMPRESS_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_INTERFACE_MTU;
import static org.dhcp4java.DHCPConstants.DHO_IP_FORWARDING;
import static org.dhcp4java.DHCPConstants.DHO_IRC_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_LOG_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_LPR_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_MASK_SUPPLIER;
import static org.dhcp4java.DHCPConstants.DHO_MAX_DGRAM_REASSEMBLY;
import static org.dhcp4java.DHCPConstants.DHO_MERIT_DUMP;
import static org.dhcp4java.DHCPConstants.DHO_MOBILE_IP_HOME_AGENT;
import static org.dhcp4java.DHCPConstants.DHO_NAME_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_NAME_SERVICE_SEARCH;
import static org.dhcp4java.DHCPConstants.DHO_NDS_CONTEXT;
import static org.dhcp4java.DHCPConstants.DHO_NDS_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_NDS_TREE_NAME;
import static org.dhcp4java.DHCPConstants.DHO_NETBIOS_DD_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_NETBIOS_NAME_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_NETBIOS_NODE_TYPE;
import static org.dhcp4java.DHCPConstants.DHO_NETBIOS_SCOPE;
import static org.dhcp4java.DHCPConstants.DHO_NISPLUS_DOMAIN;
import static org.dhcp4java.DHCPConstants.DHO_NISPLUS_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_NIS_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_NNTP_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_NON_LOCAL_SOURCE_ROUTING;
import static org.dhcp4java.DHCPConstants.DHO_NTP_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_NWIP_DOMAIN_NAME;
import static org.dhcp4java.DHCPConstants.DHO_PAD;
import static org.dhcp4java.DHCPConstants.DHO_PATH_MTU_AGING_TIMEOUT;
import static org.dhcp4java.DHCPConstants.DHO_PATH_MTU_PLATEAU_TABLE;
import static org.dhcp4java.DHCPConstants.DHO_PERFORM_MASK_DISCOVERY;
import static org.dhcp4java.DHCPConstants.DHO_POLICY_FILTER;
import static org.dhcp4java.DHCPConstants.DHO_POP3_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_RESOURCE_LOCATION_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_ROOT_PATH;
import static org.dhcp4java.DHCPConstants.DHO_ROUTERS;
import static org.dhcp4java.DHCPConstants.DHO_ROUTER_DISCOVERY;
import static org.dhcp4java.DHCPConstants.DHO_ROUTER_SOLICITATION_ADDRESS;
import static org.dhcp4java.DHCPConstants.DHO_SMTP_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_STATIC_ROUTES;
import static org.dhcp4java.DHCPConstants.DHO_STDA_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_STREETTALK_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_SUBNET_MASK;
import static org.dhcp4java.DHCPConstants.DHO_SUBNET_SELECTION;
import static org.dhcp4java.DHCPConstants.DHO_SWAP_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_TCP_KEEPALIVE_GARBAGE;
import static org.dhcp4java.DHCPConstants.DHO_TCP_KEEPALIVE_INTERVAL;
import static org.dhcp4java.DHCPConstants.DHO_TFTP_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_TIME_OFFSET;
import static org.dhcp4java.DHCPConstants.DHO_TIME_SERVERS;
import static org.dhcp4java.DHCPConstants.DHO_TRAILER_ENCAPSULATION;
import static org.dhcp4java.DHCPConstants.DHO_USER_AUTHENTICATION_PROTOCOL;
import static org.dhcp4java.DHCPConstants.DHO_USER_CLASS;
import static org.dhcp4java.DHCPConstants.DHO_VENDOR_CLASS_IDENTIFIER;
import static org.dhcp4java.DHCPConstants.DHO_WWW_SERVER;
import static org.dhcp4java.DHCPConstants.DHO_X_DISPLAY_MANAGER;
import static org.dhcp4java.DHCPConstants._DHCP_CODES;
import static org.dhcp4java.DHCPConstants._DHO_NAMES;

/**
 * Class for manipulating DHCP options (used internally).
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 * @version 1.00
 *
 * Immutable object.
 */
public class DHCPOption implements Serializable
{
    private static final long serialVersionUID = 2L;

    /**
     * The code of the option. 0 is reserved for padding, -1 for end of options.
     */
    private final byte code;

    /**
     * Raw bytes value of the option. Some methods are provided for higher
     * level of data structures, depending on the <tt>code</tt>.
     */
    private final byte[] value;

    /**
     * Used to mark an option as having a mirroring behaviour. This means that
     * this option if used by a server will first mirror the option the client sent
     * then provide a default value if this option was not present in the request.
     *
     * <p>This is only meant to be used by servers through the <tt>getMirrorValue</tt>
     * method.
     */
    private final boolean mirror;

    /**
     * Constructor for <tt>DHCPOption</tt>.
     *
     * <p>Note: you must not prefix the value by a length-byte. The length prefix
     * will be added automatically by the API.
     *
     * <p>If value is <tt>null</tt> it is considered as an empty option.
     * If you add an empty option to a DHCPPacket, it removes the option from the packet.
     *
     * <p>This constructor adds a parameter to mark the option as "mirror". See comments above.
     *
     * @param code DHCP option code
     * @param value DHCP option value as a byte array.
     */
    public DHCPOption(byte code, byte[] value, boolean mirror)
    {
        if (code == DHO_PAD) {
            throw new IllegalArgumentException("code=0 is not allowed (reserved for padding");
        }
        if (code == DHO_END) {
            throw new IllegalArgumentException("code=-1 is not allowed (reserved for End Of Options)");
        }

        this.code = code;
        this.value = (value != null) ? value.clone() : null;
        this.mirror = mirror;
    }

    /**
     * Constructor for <tt>DHCPOption</tt>. This is the default constructor.
     *
     * <p>Note: you must not prefix the value by a length-byte. The length prefix
     * will be added automatically by the API.
     *
     * <p>If value is <tt>null</tt> it is considered as an empty option.
     * If you add an empty option to a DHCPPacket, it removes the option from the packet.
     *
     * @param code DHCP option code
     * @param value DHCP option value as a byte array.
     */
    public DHCPOption(byte code, byte[] value)
    {
        this(code, value, false);
    }

    /**
     * Return the <tt>code</tt> field (byte).
     *
     * @return code field
     */
    public byte getCode()
    {
        return this.code;
    }

    /**
     * returns true if two <tt>DHCPOption</tt> objects are equal, i.e. have same <tt>code</tt>
     * and same <tt>value</tt>.
     */
    @Override
    public boolean equals(Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DHCPOption)) {
            return false;
        }
        DHCPOption opt = (DHCPOption) o;
        return ((opt.code == this.code) &&
                (opt.mirror == this.mirror) &&
                Arrays.equals(opt.value, this.value));

    }

    /**
     * Returns hashcode.
     *
     * @see Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        return this.code ^ Arrays.hashCode(this.value) ^
                (this.mirror ? 0x80000000 : 0);
    }

    /**
     * @return option value, can be null.
     */
    public byte[] getValue()
    {
        return ((this.value == null) ? null : this.value.clone());
    }

    /**
     * @return option value, never <tt>null</tt>. Minimal value is <tt>byte[0]</tt>.
     */
    public byte[] getValueFast()
    {
        return this.value;
    }

    /**
     * Returns whether the option is marked as "mirror", meaning it should mirror
     * the option value in the client request.
     *
     * <p>To be used only in servers.
     *
     * @return is the option marked is mirror?
     */
    public boolean isMirror()
    {
        return this.mirror;
    }

    public static boolean isOptionAsByte(byte code)
    {
        return OptionFormat.BYTE.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Creates a DHCP Option as Byte format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_IP_FORWARDING(19)
     * DHO_NON_LOCAL_SOURCE_ROUTING(20)
     * DHO_DEFAULT_IP_TTL(23)
     * DHO_ALL_SUBNETS_LOCAL(27)
     * DHO_PERFORM_MASK_DISCOVERY(29)
     * DHO_MASK_SUPPLIER(30)
     * DHO_ROUTER_DISCOVERY(31)
     * DHO_TRAILER_ENCAPSULATION(34)
     * DHO_IEEE802_3_ENCAPSULATION(36)
     * DHO_DEFAULT_TCP_TTL(37)
     * DHO_TCP_KEEPALIVE_GARBAGE(39)
     * DHO_NETBIOS_NODE_TYPE(46)
     * DHO_DHCP_OPTION_OVERLOAD(52)
     * DHO_DHCP_MESSAGE_TYPE(53)
     * DHO_AUTO_CONFIGURE(116)
     * </pre>
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsByte(byte code, byte val)
    {
        if (!isOptionAsByte(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not byte");
        }
        return new DHCPOption(code, byte2Bytes(val));
    }

    /**
     * Returns a DHCP Option as Byte format.
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_IP_FORWARDING(19)
     * DHO_NON_LOCAL_SOURCE_ROUTING(20)
     * DHO_DEFAULT_IP_TTL(23)
     * DHO_ALL_SUBNETS_LOCAL(27)
     * DHO_PERFORM_MASK_DISCOVERY(29)
     * DHO_MASK_SUPPLIER(30)
     * DHO_ROUTER_DISCOVERY(31)
     * DHO_TRAILER_ENCAPSULATION(34)
     * DHO_IEEE802_3_ENCAPSULATION(36)
     * DHO_DEFAULT_TCP_TTL(37)
     * DHO_TCP_KEEPALIVE_GARBAGE(39)
     * DHO_NETBIOS_NODE_TYPE(46)
     * DHO_DHCP_OPTION_OVERLOAD(52)
     * DHO_DHCP_MESSAGE_TYPE(53)
     * DHO_AUTO_CONFIGURE(116)
     * </pre>
     *
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public byte getValueAsByte()
            throws IllegalArgumentException
    {
        if (!isOptionAsByte(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not byte");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if (this.value.length != 1) {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 1");
        }
        return this.value[0];
    }

    public static boolean isOptionAsShort(byte code)
    {
        return OptionFormat.SHORT.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as Short format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_BOOT_SIZE(13)
     * DHO_MAX_DGRAM_REASSEMBLY(22)
     * DHO_INTERFACE_MTU(26)
     * DHO_DHCP_MAX_MESSAGE_SIZE(57)
     * </pre>
     *
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public short getValueAsShort()
            throws IllegalArgumentException
    {
        if (!isOptionAsShort(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not short");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if (this.value.length != 2) {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 2");
        }

        return (short) ((this.value[0] & 0xff) << 8 | (this.value[1] & 0xFF));
    }

    public static boolean isOptionAsInt(byte code)
    {
        return OptionFormat.INT.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as Integer format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_TIME_OFFSET(2)
     * DHO_PATH_MTU_AGING_TIMEOUT(24)
     * DHO_ARP_CACHE_TIMEOUT(35)
     * DHO_TCP_KEEPALIVE_INTERVAL(38)
     * DHO_DHCP_LEASE_TIME(51)
     * DHO_DHCP_RENEWAL_TIME(58)
     * DHO_DHCP_REBINDING_TIME(59)
     * </pre>
     *
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public int getValueAsInt()
            throws IllegalArgumentException
    {
        if (!isOptionAsInt(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not int");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if (this.value.length != 4) {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 4");
        }
        return ((this.value[0] & 0xFF) << 24 |
                (this.value[1] & 0xFF) << 16 |
                (this.value[2] & 0xFF) << 8 |
                (this.value[3] & 0xFF));
    }

    // TODO

    /**
     * Returns a DHCP Option as Integer format, but is usable for any numerical type: int, short or byte.
     *
     * <p>There is no check on the option
     *
     * @return the option value <tt>null</tt> if option is not present, or wrong number of bytes.
     */
    public Integer getValueAsNum()
            throws IllegalArgumentException
    {
        if (value == null) {
            return null;
        }
        if (value.length == 1) {            // byte
            return value[0] & 0xFF;
        }
        else if (value.length == 2) {        // short
            return ((value[0] & 0xff) << 8 | (value[1] & 0xFF));
        }
        else if (value.length == 4) {
            return ((this.value[0] & 0xFF) << 24 |
                    (this.value[1] & 0xFF) << 16 |
                    (this.value[2] & 0xFF) << 8 |
                    (this.value[3] & 0xFF));
        }
        else {
            return null;
        }
    }


    public static boolean isOptionAsInetAddr(byte code)
    {
        return OptionFormat.INET.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as InetAddress format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_SUBNET_MASK(1)
     * DHO_SWAP_SERVER(16)
     * DHO_BROADCAST_ADDRESS(28)
     * DHO_ROUTER_SOLICITATION_ADDRESS(32)
     * DHO_DHCP_REQUESTED_ADDRESS(50)
     * DHO_DHCP_SERVER_IDENTIFIER(54)
     * DHO_SUBNET_SELECTION(118)
     * </pre>
     *
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public InetAddress getValueAsInetAddr()
            throws IllegalArgumentException
    {
        if (!isOptionAsInetAddr(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not InetAddr");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if (this.value.length != 4) {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 4");
        }
        try {
            return InetAddress.getByAddress(this.value);
        } catch (UnknownHostException e) {
            Timber.e(e, "Unexpected UnknownHostException");
            return null;    // normally impossible
        }
    }

    public static boolean isOptionAsString(byte code)
    {
        return OptionFormat.STRING.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as String format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_HOST_NAME(12)
     * DHO_MERIT_DUMP(14)
     * DHO_DOMAIN_NAME(15)
     * DHO_ROOT_PATH(17)
     * DHO_EXTENSIONS_PATH(18)
     * DHO_NETBIOS_SCOPE(47)
     * DHO_DHCP_MESSAGE(56)
     * DHO_VENDOR_CLASS_IDENTIFIER(60)
     * DHO_NWIP_DOMAIN_NAME(62)
     * DHO_NIS_DOMAIN(64)
     * DHO_NIS_SERVER(65)
     * DHO_TFTP_SERVER(66)
     * DHO_BOOTFILE(67)
     * DHO_NDS_TREE_NAME(86)
     * DHO_USER_AUTHENTICATION_PROTOCOL(98)
     * </pre>
     *
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public String getValueAsString()
            throws IllegalArgumentException
    {
        if (!isOptionAsString(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not String");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        return DHCPPacket.bytesToString(this.value);
    }

    public static boolean isOptionAsShorts(byte code)
    {
        return OptionFormat.SHORTS.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as Short array format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_PATH_MTU_PLATEAU_TABLE(25)
     * DHO_NAME_SERVICE_SEARCH(117)
     * </pre>
     *
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public short[] getValueAsShorts()
            throws IllegalArgumentException
    {
        if (!isOptionAsShorts(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not short[]");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if ((this.value.length % 2) != 0)        // multiple of 2
        {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 2*X");
        }

        short[] shorts = new short[this.value.length / 2];
        for (int i = 0, a = 0; a < this.value.length; i++, a += 2) {
            shorts[i] = (short) (((this.value[a] & 0xFF) << 8) | (this.value[a + 1] & 0xFF));
        }
        return shorts;
    }

    public static boolean isOptionAsInetAddrs(byte code)
    {
        return OptionFormat.INETS.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as InetAddress array format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_ROUTERS(3)
     * DHO_TIME_SERVERS(4)
     * DHO_NAME_SERVERS(5)
     * DHO_DOMAIN_NAME_SERVERS(6)
     * DHO_LOG_SERVERS(7)
     * DHO_COOKIE_SERVERS(8)
     * DHO_LPR_SERVERS(9)
     * DHO_IMPRESS_SERVERS(10)
     * DHO_RESOURCE_LOCATION_SERVERS(11)
     * DHO_POLICY_FILTER(21)
     * DHO_STATIC_ROUTES(33)
     * DHO_NIS_SERVERS(41)
     * DHO_NTP_SERVERS(42)
     * DHO_NETBIOS_NAME_SERVERS(44)
     * DHO_NETBIOS_DD_SERVER(45)
     * DHO_FONT_SERVERS(48)
     * DHO_X_DISPLAY_MANAGER(49)
     * DHO_MOBILE_IP_HOME_AGENT(68)
     * DHO_SMTP_SERVER(69)
     * DHO_POP3_SERVER(70)
     * DHO_NNTP_SERVER(71)
     * DHO_WWW_SERVER(72)
     * DHO_FINGER_SERVER(73)
     * DHO_IRC_SERVER(74)
     * DHO_STREETTALK_SERVER(75)
     * DHO_STDA_SERVER(76)
     * DHO_NDS_SERVERS(85)
     * </pre>
     *
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public InetAddress[] getValueAsInetAddrs()
            throws IllegalArgumentException
    {
        if (!isOptionAsInetAddrs(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not InetAddr[]");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        if ((this.value.length % 4) != 0)        // multiple of 4
        {
            throw new DHCPBadPacketException("option " + this.code + " is wrong size:" + this.value.length + " should be 4*X");
        }
        try {
            byte[] addr = new byte[4];
            InetAddress[] addrs = new InetAddress[this.value.length / 4];
            for (int i = 0, a = 0; a < this.value.length; i++, a += 4) {
                addr[0] = this.value[a];
                addr[1] = this.value[a + 1];
                addr[2] = this.value[a + 2];
                addr[3] = this.value[a + 3];
                addrs[i] = InetAddress.getByAddress(addr);
            }
            return addrs;
        } catch (UnknownHostException e) {
            Timber.e(e, "Unexpected UnknownHostException");
            return null;    // normally impossible
        }
    }

    public static boolean isOptionAsBytes(byte code)
    {
        return OptionFormat.BYTES.equals(_DHO_FORMATS.get(code));
    }

    /**
     * Returns a DHCP Option as Byte array format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_DHCP_PARAMETER_REQUEST_LIST(55)
     * </pre>
     *
     * <p>Note: this mehtod is similar to getOptionRaw, only with option type checking.
     *
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public byte[] getValueAsBytes()
            throws IllegalArgumentException
    {
        if (!isOptionAsBytes(code)) {
            throw new IllegalArgumentException("DHCP option type (" + this.code + ") is not bytes");
        }
        if (this.value == null) {
            throw new IllegalStateException("value is null");
        }
        return this.getValue();
    }

    /**
     * Creates a DHCP Option as Short format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_BOOT_SIZE(13)
     * DHO_MAX_DGRAM_REASSEMBLY(22)
     * DHO_INTERFACE_MTU(26)
     * DHO_DHCP_MAX_MESSAGE_SIZE(57)
     * </pre>
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsShort(byte code, short val)
    {
        if (!isOptionAsShort(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not short");
        }
        return new DHCPOption(code, short2Bytes(val));
    }

    /**
     * Creates a DHCP Options as Short[] format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_PATH_MTU_PLATEAU_TABLE(25)
     * DHO_NAME_SERVICE_SEARCH(117)
     * </pre>
     *
     * @param code the option code.
     * @param arr the array of shorts
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsShorts(byte code, short[] arr)
    {
        if (!isOptionAsShorts(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not shorts");
        }
        byte[] buf = null;
        if (arr != null) {
            buf = new byte[arr.length * 2];
            for (int i = 0; i < arr.length; i++) {
                short val = arr[i];
                buf[i * 2] = (byte) ((val & 0xFF00) >>> 8);
                buf[i * 2 + 1] = (byte) (val & 0XFF);
            }
        }
        return new DHCPOption(code, buf);
    }

    /**
     * Creates a DHCP Option as Integer format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_TIME_OFFSET(2)
     * DHO_PATH_MTU_AGING_TIMEOUT(24)
     * DHO_ARP_CACHE_TIMEOUT(35)
     * DHO_TCP_KEEPALIVE_INTERVAL(38)
     * DHO_DHCP_LEASE_TIME(51)
     * DHO_DHCP_RENEWAL_TIME(58)
     * DHO_DHCP_REBINDING_TIME(59)
     * </pre>
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsInt(byte code, int val)
    {
        if (!isOptionAsInt(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not int");
        }
        return new DHCPOption(code, int2Bytes(val));
    }

    /**
     * Sets a DHCP Option as InetAddress format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_SUBNET_MASK(1)
     * DHO_SWAP_SERVER(16)
     * DHO_BROADCAST_ADDRESS(28)
     * DHO_ROUTER_SOLICITATION_ADDRESS(32)
     * DHO_DHCP_REQUESTED_ADDRESS(50)
     * DHO_DHCP_SERVER_IDENTIFIER(54)
     * DHO_SUBNET_SELECTION(118)
     * </pre>
     * and also as a simplified version for setOptionAsInetAddresses
     * <pre>
     * DHO_ROUTERS(3)
     * DHO_TIME_SERVERS(4)
     * DHO_NAME_SERVERS(5)
     * DHO_DOMAIN_NAME_SERVERS(6)
     * DHO_LOG_SERVERS(7)
     * DHO_COOKIE_SERVERS(8)
     * DHO_LPR_SERVERS(9)
     * DHO_IMPRESS_SERVERS(10)
     * DHO_RESOURCE_LOCATION_SERVERS(11)
     * DHO_POLICY_FILTER(21)
     * DHO_STATIC_ROUTES(33)
     * DHO_NIS_SERVERS(41)
     * DHO_NTP_SERVERS(42)
     * DHO_NETBIOS_NAME_SERVERS(44)
     * DHO_NETBIOS_DD_SERVER(45)
     * DHO_FONT_SERVERS(48)
     * DHO_X_DISPLAY_MANAGER(49)
     * DHO_MOBILE_IP_HOME_AGENT(68)
     * DHO_SMTP_SERVER(69)
     * DHO_POP3_SERVER(70)
     * DHO_NNTP_SERVER(71)
     * DHO_WWW_SERVER(72)
     * DHO_FINGER_SERVER(73)
     * DHO_IRC_SERVER(74)
     * DHO_STREETTALK_SERVER(75)
     * DHO_STDA_SERVER(76)
     * DHO_NDS_SERVERS(85)
     * </pre>
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsInetAddress(byte code, InetAddress val)
    {
        if ((!isOptionAsInetAddr(code)) &&
                (!isOptionAsInetAddrs(code))) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not InetAddress");
        }
        return new DHCPOption(code, inetAddress2Bytes(val));
    }

    /**
     * Creates a DHCP Option as InetAddress array format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_ROUTERS(3)
     * DHO_TIME_SERVERS(4)
     * DHO_NAME_SERVERS(5)
     * DHO_DOMAIN_NAME_SERVERS(6)
     * DHO_LOG_SERVERS(7)
     * DHO_COOKIE_SERVERS(8)
     * DHO_LPR_SERVERS(9)
     * DHO_IMPRESS_SERVERS(10)
     * DHO_RESOURCE_LOCATION_SERVERS(11)
     * DHO_POLICY_FILTER(21)
     * DHO_STATIC_ROUTES(33)
     * DHO_NIS_SERVERS(41)
     * DHO_NTP_SERVERS(42)
     * DHO_NETBIOS_NAME_SERVERS(44)
     * DHO_NETBIOS_DD_SERVER(45)
     * DHO_FONT_SERVERS(48)
     * DHO_X_DISPLAY_MANAGER(49)
     * DHO_MOBILE_IP_HOME_AGENT(68)
     * DHO_SMTP_SERVER(69)
     * DHO_POP3_SERVER(70)
     * DHO_NNTP_SERVER(71)
     * DHO_WWW_SERVER(72)
     * DHO_FINGER_SERVER(73)
     * DHO_IRC_SERVER(74)
     * DHO_STREETTALK_SERVER(75)
     * DHO_STDA_SERVER(76)
     * DHO_NDS_SERVERS(85)
     * </pre>
     *
     * @param code the option code.
     * @param val the value array
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsInetAddresses(byte code, InetAddress[] val)
    {
        if (!isOptionAsInetAddrs(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not InetAddresses");
        }
        return new DHCPOption(code, inetAddresses2Bytes(val));
    }

    /**
     * Creates a DHCP Option as String format.
     *
     * <p>This method is only allowed for the following option codes:
     * <pre>
     * DHO_HOST_NAME(12)
     * DHO_MERIT_DUMP(14)
     * DHO_DOMAIN_NAME(15)
     * DHO_ROOT_PATH(17)
     * DHO_EXTENSIONS_PATH(18)
     * DHO_NETBIOS_SCOPE(47)
     * DHO_DHCP_MESSAGE(56)
     * DHO_VENDOR_CLASS_IDENTIFIER(60)
     * DHO_NWIP_DOMAIN_NAME(62)
     * DHO_NIS_DOMAIN(64)
     * DHO_NIS_SERVER(65)
     * DHO_TFTP_SERVER(66)
     * DHO_BOOTFILE(67)
     * DHO_NDS_TREE_NAME(86)
     * DHO_USER_AUTHENTICATION_PROTOCOL(98)
     * </pre>
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public static DHCPOption newOptionAsString(byte code, String val)
    {
        if (!isOptionAsString(code)) {
            throw new IllegalArgumentException("DHCP option type (" + code + ") is not string");
        }
        return new DHCPOption(code, DHCPPacket.stringToBytes(val));
    }

    /**
     * Get the option value based on the context, i.e. the client's request.
     *
     * <p>This should be the only method used with this class to get relevant values.
     *
     * @param request the client's DHCP requets
     * @return the value of the specific option in the client request
     * @throws NullPointerException if <tt>request</tt> is <tt>null</tt>.
     */
    public DHCPOption applyOption(DHCPPacket request)
    {
        if (request == null) {
            throw new NullPointerException("request is null");
        }
        if (this.mirror) {
            DHCPOption res = request.getOption(this.getCode());
            return (res != null ? res : this);    // return res or this
        }
        else {
            return this;
        }
    }

    /**
     * Appends to this string builder a detailed string representation of the DHCP datagram.
     *
     * <p>This multi-line string details: the static, options and padding parts
     * of the object. This is useful for debugging, but not efficient.
     *
     * @param buffer the string builder the string representation of this object should be appended.
     */
    public void append(StringBuilder buffer)
    {
        // check for readable option name
        if (_DHO_NAMES.containsKey(this.code)) {
            buffer.append(_DHO_NAMES.get(this.code));
        }
        buffer.append('(')
                .append(unsignedByte(this.code))
                .append(")=");

        if (this.mirror) {
            buffer.append("<mirror>");
        }

        // check for value printing
        if (this.value == null) {
            buffer.append("<null>");
        }
        else if (this.code == DHO_DHCP_MESSAGE_TYPE) {
            Byte cmd = this.getValueAsByte();
            if (_DHCP_CODES.containsKey(cmd)) {
                buffer.append(_DHCP_CODES.get(cmd));
            }
            else {
                buffer.append(cmd);
            }
        }
        else if (this.code == DHO_USER_CLASS) {
            buffer.append(userClassToString(this.value));
        }
        else if (this.code == DHO_DHCP_AGENT_OPTIONS) {
            buffer.append(agentOptionsToString(this.value));
        }
        else if (_DHO_FORMATS.containsKey(this.code)) {
            // formatted output
            try {    // catch malformed values
                switch (_DHO_FORMATS.get(this.code)) {
                    case INET:
                        DHCPPacket.appendHostAddress(buffer, this.getValueAsInetAddr());
                        break;
                    case INETS:
                        for (InetAddress addr : this.getValueAsInetAddrs()) {
                            DHCPPacket.appendHostAddress(buffer, addr);
                            buffer.append(' ');
                        }
                        break;
                    case INT:
                        buffer.append(this.getValueAsInt());
                        break;
                    case SHORT:
                        buffer.append(this.getValueAsShort());
                        break;
                    case SHORTS:
                        for (short aShort : this.getValueAsShorts()) {
                            buffer.append(aShort)
                                    .append(' ');
                        }
                        break;
                    case BYTE:
                        buffer.append(this.getValueAsByte());
                        break;
                    case STRING:
                        buffer.append('"')
                                .append(this.getValueAsString())
                                .append('"');
                        break;
                    case BYTES:
                        if (this.value != null) {
                            for (byte aValue : this.value) {
                                buffer.append(unsignedByte(aValue))
                                        .append(' ');
                            }
                        }
                        break;
                    default:
                        buffer.append("0x");
                        DHCPPacket.appendHex(buffer, this.value);
                        break;
                }
            } catch (IllegalArgumentException e) {
                // fallback to bytes
                buffer.append("0x");
                DHCPPacket.appendHex(buffer, this.value);
            }
        }
        else {
            // unformatted raw output
            buffer.append("0x");
            DHCPPacket.appendHex(buffer, this.value);
        }
    }

    /**
     * Returns a detailed string representation of the DHCP datagram.
     *
     * <p>This multi-line string details: the static, options and padding parts
     * of the object. This is useful for debugging, but not efficient.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();

        this.append(s);
        return s.toString();
    }

    /**
     * Convert unsigned byte to int
     */
    private static int unsignedByte(byte b)
    {
        return (b & 0xFF);
    }

    /**************************************************************************
     *
     * Type converters.
     *
     **************************************************************************/

    public static byte[] byte2Bytes(byte val)
    {
        byte[] raw = {val};
        return raw;
    }

    public static byte[] short2Bytes(short val)
    {
        byte[] raw = {(byte) ((val & 0xFF00) >>> 8), (byte) (val & 0XFF)};
        return raw;
    }

    public static byte[] int2Bytes(int val)
    {
        byte[] raw = {(byte) ((val & 0xFF000000) >>> 24),
                (byte) ((val & 0X00FF0000) >>> 16),
                (byte) ((val & 0x0000FF00) >>> 8),
                (byte) ((val & 0x000000FF))};
        return raw;
    }

    public static byte[] inetAddress2Bytes(InetAddress val)
    {
        if (val == null) {
            return null;
        }
        if (!(val instanceof Inet4Address)) {
            throw new IllegalArgumentException("Adress must be of subclass Inet4Address");
        }
        return val.getAddress();
    }

    public static byte[] inetAddresses2Bytes(InetAddress[] val)
    {
        if (val == null) {
            return null;
        }

        byte[] buf = new byte[val.length * 4];
        for (int i = 0; i < val.length; i++) {
            InetAddress addr = val[i];
            if (!(addr instanceof Inet4Address)) {
                throw new IllegalArgumentException("Adress must be of subclass Inet4Address");
            }
            System.arraycopy(addr.getAddress(), 0, buf, i * 4, 4);
        }
        return buf;
    }

    /**
     * Convert DHO_USER_CLASS (77) option to a List.
     *
     * @param buf option value of type User Class.
     * @return List of String values.
     */
    public static List<String> userClassToList(byte[] buf)
    {
        if (buf == null) {
            return null;
        }

        LinkedList<String> list = new LinkedList<String>();
        int i = 0;
        while (i < buf.length) {
            int size = unsignedByte(buf[i++]);
            int instock = buf.length - i;
            if (size > instock) {
                size = instock;
            }
            list.add(DHCPPacket.bytesToString(buf, i, size));
            i += size;
        }
        return list;
    }

    /**
     * Converts DHO_USER_CLASS (77) option to a printable string
     *
     * @param buf option value of type User Class.
     * @return printable string.
     */
    public static String userClassToString(byte[] buf)
    {
        if (buf == null) {
            return null;
        }

        List list = userClassToList(buf);
        Iterator it = list.iterator();
        StringBuffer s = new StringBuffer();

        while (it.hasNext()) {
            s.append('"').append((String) it.next()).append('"');
            if (it.hasNext()) {
                s.append(',');
            }
        }
        return s.toString();
    }

    /**
     * Converts this list of strings to a DHO_USER_CLASS (77) option.
     *
     * @param list the list of strings
     * @return byte[] buffer to use with <tt>setOptionRaw</tt>, <tt>null</tt> if list is null
     * @throws IllegalArgumentException if List contains anything else than String
     */
    public static byte[] stringListToUserClass(List<String> list)
    {
        if (list == null) {
            return null;
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream(32);
        DataOutputStream out = new DataOutputStream(buf);

        try {
            for (String s : list) {
                byte[] bytes = DHCPPacket.stringToBytes(s);
                int size = bytes.length;

                if (size > 255) {
                    size = 255;
                }
                out.writeByte(size);
                out.write(bytes, 0, size);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            Timber.e(e, "Unexpected IOException");
            return buf.toByteArray();
        }
    }

    /**
     * Converts DHO_DHCP_AGENT_OPTIONS (82) option type to a printable string
     *
     * @param buf option value of type Agent Option.
     * @return printable string.
     */
    public static String agentOptionsToString(byte[] buf)
    {
        if (buf == null) {
            return null;
        }

        Map<Byte, String> map = agentOptionsToMap(buf);
        StringBuffer s = new StringBuffer();
        for (Entry<Byte, String> entry : map.entrySet()) {
            s.append('{').append(unsignedByte(entry.getKey())).append("}\"");
            s.append(entry.getValue()).append('\"');
            s.append(',');
        }
        if (s.length() > 0) {
            s.setLength(s.length() - 1);
        }

        return s.toString();
    }

    /**
     * Converts Map<Byte,String> to DHO_DHCP_AGENT_OPTIONS (82) option.
     *
     * <p>LinkedHashMap are preferred as they preserve insertion order. Regular
     * HashMap order is randon.
     *
     * @param map Map<Byte,String> couples
     * @return byte[] buffer to use with <tt>setOptionRaw</tt>
     * @throws IllegalArgumentException if List contains anything else than String
     */
    public static byte[] agentOptionToRaw(Map<Byte, String> map)
    {
        if (map == null) {
            return null;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
        DataOutputStream out = new DataOutputStream(buf);
        try {
            for (Entry<Byte, String> entry : map.entrySet()) {
                byte[] bufTemp = DHCPPacket.stringToBytes(entry.getValue());
                int size = bufTemp.length;
                assert (size >= 0);
                if (size > 255) {
                    throw new IllegalArgumentException("Value size is greater then 255 bytes");
                }
                out.writeByte(entry.getKey());
                out.writeByte(size);
                out.write(bufTemp, 0, size);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            Timber.e(e, "Unexpected IOException");
            return buf.toByteArray();
        }
    }

    /**
     * Converts DHO_DHCP_AGENT_OPTIONS (82) option type to a LinkedMap.
     *
     * <p>Order of parameters is preserved (use avc <tt>LinkedHashmap</tt<).
     * Keys are of type <tt>Byte</tt>, values are of type <tt>String</tt>.
     *
     * @param buf byte[] buffer returned by </tt>getOptionRaw</tt>
     * @return the LinkedHashmap of values, <tt>null</tt> if buf is <tt>null</tt>
     */
    public static final Map<Byte, String> agentOptionsToMap(byte[] buf)
    {
        if (buf == null) {
            return null;
        }

        Map<Byte, String> map = new LinkedHashMap<Byte, String>();
        int i = 0;

        while (i < buf.length) {
            if (buf.length - i < 2) {
                break;    // not enough data left
            }
            Byte key = buf[i++];
            int size = unsignedByte(buf[i++]);
            int instock = buf.length - i;

            if (size > instock) {
                size = instock;
            }
            map.put(key, DHCPPacket.bytesToString(buf, i, size));
            i += size;
        }
        return map;
    }

    /**
     * Returns the type of the option based on the option code.
     *
     * <p>The type is returned as a <tt>Class</tt> object:
     * <ul>
     * <li><tt>InetAddress.class</tt></li>
     * <li><tt>InetAddress[].class</tt></li>
     * <li><tt>int.class</tt></li>
     * <li><tt>short.class</tt></li>
     * <li><tt>short[].class</tt></li>
     * <li><tt>byte.class</tt></li>
     * <li><tt>byte[].class</tt></li>
     * <li><tt>String.class</tt></li>
     * </ul>
     *
     * <p>Please use <tt>getSimpleName()</tt> methode of <tt>Class</tt> object for the String representation.
     *
     * @param code the DHCP option code
     * @return the Class object representing accepted types
     */
    public static Class getOptionFormat(byte code)
    {
        OptionFormat format = _DHO_FORMATS.get(code);
        if (format == null) {
            return null;
        }
        switch (format) {
            case INET:
                return InetAddress.class;
            case INETS:
                return InetAddress[].class;
            case INT:
                return int.class;
            case SHORT:
                return short.class;
            case SHORTS:
                return short[].class;
            case BYTE:
                return byte.class;
            case BYTES:
                return byte[].class;
            case STRING:
                return String.class;
            default:
                return null;
        }
    }

    /**
     * Simple method for converting from string to supported class format.
     *
     * <p>Support values are:
     * <ul>
     * <li>InetAddress, inet</li>
     * <li>InetAddress[], inets</li>
     * <li>int</li>
     * <li>short</li>
     * <li>short[], shorts</li>
     * <li>byte</li>
     * <li>byte[], bytes</li>
     * <li>String, string</li>
     * </ul>
     *
     * @param className name of the data format (see above)
     * @return <tt>Class</tt> or <tt>null</tt> if not supported
     */
    public static Class string2Class(String className)
    {
        if ("InetAddress".equals(className))
            return InetAddress.class;
        if ("inet".equals(className))
            return InetAddress.class;
        if ("InetAddress[]".equals(className))
            return InetAddress[].class;
        if ("inets".equals(className))
            return InetAddress[].class;
        if ("int".equals(className))
            return int.class;
        if ("short".equals(className))
            return short.class;
        if ("short[]".equals(className))
            return short[].class;
        if ("shorts".equals(className))
            return short[].class;
        if ("byte".equals(className))
            return byte.class;
        if ("byte[]".equals(className))
            return byte[].class;
        if ("bytes".equals(className))
            return byte[].class;
        if ("String".equals(className))
            return String.class;
        if ("string".equals(className))
            return String.class;
        return null;
    }

    /**
     * Parse an option from a pure string representation.
     *
     * <P>The expected class is passed as a parameter, and can be provided by the
     * <tt>string2Class()</tt> method from a string representation of the class.
     *
     * <P>TODO examples
     *
     * @param code DHCP option code
     * @param format expected Java Class after conversion
     * @param value string representation of the value
     * @return the DHCPOption object
     */
    public static DHCPOption parseNewOption(byte code, Class format, String value)
    {
        if ((format == null) || (value == null)) {
            throw new NullPointerException();
        }

        if (short.class.equals(format)) {                                // short
            return newOptionAsShort(code, (short) Integer.parseInt(value));
        }
        else if (short[].class.equals(format)) {                    // short[]
            String[] listVal = value.split(" ");
            short[] listShort = new short[listVal.length];
            for (int i = 0; i < listVal.length; i++) {
                listShort[i] = (short) Integer.parseInt(listVal[i]);
            }
            return newOptionAsShorts(code, listShort);
        }
        else if (int.class.equals(format)) {                        // int
            return newOptionAsInt(code, Integer.parseInt(value));
        }
        else if (String.class.equals(format)) {                        // String
            return newOptionAsString(code, value);
        }
        else if (byte.class.equals(format)) {                        // byte
            return newOptionAsByte(code, (byte) Integer.parseInt(value));
            // TODO be explicit about BYTE allowed from -128 to 255 (unsigned int support)
        }
        else if (byte[].class.equals(format)) {                        // byte[]
            value = value.replace(".", " ");
            String[] listVal = value.split(" ");
            byte[] listBytes = new byte[listVal.length];
            for (int i = 0; i < listVal.length; i++) {
                listBytes[i] = (byte) Integer.parseInt(listVal[i]);
            }
            return new DHCPOption(code, listBytes);
        }
        else if (InetAddress.class.equals(format)) {                    // InetAddress
            try {
                return newOptionAsInetAddress(code, InetAddress.getByName(value));
            } catch (UnknownHostException e) {
                Timber.e(e, "Invalid address:%s", value);
                return null;
            }
        }
        else if (InetAddress[].class.equals(format)) {                // InetAddress[]
            String[] listVal = value.split(" ");
            InetAddress[] listInet = new InetAddress[listVal.length];
            try {
                for (int i = 0; i < listVal.length; i++) {
                    listInet[i] = InetAddress.getByName(listVal[i]);
                }
            } catch (UnknownHostException e) {
                Timber.e(e, "Invalid address");
                return null;
            }
            return newOptionAsInetAddresses(code, listInet);
        }
        return null;
    }

    // ----------------------------------------------------------------------
    // Internal constants for high-level option type conversions.
    //
    // formats of options
    //
    enum OptionFormat
    {
        INET,	// 4 bytes IP,				size = 4
        INETS,	// list of 4 bytes IP,		size = 4*n
        INT,	// 4 bytes integer,			size = 4
        SHORT,	// 2 bytes short,			size = 2
        SHORTS,	// list of 2 bytes shorts,	size = 2*n
        BYTE,	// 1 byte,					size = 1
        BYTES,	// list of bytes,			size = n
        STRING,	// string,					size = n
        //RELAYS	= 9;	// DHCP sub-options (rfc 3046)
        //ID		= 10;	// client identifier : byte (htype) + string (chaddr)
    }

    //
    // list of formats by options
    //
    private static final Object[] _OPTION_FORMATS = {
            DHO_SUBNET_MASK,					OptionFormat.INET,
            DHO_TIME_OFFSET,					OptionFormat.INT,
            DHO_ROUTERS,						OptionFormat.INETS,
            DHO_TIME_SERVERS,					OptionFormat.INETS,
            DHO_NAME_SERVERS,					OptionFormat.INETS,
            DHO_DOMAIN_NAME_SERVERS,			OptionFormat.INETS,
            DHO_LOG_SERVERS,					OptionFormat.INETS,
            DHO_COOKIE_SERVERS,					OptionFormat.INETS,
            DHO_LPR_SERVERS,					OptionFormat.INETS,
            DHO_IMPRESS_SERVERS,				OptionFormat.INETS,
            DHO_RESOURCE_LOCATION_SERVERS,		OptionFormat.INETS,
            DHO_HOST_NAME,						OptionFormat.STRING,
            DHO_BOOT_SIZE,						OptionFormat.SHORT,
            DHO_MERIT_DUMP,						OptionFormat.STRING,
            DHO_DOMAIN_NAME,					OptionFormat.STRING,
            DHO_SWAP_SERVER,					OptionFormat.INET,
            DHO_ROOT_PATH,						OptionFormat.STRING,
            DHO_EXTENSIONS_PATH,				OptionFormat.STRING,
            DHO_IP_FORWARDING,					OptionFormat.BYTE,
            DHO_NON_LOCAL_SOURCE_ROUTING,		OptionFormat.BYTE,
            DHO_POLICY_FILTER,					OptionFormat.INETS,
            DHO_MAX_DGRAM_REASSEMBLY,			OptionFormat.SHORT,
            DHO_DEFAULT_IP_TTL,					OptionFormat.BYTE,
            DHO_PATH_MTU_AGING_TIMEOUT,			OptionFormat.INT,
            DHO_PATH_MTU_PLATEAU_TABLE,			OptionFormat.SHORTS,
            DHO_INTERFACE_MTU,					OptionFormat.SHORT,
            DHO_ALL_SUBNETS_LOCAL,				OptionFormat.BYTE,
            DHO_BROADCAST_ADDRESS,				OptionFormat.INET,
            DHO_PERFORM_MASK_DISCOVERY,			OptionFormat.BYTE,
            DHO_MASK_SUPPLIER,					OptionFormat.BYTE,
            DHO_ROUTER_DISCOVERY,				OptionFormat.BYTE,
            DHO_ROUTER_SOLICITATION_ADDRESS,	OptionFormat.INET,
            DHO_STATIC_ROUTES,					OptionFormat.INETS,
            DHO_TRAILER_ENCAPSULATION,			OptionFormat.BYTE,
            DHO_ARP_CACHE_TIMEOUT,				OptionFormat.INT,
            DHO_IEEE802_3_ENCAPSULATION,		OptionFormat.BYTE,
            DHO_DEFAULT_TCP_TTL,				OptionFormat.BYTE,
            DHO_TCP_KEEPALIVE_INTERVAL,			OptionFormat.INT,
            DHO_TCP_KEEPALIVE_GARBAGE,			OptionFormat.BYTE,
            DHO_NIS_SERVERS,					OptionFormat.INETS,
            DHO_NTP_SERVERS,					OptionFormat.INETS,
            DHO_NETBIOS_NAME_SERVERS,			OptionFormat.INETS,
            DHO_NETBIOS_DD_SERVER,				OptionFormat.INETS,
            DHO_NETBIOS_NODE_TYPE,				OptionFormat.BYTE,
            DHO_NETBIOS_SCOPE,					OptionFormat.STRING,
            DHO_FONT_SERVERS,					OptionFormat.INETS,
            DHO_X_DISPLAY_MANAGER,				OptionFormat.INETS,
            DHO_DHCP_REQUESTED_ADDRESS,			OptionFormat.INET,
            DHO_DHCP_LEASE_TIME,				OptionFormat.INT,
            DHO_DHCP_OPTION_OVERLOAD,			OptionFormat.BYTE,
            DHO_DHCP_MESSAGE_TYPE,				OptionFormat.BYTE,
            DHO_DHCP_SERVER_IDENTIFIER,			OptionFormat.INET,
            DHO_DHCP_PARAMETER_REQUEST_LIST,	OptionFormat.BYTES,
            DHO_DHCP_MESSAGE,					OptionFormat.STRING,
            DHO_DHCP_MAX_MESSAGE_SIZE,			OptionFormat.SHORT,
            DHO_DHCP_RENEWAL_TIME,				OptionFormat.INT,
            DHO_DHCP_REBINDING_TIME,			OptionFormat.INT,
            DHO_VENDOR_CLASS_IDENTIFIER,		OptionFormat.STRING,
            DHO_NWIP_DOMAIN_NAME,				OptionFormat.STRING,
            DHO_NISPLUS_DOMAIN,					OptionFormat.STRING,
            DHO_NISPLUS_SERVER,					OptionFormat.STRING,
            DHO_TFTP_SERVER,					OptionFormat.STRING,
            DHO_BOOTFILE,						OptionFormat.STRING,
            DHO_MOBILE_IP_HOME_AGENT,			OptionFormat.INETS,
            DHO_SMTP_SERVER,					OptionFormat.INETS,
            DHO_POP3_SERVER,					OptionFormat.INETS,
            DHO_NNTP_SERVER,					OptionFormat.INETS,
            DHO_WWW_SERVER,						OptionFormat.INETS,
            DHO_FINGER_SERVER,					OptionFormat.INETS,
            DHO_IRC_SERVER,						OptionFormat.INETS,
            DHO_STREETTALK_SERVER,				OptionFormat.INETS,
            DHO_STDA_SERVER,					OptionFormat.INETS,
            DHO_NDS_SERVERS,					OptionFormat.INETS,
            DHO_NDS_TREE_NAME,					OptionFormat.STRING,
            DHO_NDS_CONTEXT,					OptionFormat.STRING,
            DHO_CLIENT_LAST_TRANSACTION_TIME,	OptionFormat.INT,
            DHO_ASSOCIATED_IP,					OptionFormat.INETS,
            DHO_USER_AUTHENTICATION_PROTOCOL,	OptionFormat.STRING,
            DHO_AUTO_CONFIGURE,					OptionFormat.BYTE,
            DHO_NAME_SERVICE_SEARCH,			OptionFormat.SHORTS,
            DHO_SUBNET_SELECTION,				OptionFormat.INET,
            DHO_DOMAIN_SEARCH,					OptionFormat.STRING,
    };    
    static final Map<Byte, OptionFormat> _DHO_FORMATS = new LinkedHashMap<Byte, OptionFormat>();

    /*
     * preload at startup Maps with constants
     * allowing reverse lookup
     */
    static {
        // construct map of formats
        for (int i = 0; i < _OPTION_FORMATS.length / 2; i++) {
            _DHO_FORMATS.put((Byte) _OPTION_FORMATS[i * 2], (OptionFormat) _OPTION_FORMATS[i * 2 + 1]);
        }
    }

    // ========================================================================
    // main: print DHCP options for Javadoc
    public static void main(String[] args)
    {
        String all = "";
        String inet1 = "";
        String inets = "";
        String int1 = "";
        String short1 = "";
        String shorts = "";
        String byte1 = "";
        String bytes = "";
        String string1 = "";

        for (Byte codeByte : _DHO_NAMES.keySet()) {
            byte code = codeByte.byteValue();
            String s = "";
            if (code != DHO_PAD && code != DHO_END) {
                s = " * " + _DHO_NAMES.get(codeByte) + '(' + (code & 0xFF) + ")\n";
            }

            all += s;
            if (_DHO_FORMATS.containsKey(codeByte)) {
                switch (_DHO_FORMATS.get(codeByte)) {
                    case INET:
                        inet1 += s;
                        break;
                    case INETS:
                        inets += s;
                        break;
                    case INT:
                        int1 += s;
                        break;
                    case SHORT:
                        short1 += s;
                        break;
                    case SHORTS:
                        shorts += s;
                        break;
                    case BYTE:
                        byte1 += s;
                        break;
                    case BYTES:
                        bytes += s;
                        break;
                    case STRING:
                        string1 += s;
                        break;
                    default:
                }
            }
        }

        System.out.println("---All codes---");
        System.out.println(all);
        System.out.println("---INET---");
        System.out.println(inet1);
        System.out.println("---INETS---");
        System.out.println(inets);
        System.out.println("---INT---");
        System.out.println(int1);
        System.out.println("---SHORT---");
        System.out.println(short1);
        System.out.println("---SHORTS---");
        System.out.println(shorts);
        System.out.println("---BYTE---");
        System.out.println(byte1);
        System.out.println("---BYTES---");
        System.out.println(bytes);
        System.out.println("---STRING---");
        System.out.println(string1);
    }
}
