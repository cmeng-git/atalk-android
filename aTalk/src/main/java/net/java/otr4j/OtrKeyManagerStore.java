package net.java.otr4j;

/**
 * @author George Politis
 */
public interface OtrKeyManagerStore {
	 byte[] getPropertyBytes(String id);

	 boolean getPropertyBoolean(String id, boolean defaultValue);

	 void setProperty(String id, byte[] value);

	 void setProperty(String id, boolean value);

	 void removeProperty(String id);
}
