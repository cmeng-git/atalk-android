/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic implementation of <code>ExtensionElement</code>. The purpose of this class is quite similar
 * to that of smack's {@link ExtensionElement} with the main difference being that this one is
 * meant primarily for extension rather than using as a fallback for unknown elements. We let for
 * example our descendants handle child elements and we automate attribute handling instead.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractExtensionElement implements ExtensionElement
{
    /**
     * The element of this stanza extension. Should remain <code>null</code> if there's no namespace
     * associated with this element.
     */
    private final String element;

    /**
     * The name space of this stanza extension. Should remain <code>null</code> if there's no namespace
     * associated with this element.
     */
    private String namespace;

    /**
     * A map of all attributes that this extension is currently using.
     */
    protected final Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * The text content of this stanza extension, if any.
     */
    private String textContent;

    /**
     * A list of extensions registered with this element.
     */
    private final List<ExtensionElement> childExtensions = new ArrayList<>();

    /**
     * Creates an {@link AbstractExtensionElement} instance for the specified <code>namespace</code> and <code>elementName</code> .
     *
     * @param namespace the XML namespace for this element.
     * @param element the name of the element
     */
    protected AbstractExtensionElement(String element, String namespace)
    {
        this.element = element;
        this.namespace = namespace;
    }

    /**
     * Returns the name of the <code>encryption</code> element.
     *
     * @return the name of the <code>encryption</code> element.
     */
    @Override
    public String getElementName()
    {
        return element;
    }

    /**
     * Returns the XML namespace for this element or <code>null</code> if the element does not live in a namespace of its own.
     *
     * @return the XML namespace for this element or <code>null</code> if the element does not live in a namespace of its own.
     */
    @Override
    public String getNamespace()
    {
        return namespace;
    }

    /**
     * Returns the text content of this extension or <code>null</code> if no text content has been specified so far.
     *
     * @return the text content of this extension or <code>null</code> if no text content has been specified so far.
     */
    public String getText()
    {
        return textContent;
    }

    /**
     * Returns the attribute with the specified <code>name</code> from the list of attributes registered
     * with this stanza extension.
     *
     * @param attribute the name of the attribute that we'd like to retrieve.
     * @return the value of the specified <code>attribute</code> or <code>null</code> if no such attribute
     * is currently registered with this extension.
     */
    public Object getAttribute(String attribute)
    {
        synchronized (attributes) {
            return attributes.get(attribute);
        }
    }

    /**
     * Returns the string value of the attribute with the specified <code>name</code>.
     *
     * @param attribute the name of the attribute that we'd like to retrieve.
     * @return the String value of the specified <code>attribute</code> or <code>null</code> if no such
     * attribute is currently registered with this extension.
     */
    public String getAttributeAsString(String attribute)
    {
        synchronized (attributes) {
            Object attributeVal = attributes.get(attribute);
            return attributeVal == null ? null : attributeVal.toString();
        }
    }

    /**
     * Returns the <code>int</code> value of the attribute with the specified <code>name</code>.
     *
     * @param attribute the name of the attribute that we'd like to retrieve.
     * @return the <code>int</code> value of the specified <code>attribute</code> or <code>-1</code> if no such
     * attribute is currently registered with this extension.
     */
    public int getAttributeAsInt(String attribute)
    {
        return getAttributeAsInt(attribute, -1);
    }

    /**
     * Returns the <code>int</code> value of the attribute with the specified <code>name</code>.
     *
     * @param attribute the name of the attribute that we'd like to retrieve
     * @param defaultValue the <code>int</code> to be returned as the value of the specified attribute if no such
     * attribute is currently registered with this extension
     * @return the <code>int</code> value of the specified <code>attribute</code> or <code>defaultValue</code>
     * if no such attribute is currently registered with this extension
     */
    public int getAttributeAsInt(String attribute, int defaultValue)
    {
        synchronized (attributes) {
            String value = getAttributeAsString(attribute);
            return (value == null) ? defaultValue : Integer.parseInt(value);
        }
    }

    /**
     * Tries to parse the value of the specified <code>attribute</code> as an <code>URI</code> and returns it.
     *
     * @param attribute the name of the attribute that we'd like to retrieve.
     * @return the <code>URI</code> value of the specified <code>attribute</code> or <code>null</code> if no
     * such attribute is currently registered with this extension.
     * @throws IllegalArgumentException if <code>attribute</code> is not a valid {@link URI}
     */
    public URI getAttributeAsURI(String attribute)
            throws IllegalArgumentException
    {
        synchronized (attributes) {
            String attributeVal = getAttributeAsString(attribute);

            if (attributeVal == null)
                return null;

            try {
                return new URI(attributeVal);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Gets the names of the attributes which currently have associated values in this extension.
     *
     * @return the names of the attributes which currently have associated values in this extension
     */
    public List<String> getAttributeNames()
    {
        synchronized (attributes) {
            return new ArrayList<>(attributes.keySet());
        }
    }

    /**
     * Gets the first extension present of the given type
     *
     * @param type the type of extension to get
     * @return the first instance of an extension of type T we find, or null if there is none
     */
    public <T extends ExtensionElement> T getChildExtension(Class<T> type)
    {
        List<T> childExts = getChildExtensionsOfType(type);
        if (!childExts.isEmpty()) {
            return childExts.get(0);
        }
        else {
            return null;
        }
    }

    /**
     * Returns all sub-elements for this <code>AbstractExtensionElement</code> or <code>null</code> if there aren't any.
     *
     * Overriding extensions may need to override this method if they would like to have anything
     * more elaborate than just a list of extensions.
     *
     * @return the {@link List} of elements that this stanza extension contains.
     */
    public List<? extends ExtensionElement> getChildExtensions()
    {
        return childExtensions;
    }

    /**
     * Returns this stanza's first direct child extension that matches the specified <code>type</code>.
     *
     * @param <T> the specific type of <code>ExtensionElement</code> to be returned
     * @param type the <code>Class</code> of the extension we are looking for.
     * @return this stanza's first direct child extension that matches specified <code>type</code> or
     * <code>null</code> if no such child extension was found.
     */
    public <T extends ExtensionElement> T getFirstChildOfType(Class<T> type)
    {
        List<? extends ExtensionElement> childExtensions = getChildExtensions();
        synchronized (childExtensions) {
            for (ExtensionElement extension : childExtensions) {
                if (type.isInstance(extension)) {
                    @SuppressWarnings("unchecked")
                    T extensionAsType = (T) extension;
                    return extensionAsType;
                }
            }
        }
        return null;
    }

    /**
     * Returns this packet's direct child extensions that match the specified <code>type</code>.
     *
     * @param <T> the specific <code>ExtensionElement</code> type of child extensions to be returned
     * @param type the <code>Class</code> of the extension we are looking for.
     * @return a (possibly empty) list containing all of this packet's direct child extensions that
     * match the specified <code>type</code>
     */
    public <T extends ExtensionElement> List<T> getChildExtensionsOfType(Class<T> type)
    {
        List<? extends ExtensionElement> childExtensions = getChildExtensions();
        List<T> result = new ArrayList<>();

        if (childExtensions == null)
            return result;

        synchronized (childExtensions) {
            for (ExtensionElement extension : childExtensions) {
                if (type.isInstance(extension)) {
                    @SuppressWarnings("unchecked")
                    T extensionAsType = (T) extension;
                    result.add(extensionAsType);
                }
            }
        }
        return result;
    }

    /**
     * Clones the attributes, namespace and text of a specific <code>AbstractExtensionElement</code> into
     * a new <code>AbstractExtensionElement</code> instance of the same run-time type.
     *
     * @param src the <code>AbstractExtensionElement</code> to be cloned
     * @return a new <code>AbstractExtensionElement</code> instance of the run-time type of the specified
     * <code>src</code> which has the same attributes, namespace and text
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractExtensionElement> T clone(T src)
    {
        T dst;
        try {
            dst = (T) src.getClass().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // attributes
        for (String name : src.getAttributeNames()) {
            dst.setAttribute(name, src.getAttribute(name));
        }
        // namespace
        dst.setNamespace(src.getNamespace());
        // text
        dst.setText(src.getText());
        return dst;
    }

    /**
     * Returns an XML representation of this extension.
     *
     * @return an XML representation of this extension.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }

        // add child elements if any
        List<? extends ExtensionElement> childElements = getChildExtensions();
        String text = getText();
        XmlStringBuilder childBuilder = new XmlStringBuilder();

        if (childElements.isEmpty() && childBuilder.length() == 0) {
            if (StringUtils.isEmpty(text)) {
                return xml.closeEmptyElement();
            }
            else
                xml.rightAngleBracket();
        }
        else {
            synchronized (childElements) {
                if (childElements.isEmpty() && (childBuilder.length() == 0)
                        && (StringUtils.isEmpty(text))) {
                    return xml.closeEmptyElement();
                }
                else {
                    xml.rightAngleBracket();
                    for (ExtensionElement packExt : childElements)
                        xml.optAppend(packExt);
                    xml.append(childBuilder);
                }
            }
        }
        xml.optEscape(text);

        xml.closeElement(this);
        return xml;
    }

    /**
     * Set the XML namespace for this element.
     *
     * @param namespace the XML namespace for this element.
     */
    public void setNamespace(String namespace)
    {
        this.namespace = namespace;
    }

    /**
     * Specifies the text content of this extension.
     *
     * @param text the text content of this extension.
     */
    public void setText(String text)
    {
        this.textContent = text;
    }

    /**
     * Sets the value of the attribute named <code>name</code> to <code>value</code>.
     *
     * @param name the name of the attribute that we are setting.
     * @param value an {@link Object} whose <code>toString()</code> method returns the XML value of the
     * attribute we are setting or <code>null</code> if we'd like to remove the attribute with the specified <code>name</code>.
     */
    public void setAttribute(String name, Object value)
    {
        synchronized (attributes) {
            if (value != null) {
                this.attributes.put(name, value);
            }
            else {
                this.attributes.remove(name);
            }
        }
    }

    /**
     * Removes the attribute with the specified <code>name</code> from the list of attributes registered
     * with this stanza extension.
     *
     * @param name the name of the attribute that we are removing.
     */
    public void removeAttribute(String name)
    {
        synchronized (attributes) {
            attributes.remove(name);
        }
    }

    /**
     * Adds the specified <code>childExtension</code> to the list of extensions registered with this packet.
     *
     * Overriding extensions may need to override this method if they would like to have anything
     * more elaborate than just a list of extensions (e.g. casting separate instances to more specific.
     *
     * @param childExtension the extension we'd like to add here.
     */
    public void addChildExtension(ExtensionElement childExtension)
    {
        childExtensions.add(childExtension);
    }

    /**
     * Add the given extension to the list of child extensions, but, if there already exists
     * any child extensions of this type, remove them first.
     *
     * @param childExtension the extension to add
     */
    public void setChildExtension(ExtensionElement childExtension)
    {
        // required API-24
        // getChildExtensionsOfType(childExtension.getClass()).forEach(this::removeChildExtension);
        List<? extends ExtensionElement> extensionElements = getChildExtensionsOfType(childExtension.getClass());
        for (ExtensionElement xe : extensionElements) {
            removeChildExtension(xe);
        }
        childExtensions.add(childExtension);
    }

    /**
     * Removes all occurrences of an extension element from the list of child extensions.
     *
     * @param childExtension the child extension to remove.
     * @return {@code true} if any extensions were removed, and {@code false} otherwise.
     */
    public boolean removeChildExtension(ExtensionElement childExtension)
    {
        boolean removed = false;
        if (childExtension != null) {
            while (childExtensions.remove(childExtension)) {
                removed = true;
            }
        }
        return removed;
    }
}
