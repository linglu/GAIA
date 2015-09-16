
package com.csr.gaia.android.replacevoiceprompts;

/**
 * Represents an item in the list of controls on the update tab of the UI.
 */
public class ControlItem {
    String name = null;
    boolean selected = false;
    String details = null;

    public ControlItem(String name, boolean selected, String detail) {
        super();
        this.name = name;
        this.selected = selected;
        this.details = detail;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String detail) {
        this.details = detail;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
