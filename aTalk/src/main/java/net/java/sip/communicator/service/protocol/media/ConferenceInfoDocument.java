/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import android.text.TextUtils;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.atalk.util.xml.XMLException;
import org.atalk.util.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import timber.log.Timber;

/**
 * A class that represents a Conference Information XML document as defined in RFC4575. It wraps
 * around a DOM <code>Document</code> providing convenience functions.
 *
 * {@link "https://tools.ietf.org/html/rfc4575"}
 *
 * @author Boris Grozev
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ConferenceInfoDocument {
    /**
     * The namespace of the conference-info element.
     */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:conference-info";

    /**
     * The name of the "conference-info" element.
     */
    public static final String CONFERENCE_INFO_ELEMENT = "conference-info";

    /**
     * The name of the "conference-description" element.
     */
    public static final String CONFERENCE_DESCRIPTION_ELEMENT = "conference-description";

    /**
     * The name of the "conference-state" element.
     */
    public static final String CONFERENCE_STATE_ELEMENT = "conference-state";

    /**
     * The name of the "state" attribute.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * The name of the "entity" attribute.
     */
    public static final String ENTITY_ATTR_NAME = "entity";

    /**
     * The name of the "version" attribute.
     */
    public static final String VERSION_ATTR_NAME = "version";

    /**
     * The name of the "user" element.
     */
    public static final String USER_ELEMENT = "user";

    /**
     * The name of the "users" element.
     */
    public static final String USERS_ELEMENT = "users";

    /**
     * The name of the "endpoint" element.
     */
    public static final String ENDPOINT_ELEMENT = "endpoint";

    /**
     * The name of the "media" element.
     */
    public static final String MEDIA_ELEMENT = "media";

    /**
     * The name of the "id" attribute.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The name of the "status" element.
     */
    public static final String STATUS_ELEMENT = "status";

    /**
     * The name of the "src-id" element.
     */
    public static final String SRC_ID_ELEMENT = "src-id";

    /**
     * The name of the "type" element.
     */
    public static final String TYPE_ELEMENT = "type";

    /**
     * The name of the "user-count" element.
     */
    public static final String USER_COUNT_ELEMENT = "user-count";

    /**
     * The mane of the "display-text" element.
     */
    public static final String DISPLAY_TEXT_ELEMENT = "display-text";

    /**
     * The <code>Document</code> object that we wrap around.
     */
    private Document document;

    /**
     * The single <code>conference-info</code> element of <code>document</code>
     */
    private Element conferenceInfo;

    /**
     * The <code>conference-description</code> child element of <code>conference-info</code>.
     */
    private Element conferenceDescription;

    /**
     * The <code>conference-state</code> child element of <code>conference-info</code>.
     */
    private Element conferenceState;

    /**
     * The <code>conference-state</code> child element of <code>conference-state</code>.
     */
    private Element userCount;

    /**
     * The <code>users</code> child element of <code>conference-info</code>.
     */
    private Element users;

    /**
     * A list of <code>User</code>s representing the children of <code>users</code>
     */
    private final List<User> usersList = new LinkedList<>();

    /**
     * Creates a new <code>ConferenceInfoDocument</code> instance.
     *
     * @throws XMLException if a document failed to be created.
     */
    public ConferenceInfoDocument()
            throws XMLException {
        try {
            document = XMLUtils.createDocument();
        } catch (Exception e) {
            Timber.e(e, "Failed to create a new document.");
            throw (new XMLException(e.getMessage()));
        }

        conferenceInfo = document.createElementNS(NAMESPACE, CONFERENCE_INFO_ELEMENT);
        document.appendChild(conferenceInfo);

        setVersion(1);

        conferenceDescription = document.createElement(CONFERENCE_DESCRIPTION_ELEMENT);
        conferenceInfo.appendChild(conferenceDescription);

        conferenceState = document.createElement(CONFERENCE_STATE_ELEMENT);
        conferenceInfo.appendChild(conferenceState);
        setUserCount(0);

        users = document.createElement(USERS_ELEMENT);
        conferenceInfo.appendChild(users);
    }

    /**
     * Creates a new <code>ConferenceInfoDocument</code> instance and populates it by parsing the XML in
     * <code>xml</code>
     *
     * @param xml the XML string to parse
     *
     * @throws XMLException If parsing failed
     */
    public ConferenceInfoDocument(String xml)
            throws XMLException {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        try {
            document = XMLUtils.newDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new XMLException(e.getMessage());
        }

        conferenceInfo = document.getDocumentElement();
        if (conferenceInfo == null) {
            throw new XMLException("Could not parse conference-info document,"
                    + " conference-info element not found");
        }

        conferenceDescription = XMLUtils.findChild(conferenceInfo,
                CONFERENCE_DESCRIPTION_ELEMENT);
        // conference-description is mandatory
        if (conferenceDescription == null) {
            throw new XMLException("Could not parse conference-info document,"
                    + " conference-description element not found");
        }

        conferenceState = XMLUtils.findChild(conferenceInfo, CONFERENCE_STATE_ELEMENT);
        if (conferenceState != null)
            userCount = XMLUtils.findChild(conferenceState, USER_COUNT_ELEMENT);

        users = XMLUtils.findChild(conferenceInfo, USERS_ELEMENT);
        if (users == null) {
            throw new XMLException("Could not parse conference-info document,"
                    + " 'users' element not found");
        }
        NodeList usersNodeList = users.getElementsByTagName(USER_ELEMENT);
        for (int i = 0; i < usersNodeList.getLength(); i++) {
            User user = new User((Element) usersNodeList.item(i));
            usersList.add(user);
        }
    }

    /**
     * Creates a new <code>ConferenceInfoDocument</code> instance that represents a copy of
     * <code>confInfo</code>
     *
     * @param confInfo the document to copy
     *
     * @throws XMLException if a document failed to be created.
     */
    public ConferenceInfoDocument(ConferenceInfoDocument confInfo)
            throws XMLException {
        this();

        // temporary
        String sid = confInfo.getSid();
        if (!TextUtils.isEmpty(sid))
            setSid(sid);

        setEntity(confInfo.getEntity());
        setState(confInfo.getState());
        setUserCount(confInfo.getUserCount());
        setUsersState(confInfo.getUsersState());
        setVersion(confInfo.getVersion());
        for (User user : confInfo.getUsers())
            addUser(user);
    }

    /**
     * Returns the value of the <code>version</code> attribute of the <code>conference-info</code> element,
     * or -1 if there is no <code>version</code> attribute or if it's value couldn't be parsed as an
     * integer.
     *
     * @return the value of the <code>version</code> attribute of the <code>conference-info</code> element,
     * or -1 if there is no <code>version</code> attribute or if it's value couldn't be parsed
     * as an integer.
     */
    public int getVersion() {
        String versionString = conferenceInfo.getAttribute(VERSION_ATTR_NAME);
        if (versionString == null)
            return -1;
        int version = -1;
        try {
            version = Integer.parseInt(versionString);
        } catch (NumberFormatException e) {
            Timber.i("Failed to parse version string: %s", versionString);
        }

        return version;
    }

    /**
     * Sets the <code>version</code> attribute of the <code>conference-info</code> element.
     *
     * @param version the value to set the <code>version</code> attribute of the <code>conference-info</code>
     * element to.
     */
    public void setVersion(int version) {
        conferenceInfo.setAttribute(VERSION_ATTR_NAME, Integer.toString(version));
    }

    /**
     * Gets the value of the <code>state</code> attribute of the <code>conference-info</code> element.
     *
     * @return the value of the <code>state</code> attribute of the <code>conference-info</code> element.
     */
    public State getState() {
        return getState(conferenceInfo);
    }

    /**
     * Returns the value of the <code>state</code> attribute of the <code>users</code> child of the
     * <code>conference-info</code> element.
     *
     * @return the value of the <code>state</code> attribute of the <code>users</code> child of the
     * <code>conference-info</code> element.
     */
    public State getUsersState() {
        return getState(users);
    }

    /**
     * Sets the <code>state</code> attribute of the <code>users</code> chuld of the <code>conference-info</code>
     * element.
     *
     * @param state the state to set
     */
    public void setUsersState(State state) {
        setState(users, state);
    }

    /**
     * Sets the value of the <code>state</code> attribute of the <code>conference-info</code> element.
     *
     * @param state the value to set the <code>state</code> attribute of the <code>conference-info</code> element
     * to.
     */
    public void setState(State state) {
        setState(conferenceInfo, state);
    }

    /**
     * Sets the value of the <code>sid</code> attribute of the <code>conference-info</code> element. This is
     * not part of RFC4575 and is here because we are temporarily using it in our XMPP
     * implementation. TODO: remote it when we define another way to handle the Jingle SID
     *
     * @param sid the value to set the <code>sid</code> attribute of the <code>conference-info</code> element
     * to.
     */
    public void setSid(String sid) {
        if (sid == null || sid.equals(""))
            conferenceInfo.removeAttribute("sid");
        else
            conferenceInfo.setAttribute("sid", sid);
    }

    /**
     * Gets the value of the <code>sid</code> attribute of the <code>conference-info</code> element. This is
     * not part of RFC4575 and is here because we are temporarily using it in our XMPP
     * implementation. TODO: remote it when we define another way to handle the Jingle SID
     */
    public String getSid() {
        return conferenceInfo.getAttribute("sid");
    }

    /**
     * Sets the value of the <code>entity</code> attribute of the <code>conference-info</code> element.
     *
     * @param entity the value to set the <code>entity</code> attribute of the <code>conference-info</code>
     * document to.
     */
    public void setEntity(String entity) {
        if (entity == null || entity.equals(""))
            conferenceInfo.removeAttribute(ENTITY_ATTR_NAME);
        else
            conferenceInfo.setAttribute(ENTITY_ATTR_NAME, entity);
    }

    /**
     * Gets the value of the <code>entity</code> attribute of the <code>conference-info</code> element.
     *
     * @return The value of the <code>entity</code> attribute of the <code>conference-info</code> element.
     */
    public String getEntity() {
        return conferenceInfo.getAttribute(ENTITY_ATTR_NAME);
    }

    /**
     * Sets the content of the <code>user-count</code> child element of the <code>conference-state</code>
     * child element of <code>conference-info</code>
     *
     * @param count the value to set the content of <code>user-count</code> to
     */
    public void setUserCount(int count) {
        // conference-state and its user-count child aren't mandatory
        if (userCount != null) {
            userCount.setTextContent(Integer.toString(count));
        }
        else {
            if (conferenceState == null) {
                conferenceState = document.createElement(CONFERENCE_STATE_ELEMENT);
                conferenceInfo.appendChild(conferenceState);
            }

            userCount = document.createElement(USER_COUNT_ELEMENT);
            userCount.setTextContent(Integer.toString(count));
            conferenceState.appendChild(userCount);
        }
    }

    /**
     * Returns the content of the <code>user-count</code> child of the <code>conference-state</code> child
     * of <code>conference-info</code>, parsed as an integer, if they exist. Returns -1 if either there
     * isn't a <code>conference-state</code> element, it doesn't have a <code>user-count</code> child, or
     * parsing as integer failed.
     *
     * @return the content of the <code>user-count</code> child of the <code>conference-state</code> child
     * of <code>conference-info</code> element.
     */
    public int getUserCount() {
        int ret = -1;
        try {
            ret = Integer.parseInt(userCount.getTextContent());
        } catch (Exception e) {
            Timber.w("Could not parse user-count field");
        }
        return ret;
    }

    /**
     * Returns the XML representation of the <code>conference-info</code> tree, or <code>null</code> if an
     * error occurs while trying to get it.
     *
     * @return the XML representation of the <code>conference-info</code> tree, or <code>null</code> if an
     * error occurs while trying to get it.
     */
    public String toXml(String enclosingNamespace) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter buffer = new StringWriter();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(conferenceInfo), new StreamResult(buffer));
            return buffer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the XML representation of the document (from the <code>conference-info</code> element
     * down), or an error string in case the XML cannot be generated for some reason.
     *
     * @return the XML representation of the document or an error string.
     */
    @Override
    public String toString() {
        String s = toXml(null);
        return s == null ? "Could not get conference-info XML" : s;
    }

    /**
     * Returns the list of <code>User</code> that represents the <code>user</code> children of the
     * <code>users</code> child element of <code>conference-info</code>
     *
     * @return the list of <code>User</code> that represents the <code>user</code> children of the
     * <code>users</code> child element of <code>conference-info</code>
     */
    public List<User> getUsers() {
        return usersList;
    }

    /**
     * Searches this document's <code>User</code>s and returns the one with <code>entity</code> attribute
     * <code>entity</code>, or <code>null</code> if one wasn't found.
     *
     * @param entity The value of the <code>entity</code> attribute to search for.
     *
     * @return the <code>User</code> of this document with <code>entity</code> attribute <code>entity</code>, or
     * <code>null</code> if one wasn't found.
     */
    public User getUser(String entity) {
        if (entity == null)
            return null;
        for (User u : usersList) {
            if (entity.equals(u.getEntity()))
                return u;
        }
        return null;
    }

    /**
     * Creates a new <code>User</code> instance, adds it to the document and returns it.
     *
     * @param entity The value to use for the <code>entity</code> attribute of the new <code>User</code>.
     *
     * @return the newly created <code>User</code> instance.
     */
    public User addNewUser(String entity) {
        Element userElement = document.createElement(USER_ELEMENT);
        User user = new User(userElement);
        user.setEntity(entity);

        users.appendChild(userElement);
        usersList.add(user);

        return user;
    }

    /**
     * Adds a copy of <code>user</code> to this <code>ConferenceInfoDocument</code>
     *
     * @param user the <code>User</code> to add a copy of
     */
    public void addUser(User user) {
        User newUser = addNewUser(user.getEntity());
        newUser.setDisplayText(user.getDisplayText());
        newUser.setState(user.getState());
        for (Endpoint endpoint : user.getEndpoints())
            newUser.addEndpoint(endpoint);
    }

    /**
     * Removes a specific <code>User</code> (the one with entity <code>entity</code>) from the document.
     *
     * @param entity the entity of the <code>User</code> to remove.
     */
    public void removeUser(String entity) {
        User user = getUser(entity);
        if (user != null) {
            usersList.remove(user);
            users.removeChild(user.userElement);
        }
    }

    /**
     * Returns the <code>Document</code> that this instance wraps around.
     *
     * @return the <code>Document</code> that this instance wraps around.
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Returns the <code>State</code> corresponding to the <code>state</code> attribute of an
     * <code>Element</code>. Default to <code>State.FULL</code> which is the RFC4575 default.
     *
     * @param element the <code>Element</code>
     *
     * @return the <code>State</code> corresponding to the <code>state</code> attribute of an
     * <code>Element</code>.
     */
    private State getState(Element element) {
        State state = State.parseString(element.getAttribute(STATE_ATTR_NAME));
        return state == null ? State.FULL : state;
    }

    /**
     * Sets the "state" attribute of <code>element</code> to <code>state</code>. If <code>state</code> is
     * <code>State.FULL</code> removes the "state" attribute, because this is the default value.
     *
     * @param element The <code>Element</code> for which to set the "state" attribute of.
     * @param state the <code>State</code> which to set.
     */
    private void setState(Element element, State state) {
        if (element != null) {
            if (state == State.FULL || state == null)
                element.removeAttribute(STATE_ATTR_NAME);
            else
                element.setAttribute(STATE_ATTR_NAME, state.toString());
        }
    }

    /**
     * Sets the <code>status</code> child element of <code>element</code>. If <code>statusString</code> is
     * <code>null</code>, the child element is removed if present.
     *
     * @param element the <code>Element</code> for which to set the <code>status</code> child element.
     * @param statusString the <code>String</code> to use for the text content of the <code>status</code> element
     */
    private void setStatus(Element element, String statusString) {
        Element statusElement = XMLUtils.findChild(element, STATUS_ELEMENT);
        if (statusString == null || statusString.equals("")) {
            if (statusElement != null)
                element.removeChild(statusElement);
        }
        else {
            if (statusElement == null) {
                statusElement = document.createElement(STATUS_ELEMENT);
                element.appendChild(statusElement);
            }
            statusElement.setTextContent(statusString);
        }
    }

    /**
     * Represents the possible values for the <code>state</code> attribute (see RFC4575)
     */
    public enum State {
        /**
         * State <code>full</code>
         */
        FULL("full"),

        /**
         * State <code>partial</code>
         */
        PARTIAL("partial"),

        /**
         * State <code>deleted</code>
         */
        DELETED("deleted");

        /**
         * The name of this <code>State</code>
         */
        private String name;

        /**
         * Creates a <code>State</code> instance with the specified name.
         *
         * @param name
         */
        State(String name) {
            this.name = name;
        }

        /**
         * Returns the name of this <code>State</code>
         *
         * @return the name of this <code>State</code>
         */
        @Override
        public String toString() {
            return name;
        }

        /**
         * Returns a <code>State</code> value corresponding to the specified <code>name</code>
         *
         * @return a <code>State</code> value corresponding to the specified <code>name</code>
         */
        public static State parseString(String name) {
            if (FULL.toString().equals(name))
                return FULL;
            else if (PARTIAL.toString().equals(name))
                return PARTIAL;
            else if (DELETED.toString().equals(name))
                return DELETED;
            else
                return null;
        }
    }

    /**
     * Wraps around an <code>Element</code> and represents a <code>user</code> element (child of the
     * <code>users</code> element). See RFC4575.
     */
    public class User {
        /**
         * The underlying <code>Element</code>.
         */
        private Element userElement;

        /**
         * The list of <code>Endpoint</code>s representing the <code>endpoint</code> children of this
         * <code>User</code>'s element.
         */
        private List<Endpoint> endpointsList = new LinkedList<>();

        /**
         * Creates a new <code>User</code> instance with the specified <code>Element</code> as its
         * underlying element.
         *
         * @param user the <code>Element</code> to use
         */
        private User(Element user) {
            this.userElement = user;
            NodeList endpointsNodeList = user.getElementsByTagName(ENDPOINT_ELEMENT);
            for (int i = 0; i < endpointsNodeList.getLength(); i++) {
                Endpoint endpoint = new Endpoint((Element) endpointsNodeList.item(i));
                endpointsList.add(endpoint);
            }
        }

        /**
         * Sets the <code>entity</code> attribute of this <code>User</code>'s element to <code>entity</code>
         *
         * @param entity the value to set for the <code>entity</code> attribute.
         */
        public void setEntity(String entity) {
            if (entity == null || entity.equals(""))
                userElement.removeAttribute(ENTITY_ATTR_NAME);
            else
                userElement.setAttribute(ENTITY_ATTR_NAME, entity);
        }

        /**
         * Returns the value of the <code>entity</code> attribute of this <code>User</code>'s element.
         *
         * @return the value of the <code>entity</code> attribute of this <code>User</code>'s element.
         */
        public String getEntity() {
            return userElement.getAttribute(ENTITY_ATTR_NAME);
        }

        /**
         * Sets the <code>state</code> attribute of this <code>User</code>'s element to <code>state</code>
         *
         * @param state the value to use for the <code>state</code> attribute.
         */
        public void setState(State state) {
            ConferenceInfoDocument.this.setState(userElement, state);
        }

        /**
         * Returns the value of the <code>state</code> attribute of this <code>User</code>'s element
         *
         * @return the value of the <code>state</code> attribute of this <code>User</code>'s element
         */
        public State getState() {
            return ConferenceInfoDocument.this.getState(userElement);
        }

        /**
         * Sets the <code>display-text</code> child element to this <code>User</code>'s element.
         *
         * @param text the text content to use for the <code>display-text</code> element.
         */
        public void setDisplayText(String text) {
            Element displayText = XMLUtils.findChild(userElement, DISPLAY_TEXT_ELEMENT);
            if (text == null || text.equals("")) {
                if (displayText != null)
                    userElement.removeChild(displayText);
            }
            else {
                if (displayText == null) {
                    displayText = document.createElement(DISPLAY_TEXT_ELEMENT);
                    userElement.appendChild(displayText);
                }
                displayText.setTextContent(text);
            }
        }

        /**
         * Returns the text content of the <code>display-text</code> child element of this <code>User</code>
         * 's element, if it has such a child. Returns <code>null</code> otherwise.
         *
         * @return the text content of the <code>display-text</code> child element of this <code>User</code>
         * 's element, if it has such a child. Returns <code>null</code> otherwise.
         */
        public String getDisplayText() {
            Element displayText = XMLUtils.findChild(userElement, DISPLAY_TEXT_ELEMENT);
            if (displayText != null)
                return displayText.getTextContent();

            return null;
        }

        /**
         * Returns the list of <code>Endpoint</code>s which represent the <code>endpoint</code> children of
         * this <code>User</code>'s element.
         *
         * @return the list of <code>Endpoint</code>s which represent the <code>endpoint</code> children of
         * this <code>User</code>'s element.
         */
        public List<Endpoint> getEndpoints() {
            return endpointsList;
        }

        /**
         * Searches this <code>User</code>'s associated <code>Endpoint</code>s and returns the one with
         * <code>entity</code> attribute <code>entity</code>, or <code>null</code> if one wasn't found.
         *
         * @param entity The value of the <code>entity</code> attribute to search for.
         *
         * @return The <code>Endpoint</code> with <code>entity</code> attribute <code>entity</code>, or
         * <code>null</code> if one wasn't found.
         */
        public Endpoint getEndpoint(String entity) {
            if (entity == null)
                return null;
            for (Endpoint e : endpointsList) {
                if (entity.equals(e.getEntity()))
                    return e;
            }
            return null;
        }

        /**
         * Creates a new <code>Endpoint</code> instance, adds it to this <code>User</code> and returns it.
         *
         * @param entity The value to use for the <code>entity</code> attribute of the new <code>Endpoint</code>.
         *
         * @return the newly created <code>Endpoint</code> instance.
         */
        public Endpoint addNewEndpoint(String entity) {
            Element endpointElement = document.createElement(ENDPOINT_ELEMENT);
            Endpoint endpoint = new Endpoint(endpointElement);
            endpoint.setEntity(entity);

            userElement.appendChild(endpointElement);
            endpointsList.add(endpoint);

            return endpoint;
        }

        /**
         * Adds a copy of <code>endpoint</code> to this <code>User</code>
         *
         * @param endpoint the <code>Endpoint</code> to add a copy of
         */
        public void addEndpoint(Endpoint endpoint) {
            Endpoint newEndpoint = addNewEndpoint(endpoint.getEntity());
            newEndpoint.setStatus(endpoint.getStatus());
            newEndpoint.setState(endpoint.getState());
            for (Media media : endpoint.getMedias())
                newEndpoint.addMedia(media);
        }

        /**
         * Removes a specific <code>Endpoint</code> (the one with entity <code>entity</code>) from this
         * <code>User</code>.
         *
         * @param entity the <code>entity</code> of the <code>Endpoint</code> to remove
         */
        public void removeEndpoint(String entity) {
            Endpoint endpoint = getEndpoint(entity);
            if (endpoint != null) {
                endpointsList.remove(endpoint);
                userElement.removeChild(endpoint.endpointElement);
            }
        }
    }

    /**
     * Wraps around an <code>Element</code> and represents an <code>endpoint</code> element. See RFC4575.
     */
    public class Endpoint {
        /**
         * The underlying <code>Element</code>.
         */
        private Element endpointElement;

        /**
         * The list of <code>Media</code>s representing the <code>media</code> children elements of this
         * <code>Endpoint</code>'s element.
         */
        private List<Media> mediasList = new LinkedList<>();

        /**
         * Creates a new <code>Endpoint</code> instance with the specified <code>Element</code> as its
         * underlying element.
         *
         * @param endpoint the <code>Element</code> to use
         */
        private Endpoint(Element endpoint) {
            this.endpointElement = endpoint;
            NodeList mediaNodeList = endpoint.getElementsByTagName(MEDIA_ELEMENT);
            for (int i = 0; i < mediaNodeList.getLength(); i++) {
                Media media = new Media((Element) mediaNodeList.item(i));
                mediasList.add(media);
            }
        }

        /**
         * Sets the <code>entity</code> attribute of this <code>Endpoint</code>'s element to <code>entity</code>
         *
         * @param entity the value to set for the <code>entity</code> attribute.
         */
        public void setEntity(String entity) {
            if (entity == null || entity.equals(""))
                endpointElement.removeAttribute(ENTITY_ATTR_NAME);
            else
                endpointElement.setAttribute(ENTITY_ATTR_NAME, entity);
        }

        /**
         * Returns the <code>entity</code> attribute of this <code>Endpoint</code>'s element.
         *
         * @return the <code>entity</code> attribute of this <code>Endpoint</code>'s element.
         */
        public String getEntity() {
            return endpointElement.getAttribute(ENTITY_ATTR_NAME);
        }

        /**
         * Sets the <code>state</code> attribute of this <code>User</code>'s element to <code>state</code>
         *
         * @param state the value to use for the <code>state</code> attribute.
         */
        public void setState(State state) {
            ConferenceInfoDocument.this.setState(endpointElement, state);
        }

        /**
         * Returns the value of the <code>state</code> attribute of this <code>Endpoint</code>'s element
         *
         * @return the value of the <code>state</code> attribute of this <code>Endpoint</code>'s element
         */
        public State getState() {
            return ConferenceInfoDocument.this.getState(endpointElement);
        }

        /**
         * Sets the <code>status</code> child element of this <code>Endpoint</code>'s element.
         *
         * @param status the value to be used for the text content of the <code>status</code> element.
         */
        public void setStatus(EndpointStatusType status) {
            ConferenceInfoDocument.this.setStatus(endpointElement,
                    status == null ? null : status.toString());
        }

        /**
         * Returns the <code>EndpointStatusType</code> corresponding to the <code>status</code> child of
         * this <code>Endpoint</code> 's element, or <code>null</code>.
         *
         * @return the <code>EndpointStatusType</code> corresponding to the <code>status</code> child of
         * this <code>Endpoint</code> 's element, or <code>null</code>.
         */
        public EndpointStatusType getStatus() {
            Element statusElement = XMLUtils.findChild(endpointElement, STATUS_ELEMENT);
            return statusElement == null ? null : EndpointStatusType.parseString(statusElement
                    .getTextContent());
        }

        /**
         * Returns the list of <code>Media</code>s which represent the <code>media</code> children of this
         * <code>Endpoint</code>'s element.
         *
         * @return the list of <code>Media</code>s which represent the <code>media</code> children of this
         * <code>Endpoint</code>'s element.
         */
        public List<Media> getMedias() {
            return mediasList;
        }

        /**
         * Searches this <code>Endpoint</code>'s associated <code>Media</code>s and returns the one with
         * <code>id</code> attribute <code>id</code>, or <code>null</code> if one wasn't found.
         *
         * @param id The value of the <code>id</code> attribute to search for.
         *
         * @return The <code>Media</code>s with <code>id</code> attribute <code>id</code>, or <code>null</code> if
         * one wasn't found.
         */
        public Media getMedia(String id) {
            if (id == null)
                return null;
            for (Media m : mediasList) {
                if (id.equals(m.getId()))
                    return m;
            }
            return null;
        }

        /**
         * Creates a new <code>Media</code> instance, adds it to this <code>Endpoint</code> and returns it.
         *
         * @param id The value to use for the <code>id</code> attribute of the new <code>Media</code>'s
         * element.
         *
         * @return the newly created <code>Media</code> instance.
         */
        public Media addNewMedia(String id) {
            Element mediaElement = document.createElement(MEDIA_ELEMENT);
            Media media = new Media(mediaElement);
            media.setId(id);

            endpointElement.appendChild(mediaElement);
            mediasList.add(media);

            return media;
        }

        /**
         * Adds a copy of <code>media</code> to this <code>Endpoint</code>
         *
         * @param media the <code>Media</code> to add a copy of
         */
        public void addMedia(Media media) {
            Media newMedia = addNewMedia(media.getId());
            newMedia.setSrcId(media.getSrcId());
            newMedia.setType(media.getType());
            newMedia.setStatus(media.getStatus());
        }

        /**
         * Removes a specific <code>Media</code> (the one with id <code>id</code>) from this
         * <code>Endpoint</code>.
         *
         * @param id the <code>id</code> of the <code>Media</code> to remove.
         */
        public void removeMedia(String id) {
            Media media = getMedia(id);
            if (media != null) {
                mediasList.remove(media);
                endpointElement.removeChild(media.mediaElement);
            }
        }
    }

    /**
     * Wraps around an <code>Element</code> and represents a <code>media</code> element. See RFC4575.
     */
    public class Media {
        /**
         * The underlying <code>Element</code>.
         */
        private Element mediaElement;

        /**
         * Creates a new <code>Media</code> instance with the specified <code>Element</code> as its
         * underlying element.
         *
         * @param media the <code>Element</code> to use
         */
        private Media(Element media) {
            this.mediaElement = media;
        }

        /**
         * Sets the <code>id</code> attribute of this <code>Media</code>'s element to <code>id</code>
         *
         * @param id the value to set for the <code>id</code> attribute.
         */
        public void setId(String id) {
            if (id == null || id.equals(""))
                mediaElement.removeAttribute(ID_ATTR_NAME);
            else
                mediaElement.setAttribute(ID_ATTR_NAME, id);
        }

        /**
         * Returns the <code>id</code> attribute of this <code>Media</code>'s element.
         *
         * @return the <code>id</code> attribute of this <code>Media</code>'s element.
         */
        public String getId() {
            return mediaElement.getAttribute(ID_ATTR_NAME);
        }

        /**
         * Sets the <code>src-id</code> child element of this <code>Media</code>'s element.
         *
         * @param srcId the value to be used for the text content of the <code>src-id</code> element.
         */
        public void setSrcId(String srcId) {
            Element srcIdElement = XMLUtils.findChild(mediaElement, SRC_ID_ELEMENT);
            if (srcId == null || srcId.equals("")) {
                if (srcIdElement != null)
                    mediaElement.removeChild(srcIdElement);
            }
            else {
                if (srcIdElement == null) {
                    srcIdElement = document.createElement(SRC_ID_ELEMENT);
                    mediaElement.appendChild(srcIdElement);
                }
                srcIdElement.setTextContent(srcId);
            }
        }

        /**
         * Returns the text content of the <code>src-id</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         *
         * @return the text content of the <code>src-id</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         */
        public String getSrcId() {
            Element srcIdElement = XMLUtils.findChild(mediaElement, SRC_ID_ELEMENT);
            return srcIdElement == null ? null : srcIdElement.getTextContent();
        }

        /**
         * Sets the <code>type</code> child element of this <code>Media</code>'s element.
         *
         * @param type the value to be used for the text content of the <code>type</code> element.
         */
        public void setType(String type) {
            Element typeElement = XMLUtils.findChild(mediaElement, TYPE_ELEMENT);
            if (type == null || type.equals("")) {
                if (typeElement != null)
                    mediaElement.removeChild(typeElement);
            }
            else {
                if (typeElement == null) {
                    typeElement = document.createElement(TYPE_ELEMENT);
                    mediaElement.appendChild(typeElement);
                }
                typeElement.setTextContent(type);
            }
        }

        /**
         * Returns the text content of the <code>type</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         *
         * @return the text content of the <code>type</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         */
        public String getType() {
            Element typeElement = XMLUtils.findChild(mediaElement, TYPE_ELEMENT);
            return typeElement == null ? null : typeElement.getTextContent();
        }

        /**
         * Sets the <code>status</code> child element of this <code>Media</code>'s element.
         *
         * @param status the value to be used for the text content of the <code>status</code> element.
         */
        public void setStatus(String status) {
            ConferenceInfoDocument.this.setStatus(mediaElement, status);
        }

        /**
         * Returns the text content of the <code>status</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         *
         * @return the text content of the <code>status</code> child element of this <code>Media</code>'s
         * element, if it has such a child. Returns <code>null</code> otherwise.
         */
        public String getStatus() {
            Element statusElement = XMLUtils.findChild(mediaElement, STATUS_ELEMENT);
            return statusElement == null ? null : statusElement.getTextContent();
        }
    }

    /**
     * Endpoint status type.
     *
     * @author Sebastien Vincent
     */
    public enum EndpointStatusType {
        /**
         * Pending.
         */
        pending("pending"),

        /**
         * Dialing-out.
         */
        dialing_out("dialing-out"),

        /**
         * Dialing-in.
         */
        dialing_in("dialing-in"),

        /**
         * Alerting.
         */
        alerting("alerting"),

        /**
         * On-hold.
         */
        on_hold("on-hold"),

        /**
         * Connected.
         */
        connected("connected"),

        /**
         * Muted via focus.
         */
        muted_via_focus("mute-via-focus"),

        /**
         * Disconnecting.
         */
        disconnecting("disconnecting"),

        /**
         * Disconnected.
         */
        disconnected("disconnected");

        /**
         * The name of this type.
         */
        private final String type;

        /**
         * Creates a <code>EndPointType</code> instance with the specified name.
         *
         * @param type type name.
         */
        private EndpointStatusType(String type) {
            this.type = type;
        }

        /**
         * Returns the type name.
         *
         * @return type name
         */
        @Override
        public String toString() {
            return type;
        }

        /**
         * Returns a <code>EndPointType</code>.
         *
         * @param typeStr the <code>String</code> that we'd like to parse.
         *
         * @return an EndPointType.
         *
         * @throws IllegalArgumentException in case <code>typeStr</code> is not a valid <code>EndPointType</code>.
         */
        public static EndpointStatusType parseString(String typeStr)
                throws IllegalArgumentException {
            for (EndpointStatusType value : values())
                if (value.toString().equals(typeStr))
                    return value;

            throw new IllegalArgumentException(typeStr + " is not a valid reason");
        }
    }
}
