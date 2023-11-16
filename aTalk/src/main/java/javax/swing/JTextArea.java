package javax.swing;

import java.awt.Color;
import java.awt.Font;

public class JTextArea {

    // --- variables -------------------------------------------------
    private int rows;
    private int columns;
    private int columnWidth;
    private int rowHeight;
    private boolean wrap;
    private boolean word;
	
    public void setWrapStyleWord(boolean word) {
        boolean old = this.word;
        this.word = word;
        // firePropertyChange("wrapStyleWord", old, word);
    }	
    public void setLineWrap(boolean wrap) {
        boolean old = this.wrap;
        this.wrap = wrap;
        // firePropertyChange("lineWrap", old, wrap);
    }
    
    public void setColumns(int columns) {
        int oldVal = this.columns;
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        if (columns != oldVal) {
            this.columns = columns;
            // invalidate();
        }
    }    

	public void setEditable(boolean b) {
		// TODO Auto-generated method stub
		
	}

	public void setOpaque(boolean b) {
		// TODO Auto-generated method stub
		
	}

	public void setBackground(Color color) {
		// TODO Auto-generated method stub
	}    
    
	public void setText(String i18nString) {
		// TODO Auto-generated method stub
		
	}

	public void setFont(Font newFont) {
		// TODO Auto-generated method stub
		
	}
}
