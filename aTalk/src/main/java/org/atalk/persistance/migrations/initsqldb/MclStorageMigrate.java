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
package org.atalk.persistance.migrations.initsqldb;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.contactlist.MetaContactListServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.ContactJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.aTalkApp;
import org.atalk.util.StringUtils;
import org.atalk.util.xml.XMLException;
import org.atalk.util.xml.XMLUtils;
import org.bouncycastle.util.encoders.Base64;
import org.json.*;
import org.w3c.dom.*;

import java.io.File;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

import static org.atalk.persistance.migrations.initsqldb.PropertiesMigrate.otrValues;
import static org.atalk.util.xml.XMLUtils.findChild;

/**
 * The class handles the conversion of an existing contactlist.xml file where a persistent copy
 * of the meta contact list is stored, to the new SQLite database: i.e. with tables -
 * metaContactGroup and childContacts
 * <p>
 * A default rootGroup is created for apps to operation properly in case:
 * 1) No contactlist.xml file exists for the meta contact list.
 * 2) File exists but <proto-groups/> root group has zero child.
 *
 * @author Eng Chong Meng
 */
public class MclStorageMigrate
{
    /**
     * The XML Document containing the contact list file.
     */
    private static Document contactListDocument = null;

    /**
     * A reference to the file containing the locally stored meta contact list.
     */
    private static File contactListFile = null;

    /**
     * A reference to the MetaContactListServiceImpl that created and started us.
     */
    private MetaContactListServiceImpl mclServiceImpl = null;

    /**
     * The name of the system property that stores the name of the contact list file.
     */
    private static final String DEFAULT_FILE_NAME = "contactlist.xml";

    /**
     * The name of the XML node corresponding to a meta contact group.
     */
    private static final String GROUP_NODE_NAME = "group";

    /**
     * The name of the XML node corresponding to a collection of meta contact subgroups.
     */
    private static final String SUBGROUPS_NODE_NAME = "subgroups";

    /**
     * The name of the XML attribute that contains group names.
     */
    private static final String GROUP_NAME_ATTR_NAME = "name";

    /**
     * The name of the XML attribute that contains group UIDs.
     */
    private static final String GROUP_UID_ATTR_NAME = "uid";

    /**
     * The name of the XML node that contains protocol specific group descriptorS.
     */
    private static final String PROTO_GROUPS_NODE_NAME = "proto-groups";

    /**
     * The name of the XML attribute that contains unique identifiers
     */
    private static final String UID_ATTR_NAME = "uid";

    /**
     * The name of the XML attribute that contains unique identifiers for parent contact groups.
     */
    private static final String PARENT_PROTO_GROUP_UID_ATTR_NAME = "parent-proto-group-uid";

    /**
     * The name of the XML attribute that contains account identifiers indicating proto group's
     * and proto contacts' owning providers.
     */
    private static final String ACCOUNT_ID_ATTR_NAME = "account-id";

    /**
     * The name of the XML node that contains meta contact details.
     */
    private static final String META_CONTACT_NODE_NAME = "meta-contact";

    /**
     * The name of the XML node that contains meta contact display names.
     */
    private static final String META_CONTACT_DISPLAY_NAME_NODE_NAME = "display-name";

    /**
     * The name of the XML attribute that contains true/false, whether this meta contact was
     * renamed by user.
     */
    private static final String USER_DEFINED_DISPLAY_NAME_ATTR_NAME = "user-defined";

    /**
     * The name of the XML node that contains meta contact detail.
     */
    private static final String META_CONTACT_DETAIL_NAME_NODE_NAME = "detail";

    /**
     * The name of the XML attribute that contains detail name.
     */
    private static final String DETAIL_NAME_ATTR_NAME = "name";

    /**
     * The name of the XML attribute that contains detail value.
     */
    private static final String DETAIL_VALUE_ATTR_NAME = "value";

    /**
     * The name of the XML node that contains information of a proto contact
     */
    private static final String PROTO_CONTACT_NODE_NAME = "contact";

    /**
     * The name of the XML node that contains information of a proto contact
     */
    private static final String PROTO_CONTACT_ADDRESS_ATTR_NAME = "address";

    /**
     * The name of the XML node that contains information that contacts or groups returned as
     * persistent and that should be used when restoring a contact or a group.
     */
    private static final String PERSISTENT_DATA_NODE_NAME = "persistent-data";

    /**
     * The name of the XML node that contains all meta contact nodes inside a group
     */
    private static final String CHILD_CONTACTS_NODE_NAME = "child-contacts";

    private static SQLiteDatabase mDB;

    private static Map<String, String> contacts = new Hashtable<>();
    private static ContentValues contentValues = new ContentValues();
    private static JSONObject fpKeys;
    private static JSONObject details = new JSONObject();

    /**
     * Map containing AccountID private and public KeyPair
     */
    private static HashMap<String, KeyPair> keyPairs = new HashMap<>();

    /**
     * Map containing Contact to FingerPrints table
     */
    private static HashMap<String, JSONArray> jsonFP = new HashMap<>();


    /**
     * Initializes the storage manager and makes it do initial load and parsing of the contact
     * list file.
     */
    public static void migrateMetaContactList(SQLiteDatabase db)
    {
        mDB = db;
        // Initialize table by creating a root metaContactGroup entry by default
        // Note: contactlist.xml can have <proto-groups/> with zero child at root.
        initMCLDataBase(db);

        Context ctx = aTalkApp.getGlobalContext();
        contactListFile = new File(ctx.getFilesDir() + "/" + DEFAULT_FILE_NAME);
        if (contactListFile.exists()) {
            try {
                // load the contact list
                DocumentBuilder builder
                        = XMLUtils.newDocumentBuilderFactory().newDocumentBuilder();

                if (contactListFile.length() != 0) {
                    try {
                        contactListDocument = builder.parse(contactListFile);
                    } catch (Throwable ex) {
                        Timber.e("Error parsing configuration file: %s", ex.getMessage());
                    }
                }
            } catch (ParserConfigurationException ex) {
                // it is not highly probable that this might happen - so lets just log it.
                Timber.e("Error finding configuration for default parsers: %s", ex.getMessage());
            }

            Element rootXML = (Element) contactListDocument.getFirstChild();
            Node groupNode = XMLUtils.findChild(rootXML, GROUP_NODE_NAME);
            processGroupXmlNode(groupNode);
            processOtrProperties();
        }
    }

    /**
     * Parses the contact list file and calls corresponding "add" methods belonging to
     * <tt>mclServiceImpl</tt> for every meta contact and meta contact group stored in the
     * (contactlist.xml) file that correspond to a provider caring the specified
     * <tt>accountID</tt>.
     *
     * @param accountID the identifier of the account whose contacts we're interested in.
     * @throws XMLException if a problem occurs while parsing contact list contents.
     */
    void extractContactsForAccount(String accountID)
            throws XMLException
    {
        try {
            Element root = findMetaContactGroupNode(mclServiceImpl.getRoot().getMetaUID());
            if (root != null) {
                // if there is root lets parse it parse the group node and extract all its
                // child groups and contacts
                // processGroupXmlNode(mclServiceImpl, accountID, root, null, null);
            }
        } catch (Throwable exc) {
            throw new XMLException("Failed to extract contacts for account " + accountID, exc);
        }
    }

    /**
     * Parses <tt>groupNode</tt> and all of its subNodes, creating corresponding instances through
     * <tt>mclServiceImpl</tt> as children of <tt>parentGroup</tt>
     */
    public static void processGroupXmlNode(Node groupNode)
    {
        ContentValues mcgValues = new ContentValues();
        ContentValues ccValues = new ContentValues();

        String groupMetaUID = XMLUtils.getAttribute(groupNode, GROUP_UID_ATTR_NAME);
        String groupDisplayName = XMLUtils.getAttribute(groupNode, GROUP_NAME_ATTR_NAME);
        mcgValues.put(MetaContactGroup.MC_GROUP_UID, groupMetaUID);
        mcgValues.put(MetaContactGroup.MC_GROUP_NAME, groupDisplayName);

        // extract and load one by one all proto groups in this meta group.
        Node protoGroupsNode = findChild((Element) groupNode, PROTO_GROUPS_NODE_NAME);
        NodeList protoGroups = protoGroupsNode.getChildNodes();

        for (int i = 0; i < protoGroups.getLength(); i++) {
            Node currentPGNode = protoGroups.item(i);
            if (currentPGNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            String protoGroupUID = XMLUtils.getAttribute(currentPGNode, UID_ATTR_NAME);
            mcgValues.put(MetaContactGroup.PROTO_GROUP_UID, protoGroupUID);

            String groupAccountUID = XMLUtils.getAttribute(currentPGNode, ACCOUNT_ID_ATTR_NAME);

            // values.put(MetaContactGroup.PG_ACCOUNT_UID, groupAccountUID);
            mcgValues.put(MetaContactGroup.ACCOUNT_UUID,
                    PropertiesMigrate.accountValues.get(groupAccountUID));

            String parentProtoGroupUID = XMLUtils.getAttribute(currentPGNode,
                    PARENT_PROTO_GROUP_UID_ATTR_NAME);

            Element persistentDataNode = findChild((Element) currentPGNode,
                    PERSISTENT_DATA_NODE_NAME);
            String persistentData = "";
            if (persistentDataNode != null) {
                persistentData = XMLUtils.getText(persistentDataNode);
            }
            mcgValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);

            if (!StringUtils.isNullOrEmpty(parentProtoGroupUID)) {
                Timber.i("Process meta Group: %s: %s", parentProtoGroupUID, protoGroupUID);
                mcgValues.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, parentProtoGroupUID);
                mDB.insert(MetaContactGroup.TABLE_NAME, null, mcgValues);
            }
            // update root MetaContactRootGroup if <proto-groups/> child is specified
            else {
                Timber.i("Process root metaContactGroup: %s", ContactGroup.ROOT_NAME);
                String[] args = {ContactGroup.ROOT_NAME};
                mDB.update(MetaContactGroup.TABLE_NAME, mcgValues,
                        MetaContactGroup.PARENT_PROTO_GROUP_UID + "=?", args);
            }
        }

        // we have parsed groups now go over the children contacts
        Node childContactsNode = findChild((Element) groupNode, CHILD_CONTACTS_NODE_NAME);
        NodeList childContacts = (childContactsNode == null)
                ? null : childContactsNode.getChildNodes();

        // go over every meta contact, extract its details and its encapsulated proto contacts
        for (int i = 0; (childContacts != null) && (i < childContacts.getLength()); i++) {
            Node currentMetaContactNode = childContacts.item(i);
            if (currentMetaContactNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            ccValues.clear();
            try {
                String mcUid = XMLUtils.getAttribute(currentMetaContactNode, UID_ATTR_NAME);
                ccValues.put(MetaContactGroup.MC_UID, mcUid);

                // Extract chile contact details.
                Element contactNode = findChild((Element) currentMetaContactNode,
                        PROTO_CONTACT_NODE_NAME);

                String address = contactNode.getAttribute(PROTO_CONTACT_ADDRESS_ATTR_NAME);
                ccValues.put(MetaContactGroup.CONTACT_JID, address);

                String accountId = contactNode.getAttribute(ACCOUNT_ID_ATTR_NAME);
                String accountUuid = PropertiesMigrate.accountValues.get(accountId);
                // values.put(MetaContactGroup.ACCOUNT_UID, accountId);
                ccValues.put(MetaContactGroup.ACCOUNT_UUID, accountUuid);

                String parentPgUid = contactNode.getAttribute(PARENT_PROTO_GROUP_UID_ATTR_NAME);
                ccValues.put(MetaContactGroup.PROTO_GROUP_UID, parentPgUid);

                Timber.i("Process child contact: %s: %s", parentPgUid, address);
                Element displayNameNode = findChild((Element) currentMetaContactNode,
                        META_CONTACT_DISPLAY_NAME_NODE_NAME);
                String displayName = displayNameNode.getTextContent();
                ccValues.put(MetaContactGroup.MC_DISPLAY_NAME, displayName);

                String isDNUserDefined
                        = displayNameNode.getAttribute(USER_DEFINED_DISPLAY_NAME_ATTR_NAME);
                if (StringUtils.isNullOrEmpty(isDNUserDefined))
                    isDNUserDefined = "false";
                ccValues.put(MetaContactGroup.MC_USER_DEFINED, isDNUserDefined);

                Element persistentDataNode = findChild((Element) currentMetaContactNode,
                        PERSISTENT_DATA_NODE_NAME);
                String persistentData = "";
                if (persistentDataNode != null) {
                    persistentData = XMLUtils.getText(persistentDataNode);
                }
                ccValues.put(MetaContactGroup.PERSISTENT_DATA, persistentData);
                ccValues.put(MetaContactGroup.MC_DETAILS, details.toString());

                mDB.insert(MetaContactGroup.TBL_CHILD_CONTACTS, null, ccValues);

                // Create an entry in accountValues for later metaChatSession migration
                PropertiesMigrate.accountValues.put(accountUuid + address, mcUid);

                if (!contacts.containsKey(address)) {
                    contacts.put(address, mcUid);
                    contentValues.clear();
                    contentValues.put(Contact.CONTACT_UUID, mcUid);
                    contentValues.put(Contact.PROTOCOL_PROVIDER, "Jabber");
                    contentValues.put(Contact.CONTACT_JID, address);
                    String svrDisplayName = Boolean.valueOf(isDNUserDefined)
                            ? address : displayName;
                    contentValues.put(Contact.SVR_DISPLAY_NAME, svrDisplayName);
                    mDB.insert(Contact.TABLE_NAME, null, contentValues);
                }
            } catch (Throwable thr) {
                Timber.w(thr, "Failed to parse meta contact %s", currentMetaContactNode);
            }
        }

        // now, last thing that's left to do - go over all subgroups if any
        Node subgroupsNode = findChild((Element) groupNode, SUBGROUPS_NODE_NAME);
        if (subgroupsNode == null)
            return;

        NodeList subgroups = subgroupsNode.getChildNodes();
        // recurse for every sub meta group
        for (int i = 0; i < subgroups.getLength(); i++) {
            Node currentGroupNode = subgroups.item(i);

            if (currentGroupNode.getNodeType() != Node.ELEMENT_NODE
                    || !currentGroupNode.getNodeName().equals(GROUP_NODE_NAME))
                continue;

            try {
                processGroupXmlNode((Element) currentGroupNode);
            } catch (Throwable throwable) {
                // catch everything and bravely continue with remaining groups and contacts
                Timber.e(throwable, "Failed to process group node %s", currentGroupNode);
            }
        }
    }

    /**
     * Returns the node corresponding to the meta contact with the specified uid or null if no
     * such node was found.
     *
     * @param metaContactUID the UID String of the meta contact whose node we are looking for.
     * @return the node corresponding to the meta contact with the specified UID or null if no
     * such contact was found in the meta contact list file.
     */
    private Element findMetaContactNode(String metaContactUID)
    {
        Element root = (Element) contactListDocument.getFirstChild();
        return XMLUtils.locateElement(root, META_CONTACT_NODE_NAME, UID_ATTR_NAME, metaContactUID);
    }

    /**
     * Returns the node corresponding to the meta contact with the specified uid or null if no
     * such node was found.
     *
     * @param metaContactGroupUID the UID String of the meta contact whose node we are looking for.
     * @return the node corresponding to the meta contact group with the specified UID or null if
     * no such group was found in the meta contact list file.
     */
    private Element findMetaContactGroupNode(String metaContactGroupUID)
    {
        Element root = (Element) contactListDocument.getFirstChild();
        return XMLUtils.locateElement(root, GROUP_NODE_NAME, UID_ATTR_NAME, metaContactGroupUID);
    }

    /**
     * Process all the relevant OTR Properties only after the accountID and metaContact tables
     * have been fully updated
     */
    private static void processOtrProperties()
    {
        int idx;
        byte[] b64Key;
        String accountID, contact, property;
        String FP = "fingerprints";
        PublicKey publicKey;
        PrivateKey privateKey;
        KeyPair keyPair;
        KeyFactory kf;

        for (Map.Entry<String, String> entry : otrValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Timber.i("Process account otr properties: %S: %S", key, value);

            idx = key.lastIndexOf(".");
            if (key.startsWith("Jabber")) {
                if (key.contains("privateKey") || key.contains("publicKey")) {
                    accountID = key.substring(0, idx);
                    property = key.substring(idx + 1);
                    try {
                        kf = KeyFactory.getInstance("DSA");
                        b64Key = Base64.decode(value.getBytes());
                        if (keyPairs.containsKey(accountID)) {
                            publicKey = keyPairs.get(accountID).getPublic();
                            privateKey = keyPairs.get(accountID).getPrivate();
                        }
                        else {
                            publicKey = null;
                            privateKey = null;
                        }

                        if ("privateKey".equals(property)) {
                            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(b64Key);
                            privateKey = kf.generatePrivate(privateKeySpec);
                        }
                        else if ("publicKey".equals(property)) {
                            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64Key);
                            publicKey = kf.generatePublic(publicKeySpec);
                        }
                        keyPair = new KeyPair(publicKey, privateKey);
                        keyPairs.put(accountID, keyPair);
                        if ((publicKey != null) && (privateKey != null)) {
                            saveOtrKey(accountID, keyPair);
                        }
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        Timber.e("Extract account OTR keypair failed! Exception: %s", e.getMessage());
                    }
                }
            }
            else if (key.contains(FP)) {
                contact = key.substring(0, idx);
                String fingerPrint = value.toLowerCase();
                putJsonFingerprint(contact, fingerPrint);
                updateContactKeys(contact);
            }
        }
    }

    private static void saveOtrKey(String accountID, KeyPair keyPair)
    {
        Timber.i("Store account OTR keypair for: %s", accountID);
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        KeyFactory keyFactory;
        JSONObject otrKeys = new JSONObject();

        try {
            keyFactory = KeyFactory.getInstance("DSA");
            DSAPrivateKeySpec privateKeySpec
                    = keyFactory.getKeySpec(privateKey, DSAPrivateKeySpec.class);
            DSAPublicKeySpec publicKeySpec
                    = keyFactory.getKeySpec(publicKey, DSAPublicKeySpec.class);
            otrKeys.put("otr_x", privateKeySpec.getX().toString(16));
            otrKeys.put("otr_g", privateKeySpec.getG().toString(16));
            otrKeys.put("otr_p", privateKeySpec.getP().toString(16));
            otrKeys.put("otr_q", privateKeySpec.getQ().toString(16));
            otrKeys.put("otr_y", publicKeySpec.getY().toString(16));

            String[] args = {accountID};
            contentValues.clear();
            contentValues.put(AccountID.KEYS, otrKeys.toString());
            mDB.update(AccountID.TABLE_NAME, contentValues, AccountID.ACCOUNT_UID + "=?", args);
        } catch (final NoSuchAlgorithmException | InvalidKeySpecException | JSONException e) {
            Timber.e("Saving account private & public keys failed! Exception: %s", e.getMessage());
        }
    }

    private static void putJsonFingerprint(String contact, String print)
    {
        fpKeys = new JSONObject();
        try {
            JSONArray fingerprints;
            if (jsonFP.containsKey(contact + "2")) {
                JSONArray policy = jsonFP.get(contact + "2");
                fpKeys.put(ContactJabberImpl.OTR_POLICY, policy.getString(0));
            }

            if (jsonFP.containsKey(contact)) {
                fingerprints = jsonFP.get(contact);
            }
            else {
                fingerprints = new JSONArray();
            }
            fingerprints.put(print);
            fpKeys.put(ContactJabberImpl.OTR_FP, fingerprints);
            jsonFP.put(contact, fingerprints);
        } catch (final JSONException ex) {
            Timber.e("Error in processing FingerPrint: %s", ex.getMessage());
        }
    }

    private static void updateContactKeys(String contact)
    {
        String[] args = {contact};
        contentValues.clear();
        contentValues.put(Contact.KEYS, fpKeys.toString());
        mDB.update(Contact.TABLE_NAME, contentValues, Contact.CONTACT_JID + "=?", args);
    }

    private static void putOtrPolicy(String contact, String policy)
    {
        fpKeys = new JSONObject();
        try {
            JSONArray jsPolicy = new JSONArray();
            JSONArray fingerprints;

            fpKeys.put(ContactJabberImpl.OTR_POLICY, policy);
            jsonFP.put(contact + "2", jsPolicy.put(policy));

            if (jsonFP.containsKey(contact)) {
                fingerprints = jsonFP.get(contact);
                fpKeys.put(ContactJabberImpl.OTR_FP, fingerprints);
            }
        } catch (final JSONException ex) {
            Timber.e("Error in processing OTR Policy: %s", ex.getMessage());
        }
    }

    /**
     * Fills the metaContactGroup table with the rootGroup necessary for it to be filled properly
     * as the meta contact list evolves.
     */
    public static void initMCLDataBase(SQLiteDatabase db)
    {
        ContentValues mclValues = new ContentValues();
        // dummy account for root group
        String accNodeName = "acc" + Long.toString(System.currentTimeMillis());

        mclValues.put(MetaContactGroup.ACCOUNT_UUID, accNodeName);
        mclValues.put(MetaContactGroup.PARENT_PROTO_GROUP_UID, ContactGroup.ROOT_NAME);
        mclValues.put(MetaContactGroup.MC_GROUP_UID, ContactGroup.ROOT_GROUP_UID);
        mclValues.put(MetaContactGroup.PROTO_GROUP_UID, ContactGroup.ROOT_PROTO_GROUP_UID);
        mclValues.put(MetaContactGroup.MC_GROUP_NAME, ContactGroup.ROOT_GROUP_NAME);
        db.insert(MetaContactGroup.TABLE_NAME, null, mclValues);
    }
}
