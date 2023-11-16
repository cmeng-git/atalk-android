/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto;

import android.text.TextUtils;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.params.KeyParameter;

import java.lang.reflect.Constructor;
import java.security.Provider;
import java.util.Random;

import timber.log.Timber;

/**
 * Implements a factory for an AES <code>BlockCipher</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class Aes
{
    /**
     * The block size in bytes of the AES algorithm (implemented by the <code>BlockCipher</code>s
     * initialized by the <code>Aes</code> class).
     */
    private static final int BLOCK_SIZE = 16;

    /**
     * The simple name of the <code>BlockCipherFactory</code> class/interface which is used as a class
     * name suffix by the well-known <code>BlockCipherFactory</code> implementations.
     */
    private static final String BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME = "BlockCipherFactory";

    /**
     * The <code>BlockCipherFactory</code> implemented with BouncyCastle. It is the well-known fallback.
     */
    private static final BlockCipherFactory BOUNCYCASTLE_FACTORY = new BouncyCastleBlockCipherFactory();

    /**
     * The <code>BlockCipherFactory</code> implementations known to the <code>Aes</code>
     * class among which the fastest is to be elected as {@link #factory}.
     */
    private static BlockCipherFactory[] factories;

    /**
     * The <code>BlockCipherFactory</code> implementation which is (to be) used by the class
     * <code>Aes</code> to initialize <code>BlockCipher</code>s.
     */
    private static BlockCipherFactory factory;

    /**
     * The name of the class to instantiate as a <code>BlockCipherFactory</code>
     * implementation to be used by the class <code>Aes</code> to initialize <code>BlockCipher</code>s.
     */
    private static String FACTORY_CLASS_NAME = null;

    /**
     * The <code>Class</code>es of the well-known <code>BlockCipherFactory</code> implementations.
     */
    private static final Class<?>[] FACTORY_CLASSES = {
            BouncyCastleBlockCipherFactory.class,
            SunJCEBlockCipherFactory.class,
            SunPKCS11BlockCipherFactory.class
    };

    /**
     * The number of milliseconds after which the benchmark which elected {@link #factory} is to be
     * considered expired.
     */
    public static final long FACTORY_TIMEOUT = 60 * 1000;

    /**
     * The class to instantiate as a <code>BlockCipherFactory</code> implementation to be used to
     * initialized <code>BlockCipher</code>s.
     *
     * @see #FACTORY_CLASS_NAME
     */
    private static Class<? extends BlockCipherFactory> factoryClass;

    /**
     * The time in milliseconds at which {@link #factories} were benchMarked and {@link #factory} was elected.
     */
    private static long factoryTimestamp;

    /**
     * The input buffer to be used for the benchmarking of {@link #factories}. It consists of blocks
     * and its length specifies the number of blocks to process for the purposes of the benchmark.
     */
    private static final byte[] in = new byte[BLOCK_SIZE * 1024];

    /**
     * The output buffer to be used for the benchmarking of {@link #factories}.
     */
    private static final byte[] out = new byte[BLOCK_SIZE];

    /**
     * The random number generator which generates keys and inputs for the benchmarking of the
     * <code>BlockCipherFactory</code> implementations.
     */
    private static final Random random = new Random();

    /**
     * Set the class to use as the factory class for AES cryptography.
     *
     * @param name the name of the class
     */
    public static synchronized void setFactoryClassName(String name)
    {
        FACTORY_CLASS_NAME = name;
        factoryClass = null;
    }

    /**
     * Benchmarks a specific array/list of <code>BlockCipherFactory</code> instances
     * and returns the fastest-performing element.
     *
     * @param factories the <code>BlockCipherFactory</code> instances to benchmark
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return the fastest-performing <code>BlockCipherFactory</code> among the specified <code>factories</code>
     */
    private static BlockCipherFactory benchmark(BlockCipherFactory[] factories, int keySize)
    {
        Random random = Aes.random;
        byte[] key = new byte[keySize];
        byte[] in = Aes.in;

        random.nextBytes(key);
        random.nextBytes(in);

        CipherParameters params = new KeyParameter(key);
        int blockSize = BLOCK_SIZE;
        int inEnd = in.length - blockSize + 1;
        byte[] out = Aes.out;
        long minTime = Long.MAX_VALUE;
        BlockCipherFactory minFactory = null;

        // Log information for the purposes of debugging.
        StringBuilder log = new StringBuilder();

        for (int f = 0; f < factories.length; ++f) {
            BlockCipherFactory factory = factories[f];
            if (factory == null)
                continue;

            try {
                BlockCipher cipher = factory.createBlockCipher(keySize);
                if (cipher == null) {
                    // The BlockCipherFactory failed to initialize a new
                    // BlockCipher instance. We will not use it again because
                    // the failure may persist.
                    factories[f] = null;
                }
                else {
                    cipher.init(true, params);
                    long startTime = System.nanoTime();

                    for (int inOff = 0; inOff < inEnd; inOff = inOff + blockSize) {
                        cipher.processBlock(in, inOff, out, 0);
                    }
                    // We do not invoke the method BlockCipher.reset() so we do
                    // not need to take it into account in the benchmark.

                    long endTime = System.nanoTime();
                    long time = endTime - startTime;

                    if (time < minTime) {
                        minTime = time;
                        minFactory = factory;
                    }

                    if (log.length() != 0)
                        log.append(", ");

                    log.append(getSimpleClassName(factory))
                            .append(' ')
                            .append(time);
                }
            } catch (Throwable t) {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }

        if (log.length() != 0) {
            Timber.i("AES benchmark (of execution times expressed in nanoseconds): %s", log);
        }
        return minFactory;
    }

    /**
     * Initializes a new <code>BlockCipher</code> instance which implements Advanced Encryption Standard (AES).
     *
     * @param keySize length of the AES key (16, 24, 32 bytes)
     * @return a new <code>BlockCipher</code> instance which implements Advanced Encryption Standard (AES)
     */
    public static BlockCipher createBlockCipher(int keySize)
    {
        BlockCipherFactory factory;

        synchronized (Aes.class) {
            long now = System.currentTimeMillis();

            factory = Aes.factory;
            if ((factory != null) && (now > factoryTimestamp + FACTORY_TIMEOUT))
                factory = null;
            if (factory == null) {
                try {
                    factory = getBlockCipherFactory(keySize);
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    else if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    else {
                        Timber.w("Failed to initialize an optimized AES implementation: %s", t.getLocalizedMessage());
                    }
                } finally {
                    if (factory == null) {
                        factory = Aes.factory;
                        if (factory == null)
                            factory = BOUNCYCASTLE_FACTORY;
                    }

                    Aes.factoryTimestamp = now;
                    if (Aes.factory != factory) {
                        Aes.factory = factory;
                        // Simplify the name of the BlockCipherFactory class to
                        // be employed for the purposes of brevity and ease.
                        Timber.i("Will employ AES implemented by %s", getSimpleClassName(Aes.factory));
                    }
                }
            }
        }

        try {
            return factory.createBlockCipher(keySize);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException) ex;
            else
                throw new RuntimeException(ex);
        }
    }

    private static String getEffectiveFactoryClassName()
    {
        String factoryClassName = FACTORY_CLASS_NAME;

        if ((factoryClassName == null) || (factoryClassName.length() == 0)) {
            return null;
        }
        // Support specifying FACTORY_CLASS_NAME without a package and
        // without BlockCipherFactory at the end for the purposes of brevity and ease.
        if (Character.isUpperCase(factoryClassName.charAt(0))
                && !factoryClassName.contains(".")
                && !factoryClassName.endsWith(BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME)) {
            factoryClassName = Aes.class.getName() + "$" + factoryClassName + BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME;
        }
        return factoryClassName;
    }

    /**
     * Initializes the <code>BlockCipherFactory</code> instances to be benchmarked by the class
     * <code>Aes</code> and among which the fastest-performing one is to be selected.
     *
     * @return the <code>BlockCipherFactory</code> instances to be benchmarked by the class <code>AES</code>
     * and among which the fastest-performing one is to be selected
     */
    @SuppressWarnings("unchecked")
    private static BlockCipherFactory[] createBlockCipherFactories()
    {
        // The user may have specified a specific BlockCipherFactory class
        // (name) through setFactoryClassName(String). Practically, the specified FACTORY_CLASS_NAME
        // will override all other FACTORY_CLASSES and, consequently, it does
        // not seem necessary to try FACTORY_CLASSES at all. Technically though,
        // the specified BlockCipherFactory may malfunction. That is why all
        // FACTORY_CLASSES are tried as well and FACTORY_CLASS_NAME is selected
        // later on after it has proven itself functional.
        Class<? extends BlockCipherFactory> factoryClass = Aes.factoryClass;
        Class<?>[] factoryClasses = FACTORY_CLASSES;
        boolean add = true;

        if (factoryClass == null) {
            String factoryClassName = getEffectiveFactoryClassName();

            if (factoryClassName != null) {
                // Is the specified FACTORY_CLASS_NAME one of the well-known
                // FACTORY_CLASSES? If it is, then we do not have to invoke the
                // method Class.forName(String) and add a new Class to FACTORY_CLASSES.
                for (Class<?> clazz : factoryClasses) {
                    if ((clazz != null)
                            && clazz.getName().equals(factoryClassName)
                            && BlockCipherFactory.class.isAssignableFrom(clazz)) {
                        Aes.factoryClass = factoryClass = (Class<? extends BlockCipherFactory>) clazz;
                        add = false;
                        break;
                    }
                }

                // If FACTORY_CLASS_NAME does not specify a well-known Class, find and load the Class.
                if (add) {
                    try {
                        Class<?> clazz = Class.forName(factoryClassName);

                        if (BlockCipherFactory.class.isAssignableFrom(clazz)) {
                            Aes.factoryClass = factoryClass = (Class<? extends BlockCipherFactory>) clazz;
                        }
                    } catch (Throwable t) {
                        if (t instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        else if (t instanceof ThreadDeath) {
                            throw (ThreadDeath) t;
                        }
                        else {
                            Timber.w("Failed to employ class %s as an AES implementation: %s",
                                    factoryClassName, t.getLocalizedMessage());
                        }
                    }
                }
            }
        }

        // If FACTORY_CLASS_NAME does not specify a well-known Class, add the new Class to FACTORY_CLASSES.
        if (add && (factoryClass != null)) {
            for (Class<?> clazz : factoryClasses) {
                if (factoryClass.equals(clazz)) {
                    add = false;
                    break;
                }
            }
            if (add) {
                Class<?>[] newFactoryClasses = new Class<?>[1 + factoryClasses.length];

                newFactoryClasses[0] = factoryClass;
                System.arraycopy(factoryClasses, 0, newFactoryClasses, 1, factoryClasses.length);
                factoryClasses = newFactoryClasses;
            }
        }
        return createBlockCipherFactories(factoryClasses);
    }

    /**
     * Initializes <code>BlockCipherFactory</code> instances of specific <code>Class</code>es.
     *
     * @param classes the runtime <code>Class</code>es to instantiate
     * @return the <code>BlockCipherFactory</code> instances initialized by the specified <code>classes</code>
     */
    private static BlockCipherFactory[] createBlockCipherFactories(Class<?>[] classes)
    {
        BlockCipherFactory[] factories = new BlockCipherFactory[classes.length];
        int i = 0;

        for (Class<?> clazz : classes) {
            try {
                if (BlockCipherFactory.class.isAssignableFrom(clazz)) {
                    BlockCipherFactory factory;

                    if (BouncyCastleBlockCipherFactory.class.equals(clazz))
                        factory = BOUNCYCASTLE_FACTORY;
                    else
                        factory = (BlockCipherFactory) clazz.newInstance();

                    factories[i++] = factory;
                }
            } catch (Throwable t) {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }
        return factories;
    }

    /**
     * Gets a <code>BlockCipherFactory</code> instance to be used by the <code>AES</code> class to
     * initialize <code>BlockCipher</code>s.
     *
     * Benchmarks the well-known <code>BlockCipherFactory</code> implementations and returns the fastest one.
     *
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return a <code>BlockCipherFactory</code> instance to be used by the
     * <code>Aes</code> class to initialize <code>BlockCipher</code>s
     */
    private static BlockCipherFactory getBlockCipherFactory(int keySize)
    {
        BlockCipherFactory[] factories = Aes.factories;

        if (factories == null) {
            // A single instance of each well-known BlockCipherFactory
            // implementation will be initialized i.e. the attempt to initialize
            // BlockCipherFactory instances will be made once only.
            Aes.factories = factories = createBlockCipherFactories();
        }

        // Benchmark the BlockCiphers provided by the available
        // BlockCipherFactories in order to select the fastest-performing BlockCipherFactory.
        BlockCipherFactory minFactory = benchmark(factories, keySize);

        // The user may have specified a specific BlockCipherFactory class
        // (name) through setFactoryClassName(String), Practically, FACTORY_CLASS_NAME may override
        // minFactory and, consequently, it may appear that the benchmark is
        // unnecessary. Technically though, the specified BlockCipherFactory may
        // malfunction. That is why FACTORY_CLASS_NAME is selected after it has proven itself functional.
        {
            Class<? extends BlockCipherFactory> factoryClass = Aes.factoryClass;

            if (factoryClass != null) {
                for (BlockCipherFactory factory : factories) {
                    if ((factory != null) && factory.getClass().equals(factoryClass)) {
                        minFactory = factory;
                        break;
                    }
                }
            }
        }
        return minFactory;
    }

    /**
     * Gets the simple name of the runtime <code>Class</code> of a specific <code>BlockCipherFactory</code>
     * to be used for display purposes of brevity and readability.
     *
     * @param factory the <code>BlockCipherFactory</code> for which a simple class name is to be returned
     * @return the simple name of the runtime <code>Class</code> of the specified <code>factory</code> to be
     * used for display purposes of brevity and readability
     */
    private static String getSimpleClassName(BlockCipherFactory factory)
    {
        Class<?> clazz = factory.getClass();
        String className = clazz.getSimpleName();

        if (TextUtils.isEmpty(className))
            className = clazz.getName();

        String suffix = BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME;

        if (className.endsWith(suffix)) {
            String simpleClassName = className.substring(0, className.length() - suffix.length());
            String prefix = Aes.class.getName() + "$";

            if (simpleClassName.startsWith(prefix)) {
                className = simpleClassName.substring(prefix.length());
            }
            else if (simpleClassName.contains(".")) {
                Package pkg = Aes.class.getPackage();

                if (pkg != null) {
                    prefix = pkg.getName() + ".";
                    if (simpleClassName.startsWith(prefix))
                        className = simpleClassName.substring(prefix.length());
                }
            }
            else {
                className = simpleClassName;
            }
        }
        return className;
    }

    /**
     * Implements <code>BlockCipherFactory</code> using BouncyCastle.
     */
    public static class BouncyCastleBlockCipherFactory implements BlockCipherFactory
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public BlockCipher createBlockCipher(int keySize)
                throws Exception
        {
            // The value of keySize can be ignored for BouncyCastle, it
            // determines the AES algorithm to be used with the KeyParameter.
            return new AESEngine();
        }
    }

    /**
     * Implements <code>BlockCipherFactory</code> using Sun JCE.
     */
    public static class SunJCEBlockCipherFactory extends SecurityProviderBlockCipherFactory
    {
        /**
         * Initializes a new <code>SunJCEBlockCipherFactory</code> instance.
         */
        public SunJCEBlockCipherFactory()
        {
            super("AES_<size>/ECB/NoPadding", "SunJCE");
        }
    }

    /**
     * Implements <code>BlockCipherFactory</code> using Sun PKCS#11.
     */
    public static class SunPKCS11BlockCipherFactory extends SecurityProviderBlockCipherFactory
    {
        /**
         * The <code>java.security.Provider</code> instance (to be) employed for an (optimized) AES implementation.
         */
        private static Provider provider;

        /**
         * The indicator which determines whether {@link #provider} is to be used. If <code>true</code>,
         * an attempt will be made to initialize a <code>java.security.Provider</code> instance. If the
         * attempt fails, <code>false</code> will be assigned in order to not repeatedly attempt the
         * initialization which is known to have failed.
         */
        private static boolean useProvider = true;

        /**
         * Gets the <code>java.security.Provider</code> instance (to be) employed for an (optimized) AES implementation.
         *
         * @return the <code>java.security.Provider</code> instance (to be) employed for an (optimized) AES implementation.
         */
        private static synchronized Provider getProvider()
                throws Exception
        {
            Provider provider = SunPKCS11BlockCipherFactory.provider;

            if ((provider == null) && useProvider) {
                try {
                    Class<?> clazz = Class.forName("sun.security.pkcs11.SunPKCS11");

                    if (Provider.class.isAssignableFrom(clazz)) {
                        Constructor<?> constructor = clazz.getConstructor(String.class);

                        // The SunPKCS11 Config name should be unique in order
                        // to avoid repeated initialization exceptions.
                        String name = null;
                        Package pkg = Aes.class.getPackage();

                        if (pkg != null)
                            name = pkg.getName();
                        if (name == null || name.length() == 0)
                            name = "org.atalk.impl.neomedia.transform.srtp";

                        provider = (Provider) constructor.newInstance("--name=" + name + "\\n"
                                + "nssDbMode=noDb\\n" + "attributes=compatibility");
                    }
                } finally {
                    if (provider == null)
                        useProvider = false;
                    else
                        SunPKCS11BlockCipherFactory.provider = provider;
                }
            }
            return provider;
        }

        /**
         * Initializes a new <code>SunPKCS11BlockCipherFactory</code> instance.
         *
         * @throws Exception if anything goes wrong while initializing a new <code>SunPKCS11BlockCipherFactory</code> instance
         */
        public SunPKCS11BlockCipherFactory()
                throws Exception
        {
            super("AES_<size>/ECB/NoPadding", getProvider());
        }
    }
}
