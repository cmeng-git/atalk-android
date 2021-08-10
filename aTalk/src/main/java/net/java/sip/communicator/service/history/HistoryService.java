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
package net.java.sip.communicator.service.history;

import net.java.sip.communicator.service.history.records.HistoryRecordStructure;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * This service provides the functionality to store history records. The records are called <tt>HistoryRecord</tt>s and
 * are grouped by ID.
 *
 * The ID may be used to set hierarchical structure. In a typical usage one may set the first string to be the userID,
 * and the second - the service name.
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
public interface HistoryService
{

    /**
     * Property and values used to be set in configuration Used in implementation to cache every opened history document
     * or not to cache them and to access them on every read
     */
    public static String CACHE_ENABLED_PROPERTY = "history.CACHE_ENABLED";

    /**
     * Date format used in the XML history database.
     */
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    /**
     * Returns the IDs of all existing histories.
     *
     * @return An iterator to a list of IDs.
     */
    Iterator<HistoryID> getExistingIDs();

    /**
     * Returns the history associated with this ID.
     *
     * @param id The ID of the history.
     * @return Returns the history with this ID.
     * @throws IllegalArgumentException Thrown if there is no such history.
     */
    History getHistory(HistoryID id)
            throws IllegalArgumentException;

    /**
     * Enumerates existing histories.
     *
     * @param rawid the start of the HistoryID of all the histories that will be returned.
     * @return list of histories which HistoryID starts with <tt>rawid</tt>.
     * @throws IllegalArgumentException if the <tt>rawid</tt> contains ids which are missing in current history.
     */
    List<HistoryID> getExistingHistories(String[] rawid)
            throws IllegalArgumentException;

    /**
     * Tests if a history with the given ID exists and is loaded.
     *
     * @param id The ID to test.
     * @return True if a history with this ID exists. False otherwise.
     */
    boolean isHistoryExisting(HistoryID id);

    /**
     * Creates a new history for this ID.
     *
     * @param id The ID of the history to be created.
     * @param recordStructure The structure of the data.
     * @return Returns the history with this ID.
     * @throws IllegalArgumentException Thrown if such history already exists.
     * @throws IOException Thrown if the history could not be created due to a IO error.
     */
    History createHistory(HistoryID id, HistoryRecordStructure recordStructure)
            throws IllegalArgumentException, IOException;

    /**
     * Permanently removes local stored History
     *
     * @param id HistoryID
     * @throws IOException Thrown if the history could not be removed due to a IO error.
     */
    void purgeLocallyStoredHistory(HistoryID id)
            throws IOException;

    /**
     * Clears locally(in memory) cached histories.
     */
    void purgeLocallyCachedHistories();

    /**
     * Moves the content of oldId history to the content of the newId.
     *
     * @param oldId id of the old and existing history
     * @param newId the place where content of oldId will be moved
     * @throws java.io.IOException problem moving content to newId.
     */
    void moveHistory(HistoryID oldId, HistoryID newId)
            throws IOException;

    /**
     * Checks whether a history is created and stored.
     *
     * @param id the history to check
     * @return whether a history is created and stored.
     */
    boolean isHistoryCreated(HistoryID id);
}
