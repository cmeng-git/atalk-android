package android.javax.naming.ldap;

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import android.javax.naming.InvalidNameException;
import android.javax.naming.Name;

import android.org.apache.harmony.jndi.internal.nls.Messages;
import android.org.apache.harmony.jndi.internal.parser.LdapNameParser;

/**
 * TODO: JavaDoc
 */
public class LdapName implements Name {

    private static final long serialVersionUID = -1595520034788997356L;

    private transient List<Rdn> rdns;

    private transient String rdnsStr;

    public LdapName(List<Rdn> rdns) {
        if (rdns == null) {
            throw new NullPointerException("rdns "
                    + Messages.getString("ldap.00"));
        }

        this.rdns = new ArrayList<Rdn>(rdns);
    }

    public LdapName(String name) throws InvalidNameException {
        rdnsStr = name;
        LdapNameParser parser = new LdapNameParser(rdnsStr);

        this.rdns = parser.getList();
    }

    public Name add(int posn, Rdn comp) {
        if (comp == null) {
            throw new NullPointerException("comp "
                    + Messages.getString("ldap.00"));
        }

        if (posn < 0 || posn > rdns.size()) {
            throw new IndexOutOfBoundsException(Messages.getString("ldap.05"));
        }

        rdns.add(posn, comp);
        rdnsStr = null;
        return this;
    }

    public Name add(int posn, String comp) throws InvalidNameException {
        return add(posn, new Rdn(comp));
    }

    public Name add(Rdn comp) {
        return add(rdns.size(), comp);
    }

    public Name add(String comp) throws InvalidNameException {
        return add(rdns.size(), comp);
    }

    public Name addAll(int posn, List<Rdn> suffixRdns) {
        if (suffixRdns == null) {
            throw new NullPointerException("suffixRdns "
                    + Messages.getString("ldap.00"));
        }

        if (posn < 0 || posn > rdns.size()) {
            throw new IndexOutOfBoundsException(Messages.getString("ldap.00"));
        }

        rdns.addAll(posn, suffixRdns);
        rdnsStr = null;
        return this;
    }

    public Name addAll(int posn, Name suffix) throws InvalidNameException {
        if (suffix instanceof LdapName) {
            return addAll(posn, ((LdapName) suffix).rdns);
        }
        List<Rdn> rdns = new ArrayList<Rdn>();
        for (Enumeration<?> iter = suffix.getAll(); iter.hasMoreElements();) {
            rdns.add(new Rdn((String) iter.nextElement()));
        }
        return addAll(posn, rdns);
    }

    public Name addAll(List<Rdn> suffixRdns) {
        return addAll(rdns.size(), suffixRdns);
    }

    public Name addAll(Name suffix) throws InvalidNameException {
        return addAll(rdns.size(), suffix);
    }

    public Object clone() {
        try {
            if (rdnsStr != null) {
                return new LdapName(rdnsStr);
            }
        } catch (InvalidNameException e) {
        }
        List<Rdn> lista = new ArrayList<Rdn>();
        for (int i = 0; i < rdns.size(); i++) {
            lista.add(rdns.get(i));
        }
        return new LdapName(lista);
    }

    public int compareTo(Object obj) {
        if (obj == null || !(obj instanceof LdapName)) {
            throw new ClassCastException("obj " + Messages.getString("ldap.01"));
        }

        LdapName ln = (LdapName) obj;

        Iterator<?> iter = rdns.iterator();
        Iterator<?> iter2 = ln.rdns.iterator();

        while (iter.hasNext() && iter2.hasNext()) {
            int c = iter.next().toString().toLowerCase().compareTo(
                    iter2.next().toString().toLowerCase());

            if (c != 0) {
                return c;
            }
        }

        if (iter.hasNext())
            return 1;
        if (iter2.hasNext())
            return -1;

        return 0;
    }

    public boolean endsWith(List<Rdn> rdns) {
        try {
            Iterator<?> iter = rdns.iterator();
            Iterator<?> iter2 = ((LdapName) getSuffix(this.rdns.size()
                    - rdns.size())).rdns.iterator();

            while (iter.hasNext()) {
                if (!((Rdn) iter.next()).equals(iter2.next())) {
                    return false;
                }
            }

            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean endsWith(Name n) {
        try {
            return n.equals(getSuffix(rdns.size() - n.size()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof LdapName)) {
            return false;
        }

        LdapName ln = (LdapName) obj;

        if (ln.rdns.size() != rdns.size()) {
            return false;
        }

        Iterator<Rdn> iter = ln.rdns.iterator();
        Iterator<Rdn> iter2 = rdns.iterator();
        while (iter.hasNext()) {
            if (!iter.next().equals(iter2.next())) {
                return false;
            }
        }
        return true;
    }

    public String get(int posn) {
        return getRdn(posn).toString();
    }

    public Enumeration<String> getAll() {
        final Iterator<Rdn> rdns = getRdns().iterator();

        return new Enumeration<String>() {

            public boolean hasMoreElements() {
                return rdns.hasNext();
            }

            public String nextElement() {
                return rdns.next().toString();
            }
        };
    }

    public Name getPrefix(int posn) {
        if (posn < 0) {
            throw new IndexOutOfBoundsException(Messages.getString("ldap.02"));
        }
        return new LdapName(rdns.subList(0, posn));
    }

    public Rdn getRdn(int posn) {
        return rdns.get(posn);
    }

    public List<Rdn> getRdns() {
        return rdns;
    }

    public Name getSuffix(int posn) {
        if (posn > rdns.size()) {
            throw new IndexOutOfBoundsException(Messages.getString("ldap.02"));
        }

        return new LdapName(rdns.subList(posn, rdns.size()));
    }

    public int hashCode() {
        int sum = 0;
        for (Iterator<?> iter = rdns.iterator(); iter.hasNext();) {
            sum += iter.next().hashCode();
        }
        return sum;
    }

    public boolean isEmpty() {
        return rdns.size() == 0;
    }

    public Object remove(int posn) throws InvalidNameException {
        rdnsStr = null;
        return rdns.remove(posn).toString();
    }

    public int size() {
        return rdns.size();
    }

    public boolean startsWith(List<Rdn> rdns) {
        try {
            Iterator<?> iter = rdns.iterator();
            Iterator<?> iter2 = ((LdapName) getPrefix(rdns.size())).rdns
                    .iterator();

            while (iter.hasNext()) {
                if (!((Rdn) iter.next()).equals(iter2.next())) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean startsWith(Name n) {
        try {
            return n.equals(getPrefix(n.size()));
        } catch (RuntimeException e) {
            return false;
        }

    }

    public String toString() {
        if (rdnsStr != null) {
            return rdnsStr;
        }
        if (rdns.size() == 0) {
            return ""; //$NON-NLS-1$
        }

        StringBuilder sb = new StringBuilder();
        sb.append(rdns.get(rdns.size() - 1).toString());
        for (int i = rdns.size() - 2; i >= 0; i--) {
            sb.append(',');
            sb.append(rdns.get(i).toString());
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream ois) throws IOException,
            ClassNotFoundException, InvalidNameException {
        ois.defaultReadObject();
        LdapNameParser parser = new LdapNameParser((String) ois.readObject());
        this.rdns = parser.getList();
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeObject(this.toString());
    }

}