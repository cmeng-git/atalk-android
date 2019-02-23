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

import org.atalk.android.plugin.timberlog.TimberLog;

import java.io.*;
import java.net.*;
import java.util.*;

import timber.log.Timber;

import static org.dhcp4java.DHCPConstants.BOOTREPLY;
import static org.dhcp4java.DHCPConstants.DHO_DHCP_MESSAGE_TYPE;
import static org.dhcp4java.DHCPConstants.DHO_END;
import static org.dhcp4java.DHCPConstants.DHO_PAD;
import static org.dhcp4java.DHCPConstants.HTYPE_ETHER;
import static org.dhcp4java.DHCPConstants._BOOTP_ABSOLUTE_MIN_LEN;
import static org.dhcp4java.DHCPConstants._BOOTP_VEND_SIZE;
import static org.dhcp4java.DHCPConstants._BOOT_NAMES;
import static org.dhcp4java.DHCPConstants._DHCP_DEFAULT_MAX_LEN;
import static org.dhcp4java.DHCPConstants._DHCP_MAX_MTU;
import static org.dhcp4java.DHCPConstants._HTYPE_NAMES;
import static org.dhcp4java.DHCPConstants._MAGIC_COOKIE;

/**
 * The basic class for manipulating DHCP packets.
 *
 * @author Stephan Hadinger
 * @version 1.00
 *
 * <p>There are two basic ways to build a new DHCPPacket object.
 * <p>First one is to build an object from scratch using the constructor and setters.
 * If you need to set repeatedly the same set of parameters and options,
 * you can create a "master" object and clone it many times.
 *
 * <pre>
 * DHCPPacket discover = new DHCPPacket();
 * discover.setOp(DHCPPacket.BOOTREQUEST);
 * discover.setHtype(DHCPPacket.HTYPE_ETHER);
 * discover.setHlen((byte) 6);
 * discover.setHops((byte) 0);
 * discover.setXid( (new Random()).nextInt() );
 * ...
 * </pre>
 * Second is to decode a DHCP datagram received from the network.
 * In this case, the object is created through a factory.
 *
 * <p>Example: simple DHCP sniffer
 * <pre>
 * DatagramSocket socket = new DatagramSocket(67);
 * while (true) {
 *     DatagramPacket pac = new DatagramPacket(new byte[1500], 1500);
 *     socket.receive(pac);
 *     DHCPPacket dhcp = DHCPPacket.getPacket(pac);
 *     System.out.println(dhcp.toString());
 * }
 * </pre>
 * In this second way, beware that a <tt>BadPacketExpcetion</tt> is thrown
 * if the datagram contains invalid DHCP data.
 *
 *
 * <p><b>Getters and Setters</b>: methods are provided with high-level data structures
 * wherever it is possible (String, InetAddress...). However there are also low-overhead
 * version (suffix <tt>Raw</tt>) dealing directly with <tt>byte[]</tt> for maximum performance.
 * They are useful in servers for copying parameters in a servers from a request to a response without
 * any type conversion. All parameters are copies, you may modify them as you like without
 * any side-effect on the <tt>DHCPPacket</tt> object.
 *
 * <h4>DHCP datagram format description:</h4>
 * <blockquote><table cellspacing=2>
 * <tr><th>Field</th><th>Octets</th><th>Description</th></tr>
 * <tr><td valign=top><tt>op</tt></td><td valign=top>1</td>
 * <td>Message op code / message type.<br>
 * use constants
 * <tt>BOOTREQUEST</tt>,
 * <tt>BOOTREPLY</tt></td></tr>
 * <tr><td valign=top><tt>htype</tt></td>
 * <td valign=top>1</td><td>Hardware address type, see ARP section in
 * "Assigned Numbers" RFC<br>
 * use constants
 * <tt>HTYPE_ETHER</tt>,
 * <tt>HTYPE_IEEE802</tt>,
 * <tt>HTYPE_FDDI</tt></td></tr>
 * <tr><td valign=top><tt>hlen</tt></td><td>1</td><td>Hardware address length
 * (e.g.  '6' for ethernet).</td></tr>
 * <tr><td valign=top><tt>hops</tt></td><td valign=top>1</td><td>Client sets to zero, optionally used
 * by relay agents when booting via a relay agent.</td></tr>
 * <tr><td valign=top><tt>xid</tt></td><td valign=top>4</td>
 * <td>Transaction ID, a random number chosen by the
 * client, used by the client and server to associate
 * messages and responses between a client and a
 * server.</td></tr>
 * <tr><td valign=top><tt>secs</tt></td><td valign=top>2</td>
 * <td>Filled in by client, seconds elapsed since client
 * began address acquisition or renewal process.</td></tr>
 * <tr><td valign=top><tt>flags</tt></td><td valign=top>2</td>
 * <td>Flags (see below).</td></tr>
 * <tr><td valign=top><tt>ciaddr</tt></td><td valign=top>4</td>
 * <td>Client IP address; only filled in if client is in
 * BOUND, RENEW or REBINDING state and can respond
 * to ARP requests.</td></tr>
 * <tr><td valign=top><tt>yiaddr</tt></td><td valign=top>4</td>
 * <td>'your' (client) IP address.</td></tr>
 * <tr><td valign=top><tt>siaddr</tt></td><td valign=top>4</td>
 * <td>IP address of next server to use in bootstrap;
 * returned in DHCPOFFER, DHCPACK by server.</td></tr>
 * <tr><td valign=top><tt>giaddr</tt></td><td valign=top>4</td>
 * <td>Relay agent IP address, used in booting via a
 * relay agent.</td></tr>
 * <tr><td valign=top><tt>chaddr</tt></td><td valign=top>16</td>
 * <td>Client hardware address.</td></tr>
 * <tr><td valign=top><tt>sname</tt></td><td valign=top>64</td>
 * <td>Optional server host name, null terminated string.</td></tr>
 * <tr><td valign=top><tt>file</tt></td><td valign=top>128</td>
 * <td>Boot file name, null terminated string; "generic"
 * name or null in DHCPDISCOVER, fully qualified
 * directory-path name in DHCPOFFER.</td></tr>
 * <tr><td valign=top><tt>isDhcp</tt></td><td valign=top>4</td>
 * <td>Controls whether the packet is BOOTP or DHCP.
 * DHCP contains the "magic cookie" of 4 bytes.
 * 0x63 0x82 0x53 0x63.</td></tr>
 * <tr><td valign=top><tt>DHO_*code*</tt></td><td valign=top>*</td>
 * <td>Optional parameters field.  See the options
 * documents for a list of defined options. See below.</td></tr>
 * <tr><td valign=top><tt>padding</tt></td><td valign=top>*</td>
 * <td>Optional padding at the end of the packet.</td></tr>
 * </table></blockquote>
 *
 * <h4>DHCP Option</h4>
 *
 * The following options are codes are supported:
 * <pre>
 * DHO_SUBNET_MASK(1)
 * DHO_TIME_OFFSET(2)
 * DHO_ROUTERS(3)
 * DHO_TIME_SERVERS(4)
 * DHO_NAME_SERVERS(5)
 * DHO_DOMAIN_NAME_SERVERS(6)
 * DHO_LOG_SERVERS(7)
 * DHO_COOKIE_SERVERS(8)
 * DHO_LPR_SERVERS(9)
 * DHO_IMPRESS_SERVERS(10)
 * DHO_RESOURCE_LOCATION_SERVERS(11)
 * DHO_HOST_NAME(12)
 * DHO_BOOT_SIZE(13)
 * DHO_MERIT_DUMP(14)
 * DHO_DOMAIN_NAME(15)
 * DHO_SWAP_SERVER(16)
 * DHO_ROOT_PATH(17)
 * DHO_EXTENSIONS_PATH(18)
 * DHO_IP_FORWARDING(19)
 * DHO_NON_LOCAL_SOURCE_ROUTING(20)
 * DHO_POLICY_FILTER(21)
 * DHO_MAX_DGRAM_REASSEMBLY(22)
 * DHO_DEFAULT_IP_TTL(23)
 * DHO_PATH_MTU_AGING_TIMEOUT(24)
 * DHO_PATH_MTU_PLATEAU_TABLE(25)
 * DHO_INTERFACE_MTU(26)
 * DHO_ALL_SUBNETS_LOCAL(27)
 * DHO_BROADCAST_ADDRESS(28)
 * DHO_PERFORM_MASK_DISCOVERY(29)
 * DHO_MASK_SUPPLIER(30)
 * DHO_ROUTER_DISCOVERY(31)
 * DHO_ROUTER_SOLICITATION_ADDRESS(32)
 * DHO_STATIC_ROUTES(33)
 * DHO_TRAILER_ENCAPSULATION(34)
 * DHO_ARP_CACHE_TIMEOUT(35)
 * DHO_IEEE802_3_ENCAPSULATION(36)
 * DHO_DEFAULT_TCP_TTL(37)
 * DHO_TCP_KEEPALIVE_INTERVAL(38)
 * DHO_TCP_KEEPALIVE_GARBAGE(39)
 * DHO_NIS_SERVERS(41)
 * DHO_NTP_SERVERS(42)
 * DHO_VENDOR_ENCAPSULATED_OPTIONS(43)
 * DHO_NETBIOS_NAME_SERVERS(44)
 * DHO_NETBIOS_DD_SERVER(45)
 * DHO_NETBIOS_NODE_TYPE(46)
 * DHO_NETBIOS_SCOPE(47)
 * DHO_FONT_SERVERS(48)
 * DHO_X_DISPLAY_MANAGER(49)
 * DHO_DHCP_REQUESTED_ADDRESS(50)
 * DHO_DHCP_LEASE_TIME(51)
 * DHO_DHCP_OPTION_OVERLOAD(52)
 * DHO_DHCP_MESSAGE_TYPE(53)
 * DHO_DHCP_SERVER_IDENTIFIER(54)
 * DHO_DHCP_PARAMETER_REQUEST_LIST(55)
 * DHO_DHCP_MESSAGE(56)
 * DHO_DHCP_MAX_MESSAGE_SIZE(57)
 * DHO_DHCP_RENEWAL_TIME(58)
 * DHO_DHCP_REBINDING_TIME(59)
 * DHO_VENDOR_CLASS_IDENTIFIER(60)
 * DHO_DHCP_CLIENT_IDENTIFIER(61)
 * DHO_NWIP_DOMAIN_NAME(62)
 * DHO_NWIP_SUBOPTIONS(63)
 * DHO_NIS_DOMAIN(64)
 * DHO_NIS_SERVER(65)
 * DHO_TFTP_SERVER(66)
 * DHO_BOOTFILE(67)
 * DHO_MOBILE_IP_HOME_AGENT(68)
 * DHO_SMTP_SERVER(69)
 * DHO_POP3_SERVER(70)
 * DHO_NNTP_SERVER(71)
 * DHO_WWW_SERVER(72)
 * DHO_FINGER_SERVER(73)
 * DHO_IRC_SERVER(74)
 * DHO_STREETTALK_SERVER(75)
 * DHO_STDA_SERVER(76)
 * DHO_USER_CLASS(77)
 * DHO_FQDN(81)
 * DHO_DHCP_AGENT_OPTIONS(82)
 * DHO_NDS_SERVERS(85)
 * DHO_NDS_TREE_NAME(86)
 * DHO_USER_AUTHENTICATION_PROTOCOL(98)
 * DHO_AUTO_CONFIGURE(116)
 * DHO_NAME_SERVICE_SEARCH(117)
 * DHO_SUBNET_SELECTION(118)
 * </pre>
 *
 * <p>These options can be set and get through basic low-level <tt>getOptionRaw</tt> and
 * <tt>setOptionRaw</tt> passing <tt>byte[]</tt> structures. Using these functions, data formats
 * are under your responsibility. Arrays are always passed by copies (clones) so you can modify
 * them freely without side-effects. These functions allow maximum performance, especially
 * when copying options from a request datagram to a response datagram.
 *
 * <h4>Special case: DHO_DHCP_MESSAGE_TYPE</h4>
 * The DHCP Message Type (option 53) is supported for the following values
 * <pre>
 * DHCPDISCOVER(1)
 * DHCPOFFER(2)
 * DHCPREQUEST(3)
 * DHCPDECLINE(4)
 * DHCPACK(5)
 * DHCPNAK(6)
 * DHCPRELEASE(7)
 * DHCPINFORM(8)
 * DHCPFORCERENEW(9)
 * DHCPLEASEQUERY(13)
 * </pre>
 *
 * <h4>DHCP option formats</h4>
 *
 * A limited set of higher level data-structures are supported. Type checking is enforced
 * according to rfc 2132. Check corresponding methods for a list of option codes allowed for
 * each datatype.
 *
 * <blockquote>
 * <br>Inet (4 bytes - IPv4 address)
 * <br>Inets (X*4 bytes - list of IPv4 addresses)
 * <br>Short (2 bytes - short)
 * <br>Shorts (X*2 bytes - list of shorts)
 * <br>Byte (1 byte)
 * <br>Bytes (X bytes - list of 1 byte parameters)
 * <br>String (X bytes - ASCII string)
 * <br>
 * </blockquote>
 *
 *
 * <p><b>Note</b>: this class is not synchronized for maximum performance.
 * However, it is unlikely that the same <tt>DHCPPacket</tt> is used in two different
 * threads in real life DHPC servers or clients. Multi-threading acces
 * to an instance of this class is at your own risk.
 *
 * <p><b>Limitations</b>: this class doesn't support spanned options or options longer than 256 bytes.
 * It does not support options stored in <tt>sname</tt> or <tt>file</tt> fields.
 *
 * <p>This API is originally a port from my PERL
 * <tt><a href="http://search.cpan.org/~shadinger/">Net::DHCP</a></tt> api.
 *
 * <p><b>Future extensions</b>: IPv6 support, extended data structure TODO...
 */
public class DHCPPacket implements Cloneable, Serializable
{
    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------------------
    // user defined comment
    private String comment;   // Free user-defined comment

    // ----------------------------------------------------------------------
    // static structure of the packet
    private byte op;        // Op code
    private byte htype;     // HW address Type
    private byte hlen;      // hardware address length
    private byte hops;      // Hw options
    private int xid;       // transaction id
    private short secs;      // elapsed time from trying to boot
    private short flags;     // flags
    private byte[] ciaddr;    // client IP
    private byte[] yiaddr;    // your client IP
    private byte[] siaddr;    // Server IP
    private byte[] giaddr;    // relay agent IP
    private byte[] chaddr;    // Client HW address
    private byte[] sname;     // Optional server host name
    private byte[] file;      // Boot file name

    // ----------------------------------------------------------------------
    // options part of the packet

    // DHCP options
    // Invariant 1: K is identical to V.getCode()
    // Invariant 2: V.value is never <tt>null</tt>
    // Invariant 3; K is not 0 (PAD) and not -1 (END)
    private Map<Byte, DHCPOption> options;
    private boolean isDhcp;    // well-formed DHCP Packet ?
    private boolean truncated; // are the option truncated
    // ----------------------------------------------------------------------
    // extra bytes for padding
    private byte[] padding;    // end of packet padding

    // ----------------------------------------------------------------------
    // Address/port address of the machine, which this datagram is being sent to
    // or received from.
    private InetAddress address;
    private int port;


    /**
     * Constructor for the <tt>DHCPPacket</tt> class.
     *
     * <p>This creates an empty <tt>DHCPPacket</tt> datagram.
     * All data is default values and the packet is still lacking key data
     * to be sent on the wire.
     */
    public DHCPPacket()
    {
        this.comment = "";
        this.op = BOOTREPLY;
        this.htype = HTYPE_ETHER;
        this.hlen = 6;
        this.ciaddr = new byte[4];
        this.yiaddr = new byte[4];
        this.siaddr = new byte[4];
        this.giaddr = new byte[4];
        this.chaddr = new byte[16];
        this.sname = new byte[64];
        this.file = new byte[128];
        this.padding = new byte[0];
        this.isDhcp = true;
        this.options = new LinkedHashMap<Byte, DHCPOption>();
    }

    /**
     * Factory for creating <tt>DHCPPacket</tt> objects by parsing a
     * <tt>DatagramPacket</tt> object.
     *
     * @param datagram the UDP datagram received to be parsed
     * @return the newly create <tt>DHCPPacket</tt> instance
     * @throws DHCPBadPacketException the datagram is malformed and cannot be parsed properly.
     * @throws IllegalArgumentException datagram is <tt>null</tt>
     * @throws IOException
     */
    public static DHCPPacket getPacket(DatagramPacket datagram)
            throws DHCPBadPacketException
    {
        if (datagram == null) {
            throw new IllegalArgumentException("datagram is null");
        }
        DHCPPacket packet = new DHCPPacket();
        // all parameters are checked in marshall()
        packet.marshall(datagram.getData(), datagram.getOffset(), datagram.getLength(),
                datagram.getAddress(), datagram.getPort(),
                true);        // strict mode by default
        return packet;
    }

    /**
     * Factory for creating <tt>DHCPPacket</tt> objects by parsing a
     * <tt>byte[]</tt> e.g. from a datagram.
     *
     * <p>This method allows you to specify non-strict mode which is much more
     * tolerant for packet options. By default, any problem seen during DHCP option
     * parsing causes a DHCPBadPacketException to be thrown.
     *
     * @param buf buffer for holding the incoming datagram.
     * @param offset the offset for the buffer.
     * @param length the number of bytes to read.
     * @param strict do we parse in strict mode?
     * @return the newly create <tt>DHCPPacket</tt> instance
     * @throws DHCPBadPacketException the datagram is malformed.
     */
    public static DHCPPacket getPacket(byte[] buf, int offset, int length, boolean strict)
            throws DHCPBadPacketException
    {
        DHCPPacket packet = new DHCPPacket();
        // all parameters are checked in marshall()
        packet.marshall(buf, offset, length, null, 0, strict);
        return packet;
    }

    /**
     * Returns a copy of this <tt>DHCPPacket</tt>.
     *
     * <p>The <tt>truncated</tt> flag is reset.
     *
     * @return a copy of the <tt>DHCPPacket</tt> instance.
     */
    @Override
    public DHCPPacket clone()
    {
        try {
            DHCPPacket p = (DHCPPacket) super.clone();

            // specifically cloning arrays to avoid side-effects
            p.ciaddr = this.ciaddr.clone();
            p.yiaddr = this.yiaddr.clone();
            p.siaddr = this.siaddr.clone();
            p.giaddr = this.giaddr.clone();
            p.chaddr = this.chaddr.clone();
            p.sname = this.sname.clone();
            p.file = this.file.clone();
            //p.options = this.options.clone();
            p.options = new LinkedHashMap<Byte, DHCPOption>(this.options);
            p.padding = this.padding.clone();

            p.truncated = false;    // freshly new object, it is not considered as corrupt

            return p;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * Returns true if 2 instances of <tt>DHCPPacket</tt> represent the same DHCP packet.
     *
     * <p>This is a field by field comparison, except <tt>truncated</tt> which is ignored.
     */
    @Override
    public boolean equals(Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DHCPPacket)) {
            return false;
        }

        DHCPPacket p = (DHCPPacket) o;
        boolean b;

        b = (this.comment.equals(p.comment));
        b &= (this.op == p.op);
        b &= (this.htype == p.htype);
        b &= (this.hlen == p.hlen);
        b &= (this.hops == p.hops);
        b &= (this.xid == p.xid);
        b &= (this.secs == p.secs);
        b &= (this.flags == p.flags);
        b &= (Arrays.equals(this.ciaddr, p.ciaddr));
        b &= (Arrays.equals(this.yiaddr, p.yiaddr));
        b &= (Arrays.equals(this.siaddr, p.siaddr));
        b &= (Arrays.equals(this.giaddr, p.giaddr));
        b &= (Arrays.equals(this.chaddr, p.chaddr));
        b &= (Arrays.equals(this.sname, p.sname));
        b &= (Arrays.equals(this.file, p.file));
        b &= (this.options.equals(p.options));
        b &= (this.isDhcp == p.isDhcp);
        // we deliberately ignore "truncated" since it is reset when cloning
        b &= (Arrays.equals(this.padding, p.padding));
        b &= (equalsStatic(this.address, p.address));
        b &= (this.port == p.port);

        return b;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode()
    {
        int h = -1;
        h ^= this.comment.hashCode();
        h += this.op;
        h += this.htype;
        h += this.hlen;
        h += this.hops;
        h += this.xid;
        h += this.secs;
        h ^= this.flags;
        h ^= Arrays.hashCode(this.ciaddr);
        h ^= Arrays.hashCode(this.yiaddr);
        h ^= Arrays.hashCode(this.siaddr);
        h ^= Arrays.hashCode(this.giaddr);
        h ^= Arrays.hashCode(this.chaddr);
        h ^= Arrays.hashCode(this.sname);
        h ^= Arrays.hashCode(this.file);
        h ^= this.options.hashCode();
        h += this.isDhcp ? 1 : 0;
        //		h += this.truncated ? 1 : 0;
        h ^= Arrays.hashCode(this.padding);
        h ^= (this.address != null) ? this.address.hashCode() : 0;
        h += this.port;
        return h;
    }

    private static boolean equalsStatic(Object a, Object b)
    {
        return ((a == null) ? (b == null) : a.equals(b));
    }

    /**
     * Assert all the invariants of the object. For debug purpose only.
     */
    private void assertInvariants()
    {
        assert (this.comment != null);
        assert (this.ciaddr != null);
        assert (this.ciaddr.length == 4);
        assert (this.yiaddr != null);
        assert (this.yiaddr.length == 4);
        assert (this.siaddr != null);
        assert (this.siaddr.length == 4);
        assert (this.giaddr != null);
        assert (this.giaddr.length == 4);
        // strings
        assert (this.chaddr != null);
        assert (this.chaddr.length == 16);
        assert (this.sname != null);
        assert (this.sname.length == 64);
        assert (this.file != null);
        assert (this.file.length == 128);
        assert (this.padding != null);    // length is free for padding
        // options
        assert (this.options != null);
        for (Map.Entry<Byte, DHCPOption> mapEntry : this.options.entrySet()) {
            Byte key = mapEntry.getKey();
            DHCPOption opt = mapEntry.getValue();

            assert (key != null);
            assert (key != DHO_PAD);
            assert (key != DHO_END);
            assert (opt != null);
            assert (opt.getCode() == key);
            assert (opt.getValueFast() != null);
        }
    }

    /**
     * Convert a specified byte array containing a DHCP message into a
     * DHCPMessage object.
     *
     * @param buffer byte array to convert to a DHCPMessage object
     * @param offset starting offset for the buffer
     * @param length length of the buffer
     * @param address0 the address from which the packet was sent, or <tt>null</tt>
     * @param port0 the port from which the packet was sent
     * @param strict do we read in strict mode?
     * @return a DHCPMessage object with information from byte array.
     * @throws IllegalArgumentException if buffer is <tt>null</tt>...
     * @throws IndexOutOfBoundsException offset..offset+length is out of buffer bounds
     * @throws DHCPBadPacketException datagram is malformed
     */
    protected DHCPPacket marshall(byte[] buffer, int offset, int length,
            InetAddress address0, int port0, boolean strict)
    {
        // do some basic sanity checks
        // ibuff, offset & length are valid?
        if (buffer == null) {
            throw new IllegalArgumentException("null buffer not allowed");
        }
        if (offset < 0) {
            throw new IndexOutOfBoundsException("negative offset not allowed");
        }
        if (length < 0) {
            throw new IllegalArgumentException("negative length not allowed");
        }
        if (buffer.length < offset + length) {
            throw new IndexOutOfBoundsException("offset+length exceeds buffer length");
        }

        // absolute minimum size for a valid packet
        if (length < _BOOTP_ABSOLUTE_MIN_LEN) {
            throw new DHCPBadPacketException("DHCP Packet too small (" + length +
                    ") absolute minimum is " + _BOOTP_ABSOLUTE_MIN_LEN);
        }
        // maximum size for a valid DHCP packet
        if (length > _DHCP_MAX_MTU) {
            throw new DHCPBadPacketException("DHCP Packet too big (" + length +
                    ") max MTU is " + _DHCP_MAX_MTU);
        }

        // copy address and port
        this.address = address0; // no need to clone, InetAddress is immutable
        this.port = port0;

        try {
            // turn buffer into a readable stream
            ByteArrayInputStream inBStream = new ByteArrayInputStream(buffer, offset, length);
            DataInputStream inStream = new DataInputStream(inBStream);

            // parse static part of packet
            this.op = inStream.readByte();
            this.htype = inStream.readByte();
            this.hlen = inStream.readByte();
            this.hops = inStream.readByte();
            this.xid = inStream.readInt();
            this.secs = inStream.readShort();
            this.flags = inStream.readShort();
            inStream.readFully(this.ciaddr, 0, 4);
            inStream.readFully(this.yiaddr, 0, 4);
            inStream.readFully(this.siaddr, 0, 4);
            inStream.readFully(this.giaddr, 0, 4);
            inStream.readFully(this.chaddr, 0, 16);
            inStream.readFully(this.sname, 0, 64);
            inStream.readFully(this.file, 0, 128);

            // check for DHCP MAGIC_COOKIE
            this.isDhcp = true;
            inBStream.mark(4);        // read ahead 4 bytes
            if (inStream.readInt() != _MAGIC_COOKIE) {
                this.isDhcp = false;
                inBStream.reset();    // re-read the 4 bytes
            }

            if (this.isDhcp) {    // is it a full DHCP packet or a simple BOOTP?
                // DHCP Packet: parsing options
                int type = 0;

                while (true) {
                    int r = inBStream.read();
                    if (r < 0) {
                        break;
                    } // EOF

                    type = (byte) r;

                    if (type == DHO_PAD) {
                        continue;
                    } // skip Padding
                    if (type == DHO_END) {
                        break;
                    } // break if end of options

                    r = inBStream.read();
                    if (r < 0) {
                        break;
                    } // EOF

                    int len = Math.min(r, inBStream.available());
                    byte[] unit_opt = new byte[len];
                    inBStream.read(unit_opt);

                    this.setOption(new DHCPOption((byte) type, unit_opt));  // store option
                }
                this.truncated = (type != DHO_END); // truncated options?
                if (strict && this.truncated) {
                    throw new DHCPBadPacketException("Packet seams to be truncated");
                }
            }

            // put the remaining in padding
            this.padding = new byte[inBStream.available()];
            inBStream.read(this.padding);
            // final verifications (if assertions are activated)
            this.assertInvariants();

            return this;
        } catch (IOException e) {
            // unlikely with ByteArrayInputStream
            throw new DHCPBadPacketException("IOException: " + e.toString(), e);
        }
    }

    /**
     * Converts the object to a byte array ready to be sent on the wire.
     *
     * <p>Default max size of resulting packet is 576, which is the maximum
     * size a client can accept without explicit notice (option XXX)
     *
     * @return a byte array with information from DHCPMessage object.
     * @throws DHCPBadPacketException the datagram would be malformed (too small, too big...)
     */
    public byte[] serialize()
    {
        int minLen = _BOOTP_ABSOLUTE_MIN_LEN;

        if (this.isDhcp) {
            // most other DHCP software seems to ensure that the BOOTP 'vend'
            // field is padded to at least 64 bytes
            minLen += _BOOTP_VEND_SIZE;
        }

        return serialize(minLen, _DHCP_DEFAULT_MAX_LEN);
    }

    /**
     * Converts the object to a byte array ready to be sent on the wire.
     *
     * @param maxSize the maximum buffer size in bytes
     * @return a byte array with information from DHCPMessage object.
     * @throws DHCPBadPacketException the datagram would be malformed (too small, too big...)
     */
    public byte[] serialize(int minSize, int maxSize)
    {
        this.assertInvariants();
        // prepare output buffer, pre-sized to maximum buffer length
        // default buffer is half the maximum size of possible packet
        // (this seams reasonable for most uses, worst case only doubles the buffer size once
        ByteArrayOutputStream outBStream = new ByteArrayOutputStream(_DHCP_MAX_MTU / 2);
        DataOutputStream outStream = new DataOutputStream(outBStream);
        try {
            outStream.writeByte(this.op);
            outStream.writeByte(this.htype);
            outStream.writeByte(this.hlen);
            outStream.writeByte(this.hops);
            outStream.writeInt(this.xid);
            outStream.writeShort(this.secs);
            outStream.writeShort(this.flags);
            outStream.write(this.ciaddr, 0, 4);
            outStream.write(this.yiaddr, 0, 4);
            outStream.write(this.siaddr, 0, 4);
            outStream.write(this.giaddr, 0, 4);
            outStream.write(this.chaddr, 0, 16);
            outStream.write(this.sname, 0, 64);
            outStream.write(this.file, 0, 128);

            if (this.isDhcp) {
                // DHCP and not BOOTP -> magic cookie required
                outStream.writeInt(_MAGIC_COOKIE);

                // parse output options in creation order (LinkedHashMap)
                for (DHCPOption opt : this.getOptionsCollection()) {
                    assert (opt != null);
                    assert (opt.getCode() != DHO_PAD);
                    assert (opt.getCode() != DHO_END);
                    assert (opt.getValueFast() != null);
                    int size = opt.getValueFast().length;
                    assert (size >= 0);
                    if (size > 255) {
                        throw new DHCPBadPacketException("Options larger than 255 bytes are not yet supported");
                    }
                    outStream.writeByte(opt.getCode());        // output option code
                    outStream.writeByte(size);    // output option length
                    outStream.write(opt.getValueFast());    // output option data
                }
                // mark end of options
                outStream.writeByte(DHO_END);
            }

            // write padding
            outStream.write(this.padding);

            // add padding if the packet is too small
            int min_padding = minSize - outBStream.size();
            if (min_padding > 0) {
                byte[] add_padding = new byte[min_padding];
                outStream.write(add_padding);
            }

            // final packet is here
            byte[] data = outBStream.toByteArray();

            // do some post sanity checks
            if (data.length > _DHCP_MAX_MTU) {
                throw new DHCPBadPacketException("serialize: packet too big (" + data.length + " greater than max MAX_MTU (" + _DHCP_MAX_MTU + ')');
            }

            return data;
        } catch (IOException e) {
            // nomrally impossible with ByteArrayOutputStream
            Timber.e(e, "Unexpected Exception");
            throw new DHCPBadPacketException("IOException raised: " + e.toString());
        }
    }

    // ========================================================================
    // debug functions

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
        StringBuilder buffer = new StringBuilder(); // output buffer

        try {
            buffer.append(this.isDhcp ? "DHCP Packet" : "BOOTP Packet")
                    .append("\ncomment=")
                    .append(this.comment)
                    .append("\naddress=")
                    .append(this.address != null ? this.address.getHostAddress() : "")
                    .append('(')
                    .append(this.port)
                    .append(')')
                    .append("\nop=");

            final Object bootName = _BOOT_NAMES.get(this.op);
            if (bootName != null) {
                buffer.append(bootName)
                        .append('(')
                        .append(this.op)
                        .append(')');
            }
            else {
                buffer.append(this.op);
            }

            buffer.append("\nhtype=");

            final Object htypeName = _HTYPE_NAMES.get(this.htype);
            if (htypeName != null) {
                buffer.append(htypeName)
                        .append('(')
                        .append(this.htype)
                        .append(')');
            }
            else {
                buffer.append(this.htype);
            }

            buffer.append("\nhlen=")
                    .append(this.hlen)
                    .append("\nhops=")
                    .append(this.hops)
                    .append("\nxid=0x");
            appendHex(buffer, this.xid);
            buffer.append("\nsecs=")
                    .append(this.secs)
                    .append("\nflags=0x")
                    .append(Integer.toHexString(this.flags))
                    .append("\nciaddr=");
            appendHostAddress(buffer, InetAddress.getByAddress(this.ciaddr));
            buffer.append("\nyiaddr=");
            appendHostAddress(buffer, InetAddress.getByAddress(this.yiaddr));
            buffer.append("\nsiaddr=");
            appendHostAddress(buffer, InetAddress.getByAddress(this.siaddr));
            buffer.append("\ngiaddr=");
            appendHostAddress(buffer, InetAddress.getByAddress(this.giaddr));
            buffer.append("\nchaddr=0x");
            this.appendChaddrAsHex(buffer);
            buffer.append("\nsname=")
                    .append(this.getSname())
                    .append("\nfile=")
                    .append(this.getFile());

            if (this.isDhcp) {
                buffer.append("\nOptions follows:");

                // parse options in creation order (LinkedHashMap)
                for (DHCPOption opt : this.getOptionsCollection()) {
                    buffer.append('\n');
                    opt.append(buffer);
                }
            }

            // padding
            buffer.append("\npadding[")
                    .append(this.padding.length)
                    .append("]=");
            appendHex(buffer, this.padding);
        } catch (Exception e) {
            // what to do ???
        }

        return buffer.toString();
    }

    // ========================================================================
    // getters and setters

    /**
     * Returns the comment associated to this packet.
     *
     * <p>This field can be used freely and has no influence on the real network datagram.
     * It can be used to store a transaction number or any other information
     *
     * @return the _comment field.
     */
    public String getComment()
    {
        return this.comment;
    }

    /**
     * Sets the comment associated to this packet.
     *
     * <p>This field can be used freely and has no influence on the real network datagram.
     * It can be used to store a transaction number or any other information
     *
     * @param comment The comment to set.
     */
    public void setComment(String comment)
    {
        this.comment = comment;
    }

    /**
     * Returns the chaddr field (Client hardware address - typically MAC address).
     *
     * <p>Returns the byte[16] raw buffer. Only the first <tt>hlen</tt> bytes are valid.
     *
     * @return the chaddr field.
     */
    public byte[] getChaddr()
    {
        return this.chaddr.clone();
    }

    /**
     * Appends the chaddr field (Client hardware address - typically MAC address) as
     * a hex string to this string buffer.
     *
     * <p>Only first <tt>hlen</tt> bytes are appended, as uppercase hex string.
     *
     * @param buffer this string buffer
     * @return the string buffer.
     */
    private StringBuilder appendChaddrAsHex(StringBuilder buffer)
    {
        appendHex(buffer, this.chaddr, 0, this.hlen & 0xFF);
        return buffer;
    }

    /**
     * Return the hardware address (@MAC) as an <tt>HardwareAddress</tt> object.
     *
     * @return the <tt>HardwareAddress</tt> object
     */
    public HardwareAddress getHardwareAddress()
    {
        int len = this.hlen & 0xff;
        if (len > 16) {
            len = 16;
        }
        byte[] buf = new byte[len];
        System.arraycopy(this.chaddr, 0, buf, 0, len);
        return new HardwareAddress(this.htype, buf);
    }

    /**
     * Returns the chaddr field (Client hardware address - typically MAC address) as
     * a hex string.
     *
     * <p>Only first <tt>hlen</tt> bytes are printed, as uppercase hex string.
     *
     * @return the chaddr field as hex string.
     */
    public String getChaddrAsHex()
    {
        return this.appendChaddrAsHex(new StringBuilder(this.hlen & 0xFF)).toString();
    }

    /**
     * Sets the chaddr field (Client hardware address - typically MAC address).
     *
     * <p>The buffer length should be between 0 and 16, otherwise an
     * <tt>IllegalArgumentException</tt> is thrown.
     *
     * <p>If chaddr is null, the field is filled with zeros.
     *
     * @param chaddr The chaddr to set.
     * @throws IllegalArgumentException chaddr buffer is longer than 16 bytes.
     */
    public void setChaddr(byte[] chaddr)
    {
        if (chaddr != null) {
            if (chaddr.length > this.chaddr.length) {
                throw new IllegalArgumentException("chaddr is too long: " + chaddr.length +
                        ", max is: " + this.chaddr.length);
            }
            Arrays.fill(this.chaddr, (byte) 0);
            System.arraycopy(chaddr, 0, this.chaddr, 0, chaddr.length);
        }
        else {
            Arrays.fill(this.chaddr, (byte) 0);
        }
    }

    /**
     * Sets the chaddr field - from an hex String.
     *
     * @param hex the chaddr in hex format
     */
    public void setChaddrHex(String hex)
    {
        this.setChaddr(hex2Bytes(hex));
    }

    /**
     * Returns the ciaddr field (Client IP Address).
     *
     * @return the ciaddr field converted to <tt>InetAddress</tt> object.
     */
    public InetAddress getCiaddr()
    {
        try {
            return InetAddress.getByAddress(this.getCiaddrRaw());
        } catch (UnknownHostException e) {
            Timber.e(e, "Unexpected UnknownHostException");
            return null;    // normaly impossible
        }
    }

    /**
     * Returns the ciaddr field (Client IP Address).
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return Returns the ciaddr as raw byte[4].
     */
    public byte[] getCiaddrRaw()
    {
        return this.ciaddr.clone();
    }

    /**
     * Sets the ciaddr field (Client IP Address).
     *
     * <p>Ths <tt>ciaddr</tt> field must be of <tt>Inet4Address</tt> class or
     * an <tt>IllegalArgumentException</tt> is thrown.
     *
     * @param ciaddr The ciaddr to set.
     */
    public void setCiaddr(InetAddress ciaddr)
    {
        if (!(ciaddr instanceof Inet4Address)) {
            throw new IllegalArgumentException("Inet4Address required");
        }
        this.setCiaddrRaw(ciaddr.getAddress());
    }

    /**
     * Sets the ciaddr field (Client IP Address).
     *
     * @param ciaddr The ciaddr to set.
     * @throws UnknownHostException
     */
    public void setCiaddr(String ciaddr)
            throws UnknownHostException
    {
        this.setCiaddr(InetAddress.getByName(ciaddr));
    }

    /**
     * Sets the ciaddr field (Client IP Address).
     *
     * <p><tt>ciaddr</tt> must be a 4 bytes array, or an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to <tt>ciaddr</tt>
     * parameter has no side effect.
     *
     * @param ciaddr The ciaddr to set.
     */
    public void setCiaddrRaw(byte[] ciaddr)
    {
        if (ciaddr.length != 4) {
            throw new IllegalArgumentException("4-byte array required");
        }
        System.arraycopy(ciaddr, 0, this.ciaddr, 0, 4);
    }

    /**
     * Returns the file field (Boot File Name).
     *
     * <p>Returns the raw byte[128] buffer, containing a null terminated string.
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return the file field.
     */
    public byte[] getFileRaw()
    {
        return this.file.clone();
    }

    /**
     * Returns the file field (Boot File Name) as String.
     *
     * @return the file converted to a String (transparent encoding).
     */
    public String getFile()
    {
        return bytesToString(this.getFileRaw());
    }

    /**
     * Sets the file field (Boot File Name) as String.
     *
     * <p>The string is first converted to a byte[] array using transparent
     * encoding. If the resulting buffer size is > 128, an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>If <tt>file</tt> parameter is null, the buffer is filled with zeros.
     *
     * @param file The file field to set.
     * @throws IllegalArgumentException string too long
     */
    public void setFile(String file)
    {
        this.setFileRaw(stringToBytes(file));
    }

    /**
     * Sets the file field (Boot File Name) as String.
     *
     * <p>If the buffer size is > 128, an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>If <tt>file</tt> parameter is null, the buffer is filled with zeros.
     *
     * <p>This is the low-level maximum performance setter for this field.
     *
     * @param file The file field to set.
     * @throws IllegalArgumentException string too long
     */
    public void setFileRaw(byte[] file)
    {
        if (file != null) {
            if (file.length > this.file.length) {
                throw new IllegalArgumentException("File is too long:" + file.length + " max is:" + this.file.length);
            }
            Arrays.fill(this.file, (byte) 0);
            System.arraycopy(file, 0, this.file, 0, file.length);
        }
        else {
            Arrays.fill(this.file, (byte) 0);
        }
    }

    /**
     * Returns the flags field.
     *
     * @return the flags field.
     */
    public short getFlags()
    {
        return this.flags;
    }

    /**
     * Sets the flags field.
     *
     * @param flags The flags field to set.
     */
    public void setFlags(short flags)
    {
        this.flags = flags;
    }

    /**
     * Returns the giaddr field (Relay agent IP address).
     *
     * @return the giaddr field converted to <tt>InetAddress</tt> object.
     */
    public InetAddress getGiaddr()
    {
        try {
            return InetAddress.getByAddress(this.getGiaddrRaw());
        } catch (UnknownHostException e) {
            Timber.e(e, "Unexpected UnknownHostException");
            return null;    // normaly impossible
        }
    }

    /**
     * Returns the giaddr field (Relay agent IP address).
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return Returns the giaddr as raw byte[4].
     */
    public byte[] getGiaddrRaw()
    {
        return this.giaddr.clone();
    }

    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     * <p>Ths <tt>giaddr</tt> field must be of <tt>Inet4Address</tt> class or
     * an <tt>IllegalArgumentException</tt> is thrown.
     *
     * @param giaddr The giaddr to set.
     */
    public void setGiaddr(InetAddress giaddr)
    {
        if (!(giaddr instanceof Inet4Address)) {
            throw new IllegalArgumentException("Inet4Address required");
        }
        this.setGiaddrRaw(giaddr.getAddress());
    }

    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     * @param giaddr The giaddr to set.
     * @throws UnknownHostException
     */
    public void setGiaddr(String giaddr)
            throws UnknownHostException
    {
        this.setGiaddr(InetAddress.getByName(giaddr));
    }

    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     * <p><tt>giaddr</tt> must be a 4 bytes array, or an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to <tt>ciaddr</tt>
     * parameter has no side effect.
     *
     * @param giaddr The giaddr to set.
     */
    public void setGiaddrRaw(byte[] giaddr)
    {
        if (giaddr.length != 4) {
            throw new IllegalArgumentException("4-byte array required");
        }
        System.arraycopy(giaddr, 0, this.giaddr, 0, 4);
    }

    /**
     * Returns the hlen field (Hardware address length).
     *
     * <p>Typical value is 6 for ethernet - 6 bytes MAC address.
     *
     * @return the hlen field.
     */
    public byte getHlen()
    {
        return this.hlen;
    }

    /**
     * Sets the hlen field (Hardware address length).
     *
     * <p>Typical value is 6 for ethernet - 6 bytes MAC address.
     *
     * <p>hlen value should be between 0 and 16, but no control is done here.
     *
     * @param hlen The hlen to set.
     */
    public void setHlen(byte hlen)
    {
        this.hlen = hlen;
    }

    /**
     * Returns the hops field.
     *
     * @return the hops field.
     */
    public byte getHops()
    {
        return this.hops;
    }

    /**
     * Sets the hops field.
     *
     * @param hops The hops to set.
     */
    public void setHops(byte hops)
    {
        this.hops = hops;
    }

    /**
     * Returns the htype field (Hardware address length).
     *
     * <p>Predefined values are:
     * <pre>
     * HTYPE_ETHER (1)
     * HTYPE_IEEE802 (6)
     * HTYPE_FDDI (8)
     * </pre>
     *
     * <p>Typical value is <tt>HTYPE_ETHER</tt>.
     *
     * @return the htype field.
     */
    public byte getHtype()
    {
        return this.htype;
    }

    /**
     * Sets the htype field (Hardware address length).
     *
     * <p>Predefined values are:
     * <pre>
     * HTYPE_ETHER (1)
     * HTYPE_IEEE802 (6)
     * HTYPE_FDDI (8)
     * </pre>
     *
     * <p>Typical value is <tt>HTYPE_ETHER</tt>.
     *
     * @param htype The htype to set.
     */
    public void setHtype(byte htype)
    {
        this.htype = htype;
    }

    /**
     * Returns whether the packet is DHCP or BOOTP.
     *
     * <p>It indicates the presence of the DHCP Magic Cookie at the end
     * of the BOOTP portion.
     *
     * <p>Default is <tt>true</tt> for a brand-new object.
     *
     * @return Returns the isDhcp.
     */
    public boolean isDhcp()
    {
        return this.isDhcp;
    }

    /**
     * Sets the isDhcp flag.
     *
     * <p>Indicates whether to generate a DHCP or a BOOTP packet. If <tt>true</tt>
     * the DHCP Magic Cookie is added after the BOOTP portion and before the
     * DHCP Options.
     *
     * <p>If <tt>isDhcp</tt> if false, all DHCP options are ignored when calling
     * <tt>serialize()</tt>.
     *
     * <p>Default value is <tt>true</tt>.
     *
     * @param isDhcp The isDhcp to set.
     */
    public void setDhcp(boolean isDhcp)
    {
        this.isDhcp = isDhcp;
    }

    /**
     * Returns the op field (Message op code).
     *
     * <p>Predefined values are:
     * <pre>
     * BOOTREQUEST (1)
     * BOOTREPLY (2)
     * </pre>
     *
     * @return the op field.
     */
    public byte getOp()
    {
        return this.op;
    }

    /**
     * Sets the op field (Message op code).
     *
     * <p>Predefined values are:
     * <pre>
     * BOOTREQUEST (1)
     * BOOTREPLY (2)
     * </pre>
     *
     * <p>Default value is <tt>BOOTREPLY</tt>, suitable for server replies.
     *
     * @param op The op to set.
     */
    public void setOp(byte op)
    {
        this.op = op;
    }

    /**
     * Returns the padding portion of the packet.
     *
     * <p>This byte array follows the DHCP Options.
     * Normally, its content is irrelevant.
     *
     * @return Returns the padding.
     */
    public byte[] getPadding()
    {
        return this.padding.clone();
    }

    /**
     * Sets the padding buffer.
     *
     * <p>This byte array follows the DHCP Options.
     * Normally, its content is irrelevant.
     *
     * <p>If <tt>paddig</tt> is null, it is set to an empty buffer.
     *
     * <p>Padding is automatically added at the end of the datagram when calling
     * <tt>serialize()</tt> to match DHCP minimal packet size.
     *
     * @param padding The padding to set.
     */
    public void setPadding(byte[] padding)
    {
        this.padding = ((padding == null) ? new byte[0] : padding.clone());
    }

    /**
     * Sets the padding buffer with <tt>length</tt> zero bytes.
     *
     * <p>This is a short cut for <tt>setPadding(new byte[length])</tt>.
     *
     * @param length size of the padding buffer
     */
    public void setPaddingWithZeroes(int length)
    {
        if (length < 0) {
            length = 0;
        }
        if (length > _DHCP_MAX_MTU) {
            throw new IllegalArgumentException("length is > " + _DHCP_MAX_MTU);
        }
        this.setPadding(new byte[length]);
    }

    /**
     * Returns the secs field (seconds elapsed).
     *
     * @return the secs field.
     */
    public short getSecs()
    {
        return this.secs;
    }

    /**
     * Sets the secs field (seconds elapsed).
     *
     * @param secs The secs to set.
     */
    public void setSecs(short secs)
    {
        this.secs = secs;
    }

    /**
     * Returns the siaddr field (IP address of next server).
     *
     * @return the siaddr field converted to <tt>InetAddress</tt> object.
     */
    public InetAddress getSiaddr()
    {
        try {
            return InetAddress.getByAddress(this.getSiaddrRaw());
        } catch (UnknownHostException e) {
            Timber.log(TimberLog.FINER, e, "Unexpected UnknownHostException");
            return null;    // normaly impossible
        }
    }

    /**
     * Returns the siaddr field (IP address of next server).
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return Returns the siaddr as raw byte[4].
     */
    public byte[] getSiaddrRaw()
    {
        return this.siaddr.clone();
    }

    /**
     * Sets the siaddr field (IP address of next server).
     *
     * <p>Ths <tt>siaddr</tt> field must be of <tt>Inet4Address</tt> class or
     * an <tt>IllegalArgumentException</tt> is thrown.
     *
     * @param siaddr The siaddr to set.
     */
    public void setSiaddr(InetAddress siaddr)
    {
        if (!(siaddr instanceof Inet4Address)) {
            throw new IllegalArgumentException("Inet4Address required");
        }
        this.setSiaddrRaw(siaddr.getAddress());
    }

    /**
     * Sets the siaddr field (IP address of next server).
     *
     * @param siaddr The siaddr to set.
     * @throws UnknownHostException
     */
    public void setSiaddr(String siaddr)
            throws UnknownHostException
    {
        this.setSiaddr(InetAddress.getByName(siaddr));
    }

    /**
     * Sets the siaddr field (IP address of next server).
     *
     * <p><tt>siaddr</tt> must be a 4 bytes array, or an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to <tt>ciaddr</tt>
     * parameter has no side effect.
     *
     * @param siaddr The siaddr to set.
     */
    public void setSiaddrRaw(byte[] siaddr)
    {
        if (siaddr.length != 4) {
            throw new IllegalArgumentException("4-byte array required");
        }
        System.arraycopy(siaddr, 0, this.siaddr, 0, 4);
    }

    /**
     * Returns the sname field (Optional server host name).
     *
     * <p>Returns the raw byte[64] buffer, containing a null terminated string.
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return the sname field.
     */
    public byte[] getSnameRaw()
    {
        return this.sname.clone();
    }

    /**
     * Returns the sname field (Optional server host name) as String.
     *
     * @return the sname converted to a String (transparent encoding).
     */
    public String getSname()
    {
        return bytesToString(this.getSnameRaw());
    }

    /**
     * Sets the sname field (Optional server host name) as String.
     *
     * <p>The string is first converted to a byte[] array using transparent
     * encoding. If the resulting buffer size is > 64, an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>If <tt>sname</tt> parameter is null, the buffer is filled with zeros.
     *
     * @param sname The sname field to set.
     * @throws IllegalArgumentException string too long
     */
    public void setSname(String sname)
    {
        this.setSnameRaw(stringToBytes(sname));
    }

    /**
     * Sets the sname field (Optional server host name) as String.
     *
     * <p>If the buffer size is > 64, an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>If <tt>sname</tt> parameter is null, the buffer is filled with zeros.
     *
     * <p>This is the low-level maximum performance setter for this field.
     *
     * @param sname The sname field to set.
     * @throws IllegalArgumentException string too long
     */
    public void setSnameRaw(byte[] sname)
    {
        if (sname != null) {
            if (sname.length > this.sname.length) {
                throw new IllegalArgumentException("Sname is too long:" + sname.length + " max is:" + this.sname.length);
            }
            Arrays.fill(this.sname, (byte) 0);
            System.arraycopy(sname, 0, this.sname, 0, sname.length);
        }
        else {
            Arrays.fill(this.sname, (byte) 0);
        }
    }

    /**
     * Returns the xid field (Transaction ID).
     *
     * @return Returns the xid.
     */
    public int getXid()
    {
        return this.xid;
    }

    /**
     * Sets the xid field (Transaction ID).
     *
     * <p>This field is random generated by the client, and used by the client and
     * server to associate requests and responses for the same transaction.
     *
     * @param xid The xid to set.
     */
    public void setXid(int xid)
    {
        this.xid = xid;
    }

    /**
     * Returns the yiaddr field ('your' IP address).
     *
     * @return the yiaddr field converted to <tt>InetAddress</tt> object.
     */
    public InetAddress getYiaddr()
    {
        try {
            return InetAddress.getByAddress(this.getYiaddrRaw());
        } catch (UnknownHostException e) {
            Timber.e(e,"Unexpected UnknownHostException");
            return null;    // normaly impossible
        }
    }

    /**
     * Returns the yiaddr field ('your' IP address).
     *
     * <p>This is the low-level maximum performance getter for this field.
     *
     * @return Returns the yiaddr as raw byte[4].
     */
    public byte[] getYiaddrRaw()
    {
        return this.yiaddr.clone();
    }

    /**
     * Sets the yiaddr field ('your' IP address).
     *
     * <p>Ths <tt>yiaddr</tt> field must be of <tt>Inet4Address</tt> class or
     * an <tt>IllegalArgumentException</tt> is thrown.
     *
     * @param yiaddr The yiaddr to set.
     */
    public void setYiaddr(InetAddress yiaddr)
    {
        if (!(yiaddr instanceof Inet4Address)) {
            throw new IllegalArgumentException("Inet4Address required");
        }
        this.setYiaddrRaw(yiaddr.getAddress());
    }

    /**
     * Sets the yiaddr field ('your' IP address).
     *
     * @param yiaddr The yiaddr to set.
     * @throws UnknownHostException
     */
    public void setYiaddr(String yiaddr)
            throws UnknownHostException
    {
        this.setYiaddr(InetAddress.getByName(yiaddr));
    }

    /**
     * Sets the yiaddr field ('your' IP address).
     *
     * <p><tt>yiaddr</tt> must be a 4 bytes array, or an <tt>IllegalArgumentException</tt>
     * is thrown.
     *
     * <p>This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to <tt>ciaddr</tt>
     * parameter has no side effect.
     *
     * @param yiaddr The yiaddr to set.
     */
    public void setYiaddrRaw(byte[] yiaddr)
    {
        if (yiaddr.length != 4) {
            throw new IllegalArgumentException("4-byte array required");
        }
        System.arraycopy(yiaddr, 0, this.yiaddr, 0, 4);
    }

    /**
     * Return the DHCP Option Type.
     *
     * <p>This is a short-cut for <tt>getOptionAsByte(DHO_DHCP_MESSAGE_TYPE)</tt>.
     *
     * @return option type, of <tt>null</tt> if not present.
     */
    public Byte getDHCPMessageType()
    {
        return this.getOptionAsByte(DHO_DHCP_MESSAGE_TYPE);
    }

    /**
     * Sets the DHCP Option Type.
     *
     * <p>This is a short-cur for <tt>setOptionAsByte(DHO_DHCP_MESSAGE_TYPE, optionType);</tt>.
     *
     * @param optionType
     */
    public void setDHCPMessageType(byte optionType)
    {
        this.setOptionAsByte(DHO_DHCP_MESSAGE_TYPE, optionType);
    }

    /**
     * Indicates that the DHCP packet has been truncated and did not finished
     * with a 0xFF option. This parameter is set only when parsing packets in
     * non-strict mode (which is not the default behaviour).
     *
     * <p>This field is read-only and can be <tt>true</tt> only with objects created
     * by parsing a Datagram - getPacket() methods.
     *
     * <p>This field is cleared if the object is cloned.
     *
     * @return the truncated field.
     */
    public boolean isTruncated()
    {
        return this.truncated;
    }

    /**
     * Wrapper function for getValueAsNum() in DHCPOption. Returns a numerical option: int, short or byte.
     *
     * @param code DHCP option code
     * @return Integer object or <tt>null</tt>
     */
    public Integer getOptionAsNum(byte code)
    {
        DHCPOption opt = this.getOption(code);
        return (opt != null) ? opt.getValueAsNum() : null;
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
     * @param code the option code.
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public Byte getOptionAsByte(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsByte();
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
     * @param code the option code.
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public Short getOptionAsShort(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsShort();
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
     * @param code the option code.
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public Integer getOptionAsInteger(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsInt();
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
     * @param code the option code.
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public InetAddress getOptionAsInetAddr(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsInetAddr();
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
     * @param code the option code.
     * @return the option value, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public String getOptionAsString(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsString();
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
     * @param code the option code.
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public short[] getOptionAsShorts(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsShorts();
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
     * @param code the option code.
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    public InetAddress[] getOptionAsInetAddrs(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsInetAddrs();
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
     * @param code the option code.
     * @return the option value array, <tt>null</tt> if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public byte[] getOptionAsBytes(byte code)
            throws IllegalArgumentException
    {
        DHCPOption opt = this.getOption(code);
        return (opt == null) ? null : opt.getValueAsBytes();
    }

    /**
     * Sets a DHCP Option as Byte format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsByte(byte code, byte val)
    {
        this.setOption(DHCPOption.newOptionAsByte(code, val));
    }

    /**
     * Sets a DHCP Option as Short format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsShort(byte code, short val)
    {
        this.setOption(DHCPOption.newOptionAsShort(code, val));
    }

    /**
     * Sets a DHCP Option as Integer format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsInt(byte code, int val)
    {
        this.setOption(DHCPOption.newOptionAsInt(code, val));
    }

    /**
     * Sets a DHCP Option as InetAddress format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsInetAddress(byte code, InetAddress val)
    {
        this.setOption(DHCPOption.newOptionAsInetAddress(code, val));
    }

    /**
     * Sets a DHCP Option as InetAddress format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code in String format.
     * @param val the value
     * @throws UnknownHostException cannot find the address
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsInetAddress(byte code, String val)
            throws UnknownHostException
    {
        this.setOption(DHCPOption.newOptionAsInetAddress(code, InetAddress.getByName(val)));
    }

    /**
     * Sets a DHCP Option as InetAddress array format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value array
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsInetAddresses(byte code, InetAddress[] val)
    {
        this.setOption(DHCPOption.newOptionAsInetAddresses(code, val));
    }

    /**
     * Sets a DHCP Option as String format.
     *
     * <p>See <tt>DHCPOption</tt> for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    public void setOptionAsString(byte code, String val)
    {
        this.setOption(DHCPOption.newOptionAsString(code, val));
    }

    /**
     * Returns the option as raw byte[] buffer.
     *
     * <p>This is the low-level maximum performance getter for options.
     * No byte[] copy is completed to increase performance.
     *
     * @param code option code
     * @return Returns the option as raw <tt>byte[]</tt>, or <tt>null</tt> if
     * the option is not present.
     */
    public byte[] getOptionRaw(byte code)
    {
        final DHCPOption opt = this.getOption(code);
        return ((opt == null) ? null : opt.getValueFast());
    }

    /**
     * Returns the option as DHCPOption object.
     *
     * <p>This is the low-level maximum performance getter for options.
     * This method is used by every option getter in this object.
     *
     * @param code option code
     * @return Returns the option as <tt>DHCPOption</tt>, or <tt>null</tt> if
     * the option is not present.
     */
    public DHCPOption getOption(byte code)
    {
        DHCPOption opt = this.options.get(code);
        // Sanity checks
        if (opt == null) {
            return null;
        }
        assert (opt.getCode() == code);
        assert (opt.getValueFast() != null);
        return opt;
    }

    /**
     * Tests whether an option code is present in the packet.
     *
     * @param code DHCP option code
     * @return true if option is present
     */
    public boolean containsOption(byte code)
    {
        return this.options.containsKey(code);
    }

    /**
     * Return an ordered list/collection of all options.
     *
     * <p>The Collection is read-only.
     *
     * @return collection of <tt>DHCPOption</tt>.
     */
    public Collection<DHCPOption> getOptionsCollection()
    {
        return Collections.unmodifiableCollection(this.options.values());    // read only
    }

    /**
     * Return an array of all DHCP options.
     *
     * @return the options array
     */
    public DHCPOption[] getOptionsArray()
    {
        return this.options.values().toArray(new DHCPOption[this.options.size()]);
    }

    /**
     * Sets the option specified for the option.
     *
     * <p>If <tt>buf</tt> is <tt>null</tt>, the option is cleared.
     *
     * <p>Options are sorted in creation order. Previous values are replaced.
     *
     * <p>This is the low-level maximum performance setter for options.
     *
     * @param code opt    option code, use <tt>DHO_*</tt> for predefined values.
     * @param buf raw buffer value (cloned). If null, the option is removed.
     */
    public void setOptionRaw(byte code, byte[] buf)
    {
        if (buf == null) {        // clear parameter
            this.removeOption(code);
        }
        else {
            this.setOption(new DHCPOption(code, buf));    // exception here if code=0 or code=-1
        }
    }

    /**
     * Sets the option specified for the option.
     *
     * <p>If <tt>buf</tt> is <tt>null</tt>, the option is cleared.
     *
     * <p>Options are sorted in creation order. Previous values are replaced, but their
     * previous position is retained.
     *
     * <p>This is the low-level maximum performance setter for options.
     * This method is called by all setter methods in this class.
     *
     * @param opt option code, use <tt>DHO_*</tt> for predefined values.
     */
    public void setOption(DHCPOption opt)
    {
        if (opt != null) {
            if (opt.getValueFast() == null) {
                this.removeOption(opt.getCode());
            }
            else {
                this.options.put(opt.getCode(), opt);
            }
        }
    }

    /**
     * Sets an array of options. Calles repeatedly setOption on each element of the array.
     *
     * @param opts array of options.
     */
    public void setOptions(DHCPOption[] opts)
    {
        if (opts != null) {
            for (DHCPOption opt : opts) {
                this.setOption(opt);
            }
        }
    }

    /**
     * Sets a Collection of options. Calles repeatedly setOption on each element of the List.
     *
     * @param opts List of options.
     */
    public void setOptions(Collection<DHCPOption> opts)
    {
        if (opts != null) {
            for (DHCPOption opt : opts) {
                this.setOption(opt);
            }
        }
    }

    /**
     * Remove this option from the options list.
     *
     * @param opt the option code to remove.
     */
    public void removeOption(byte opt)
    {
        this.options.remove(opt);
    }

    /**
     * Remove all options.
     */
    public void removeAllOptions()
    {
        this.options.clear();
    }

    /**
     * Returns the IP address of the machine to which this datagram is being sent
     * or from which the datagram was received.
     *
     * @return the IP address of the machine to which this datagram is being sent
     * or from which the datagram was received. <tt>null</tt> if no address.
     */
    public InetAddress getAddress()
    {
        return this.address;
    }

    /**
     * Sets the IP address of the machine to which this datagram is being sent.
     *
     * @param address the <tt>InetAddress</tt>.
     * @throws IllegalArgumentException address is not of <tt>Inet4Address</tt> class.
     */
    public void setAddress(InetAddress address)
    {
        if (address == null) {
            this.address = null;
        }
        else if (!(address instanceof Inet4Address)) {
            throw new IllegalArgumentException("only IPv4 addresses accepted");
        }
        else {
            this.address = address;
        }
    }

    /**
     * Returns the port number on the remote host to which this datagram is being sent
     * or from which the datagram was received.
     *
     * @return the port number on the remote host to which this datagram is being sent
     * or from which the datagram was received.
     */
    public int getPort()
    {
        return this.port;
    }

    /**
     * Sets the port number on the remote host to which this datagram is being sent.
     *
     * @param port the port number.
     */
    public void setPort(int port)
    {
        this.port = port;
    }

    /**
     * Syntactic sugar for getAddress/getPort.
     *
     * @return address + port.
     */
    public InetSocketAddress getAddrPort()
    {
        return new InetSocketAddress(address, port);
    }

    /**
     * Syntactic sugar for setAddress/setPort.
     *
     * @param addrPort address and port, if <tt>null</t> address is set to null and port to 0
     */
    public void setAddrPort(InetSocketAddress addrPort)
    {
        if (addrPort == null) {
            setAddress(null);
            setPort(0);
        }
        else {
            setAddress(addrPort.getAddress());
            setPort(addrPort.getPort());
        }
    }

    // ========================================================================
    // utility functions

    /**
     * Converts a null terminated byte[] string to a String object,
     * with a transparent conversion.
     *
     * Faster version than String.getBytes()
     */
    static String bytesToString(byte[] buf)
    {
        if (buf == null) {
            return "";
        }
        return bytesToString(buf, 0, buf.length);
    }

    static String bytesToString(byte[] buf, int src, int len)
    {
        if (buf == null) {
            return "";
        }
        if (src < 0) {
            len += src;    // reduce length
            src = 0;
        }
        if (len <= 0) {
            return "";
        }
        if (src >= buf.length) {
            return "";
        }
        if (src + len > buf.length) {
            len = buf.length - src;
        }
        // string should be null terminated or whole buffer
        // first find the real lentgh
        for (int i = src; i < src + len; i++) {
            if (buf[i] == 0) {
                len = i - src;
                break;
            }
        }

        char[] chars = new char[len];

        for (int i = src; i < src + len; i++) {
            chars[i - src] = (char) buf[i];
        }
        return new String(chars);
    }

    /**
     * Converts byte to hex string (2 chars) (uppercase)
     */
    private static final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    static void appendHex(StringBuilder sbuf, byte b)
    {
        int i = (b & 0xFF);
        sbuf.append(hex[(i & 0xF0) >> 4])
                .append(hex[i & 0x0F]);
    }

    /**
     * Converts a byte[] to a sequence of hex chars (uppercase), limited to <tt>len</tt> bytes
     * and appends them to a string buffer
     */
    static void appendHex(StringBuilder sbuf, final byte[] buf, int src, int len)
    {
        if (buf == null) {
            return;
        }
        if (src < 0) {
            len += src;    // reduce length
            src = 0;
        }
        if (len <= 0 || src >= buf.length) {
            return;
        }
        if (src + len > buf.length) {
            len = buf.length - src;
        }

        for (int i = src; i < src + len; i++) {
            appendHex(sbuf, buf[i]);
        }
    }

    /**
     * Convert plain byte[] to hex string (uppercase)
     */
    static void appendHex(StringBuilder sbuf, final byte[] buf)
    {
        appendHex(sbuf, buf, 0, buf.length);
    }

    /**
     * Convert bytes to hex string.
     *
     * @param buf
     * @return hex string (lowercase) or "" if buf is <tt>null</tt>
     */
    static String bytes2Hex(byte[] buf)
    {
        if (buf == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(buf.length * 2);
        appendHex(sb, buf);
        return sb.toString();
    }

    /**
     * Convert hes String to byte[]
     */
    static byte[] hex2Bytes(String s)
    {
        if ((s.length() & 1) != 0) {
            throw new IllegalArgumentException("String length must be even: " + s.length());
        }

        byte[] buf = new byte[s.length() / 2];

        for (int index = 0; index < buf.length; index++) {
            final int stringIndex = index << 1;
            buf[index] = (byte) Integer.parseInt(s.substring(stringIndex, stringIndex + 2), 16);
        }
        return buf;
    }

    /**
     * Convert integer to hex chars (uppercase) and appends them to a string builder
     */
    private static void appendHex(StringBuilder sbuf, int i)
    {
        appendHex(sbuf, (byte) ((i & 0xff000000) >>> 24));
        appendHex(sbuf, (byte) ((i & 0x00ff0000) >>> 16));
        appendHex(sbuf, (byte) ((i & 0x0000ff00) >>> 8));
        appendHex(sbuf, (byte) ((i & 0x000000ff)));
    }

    public static byte[] stringToBytes(String str)
    {
        if (str == null) {
            return null;
        }

        char[] chars = str.toCharArray();
        int len = chars.length;
        byte[] buf = new byte[len];

        for (int i = 0; i < len; i++) {
            buf[i] = (byte) chars[i];
        }
        return buf;
    }

    /**
     * Even faster version than {@link #getHostAddress} when the address is not
     * the only piece of information put in the string.
     *
     * @param sbuf the string builder
     * @param addr the Internet address
     */
    public static void appendHostAddress(StringBuilder sbuf, InetAddress addr)
    {
        if (addr == null) {
            throw new IllegalArgumentException("addr must not be null");
        }
        if (!(addr instanceof Inet4Address)) {
            throw new IllegalArgumentException("addr must be an instance of Inet4Address");
        }

        byte[] src = addr.getAddress();

        sbuf.append(src[0] & 0xFF)
                .append('.')
                .append(src[1] & 0xFF)
                .append('.')
                .append(src[2] & 0xFF)
                .append('.')
                .append(src[3] & 0xFF);
    }

    /**
     * Faster version than <tt>InetAddress.getHostAddress()</tt>.
     *
     * @return String representation of address.
     */
    public static String getHostAddress(InetAddress addr)
    {
        StringBuilder sbuf = new StringBuilder(15);
        appendHostAddress(sbuf, addr);
        return sbuf.toString();
    }
}
