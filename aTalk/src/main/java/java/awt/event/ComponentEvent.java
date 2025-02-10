package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;

public class ComponentEvent extends AWTEvent {
    public ComponentEvent(Component source, int id) {
        super(source, id);
    }

    public Component getComponent() {
        return source instanceof Component ? (Component) source : null;
    }
}
