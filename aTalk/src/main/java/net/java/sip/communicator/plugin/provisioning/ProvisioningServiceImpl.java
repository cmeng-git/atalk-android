package net.java.sip.communicator.plugin.provisioning;

import static org.atalk.android.gui.settings.SettingsFragment.P_KEY_LOCALE;

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

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.provisioning.ProvisioningService;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.OrderedProperties;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.impl.appversion.VersionActivator;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.httputil.OkHttpUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Provisioning service.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ProvisioningServiceImpl implements ProvisioningService {
    /**
     * Name of the property that contains the provisioning method (i.e. DHCP, DNS, manual, ...).
     */
    public static final String PROVISIONING_METHOD_PROP = "plugin.provisioning.METHOD";
    private static final String PROVISIONING_METHOD_DEFAULT = "plugin.provisioning.DEFAULT_PROVISIONING_METHOD";

    /**
     * Name of the provisioning URL in the configuration service.
     */
    public static final String PROVISIONING_URL_PROP = "plugin.provisioning.URL";
    private static final String PROVISIONING_URL_DEFAULT = "plugin.provisioning.DEFAULT_URI";

    /**
     * Name of the UUID property.
     */
    public static final String PROVISIONING_UUID_PROP = "net.java.sip.communicator.UUID";

    /**
     * Name of the provisioning username in the configuration service authentication).
     */
    public static final String PROVISIONING_USERNAME_PROP = "plugin.provisioning.USERNAME";

    /**
     * Name of the provisioning password in the configuration service (HTTP authentication).
     */
    public static final String PROVISIONING_PASSWORD_PROP = "plugin.provisioning.PASSWORD";

    /**
     * Name of the property, whether provisioning is mandatory.
     */
    private static final String PROVISIONING_MANDATORY_PROP = "plugin.provisioning.MANDATORY";

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
     * Prefix that can be used to indicate a property that will be set as a system property.
     */
    private static final String SYSTEM_PROP_PREFIX = "${system}.";

    private final ConfigurationService configService;

    /**
     * Constructor.
     */
    public ProvisioningServiceImpl() {
        configService = ProvisioningActivator.getConfigurationService();

        // check if UUID is already configured
        String uuid = (String) configService.getProperty(PROVISIONING_UUID_PROP);
        if (StringUtils.isEmpty(uuid)) {
            uuid = UUID.randomUUID().toString();
            configService.setProperty(PROVISIONING_UUID_PROP, uuid);
        }
    }

    /**
     * Starts provisioning.
     *
     * @param url provisioning URL
     */
    void start(String url) {
        /* try to see if provisioning URL is stored in properties */
        if (url == null) {
            url = getProvisioningUri();
        }

        if (StringUtils.isNotEmpty(url)) {
            InputStream data = retrieveConfigurationFile(url, null);
            if (data != null) {
                /*
                 * cmeng (20250619): do not store url, it may be the aTalk default
                 * store the provisioning URL in local configuration in case the provisioning
                 * discovery failed (DHCP/DNS unavailable, ...)
                 */
                // configService.setProperty(PROVISIONING_URL_PROP, url);
                updateConfiguration(data);
            }
        }
    }

    /**
     * Indicates if the provisioning has been enabled.
     *
     * @return <code>true</code> if the provisioning is enabled, <code>false</code> - otherwise
     */
    public String getProvisioningMethod() {
        String provMethod = configService.getString(PROVISIONING_METHOD_PROP);
        if (StringUtils.isEmpty(provMethod)) {
            provMethod = configService.getString(PROVISIONING_METHOD_DEFAULT);
            if (StringUtils.isNotEmpty(provMethod))
                setProvisioningMethod(provMethod);
        }
        return provMethod;
    }

    /**
     * Enables the provisioning with the given method. If the provisioningMethod is null disables the provisioning.
     *
     * @param provisioningMethod the provisioning method
     */
    public void setProvisioningMethod(String provisioningMethod) {
        configService.setProperty(PROVISIONING_METHOD_PROP, provisioningMethod);
    }

    /**
     * Returns the provisioning URI.
     *
     * @return the provisioning URI
     */
    @Override
    public String getProvisioningUri() {
        String provUri = configService.getString(PROVISIONING_URL_PROP);
        if (StringUtils.isEmpty(provUri)) {
            provUri = configService.getString(PROVISIONING_URL_DEFAULT);
        }
        return provUri;
    }

    /**
     * Sets the provisioning URI.
     *
     * @param uri the provisioning URI to set
     */
    public void setProvisioningUri(String uri) {
        configService.setProperty(PROVISIONING_URL_PROP, uri);
    }

    /**
     * Retrieve configuration file from provisioning URL. This method is blocking until
     * configuration file is retrieved from the network or if an exception happen
     *
     * @param url provisioning URL
     * @param jsonParams the already filled parameters if any.
     *
     * @return Stream of provisioning data
     */
    private InputStream retrieveConfigurationFile(String url, JSONObject jsonParams) {
        JSONObject jsonObject = new JSONObject();
        String username = null;
        String password = null;
        String pUrl = url; // URL before the "?" parameters
        String arg;
        String[] args = null;

        try {
            String host = new URL(url).getHost();
            InetAddress ipaddr = ProvisioningActivator.getNetworkAddressManagerService().getLocalHost(InetAddress.getByName(host));

            // Get any system environment identified by ${env.xyz}
            Pattern p = Pattern.compile("\\$\\{env\\.([^\\}]*)\\}");
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
            p = Pattern.compile("\\$\\{system\\.([^\\}]*)\\}");
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
                url = url.replace("${home.location}", configService.getScHomeDirLocation());
            }

            if (url.contains("${home.name}")) {
                url = url.replace("${home.name}", configService.getScHomeDirName());
            }

            if (url.contains("${uuid}")) {
                url = url.replace("${uuid}", (String) configService.getProperty(PROVISIONING_UUID_PROP));
            }

            if (url.contains("${osname}")) {
                url = url.replace("${osname}", System.getProperty("os.name"));
            }

            if (url.contains("${arch}")) {
                url = url.replace("${arch}", System.getProperty("os.arch"));
            }

            if (url.contains("${build}")) {
                url = url.replace("${build}", VersionActivator.getVersionService().getCurrentVersionName());
            }

            if (url.contains("${locale}")) {
                String locale = ConfigurationUtils.getProperty(P_KEY_LOCALE, "");
                url = url.replace("${locale}", locale);
            }

            if (url.contains("${ipaddr}")) {
                url = url.replace("${ipaddr}", ipaddr.getHostAddress());
            }

            if (url.contains("${hostname}")) {
                String name = ipaddr.getHostName();
                url = url.replace("${hostname}", name);
            }

            if (url.contains("${hwaddr}")) {
                if (ipaddr != null) {
                    /*
                     * Find the hardware address of the interface that has this IP address
                     * For android: Don't work with MAC addresses; always return null.
                     * See https://developer.android.com/identity/user-data-ids#mac-11-plus
                     */
                    Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                    while (en.hasMoreElements()) {
                        NetworkInterface iface = en.nextElement();

                        Enumeration<InetAddress> enInet = iface.getInetAddresses();
                        while (enInet.hasMoreElements()) {
                            InetAddress inet = enInet.nextElement();
                            // Strip off the zone info
                            InetAddress inetAddr = InetAddress.getByAddress(inet.getAddress());
                            if (inetAddr.equals(ipaddr)) {
                                byte[] bmac = iface.getHardwareAddress();
                                if (bmac == null || bmac.length == 0) {
                                    Timber.d("NetworkInterface MAC for %s (%s) is null", iface.getName(), inetAddr);
                                    url = url.replace("${hwaddr}", "NA");
                                }
                                else {
                                    String smac = String.format("%02x:%02x:%02x:%02x:%02x:%02x", bmac[0], bmac[1], bmac[2], bmac[3], bmac[4], bmac[5]);
                                    url = url.replace("${hwaddr}", smac);
                                }
                                break;
                            }
                        }
                    }
                }
            }

            if (url.contains("${username}")) {
                username = configService.getString(PROVISIONING_USERNAME_PROP);
                if (StringUtils.isNotEmpty(username))
                    url = url.replace("${username}", username);
            }

            if (url.contains("${password}")) {
                CredentialsStorageService credentialsService = ProvisioningActivator.getCredentialsStorageService();
                password = credentialsService.loadPassword(ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP);
                if (StringUtils.isNotEmpty(password))
                    url = url.replace("${password}", password);
            }

            if (url.contains("?")) {
                /*
                 * do not handle URL of type https://domain/index.php? (no parameters)
                 */
                if ((url.indexOf('?') + 1) != url.length()) {
                    arg = url.substring(url.indexOf('?') + 1);
                    args = arg.split("&");
                }
                pUrl = url.substring(0, url.indexOf('?'));
            }

            if (args != null) {
                for (String paramPair : args) {
                    String paramValue = null;

                    String[] params = paramPair.split("=");
                    String paramName = params[0];
                    if (params.length == 2) {
                        paramValue = params[1];
                    }
                    else {
                        Timber.d("Invalid provisioning request parameter: %s", paramPair);
                    }
                    jsonObject.put(paramName, paramValue);
                }
            }

            ResponseBody responseBody = null;
            String errorMsg = "";
            try {
                responseBody = OkHttpUtils.postForm(pUrl, jsonObject, username, password, null);
            } catch (IOException e) {
                errorMsg = e.getLocalizedMessage();
                Timber.e("Provisioning failed posting form: %s", errorMsg);
                aTalkApp.showToastMessage(R.string.provisioning_failed_message, errorMsg);
            }

            // if there was an error in retrieving, then stop
            // if canceled, lets check whether provisioning is mandatory
            if (responseBody == null) {
                if (configService.getBoolean(PROVISIONING_MANDATORY_PROP, false)) {
                    DialogActivity.showDialog(aTalkApp.getInstance(), R.string.provisioning_failed,
                            R.string.provisioning_failed_message, errorMsg);

                    // as shutdown service is not started and other bundles are scheduled to start, stop all of them
                    Bundle[] bundles = ProvisioningActivator.bundleContext.getBundles();
                    if (bundles != null && bundles.length != 0) {
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
            } else {
                return responseBody.byteStream();
            }
        } catch (Exception e) {
            String errMsg = aTalkApp.getResString(R.string.provisioning_failed_message, e.getMessage());
            Timber.d(errMsg);
            aTalkApp.showToastMessage(errMsg);
            return null;
        }
    }

    /**
     * Search param value for the supplied name.
     *
     * @param jsonObject the JSONOBject can be null.
     * @param paramName the name to search.
     *
     * @return the corresponding parameter value.
     */
    private static String getParamValue(JSONObject jsonObject, String paramName) {
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
    private void updateConfiguration(final InputStream data) {
        Properties fileProps = new OrderedProperties();

        try (InputStream in = new BufferedInputStream(data)) {
            fileProps.load(in);

            for (Map.Entry<Object, Object> entry : fileProps.entrySet()) {
                String key = ((String) entry.getKey()).trim();
                Object value = entry.getValue();

                // skip empty keys, prevent them going into the configuration
                if (key.isEmpty())
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
                configService.storeConfiguration();
                configService.reloadConfiguration();
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
     *
     * @return true if key is allowed, false otherwise
     */
    private boolean isPrefixAllowed(String key) {
        if (!allowedPrefixes.isEmpty()) {
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
    private void processProperty(String key, Object value) {
        if ((value instanceof String) && value.equals("${null}")) {
            configService.removeProperty(key);
        }
        else if (key.endsWith(".PASSWORD")) {
            /* password => credentials storage service */
            if ((value instanceof String))
                ProvisioningActivator.getCredentialsStorageService()
                        .storePassword(key.substring(0, key.lastIndexOf(".")), (String) value);

            Timber.i("%s = <password hidden>", key);
            return;
        }
        else if (key.startsWith(SYSTEM_PROP_PREFIX)) {
            String sysKey = key.substring(SYSTEM_PROP_PREFIX.length());
            if ((value instanceof String))
                System.setProperty(sysKey, (String) value);
        }
        else {
            configService.setProperty(key, value);
        }
        Timber.i("Set Property per Provisioning setting: %s = %s", key, value);
    }

    /**
     * Walk through all properties and make sure all properties keys match a specific set of
     * prefixes defined in configuration.
     *
     * @param enforcePrefix list of enforce prefix.
     */
    private void checkEnforcePrefix(String enforcePrefix) {
        if (enforcePrefix == null) {
            return;
        }
        /* must escape the | character */
        String[] prefixes = enforcePrefix.split("\\|");

        /* get all properties */
        for (String key : configService.getAllPropertyNames(enforcePrefix)) {
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
                configService.removeProperty(key);
            }
        }
    }
}
