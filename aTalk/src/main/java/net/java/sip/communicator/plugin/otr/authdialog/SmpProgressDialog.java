/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.plaf.basic.BasicProgressBarUI;

import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.util.swing.TransparentPanel;

/**
 * The dialog that pops up when SMP negotiation starts.
 * It contains a progress bar that indicates the status of the SMP
 * authentication process.
 *
 * @author Marin Dzhigarov
 */
@SuppressWarnings("serial")
public class SmpProgressDialog extends JDialog {
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    private final Color successColor = new Color(86, 140, 2);

    private final Color failColor = new Color(204, 0, 0);

    private final JLabel iconLabel = new JLabel();

    /**
     * Instantiates SmpProgressDialog.
     *
     * @param contact The contact that this dialog is associated with.
     */
    public SmpProgressDialog(Contact contact) {
        setTitle(OtrActivator.resourceService.getI18NString("plugin.otr.smpprogressdialog.TITLE"));

        JPanel mainPanel = new TransparentPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setPreferredSize(new Dimension(300, 70));

        String authFromText = String.format(OtrActivator.resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_FROM", new String[]{contact.getDisplayName()}));

        JPanel labelsPanel = new TransparentPanel();
        labelsPanel.setLayout(new BoxLayout(labelsPanel, BoxLayout.X_AXIS));

        labelsPanel.add(iconLabel);
        labelsPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        labelsPanel.add(new JLabel(authFromText));

        mainPanel.add(labelsPanel);
        mainPanel.add(progressBar);

        init();

        this.getContentPane().add(mainPanel);
        this.pack();
    }

    /**
     * Initializes the progress bar and sets it's progression to 1/3.
     */
    public void init() {
        progressBar.setUI(new BasicProgressBarUI() {
            private Rectangle r = new Rectangle();

            @Override
            protected void paintIndeterminate(Graphics g, JComponent c) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                r = getBox(r);
                g.setColor(progressBar.getForeground());
                g.fillOval(r.x, r.y, r.width, r.height);
            }
        });
        progressBar.setValue(33);
        progressBar.setForeground(successColor);
        progressBar.setStringPainted(false);
        iconLabel.setIcon(OtrActivator.resourceService.getImage("plugin.otr.ENCRYPTED_UNVERIFIED_ICON_22x22"));
    }

    public void dispose() {
    }

    /**
     * Sets the progress bar to 2/3 of completion.
     */
    public void incrementProgress() {
        progressBar.setValue(66);
    }

    /**
     * Sets the progress bar to green.
     */
    public void setProgressSuccess() {
        progressBar.setValue(100);
        progressBar.setForeground(successColor);
        progressBar.setStringPainted(true);
        progressBar.setString(OtrActivator.resourceService.getI18NString("plugin.otr.smpprogressdialog.AUTHENTICATION_SUCCESS"));
        iconLabel.setIcon(OtrActivator.resourceService.getImage("plugin.otr.ENCRYPTED_ICON_22x22"));
    }

    /**
     * Sets the progress bar to red.
     */
    public void setProgressFail() {
        progressBar.setValue(100);
        progressBar.setForeground(failColor);
        progressBar.setStringPainted(true);
        progressBar.setString(OtrActivator.resourceService.getI18NString("plugin.otr.smpprogressdialog.AUTHENTICATION_FAIL"));
    }
}
