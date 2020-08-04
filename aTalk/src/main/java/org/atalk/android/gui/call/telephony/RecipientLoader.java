package org.atalk.android.gui.call.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Data;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import java.util.*;

import androidx.annotation.Nullable;
import androidx.loader.content.AsyncTaskLoader;

public class RecipientLoader extends AsyncTaskLoader<List<RecipientSelectView.Recipient>>
{
    /*
     * Indexes of the fields in the projection. This must match the order in {@link #PROJECTION}.
     */
    private static final int INDEX_NAME = 1;
    private static final int INDEX_LOOKUP_KEY = 2;
    private static final int INDEX_PHONE = 3;
    private static final int INDEX_PHONE_TYPE = 4;
    private static final int INDEX_PHONE_CUSTOM_LABEL = 5;
    private static final int INDEX_CONTACT_ID = 6;
    private static final int INDEX_PHOTO_URI = 7;

    private static final String[] PROJECTION = {
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DATA,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
    };

    private static final String SORT_ORDER = "" +
            Phone.TIMES_CONTACTED + " DESC, " + ContactsContract.Contacts.SORT_KEY_PRIMARY;

    private static final String[] PROJECTION_NICKNAME = {
            ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME
    };

    private static final int INDEX_CONTACT_ID_FOR_NICKNAME = 0;
    private static final int INDEX_NICKNAME = 1;

    private final String query;
    private final Address[] addresses;
    private final Uri contactUri;
    private final Uri lookupKeyUri;
    private final ContentResolver contentResolver;

    private List<RecipientSelectView.Recipient> cachedRecipients;
    private ForceLoadContentObserver observerContact, observerKey;

    public RecipientLoader(Context context, String query)
    {
        super(context);
        this.query = query;
        this.lookupKeyUri = null;
        this.addresses = null;
        this.contactUri = null;
        contentResolver = context.getContentResolver();
    }

    public RecipientLoader(Context context, Address... addresses)
    {
        super(context);
        this.query = null;
        this.addresses = addresses;
        this.contactUri = null;
        this.lookupKeyUri = null;
        contentResolver = context.getContentResolver();
    }

    public RecipientLoader(Context context, Uri contactUri, boolean isLookupKey)
    {
        super(context);
        this.query = null;
        this.addresses = null;
        this.contactUri = isLookupKey ? null : contactUri;
        this.lookupKeyUri = isLookupKey ? contactUri : null;
        contentResolver = context.getContentResolver();
    }

    @Override
    public List<RecipientSelectView.Recipient> loadInBackground()
    {
        List<RecipientSelectView.Recipient> recipients = new ArrayList<>();
        Map<String, RecipientSelectView.Recipient> recipientMap = new HashMap<>();

        if (this.addresses != null) {
            fillContactDataFromAddresses(this.addresses, recipients, recipientMap);
        }
        else if (contactUri != null) {
            fillContactDataFromPhoneContentUri(contactUri, recipients, recipientMap);
        }
        else if (query != null) {
            fillContactDataFromQuery(query, recipients, recipientMap);
        }
        else if (lookupKeyUri != null) {
            fillContactDataFromLookupKey(lookupKeyUri, recipients, recipientMap);
        }
        else {
            throw new IllegalStateException("loader must be initialized with query or list of addresses!");
        }

        if (recipients.isEmpty()) {
            return recipients;
        }
        return recipients;
    }

    private void fillContactDataFromAddresses(Address[] addresses, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        for (Address address : addresses) {
            // TODO actually query contacts - not sure if this is possible in a single query tho :(
            RecipientSelectView.Recipient recipient = new RecipientSelectView.Recipient(address);
            recipients.add(recipient);
            recipientMap.put(address.getAddress(), recipient);
        }
    }

    private void fillContactDataFromPhoneContentUri(Uri contactUri, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        Cursor cursor = contentResolver.query(contactUri, PROJECTION, null, null, null);
        if (cursor == null) {
            return;
        }
        fillContactDataFromCursor(cursor, recipients, recipientMap);
    }

    private void fillContactDataFromLookupKey(Uri lookupKeyUri, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        // We could use the contact id from the URI directly, but getting it from the lookup key is safer
        Uri contactContentUri = Contacts.lookupContact(contentResolver, lookupKeyUri);
        if (contactContentUri == null) {
            return;
        }

        String contactIdStr = getContactIdFromContactUri(contactContentUri);
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PROJECTION, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                new String[]{contactIdStr}, null);

        if (cursor == null) {
            return;
        }
        fillContactDataFromCursor(cursor, recipients, recipientMap);
    }

    private static String getContactIdFromContactUri(Uri contactUri)
    {
        return contactUri.getLastPathSegment();
    }

    private Cursor getNicknameCursor(String nickname)
    {
        nickname = "%" + nickname + "%";
        Uri queryUriForNickname = ContactsContract.Data.CONTENT_URI;

        try {
            return contentResolver.query(queryUriForNickname, PROJECTION_NICKNAME,
                    ContactsContract.CommonDataKinds.Nickname.NAME + " LIKE ? AND " + Data.MIMETYPE + " = ?",
                    new String[]{nickname, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE},
                    null);
        } catch (Exception e) {
            aTalkApp.showToastMessage("Ccontact Access Exception:\n" + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("ConstantConditions")
    private void fillContactDataFromQuery(String query, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        boolean foundValidCursor = false;
        foundValidCursor |= fillContactDataFromNickname(query, recipients, recipientMap);
        foundValidCursor |= fillContactDataFromNameAndPhone(query, recipients, recipientMap);

        if (foundValidCursor) {
            registerContentObserver();
        }
    }

    private void registerContentObserver()
    {
        if (observerContact != null) {
            observerContact = new ForceLoadContentObserver();
            contentResolver.registerContentObserver(Phone.CONTENT_URI, false, observerContact);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean fillContactDataFromNickname(String nickname, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        boolean hasContact = false;
        Uri queryUri = Phone.CONTENT_URI;
        Cursor nicknameCursor = getNicknameCursor(nickname);

        if (nicknameCursor == null) {
            return hasContact;
        }
        try {
            while (nicknameCursor.moveToNext()) {
                String id = nicknameCursor.getString(INDEX_CONTACT_ID_FOR_NICKNAME);
                String selection = ContactsContract.Data.CONTACT_ID + " = ?";
                Cursor cursor = contentResolver
                        .query(queryUri, PROJECTION, selection, new String[]{id}, SORT_ORDER);

                String contactNickname = nicknameCursor.getString(INDEX_NICKNAME);
                fillContactDataFromCursor(cursor, recipients, recipientMap, contactNickname);
                hasContact = true;
            }
        } finally {
            nicknameCursor.close();
        }
        return hasContact;
    }

    private boolean fillContactDataFromNameAndPhone(String query, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        query = "%" + query + "%";
        Uri queryUri = Phone.CONTENT_URI;

        String selection = Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? " +
                " OR (" + ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ? AND " + Data.MIMETYPE + " = '" + Phone.CONTENT_ITEM_TYPE + "')";
        String[] selectionArgs = {query, query};

        Cursor cursor = null;
        try {
            cursor = contentResolver.query(queryUri, PROJECTION, selection, selectionArgs, SORT_ORDER);
        } catch (SecurityException e) {
            aTalkApp.showToastMessage(R.string.contacts_permission_denied_feedback);
        }
        if (cursor == null) {
            return false;
        }
        fillContactDataFromCursor(cursor, recipients, recipientMap);
        return true;
    }

    private void fillContactDataFromCursor(Cursor cursor, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap)
    {
        fillContactDataFromCursor(cursor, recipients, recipientMap, null);
    }

    private void fillContactDataFromCursor(Cursor cursor, List<RecipientSelectView.Recipient> recipients,
            Map<String, RecipientSelectView.Recipient> recipientMap, @Nullable String prefilledName)
    {
        while (cursor.moveToNext()) {
            String name = prefilledName != null ? prefilledName : cursor.getString(INDEX_NAME);
            String phone = cursor.getString(INDEX_PHONE);
            long contactId = cursor.getLong(INDEX_CONTACT_ID);
            String lookupKey = cursor.getString(INDEX_LOOKUP_KEY);

            // phone is invalid or already exists? just skip and use the first default
            if (!RecipientSelectView.Recipient.isValidPhoneNum(phone) || recipientMap.containsKey(phone)) {
                continue;
            }

            int phoneType = cursor.getInt(INDEX_PHONE_TYPE);
            String phoneLabel = null;
            switch (phoneType) {
                case Phone.TYPE_HOME:
                case Phone.TYPE_MOBILE:
                case Phone.TYPE_WORK:
                case Phone.TYPE_OTHER:
                case Phone.TYPE_ISDN:
                case Phone.TYPE_MAIN:
                case Phone.TYPE_WORK_MOBILE:
                case Phone.TYPE_ASSISTANT: {
                    phoneLabel = getContext().getString(Phone.getTypeLabelResource(phoneType));
                    break;
                }
                case Phone.TYPE_CUSTOM: {
                    phoneLabel = cursor.getString(INDEX_PHONE_CUSTOM_LABEL);
                    break;
                }
            }
            RecipientSelectView.Recipient recipient = new RecipientSelectView.Recipient(name, phone, phoneLabel, contactId, lookupKey);
            recipient.photoThumbnailUri = cursor.isNull(INDEX_PHOTO_URI) ? null : Uri.parse(cursor.getString(INDEX_PHOTO_URI));
            recipientMap.put(phone, recipient);
            recipients.add(recipient);
        }
        cursor.close();
    }

    @Override
    public void deliverResult(List<RecipientSelectView.Recipient> data)
    {
        cachedRecipients = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onStartLoading()
    {
        if (cachedRecipients != null) {
            super.deliverResult(cachedRecipients);
            return;
        }
        if (takeContentChanged() || cachedRecipients == null) {
            forceLoad();
        }
    }

    @Override
    protected void onAbandon()
    {
        super.onAbandon();

        if (observerKey != null) {
            contentResolver.unregisterContentObserver(observerKey);
        }
        if (observerContact != null) {
            contentResolver.unregisterContentObserver(observerContact);
        }
    }
}
