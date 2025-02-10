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
    void addController(Controller newController)
            throws IncompatibleTimeBaseException;

    java.awt.Component getControlPanelComponent();

    GainControl getGainControl();

    java.awt.Component getVisualComponent();

    void removeController(Controller oldController);

    void start();
}
