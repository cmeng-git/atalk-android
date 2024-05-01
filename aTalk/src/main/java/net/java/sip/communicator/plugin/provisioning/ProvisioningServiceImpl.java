package net.java.sip.communicator.plugin.provisioning;

import net.java.sip.communicator.service.provisioning.ProvisioningService;
import net.java.sip.communicator.util.OrderedProperties;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.httputil.HttpUtils;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.OSUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Provisioning service.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ProvisioningServiceImpl implements ProvisioningService
{
    /**
     * Name of the UUID property.
     */
    public static final String PROVISIONING_UUID_PROP = "net.java.sip.communicator.UUID";

    /**
     * Name of the provisioning URL in the configuration service.
     */
    private static final String PROPERTY_PROVISIONING_URL = "provisioning.URL";

    /**
     * Name of the provisioning username in the configuration service authentication).
     */
    static final String PROPERTY_PROVISIONING_USERNAME = "provisioning.auth.USERNAME";

    /**
     * Name of the provisioning password in the configuration service (HTTP authentication).
     */
    static final String PROPERTY_PROVISIONING_PASSWORD = "provisioning.auth";

    /**
     * Name of the property that contains the provisioning method (i.e. DHCP, DNS, manual, ...).
     */
    private static final String PROVISIONING_METHOD_PROP = "provisioning.METHOD";

    /**
     * Name of the property, whether provisioning is mandatory.
     */
    private static final String PROPERTY_PROVISIONING_MANDATORY = "provisioning.MANDATORY";

    /**
     * Name of the property that contains enforce prefix list (separated by pipe) for the provisioning.
     * The retrieved configuration properties will be checked against these prefixes to avoid having
     * incorrect content in the configuration file (such as HTML content resulting of HTTP error).
     */
    private static final String PROVISIONING_ALLOW_PREFIX_PROP = "provisioning.ALLOW_PREFIX";

    /**
     * Name of the enforce prefix property.
     */
    private static final String PROVISIONING_ENFORCE_PREFIX_PROP = "provisioning.ENFORCE_PREFIX";

    /**
     * List of allowed configuration prefixes.
     */
    private final List<String> allowedPrefixes = new ArrayList<>();

    /**
     * Authentication username.
     */
    private static String provUsername = null;

    /**
     * Authentication password.
     */
    private static String provPassword = null;

    /**
     * Prefix that can be used to indicate a property that will be set as a system property.
     */
    private static final String SYSTEM_PROP_PREFIX = "${system}.";

    /**
     * Constructor.
     */
    public ProvisioningServiceImpl()
    {
        // check if UUID is already configured
        String uuid = (String) ProvisioningActivator.getConfigurationService().getProperty(PROVISIONING_UUID_PROP);

        if (uuid == null || uuid.equals("")) {
            uuid = UUID.randomUUID().toString();
            ProvisioningActivator.getConfigurationService().setProperty(PROVISIONING_UUID_PROP, uuid);
        }
    }

    /**
     * Starts provisioning.
     *
     * @param url provisioning URL
     */
    void start(String url)
    {
        if (url == null) {
            /* try to see if provisioning URL is stored in properties */
            url = getProvisioningUri();
        }

        if (StringUtils.isNotEmpty(url)) {
            InputStream data = retrieveConfigurationFile(url);

            if (data != null) {
                /*
                 * store the provisioning URL in local configuration in case the provisioning
                 * discovery failed (DHCP/DNS unavailable, ...)
                 */
                ProvisioningActivator.getConfigurationService().setProperty(PROPERTY_PROVISIONING_URL, url);
                updateConfiguration(data);
            }
        }
    }

    /**
     * Indicates if the provisioning has been enabled.
     *
     * @return <code>true</code> if the provisioning is enabled, <code>false</code> - otherwise
     */
    public String getProvisioningMethod()
    {
        String provMethod = ProvisioningActivator.getConfigurationService().getString(PROVISIONING_METHOD_PROP);

        if (provMethod == null || provMethod.length() <= 0) {
            provMethod = ProvisioningActivator.getResourceService()
                    .getSettingsString("plugin.provisioning.DEFAULT_PROVISIONING_METHOD");

            if (provMethod != null && provMethod.length() > 0)
                setProvisioningMethod(provMethod);
        }
        return provMethod;
    }

    /**
     * Enables the provisioning with the given method. If the provisioningMethod is null disables the provisioning.
     *
     * @param provisioningMethod the provisioning method
     */
    public void setProvisioningMethod(String provisioningMethod)
    {
        ProvisioningActivator.getConfigurationService().setProperty(PROVISIONING_METHOD_PROP, provisioningMethod);
    }

    /**
     * Returns the provisioning URI.
     *
     * @return the provisioning URI
     */
    public String getProvisioningUri()
    {
        String provUri = ProvisioningActivator.getConfigurationService().getString(PROPERTY_PROVISIONING_URL);
        if (provUri == null || provUri.length() <= 0) {
            provUri = ProvisioningActivator.getResourceService()
                    .getSettingsString("plugin.provisioning.DEFAULT_PROVISIONING_URI");

            if (provUri != null && provUri.length() > 0)
                setProvisioningUri(provUri);
        }
        return provUri;
    }

    /**
     * Sets the provisioning URI.
     *
     * @param uri the provisioning URI to set
     */
    public void setProvisioningUri(String uri)
    {
        ProvisioningActivator.getConfigurationService().setProperty(PROPERTY_PROVISIONING_URL, uri);
    }

    /**
     * Returns provisioning username.
     *
     * @return provisioning username
     */
    public String getProvisioningUsername()
    {
        return provUsername;
    }

    /**
     * Returns provisioning password.
     *
     * @return provisioning password
     */
    public String getProvisioningPassword()
    {
        return provPassword;
    }

    /**
     * Retrieve configuration file from provisioning URL. This method is blocking until
     * configuration file is retrieved from the network or if an exception happen
     *
     * @param url provisioning URL
     * @return Stream of provisioning data
     */
    private InputStream retrieveConfigurationFile(String url)
    {
        return retrieveConfigurationFile(url, null);
    }

    /**
     * Retrieve configuration file from provisioning URL. This method is blocking until
     * configuration file is retrieved from the network or if an exception happen
     *
     * @param url provisioning URL
     * @param jsonParameters the already filled parameters if any.
     * @return Stream of provisioning data
     */
    private InputStream retrieveConfigurationFile(String url, JSONObject jsonParameters)
    {
        try {
            String arg;
            String[] args = null;

            InetAddress ipaddr = ProvisioningActivator.getNetworkAddressManagerService()
                    .getLocalHost(InetAddress.getByName(new URL(url).getHost()));

            // Get any system environment identified by ${env.xyz}
            Pattern p = Pattern.compile("\\$\\{env\\.([^}]*)}");
            Matcher m = p.matcher(url);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String value = System.getenv(m.group(1));
                if (value != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value));
                }
            }
            m.appendTail(sb);
            url = sb.toString();

            // Get any system property variable identified by ${system.xyz}
            p = Pattern.compile("\\$\\{system\\.([^}]*)}");
            m = p.matcher(url);
            sb = new StringBuffer();
            while (m.find()) {
                String value = System.getProperty(m.group(1));
                if (value != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value));
                }
            }
            m.appendTail(sb);
            url = sb.toString();

            if (url.contains("${home.location}")) {
                url = url.replace("${home.location}",
                        ProvisioningActivator.getConfigurationService().getScHomeDirLocation());
            }

            if (url.contains("${home.name}")) {
                url = url.replace("${home.name}",
                        ProvisioningActivator.getConfigurationService().getScHomeDirName());
            }

            if (url.contains("${uuid}")) {
                url = url.replace("${uuid}",
                        (String) ProvisioningActivator.getConfigurationService().getProperty(PROVISIONING_UUID_PROP));
            }

            if (url.contains("${osname}")) {
                url = url.replace("${osname}", System.getProperty("os.name"));
            }

            if (url.contains("${arch}")) {
                url = url.replace("${arch}", System.getProperty("os.arch"));
            }

//            if (url.indexOf("${resx}") != -1 || url.indexOf("${resy}") != -1) {
//                Rectangle screen = ScreenInformation.getScreenBounds();
//
//                if (url.indexOf("${resx}") != -1) {
//                    url = url.replace("${resx}", String.valueOf(screen.width));
//                }
//
//                if (url.indexOf("${resy}") != -1) {
//                    url = url.replace("${resy}", String.valueOf(screen.height));
//                }
//            }

            if (url.contains("${build}")) {
                url = url.replace("${build}", System.getProperty("sip-communicator.version"));
            }

            if (url.contains("${locale}")) {
                String locale = ProvisioningActivator.getConfigurationService()
                        .getString(ResourceManagementService.DEFAULT_LOCALE_CONFIG);
                if (locale == null)
                    locale = "";

                url = url.replace("${locale}", locale);
            }

            if (url.contains("${ipaddr}")) {
                url = url.replace("${ipaddr}", ipaddr.getHostAddress());
            }

            if (url.contains("${hostname}")) {
                String name;
                if (OSUtils.IS_WINDOWS) {
                    // avoid reverse DNS lookup
                    name = System.getenv("COMPUTERNAME");
                }
                else {
                    name = ipaddr.getHostName();
                }
                url = url.replace("${hostname}", name);
            }

            if (url.contains("${hwaddr}")) {
                if (ipaddr != null) {
                    /*
                     * find the hardware address of the interface that has this IP address
                     */
                    Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                    while (en.hasMoreElements()) {
                        NetworkInterface iface = en.nextElement();

                        Enumeration<InetAddress> enInet = iface.getInetAddresses();
                        while (enInet.hasMoreElements()) {
                            InetAddress inet = enInet.nextElement();

                            if (inet.equals(ipaddr)) {
                                byte[] hw = ProvisioningActivator.getNetworkAddressManagerService().getHardwareAddress(iface);
                                if (hw == null || hw.length == 0)
                                    continue;

                                StringBuilder buf = new StringBuilder();
                                for (byte h : hw) {
                                    int hi = h >= 0 ? h : h + 256;
                                    String t = (hi <= 0xf) ? "0" : "";
                                    t += Integer.toHexString(hi);
                                    buf.append(t);
                                    buf.append(":");
                                }
                                buf.deleteCharAt(buf.length() - 1);
                                url = url.replace("${hwaddr}", buf.toString());
                                break;
                            }
                        }
                    }
                }
            }

            if (url.contains("?")) {
                /*
                 * do not handle URL of type http://domain/index.php? (no parameters)
                 */
                if ((url.indexOf('?') + 1) != url.length()) {
                    arg = url.substring(url.indexOf('?') + 1);
                    args = arg.split("&");
                }
                url = url.substring(0, url.indexOf('?'));
            }

            String username = null;
            String password = null;
            JSONObject jsonBody = new JSONObject();

            if (args != null && args.length > 0) {
                String usernameParam = "${username}";
                String passwordParam = "${password}";

                for (String param : args) {
                    String paramValue = "";

                    String[] params = param.split("=");
                    String paramName = params[0];
                    if (params.length == 2) {
                        paramValue = params[1];
                    }

                    // pre loaded value we will reuse.
                    String preloadedParamValue = getParamValue(jsonParameters, paramName);

                    // If we find the username or password parameter at this stage we replace it
                    // with an empty string. or if we have an already filled value we will reuse it.
                    if (param.contains(usernameParam)) {
                        username = (preloadedParamValue != null) ? preloadedParamValue : paramValue;
                        continue;
                    }
                    else if (param.contains(passwordParam)) {
                        password = (preloadedParamValue != null) ? preloadedParamValue : paramValue;
                        continue;
                    }
                    jsonBody.put(paramName, paramValue);
                }
            }

            HttpUtils.HTTPResponseResult res = null;
            IOException errorWhileProvisioning = null;
            try {
                res = HttpUtils.postForm(url, PROPERTY_PROVISIONING_USERNAME, PROPERTY_PROVISIONING_PASSWORD,
                        jsonBody, username, password, null, null);
            } catch (IOException e) {
                Timber.e("Error posting form: %s", e.getMessage());
                errorWhileProvisioning = e;
            }

            // if there was an error in retrieving stop
            // if canceled, lets check whether provisioning is mandatory
            if (res == null) {
                if (ProvisioningActivator.getConfigurationService().getBoolean(PROPERTY_PROVISIONING_MANDATORY, false)) {
                    String errorMsg;
                    if (errorWhileProvisioning != null)
                        errorMsg = errorWhileProvisioning.getLocalizedMessage();
                    else
                        errorMsg = "";

                    DialogActivity.showDialog(aTalkApp.getInstance(), R.string.provisioning_failed,
                            R.string.provisioning_failed_message, errorMsg);

                    // as shutdown service is not started and other bundles are scheduled to start, stop all of them
                    {
                        for (Bundle b : ProvisioningActivator.bundleContext.getBundles()) {
                            try {
                                // skip our Bundle avoiding stopping us while starting and NPE in felix
                                if (ProvisioningActivator.bundleContext.equals(b.getBundleContext())) {
                                    continue;
                                }
                                b.stop();
                            } catch (BundleException ex) {
                                Timber.e(ex, "Failed to being gentle stop %s", b.getLocation());
                            }
                        }
                    }
                }
                // stop processing
                return null;
            }

            String[] userPass = res.getCredentials();
            if (userPass[0] != null && userPass[1] != null) {
                provUsername = userPass[0];
                provPassword = userPass[1];
            }
            InputStream in = res.getContent();

            // Skips ProgressMonitorInputStream wrapper on Android
            if (!OSUtils.IS_ANDROID) {
                // Chain a ProgressMonitorInputStream to the URLConnection's InputStream
//				final ProgressMonitorInputStream pin;
//				pin = new ProgressMonitorInputStream(null, u.toString(), in);
//
//				// Set the maximum value of the ProgressMonitor
//				ProgressMonitor pm = pin.getProgressMonitor();
//				pm.setMaximum((int) res.getContentLength());

                // Uses ProgressMonitorInputStream if available
                return null; //pin;
            }

            return in;
        } catch (Exception e) {
            Timber.i(e, "Error retrieving provisioning file!");
            return null;
        }
    }

    /**
     * Search param value for the supplied name.
     *
     * @param jsonObject the JSONOBject can be null.
     * @param paramName the name to search.
     * @return the corresponding parameter value.
     */
    private static String getParamValue(JSONObject jsonObject, String paramName)
    {
        if (jsonObject == null || paramName == null)
            return null;

        try {
            return jsonObject.get(paramName).toString();
        } catch (JSONException e) {
            Timber.e("JSONObject exception: %s", e.getMessage());
        }
        return null;
    }

    /**
     * Update configuration with properties retrieved from provisioning URL.
     *
     * @param data Provisioning data
     */
    private void updateConfiguration(final InputStream data)
    {
        Properties fileProps = new OrderedProperties();
        try (InputStream in = new BufferedInputStream(data)) {
            fileProps.load(in);

            for (Map.Entry<Object, Object> entry : fileProps.entrySet()) {
                String key = (String) entry.getKey();
                Object value = entry.getValue();

                // skip empty keys, prevent them going into the configuration
                if (key.trim().length() == 0)
                    continue;

                if (key.equals(PROVISIONING_ALLOW_PREFIX_PROP)) {
                    String[] prefixes = ((String) value).split("\\|");

                    /* updates allowed prefixes list */
                    Collections.addAll(allowedPrefixes, prefixes);
                    continue;
                }
                else if (key.equals(PROVISIONING_ENFORCE_PREFIX_PROP)) {
                    checkEnforcePrefix((String) value);
                    continue;
                }

                /* check that properties is allowed */
                if (!isPrefixAllowed(key)) {
                    continue;
                }
                processProperty(key, value);
            }

            try {
                /* save and reload the "new" configuration */
                ProvisioningActivator.getConfigurationService().storeConfiguration();
                ProvisioningActivator.getConfigurationService().reloadConfiguration();
            } catch (Exception e) {
                Timber.e("Cannot reload configuration");
            }
        } catch (IOException e) {
            Timber.w("Error during load of provisioning file");
        }
    }

    /**
     * Check if a property name belongs to the allowed prefixes.
     *
     * @param key property key name
     * @return true if key is allowed, false otherwise
     */
    private boolean isPrefixAllowed(String key)
    {
        if (allowedPrefixes.size() > 0) {
            for (String s : allowedPrefixes) {
                if (key.startsWith(s)) {
                    return true;
                }
            }
            /* current property prefix is not allowed */
            return false;
        }
        else {
            /* no allowed prefixes configured so key is valid by default */
            return true;
        }
    }

    /**
     * Process a new property. If value equals "${null}", it means to remove the property in the
     * configuration service. If the key name end with "PASSWORD", its value is encrypted through
     * credentials storage service, otherwise the property is added/updated in the configuration service.
     *
     * @param key property key name
     * @param value property value
     */
    private void processProperty(String key, Object value)
    {
        if ((value instanceof String) && value.equals("${null}")) {
            ProvisioningActivator.getConfigurationService().removeProperty(key);
        }
        else if (key.endsWith(".PASSWORD")) {
            /* password => credentials storage service */
            ProvisioningActivator.getCredentialsStorageService()
                    .storePassword(key.substring(0, key.lastIndexOf(".")), (String) value);

            Timber.i("%s = <password hidden>", key);
            return;
        }
        else if (key.startsWith(SYSTEM_PROP_PREFIX)) {
            String sysKey = key.substring(SYSTEM_PROP_PREFIX.length(), key.length());
            System.setProperty(sysKey, (String) value);
        }
        else {
            ProvisioningActivator.getConfigurationService().setProperty(key, value);
        }
        Timber.i("%s = %s", key, value);
    }

    /**
     * Walk through all properties and make sure all properties keys match a specific set of
     * prefixes defined in configuration.
     *
     * @param enforcePrefix list of enforce prefix.
     */
    private void checkEnforcePrefix(String enforcePrefix)
    {
        ConfigurationService config = ProvisioningActivator.getConfigurationService();
        if (enforcePrefix == null) {
            return;
        }
        /* must escape the | character */
        String[] prefixes = enforcePrefix.split("\\|");

        /* get all properties */
        for (String key : config.getAllPropertyNames(enforcePrefix)) {
            boolean isValid = false;

            for (String k : prefixes) {
                if (key.startsWith(k)) {
                    isValid = true;
                    break;
                }
            }
            /*
             * property name does is not in the enforce prefix list so remove it
             */
            if (!isValid) {
                config.removeProperty(key);
            }
        }
    }
}
