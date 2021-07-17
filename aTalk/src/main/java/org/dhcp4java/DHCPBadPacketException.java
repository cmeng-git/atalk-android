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

/**
 * Thrown to indicate that a DHCP datagram is malformed.
 * 
 * <p>The DHCP datagram may be too big, too small, or contain garbage data that makes
 * it impossible to parse correctly.
 * 
 * <p>It inherits from <tt>IllegalArgumentException</tt> and <tt>RuntimeException</tt>
 * so it doesn't need to be explicitly caught.
 * 
 * @author Stephan Hadinger
 * @version 1.00
 */
public class DHCPBadPacketException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	
    /**
     * Constructs an <tt>DHCPBadPacketException</tt> with no detail message.
     */
    public DHCPBadPacketException() {
    	// empty constructor
    }
    /**
     * Constructs an <tt>DHCPBadPacketException</tt> with the specified detail message.
     * 
     * @param message the detail message.
     */
    public DHCPBadPacketException(String message) {
        super(message);
    }
    /**
     * Constructs a new exception with the specified detail message and cause.
     * 
     * <p>Note that the detail message associated with <tt>cause</tt> is <i>not</i>
     * automatically incorporated in this exception's detail message.
     * 
     * @param message the detail message (which is saved for later retrieval 
     * 			by the <tt>Throwable.getMessage()</tt> method).
     * @param cause the cause (which is saved for later retrieval by the 
     * 			<tt>Throwable.getCause()</tt> method).
     * 			(A <tt>null</tt> value is permitted, and indicates that the cause 
     * 			is nonexistent or unknown.)
     */
    public DHCPBadPacketException(String message, Throwable cause) {
        super(message, cause);
    }
    /**
     * Constructs a new exception with the specified cause and a detail message 
     * of <tt>(cause==null ? null : cause.toString())</tt>
     * (which typically contains the class and detail message of cause). 
     * 
     * @param cause the cause (which is saved for later retrieval by the 
     * 			<tt>Throwable.getCause()</tt> method).
     * 			(A <tt>null</tt> value is permitted, and indicates that the cause 
     * 			is nonexistent or unknown.)
     */
    public DHCPBadPacketException(Throwable cause) {
        super(cause);
    }
}
