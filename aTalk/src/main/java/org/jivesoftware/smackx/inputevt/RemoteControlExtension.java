/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.inputevt;

import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * This class implements input event extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RemoteControlExtension implements XmlElement
{
    /**
     * AWT event that represents our <code>RemoteControlExtension</code>.
     */
    private final ComponentEvent event;

    /**
     * Size of the panel that contains video
     */
    private final Dimension videoPanelSize;

    /**
     * Constructor.
     */
    public RemoteControlExtension()
    {
        videoPanelSize = null;
        event = null;
    }

    /**
     * Constructor.
     *
     * @param videoPanelSize size of the panel that contains video
     */
    public RemoteControlExtension(Dimension videoPanelSize)
    {
        this.videoPanelSize = videoPanelSize;
        this.event = null;
    }

    /**
     * Constructor.
     *
     * @param event AWT event
     */
    public RemoteControlExtension(ComponentEvent event)
    {
        this.event = event;
        this.videoPanelSize = null;
    }

    /**
     * Constructor.
     *
     * @param videoPanelSize size of the panel that contains video
     * @param event AWT event
     */
    /*
     * public RemoteControlExtension(InputEvent event, Dimension videoPanelSize) {
     * this.videoPanelSize = videoPanelSize; this.event = event; }
     */

    /**
     * Get <code>ComponentEvent</code> that represents our <code>InputExtensionItem</code>.
     *
     * @return AWT <code>ComponentEvent</code>
     */
    public ComponentEvent getEvent()
    {
        return event;
    }

    /**
     * Get the element name of the <code>XmlElement</code>.
     *
     * @return "remote-control"
     */
    public String getElementName()
    {
        return RemoteControlExtensionProvider.ELEMENT_REMOTE_CONTROL;
    }

    /**
     * Returns the XML namespace of the extension sub-packet root element. The namespace is always
     * "<a href="http://jitsi.org/protocol/inputevt">...</a>".
     *
     * @return the XML namespace of the packet extension.
     */
    public String getNamespace()
    {
        return RemoteControlExtensionProvider.NAMESPACE;
    }

    /**
     * Get the XML representation.
     *
     * @return XML representation of the item
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();

        if (event != null) {
            /*
             * if (event instanceof MouseEvent) { MouseEvent e = (MouseEvent) event;
             *
             * switch (e.getID()) { case MouseEvent.MOUSE_DRAGGED: case MouseEvent.MOUSE_MOVED: if
             * (videoPanelSize != null) { Point p = e.getPoint(); double x = (p.getX() /
             * videoPanelSize.width); double y = (p.getY() / videoPanelSize.height); ret =
             * RemoteControlExtensionProvider.getMouseMovedXML(x, y); } break; case
             * MouseEvent.MOUSE_WHEEL: MouseWheelEvent ew = (MouseWheelEvent) e; ret =
             * RemoteControlExtensionProvider.getMouseWheelXML(ew.getWheelRotation()); break; case
             * MouseEvent.MOUSE_PRESSED: ret =
             * RemoteControlExtensionProvider.getMousePressedXML(e.getModifiers()); break; case
             * MouseEvent.MOUSE_RELEASED: ret =
             * RemoteControlExtensionProvider.getMouseReleasedXML(e.getModifiers()); break; default:
             * break; } } else
             *//*
             * if (event instanceof KeyEvent) { KeyEvent e = (KeyEvent) event; int keycode =
             * e.getKeyCode(); int key = e.getKeyChar();
             *
             * if (key != KeyEvent.CHAR_UNDEFINED) { keycode = e.getKeyChar(); } else { keycode
             * = e.getKeyCode(); }
             *
             * if (keycode == 0) { return null; }
             *
             * switch (e.getID()) { case KeyEvent.KEY_PRESSED: ret =
             * RemoteControlExtensionProvider.getKeyPressedXML(keycode); break; case
             * KeyEvent.KEY_RELEASED: ret =
             * RemoteControlExtensionProvider.getKeyReleasedXML(keycode); break; case
             * KeyEvent.KEY_TYPED: ret = RemoteControlExtensionProvider.getKeyTypedXML(keycode);
             * break; default: break; }
             */
        }
        return xml;
    }
}
