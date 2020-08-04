package org.atalk.android.gui.call.telephony;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.tokenautocomplete.TokenCompleteTextView;

import org.apache.james.mime4j.util.CharsetUtil;
import org.atalk.android.R;
import org.atalk.android.gui.aTalk;

import java.io.*;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import timber.log.Timber;

public class RecipientSelectView extends TokenCompleteTextView<RecipientSelectView.Recipient>
        implements LoaderCallbacks<List<RecipientSelectView.Recipient>>, AlternateRecipientAdapter.AlternateRecipientListener
{
    private static final int MINIMUM_LENGTH_FOR_FILTERING = 2;
    private static final String ARG_QUERY = "query";
    private static final int LOADER_ID_FILTERING = 0;
    private static final int LOADER_ID_ALTERNATES = 1;

    private RecipientAdapter adapter;
    @Nullable
    private LoaderManager loaderManager;

    private ListPopupWindow alternatesPopup;
    private AlternateRecipientAdapter alternatesAdapter;
    private Recipient alternatesPopupRecipient;
    private TokenListener<Recipient> listener;

    public RecipientSelectView(Context context)
    {
        super(context);
        initView(context);
    }

    public RecipientSelectView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initView(context);
    }

    public RecipientSelectView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        initView(context);
    }

    private void initView(Context context)
    {
        alternatesPopup = new ListPopupWindow(context);
        alternatesAdapter = new AlternateRecipientAdapter(context, this);
        alternatesPopup.setAdapter(alternatesAdapter);

        // allow only single entry
        setTokenLimit(1);

        // if a token is completed, pick an entry based on best guess.
        // Note that we override performCompletion, so this doesn't actually do anything
        performBestGuess(true);

        adapter = new RecipientAdapter(context);
        setAdapter(adapter);
        setLongClickable(true);

        // cmeng - must init loaderManager in initView to take care of screen rotation
        loaderManager = LoaderManager.getInstance(aTalk.getFragment(aTalk.CL_FRAGMENT));
    }

    @Override
    protected View getViewForObject(Recipient recipient)
    {
        View view = inflateLayout();
        RecipientTokenViewHolder holder = new RecipientTokenViewHolder(view);
        view.setTag(holder);
        bindObjectView(recipient, view);
        return view;
    }

    @SuppressLint("InflateParams")
    private View inflateLayout()
    {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        return layoutInflater.inflate(R.layout.recipient_token_item, null, false);
    }

    private void bindObjectView(Recipient recipient, View view)
    {
        RecipientTokenViewHolder holder = (RecipientTokenViewHolder) view.getTag();
        holder.vName.setText(recipient.getDisplayNameOrPhone());
        holder.vPhone.setText(recipient.getPhone());
        RecipientAdapter.setContactPhotoOrPlaceholder(getContext(), holder.vContactPhoto, recipient);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event)
    {
        int action = event.getActionMasked();
        Editable text = getText();

        if (text != null && action == MotionEvent.ACTION_UP) {
            int offset = getOffsetForPosition(event.getX(), event.getY());

            if (offset != -1) {
                TokenImageSpan[] links = text.getSpans(offset, offset, RecipientTokenSpan.class);
                if (links.length > 0) {
                    showAlternates(links[0].getToken());
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected Recipient defaultObject(String completionText)
    {
        Address[] parsedAddresses = Address.parse(completionText);
        if (!CharsetUtil.isASCII(completionText)) {
            setError(getContext().getString(R.string.recipient_error_non_ascii));
            return null;
        }
        if (parsedAddresses.length == 0 || parsedAddresses[0].getAddress() == null) {
            // aTalk telephony call can go with text string only i.e. not Address
            // setError(getContext().getString(R.string.recipient_error_parse_failed));
            return null;
        }
        return new Recipient(parsedAddresses[0]);
    }

    public boolean isEmpty()
    {
        return getObjects().isEmpty();
    }

    public void setLoaderManager(@Nullable LoaderManager loaderManager)
    {
        this.loaderManager = loaderManager;
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        if (loaderManager != null) {
            loaderManager.destroyLoader(LOADER_ID_ALTERNATES);
            loaderManager.destroyLoader(LOADER_ID_FILTERING);
            loaderManager = null;
        }
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous)
    {
        super.onFocusChanged(hasFocus, direction, previous);
        if (hasFocus) {
            displayKeyboard();
        }
    }

    /**
     * TokenCompleteTextView removes composing strings, and etc, but leaves internal composition
     * predictions partially constructed. Changing either/or the Selection or Candidate start/end
     * positions, forces the IMM to reset cleaner.
     */
    @Override
    protected void replaceText(CharSequence text)
    {
        super.replaceText(text);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.updateSelection(this, getSelectionStart(), getSelectionEnd(), -1, -1);
    }

    private void displayKeyboard()
    {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void showDropDown()
    {
        boolean cursorIsValid = adapter != null;
        if (!cursorIsValid) {
            return;
        }
        super.showDropDown();
    }

    @Override
    public void performCompletion()
    {
        if (getListSelection() == ListView.INVALID_POSITION && enoughToFilter()) {
            Object recipientText = defaultObject(currentCompletionText());
            if (recipientText != null) {
                replaceText(convertSelectionToString(recipientText));
            }
        }
        else {
            super.performCompletion();
        }
    }

    @Override
    protected void performFiltering(@NonNull CharSequence text, int keyCode)
    {
        if (loaderManager == null) {
            return;
        }

        String query = text.toString();
        if (TextUtils.isEmpty(query) || query.length() < MINIMUM_LENGTH_FOR_FILTERING) {
            loaderManager.destroyLoader(LOADER_ID_FILTERING);
            return;
        }
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        loaderManager.restartLoader(LOADER_ID_FILTERING, args, this);
    }

    private void redrawAllTokens()
    {
        Editable text = getText();
        if (text == null) {
            return;
        }
        RecipientTokenSpan[] recipientSpans = text.getSpans(0, text.length(), RecipientTokenSpan.class);
        for (RecipientTokenSpan recipientSpan : recipientSpans) {
            bindObjectView(recipientSpan.getToken(), recipientSpan.view);
        }
        invalidate();
    }

    public void addRecipients(Recipient... recipients)
    {
        for (Recipient recipient : recipients) {
            addObjectSync(recipient);
        }
    }

    public Address[] getAddresses()
    {
        List<Recipient> recipients = getObjects();
        Address[] address = new Address[recipients.size()];
        for (int i = 0; i < address.length; i++) {
            address[i] = recipients.get(i).address;
        }
        return address;
    }

    private void showAlternates(Recipient recipient)
    {
        if (loaderManager == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(getWindowToken(), 0);

        alternatesPopupRecipient = recipient;
        loaderManager.restartLoader(LOADER_ID_ALTERNATES, null, RecipientSelectView.this);
    }

    public void postShowAlternatesPopup(final List<Recipient> data)
    {
        // We delay this call so the soft keyboard is gone by the time the popup is layout
        new Handler().post(() -> showAlternatesPopup(data));
    }

    public void showAlternatesPopup(List<Recipient> data)
    {
        if (loaderManager == null) {
            return;
        }

        // Copy anchor settings from the autocomplete dropdown
        View anchorView = getRootView().findViewById(getDropDownAnchor());
        alternatesPopup.setAnchorView(anchorView);
        alternatesPopup.setWidth(getDropDownWidth());

        alternatesAdapter.setCurrentRecipient(alternatesPopupRecipient);
        alternatesAdapter.setAlternateRecipientInfo(data);

        // Clear the checked item.
        alternatesPopup.show();
        ListView listView = alternatesPopup.getListView();
        if (listView != null)
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event)
    {
        alternatesPopup.dismiss();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public Loader<List<Recipient>> onCreateLoader(int id, Bundle args)
    {
        switch (id) {
            case LOADER_ID_FILTERING: {
                String query = args != null && args.containsKey(ARG_QUERY) ? args.getString(ARG_QUERY) : "";
                adapter.setHighlight(query);
                return new RecipientLoader(getContext(), query);
            }
            case LOADER_ID_ALTERNATES: {
                Uri contactLookupUri = alternatesPopupRecipient.getContactLookupUri();
                if (contactLookupUri != null) {
                    return new RecipientLoader(getContext(), contactLookupUri, true);
                }
                else {
                    return new RecipientLoader(getContext(), alternatesPopupRecipient.address);
                }
            }
        }
        throw new IllegalStateException("Unknown Loader ID: " + id);
    }

    @Override
    public void onLoadFinished(Loader<List<Recipient>> loader, List<Recipient> data)
    {
        if (loaderManager == null) {
            return;
        }

        switch (loader.getId()) {
            case LOADER_ID_FILTERING: {
                adapter.setRecipients(data);
                break;
            }
            case LOADER_ID_ALTERNATES: {
                postShowAlternatesPopup(data);
                loaderManager.destroyLoader(LOADER_ID_ALTERNATES);
                break;
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Recipient>> loader)
    {
        if (loader.getId() == LOADER_ID_FILTERING) {
            adapter.setHighlight(null);
            adapter.setRecipients(null);
        }
    }

    public boolean tryPerformCompletion()
    {
        if (!hasUncompletedText()) {
            return false;
        }
        int previousNumRecipients = getTokenCount();
        performCompletion();
        int numRecipients = getTokenCount();

        return previousNumRecipients != numRecipients;
    }

    private int getTokenCount()
    {
        return getObjects().size();
    }

    public boolean hasUncompletedText()
    {
        String currentCompletionText = currentCompletionText();
        return !TextUtils.isEmpty(currentCompletionText) && !isPlaceholderText(currentCompletionText);
    }

    static private boolean isPlaceholderText(String currentCompletionText)
    {
        // TODO string matching here is sort of a hack, but it's somewhat reliable and the info isn't easily available
        return currentCompletionText.startsWith("+") && currentCompletionText.substring(1).matches("[0-9]+");
    }

    @Override
    public void onRecipientRemove(Recipient currentRecipient)
    {
        alternatesPopup.dismiss();
        removeObjectSync(currentRecipient);
    }

    @Override
    public void onRecipientChange(Recipient recipientToReplace, Recipient alternateRecipient)
    {
        alternatesPopup.dismiss();

        List<Recipient> currentRecipients = getObjects();
        int indexOfRecipient = currentRecipients.indexOf(recipientToReplace);
        if (indexOfRecipient == -1) {
            Timber.e("Tried to refresh invalid view token!");
            return;
        }
        Recipient currentRecipient = currentRecipients.get(indexOfRecipient);
        currentRecipient.address = alternateRecipient.address;
        currentRecipient.addressLabel = alternateRecipient.addressLabel;

        View recipientTokenView = getTokenViewForRecipient(currentRecipient);
        if (recipientTokenView == null) {
            Timber.e("Tried to refresh invalid view token!");
            return;
        }

        bindObjectView(currentRecipient, recipientTokenView);
        if (listener != null) {
            listener.onTokenChanged(currentRecipient);
        }
        invalidate();
    }

    /**
     * This method builds the span given a address object. We override it with identical
     * functionality, but using the custom RecipientTokenSpan class which allows us to
     * retrieve the view for redrawing at a later point.
     */
    @Override
    protected TokenImageSpan buildSpanForObject(Recipient obj)
    {
        if (obj == null) {
            return null;
        }
        View tokenView = getViewForObject(obj);
        return new RecipientTokenSpan(tokenView, obj);
    }

    /**
     * Find the token view tied to a given address. This method relies on spans to
     * be of the RecipientTokenSpan class, as created by the buildSpanForObject method.
     */
    private View getTokenViewForRecipient(Recipient currentRecipient)
    {
        Editable text = getText();
        if (text == null) {
            return null;
        }

        RecipientTokenSpan[] recipientSpans = text.getSpans(0, text.length(), RecipientTokenSpan.class);
        for (RecipientTokenSpan recipientSpan : recipientSpans) {
            if (recipientSpan.getToken().equals(currentRecipient)) {
                return recipientSpan.view;
            }
        }
        return null;
    }

    /**
     * We use a specialized version of TokenCompleteTextView.TokenListener as well,
     * adding a callback for onTokenChanged.
     */
    public void setTokenListener(TokenListener<Recipient> listener)
    {
        super.setTokenListener(listener);
        this.listener = listener;
    }

    public interface TokenListener<T> extends TokenCompleteTextView.TokenListener<T>
    {
        void onTokenChanged(T token);
    }

    private class RecipientTokenSpan extends TokenImageSpan
    {
        private final View view;

        public RecipientTokenSpan(View view, Recipient token)
        {
            super(view, token);
            this.view = view;
        }
    }

    private static class RecipientTokenViewHolder
    {
        final TextView vName;
        final TextView vPhone;
        final ImageView vContactPhoto;

        RecipientTokenViewHolder(View view)
        {
            vName = view.findViewById(android.R.id.text1);
            vPhone = view.findViewById(android.R.id.text2);
            vContactPhoto = view.findViewById(R.id.contact_photo);
        }
    }

    public static class Recipient implements Serializable
    {
        @Nullable // null means the address is not associated with a contact
        public final Long contactId;
        public final String contactLookupKey;

        @NonNull
        public Address address;
        public String addressLabel;

        @Nullable // null if the contact has no photo. transient because we serialize this manually, see below.
        public transient Uri photoThumbnailUri;

        public Recipient(@NonNull Address address)
        {
            this.address = address;
            this.contactId = null;
            this.contactLookupKey = null;
        }

        public Recipient(String name, String phone, String addressLabel, long contactId, String lookupKey)
        {
            this.address = new Address(phone, name);
            this.contactId = contactId;
            this.addressLabel = addressLabel;
            this.contactLookupKey = lookupKey;
        }

        public String getDisplayNameOrPhone()
        {
            final String displayName = getDisplayName();
            if (displayName != null) {
                return displayName;
            }
            return address.getAddress();
        }

        public String getPhone()
        {
            return address.getAddress();
        }

        public static boolean isValidPhoneNum(CharSequence target)
        {
            return (target != null) && (target.length() >= 4)
                    && android.util.Patterns.PHONE.matcher(target).matches();
        }

        public String getDisplayNameOrUnknown(Context context)
        {
            String displayName = getDisplayName();
            if (displayName != null) {
                return displayName;
            }
            return context.getString(R.string.unknown_recipient);
        }

        public String getNameOrUnknown(Context context)
        {
            String name = address.getPerson();
            if (name != null) {
                return name;
            }
            return context.getString(R.string.unknown_recipient);
        }

        private String getDisplayName()
        {
            if (TextUtils.isEmpty(address.getPerson())) {
                return null;
            }

            String displayName = address.getPerson();
            if (addressLabel != null) {
                displayName += " (" + addressLabel + ")";
            }
            return displayName;
        }

        @Nullable
        public Uri getContactLookupUri()
        {
            if (contactId == null) {
                return null;
            }
            return Contacts.getLookupUri(contactId, contactLookupKey);
        }

        @Override
        public boolean equals(Object o)
        {
            // Equality is entirely up to the address
            return o instanceof Recipient && address.equals(((Recipient) o).address);
        }

        private void writeObject(ObjectOutputStream oos)
                throws IOException
        {
            oos.defaultWriteObject();

            // custom serialization, Android's Uri class is not serializable
            if (photoThumbnailUri != null) {
                oos.writeInt(1);
                oos.writeUTF(photoThumbnailUri.toString());
            }
            else {
                oos.writeInt(0);
            }
        }

        private void readObject(ObjectInputStream ois)
                throws ClassNotFoundException, IOException
        {
            ois.defaultReadObject();
            // custom deserialization, Android's Uri class is not serializable
            if (ois.readInt() != 0) {
                String uriString = ois.readUTF();
                photoThumbnailUri = Uri.parse(uriString);
            }
        }
    }
}
