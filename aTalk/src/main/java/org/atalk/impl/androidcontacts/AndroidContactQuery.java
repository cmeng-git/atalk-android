/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import net.java.sip.communicator.service.contactsource.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Android contact query.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidContactQuery extends AbstractContactQuery<AndroidContactSource>
{
    /**
     * Selection query
     */
    private static final String SELECTION = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? AND "
            + ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0";

    /**
     * List of projection columns that will be returned
     */
    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.PHOTO_ID};

    /**
     * The uri that will be user for queries.
     */
    private static final Uri CONTACTS_URI = ContactsContract.Contacts.CONTENT_URI;

    /**
     * Query string
     */
    private final String queryString;

    /**
     * Results list
     */
    private final List<SourceContact> results = new ArrayList<>();

    /**
     * The thread that runs the query
     */
    private Thread queryThread;

    /**
     * Flag used to cancel the query thread
     */
    private boolean cancel = false;

    // TODO: implement cancel, on API >= 16
    // private CancellationSignal cancelSignal = new CancellationSignal();

    /**
     * Creates new instance of <tt>AndroidContactQuery</tt>.
     *
     * @param contactSource parent Android contact source.
     * @param queryString query string.
     */
    protected AndroidContactQuery(AndroidContactSource contactSource, String queryString)
    {
        super(contactSource);
        this.queryString = queryString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start()
    {
        if (queryThread != null)
            return;

        queryThread = new Thread(() -> doQuery());
        queryThread.start();
    }

    /**
     * Executes the query.
     */
    private void doQuery()
    {
        ContentResolver contentResolver = aTalkApp.getGlobalContext().getContentResolver();
        Cursor cursor = null;

        try {
            cursor = contentResolver.query(CONTACTS_URI, PROJECTION, SELECTION, new String[]{queryString}, null);
            if (cancel)
                return;

            // Get projection column ids
            int ID = cursor.getColumnIndex(ContactsContract.Contacts._ID);
            int LOOP_UP = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
            int DISPLAY_NAME = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
            // int HAS_PHONE = cursor.getColumnIndex(
            // ContactsContract.Contacts.HAS_PHONE_NUMBER);
            int THUMBNAIL_URI = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI);
            int PHOTO_URI = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI);
            int PHOTO_ID = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID);

            // Create results
            while (cursor.moveToNext()) {
                if (cancel)
                    break;

                long id = cursor.getLong(ID);
                String lookUp = cursor.getString(LOOP_UP);
                String displayName = cursor.getString(DISPLAY_NAME);
                String thumbnail = cursor.getString(THUMBNAIL_URI);
                String photoUri = cursor.getString(PHOTO_URI);
                String photoId = cursor.getString(PHOTO_ID);

                // Loop on all phones
                Cursor result = null;
                try {
                    result = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Phone.DATA},
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY + "=?",
                            new String[]{String.valueOf(lookUp)}, null);

                    if (result != null) {
                        while (result.moveToNext() && !cancel) {
                            int adrIdx = result.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA);
                            String phone = result.getString(adrIdx);
                            results.add(new AndroidContact(getContactSource(), id, lookUp, displayName,
                                    thumbnail, photoUri, photoId, phone));
                        }
                    }
                } finally {
                    if (result != null)
                        result.close();
                }
            }
            if (!cancel) {
                setStatus(ContactQuery.QUERY_COMPLETED);
            }
        } catch (SecurityException e) {
            aTalkApp.showToastMessage(aTalkApp.getResString(R.string.contacts_permission_denied_feedback)
                    + "\n" + e.getMessage());
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel()
    {
        cancel = true;
        try {
            queryThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        super.cancel();
    }

    /**
     * Returns the query string, this query was created for.
     *
     * @return the query string, this query was created for
     */
    @Override
    public String getQueryString()
    {
        return queryString;
    }

    /**
     * Returns the list of <tt>SourceContact</tt>s returned by this query.
     *
     * @return the list of <tt>SourceContact</tt>s returned by this query
     */
    @Override
    public List<SourceContact> getQueryResults()
    {
        return results;
    }
}
