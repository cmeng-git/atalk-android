/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.JabberActivator;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.protocol.event.AccountManagerEvent;
import net.java.sip.communicator.service.protocol.event.AccountManagerListener;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.OSGiService;
import org.bouncycastle.util.encoders.Base64;
import org.osgi.framework.*;

import java.util.*;

import timber.log.Timber;

/**
 * Represents an implementation of <tt>AccountManager</tt> which loads the accounts in a separate thread.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class AccountManager
{
    /**
     * The delay in milliseconds the background <tt>Thread</tt> loading the stored accounts should wait
     * before dying so that it doesn't get recreated for each <tt>ProtocolProviderFactory</tt> registration.
     */
    private static final long LOAD_STORED_ACCOUNTS_TIMEOUT = 30000;

    /**
     * The <tt>BundleContext</tt> this service is registered in.
     */
    private final BundleContext bundleContext;

    private final ConfigurationService configurationService;

    /**
     * The <tt>AccountManagerListener</tt>s currently interested in the events fired by this manager.
     */
    private final List<AccountManagerListener> listeners = new LinkedList<>();

    /**
     * The queue of <tt>ProtocolProviderFactory</tt> services awaiting their stored accounts to be loaded.
     */
    private final Queue<ProtocolProviderFactory> loadStoredAccountsQueue = new LinkedList<>();

    /**
     * The <tt>Thread</tt> loading the stored accounts of the <tt>ProtocolProviderFactory</tt>
     * services waiting in {@link #loadStoredAccountsQueue}.
     */
    private Thread loadStoredAccountsThread;

    /**
     * The list of <tt>AccountID</tt>s, corresponding to all stored accounts.
     */
    private final Vector<AccountID> storedAccounts = new Vector<>();

    /**
     * aTalk backend SQLite database
     */
    private final DatabaseBackend databaseBackend;

    /**
     * Initializes a new <tt>AccountManagerImpl</tt> instance loaded in a specific
     * <tt>BundleContext</tt> (in which the caller will usually later register it).
     *
     * @param bundleContext the <tt>BundleContext</tt> in which the new instance is loaded (and in which the
     * caller will usually later register it as a service)
     */
    public AccountManager(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
        Context context = ServiceUtils.getService(bundleContext, OSGiService.class);
        databaseBackend = DatabaseBackend.getInstance(context);
        configurationService = ProtocolProviderActivator.getConfigurationService();

        this.bundleContext.addServiceListener(AccountManager.this::serviceChanged);
    }

    /**
     * Implements AccountManager#addListener(AccountManagerListener).
     *
     * @param listener the <tt>AccountManagerListener</tt> to add
     */
    public void addListener(AccountManagerListener listener)
    {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Loads all the accounts stored for a specific <tt>ProtocolProviderFactory</tt>.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> to load the stored accounts of
     */
    private void doLoadStoredAccounts(ProtocolProviderFactory factory)
    {
        List<AccountID> accountIDs = databaseBackend.getAccounts(factory);
        Timber.d("Found %s %s accounts", accountIDs.size(), factory.getProtocolName());

        CredentialsStorageService credentialsStorage
                = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);

        for (AccountID accountID : accountIDs) {
            Timber.d("Loading account %s", accountID.getAccountJid());
            synchronized (storedAccounts) {
                storedAccounts.add(accountID);
            }

            if (accountID.isEnabled()) {
                // Decode passwords.
                if (!credentialsStorage.isStoredEncrypted(accountID.getAccountUuid())) {
                    String B64EncodedPwd = accountID.getAccountPropertyString("ENCRYPTOED_PASSWORD");
                    if (!TextUtils.isEmpty(B64EncodedPwd)) {
                        /*
                         * Converting byte[] to String using the platform's default charset
                         * may result in an invalid password.
                         */
                        String decryptedPassword = new String(Base64.decode(B64EncodedPwd));
                        accountID.setPassword(decryptedPassword);
                    }
                }
                factory.loadAccount(accountID);
            }
        }
    }

    /**
     * Notifies the registered {@link #listeners} that the stored accounts of a specific
     * <tt>ProtocolProviderFactory</tt> have just been loaded.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> which had its stored accounts just loaded
     */
    private void fireStoredAccountsLoaded(ProtocolProviderFactory factory)
    {
        AccountManagerListener[] listeners;
        synchronized (this.listeners) {
            listeners = this.listeners.toArray(new AccountManagerListener[0]);
        }

        int listenerCount = listeners.length;
        if (listenerCount > 0) {
            AccountManagerEvent event = new AccountManagerEvent(this,
                    AccountManagerEvent.STORED_ACCOUNTS_LOADED, factory);

            for (AccountManagerListener listener : listeners) {
                listener.handleAccountManagerEvent(event);
            }
        }
    }

    /**
     * Returns the package name of the <tt>factory</tt>.
     *
     * @param factory the factory which package will be returned.
     * @return the package name of the <tt>factory</tt>.
     */
    public String getFactoryImplPackageName(ProtocolProviderFactory factory)
    {
        String className = factory.getClass().getName();
        return className.substring(0, className.lastIndexOf('.'));
    }

    /**
     * Check for stored accounts for the supplied <tt>protocolName</tt>.
     *
     * @param protocolName the protocol name to check for
     * @param includeHidden whether to include hidden providers
     * @return <tt>true</tt> if there is any account stored in configuration service with
     * <tt>protocolName</tt>, <tt>false</tt> otherwise.
     */
    public boolean hasStoredAccounts(String protocolName, boolean includeHidden)
    {
        return hasStoredAccount(protocolName, includeHidden, null);
    }

    /**
     * Checks whether a stored account with <tt>userID</tt> is stored in configuration.
     *
     * @param protocolName the protocol name
     * @param includeHidden whether to check hidden providers
     * @param userID the user id to check.
     * @return <tt>true</tt> if there is any account stored in configuration service with
     * <tt>protocolName</tt> and <tt>userID</tt>, <tt>false</tt> otherwise.
     */
    public boolean hasStoredAccount(String protocolName, boolean includeHidden, String userID)
    {
        boolean hasStoredAccount = false;
        Map<String, String> accounts;
        ServiceReference[] factoryRefs;

        try {
            factoryRefs = this.bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            Timber.e(ex, "Failed to retrieve the registered ProtocolProviderFactories");
            return false;
        }

        if ((factoryRefs != null) && (factoryRefs.length > 0)) {
            for (ServiceReference<ProtocolProviderFactory> factoryRef : factoryRefs) {
                ProtocolProviderFactory factory = bundleContext.getService(factoryRef);

                accounts = getStoredAccounts(factory);
                if ((protocolName == null) || (protocolName.equals(factory.getProtocolName()))) {
                    for (String key : accounts.keySet()) {
                        String accountUuid = accounts.get(key);

                        boolean hidden = false;
                        String accountUserID = key.split(":")[1];
                        if (!includeHidden || (userID != null)) {
                            hidden = configurationService.getBoolean(accountUuid + "."
                                    + ProtocolProviderFactory.IS_PROTOCOL_HIDDEN, false);
                        }
                        if (includeHidden || !hidden) {
                            if ((userID == null) || (userID.equals(accountUserID))) {
                                hasStoredAccount = true;
                                break;
                            }
                        }
                    }
                    if (hasStoredAccount || (protocolName != null)) {
                        break;
                    }
                }
            }
        }
        return hasStoredAccount;
    }

    /**
     * Searches for stored account with <tt>uid</tt> in stored configuration. The <tt>uid</tt> is
     * the one generated when creating accounts with prefix <tt>ACCOUNT_UID_PREFIX</tt>.
     *
     * @return <tt>AccountID</tt> if there is any account stored in configuration service with
     * <tt>uid</tt>, <tt>null</tt> otherwise.
     */
    public AccountID findAccountID(String uid)
    {
        Map<String, String> accounts;
        ServiceReference[] factoryRefs;

        try {
            factoryRefs = this.bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            Timber.e(ex, "Failed to retrieve the registered ProtocolProviderFactories");
            return null;
        }

        if ((factoryRefs != null) && (factoryRefs.length > 0)) {
            for (ServiceReference<ProtocolProviderFactory> factoryRef : factoryRefs) {
                ProtocolProviderFactory factory = bundleContext.getService(factoryRef);
                accounts = getStoredAccounts(factory);

                for (String accountUID : accounts.keySet()) {
                    if (uid.equals(accounts.get(accountUID))) {
                        for (AccountID acc : storedAccounts) {
                            if (acc.getAccountUniqueID().equals(accountUID)) {
                                return acc;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Loads the accounts stored for a specific <tt>ProtocolProviderFactory</tt> and notifies
     * the registered {@link #listeners} that the stored accounts of the specified <tt>factory</tt>
     * have just been loaded
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> to load the stored accounts of
     */

    private void loadStoredAccounts(ProtocolProviderFactory factory)
    {
        doLoadStoredAccounts(factory);
        fireStoredAccountsLoaded(factory);
    }

    /**
     * Notifies this manager that a specific <tt>ProtocolProviderFactory</tt> has been
     * registered as a service. The current implementation queues the specified <tt>factory</tt>
     * to have its stored accounts as soon as possible.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> which has been registered as a service.
     */
    private void protocolProviderFactoryRegistered(ProtocolProviderFactory factory)
    {
        queueLoadStoredAccounts(factory);
    }

    /**
     * Queues a specific <tt>ProtocolProviderFactory</tt> to have its stored accounts loaded as
     * soon as possible.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> to be queued for loading its stored accounts as
     * soon as possible
     */
    private void queueLoadStoredAccounts(ProtocolProviderFactory factory)
    {
        synchronized (loadStoredAccountsQueue) {
            loadStoredAccountsQueue.add(factory);
            loadStoredAccountsQueue.notifyAll();

            if (loadStoredAccountsThread == null) {
                loadStoredAccountsThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        runInLoadStoredAccountsThread();
                    }
                };
                loadStoredAccountsThread.setDaemon(true);
                loadStoredAccountsThread.setName("AccountManager.loadStoredAccounts");
                loadStoredAccountsThread.start();
            }
        }
    }

    /**
     * Implements AccountManager#removeListener(AccountManagerListener).
     *
     * @param listener the <tt>AccountManagerListener</tt> to remove
     */
    public void removeListener(AccountManagerListener listener)
    {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Running in {@link #loadStoredAccountsThread}, loads the stored accounts of the
     * <tt>ProtocolProviderFactory</tt> services waiting in {@link #loadStoredAccountsQueue}
     */
    private void runInLoadStoredAccountsThread()
    {
        boolean interrupted = false;
        while (!interrupted) {
            try {
                ProtocolProviderFactory factory;
                synchronized (loadStoredAccountsQueue) {
                    factory = loadStoredAccountsQueue.poll();
                    if (factory == null) {
                        /*
                         * Technically, we should be handing spurious wakeup. However, we cannot
                         * check the condition in a queue. Anyway, we just want to keep this Thread
                         * alive long enough to allow it to not be re-created multiple times and
                         * not handing a spurious wakeup will just cause such an inconvenience.
                         */
                        try {
                            loadStoredAccountsQueue.wait(LOAD_STORED_ACCOUNTS_TIMEOUT);
                        } catch (InterruptedException ex) {
                            Timber.w(ex, "The loading of the stored accounts has been interrupted");
                            interrupted = true;
                            synchronized (this.loadStoredAccountsQueue) {
                                if ((!interrupted) && (this.loadStoredAccountsQueue.size() <= 0)) {
                                    if (this.loadStoredAccountsThread == Thread.currentThread()) {
                                        this.loadStoredAccountsThread = null;
                                        this.loadStoredAccountsQueue.notifyAll();
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        factory = loadStoredAccountsQueue.poll();
                    }
                    if (factory != null) {
                        loadStoredAccountsQueue.notifyAll();
                    }
                }
                if (factory != null) {
                    try {
                        loadStoredAccounts(factory);
                    } catch (Exception ex) {

                        /*
                         * Swallow the exception in order to prevent a single factory from halting
                         * the loading of subsequent factories.
                         */
                        Timber.e(ex, "Failed to load accounts for %s", factory);
                    }
                }

                synchronized (this.loadStoredAccountsQueue) {
                    if ((!interrupted) && (this.loadStoredAccountsQueue.size() <= 0)) {
                        if (this.loadStoredAccountsThread == Thread.currentThread()) {
                            loadStoredAccountsThread = null;
                            loadStoredAccountsQueue.notifyAll();
                        }
                        break;
                    }
                }
            } finally {
                synchronized (loadStoredAccountsQueue) {
                    if (!interrupted && (loadStoredAccountsQueue.size() <= 0)) {
                        if (loadStoredAccountsThread == Thread.currentThread()) {
                            loadStoredAccountsThread = null;
                            loadStoredAccountsQueue.notifyAll();
                        }
                    }
                }
            }
        }
    }

    /**
     * Notifies this manager that an OSGi service has changed. The current implementation tracks
     * the registrations of <tt>ProtocolProviderFactory</tt> services in order to queue them for
     * loading their stored accounts.
     *
     * @param serviceEvent the <tt>ServiceEvent</tt> containing the event data
     */
    private void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            Object service = bundleContext.getService(serviceEvent.getServiceReference());

            if (service instanceof ProtocolProviderFactory) {
                protocolProviderFactoryRegistered((ProtocolProviderFactory) service);
            }
        }
    }

    /**
     * Stores an account represented in the form of an <tt>AccountID</tt> created by a specific
     * <tt>ProtocolProviderFactory</tt>.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> which created the account to be stored
     * @param accountID the account in the form of <tt>AccountID</tt> to be stored
     * @throws OperationFailedException if anything goes wrong while storing the account
     */
    public void storeAccount(ProtocolProviderFactory factory, AccountID accountID)
            throws OperationFailedException
    {
        Map<String, Object> configurationProperties = new HashMap<>();
        synchronized (storedAccounts) {
            if (!storedAccounts.contains(accountID))
                storedAccounts.add(accountID);
        }

        // Check to check if this is an existing stored account; else need to create the new
        // account in table before storing other account Properties
        String accountUid = accountID.getAccountUniqueID();
        String accountUuid = getStoredAccountUUID(factory, accountUid);
        if (accountUuid == null) {
            accountUuid = accountID.getAccountUuid();
            databaseBackend.createAccount(accountID);
        }

        // store the rest of the properties
        Map<String, String> accountProperties = accountID.getAccountProperties();
        for (Map.Entry<String, String> entry : accountProperties.entrySet()) {
            String property = entry.getKey();
            String value = entry.getValue();

            // Properties already stored in table AccountID.TABLE_NAME; so skip
            if (property.equals(ProtocolProviderFactory.ACCOUNT_UUID)
                    || property.equals(ProtocolProviderFactory.PROTOCOL)
                    || property.equals(ProtocolProviderFactory.USER_ID)
                    || property.equals(ProtocolProviderFactory.ACCOUNT_UID)
                    || property.equals(ProtocolProviderFactory.PASSWORD)
                    || property.equals("ENCRYPTED_PASSWORD")) {
                continue;
            }
            else {
                configurationProperties.put(accountUuid + "." + property, value);
            }
        }

        /*
         * Account modification can request password delete and only if it's not stored already in encrypted form.
         * Account registration object clears this property in order to forget the password
         * If password persistent is set and password is not null, then store password securely. Otherwise purge it.
         */
        CredentialsStorageService credentialsStorage = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);
        if (credentialsStorage != null) {
            if (accountID.isPasswordPersistent()) {
                String password = accountProperties.get(ProtocolProviderFactory.PASSWORD);
                credentialsStorage.storePassword(accountUuid, password);
            }
            else {
                credentialsStorage.removePassword(accountUuid);
            }
        }
        else {
            throw new OperationFailedException("CredentialsStorageService failed to storePassword",
                    OperationFailedException.GENERAL_ERROR);
        }

        // Save all the account configurationProperties into the database
        if (configurationProperties.size() > 0)
            configurationService.setProperties(configurationProperties);

        Timber.d("Stored account for id %s", accountUid);
    }

    /**
     * Modify accountID table with the new AccountID parameters e.g. user changes the userID
     *
     * @param accountID the account in the form of <tt>AccountID</tt> to be modified
     */
    public void modifyAccountId(AccountID accountID)
    {
        databaseBackend.createAccount(accountID);
    }

    /**
     * Gets account node name under which account configuration properties are stored.
     *
     * @param factory account's protocol provider factory
     * @param accountUID account for which the prefix will be returned
     * @return configuration prefix for given <tt>accountID</tt> if exists or <tt>null</tt> otherwise
     */
    public String getStoredAccountUUID(ProtocolProviderFactory factory, String accountUID)
    {
        Map<String, String> accounts;
        accounts = getStoredAccounts(factory);
        if (accounts.containsKey(accountUID))
            return accounts.get(accountUID);
        else
            return null;
    }

    /**
     * Removes the account with <tt>accountID</tt> from the set of accounts that are persistently
     * stored inside the configuration service.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt> which created the account to be stored
     * @param accountID the AccountID of the account to remove.
     * @return true if an account has been removed and false otherwise.
     */
    public boolean removeStoredAccount(ProtocolProviderFactory factory, AccountID accountID)
    {
        synchronized (storedAccounts) {
            storedAccounts.remove(accountID);
        }
        /*
         * We're already doing it in #unloadAccount(AccountID) - we're figuring out the
         * ProtocolProviderFactory by the AccountID.
         */
        if (factory == null) {
            factory = ProtocolProviderActivator.getProtocolProviderFactory(accountID.getProtocolName());
        }
        // null means account has been removed.
        return (getStoredAccountUUID(factory, accountID.getAccountUniqueID()) == null);
    }

    /**
     * Removes all accounts which have been persistently stored.
     *
     * @see #removeStoredAccount(ProtocolProviderFactory, AccountID)
     */
    public void removeStoredAccounts()
    {
        synchronized (loadStoredAccountsQueue) {
            /*
             * Wait for the Thread which loads the stored account to complete so that we can be
             * sure later on that it will not load a stored account while we are deleting it or
             * another one for that matter.
             */
            boolean interrupted = false;
            while (loadStoredAccountsThread != null) {
                try {
                    loadStoredAccountsQueue.wait(LOAD_STORED_ACCOUNTS_TIMEOUT);
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            synchronized (this.storedAccounts) {
                AccountID[] storedAccounts = this.storedAccounts.toArray(new AccountID[0]);

                for (AccountID storedAccount : storedAccounts) {
                    ProtocolProviderFactory ppf
                            = ProtocolProviderActivator.getProtocolProviderFactory(storedAccount.getProtocolName());

                    if (ppf != null) {
                        ppf.uninstallAccount(storedAccount);
                    }
                }
            }
        }
    }

    /**
     * Returns an <tt>Iterator</tt> over a list of all stored <tt>AccountID</tt>s. The list of
     * stored accounts include all registered accounts and all disabled accounts. In other words in
     * this list we could find accounts that aren't loaded.
     * <p>
     * In order to check if an account is already loaded please use the #isAccountLoaded(AccountID
     * accountID) method. To load an account use the #loadAccount(AccountID accountID) method.
     *
     * @return an <tt>Iterator</tt> over a list of all stored <tt>AccountID</tt>s
     */
    public Collection<AccountID> getStoredAccounts()
    {
        synchronized (storedAccounts) {
            return new Vector<>(storedAccounts);
        }
    }

    /**
     * Loads the account corresponding to the given <tt>AccountID</tt>. An account is loaded when
     * its <tt>ProtocolProviderService</tt> is registered in the bundle context. This method is
     * meant to load the account through the corresponding <tt>ProtocolProviderFactory</tt> .
     *
     * @param accountID the identifier of the account to load
     * @throws OperationFailedException if anything goes wrong while loading the account corresponding to the specified
     * <tt>accountID</tt>
     */
    public void loadAccount(AccountID accountID)
            throws OperationFailedException
    {
        // If the account with the given id is already loaded we have nothing to do here.
        if (isAccountLoaded(accountID)) {
            Timber.w("Account is already loaded: %s", accountID);
            return;
        }

        ProtocolProviderFactory providerFactory
                = ProtocolProviderActivator.getProtocolProviderFactory(accountID.getProtocolName());

        if (providerFactory.loadAccount(accountID)) {
            accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, String.valueOf(false));

            // must retrieve password before store the modified properties;
            // otherwise password become null if it was not login on app launch.
            String password = JabberActivator.getProtocolProviderFactory().loadPassword(accountID);
            accountID.putAccountProperty(ProtocolProviderFactory.PASSWORD, password);
            storeAccount(providerFactory, accountID);
        }
        else {
            Timber.w("Account was not loaded: %s ", accountID);
        }
    }

    /**
     * Unloads the account corresponding to the given <tt>AccountID</tt>. An account is unloaded
     * when its <tt>ProtocolProviderService</tt> is unregistered in the bundle context. This method
     * is meant to unload the account through the corresponding <tt>ProtocolProviderFactory</tt>.
     *
     * @param accountID the identifier of the account to load
     * @throws OperationFailedException if anything goes wrong while unloading the account corresponding
     * to the specified <tt>accountID</tt>
     */
    public void unloadAccount(AccountID accountID)
            throws OperationFailedException
    {
        // If the account with the given id is already unloaded we have nothing to do here.
        if (!isAccountLoaded(accountID))
            return;

        // Obtain the protocol provider.
        ProtocolProviderFactory providerFactory
                = ProtocolProviderActivator.getProtocolProviderFactory(accountID.getProtocolName());
        ServiceReference<ProtocolProviderService> serRef = providerFactory.getProviderForAccount(accountID);

        // If there's no such provider we have nothing to do here.
        if (serRef == null) {
            return;
        }

        ProtocolProviderService protocolProvider = bundleContext.getService(serRef);
        // Set the account icon path for unloaded accounts.
        String iconPathProperty = accountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_ICON_PATH);

        if (iconPathProperty == null) {
            accountID.putAccountProperty(ProtocolProviderFactory.ACCOUNT_ICON_PATH,
                    protocolProvider.getProtocolIcon().getIconPath(ProtocolIcon.ICON_SIZE_32x32));
        }

        accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, String.valueOf(true));
        if (!providerFactory.unloadAccount(accountID)) {
            accountID.putAccountProperty(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, String.valueOf(false));
        }

        // must retrieve password before store the modified properties;
        // otherwise password may become null if it was never login on before unload.
        String password = JabberActivator.getProtocolProviderFactory().loadPassword(accountID);
        accountID.putAccountProperty(ProtocolProviderFactory.PASSWORD, password);

        // Finally store the modified properties.
        storeAccount(providerFactory, accountID);
    }

    /**
     * Checks if the account corresponding to the given <tt>accountID</tt> is loaded. An account is
     * loaded if its <tt>ProtocolProviderService</tt> is registered in the bundle context. By
     * default all accounts are loaded. However the user could manually unload an account, which
     * would be unregistered from the bundle context, but would remain in the configuration file.
     *
     * @param accountID the identifier of the account to load
     * @return <tt>true</tt> to indicate that the account with the given <tt>accountID</tt> is
     * loaded, <tt>false</tt> - otherwise
     */
    public boolean isAccountLoaded(AccountID accountID)
    {
        return storedAccounts.contains(accountID) && accountID.isEnabled();
    }

    private String stripPackagePrefix(String property)
    {
        int packageEndIndex = property.lastIndexOf('.');

        if (packageEndIndex != -1) {
            property = property.substring(packageEndIndex + 1);
        }
        return property;
    }

    public Map<String, String> getStoredAccounts(ProtocolProviderFactory factory)
    {
        Map<String, String> accounts = new Hashtable<>();
        SQLiteDatabase mDB = databaseBackend.getReadableDatabase();
        String[] args = {factory.getProtocolName()};

        Cursor cursor = mDB.query(AccountID.TABLE_NAME, null, AccountID.PROTOCOL + "=?",
                args, null, null, null);
        while (cursor.moveToNext()) {
            accounts.put(cursor.getString(cursor.getColumnIndex(AccountID.ACCOUNT_UID)),
                    cursor.getString(cursor.getColumnIndex(AccountID.ACCOUNT_UUID)));
        }
        cursor.close();
        return accounts;
    }
}
