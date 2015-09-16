
package com.csr.gaia.android.replacevoiceprompts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

/**
 * Class for the dialog that allows selection of a device to make a GAIA
 * connection to.
 */
public class SelectDeviceDialogFragment extends DialogFragment {

    private static final String TAG = "SelectDeviceDialogFragment";

    /**
     * The hosting Activity must implement this interface to receive a callback
     * when an item is selected.
     */
    public interface DeviceDialogListener {
        public void onDialogSelectItem(String btAddress);
    }

    // A reference to the listener implemented in the Activity so that we can
    // trigger the callback.
    DeviceDialogListener mListener;

    /**
     * A factory for dialog fragments, that saves the arguments from the
     * Activity in a bundle to be picked up by onCreate.
     * 
     * @param names Array of device names strings.
     * @param address Array of Bluetooth address strings corresponding to device names.
     * @param checkedIndex The index of the item that should be shown as initially checked.
     * @param isConnected True if a GAIA connection already exists.
     * @return
     */
    public static SelectDeviceDialogFragment newInstance(String[] names, String[] address, int checkedIndex, boolean isConnected) {
        SelectDeviceDialogFragment newDialog = new SelectDeviceDialogFragment();
        // Build a Bundle with all the passed in arguments.
        Bundle args = new Bundle();
        args.putStringArray("names", names);
        args.putStringArray("address", address);
        args.putInt("checkedIndex", checkedIndex);
        args.putBoolean("isConnected", isConnected);
        // Store the bundle in the newly created dialog object and then return it.
        newDialog.setArguments(args);
        return newDialog;
    }

    /**
     * Override the fragment's onAttach method to instantiate the listener in
     * the calling Activity.
     * 
     * @param hostActivity The activity that created this instance of
     *            SelectDeviceDialogFragment.
     */
    @Override
    public void onAttach(Activity hostActivity) {
        super.onAttach(hostActivity);
        // Verify that the host activity implements the callback interface.
        try {
            mListener = (DeviceDialogListener) hostActivity;
        } catch (ClassCastException e) {
            throw new ClassCastException(hostActivity.toString() + " must implement DeviceDialogListener");
        }
    }

    /**
     * Override the onCreateDialog to set up the dialog.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        // Grab the parameters from the bundle that was populated in newInstance().
        String[] deviceNames = getArguments().getStringArray("names");
        final String[] addresses = getArguments().getStringArray("address");
        int checkedIndex = getArguments().getInt("checkedIndex", -1);

        if (checkedIndex == -1)
            checkedIndex = 0;

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.select_device).setPositiveButton(R.string.connect, new DialogInterface.OnClickListener() {
            /**
             * onClick event called when a device is selected.
             */
            public void onClick(DialogInterface dialog, int which) {
                boolean isConnected = getArguments().getBoolean("isConnected");
                AlertDialog dlg = (AlertDialog) dialog;
                int selected = dlg.getListView().getCheckedItemPosition();
                Log.d(TAG, "connected = " + isConnected);
                if (selected >= 0) {
                    Log.d(TAG, "Going to connect to " + addresses[selected]);
                    // Call the activity's call back, passing it the
                    // selected Bluetooth address.
                    mListener.onDialogSelectItem(addresses[selected]);
                }
            }
        }).setSingleChoiceItems(deviceNames, checkedIndex, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).create();
    }
}
