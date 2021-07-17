/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.packet.IQ;

/**
 * Coin (Conference Info) IQ. It is used to inform conference participants about useful information
 * (users, ...).
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CoinIQ extends IQ
{
    /**
     * The name of the element that contains the coin data.
     */
    public static final String ELEMENT = "conference-info";

    /**
     * The namespace that coin belongs to.
     */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:conference-info";

    /**
     * Entity attribute name.
     */
    public static final String ENTITY_ATTR_NAME = "entity";

    /**
     * Jingle session ID attribute name.
     */
    public static final String SID_ATTR_NAME = "sid";

    /**
     * Version attribute name.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * Version attribute name.
     */
    public static final String VERSION_ATTR_NAME = "version";

    /**
     * Entity name.
     */
    private String entity = null;

    /**
     * Jingle session ID.
     */
    private String sid = null;

    /**
     * State.
     */
    private StateType state = StateType.full;

    /**
     * Version.
     */
    private Integer version = 0;

    public CoinIQ()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the XML string of this Jingle IQ's "section" sub-element.
     *
     * Extensions of this class must override this method.
     *
     * @return the child element section of the IQ XML.
     */

    // <iq to='abc123@atalk.org/jitsi-15b488a' id='WikRS-116' type='set'>
    // <conference-info xmlns='urn:ietf:params:xml:ns:conference-info' version="1" entity="xmpp:leopard@atalk.org/atalk" sid="8bhihsq6md3g2">
    // <conference-description/>
    // <conference-state><user-count>2</user-count></conference-state>
    // <users>
    // <user entity="xmpp:leopard@atalk.org/atalk">
    // <endpoint entity="xmpp:leopard@atalk.org/atalk">
    // <status>connected</status>
    // <media id="audio"><type>audio</type><status>sendonly</status></media>
    // </endpoint></user>
    // <user entity="abc123@atalk.org/jitsi-15b488a">
    // <display-text>abc123@atalk.org</display-text>
    // <endpoint entity="abc123@atalk.org/jitsi-15b488a"><status>alerting</status></endpoint>
    // </user></users>
    // </conference-info></iq>
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.attribute(STATE_ATTR_NAME, state);
        xml.attribute(ENTITY_ATTR_NAME, entity);
        xml.attribute(VERSION_ATTR_NAME, version);

        // cmeng (2016/09/14): sid should not be null; Otherwise Smack Exception
        xml.attribute(SID_ATTR_NAME, sid);
        xml.setEmptyElement();
        return xml;
    }

    /**
     * Get entity.
     *
     * @return entity
     */
    public String getEntity()
    {
        return entity;
    }

    /**
     * Get session ID.
     *
     * @return session ID
     */
    public String getSid()
    {
        return sid;
    }

    /**
     * Get state.
     *
     * @return state
     */
    public StateType getState()
    {
        return state;
    }

    /**
     * Get version.
     *
     * @return version
     */
    public int getVersion()
    {
        return version;
    }

    /**
     * Set entity.
     *
     * @param entity entity
     */
    public void setEntity(String entity)
    {
        this.entity = entity;
    }

    /**
     * Set session ID.
     *
     * @param sid session ID to set
     */
    public void setSID(String sid)
    {
        this.sid = sid;
    }

    /**
     * Set state.
     *
     * @param state state to set
     */
    public void setState(StateType state)
    {
        this.state = state;
    }

    /**
     * Set version.
     *
     * @param version version
     */
    public void setVersion(int version)
    {
        this.version = version;
    }
}
