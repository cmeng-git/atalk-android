/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import java.io.PrintStream;

/**
 * This class provides a PrintWriter implementation that we use to replace
 * System.out so that we could capture output from all libs or SC code that
 * uses calls to System.out.println();
 *
 * @author Emil Ivov
 */
public class ScStdOut extends PrintStream
{
    private static boolean stdOutPrintingEnabled = false;

    /**
     * This PrintStream contains System.out when the class were initiated.
     * Normally that would be the system default System.out
     */
    private PrintStream systemOut;

    public static void setStdOutPrintingEnabled(boolean enabled)
    {
        stdOutPrintingEnabled = enabled;
    }

    public ScStdOut(PrintStream printStream)
    {
        super(printStream);
        systemOut = System.out;
    }

    /**
     * Returns the default System.out <code>PrintStream</code> that was in use
     * before this class was instantiated.
     *
     * @return the original System.out PrintStream
     */
    public PrintStream getSystemOut()
    {
        return systemOut;
    }

    /**
     * Prints <code>string</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param string the <code>String</code> to print.
     */
    @Override
    public void print(String string)
    {
        if(stdOutPrintingEnabled)
            super.print(string);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>boolean</code> to print.
     */
    @Override
    public void println(boolean x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>char</code> to print.
     */
    @Override
    public void println(char x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>char[]</code> to print.
     */
    @Override
    public void println(char[] x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>double</code> to print.
     */
    @Override
    public void println(double x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>float</code> to print.
     */
    @Override
    public void println(float x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>int</code> to print.
     */
    @Override
    public void println(int x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>long</code> to print.
     */
    @Override
    public void println(long x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>Object</code> to print.
     */
    @Override
    public void println(Object x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>x</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param x the <code>String</code> to print.
     */
    @Override
    public void println(String x)
    {
        if(stdOutPrintingEnabled)
            super.println(x);
    }

    /**
     * Prints <code>b</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param b the <code>boolean</code> to print.
     */
    @Override
    public void print(boolean b)
    {
        if(stdOutPrintingEnabled)
            super.print(b);
    }

    /**
     * Prints <code>c</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param c the <code>char</code> to print.
     */
    @Override
    public void print(char c)
    {
        if(stdOutPrintingEnabled)
            super.print(c);
    }

    /**
     * Prints <code>s</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param s the <code>char[]</code> to print.
     */
    @Override
    public void print(char[] s)
    {
        if(stdOutPrintingEnabled)
            super.print(s);
    }

    /**
     * Prints <code>d</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param d the <code>double</code> to print.
     */
    @Override
    public void print(double d)
    {
        if(stdOutPrintingEnabled)
            super.print(d);
    }

    /**
     * Prints <code>f</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param f the <code>float</code> to print.
     */
    @Override
    public void print(float f)
    {
        if(stdOutPrintingEnabled)
            super.print(f);
    }

    /**
     * Prints <code>i</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param i the <code>int</code> to print.
     */
    @Override
    public void print(int i)
    {
        if(stdOutPrintingEnabled)
            super.print(i);
    }

    /**
     * Prints <code>l</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param l the <code>long</code> to print.
     */
    @Override
    public void print(long l)
    {
        if(stdOutPrintingEnabled)
            super.print(l);
    }

    /**
     * Prints <code>obj</code> if <code>stdOutPrintingEnabled</code> is enabled.
     *
     * @param obj the <code>Object</code> to print.
     */
    @Override
    public void print(Object obj)
    {
        if(stdOutPrintingEnabled)
            super.print(obj);
    }

    /**
     * Prints an empty line <code>stdOutPrintingEnabled</code> is enabled.
     */
    @Override
    public void println()
    {
        if(stdOutPrintingEnabled)
            super.println();
    }
}
