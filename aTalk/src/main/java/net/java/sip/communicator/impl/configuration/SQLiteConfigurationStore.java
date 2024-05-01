/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.configuration;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.impl.configuration.ConfigurationStore;
import org.atalk.impl.configuration.DatabaseConfigurationStore;
import org.atalk.impl.configuration.HashtableConfigurationStore;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.osgi.OSGiService;

import timber.log.Timber;

/**
 * Implements a <code>ConfigurationStore</code> which stores property name-value associations in an
 * SQLite database.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class SQLiteConfigurationStore extends DatabaseConfigurationStore {
    public static final String TABLE_NAME = "properties";
    public static final String COLUMN_NAME = "Name";
    public static final String COLUMN_VALUE = "Value";

    /**
     * aTalk backend SQLite database
     */
    private final SQLiteOpenHelper openHelper;
    private static SQLiteDatabase mDB = null;

    /**
     * Initializes a new <code>SQLiteConfigurationStore</code> instance.
     */
    public SQLiteConfigurationStore() {
        this(ServiceUtils.getService(ConfigurationActivator.getBundleContext(), OSGiService.class));
    }

    public SQLiteConfigurationStore(Context context) {
        openHelper = DatabaseBackend.getInstance(context);
        mDB = openHelper.getReadableDatabase();
    }

    /**
     * Overrides {@link HashtableConfigurationStore#getProperty(String)}. If this
     * <code>ConfigurationStore</code> contains a value associated with the specified property name,
     * returns it. Otherwise, searches for a system property with the specified name and returns
     * its value. If property name starts with "acc", the look up the value in table
     * AccountID.TBL_PROPERTIES for the specified accountUuid, otherwise use table TABLE_NAME
     *
     * @param name the name of the property to get the value of
     *
     * @return the value in this <code>ConfigurationStore</code> of the property with the specified
     * name; <code>null</code> if the property with the specified name does not have an association
     * with a value in this <code>ConfigurationStore</code>
     *
     * @see ConfigurationStore#getProperty(String)
     */
    @Override
    public Object getProperty(String name) {
        Cursor cursor = null;
        Object value = properties.get(name);
        if (value == null) {
            String[] columns = {COLUMN_VALUE};
            synchronized (openHelper) {
                if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                    int idx = name.indexOf(".");
                    if (idx == -1) {
                        value = name;  // just return the accountUuid
                    }
                    else {
                        String[] args = {name.substring(0, idx), name.substring(idx + 1)};
                        cursor = mDB.query(AccountID.TBL_PROPERTIES, columns,
                                AccountID.ACCOUNT_UUID + "=? AND " + COLUMN_NAME + "=?",
                                args, null, null, null, "1");
                    }
                }
                else {
                    cursor = mDB.query(TABLE_NAME, columns,
                            COLUMN_NAME + "=?", new String[]{name}, null, null, null, "1");
                }
                if (cursor != null) {
                    try {
                        if ((cursor.getCount() == 1) && cursor.moveToFirst())
                            value = cursor.getString(0);
                    } finally {
                        cursor.close();
                    }
                }
            }
            if (value == null)
                value = System.getProperty(name);
        }
        return value;
    }

    /**
     * Overrides {@link HashtableConfigurationStore#getPropertyNames(String)}. Gets the names of
     * the properties which have values associated in this <code>ConfigurationStore</code>.
     *
     * @return an array of <code>String</code>s which specify the names of the properties that have
     * values associated in this <code>ConfigurationStore</code>; an empty array if this instance
     * contains no property values
     *
     * @see ConfigurationStore#getPropertyNames(String)
     */
    @Override
    public String[] getPropertyNames(String name) {
        List<String> propertyNames = new ArrayList<>();
        String tableName;

        synchronized (openHelper) {
            if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                tableName = AccountID.TBL_PROPERTIES;
            }
            else {
                tableName = TABLE_NAME;
            }

            try (Cursor cursor = mDB.query(tableName, new String[]{COLUMN_NAME},
                    null, null, null, null, COLUMN_NAME + " ASC")) {
                while (cursor.moveToNext()) {
                    propertyNames.add(cursor.getString(0));
                }
            }
        }
        return propertyNames.toArray(new String[0]);
    }

    /**
     * Removes all property name-value associations currently present in this
     * <code>ConfigurationStore</code> instance and de-serializes new property name-value
     * associations from its underlying database (storage).
     *
     * @throws IOException if there is an input error while reading from the underlying database (storage)
     */

    protected void reloadConfiguration()
            throws IOException {
        // TODO Auto-generated method stub
    }

    /**
     * Overrides {@link HashtableConfigurationStore#removeProperty(String)}. Removes the value
     * association in this <code>ConfigurationStore</code> of the property with a specific name. If
     * the property with the specified name is not associated with a value in this
     * <code>ConfigurationStore</code>, does nothing.
     *
     * @param name the name of the property which is to have its value association in this
     * <code>ConfigurationStore</code> removed
     *
     * @see ConfigurationStore#removeProperty(String)
     */
    public void removeProperty(String name) {
        super.removeProperty(name);
        synchronized (openHelper) {
            if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                int idx = name.indexOf(".");
                // remove user account if only accountUuid is specified
                if (idx == -1) {
                    String[] args = {name};
                    mDB.delete(AccountID.TABLE_NAME, AccountID.ACCOUNT_UUID + "=?", args);
                }
                // Otherwise, remove the accountProperty from the AccountID.TBL_PROPERTIES
                else {
                    String[] args = {name.substring(0, idx), name.substring(idx + 1)};
                    mDB.delete(AccountID.TBL_PROPERTIES,
                            AccountID.ACCOUNT_UUID + "=? AND " + COLUMN_NAME + "=?", args);
                }
            }
            else {
                mDB.delete(TABLE_NAME, COLUMN_NAME + "=?", new String[]{name});
            }
        }
        Timber.log(TimberLog.FINER, "### Remove property from table: %s", name);
    }

    /**
     * Overrides {@link HashtableConfigurationStore#setNonSystemProperty(String, Object)}.
     *
     * @param name the name of the non-system property to be set to the specified value in this
     * <code>ConfigurationStore</code>
     * @param value the value to be assigned to the non-system property with the specified name in this
     * <code>ConfigurationStore</code>
     *
     * @see ConfigurationStore#setNonSystemProperty(String, Object)
     */
    @Override
    public void setNonSystemProperty(String name, Object value) {
        synchronized (openHelper) {
            String tableName = TABLE_NAME;

            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_VALUE, value.toString());

            if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                int idx = name.indexOf(".");
                contentValues.put(AccountID.ACCOUNT_UUID, name.substring(0, idx));
                contentValues.put(COLUMN_NAME, name.substring(idx + 1));
                tableName = AccountID.TBL_PROPERTIES;
            }
            else {
                contentValues.put(COLUMN_NAME, name);
            }

            // Insert the properties in DB, replace if exist
            long rowId = mDB.replace(tableName, null, contentValues);
            if (rowId == -1)
                Timber.e("Failed to set non-system property: %s: %s <= %s", tableName, name, value);

            Timber.log(TimberLog.FINER, "### Set non-system property: %s: %s <= %s", tableName, name, value);
        }
        // To take care of cached properties and accountProperties
        super.setNonSystemProperty(name, value);
    }
}
