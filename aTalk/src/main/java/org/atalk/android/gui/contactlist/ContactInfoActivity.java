package org.atalk.android.gui.contactlist;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.text.Html;
import android.text.InputFilter;
import android.widget.*;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.service.osgi.OSGiActivity;

import java.text.DateFormat;
import java.util.*;

import androidx.fragment.app.Fragment;
import timber.log.Timber;

/**
 * Activity allows user to view presence status, status message, the avatar and the full
 * vCard-temp information for the {@link #mContact}.
 * <p>
 * The main panel that allows users to view their account information. Different instances of
 * this class are created for every registered <tt>ProtocolProviderService</tt>.
 * Currently, supported account details are first/middle/last names, nickname,
 * street/city/region/country address, postal code, birth date, gender, organization name, job
 * title, about me, home/work email, home/work phone.
 * <p>
 * <p>
 * The {@link #mContact} is retrieved from the {@link Intent} by direct access to
 * {@link ContactListFragment#getClickedContact()} via instance obtained from
 * {@link aTalkApp#getContactListFragment()}.
 *
 * @author Eng Chong Meng
 */

public class ContactInfoActivity extends OSGiActivity
        implements OperationSetServerStoredContactInfo.DetailsResponseListener
{
    /**
     * Mapping between all supported by this plugin <tt>ServerStoredDetails</tt> and their
     * respective <tt>TextView</tt> that are used for modifying the details.
     */
    private final Map<Class<? extends GenericDetail>, TextView> detailToTextField = new HashMap<>();

    private TextView urlField;
    private TextView ageField;
    private TextView birthDateField;

    /**
     * Intent's extra's key for account ID property of this activity
     */
    public static final String INTENT_CONTACT_ID = "contact_id";

    public static final int ABOUT_ME_MAX_CHARACTERS = 200;
    /**
     * The operation set giving access to the server stored contact details.
     */
    private OperationSetServerStoredContactInfo contactInfoOpSet;

    /**
     * The currently selected contact we are displaying information about.
     */
    private Contact mContact;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_info);

        // Get contact ID from intent extras - but cannot link to mContact
        String contactId = getIntent().getStringExtra(INTENT_CONTACT_ID);

        Fragment clf = aTalk.getFragment(aTalk.CL_FRAGMENT);
        if (clf instanceof ContactListFragment) {
            MetaContact metaContact = ((ContactListFragment) clf).getClickedContact();
            if (metaContact == null) {
                Timber.e("Requested contact info not found: %s", contactId);
                finish();
            }
            else {
                mContact = metaContact.getDefaultContact();
                ProtocolProviderService pps = mContact.getProtocolProvider();
                contactInfoOpSet = pps.getOperationSet(OperationSetServerStoredContactInfo.class);
                if ((contactInfoOpSet != null) && pps.isRegistered()) {
                    initPresenceStatus();
                    initSummaryPanel();

                    // Always retrieve new contact vCard-temp info from server. Otherwise contact
                    // info changes after account login will not be reflected in the display info.
                    contactInfoOpSet.requestAllDetailsForContact(mContact, this);
                }
            }
        }
    }

    /**
     * Create and initialize the view with actual values
     */
    private void initPresenceStatus()
    {
        String title = mContact.getDisplayName();
        ActionBarUtil.setTitle(this, title);

        // Setup the contact presence status
        PresenceStatus presenceStatus = mContact.getPresenceStatus();
        if (presenceStatus != null) {
            ActionBarUtil.setStatus(this, presenceStatus.getStatusIcon());

            TextView statusNameView = findViewById(R.id.presenceStatusName);
            ImageView statusIconView = findViewById(R.id.presenceStatusIcon);

            // Set status icon
            Bitmap presenceIcon = AndroidImageUtil.bitmapFromBytes(presenceStatus.getStatusIcon());
            statusIconView.setImageBitmap(presenceIcon);

            // Set status name
            String statusName = presenceStatus.getStatusName();
            statusNameView.setText(statusName);

            // Add users status message if it exists
            TextView statusMessage = findViewById(R.id.statusMessage);
            ProtocolProviderService pps = mContact.getProtocolProvider();
            OperationSetPresence contactPresence = pps.getOperationSet(OperationSetPresence.class);
            String statusMsg = contactPresence.getCurrentStatusMessage();
            // String statusMsg = mContact.getStatusMessage();
            if (StringUtils.isNotBlank(statusMsg)) {
                statusMessage.setText(statusMsg);
            }
        }
    }

    /**
     * Creates a panel that displays the following contact details:
     * <p>
     * Currently, supported contact details are first/middle/last names, nickname,
     * street/city/region/country address, postal code, birth date, gender,
     * organization name, job title, about me, home/work email, home/work phone.
     */
    private void initSummaryPanel()
    {
        // Display name details.
        TextView displayNameField = findViewById(R.id.ci_DisplayNameField);
        detailToTextField.put(DisplayNameDetail.class, displayNameField);

        // First name details.
        TextView firstNameField = findViewById(R.id.ci_FirstNameField);
        detailToTextField.put(FirstNameDetail.class, firstNameField);

        // Middle name details.
        TextView middleNameField = findViewById(R.id.ci_MiddleNameField);
        detailToTextField.put(MiddleNameDetail.class, middleNameField);

        // Last name details.
        TextView lastNameField = findViewById(R.id.ci_LastNameField);
        detailToTextField.put(LastNameDetail.class, lastNameField);

        TextView nicknameField = findViewById(R.id.ci_NickNameField);
        detailToTextField.put(NicknameDetail.class, nicknameField);

        urlField = findViewById(R.id.ci_URLField);
        detailToTextField.put(URLDetail.class, urlField);

        // Gender details.
        TextView genderField = findViewById(R.id.ci_GenderField);
        detailToTextField.put(GenderDetail.class, genderField);

        // Birthday and Age details.
        ageField = findViewById(R.id.ci_AgeField);
        birthDateField = findViewById(R.id.ci_BirthDateField);
        detailToTextField.put(BirthDateDetail.class, birthDateField);

        TextView streetAddressField = findViewById(R.id.ci_StreetAddressField);
        detailToTextField.put(AddressDetail.class, streetAddressField);

        TextView cityField = findViewById(R.id.ci_CityField);
        detailToTextField.put(CityDetail.class, cityField);

        TextView regionField = findViewById(R.id.ci_RegionField);
        detailToTextField.put(ProvinceDetail.class, regionField);

        TextView postalCodeField = findViewById(R.id.ci_PostalCodeField);
        detailToTextField.put(PostalCodeDetail.class, postalCodeField);

        TextView countryField = findViewById(R.id.ci_CountryField);
        detailToTextField.put(CountryDetail.class, countryField);

        // Email details.
        TextView emailField = findViewById(R.id.ci_EMailField);
        detailToTextField.put(EmailAddressDetail.class, emailField);

        TextView workEmailField = findViewById(R.id.ci_WorkEmailField);
        detailToTextField.put(WorkEmailAddressDetail.class, workEmailField);

        // Phone number details.
        TextView phoneField = findViewById(R.id.ci_PhoneField);
        detailToTextField.put(PhoneNumberDetail.class, phoneField);

        TextView workPhoneField = findViewById(R.id.ci_WorkPhoneField);
        detailToTextField.put(WorkPhoneDetail.class, workPhoneField);

        TextView mobilePhoneField = findViewById(R.id.ci_MobilePhoneField);
        detailToTextField.put(MobilePhoneDetail.class, mobilePhoneField);

        TextView organizationField = findViewById(R.id.ci_OrganizationNameField);
        detailToTextField.put(WorkOrganizationNameDetail.class, organizationField);
        TextView jobTitleField = findViewById(R.id.ci_JobTitleField);
        detailToTextField.put(JobTitleDetail.class, jobTitleField);

        TextView aboutMeArea = findViewById(R.id.ci_AboutMeField);
        InputFilter[] filterArray = new InputFilter[1];
        filterArray[0] = new InputFilter.LengthFilter(ABOUT_ME_MAX_CHARACTERS);
        aboutMeArea.setFilters(filterArray);
        aboutMeArea.setBackgroundResource(R.drawable.alpha_blue_01);
        detailToTextField.put(AboutMeDetail.class, aboutMeArea);

        Button mOkButton = findViewById(R.id.button_OK);
        mOkButton.setOnClickListener(v -> finish());
    }

    @Override
    public void detailsRetrieved(final Iterator<GenericDetail> allDetails)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (allDetails != null) {
                while (allDetails.hasNext()) {
                    GenericDetail detail = allDetails.next();
                    loadDetail(detail);
                }
            }
        });
    }

    /**
     * Loads a single <tt>GenericDetail</tt> obtained from the
     * <tt>OperationSetServerStoredAccountInfo</tt> into this plugin.
     *
     * @param detail to be loaded.
     */
    private void loadDetail(GenericDetail detail)
    {
        if (detail instanceof BinaryDetail) {
            ImageView avatarView = findViewById(R.id.contactAvatar);

            // If the user has a contact image, let's use it. If not, leave the default as it
            byte[] avatarImage = (byte[]) detail.getDetailValue();
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage.length);
            avatarView.setImageBitmap(bitmap);

        }
        else if (detail instanceof URLDetail) {
            // If the contact's protocol supports web info, give them a link to get it
            URLDetail urlDetail = (URLDetail) detail;
            final String urlString = urlDetail.getURL().toString();
            // urlField.setText(urlString);

            String html = "Click to see web info for: <a href='"
                    + urlString + "'>"
                    + urlString
                    + "</a>";
            urlField.setText(Html.fromHtml(html));
            urlField.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
                startActivity(browserIntent);
            });
        }
        else if (detail instanceof BirthDateDetail) {
            // birthDateDetail = (BirthDateDetail) detail;
            Calendar calendarDetail = (Calendar) detail.getDetailValue();

            Date birthDate = calendarDetail.getTime();
            DateFormat dateFormat = DateFormat.getDateInstance();
            String birthDateDetail = dateFormat.format(birthDate);
            birthDateField.setText(birthDateDetail);

            // Calculate age based on given birthDate
            Calendar mDate = Calendar.getInstance();
            int age = mDate.get(Calendar.YEAR) - calendarDetail.get(Calendar.YEAR);

            if (mDate.get(Calendar.MONTH) < calendarDetail.get(Calendar.MONTH))
                age--;
            if ((mDate.get(Calendar.MONTH) == calendarDetail.get(Calendar.MONTH))
                    && (mDate.get(Calendar.DAY_OF_MONTH)
                    < calendarDetail.get(Calendar.DAY_OF_MONTH)))
                age--;

            String ageDetail = Integer.toString(age);
            ageField.setText(ageDetail);
        }
        else {
            TextView field = detailToTextField.get(detail.getClass());
            if (field != null) {
                Object obj = detail.getDetailValue();
                if (obj instanceof String)
                    field.setText((String) obj);
                else if (obj != null)
                    field.setText(obj.toString());
            }
        }
    }
}
