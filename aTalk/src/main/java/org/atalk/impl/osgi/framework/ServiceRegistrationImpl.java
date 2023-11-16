/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author Lyubomir Marinov
 */
public class ServiceRegistrationImpl implements ServiceRegistration<Object>
{
    private static final Comparator<String> CASE_INSENSITIVE_COMPARATOR = String::compareToIgnoreCase;
    private static final Map<String, Object> EMPTY_PROPERTIES = newCaseInsensitiveMapInstance();

    private final BundleImpl bundle;
    private final Long serviceId;
    private final String[] classNames;
    private final Object service;
    private final Map<String, Object> properties;
    private final ServiceReferenceImpl serviceReference = new ServiceReferenceImpl();

    public ServiceRegistrationImpl(BundleImpl bundle, long serviceId,
        String[] classNames, Object service, Dictionary<String, ?> properties)
    {
        this.bundle = bundle;
        this.serviceId = serviceId;
        this.classNames = classNames;
        this.service = service;

        if ((properties == null) || properties.isEmpty()) {
            this.properties = EMPTY_PROPERTIES;
        }
        else {
            Enumeration<String> keys = properties.keys();
            Map<String, Object> thisProperties = newCaseInsensitiveMapInstance();

            while (keys.hasMoreElements()) {
                String key = keys.nextElement();

                if (Constants.OBJECTCLASS.equalsIgnoreCase(key) || Constants.SERVICE_ID.equalsIgnoreCase(key))
                    continue;
                else if (thisProperties.containsKey(key))
                    throw new IllegalArgumentException(key);
                else
                    thisProperties.put(key, properties.get(key));
            }
            this.properties = thisProperties.isEmpty() ? EMPTY_PROPERTIES : thisProperties;
        }
    }

    @Override
    public ServiceReference<Object> getReference() {
        return serviceReference;
    }

//    @Override
//    public ServiceReferenceImpl getReference()
//    {
//        return serviceReference;
//    }

    public ServiceReference<?> getReference(Class<?> clazz)
    {
        return serviceReference;
    }

    private static Map<String, Object> newCaseInsensitiveMapInstance()
    {
        return new TreeMap<>(CASE_INSENSITIVE_COMPARATOR);
    }

    @Override
    public void setProperties(Dictionary properties)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void unregister()
    {
        bundle.getFramework().unregisterService(bundle, this);
    }

    class ServiceReferenceImpl implements ServiceReference<Object>
    {
        public int compareTo(Object other)
        {
            Long otherServiceId = ((ServiceRegistrationImpl) other).serviceId;
            return otherServiceId.compareTo(serviceId);
        }

        public Bundle getBundle()
        {
            return bundle;
        }

        public Object getProperty(String key)
        {
            Object value;
            if (Constants.OBJECTCLASS.equalsIgnoreCase(key))
                value = classNames;
            else if (Constants.SERVICE_ID.equalsIgnoreCase(key))
                value = serviceId;
            else
                synchronized (properties)
                {
                    value = properties.get(key);
                }
            return value;
        }

        public String[] getPropertyKeys()
        {
            synchronized (properties)
            {
                String[] keys = new String[2 + properties.size()];
                int index = 0;

                keys[index++] = Constants.OBJECTCLASS;
                keys[index++] = Constants.SERVICE_ID;

                for (String key : properties.keySet())
                    keys[index++] = key;
                return keys;
            }
        }

        Object getService()
        {
            return service;
        }

        public Bundle[] getUsingBundles()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean isAssignableTo(Bundle bundle, String className)
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Dictionary<String, Object> getProperties()
        {
            return null;
        }

        // for osgi 8.0.0
        @Override
        public <A> A adapt(Class<A> type) {
            return null;
        }
    }
}
