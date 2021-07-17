package net.java.sip.communicator.plugin.desktoputil;

import org.atalk.android.util.javax.swing.JDialog;

public class SIPCommDialog extends JDialog
{
    /*
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Indicates if the size and location of this dialog are stored after closing.
     */
    private boolean isSaveSizeAndLocation = true;

    public SIPCommDialog()
    {
        super();
        this.init();
    }

    public SIPCommDialog(boolean isSaveSizeAndLocation)
    {
        this();
        this.isSaveSizeAndLocation = isSaveSizeAndLocation;
    }


    public void setTitle(String paramString)
    {
    }


    public void dispose()
    {

    }

    private void init()
    {
    }

    public void setVisible(boolean isVisible)
    {
        super.setVisible(isVisible);
    }

}
