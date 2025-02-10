package java.awt;

import java.util.EventObject;

public abstract class AWTEvent extends EventObject {
    protected int id;
    protected boolean consumed;
    byte[] bdata;

    public AWTEvent(Object source, int id) {
        super(source);
        this.id = id;
    }

    public int getID() {
        return id;
    }
}