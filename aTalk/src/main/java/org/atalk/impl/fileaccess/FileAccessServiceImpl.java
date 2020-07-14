/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.fileaccess;

import org.apache.commons.lang3.StringUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.*;
import org.atalk.service.libjitsi.LibJitsi;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

/**
 * Default FileAccessService implementation.
 *
 * @author Alexander Pelov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class FileAccessServiceImpl implements FileAccessService
{
    /**
     * The file prefix for all temp files.
     */
    private static final String TEMP_FILE_PREFIX = "SIPCOMM";

    /**
     * The file suffix for all temp files.
     */
    private static final String TEMP_FILE_SUFFIX = "TEMP";

    private String profileDirLocation;
    private String cacheDirLocation;
    private String logDirLocation;
    private String scHomeDirName;

    /**
     * The indicator which determines whether {@link #initialize()} has been invoked on this instance. Introduced to
     * delay the initialization of the state of this instance until it is actually necessary.
     */
    private boolean initialized = false;

    public FileAccessServiceImpl()
    {
    }

    /**
     * This method returns a created temporary file. After you close this file it is not guaranteed that you will be
     * able to open it again nor that it will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException If the file cannot be created
     */
    @Override
    public File getTemporaryFile()
            throws IOException
    {
        return TempFileManager.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
    }

    /**
     * Returns the temporary directory.
     *
     * @return the created temporary directory
     * @throws IOException if the temporary directory cannot not be created
     */
    @Override
    public File getTemporaryDirectory()
            throws IOException
    {
        File file = getTemporaryFile();

        if (!file.delete()) {
            throw new IOException("Could not create temporary directory, because: could not delete temporary file.");
        }
        if (!file.mkdirs()) {
            throw new IOException("Could not create temporary directory");
        }
        return file;
    }

    /**
     * Please use {@link #getPrivatePersistentFile(String, FileCategory)}.
     */
    @Deprecated
    @Override
    public File getPrivatePersistentFile(String fileName)
            throws Exception
    {
        return this.getPrivatePersistentFile(fileName, FileCategory.PROFILE);
    }

    /**
     * This method returns a file specific to the current user. It may not exist, but it is guaranteed that you will
     * have the sufficient rights to create it.
     *
     * This file should not be considered secure because the implementor may return a file accessible to everyone.
     * Generally it will reside in current user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName The name of the private file you wish to access
     * @param category The classification of the file.
     * @return The file
     * @throws Exception if we failed to create the file.
     */
    @Override
    public File getPrivatePersistentFile(String fileName, FileCategory category)
            throws Exception
    {
        File file = accessibleFile(getFullPath(category), fileName);
        if (file == null) {
            throw new SecurityException("Insufficient rights to access this file in current user's home directory: "
                    + new File(getFullPath(category), fileName).getPath());
        }
        return file;
    }

    /**
     * Please use {@link #getPrivatePersistentDirectory(String, FileCategory)}
     */
    @Deprecated
    @Override
    public File getPrivatePersistentDirectory(String dirName)
            throws Exception
    {
        return getPrivatePersistentDirectory(dirName, FileCategory.PROFILE);
    }

    /**
     * This method creates a directory specific to the current user.
     *
     * This directory should not be considered secure because the implementor may return a directory accessible to
     * everyone. Generally it will reside in current user's homedir, but it may as well reside in a shared directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName The name of the private directory you wish to access.
     * @param category The classification of the directory.
     * @return The created directory.
     * @throws Exception Thrown if there is no suitable location for the persistent directory.
     */
    @Override
    public File getPrivatePersistentDirectory(String dirName, FileCategory category)
            throws Exception
    {
        File dir = new File(getFullPath(category), dirName);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new RuntimeException("Could not create directory " + "because: A file exists with this name:" + dir.getAbsolutePath());
            }
        }
        else if (!dir.mkdirs()) {
            throw new IOException("Could not create directory");
        }
        return dir;
    }

    /**
     * Returns the full path corresponding to a file located in the sip-communicator config home and carrying the
     * specified name.
     *
     * @param category The classification of the file or directory.
     * @return the config home location of a a file with the specified name.
     */
    private File getFullPath(FileCategory category)
    {
        initialize();

        // bypass the configurationService here to remove the dependency
        String directory;

        switch (category) {
            case CACHE:
                directory = this.cacheDirLocation;
                break;
            case LOG:
                directory = this.logDirLocation;
                break;
            default:
                directory = this.profileDirLocation;
                break;
        }

        return new File(directory, this.scHomeDirName);
    }

    /**
     * Returns the value of the specified java system property. In case the value was a zero length String or one that
     * only contained whitespaces, null is returned. This method is for internal use only. Users of the configuration
     * service are to use the getProperty() or getString() methods which would automatically determine whether a
     * property is system or not.
     *
     * @param propertyName the name of the property whose value we need.
     * @return the value of the property with name propertyName or null if the value had length 0 or only contained
     * spaces tabs or new lines.
     */
    private static String getSystemProperty(String propertyName)
    {
        String retval = System.getProperty(propertyName);
        return StringUtils.isBlank(retval) ? null : retval;
    }

    /**
     * Checks if a file exists and if it is writable or readable. If not - checks if the user has a write privileges to
     * the containing directory.
     *
     * If those conditions are met it returns a File in the directory with a fileName. If not - returns null.
     *
     * @param homedir the location of the sip-communicator home directory.
     * @param fileName the name of the file to create.
     * @return Returns null if the file does not exist and cannot be created. Otherwise - an object to this file
     * @throws IOException Thrown if the home directory cannot be created
     */
    private static File accessibleFile(File homedir, String fileName)
            throws IOException
    {
        File file = new File(homedir, fileName);
        if (file.canRead() || file.canWrite()) {
            return file;
        }

        if (!homedir.exists()) {
            Timber.d("Creating home directory : %s", homedir.getAbsolutePath());
            if (!homedir.mkdirs()) {
                String message = "Could not create the home directory : " + homedir.getAbsolutePath();

                Timber.d("%s", message);
                throw new IOException(message);
            }
            Timber.d("Home directory created : %s", homedir.getAbsolutePath());
        }
        else if (!homedir.canWrite()) {
            file = null;
        }

        if (file != null && !file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                String message = "Could not create the parent directory : " + homedir.getAbsolutePath();

                Timber.d("%s", message);
                throw new IOException(message);
            }
        }
        return file;
    }

    /**
     * Returns the default download directory.
     *
     * @return the default download directory
     */
    @Override
    public File getDefaultDownloadDirectory()
    {
        // For all other operating systems we return the Downloads folder.
        return new File(getSystemProperty("user.home"), "Downloads");
    }

    /**
     * Gets the major version of the executing operating system as defined by the <tt>os.version</tt> system property.
     *
     * @return the major version of the executing operating system as defined by the <tt>os.version</tt> system property
     */
    private static int getMajorOSVersion()
    {
        String osVersion = System.getProperty("os.version");
        int majorOSVersion;

        if ((osVersion != null) && (osVersion.length() > 0)) {
            int majorOSVersionEnd = osVersion.indexOf('.');
            String majorOSVersionString = (majorOSVersionEnd > -1) ? osVersion.substring(0, majorOSVersionEnd) : osVersion;

            majorOSVersion = Integer.parseInt(majorOSVersionString);
        }
        else
            majorOSVersion = 0;
        return majorOSVersion;
    }

    /**
     * Creates a failsafe transaction which can be used to safely store informations into a file.
     *
     * @param file The file concerned by the transaction, null if file is null.
     * @return A new failsafe transaction related to the given file.
     */
    @Override
    public FailSafeTransaction createFailSafeTransaction(File file)
    {
        return (file == null) ? null : new FailSafeTransactionImpl(file);
    }

    /**
     * Initializes this instance if it has not been initialized yet i.e. acts as a delayed constructor of this instance.
     * Introduced because this <tt>FileAccessServiceImpl</tt> queries <tt>System</tt> properties that may not be set yet
     * at construction time and, consequently, throws an <tt>IllegalStateException</tt> which could be avoided.
     */
    private synchronized void initialize()
    {
        if (initialized)
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        profileDirLocation = cfg != null ? cfg.getScHomeDirLocation()
                : getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);
        if (profileDirLocation == null) {
            throw new IllegalStateException(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);
        }

        scHomeDirName = cfg != null ? cfg.getScHomeDirName()
                : getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME);
        if (scHomeDirName == null) {
            throw new IllegalStateException(ConfigurationService.PNAME_SC_HOME_DIR_NAME);
        }

        String cacheDir = getSystemProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION);
        cacheDirLocation = (cacheDir == null) ? profileDirLocation : cacheDir;

        String logDir = getSystemProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION);
        logDirLocation = (logDir == null) ? profileDirLocation : logDir;
        initialized = true;
    }
}
