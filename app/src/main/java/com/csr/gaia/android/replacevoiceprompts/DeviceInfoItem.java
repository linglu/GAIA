
package com.csr.gaia.android.replacevoiceprompts;

/**
 * Represents an item in the list of information on the status tab of the UI.
 */
public class DeviceInfoItem {

    String name = null;
    boolean hasImage = false;
    String value = null;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasImage() {
        return hasImage;
    }

    public void setHasImage(boolean hasImage) {
        this.hasImage = hasImage;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public DeviceInfoItem(String name, boolean hasImage, String value) {
        this.name = name;
        this.hasImage = hasImage;
        this.value = value;
    }
}
