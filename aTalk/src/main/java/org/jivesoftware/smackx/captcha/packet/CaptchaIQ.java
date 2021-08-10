/**
 * Copyright 2003-2007 Jive Software.
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

package org.jivesoftware.smackx.captcha.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smackx.xdata.packet.DataForm;

/**
 * XEP-0158: CAPTCHA Forms IQ Implementation with DataForm
 *
 * @author Eng Chong Meng
 */
public class CaptchaIQ extends IQ
{
    public static final String ELEMENT = CaptchaExtension.ELEMENT;
    public static final String NAMESPACE = CaptchaExtension.NAMESPACE;

    private final DataForm mDataForm;

    public CaptchaIQ(DataForm dataForm)
    {
        super(ELEMENT, NAMESPACE);
        this.mDataForm = dataForm;
    }

    /**
     * Returns the DataForm in the registration.
     *
     * @return the DataForm in the registration.
     */
    public DataForm getDataForm()
    {
        return mDataForm;
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.rightAngleBracket();
        if (mDataForm != null) {
            xml.append(mDataForm.toXML(XmlEnvironment.EMPTY));
        }
        return xml;
    }
}
