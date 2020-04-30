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

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Custom packets extension indicates error returned by reservation system.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ReservationErrorExtension extends AbstractExtensionElement
{
    /**
     * XML namespace of this packets extension.
     */
    public static final String NAMESPACE = ConferenceIq.NAMESPACE;

    /**
     * XML element name of this packets extension.
     */
    public static final String ELEMENT = "reservation-error";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of XML attribute that holds error code returned by
     * the reservation system.
     */
    public static final String ERROR_CODE_ATTR_NAME = "error-code";

    /**
     * Creates new instance of <tt>ReservationErrorExtensionElement</tt>.
     */
    public ReservationErrorExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Creates new instance of <tt>ReservationErrorExtensionElement</tt>.
     */
    public ReservationErrorExtension(int code)
    {
        super(ELEMENT, NAMESPACE);
        setErrorCode(code);
    }

    /**
     * Sets new value for error code attribute.
     *
     * @param code error code value to set or <tt>-1</tt> to remove
     * the attribute.
     */
    public void setErrorCode(int code)
    {
        if (code == -1) {
            setAttribute(ERROR_CODE_ATTR_NAME, null);
        }
        else {
            setAttribute(ERROR_CODE_ATTR_NAME, code);
        }
    }

    /**
     * Returns error code attribute value or <tt>-1</tt> if is unspecified.
     */
    public int getErrorCode()
    {
        return getAttributeAsInt(ERROR_CODE_ATTR_NAME, -1);
    }
}
