/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.contactsource;

import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The <code>ContactDetail</code> is a detail of a <code>SourceContact</code>
 * corresponding to a specific address (phone number, email, identifier, etc.),
 * which defines the different possible types of communication and the preferred
 * <code>ProtocolProviderService</code>s to go through.
 * <p/>
 * Example: A <code>ContactDetail</code> could define two types of communication,
 * by declaring two supported operation sets
 * <code>OperationSetBasicInstantMessaging</code> to indicate the support of instant
 * messages and <code>OperationSetBasicTelephony</code> to indicate the support of
 * telephony. It may then specify a certain <code>ProtocolProviderService</code> to
 * go through only for instant messages. This would mean that for sending an
 * instant message to this <code>ContactDetail</code> one should obtain an instance
 * of the <code>OperationSetBasicInstantMessaging</code> from the specific
 * <code>ProtocolProviderService</code> and send a message through it. However when
 * no provider is specified for telephony operations, then one should try to
 * obtain all currently available telephony providers and let the user make
 * their choice.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public class ContactDetail
{
    /**
     * Defines all possible categories for a <code>ContactDetail</code>.
     */
    public enum Category
    {
        /**
         * The standard/well-known category of a <code>ContactDetail</code>
         * representing personal details, like name, last name, nickname.
         */
        Personal("Personal"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code>
         * representing personal details, like web address.
         */
        Web("Web"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code>
         * representing organization details, like organization name and job title.
         */
        Organization("Organization"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code> representing an e-mail
         * address.
         */
        Email("Email"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code> representing a contact
         * address for instant messaging.
         */
        InstantMessaging("InstantMessaging"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code> representing a phone number.
         */
        Phone("Phone"),

        /**
         * The standard/well-known category of a <code>ContactDetail</code> representing a postal
         * address.
         */
        Address("Address");

        /**
         * Current enum value.
         */
        private final String value;

        /**
         * Creates enum within the specified value.
         *
         * @param value the value to set.
         */
        Category(String value)
        {
            this.value = value;
        }

        /**
         * Gets the value.
         *
         * @return the value
         */
        public String value()
        {
            return value;
        }

        /**
         * Creates enum from its value.
         *
         * @param value the enum's value.
         * @return created enum.
         */
        public static Category fromString(String value)
        {
            if (value != null) {
                for (Category category : Category.values()) {
                    if (value.equalsIgnoreCase(category.value())) {
                        return category;
                    }
                }
                return null;
            }
            return null;
        }
    }

    /**
     * Defines all possible sub-categories for a <code>ContactDetail</code>.
     */
    public enum SubCategory
    {
        /**
         * The standard/well-known label of a <code>ContactDetail</code>
         * representing a name. It could be an organization name or a personal name.
         */
        Name("Name"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a last name.
         */
        LastName("LastName"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a nickname.
         */
        Nickname("Nickname"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a postal code.
         */
        HomePage("HomePage"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing an address of a
         * contact at their home.
         */
        Home("Home"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a mobile
         * contact address (e.g. a cell phone number).
         */
        Mobile("Mobile"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing an address of a
         * contact at their work.
         */
        Work("Work"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a fax number.
         */
        Fax("Fax"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a different
         * number.
         */
        Other("Other"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing an IM network
         * (like for example jabber).
         */
        AIM("AIM"),
        ICQ("ICQ"),
        Jabber("XMPP"),
        Skype("Skype"),
        Yahoo("Yahoo"),
        GoogleTalk("GoogleTalk"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a country name.
         */
        Country("Country"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a state name.
         */
        State("State"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a city name.
         */
        City("City"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a street address.
         */
        Street("Street"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a postal code.
         */
        PostalCode("PostalCode"),

        /**
         * The standard/well-known label of a <code>ContactDetail</code> representing a job title.
         */
        JobTitle("JobTitle");

        /**
         * Current enum value.
         */
        private final String value;

        /**
         * Creates enum within the specified value.
         *
         * @param value the value to set.
         */
        SubCategory(String value)
        {
            this.value = value;
        }

        /**
         * Gets the value.
         *
         * @return the value
         */
        public String value()
        {
            return value;
        }

        /**
         * Creates enum from its value.
         *
         * @param value the enum's value.
         * @return created enum.
         */
        public static SubCategory fromString(String value)
        {
            if (value != null) {
                for (SubCategory subCategory : SubCategory.values()) {
                    if (value.equalsIgnoreCase(subCategory.value())) {
                        return subCategory;
                    }
                }
                return null;
            }
            return null;
        }
    }

    /**
     * The category of this <code>ContactQuery</code>.
     */
    private final Category category;

    /**
     * The address of this contact detail. This should be the address through which the contact
     * could be reached by one of the supported <code>OperationSet</code>s (e.g. by IM, call).
     */
    protected String contactDetailValue;

    /**
     * The display name of this detail.
     */
    private String detailDisplayName;

    /**
     * The set of labels of this <code>ContactDetail</code>. The labels may be
     * arbitrary and may include any of the standard/well-known labels defined
     * by the <code>LABEL_XXX</code> constants of the <code>ContactDetail</code> class.
     */
    private final Collection<SubCategory> subCategories
            = new LinkedList<SubCategory>();

    /**
     * A mapping of <code>OperationSet</code> classes and preferred protocol providers for them.
     */
    private Map<Class<? extends OperationSet>, ProtocolProviderService> preferredProviders;

    /**
     * A mapping of <code>OperationSet</code> classes and preferred protocol name for them.
     */
    private Map<Class<? extends OperationSet>, String> preferredProtocols;

    /**
     * A list of all supported <code>OperationSet</code> classes.
     */
    private List<Class<? extends OperationSet>> supportedOpSets = null;

    /**
     * Creates a <code>ContactDetail</code> by specifying the contact address, corresponding to this
     * detail.
     *
     * @param contactDetailValue the contact detail value corresponding to this detail
     */
    public ContactDetail(String contactDetailValue)
    {
        this(contactDetailValue, null, null, null);
    }

    /**
     * Creates a <code>ContactDetail</code> by specifying the contact address,
     * corresponding to this detail.
     *
     * @param contactDetailValue the contact detail value corresponding to this detail
     * @param detailDisplayName the display name of this detail
     */
    public ContactDetail(String contactDetailValue, String detailDisplayName)
    {
        this(contactDetailValue, detailDisplayName, null, null);
    }

    /**
     * Initializes a new <code>ContactDetail</code> instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new <code>ContactDetail</code> instance
     * @param category
     */
    public ContactDetail(String contactDetailValue,
            Category category)
    {
        this(contactDetailValue, null, category, null);
    }

    /**
     * Initializes a new <code>ContactDetail</code> instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new <code>ContactDetail</code> instance
     * @param detailDisplayName the display name of this detail
     * @param category
     */
    public ContactDetail(String contactDetailValue,
            String detailDisplayName,
            Category category)
    {
        this(contactDetailValue, detailDisplayName, category, null);
    }

    /**
     * Initializes a new <code>ContactDetail</code> instance which is to represent a
     * specific contact address and which is to be optionally labeled with a
     * specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new <code>ContactDetail</code> instance
     * @param category
     * @param subCategories the set of sub categories with which the new <code>ContactDetail</code> instance is to be
     * labeled.
     */
    public ContactDetail(String contactDetailValue,
            Category category,
            SubCategory[] subCategories)
    {
        this(contactDetailValue, null, category, subCategories);
    }

    /**
     * Initializes a new <code>ContactDetail</code> instance which is to represent a specific contact
     * address and which is to be optionally labeled with a specific set of labels.
     *
     * @param contactDetailValue the contact detail value to be represented by the new <code>ContactDetail</code> instance
     * @param detailDisplayName the display name of this detail
     * @param category
     * @param subCategories the set of sub categories with which the new <code>ContactDetail</code> instance is to be
     * labeled.
     */
    public ContactDetail(String contactDetailValue,
            String detailDisplayName,
            Category category,
            SubCategory[] subCategories)
    {
        // the value of the detail
        this.contactDetailValue = contactDetailValue;

        if (StringUtils.isNotEmpty(detailDisplayName)) {
            this.detailDisplayName = detailDisplayName;
        }
        else if (category == Category.Phone) {
            this.detailDisplayName = ContactSourceActivator.getPhoneNumberI18nService()
                    .formatForDisplay(contactDetailValue);
        }
        else {
            this.detailDisplayName = contactDetailValue;
        }

        // category & labels
        this.category = category;

        if (subCategories != null) {
            for (SubCategory subCategory : subCategories) {
                if ((subCategory != null)
                        && !this.subCategories.contains(subCategory)) {
                    this.subCategories.add(subCategory);
                }
            }
        }
    }

    /**
     * Sets a mapping of preferred <code>ProtocolProviderServices</code> for a specific
     * <code>OperationSet</code>.
     *
     * @param preferredProviders a mapping of preferred <code>ProtocolProviderService</code>s for specific
     * <code>OperationSet</code> classes
     */
    public void setPreferredProviders(
            Map<Class<? extends OperationSet>, ProtocolProviderService> preferredProviders)
    {
        this.preferredProviders = preferredProviders;
    }

    /**
     * Sets a mapping of a preferred <code>preferredProtocol</code> for a specific
     * <code>OperationSet</code>. The preferred protocols are meant to be set by
     * contact source implementations that don't have a specific protocol
     * providers to suggest, but are able to propose just the name of the
     * protocol to be used for a specific operation. If both - preferred
     * provider and preferred protocol are set, then the preferred protocol
     * provider should be prioritized.
     *
     * @param preferredProtocols a mapping of preferred
     * <code>ProtocolProviderService</code>s for specific <code>OperationSet</code> classes
     */
    public void setPreferredProtocols(
            Map<Class<? extends OperationSet>, String> preferredProtocols)
    {
        this.preferredProtocols = preferredProtocols;

        // protocol added so an opset is supported, add it if missing
        for (Class<? extends OperationSet> opsetClass
                : preferredProtocols.keySet()) {
            if (supportedOpSets == null || !supportedOpSets.contains(opsetClass))
                addSupportedOpSet(opsetClass);
        }
    }

    /**
     * Creates a <code>ContactDetail</code> by specifying the corresponding contact
     * address and a list of all <code>supportedOpSets</code>, indicating what are
     * the supporting actions with this contact detail (e.g. sending a message,
     * making a call, etc.)
     *
     * @param supportedOpSets a list of all <code>supportedOpSets</code>, indicating what are the supporting actions
     * with this contact detail (e.g. sending a message, making a call, etc.)
     */
    public void setSupportedOpSets(
            List<Class<? extends OperationSet>> supportedOpSets)
    {
        this.supportedOpSets = supportedOpSets;
    }

    /**
     * Adds a supported OpSet to the list of supported OpSets.
     *
     * @param supportedOpSet the OpSet to support.
     */
    public void addSupportedOpSet(Class<? extends OperationSet> supportedOpSet)
    {
        if (this.supportedOpSets == null) {
            this.supportedOpSets
                    = new ArrayList<Class<? extends OperationSet>>(2);
        }

        this.supportedOpSets.add(supportedOpSet);
    }

    /**
     * Gets the category, if any, of this <code>ContactQuery</code>.
     *
     * @return the category of this <code>ContactQuery</code> if it has any;
     * otherwise, <code>null</code>
     */
    public Category getCategory()
    {
        return category;
    }

    /**
     * Returns the contact address corresponding to this detail.
     *
     * @return the contact address corresponding to this detail
     */
    public String getDetail()
    {
        return contactDetailValue;
    }

    /**
     * Returns the display name of this detail. By default returns the detail value.
     *
     * @return the display name of this detail
     */
    public String getDisplayName()
    {
        return detailDisplayName;
    }

    /**
     * Returns the preferred <code>ProtocolProviderService</code> when using the
     * given <code>opSetClass</code>.
     *
     * @param opSetClass the <code>OperationSet</code> class corresponding to a
     * certain action (e.g. sending an instant message, making a call, etc.).
     * @return the preferred <code>ProtocolProviderService</code> corresponding to
     * the given <code>opSetClass</code>
     */
    public ProtocolProviderService getPreferredProtocolProvider(
            Class<? extends OperationSet> opSetClass)
    {
        if (preferredProviders != null && preferredProviders.size() > 0)
            return preferredProviders.get(opSetClass);

        return null;
    }

    /**
     * Returns the name of the preferred protocol for the operation given by
     * the <code>opSetClass</code>. The preferred protocols are meant to be set by
     * contact source implementations that don't have a specific protocol
     * providers to suggest, but are able to propose just the name of the
     * protocol to be used for a specific operation. If both - preferred
     * provider and preferred protocol are set, then the preferred protocol
     * provider should be prioritized.
     *
     * @param opSetClass the <code>OperationSet</code> class corresponding to a
     * certain action (e.g. sending an instant message, making a call, etc.).
     * @return the name of the preferred protocol for the operation given by
     * the <code>opSetClass</code>
     */
    public String getPreferredProtocol(Class<? extends OperationSet> opSetClass)
    {
        if (preferredProtocols != null && preferredProtocols.size() > 0)
            return preferredProtocols.get(opSetClass);

        return null;
    }

    /**
     * Returns a list of all supported <code>OperationSet</code> classes, which
     * would indicate what are the supported actions by this contact
     * (e.g. write a message, make a call, etc.)
     *
     * @return a list of all supported <code>OperationSet</code> classes
     */
    public List<Class<? extends OperationSet>> getSupportedOperationSets()
    {
        return supportedOpSets;
    }

    /**
     * Determines whether the set of labels of this <code>ContactDetail</code>
     * contains a specific label. The labels may be arbitrary and may include
     * any of the standard/well-known labels defined by the <code>LABEL_XXX</code>
     * constants of the <code>ContactDetail</code> class.
     *
     * @param subCategory the subCategory to be determined whether
     * it is contained in this <code>ContactDetail</code>
     * @return <code>true</code> if the specified <code>label</code> is contained in the
     * set of labels of this <code>ContactDetail</code>
     */
    public boolean containsSubCategory(SubCategory subCategory)
    {
        return subCategories.contains(subCategory);
    }

    /**
     * Gets the set of labels of this <code>ContactDetail</code>. The labels may be
     * arbitrary and may include any of the standard/well-known labels defined
     * by the <code>LABEL_XXX</code> constants of the <code>ContactDetail</code> class.
     *
     * @return the set of labels of this <code>ContactDetail</code>. If this
     * <code>ContactDetail</code> has no labels, the returned <code>Collection</code> is
     * empty.
     */
    public Collection<SubCategory> getSubCategories()
    {
        return Collections.unmodifiableCollection(subCategories);
    }
}
