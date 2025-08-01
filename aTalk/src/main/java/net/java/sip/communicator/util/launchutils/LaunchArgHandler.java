/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;

import net.java.sip.communicator.util.ScStdOut;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;

import timber.log.Timber;

/**
 * The <code>LauncherArgHandler</code> class handles invocation arguments that have
 * been passed to us when running SIP Communicator. The class supports a fixed
 * set of options and also allows for registration of delegates.
 *
 * @author Emil Ivov <emcho at sip-communicator.org>
 * @author Eng Chong Meng
 */
public class LaunchArgHandler {
    /**
     * Our class logger.
     */
    private static final net.java.sip.communicator.util.Logger logger =
            net.java.sip.communicator.util.Logger.getLogger(LaunchArgHandler.class);

    /**
     * Returned by the <code>handleArgs</code> methods when the arguments that have
     * been parsed do not require for SIP Communicator to be started and the
     * Launcher is supposed to exit. That could happen when "SIP Communicator"
     * is launched with a --version argument for example or when trying to
     * run the application after an instance was already launched.
     */
    public static final int ACTION_EXIT = 0;

    /**
     * Returned by the <code>handleArgs</code> methods when all arguments have been
     * parsed and the SIP Communicator launch can continue.
     */
    public static final int ACTION_CONTINUE = 1;

    /**
     * Returned by the <code>handleArgs</code> method when parsing the arguments
     * has failed or if no arguments were passed and an instance of SC was already
     * launched. If this is the code returned by handleArgs, then the <code>getErrorCode</code>
     * method would return an error code indicating what the error was.
     */
    public static final int ACTION_ERROR = 2;

    /**
     * Returned by the <code>handleArgs</code> methods when all arguments have been successfully
     * parsed and one of them indicates that the user has requested a multi instance launch.
     */
    public static final int ACTION_CONTINUE_LOCK_DISABLED = 3;

    /**
     * The error code returned when we couldn't parse one of the options.
     */
    public static final int ERROR_CODE_UNKNOWN_ARG = 1;

    /**
     * The error code returned when we try to launch SIP Communicator while there is already
     * a running instance and there were no arguments that we forward to that instance.
     */
    public static final int ERROR_CODE_ALREADY_STARTED = 2;

    /**
     * The error code that we return when we fail to create a directory that has
     * been specified with the -c|--config option.
     */
    public static final int ERROR_CODE_CREATE_DIR_FAILED = 3;

    /**
     * The property name containing the name of the application (e.g. SIP Communicator)
     */
    private static final String PNAME_APPLICATION_NAME = "APPLICATION_NAME";

    /**
     * The package name of the applications (e.g. atalk).
     */
    private static final String PNAME_PACKAGE_NAME = "PACKAGE_NAME";

    /**
     * The property name containing the current version.
     */
    private static final String PNAME_VERSION = "APPLICATION_VERSION";

    /**
     * The name of the file containing version properties for use with the argument handler.
     */
    private static final String VERSION_PROPERTIES = "version.properties";

    /**
     * The errorCode identifying the error that occurred last time <code>handleArgs</code> was called.
     */
    private int errorCode = 0;

    /**
     * A reference to the instance of the
     */
    private final ArgDelegator argDelegator = new ArgDelegator();

    /**
     * The singleton instance of this handler.
     */
    private static LaunchArgHandler argHandler = null;

    /**
     * The properties where we load version info from our update location.
     */
    private final Properties versionProperties = new Properties();

    /**
     * Creates the sole instance of this class;
     */
    private LaunchArgHandler() {
        InputStream versionPropertiesStream = getClass().getResourceAsStream(VERSION_PROPERTIES);
        boolean versionPropertiesAreLoaded = false;
        if (versionPropertiesStream != null) {
            try {
                try {
                    versionProperties.load(versionPropertiesStream);
                    versionPropertiesAreLoaded = true;
                } finally {
                    versionPropertiesStream.close();
                }
            } catch (IOException exc) {
                // no need to worry the user, so only print if we're in FINEST
            }
        }
        if (!versionPropertiesAreLoaded) {
            Timber.log(TimberLog.FINER, "Couldn't open version.properties");
        }

        // Start url handler for Mac OS X.
        /*
         * XXX The detection of the operating systems is the responsibility of
         * OSUtils. It used to reside in the util.jar which is in the classpath
         * but it is now in libjitsi.jar which is not in the classpath.
         */
        String osName = System.getProperty("os.name");
        if ((osName != null) && osName.startsWith("Mac"))
            new AEGetURLEventHandler(this);
    }

    /**
     * Creates a singleton instance of the LauncherArgHandler if necessary and returns a reference to it.
     *
     * @return the singleton instance of the LauncherArgHandler.
     */
    public static LaunchArgHandler getInstance() {
        if (argHandler == null) {
            argHandler = new LaunchArgHandler();
        }

        return argHandler;
    }

    /**
     * Does the actual argument handling.
     *
     * @param args the arguments the way we have received them from the main() method.
     *
     * @return one of the ACTION_XXX fields defined here, intended to indicate
     * to the caller they action that they are supposed as a result of the arg handling.
     */
    public int handleArgs(String[] args) {
        int returnAction = ACTION_CONTINUE;

        for (int i = 0; i < args.length; i++) {
            Timber.log(TimberLog.FINER, "handling arg %s", i);

            if (args[i].equals("--version") || args[i].equals("-v")) {
                handleVersionArg();
                //we're supposed to exit after printing version info
                returnAction = ACTION_EXIT;
                break;
            }
            else if (args[i].equals("--help") || args[i].equals("-h")) {
                handleHelpArg();
                //we're supposed to exit after printing the help message
                returnAction = ACTION_EXIT;
                break;
            }
            else if (args[i].equals("--debug") || args[i].equals("-d")) {
                handleDebugArg(args[i]);
                continue;
            }
            else if (args[i].equals("--ipv6") || args[i].equals("-6")) {
                handleIPv6Enforcement();
                break;
            }
            else if (args[i].equals("--ipv4") || args[i].equals("-4")) {
                handleIPv4Enforcement();
                break;
            }
            else if (args[i].startsWith("--config=")) {
                returnAction = handleConfigArg(args[i]);

                if (returnAction == ACTION_ERROR)
                    break;
                else
                    continue;
            }
            else if (args[i].equals("-c")) {
                //make sure we have at least one more argument left.
                if (i == args.length - 1) {
                    System.out.println("The \"-c\" option expects a directory parameter.");
                    returnAction = ACTION_ERROR;
                    break;
                }
                handleConfigArg(args[++i]);
                continue;
            }
            else if (args[i].equals("--multiple") || args[i].equals("-m")) {
                returnAction = ACTION_CONTINUE_LOCK_DISABLED;
                continue;
            }
            else if (args[i].startsWith("--splash=")) {
                // do nothing already handled by startup script/binary
                continue;
            }
            else if (args[i].startsWith("--notray")) {
                System.setProperty("disable-tray", "true");
                continue;
            }
            //if this is the last arg and it's not an option then it's probably
            //an URI
            else if (i == args.length - 1
                    && !args[i].startsWith("-")) {
                handleUri(args[i]);
            }
            else {
                handleUnknownArg(args[i]);

                errorCode = ERROR_CODE_UNKNOWN_ARG;
                returnAction = ACTION_ERROR;
                break;
            }
        }
        return returnAction;
    }

    /**
     * Forces use of IPv6 addresses where possible. (This should one day become a default mode of operation.)
     */
    private void handleIPv6Enforcement() {
        System.setProperty("java.net.preferIPv4Stack", "false");
        System.setProperty("java.net.preferIPv6Addresses", "true");
    }

    /**
     * Forces non-support for IPv6 and use of IPv4 only.
     */
    private void handleIPv4Enforcement() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    /**
     * Passes <code>uriArg</code> to our uri manager for handling.
     *
     * @param uri the uri that we'd like to pass to
     */
    private void handleUri(String uri) {
        Timber.log(TimberLog.FINER, "Handling uri %s", uri);
        argDelegator.handleUri(uri);
    }

    /**
     * Instructs SIP Communicator to print logging messages to the console.
     *
     * @param arg the debug arg which we are not really using in this method.
     */
    private void handleDebugArg(String arg) {
        //first enable standard out printing
        ScStdOut.setStdOutPrintingEnabled(true);

        //then find a console handler (or create a new one) and set its level to FINEST
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getAnonymousLogger().getParent();
        ConsoleHandler conHan = null;

        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                conHan = (ConsoleHandler) handler;
                break;
            }
        }
        if (conHan == null) {
            conHan = new ConsoleHandler();
            rootLogger.addHandler(conHan);
        }
        //conHan.setLevel(Level.SEVERE);
    }

    /**
     * Instructs SIP Communicator change the location of its home dir.
     *
     * @param configArg the arg containing the location of the new dir.
     *
     * @return either ACTION_ERROR or ACTION_CONTINUE depending on whether or
     * not parsing the option went fine.
     */
    private int handleConfigArg(String configArg) {
        if (configArg.startsWith("--config=")) {
            configArg = configArg.substring("--config=".length());

        }
        File configDir = new File(configArg);
        configDir.mkdirs();

        if (!configDir.isDirectory()) {
            System.out.println("Failed to create directory " + configArg);
            errorCode = ERROR_CODE_CREATE_DIR_FAILED;
            return ACTION_ERROR;
        }

        System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, configDir.getParent());
        System.setProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION, configDir.getParent());
        System.setProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION, configDir.getParent());
        System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME, configDir.getName());

        //we instantiated our class logger before we had a chance to change the dir so we need to reset it now.
        logger.reset();

        return ACTION_CONTINUE;
    }

    /**
     * Prints the name and the version of this application. This method uses the
     * version.properties file which is created by ant during the build process.
     * If this file does not exist the method would print a default name and version string.
     */
    private void handleVersionArg() {
        String name = getApplicationName();
        String version = getVersion();

        if (name.trim().isEmpty()) {
            name = "atalk";
        }

        if (version.trim().isEmpty()) {
            version = "build.by.SVN";
        }
        System.out.println(name + " " + version);

    }

    /**
     * Returns the version of the SIP Communicator instance that we are currently running.
     *
     * @return a String containing the version of the SC instance we are currently running.
     */
    private String getVersion() {
        String version = versionProperties.getProperty(PNAME_VERSION);
        return version == null ? "build.by.SVN" : version;
    }

    /**
     * Returns the name of the application. That should be aTalk most of the time but who knows ..
     *
     * @return the name of the application (i.e. SIP Communicator until we change our name some day.)
     */
    private String getApplicationName() {
        String name = versionProperties.getProperty(PNAME_APPLICATION_NAME);
        return name == null ? "atalk" : name;
    }

    /**
     * Returns the package name of the application. That should be jitsi most of the time but who knows ..
     *
     * @return the package name of the application.
     */
    private String getPackageName() {
        String name = versionProperties.getProperty(PNAME_PACKAGE_NAME);
        return name == null ? "atalk" : name;
    }

    /**
     * Prints an error message and then prints the help message.
     *
     * @param arg the unknown argument we need to print
     */
    public void handleUnknownArg(String arg) {
        System.out.println("Unknown argument: " + arg);
        handleHelpArg();
    }

    /**
     * Prints a help message containing usage instructions and descriptions of
     * all options currently supported by Jitsi.
     */
    public void handleHelpArg() {
        handleVersionArg();

        System.out.println("Usage: " + getPackageName() + " [OPTIONS] [uri-to-call]");
        System.out.println("");
        System.out.println("  -c, --config=DIR  use DIR for config files");
        System.out.println("  -d, --debug       print debugging messages to stdout");
        System.out.println("  -h, --help        display this help message and exit");
        System.out.println("  -m, --multiple    do not ensure single instance");
        System.out.println("  -6, --ipv6        prefer IPv6 addresses where possible only");
        System.out.println("  -4, --ipv4        forces use of IPv4 only");
        System.out.println("  -v, --version     display the current version and exit");
        System.out.println("  -n, --notray      disable the tray icon and show the GUI");
    }

    /**
     * Returns an error code that could help identify an error when
     * <code>handleArgs</code> returns ACTION_ERROR or 0 if everything went fine.
     *
     * @return an error code that could help identify an error when
     * <code>handleArgs</code> returns ACTION_ERROR or 0 if everything went fine.
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the <code>delegationPeer</code> that would be handling all URIs passed
     * as command line arguments to SIP Communicator.
     *
     * @param delegationPeer the <code>delegationPeer</code> that should handle URIs
     * or <code>null</code> if we'd like to unset a previously set peer.
     */
    public void setDelegationPeer(ArgDelegationPeer delegationPeer) {
        this.argDelegator.setDelegationPeer(delegationPeer);
    }

    /**
     * Called when the user has tried to launch a second instance of
     * SIP Communicator while a first one was already running. This method
     * only handles arguments that need to be handled by a running instance
     * of SIP Communicator assuming that simple ones such as "--version" or
     * "--help" have been handled by the calling instance.
     *
     * @param args the args that we need to handle.
     */
    public void handleConcurrentInvocationRequestArgs(String[] args) {
        //if we have 1 or more args then we only care about the last one since
        //the only interinstance arg we currently know how to handle are URIs.
        //Change this if one day we implement fun stuff like inter instance command execution.
        if (args.length >= 1
                && !args[args.length - 1].startsWith("-")) {
            this.argDelegator.handleUri(args[args.length - 1]);
        }
        //otherwise, we simply notify SC of the request so that it could do stuff like showing the contact list for example.
        else {
            this.argDelegator.handleConcurrentInvocationRequest();
        }
    }
}
