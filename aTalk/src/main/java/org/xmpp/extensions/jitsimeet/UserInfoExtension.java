/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.xmpp.extensions.jitsimeet;

import org.apache.commons.lang3.StringUtils;
import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Packet extension included in Jitsi-Meet MUC presence to signal extra
 * information about the participant.
 *
 * @author Pawel Domas
 */
public class UserInfoExtension extends AbstractExtensionElement
{
    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "userinfo";

    /**
     * Name space of start muted packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/userinfo";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the "robot" attribute which indicates whether or not given
     * user is a robot(SIP gateway, recorder component etc.).
     */
    public static final String ROBOT_ATTRIBUTE_NAME = "robot";

    /**
     * Creates an {@link UserInfoExtension} instance.
     */
    public UserInfoExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns <tt>true</tt> if the user is considered a "robot"(recorder
     * component, SIP gateway etc.), <tt>false</tt> if it's not and
     * <tt>null</tt> if the attribute value is not defined.
     */
    public Boolean isRobot()
    {
        String isRobotStr = getAttributeAsString(ROBOT_ATTRIBUTE_NAME);
        if (StringUtils.isNotEmpty(isRobotStr)) {
            return Boolean.parseBoolean(isRobotStr);
        }
        else {
            return null;
        }
    }

    /**
     * Sets new value for the "robot" attribute.
     *
     * @param isRobot <tt>true</tt> if the user is considered a robot or
     * <tt>false</tt> otherwise. Pass <tt>null</tt> to remove the attribute.
     * @see {@link #ROBOT_ATTRIBUTE_NAME}
     */
    public void setIsRobot(Boolean isRobot)
    {
        setAttribute(ROBOT_ATTRIBUTE_NAME, isRobot);
    }
}
