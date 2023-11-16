/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration.xml;

import org.atalk.impl.configuration.ConfigurationStore;
import org.atalk.util.xml.DOMElementWriter;
import org.atalk.util.xml.XMLException;
import org.atalk.util.xml.XMLUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

/**
 * Implements a {@code ConfigurationStore} which serializes property name-value associations
 * in XML format.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class XMLConfigurationStore implements ConfigurationStore
{
    /**
     * Name of the xml attribute containing property values
     */
    private static final String ATTRIBUTE_VALUE = "value";

    /**
     * Name of the xml attribute indicating that a property is to be resolved in the system properties
     */
    private static final String SYSTEM_ATTRIBUTE_NAME = "system";

    /**
     * The value of the Name of the xml attribute containing property values
     */
    private static final String SYSTEM_ATTRIBUTE_TRUE = "true";

    /**
     * The list of properties currently registered in the configuration service.
     */
    private Hashtable<String, Object> properties = new Hashtable<>();

    /**
     * Contains the properties that were initially loaded from the configuration file or (if the
     * properties have been modified and saved since initially loaded) those that were last
     * written to the file.We use the property so that we could determine which properties are
     * new and do not have a corresponding node in the XMLDocument object.
     */
    private Map<String, Object> fileExtractedProperties = new Hashtable<>();

    /**
     * The XML Document containing the configuration file this service loaded.
     */
    private Document propertiesDocument;

    /**
     * Returns a copy of the Map containing all configuration properties
     *
     * @return a Map clone of the current configuration property set.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> cloneProperties()
    {
        return (Map<String, Object>) properties.clone();
    }

    /**
     * Creates a new runtime XML document which is to contain the properties managed by this <code>ConfigurationStore</code>
     * .
     *
     * @return a new runtime XML <code>Document</code> which is to contain the properties managed by
     * this <code>ConfigurationStore</code>
     */
    private Document createPropertiesDocument()
    {
        if (propertiesDocument == null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException ex) {
                Timber.e(ex, "Failed to create a DocumentBuilder");
                return null;
            }
            propertiesDocument = builder.newDocument();
            propertiesDocument.appendChild(propertiesDocument.createElement("sip-communicator"));
        }
        return propertiesDocument;
    }

    /**
     * Implements {@link ConfigurationStore#getProperty(String)}. Gets the value in this
     * {@code ConfigurationStore} of a property with a specific name.
     *
     * @param propertyName the name of the property to get the value of
     * @return the value in this <code>ConfigurationStore</code> of the property with the specified
     * name; <code>null</code> if the property with the specified name does not have an association
     * with a value in this <code>ConfigurationStore</code>
     * @see ConfigurationStore#getProperty(String)
     */
    public Object getProperty(String propertyName)
    {
        Object value = properties.get(propertyName);

        // if this is a property reference make sure we return the referenced
        // value and not the reference itself
        if (value instanceof PropertyReference)
            return ((PropertyReference) value).getValue();
        else
            return value;
    }

    /**
     * Implements {ConfigurationStore#getPropertyNames(String)}. Gets the names of the properties
     * which have values associated in this <code>ConfigurationStore</code>.
     *
     * @return an array of <code>String</code>s which specify the names of the properties that have
     * values associated in this <code>ConfigurationStore</code>; an empty array if this instance
     * contains no property values
     * @see ConfigurationStore#getPropertyNames(String)
     */
    public String[] getPropertyNames(String name)
    {
        Set<String> propertyNames = properties.keySet();
        return propertyNames.toArray(new String[0]);
    }

    /**
     * Implements {ConfigurationStore#isSystemProperty(String)}. Determines whether a specific
     * name stands for a system property.
     *
     * @param propertyName the name of a property which is to be determined whether it is a system property
     * @return <code>true</code> if the specified name stands for a system property; <code>false</code>, otherwise
     * @see ConfigurationStore#isSystemProperty(String)
     */
    public boolean isSystemProperty(String propertyName)
    {
        return properties.get(propertyName) instanceof PropertyReference;
    }

    /**
     * Loads the contents of the specified configuration file into the local properties object.
     *
     * @param file a reference to the configuration file to load.
     * @return a hashTable containing all properties extracted from the specified file.
     * @throws IOException if the specified file does not exist
     * @throws XMLException if there is a problem with the file syntax.
     */
    private Map<String, Object> loadConfiguration(File file)
            throws IOException, XMLException
    {
        try {
            DocumentBuilderFactory factory = XMLUtils.newDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Map<String, Object> props = new Hashtable<>();

            // if the file is empty (or contains only sth insignificant) ignore it and create a new document.
            if (file.length() < "<sip-communicator>".length() * 2)
                propertiesDocument = createPropertiesDocument();
            else
                propertiesDocument = builder.parse(file);

            Node root = propertiesDocument.getFirstChild();

            Node currentNode;
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                currentNode = children.item(i);

                if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                    String propertyName = DOMElementWriter.decodeName(currentNode.getNodeName());

                    loadNode(currentNode, propertyName, props);
                }
            }

            return props;
        } catch (SAXException ex) {
            Timber.e(ex, "Error parsing configuration file");
            throw new XMLException(ex.getMessage(), ex);
        } catch (ParserConfigurationException ex) {
            // it is not highly probable that this might happen - so lets just log it.
            Timber.e(ex, "Error finding configuration for default parsers");
            return new Hashtable<String, Object>();
        }
    }

    /**
     * Loads the contents of the specified node and its children into the local properties. Any
     * nodes marked as "system" will also be resolved in the system properties.
     *
     * @param node the root node that we should load together with its children
     * @param propertyName a String containing the prefix describing the route to the specified node including
     * its one name
     * @param props the dictionary object where all properties extracted from this node and its children
     * should be recorded.
     */
    private void loadNode(Node node, String propertyName, Map<String, Object> props)
    {
        Node currentNode;
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            currentNode = children.item(i);

            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                String newProp = propertyName + "."
                        + DOMElementWriter.decodeName(currentNode.getNodeName());
                String value = XMLConfUtils.getAttribute(currentNode, ATTRIBUTE_VALUE);

                // the value attr is present we must handle the desired property
                if (value != null) {
                    String propertyType = XMLConfUtils.getAttribute(currentNode,
                            SYSTEM_ATTRIBUTE_NAME);

                    // if the property is marked as "system", we should resolve it against the
                    // system properties and only store a reference locally. this is normally
                    // done for properties that are supposed to configure underlying libraries.
                    if ((propertyType != null) && propertyType.equals(SYSTEM_ATTRIBUTE_TRUE)) {
                        props.put(newProp, new PropertyReference(newProp));
                        System.setProperty(newProp, value);
                    }
                    else
                        props.put(newProp, value);
                }

                // load child nodes
                loadNode(currentNode, newProp, props);
            }
        }
    }

    /**
     * Creates new entries in the XML <code>doc</code> for every element in the <code>newProperties</code> table.
     *
     * @param doc the XML <code>Document</code> where the new entries should be created
     * @param newProperties the table containing the properties that are to be introduced in the document.
     */
    private void processNewProperties(Document doc, Map<String, Object> newProperties)
    {
        for (Map.Entry<String, Object> entry : newProperties.entrySet()) {
            Object value = entry.getValue();
            boolean system;

            if (system = (value instanceof PropertyReference))
                value = ((PropertyReference) value).getValue();
            processNewProperty(doc, entry.getKey(), value.toString(), system);
        }
    }

    /**
     * Creates an entry in the XML <code>doc</code> for the specified key value pair.
     *
     * @param doc the XML <code>document</code> to update.
     * @param key the value of the <code>name</code> attribute for the new entry
     * @param value the value of the <code>value</code> attribute for the new entry
     * @param isSystem specifies whether this is a system property (system attribute will be set to true).
     */
    private void processNewProperty(Document doc, String key, String value, boolean isSystem)
    {
        StringTokenizer tokenizer = new StringTokenizer(key, ".");
        String[] toks = new String[tokenizer.countTokens()];
        int i = 0;

        while (tokenizer.hasMoreTokens()) {
            toks[i++] = DOMElementWriter.encodeName(tokenizer.nextToken());
        }

        String nodeName = toks[toks.length - 1];
        Element parent = XMLConfUtils.createLastPathComponent(doc, toks, toks.length - 1);
        Element newNode = XMLConfUtils.findChild(parent, nodeName);

        if (newNode == null) {
            newNode = doc.createElement(nodeName);
            parent.appendChild(newNode);
        }
        newNode.setAttribute("value", value);

        if (isSystem)
            newNode.setAttribute(SYSTEM_ATTRIBUTE_NAME, SYSTEM_ATTRIBUTE_TRUE);
    }

    /**
     * Implements {@link ConfigurationStore#reloadConfiguration(File)}. Removes all property
     * name-value associations currently present in this <code>ConfigurationStore</code> and
     * de-serializes new property name-value associations from a specific <code>File</code> which
     * presumably is in the format represented by this instance.
     *
     * @param file the <code>File</code> to be read and to deserialize new property name-value associations
     * from into this instance
     * @throws IOException if there is an input error while reading from the specified <code>file</code>
     * @throws XMLException if parsing the contents of the specified <code>file</code> fails
     * @see ConfigurationStore#reloadConfiguration(File)
     */
    public void reloadConfiguration(File file)
            throws IOException, XMLException
    {
        properties = new Hashtable<>();
        fileExtractedProperties = loadConfiguration(file);
        properties.putAll(fileExtractedProperties);
    }

    /**
     * Implements {@link ConfigurationStore#removeProperty(String)}. Removes the value association
     * in this <code>ConfigurationStore</code> of the property with a specific name. If the property
     * with the specified name is not associated with a value in this <code>ConfigurationStore</code>, does nothing.
     *
     * @param propertyName the name of the property which is to have its value association in this
     * <code>ConfigurationStore</code> removed
     * @see ConfigurationStore#removeProperty(String)
     */
    public void removeProperty(String propertyName)
    {
        properties.remove(propertyName);

        fileExtractedProperties.remove(propertyName);
    }

    /**
     * Implements {@link ConfigurationStore#setNonSystemProperty(String, Object)}. Sets the value
     * of a non-system property with a specific name to a specific value in this
     * <code>ConfigurationStore</code>.
     *
     * @param propertyName the name of the non-system property to be set to the specified value in this
     * <code>ConfigurationStore</code>
     * @param property the value to be assigned to the non-system property with the specified name in this
     * <code>ConfigurationStore</code>
     * @see ConfigurationStore#setNonSystemProperty(String, Object)
     */
    public void setNonSystemProperty(String propertyName, Object property)
    {
        properties.put(propertyName, property);
    }

    /**
     * Implements {@link ConfigurationStore#setSystemProperty(String)}. Sets a property with a
     * specific name to be considered a system property by the <code>ConfigurationStore</code>.
     *
     * @param propertyName the name of the property to be set as a system property in this <code>ConfigurationStore</code>
     * @see ConfigurationStore#setSystemProperty(String)
     */
    public void setSystemProperty(String propertyName)
    {
        setNonSystemProperty(propertyName, new PropertyReference(propertyName));
    }

    /**
     * Implements {@link ConfigurationStore#storeConfiguration(OutputStream)}. Stores/serializes
     * the property name-value associations currently present in this <code>ConfigurationStore</code>
     * into a specific <code>OutputStream</code> in the format represented by this instance.
     *
     * @param out the <code>OutputStream</code> to receive the serialized form of the property name-value
     * associations currently present in this <code>ConfigurationStore</code>
     * @see ConfigurationStore#storeConfiguration(OutputStream)
     */
    public void storeConfiguration(OutputStream out)
    {
        // resolve the properties that were initially in the file - back to the document.
        if (propertiesDocument == null)
            propertiesDocument = createPropertiesDocument();

        Node root = propertiesDocument.getFirstChild();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node currentNode = children.item(i);

            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                String propertyName = DOMElementWriter.decodeName(currentNode.getNodeName());

                updateNode(currentNode, propertyName, properties);
            }
        }

        // create in the document the properties that were added by other bundles after the
        // initial property load.
        Map<String, Object> newlyAddedProperties = cloneProperties();

        // remove those that were originally there;
        for (String propName : fileExtractedProperties.keySet())
            newlyAddedProperties.remove(propName);

        this.processNewProperties(propertiesDocument, newlyAddedProperties);

        XMLUtils.indentedWriteXML(propertiesDocument, out);
    }

    /**
     * Updates the value of the specified node and its children to reflect those in the properties
     * file. Nodes marked as "system" will be updated from the specified properties object and
     * not from the system properties since if any intentional change (through a configuration
     * form) has occurred it will have been made there.
     *
     * @param node the root node that we should update together with its children
     * @param propertyName a String containing the prefix describing the dot-separated route to the specified
     * node including its one name
     * @param props the dictionary object where the up to date values of the node should be queried.
     */
    private void updateNode(Node node, String propertyName, Map<String, Object> props)
    {
        Node currentNode;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            currentNode = children.item(i);

            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                String newProp = propertyName + "."
                        + DOMElementWriter.decodeName(currentNode.getNodeName());
                Attr attr = ((Element) currentNode).getAttributeNode(ATTRIBUTE_VALUE);

                if (attr != null) {
                    // update the corresponding node
                    Object value = props.get(newProp);

                    if (value == null) {
                        node.removeChild(currentNode);
                        continue;
                    }

                    boolean isSystem = (value instanceof PropertyReference);
                    String prop = isSystem ? ((PropertyReference) value).getValue().toString() : value.toString();

                    attr.setNodeValue(prop);

                    // in case the property has changed to system since the last load - update the
                    // conf file accordingly.
                    if (isSystem)
                        ((Element) currentNode).setAttribute(SYSTEM_ATTRIBUTE_NAME, SYSTEM_ATTRIBUTE_TRUE);
                    else
                        ((Element) currentNode).removeAttribute(SYSTEM_ATTRIBUTE_NAME);

                }

                // update child nodes
                updateNode(currentNode, newProp, props);
            }
        }
    }

    /**
     * We use property references when we'd like to store system properties. Simply storing System
     * properties in our properties Map would not be enough since it will lead to mismatching
     * values for the same property in the System property set and in our local set of properties.
     * Storing them only in the System property  set OTOH is a bit clumsy since it obliges bundles
     * to use to different configuration property sources. For that reason, every time we get
     * handed a property labeled as System, instead of storing its actual value in the
     * <p>
     * local property set we store a PropertyReference instance that will retrieve it from the
     * system properties when  necessary.
     */
    private static class PropertyReference
    {
        /**
         * The name of the system property represented by this instance.
         */
        private final String propertyName;

        /**
         * Initializes a new <code>PropertyReference</code> instance which is to represent a system
         * property with a specific name.
         *
         * @param propertyName the name of the system property to be represented by the new instance
         */
        public PropertyReference(String propertyName)
        {
            this.propertyName = propertyName;
        }

        /**
         * Return the actual value of the property as recorded in the System properties.
         *
         * @return the valued of the property as recorded in the System props.
         */
        public Object getValue()
        {
            return System.getProperty(propertyName);
        }
    }
}
