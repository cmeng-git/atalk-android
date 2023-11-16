/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In the fashion of <code>java.io.DeleteOnExitHook</code>, provides a way to delete
 * files when <code>Runtime.halt(int)</code> is to be invoked.
 *
 * @author Lyubomir Marinov
 */
public class DeleteOnHaltHook
{
    /**
     * The set of files to be deleted when <code>Runtime.halt(int)</code> is to be
     * invoked.
     */
    private static Set<String> files = new LinkedHashSet<String>();

    /**
     * Adds a file to the set of files to be deleted when
     * <code>Runtime.halt(int)</code> is to be invoked.
     *
     * @param file the name of the file to be deleted when
     * <code>Runtime.halt(int)</code> is to be invoked
     */
    public static synchronized void add(String file)
    {
        if (files == null)
            throw new IllegalStateException("Shutdown in progress.");
        else
            files.add(file);
    }

    /**
     * Deletes the files which have been registered for deletion when
     * <code>Runtime.halt(int)</code> is to be invoked.
     */
    public static void runHooks()
    {
        Set<String> files;

        synchronized (DeleteOnHaltHook.class)
        {
            files = DeleteOnHaltHook.files;
            DeleteOnHaltHook.files = null;
        }

        if (files != null)
        {
            List<String> toBeDeleted = new ArrayList<String>(files);

            Collections.reverse(toBeDeleted);
            for (String filename : toBeDeleted)
                new File(filename).delete();
        }
    }

    /** Prevents the initialization of <code>DeleteOnHaltHook</code> instances. */
    private DeleteOnHaltHook() {}
}
