/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smackx.avatar.cache;

import org.jxmpp.jid.BareJid;

/**
 * Interface for an id to Avatar Hash index cache.
 * The specified id is normally the bareJid
 *
 * @author Eng Chong Meng
 *
 */
public interface JidToHashCache
{
	/**
	 * Put some data in cache.
	 *
	 * @param id
	 * 		the key bareJid of the data
	 * @param hash
	 * 		the image hash to cache
	 */
	void addHashByJid(BareJid id, String hash);

	/**
	 * Get some data from the cache.
	 *
	 * @param id
	 * 		the image hash of the data for the jid
	 * @return the cached hash
	 */
	String getHashForJid(BareJid id);

	/**
	 * Test if a data is in cache.
	 *
	 * @param id
	 * 		the bardJid of the data
	 * @return true if data is in cache false otherwise
	 */
	boolean contains(BareJid id);

	/**
	 * Purge obsoleted file in store
	 *
	 * @param id
	 * 		the bardJid of the file to be purged
	 * @return true if file purged is successful, false otherwise
	 */
	boolean purgeItemFor(BareJid id);

	/**
	 * Purge all the files in store
	 * @return true if all files deletion is successful, false otherwise
	 *
	 */
	boolean emptyCache();
}
