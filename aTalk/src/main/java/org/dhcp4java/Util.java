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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Stephan Hadinger
 * @version 1.00
 */
public final class Util {

    // Suppresses default constructor, ensuring non-instantiability.
	private Util() {
		throw new UnsupportedOperationException();
	}

    /**
     * Converts 32 bits int to IPv4 <tt>InetAddress</tt>.
     * 
     * @param val int representation of IPv4 address
     * @return the address object
     */
    public static final InetAddress int2InetAddress(int val) {
        byte[] value = { (byte) ((val & 0xFF000000) >>> 24),
                         (byte) ((val & 0X00FF0000) >>> 16),
                         (byte) ((val & 0x0000FF00) >>>  8),
                         (byte) ((val & 0x000000FF)) };
        try {
            return InetAddress.getByAddress(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }
    /**
     * Converts 32 bits int packaged into a 64bits long to IPv4 <tt>InetAddress</tt>.
     * 
     * @param val int representation of IPv4 address
     * @return the address object
     */
    public static final InetAddress long2InetAddress(long val) {
    	if ((val < 0) || (val > 0xFFFFFFFFL)) {
    		// TODO exception ???
    	}
    	return int2InetAddress((int) val);
    }
    /**
     * Converts IPv4 <tt>InetAddress</tt> to 32 bits int.
     * 
     * @param addr IPv4 address object
     * @return 32 bits int
     * @throws NullPointerException <tt>addr</tt> is <tt>null</tt>.
     * @throws IllegalArgumentException the address is not IPv4 (Inet4Address).
     */
    public static final int inetAddress2Int(InetAddress addr) {
        if (!(addr instanceof Inet4Address)) {
            throw new IllegalArgumentException("Only IPv4 supported");
        }

        byte[] addrBytes = addr.getAddress();
        return  ((addrBytes[0] & 0xFF) << 24) |
        		((addrBytes[1] & 0xFF) << 16) |
        		((addrBytes[2] & 0xFF) <<  8) |
        		((addrBytes[3] & 0xFF));
    }
    /**
     * Converts IPv4 <tt>InetAddress</tt> to 32 bits int, packages into a 64 bits <tt>long</tt>.
     * 
     * @param addr IPv4 address object
     * @return 32 bits int
     * @throws NullPointerException <tt>addr</tt> is <tt>null</tt>.
     * @throws IllegalArgumentException the address is not IPv4 (Inet4Address).
     */
    public static final long inetAddress2Long(InetAddress addr) {
    	return (inetAddress2Int(addr) & 0xFFFFFFFFL);
    }
}
