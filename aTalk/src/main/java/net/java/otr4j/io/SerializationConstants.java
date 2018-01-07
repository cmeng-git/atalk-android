/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.io;

/**
 * 
 * @author George Politis
 */
public interface SerializationConstants
{
	String HEAD = "?OTR";
	char HEAD_ENCODED = ':';
	char HEAD_ERROR = ' ';
	char HEAD_QUERY_Q = '?';
	char HEAD_QUERY_V = 'v';
	String ERROR_PREFIX = "Error:";

	int TYPE_LEN_BYTE = 1;
	int TYPE_LEN_SHORT = 2;
	int TYPE_LEN_INT = 4;
	int TYPE_LEN_MAC = 20;
	int TYPE_LEN_CTR = 8;

	int DATA_LEN = TYPE_LEN_INT;
	int TLV_LEN = TYPE_LEN_SHORT;
}
