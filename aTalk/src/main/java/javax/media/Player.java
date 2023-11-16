package javax.media;

/**
 * Standard JMF class -- see <a href=
 * "http://java.sun.com/products/java-media/jmf/2.1.1/apidocs/javax/media/Player.html"
 * target="_blank">this class in the JMF Javadoc</a>. Complete.
 *
 * @author Ken Larson
 *
 */
public interface Player extends MediaHandler, Controller
{
    public void addController(Controller newController)
            throws IncompatibleTimeBaseException;

    public java.awt.Component getControlPanelComponent();

    public GainControl getGainControl();

    public java.awt.Component getVisualComponent();

    public void removeController(Controller oldController);

    public void start();
}
