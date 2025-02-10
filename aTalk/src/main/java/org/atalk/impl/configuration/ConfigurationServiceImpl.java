/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.configuration.xml.XMLConfigurationStore;
import org.atalk.service.configuration.ConfigPropertyVetoException;
import org.atalk.service.configuration.ConfigVetoableChangeListener;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.FailSafeTransaction;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.util.OSUtils;
import org.atalk.util.PasswordUtil;
import org.atalk.util.xml.XMLException;
import org.jivesoftware.smack.util.StringUtils;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * A straightforward implementation of the <code>ConfigurationService</code> using an XML or a
 * .properties file for storing properties. Currently only <code>String</code> properties are
 * meaningfully saved (we should probably consider how and whether we should take care of the rest).
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ConfigurationServiceImpl implements ConfigurationService
{
    /**
     * The name of the <code>ConfigurationStore</code> class to be used as the default when no
     * specific <code>ConfigurationStore</code> class is determined as necessary.
     */
    private static final String DEFAULT_CONFIGURATION_STORE_CLASS_NAME
            = "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore";

    /**
     * Name of the system file name property.
     */
    private static final String SYS_PROPS_FILE_NAME_PROPERTY = "net.java.sip.communicator.SYS_PROPS_FILE_NAME";

    /**
     * Name of the file containing default properties.
     */
    private static final String DEFAULT_PROPS_FILE_NAME = "atalk-defaults.properties";

    /**
     * Name of the file containing overrides (possibly set by the developer) for any of the default properties.
     */
    private static final String DEFAULT_OVERRIDES_PROPS_FILE_NAME = "atalk-default-overrides.properties";

    /**
     * Specify names of command line arguments which are password, so that their values will be
     * masked when 'sun.java.command' is printed to the logs. Separate each name with a comma.
     */
    private static String PASSWORD_CMD_LINE_ARGS;

    /**
     * Set this filed value to a regular expression which will be used to select system
     * properties mKeys whose values should be masked when printed out to the logs.
     */
    private static String PASSWORD_SYS_PROPS;

    /**
     * A reference to the currently used configuration file.
     */
    private File configurationFile = null;

    /**
     * A set of immutable properties deployed with the application during install time. The
     * properties in this file will be impossible to override and attempts to do so will simply be ignored.
     *
     * @see #defaultProperties
     */
    private final Map<String, String> immutableDefaultProperties = new HashMap<>();

    /**
     * A set of properties deployed with the application during install time. Contrary to the
     * properties in {@link #immutableDefaultProperties} the ones in this map can be overridden
     * with call to the <code>setProperty()</code> methods. Still, re-setting one of these properties
     * to <code>null</code> would cause for its initial value to be restored.
     */
    private final Map<String, String> defaultProperties = new HashMap<>();

    /**
     * Our event dispatcher.
     */
    private final ChangeEventDispatcher changeEventDispatcher = new ChangeEventDispatcher(this);

    /**
     * A (cached) reference to a <code>FileAccessService</code> implementation used by this
     * <code>ConfigurationService</code> implementation.
     */
    private FileAccessService faService;

    /**
     * The indicator which determines whether this instance has assigned a value to
     * {@link #faService}. Introduced in order to avoid multiple attempts to query for a
     * <code>FileAccessService</code> implementation while still delaying the initial query.
     */
    private boolean faServiceIsAssigned = false;

    /**
     * The {@code ConfigurationStore} implementation which contains the property name-value
     * associations of this {@code ConfigurationService} and performs their actual storing in
     * {@code configurationFile}.
     */
    private ConfigurationStore store;

    public ConfigurationServiceImpl()
    {
        /*
         * XXX We explicitly delay the query for the FileAccessService implementation because
         * FileAccessServiceImpl looks for properties set by methods of ConfigurationServiceImpl
         * and we want to make sure that we have given the  chance to this ConfigurationServiceImpl
         * to set these properties before FileAccessServiceImpl looks for them.
         */

        try {
            debugPrintSystemProperties();
            preloadSystemPropertyFiles();
            loadDefaultProperties();
            reloadConfiguration();
        } catch (IOException ex) {
            Timber.e(ex, "Failed to load the configuration file");
        }
    }

    /**
     * Sets the property with the specified name to the specified value. Calling this method would
     * first trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners.
     * In case no complaints (PropertyVetoException) have been received, the property  will be
     * actually changed and a PropertyChangeEvent will be dispatched.
     *
     * @param propertyName the name of the property
     * @param property the object that we'd like to be come the new value of the property.
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Override
    public void setProperty(String propertyName, Object property)
            throws ConfigPropertyVetoException
    {
        setProperty(propertyName, property, false);
    }

    /**
     * Sets the property of the specified name to the specified property. Calling this method would
     * first trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners.
     * In case no complaints (PropertyVetoException) have been received, the property will be
     * actually changed and a PropertyChangeEvent will be dispatched. This method also allows the
     * caller to specify whether or not the specified property is a system one.
     *
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @param isSystem specifies whether or not the property being is a System property and should be
     * resolved against the system property set. If the property has previously been
     * specified as system then this value is internally forced to true.
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Override
    public void setProperty(String propertyName, Object property, boolean isSystem)
            throws ConfigPropertyVetoException
    {
        Object oldValue = getProperty(propertyName);

        // first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
            changeEventDispatcher.fireVetoableChange(propertyName, oldValue, property);

        // no exception was thrown - lets change the property and fire a change event
        // cmeng - define the location where to store the change properties - can be removed??? file system not use
        doSetProperty(propertyName, property, isSystem);
        try {
            storeConfiguration();
        } catch (IOException ex) {
            Timber.e("Failed to store configuration after a property change");
        }

        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
            changeEventDispatcher.firePropertyChange(propertyName, oldValue, property);
    }

    /*
     * Implements ConfigurationService#setProperties(Map). Optimizes the setting of properties by
     * performing a single saving of the property store to the configuration file which is known
     * to be slow because it involves converting the whole store to a string representation
     * and writing a file to the disk.
     *
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Override
    public void setProperties(Map<String, Object> properties)
            throws ConfigPropertyVetoException
    {
        // first check whether the changes are ok with everyone
        Map<String, Object> oldValues = new HashMap<>(properties.size());
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            String propertyName = property.getKey();
            Object oldValue = getProperty(propertyName);

            oldValues.put(propertyName, oldValue);
            if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
                changeEventDispatcher.fireVetoableChange(propertyName, oldValue, property.getValue());
        }

        for (Map.Entry<String, Object> property : properties.entrySet())
            doSetProperty(property.getKey(), property.getValue(), false);

        try {
            storeConfiguration();
        } catch (IOException ex) {
            Timber.e("Failed to store configuration after property changes");
        }

        for (Map.Entry<String, Object> property : properties.entrySet()) {
            String propertyName = property.getKey();

            if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
                changeEventDispatcher.firePropertyChange(propertyName, oldValues.get(propertyName), property.getValue());
        }
    }

    /**
     * Performs the actual setting of a property with a specific name to a specific new value
     * without asking {@code VetoableChangeListener}, storing into the configuration file
     * and notifying {@code PropertyChangeListener}s.
     *
     * @param propertyName the name of the property which is to be set to a specific value
     * @param property the value to be assigned to the property with the specified name
     * @param isSystem <code>true</code> if the property with the specified name is to be set as a system
     * property; <code>false</code>, otherwise
     */
    private void doSetProperty(String propertyName, Object property, boolean isSystem)
    {
        // once set system, a property remains system even if the user specified something else
        if (isSystemProperty(propertyName))
            isSystem = true;

        // ignore requests to override immutable properties:
        if (immutableDefaultProperties.containsKey(propertyName))
            return;

        if (property == null) {
            store.removeProperty(propertyName);

            if (isSystem) {
                // we can't remove or null set a sys property so let's "empty" it.
                System.setProperty(propertyName, "");
            }
        }
        else if (isSystem) {
            // in case this is a system property, we must only store it in the System property set
            // and keep only a ref locally.
            System.setProperty(propertyName, property.toString());
            store.setSystemProperty(propertyName);
        }
        else {
            store.setNonSystemProperty(propertyName, property);
        }
    }

    /**
     * Removes the property with the specified name. Calling this method would first trigger a
     * PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In case no
     * complaints (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched. All properties with prefix
     * propertyName will also be removed.
     *
     * @param propertyName the name of the property to change.
     */
    @Override
    public int removeProperty(String propertyName)
    {
        List<String> childPropertyNames = getPropertyNamesByPrefix(propertyName, false);
        int size = childPropertyNames.size() + 1;
        // remove all child properties
        for (String pName : childPropertyNames) {
            removePropertyInternal(pName);
        }
        // remove the parent properties if any
        removePropertyInternal(propertyName);

        try {
            storeConfiguration();
        } catch (IOException ex) {
            Timber.e("Failed to store configuration after a property change");
        }
        return size;
    }

    /**
     * Removes the property with the specified name. Calling this method would first trigger a
     * PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In case no
     * complaints (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched. All properties with prefix
     * propertyName will also be removed.
     *
     * Does not store anything.
     *
     * @param propertyName the name of the property to change.
     */
    private void removePropertyInternal(String propertyName)
    {
        Object oldValue = getProperty(propertyName);
        // first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
            changeEventDispatcher.fireVetoableChange(propertyName, oldValue, null);

        // no exception was thrown - lets change the property and fire a change event
        Timber.log(TimberLog.FINER, "Will remove prop: %", propertyName);

        store.removeProperty(propertyName);
        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
            changeEventDispatcher.firePropertyChange(propertyName, oldValue, null);
    }

    /**
     * Returns the value of the property with the specified name or null if no such property exists.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the value of the property with the specified name.
     */
    @Override
    public Object getProperty(String propertyName)
    {
        Object result = immutableDefaultProperties.get(propertyName);
        if (result != null)
            return result;

        result = store.getProperty(propertyName);
        if (result == null)
            result = defaultProperties.get(propertyName);

        // cmeng - will enable for more testing later
//		if (result == null)
//			Timber.w("Found empty or null property value for: %s", propertyName);

        return result;
    }

    /**
     * Returns a <code>java.util.List</code> of <code>String</code>s containing all property names.
     *
     * @return a <code>java.util.List</code>containing all property names
     */
    @Override
    public List<String> getAllPropertyNames(String name)
    {
        List<String> resultKeySet = new LinkedList<>();
        Collections.addAll(resultKeySet, store.getPropertyNames(name));
        return resultKeySet;
    }

    /**
     * Returns a <code>java.util.List</code> of <code>String</code>s containing the all property names
     * that have the specified prefix. Depending on the value of the <code>exactPrefixMatch</code>
     * parameter the method will (when false) or will not (when exactPrefixMatch is true) include
     * property names that have prefixes longer than the specified <code>prefix</code> param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br>
     * <code>
     * net.java.sip.communicator.PROP1=value1<br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix
     * includes the requested prefix string.
     *
     * In addition to stored properties this method will also search the default mutable and
     * immutable properties.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have
     * a prefix that is an exact match of the the <code>prefix</code> param or whether properties with
     * prefixes that contain it but are longer than it are also accepted.
     * @return a <code>java.util.List</code>containing all property name String-s matching the
     * specified conditions.
     */
    @Override
    public List<String> getPropertyNamesByPrefix(String prefix, boolean exactPrefixMatch)
    {
        HashSet<String> resultKeySet = new HashSet<>();

        // first fill in the names from the immutable default property set
        Set<String> propertyNameSet;
        String[] namesArray;

        if (!immutableDefaultProperties.isEmpty()) {
            propertyNameSet = immutableDefaultProperties.keySet();
            namesArray = propertyNameSet.toArray(new String[0]);
            getPropertyNamesByPrefix(prefix, exactPrefixMatch, namesArray, resultKeySet);
        }

        // now get property names from the current store.
        getPropertyNamesByPrefix(prefix, exactPrefixMatch, store.getPropertyNames(prefix), resultKeySet);

        // finally, get property names from mutable default property set.
        if (!defaultProperties.isEmpty()) {
            propertyNameSet = defaultProperties.keySet();
            namesArray = propertyNameSet.toArray(new String[0]);
            getPropertyNamesByPrefix(prefix, exactPrefixMatch, namesArray, resultKeySet);
        }
        return new ArrayList<>(resultKeySet);
    }

    /**
     * Updates the specified <code>String</code> <code>resultSet</code> to contain all property names in
     * the <code>names</code> array that partially or completely match the specified prefix.
     * Depending on the value of the <code>exactPrefixMatch</code> parameter the method will (when
     * false) or will not (when exactPrefixMatch is true) include property names that have prefixes
     * longer than the specified <code>prefix</code> param.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have
     * a prefix that is an exact match of the the <code>prefix</code> param or whether properties with
     * prefixes that contain it but are longer than it are also accepted.
     * @param names the list of names that we'd like to search.
     * @return a reference to the updated result set.
     */
    private Set<String> getPropertyNamesByPrefix(String prefix, boolean exactPrefixMatch,
            String[] names, Set<String> resultSet)
    {
        for (String key : names) {
            // cmeng - A valid Property item must have a "." with suffix propertyName string
            int ix = key.lastIndexOf('.');
            if (ix != -1) {
                String keyPrefix = key.substring(0, ix);
                if (exactPrefixMatch) {
                    if (prefix.equals(keyPrefix)) {
                        resultSet.add(key);
                    }
                }
                else if (keyPrefix.startsWith(prefix)) {
                    resultSet.add(key);
                }
            }
        }
        return resultSet;
    }

    /**
     * Returns a <code>List</code> of <code>String</code>s containing the property names that have the
     * specified suffix. A suffix is considered to be everything after the last dot in the property name.
     *
     * For example, imagine a configuration service instance containing two properties only:
     *
     * {@code
     * net.java.sip.communicator.PROP1=value1
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * }
     *
     * A call to this method with <code>suffix</code> equal to "PROP1" will return both properties,
     * whereas the call with <code>suffix</code> equal to "communicator.PROP1" or "PROP2" will return
     * an empty <code>List</code>. Thus, if the <code>suffix</code> argument contains a dot, nothing will be found.
     *
     * @param suffix the suffix for the property names to be returned
     * @return a <code>List</code> of <code>String</code>s containing the property names which contain the
     * specified <code>suffix</code>
     */
    @Override
    public List<String> getPropertyNamesBySuffix(String suffix)
    {
        List<String> resultKeySet = new LinkedList<>();

        for (String key : store.getPropertyNames(suffix)) {
            int ix = key.lastIndexOf('.');

            if ((ix != -1) && suffix.equals(key.substring(ix + 1)))
                resultKeySet.add(key);
        }
        return resultKeySet;
    }

    /**
     * Adds a PropertyChangeListener to the listener list.
     *
     * @param listener the PropertyChangeListener to be added
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the PropertyChangeListener to be added
     */
    @Override
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener)
    {
        changeEventDispatcher.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list for a specific property.
     *
     * @param propertyName a valid property name
     * @param listener the PropertyChangeListener to be removed
     */
    @Override
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener)
    {
        changeEventDispatcher.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Adds a VetoableChangeListener to the listener list.
     *
     * @param listener the VetoableChangeListener to be added
     */
    @Override
    public void addVetoableChangeListener(ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.addVetoableChangeListener(listener);
    }

    /**
     * Removes a VetoableChangeListener from the listener list.
     *
     * @param listener the VetoableChangeListener to be removed
     */
    @Override
    public void removeVetoableChangeListener(ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.removeVetoableChangeListener(listener);
    }

    /**
     * Adds a VetoableChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the VetoableChangeListener to be added
     */
    @Override
    public void addVetoableChangeListener(String propertyName, ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.addVetoableChangeListener(propertyName, listener);
    }

    /**
     * Removes a VetoableChangeListener from the listener list for a specific property.
     *
     * @param propertyName a valid property name
     * @param listener the VetoableChangeListener to be removed
     */
    @Override
    public void removeVetoableChangeListener(String propertyName, ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.removeVetoableChangeListener(propertyName, listener);
    }

    /*
     * Implements ConfigurationService#reloadConfiguration().
     */
    @Override
    public void reloadConfiguration()
            throws IOException
    {
        this.configurationFile = null;
        File file = getConfigurationFile();
        if (file != null) {
            FileAccessService faService = getFileAccessService();

            if (faService != null) {
                // Restore the file if necessary.
                FailSafeTransaction trans = faService.createFailSafeTransaction(file);

                try {
                    trans.restoreFile();
                } catch (Exception e) {
                    Timber.e(e, "Failed to restore configuration file %s", file);
                }
            }
        }

        try {
            store.reloadConfiguration(file);
        } catch (XMLException xmle) {
            throw new IOException(xmle);
        }
    }

    /*
     * Implements ConfigurationService#storeConfiguration().
     */
    @Override
    public synchronized void storeConfiguration()
            throws IOException
    {
        storeConfiguration(getConfigurationFile());
    }

    /**
     * Stores local properties in the specified configuration file.
     *
     * @param file a reference to the configuration file where properties should be stored.
     * @throws IOException if there was a problem writing to the specified file.
     */
    private void storeConfiguration(File file)
            throws IOException
    {
        /*
         * If the configuration file is forcibly considered read-only, do not write it.
         */
        String readOnly = System.getProperty(PNAME_CONFIGURATION_FILE_IS_READ_ONLY);
        if ((readOnly != null) && Boolean.parseBoolean(readOnly))
            return;

        // write the file.
        FailSafeTransaction trans = null;

        if (file != null) {
            FileAccessService faService = getFileAccessService();

            if (faService != null)
                trans = faService.createFailSafeTransaction(file);
        }

        Throwable exception = null;

        try {
            if (trans != null)
                trans.beginTransaction();

            try (OutputStream stream = (file == null) ? null : new FileOutputStream(file)) {
                store.storeConfiguration(stream);
            }

            if (trans != null)
                trans.commit();
        } catch (IllegalStateException | IOException ex) {
            exception = ex;
        }
        if (exception != null) {
            Timber.e(exception, "can't write data in the configuration file");
            if (trans != null)
                trans.rollback();
        }
    }

    /**
     * Use with caution! Returns the name of the configuration file currently used. Placed in
     * HomeDirLocation/HomeDirName {@link #getScHomeDirLocation()} {@link #getScHomeDirName()}
     *
     * @return the name of the configuration file currently used.
     */
    @Override
    public String getConfigurationFilename()
    {
        try {
            File file = getConfigurationFile();
            if (file != null)
                return file.getName();
        } catch (IOException ex) {
            Timber.e(ex, "Error loading configuration file");
        }
        return null;
    }

    @Override
    public boolean isBlindTrustBeforeVerification()
    {
        return getBoolean(PNAME_OMEMO_KEY_BLIND_TRUST, true);
    }

    /**
     * Returns the configuration file currently used by the implementation. If there is no such
     * file or this is the first time we reference it a new one is created.
     *
     * @return the configuration File currently used by the implementation.
     */
    private File getConfigurationFile()
            throws IOException
    {
        if (this.configurationFile == null) {
            createConfigurationFile();
            /*
             * Make sure that the properties SC_HOME_DIR_LOCATION and SC_HOME_DIR_NAME are
             * available in the store of this instance so that users don't have to ask the system
             * properties again.
             */
            getScHomeDirLocation();
            getScHomeDirName();
        }
        return this.configurationFile;
    }

    /**
     * Determines the name and the format of the configuration file to be used and initializes the
     * {@link #configurationFile} and {@link #store} fields of this instance.
     */
    private void createConfigurationFile()
            throws IOException
    {
        /*
         * Choose the format of the configuration file so with the slow and fast XML format when necessary.
         */
        File configurationFile = getConfigurationFile("xml", false);
        if (configurationFile == null) {
            /*
             * It's strange that there's no configuration file name but let it play out as it did
             * when the configuration file was in XML format.
             */
            setConfigurationStore(XMLConfigurationStore.class);
        }
        else {
            /*
             * Figure out the format of the configuration file by looking at its extension.
             */
            String name = configurationFile.getName();
            int extensionBeginIndex = name.lastIndexOf('.');
            String extension = (extensionBeginIndex > -1) ? name.substring(extensionBeginIndex) : null;

            /*
             * Obviously, a file with the .properties extension is in the properties format. Since
             * there's no file with the .xml extension, the case is simple.
             */
            if (".properties".equalsIgnoreCase(extension)) {
                this.configurationFile = configurationFile;
                if (!(this.store instanceof PropertyConfigurationStore))
                    this.store = new PropertyConfigurationStore();
            }
            else {
                /*
                 * But if we're told that the configuration file name is with the .xml extension,
                 * we may also have a .properties file or the .xml extension may be only the
                 * default and not forced on us so it may be fine to create a .properties file
                 * and use the properties format anyway.
                 */
                File newConfigurationFile = new File(configurationFile.getParentFile(),
                        ((extensionBeginIndex > -1) ? name.substring(0, extensionBeginIndex) : name) + ".properties");

                /*
                 * If there's an actual file with the .properties extension, then we've previously
                 * migrated the configuration from the XML format to the properties format. We
                 * may have failed to delete the migrated .xml file but it's fine because the
                 * .properties file is there to signal that we have to use it instead of the .xml file.
                 */
                if (newConfigurationFile.exists()) {
                    this.configurationFile = newConfigurationFile;
                    if (!(this.store instanceof PropertyConfigurationStore))
                        this.store = new PropertyConfigurationStore();
                }
                /*
                 * Otherwise, the lack of an existing .properties file doesn't help us much and we
                 * have the .xml extension for the file name so we have to determine whether
                 * it's just the default or it's been forced on us.
                 */
                else if (getSystemProperty(PNAME_CONFIGURATION_FILE_NAME) == null) {
                    Class<? extends ConfigurationStore> defaultConfigurationStoreClass
                            = getDefaultConfigurationStoreClass();

                    /*
                     * The .xml is not forced on us so we allow ourselves to not obey the default
                     * and use the properties format. If a configuration file in the XML format
                     * exists already, we have to migrate it  to the properties format.
                     */
                    if (configurationFile.exists()) {
                        ConfigurationStore xmlStore = new XMLConfigurationStore();
                        try {
                            xmlStore.reloadConfiguration(configurationFile);
                        } catch (XMLException xmlex) {
                            throw new IOException(xmlex);
                        }
                        setConfigurationStore(defaultConfigurationStoreClass);
                        if (this.store != null)
                            copy(xmlStore, this.store);

                        Throwable exception = null;
                        try {
                            storeConfiguration(this.configurationFile);
                        } catch (IllegalStateException | IOException ex) {
                            exception = ex;
                        }
                        if (exception == null)
                            configurationFile.delete();
                        else {
                            this.configurationFile = configurationFile;
                            this.store = xmlStore;
                        }
                    }
                    else {
                        setConfigurationStore(defaultConfigurationStoreClass);
                    }
                }
                else {
                    /*
                     * The .xml extension is forced on us so we have to assume that whoever forced
                     * it knows what she wants to get so we have to obey and use the XML format.
                     */
                    this.configurationFile = configurationFile.exists()
                            ? configurationFile : getConfigurationFile("xml", true);
                    if (!(this.store instanceof XMLConfigurationStore))
                        this.store = new XMLConfigurationStore();
                }
            }
        }
    }

    /**
     * Returns the location of the directory where SIP Communicator is to store user specific data
     * such as configuration files.
     * Message and call history as well as is bundle repository are store SQL Database.
     *
     * @return the location of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     */
    @Override
    public String getScHomeDirLocation()
    {
        // first let's check whether we already have the name of the directory set as a configuration property
        String scHomeDirLocation = null;
        if (store != null)
            scHomeDirLocation = getString(PNAME_SC_HOME_DIR_LOCATION);

        if (scHomeDirLocation == null) {
            // no luck, check whether user has specified a custom name in the system properties
            // return "/data/user/0/org.atalk.ohos/files" linked to /data/data/..
            scHomeDirLocation = getSystemProperty(PNAME_SC_HOME_DIR_LOCATION);

            if (scHomeDirLocation == null)
                scHomeDirLocation = getSystemProperty("user.home");

            // now save all this as a configuration property so that we don't have to look for it
            // in the sys props next time and so that it is available for other bundles to consult.
            if (store != null) {
                store.setNonSystemProperty(PNAME_SC_HOME_DIR_LOCATION, scHomeDirLocation);
            }
        }
        return scHomeDirLocation;
    }

    /**
     * Returns the name of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     *
     * @return the name of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     */
    @Override
    public String getScHomeDirName()
    {
        // first let's check whether we already have the name of the directory set as a configuration property
        String scHomeDirName = null;

        if (store != null)
            scHomeDirName = getString(PNAME_SC_HOME_DIR_NAME);

        if (scHomeDirName == null) {
            // no luck, check whether user has specified a custom name in the system properties
            // return "/data/user/0/org.atalk.ohos/files" linked to /data/data/..
            scHomeDirName = getSystemProperty(PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName == null)
                scHomeDirName = ".sip-communicator";

            // now save all this as a configuration property so that we don't have to look for it
            // in the sys props next time and so that it is available for other bundles to consult.
            if (store != null)
                store.setNonSystemProperty(PNAME_SC_HOME_DIR_NAME, scHomeDirName);
        }
        return scHomeDirName;
    }

    /**
     * Returns a reference to the configuration file that the service should load. The method
     * would try to load a file with the name sip-communicator.xml unless a different one is
     * specified in the system property net.java .sip.communicator.PROPERTIES_FILE_NAME. The
     * method would first try to load the file from the current directory if it exists this is not
     * the case a load would be attempted from the $HOME/.sip-communicator directory. In case it
     * was not found there either we'll look for it in all locations currently present in the
     * $CLASSPATH. In case we find it in there we will copy it to the $HOME/.sip-communicator
     * directory in case it was in a jar archive and return the reference to the newly created
     * file. In case the file is to be found nowhere - a new empty file in the user home
     * directory and returns a link to that one.
     *
     * @param extension the extension of the file name of the configuration file. The specified extension may
     * not be taken into account if the the configuration file name is forced through a system property.
     * @param create <code>true</code> to create the configuration file with the determined file name if it
     * does not exist; <code>false</code> to only figure out the file name of the configuration file without creating it
     * @return the configuration file currently used by the implementation.
     */
    private File getConfigurationFile(String extension, boolean create)
            throws IOException
    {
        // see whether we have a user specified name for the conf file
        String pFileName = getSystemProperty(PNAME_CONFIGURATION_FILE_NAME);
        if (pFileName == null)
            pFileName = "sip-communicator." + extension;

        // try to open the file in current directory
        File configFileInCurrentDir = new File(pFileName);
        if (configFileInCurrentDir.exists()) {
            Timber.d("Using config file in current dir: %s", configFileInCurrentDir.getAbsolutePath());
            return configFileInCurrentDir;
        }

        // we didn't find it in ".", try the SIP Communicator home directory first check whether a
        // custom SC home directory is specified

        File configDir = new File(getScHomeDirLocation(), getScHomeDirName());
        File configFileInUserHomeDir = new File(configDir, pFileName);

        if (configFileInUserHomeDir.exists()) {
            Timber.d("Using config file in $HOME/.sip-communicator: %s", configFileInUserHomeDir.getAbsolutePath());
            return configFileInUserHomeDir;
        }

        // If we are in a jar - copy config file from jar to user home.
        InputStream in = getClass().getClassLoader().getResourceAsStream(pFileName);

        // Return an empty file if there wasn't any in the jar null check report from John J.Barton - IBM
        if (in == null) {
            if (create) {
                configDir.mkdirs();
                configFileInUserHomeDir.createNewFile();
                Timber.d("Created an empty file in $HOME: %s", configFileInUserHomeDir.getAbsolutePath());
            }
            return configFileInUserHomeDir;
        }

        Timber.log(TimberLog.FINER, "Copying config file from JAR into %s", configFileInUserHomeDir.getAbsolutePath());
        configDir.mkdirs();
        try {
            copy(in, configFileInUserHomeDir);
        } finally {
            try {
                in.close();
            } catch (IOException ioex) {
                /*
                 * Ignore it because it doesn't matter and, most importantly, it shouldn't prevent
                 * us from using the configuration file.
                 */
                ioex.printStackTrace();
            }
        }
        return configFileInUserHomeDir;
    }

    /**
     * Gets the <code>ConfigurationStore</code> <code>Class</code> to be used as the default when no
     * specific <code>ConfigurationStore</code> <code>Class</code> is determined as necessary.
     *
     * @return the <code>ConfigurationStore</code> <code>Class</code> to be used as the default when no
     * specific <code>ConfigurationStore</code> <code>Class</code> is determined as necessary
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends ConfigurationStore> getDefaultConfigurationStoreClass()
    {
        Class<? extends ConfigurationStore> defaultConfigurationStoreClass = null;

        if (DEFAULT_CONFIGURATION_STORE_CLASS_NAME != null) {
            Class<?> clazz = null;

            try {
                clazz = Class.forName(DEFAULT_CONFIGURATION_STORE_CLASS_NAME);
            } catch (ClassNotFoundException ignore) {
            }
            if ((clazz != null) && ConfigurationStore.class.isAssignableFrom(clazz))
                defaultConfigurationStoreClass = (Class<? extends ConfigurationStore>) clazz;
        }
        if (defaultConfigurationStoreClass == null)
            defaultConfigurationStoreClass = PropertyConfigurationStore.class;
        return defaultConfigurationStoreClass;
    }

    private static void copy(ConfigurationStore src, ConfigurationStore dest)
    {
        for (String name : src.getPropertyNames(""))
            if (src.isSystemProperty(name))
                dest.setSystemProperty(name);
            else
                dest.setNonSystemProperty(name, src.getProperty(name));
    }

    /**
     * Copies the contents of a specific {@code InputStream} as bytes into a specific output {@code File}.
     *
     * @param inputStream the {@code InputStream} the contents of which is to be output in the specified {@code File}
     * @param outputFile the {@code File} to write the contents of the specified {@code InputStream} into
     * @throws IOException IO Exception
     */
    private static void copy(InputStream inputStream, File outputFile)
            throws IOException
    {

        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] bytes = new byte[4 * 1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, bytesRead);
            }
        }
    }

    /**
     * Returns the value of the specified java system property. In case the value was a zero
     * length String or one that only contained whitespaces, null is returned. This method is for
     * internal use only. Users of the configuration service are to use the getProperty() or
     * getString() methods which would automatically determine whether a property is system or not.
     *
     * @param propertyName the name of the property whose value we need.
     * @return the value of the property with name propertyName or null if the value had length 0
     * or only contained spaces tabs or new lines.
     */
    private static String getSystemProperty(String propertyName)
    {
        String retval = System.getProperty(propertyName);
        return StringUtils.returnIfNotEmptyTrimmed(retval);
    }

    /**
     * Returns the String value of the specified property (minus all encompassing whitespaces)and
     * null in case no property value was mapped against the specified propertyName, or in case
     * the returned property string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in case there was no
     * value mapped against the specified <code>propertyName</code>, or the returned string had zero
     * length or contained whitespaces only.
     */
    @Override
    public String getString(String propertyName)
    {
        Object propValue = getProperty(propertyName);
        if (propValue == null)
            return null;

        return StringUtils.returnIfNotEmptyTrimmed(propValue.toString());
    }

    /**
     * Returns the String value of the specified property and null in case no property value was
     * mapped against the specified propertyName, or in case the returned property string had
     * zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @param defaultValue the value to be returned if the specified property name is not associated with a value
     * in this {@code ConfigurationService}
     * @return the result of calling the property's toString method and {@code defaultValue}
     * in case there was no value mapped against the specified <code>propertyName</code>, or the
     * returned string had zero length or contained whitespaces only.
     */
    @Override
    public String getString(String propertyName, String defaultValue)
    {
        String value = getString(propertyName);
        return StringUtils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Implements ConfigurationService#getBoolean(String, boolean).
     */
    @Override
    public boolean getBoolean(String propertyName, boolean defaultValue)
    {
        String value = getString(propertyName);
        return StringUtils.isNullOrEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * Gets a (cached) reference to a <code>FileAccessService</code> implementation to be used by this
     * <code>ConfigurationService</code> implementation.
     *
     * @return a (cached) reference to a <code>FileAccessService</code> implementation
     */
    private synchronized FileAccessService getFileAccessService()
    {
        if ((faService == null) && !faServiceIsAssigned) {
            faService = LibJitsi.getFileAccessService();
            faServiceIsAssigned = true;
        }
        return faService;
    }

    /**
     * Gets the value of a specific property as a signed decimal integer. If the specified
     * property name is associated with a value in this <code>ConfigurationService</code>, the string
     * representation of the value is parsed into a signed decimal integer according to the rules
     * of {@link Integer#parseInt(String)} . If parsing the value as a signed decimal integer
     * fails or there is no value associated with the specified property name,
     * <code>defaultValue</code> is returned.
     *
     * @param propertyName the name of the property to get the value of as a signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the specified property name as a
     * signed decimal integer fails or there is no value associated with the specified
     * property name in this <code>ConfigurationService</code>
     * @return the value of the property with the specified name in this
     * <code>ConfigurationService</code> as a signed decimal integer; <code>defaultValue</code> if
     * parsing the value of the specified property name fails or no value is associated in this
     * <code>ConfigurationService</code> with the specified property name
     */
    @Override
    public int getInt(String propertyName, int defaultValue)
    {
        String stringValue = getString(propertyName);
        int intValue = defaultValue;

        if ((stringValue != null) && (!stringValue.isEmpty())) {
            try {
                intValue = Integer.parseInt(stringValue);
            } catch (NumberFormatException ex) {
                Timber.e(ex, " %sdoes not appear to be an integer. Defaulting to %s",
                        propertyName, defaultValue);
            }
        }
        return intValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getDouble(String propertyName, double defaultValue)
    {
        String stringValue = getString(propertyName);
        double doubleValue = defaultValue;

        if ((stringValue != null) && (!stringValue.isEmpty())) {
            try {
                doubleValue = Double.parseDouble(stringValue);
            } catch (NumberFormatException ex) {
                Timber.e(ex, "%s does not appear to be a double. Defaulting to %s",
                        propertyName, defaultValue);
            }
        }
        return doubleValue;
    }

    /**
     * Gets the value of a specific property as a signed decimal long integer. If the specified
     * property name is associated with a value in this <code>ConfigurationService</code>, the string
     * representation of the value is parsed into a signed decimal long integer according to
     * the rules of {@link Long#parseLong(String)} . If parsing the value as a signed decimal long
     * integer fails or there is no value associated with the specified property name,
     * <code>defaultValue</code> is returned.
     *
     * @param propertyName the name of the property to get the value of as a signed decimal long integer
     * @param defaultValue the value to be returned if parsing the value of the specified property name as a
     * signed decimal long integer fails or there is no value associated with the specified
     * property name in this
     * <code>ConfigurationService</code>
     * @return the value of the property with the specified name in this
     * <code>ConfigurationService</code> as a signed decimal long integer;
     * <code>defaultValue</code> if parsing the value of the specified property name fails or no value
     * is associated in this <code>ConfigurationService</code> with the specified property name
     */
    @Override
    public long getLong(String propertyName, long defaultValue)
    {
        String stringValue = getString(propertyName);
        long longValue = defaultValue;

        if ((stringValue != null) && (!stringValue.isEmpty())) {
            try {
                longValue = Long.parseLong(stringValue);
            } catch (NumberFormatException ex) {
                Timber.e(ex, "%s does not appear to be a longinteger. Defaulting to %s",
                        propertyName, defaultValue);
            }
        }
        return longValue;
    }

    /**
     * Determines whether the property with the specified <code>propertyName</code> has been
     * previously declared as System
     *
     * @param propertyName the name of the property to verify
     * @return true if someone at some point specified that property to be system. (This could
     * have been either through a call to setProperty(string, true)) or by setting the system
     * attribute in the xml conf file to true.
     */
    private boolean isSystemProperty(String propertyName)
    {
        return store.isSystemProperty(propertyName);
    }

    /**
     * Deletes the configuration file currently used by this implementation.
     */
    @Override
    public void purgeStoredConfiguration()
    {
        if (configurationFile != null) {
            configurationFile.delete();
            configurationFile = null;
        }
        if (store != null)
            for (String name : store.getPropertyNames(""))
                store.removeProperty(name);
    }

    /**
     * Goes over all system properties and outputs their names and values for debug purposes.
     * Changed that system properties are printed in INFO level and this way they
     * are included in the beginning of every users log file.
     */
    private void debugPrintSystemProperties()
    {
        try {
            // Password system properties
            Pattern exclusion = null;
            if (PASSWORD_SYS_PROPS != null) {
                exclusion = Pattern.compile(PASSWORD_SYS_PROPS, Pattern.CASE_INSENSITIVE);
            }
            // Password command line arguments
            String[] passwordArgs = null;
            if (PASSWORD_CMD_LINE_ARGS != null)
                passwordArgs = PASSWORD_CMD_LINE_ARGS.split(",");

            for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
                String key = String.valueOf(e.getKey());
                String value = String.valueOf(e.getValue());
                // Check if this key value should be masked
                if (exclusion != null && exclusion.matcher(key).find()) {
                    value = "**********";
                }
                // Mask command line arguments
                if (passwordArgs != null && "sun.java.command".equals(key)) {
                    value = PasswordUtil.replacePasswords(value, passwordArgs);
                }
                Timber.i("%s = %s", key, value);
            }
        } catch (RuntimeException e) {
            Timber.w(e, "An exception occurred while writing debug info");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logConfigurationProperties(String excludePattern)
    {
        if (!TimberLog.isTraceEnable)
            return;

        Pattern exclusion = null;
        if (!StringUtils.isNullOrEmpty(excludePattern)) {
            exclusion = Pattern.compile(excludePattern, Pattern.CASE_INSENSITIVE);
        }

        for (String p : getAllPropertyNames(excludePattern)) {
            Object v = getProperty(p);

            // Not sure if this can happen, but just in case...
            if (v == null)
                continue;

            if (exclusion != null && exclusion.matcher(p).find()) {
                v = "**********";
            }
            Timber.i("%s = %s", p, v);
        }
    }

    /**
     * The method scans the contents of the SYS_PROPS_FILE_NAME_PROPERTY where it expects to find
     * a comma separated list of names of files that should be loaded as system properties. The
     * method then parses these files and loads their contents as system properties. All such
     * files have to be in a location that's in the classpath.
     */
    private void preloadSystemPropertyFiles()
    {
        String propFilesListStr
                = StringUtils.returnIfNotEmptyTrimmed(System.getProperty(SYS_PROPS_FILE_NAME_PROPERTY));
        if (propFilesListStr == null)
            return;

        StringTokenizer tokenizer = new StringTokenizer(propFilesListStr, ";,", false);
        while (tokenizer.hasMoreTokens()) {
            String fileName = tokenizer.nextToken();
            try {
                fileName = fileName.trim();
                Properties fileProps = new Properties();
                try (InputStream stream = ClassLoader.getSystemResourceAsStream(fileName)) {
                    fileProps.load(stream);
                }

                // now set all of this file's properties as system properties
                for (Map.Entry<Object, Object> entry : fileProps.entrySet())
                    System.setProperty((String) entry.getKey(), (String) entry.getValue());
            } catch (Exception ex) {
                // this is an insignificant method that should never affect the rest of the
                // application so we'll afford ourselves to kind of silence all possible
                // exceptions (which would most often be IOExceptions). We will however log them
                // in case anyone would be interested.
                Timber.e(ex, "Failed to load property file: %s", fileName);
            }
        }
    }

    /**
     * Specifies the configuration store that this instance of the configuration service
     * implementation must use.
     *
     * @param clazz the {@link ConfigurationStore} that this configuration service instance instance has to use.
     * @throws IOException if loading properties from the specified store fails.
     */
    private void setConfigurationStore(Class<? extends ConfigurationStore> clazz)
            throws IOException
    {
        String extension = null;

        if (PropertyConfigurationStore.class.isAssignableFrom(clazz))
            extension = "properties";
        else if (XMLConfigurationStore.class.isAssignableFrom(clazz))
            extension = "xml";

        this.configurationFile = (extension == null) ? null : getConfigurationFile(extension, true);

        if (!clazz.isInstance(this.store)) {
            Throwable exception = null;
            try {
                this.store = clazz.newInstance();
            } catch (IllegalAccessException | InstantiationException ex) {
                exception = ex;
            }

            if (exception != null)
                throw new RuntimeException(exception);
        }

    }

    /**
     * Loads the default property maps from the Jitsi installation directory then overrides them
     * with the default override values.
     */
    private void loadDefaultProperties()
    {
        loadDefaultProperties(DEFAULT_PROPS_FILE_NAME);
        loadDefaultProperties(DEFAULT_OVERRIDES_PROPS_FILE_NAME);
    }

    /**
     * Tests whether the application has been launched using Java WebStart
     */
    private boolean isLaunchedByWebStart()
    {
        boolean hasJNLP;
        try {
            Class.forName("javax.jnlp.ServiceManager");
            hasJNLP = true;
        } catch (ClassNotFoundException ex) {
            hasJNLP = false;
        }
        String jwsVersion = System.getProperty("javawebstart.version");
        if (jwsVersion != null && !jwsVersion.isEmpty()) {
            hasJNLP = true;
        }
        return hasJNLP;
    }

    /**
     * Loads the specified default properties maps from the Jitsi installation directory.
     * Typically this file is to be called for the default properties and the admin overrides.
     *
     * @param fileName the name of the file we need to load.
     */
    private void loadDefaultProperties(String fileName)
    {
        try {
            Properties fileProps = new Properties();

            InputStream fileStream;
            if (OSUtils.IS_ANDROID) {
                fileStream = getClass().getClassLoader().getResourceAsStream(fileName);
            }
            else if (isLaunchedByWebStart()) {
                Timber.i("WebStart classloader");
                fileStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
            }
            else {
                Timber.i("Normal classloader");
                fileStream = ClassLoader.getSystemResourceAsStream(fileName);
            }

            if (fileStream == null) {
                Timber.i("Failed to find '%s' with class loader, will continue without it.", fileName);
                return;
            }

            fileProps.load(fileStream);
            fileStream.close();

            // now get those properties and place them into the mutable and immutable properties maps.
            for (Map.Entry<Object, Object> entry : fileProps.entrySet()) {
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();

                if (name == null || value == null || name.trim().isEmpty()) {
                    continue;
                }

                if (name.startsWith("*")) {
                    name = name.substring(1);
                    if (name.trim().isEmpty()) {
                        continue;
                    }

                    // it seems that we have a valid default immutable property
                    immutableDefaultProperties.put(name, value);

                    // in case this is an override, make sure we remove previous definitions of this property
                    defaultProperties.remove(name);
                }
                else {
                    // this property is a regular, mutable default property.
                    defaultProperties.put(name, value);

                    // in case this is an override, make sure we remove previous definitions of this property
                    immutableDefaultProperties.remove(name);
                }
            }
        } catch (Exception ex) {
            // we can function without defaults so we are just logging those.
            Timber.i("No defaults property file loaded: %s. Not a problem.", fileName);

            Timber.d(ex, "load exception");
        }
    }
}
