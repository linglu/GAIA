package com.csr.gaia.android.replacevoiceprompts;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

import com.csr.gaia.android.library.DataConnectionListener;
import com.csr.gaia.android.library.Gaia;
import com.csr.gaia.android.library.Gaia.EventId;
import com.csr.gaia.android.library.Gaia.Status;
import com.csr.gaia.android.library.GaiaCommand;
import com.csr.gaia.android.library.GaiaLink;
import com.csr.gaia.android.library.log.DebugLog;
import com.lamerman.FileDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**This demo application is based on Gaia library which includes {@link Gaia},{@link GaiaLink},{@link GaiaCommand},{@link DataConnectionListener} classes.
 * 
 * Handset manufacturer need to prepare SQIF for OTA Updates through configuring PSKEY_USR47(0x02b9)
 * Two partitions were created in the demo, initial value is 0x0002, which means partition 1 is used, partition 2 is for OTA update.
 * 
 * When application launched, two bits in PSKEY_USER15(0x0299) might be set if device is not in searchable mode.
 *   
 * For OTA update please do below steps when running application: 
 * 1.  Check mounted partition firstly
 * 2.  If current mounted partition is 0, means there is no mounted partitions, then go to update language
 *     otherwise reboot device to be into initial status
 *     
 * For Apt-X enable feature:
 * APT-X security key in PSKEY_USER20(0x226c) need to be set on the device
 * PSKEY_USER15(0x0299) will be retrieved and set on the phone, after that please reboot device. 
 * 
 * For language options, PSKEY_USER9(0293) will be retrieved to get the total of languages.
 */
public class VoicePromptUpdater extends Activity implements SelectDeviceDialogFragment.DeviceDialogListener {

    private ImageView mDataIndicator;
    private TextView mConnectIndicator;
    private TextView mDeviceName;

    private String mBtAddress = "";
    private GaiaLink mGaiaLink;
    private GaiaCommand mCommand;
    private boolean mIsConnected;
    private byte[] mPacketPayloadBuffer;
    private byte mStreamId;
    private int mReadByteSize;
    // The InputStream used to read from voice prompt files.
    private InputStream mInputStream;
    
    // The total number of packets required to send the current file opened as mInputStream. Used to calculate progress.
    private int mNumPacketsInFile = 100;
    private int mSentPacketsNum = 0;    
    private String mEventNameArray[];
    private String mLanguageList[];
    private String mBuiltinVoicePromptArray[];

    private int mDataIndicatorInt;
    private int mCurrentLanguageID = -1;
    private int mTotalLanguageNum = -1;
    private int mPartitionID = -1;
    private int mBootMode = -1;
    private String mAPIVersion;
    private int mRssi;
    private int mBattery;
    private String mConnectionDescription = "";

    // Progress bar.
    ProgressDialog mProgressBar;
    private int mProgressBarStatus = 0;
    private Handler mProgressBarHandler = new Handler();
    private Handler mDeviceInfoHandler = new Handler();
    private Handler mReconnectionHandler = new Handler();
    private ProgressDialog mBluetoothConnectionProgressDialog;
    private ProgressDialog mWaitingProgressDialog;

    /* List view adaptor */
    private ListView mControlListView;
    private BluetoothDevice[] mAllBondedDevices;
    private int mDeviceIndex = 0;
    private ControllistAdapter mControlAdapter = null;
    private InfolistAdapter mInfoAdapter = null;

    private boolean isLedOn                     = false;
    private boolean isAptXOn                    = false;
    private boolean isDiscoverableModeOnPowerOn = false;
    private boolean isRemainDiscoverableAlltime = false;
    private boolean isRebootDevice              = false;   

    private byte[] mPskeydata15 = null;
    private String mUpdateStatus = null;
    private boolean isStorePskey47 = false;
    private boolean isStorePskey15forDiscoverableMode = false;
    
    private static String sOtaUpdateLabel_Noupdate;
    private static String sOtaUpdateLabel_Updating;

    // Battery and signal indicator.
    private static final int BATTERYINDICATORICON0 = R.drawable.b000;
    private static final int SIGNALINDICATORICON0 = R.drawable.stat_sys_signal_0;
    private static final int BATTERYTHRESHOLD = 4400; // unit is mV
    private static final int HS_EVENT_MESSAGE_BASE = 0x6000;

    private static final int SELECTION_ID = Menu.FIRST - 1;
    //private static final int DISCONNECT_ID = Menu.FIRST + 1;
    private static final int REFRESH_ID    = SELECTION_ID + 1;
    private static final int SCAN_ID       = REFRESH_ID + 1;
    private static final int ABOUT_ID       = SCAN_ID + 1;

    private static final int UPLOAD_LANGUAGE_ACTION = 0;
    private static final int ENABLE_APTX_ACTION = 1;
    private static final int NO_VALID_PARTITION = 2;
    private static final int RESTORE_PSKEY15_FORDISCOVERABLEMODE_ACTION = 3;

    private static final int REQUEST_FILEDIALOG_OK = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_CONNECT_DEVICE = 4;
    private static final int REQUEST_SETTINGS = 5;
    private static final int DISCOVMODEPOWERON_BYTE_OFFSET = 3;
    private static final int REMAINDISCOVMODEPOWERON_BYTE_OFFSET = 2;
    private static final int APTX_BYTE_OFFSET = 13;

    private static final int CONTROL_LISTVIEW_APTX_ACTION = 0;
    private static final int CONTROL_LISTVIEW_LED_ACTION = 1;
    private static final int CONTROL_LISTVIEW_SWITCHLANGUAGE_ACTION = 2;
    private static final int CONTROL_LISTVIEW_UPDATELANGUAGE_ACTION = 3;
    private static final int CONTROL_LISTVIEW_REBOOT_ACTION = 4;

    private static final int STORAGE_DEVICE_ID =  1;
    private static final int GAIA_ACCESS_MODE  =  2;

    // The maximum size of the data portion within the payload of the write storage command packet.
    private static final int WRITE_STORAGE_MAX_DATA_SIZE = 240;
    
    private static final String PSKEY_FEATURE_BLOCK = "0299";
    private static final String PSKEY_USER_47 = "02b9";
    private static final String PSKEY_USER_9 = "0293";
    private static final String LANGUAGE_UPDATE_TIME_PREF_KEY = "LANGUAGE_UPDATE_TIME_PREF_KEY";
    private static final String BatteryUnit = "mV";
    private static final String RssiUnit = "dBm";
    private static final String TAG = "ReplaceVoicePrompts";
    
    private Menu mOptionMenu;
    long startTime;

    /**
     * Override Activity onCreate to initialise various things.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DebugLog.d(DebugLog.TAG, "VoicePromptUpdater:onCreate " + "");

        final ActionBar bar = getActionBar();
        if(bar != null)
            bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        
        setContentView(R.layout.tabmain);
        displayCotrolListView();
        displayInfoListView();

        // Tab layout setup.
        TabHost tabHost = (TabHost) findViewById(R.id.tabHost);
        tabHost.setup();

        View tabview1 = createTabView(tabHost.getContext(), getResources().getString(R.string.tab1_name));
        View tabview2 = createTabView(tabHost.getContext(), getResources().getString(R.string.tab2_name));

        tabHost.getTabWidget().setDividerDrawable(R.drawable.tab_divider);

        TabSpec spec1 = tabHost.newTabSpec(getResources().getString(R.string.tab1_name));
        spec1.setIndicator(tabview1);
        spec1.setContent(R.id.tab1);

        TabSpec spec2 = tabHost.newTabSpec(getResources().getString(R.string.tab2_name));
        spec2.setIndicator(tabview2);
        spec2.setContent(R.id.tab2);

        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        // Tab layout setup end.

        // Initial state is disconnected.
        mIsConnected = false;
        mConnectIndicator = (TextView) findViewById(R.id.connect_ind);
        mDeviceName = (TextView) findViewById(R.id.device_name);
        mDataIndicator = (ImageView) findViewById(R.id.data_ind);
        mConnectionDescription = getResources().getString(R.string.channel_connection_desc);
        final Animation mAnimation = new AlphaAnimation(1, 0);

        // Set up the GAIA link (implemented in GaiaLibrary).
        mGaiaLink = new GaiaLink();
        // Set the handler for GAIA messages received from the remote device.
        mGaiaLink.setReceiveHandler(mGaiaReceiveHandler);
        
        // Listener that allows the GaiaLink to update this Activity to indicate when data is being sent or received.
        mGaiaLink.setDataConnectionListener(new DataConnectionListener() {
            /**
             * This will be called when data starts or stops being sent or received on the GaiaLink.
             * @param isDataTransferInProgress True if data is being sent or received on the link.
             */
            public void update(boolean isDataTransferInProgress) {
                mDataIndicatorInt = isDataTransferInProgress ? 1 : 0;
                mDataIndicator.post(new Runnable() {
                    public void run() {
                        mDataIndicator.setImageLevel(mDataIndicatorInt);
                        if (mDataIndicatorInt == 1) {
                            // If data is being transferred then we show a blinking animation.
                            mAnimation.setDuration(100);
                            mAnimation.setInterpolator(new LinearInterpolator());
                            mAnimation.setRepeatCount(5);
                            mAnimation.setRepeatMode(Animation.REVERSE);
                            mDataIndicator.startAnimation(mAnimation);
                        } else {
                            // No data is currently being transferred so stop the animation.
                            mDataIndicator.clearAnimation();
                        }
                    }
                });
            }
        });
 
        // Allocate the buffer for building packets to be sent over the air.
        mPacketPayloadBuffer = new byte[GaiaLink.MAX_PACKET_PAYLOAD];

        // Read preferences.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mUpdateStatus = prefs.getString(LANGUAGE_UPDATE_TIME_PREF_KEY, "");

        mEventNameArray          = getResources().getStringArray(R.array.ua_names);
        mLanguageList            = getResources().getStringArray(R.array.language_id);
        mBuiltinVoicePromptArray = getResources().getStringArray(R.array.built_in_voiceprompt);
        sOtaUpdateLabel_Noupdate = getResources().getString(R.string.no_update);
        sOtaUpdateLabel_Updating = getResources().getString(R.string.updating);
        
        mAPIVersion = getResources().getString(R.string.null_string);
    }
    
    /**
     * Initialise the contents of the options menu shown at the top of the activity.
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, SELECTION_ID, 0, getResources().getString(R.string.selection)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, SCAN_ID, 0, getResources().getString(R.string.scan)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, REFRESH_ID, 0, getResources().getString(R.string.refresh)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, ABOUT_ID, 0, getResources().getString(R.string.about)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mOptionMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Event handler for the options menu.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SELECTION_ID:
                if (!mIsConnected) {
                    // Select a device to make a GAIA connection to.
                        if (BluetoothAdapter.getDefaultAdapter() != null) {
                            mAllBondedDevices = (BluetoothDevice[]) BluetoothAdapter.getDefaultAdapter().getBondedDevices().toArray(new BluetoothDevice[0]);
        
                            if (mAllBondedDevices.length > 0) {
                                int deviceCount = mAllBondedDevices.length;
                                if (mDeviceIndex >= deviceCount) {
                                    mDeviceIndex = 0;
                                }
                                String[] deviceNames = new String[deviceCount];
                                String[] deviceAddress = new String[deviceCount];
                                int i = 0;
                                for (BluetoothDevice device : mAllBondedDevices) {
                                    deviceNames[i] = device.getName();
                                    deviceAddress[i++] = device.getAddress();
                                }
                                // Show the dialog to allow selection of a device to connect to. See onDialogSelectItem() for the callback for this dialog.                         
                                SelectDeviceDialogFragment deviceDialog = SelectDeviceDialogFragment.newInstance(deviceNames, deviceAddress, mDeviceIndex, mIsConnected );
                                deviceDialog.show(getFragmentManager(), "deviceDialog");
                            }
                        }
                    }else{
                        try {
                          Log.i(TAG, "Disconnecting.");
                          mGaiaLink.disconnect();
                          mIsConnected = false;
                      } catch (IOException e) {
                          Log.d(TAG, "Disconnect failed: " + e.getMessage());
                      }
                    }
                return true;
            case REFRESH_ID:                
                getDeveiceInfo();
                return true;
            case SCAN_ID:
                // Show the system Bluetooth Settings to allow the user to scan and pair with a device.
                scanDevice();
                return true;
            case ABOUT_ID: {
                showAboutDialog();
            }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Override the Activity onDestroy to disconnect the link.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If the system destroys us then make sure the link gets disconnected and open files are closed.
        if (mIsConnected) {
            try {
                mGaiaLink.disconnect();
                mInputStream.close();
            } catch (IOException e) {
                Log.d(TAG, "Failure in onDestroy: " + e.getMessage());
            }
        }
    }
    
    /**
     * Callback for select device dialog.
     * @param btAddress The Bluetooth address that was selected in the dialog. 
     */
    @Override
    public void onDialogSelectItem(String btAddress) {
        mBtAddress = btAddress;
        try {
            mGaiaLink.connect(mBtAddress);
            showConnectProgress();
        } catch (IOException e) {
            this.toast("Connection failed");
            Log.d(TAG, "Connection failed: " + e.getMessage());
        }
    }    

    /**
     * Send GAIA commands to retrieve information about the remote device.
     */
    private void getDeveiceInfo() {
        // Send the request commands. If the remote device responds, the values will be received as a GAIA message
        // that will be processed in handleTheUnhandled() to update the local variables holding state about the remote device.
        if (mIsConnected) {
            try {
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_TTS_LANGUAGE);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_API_VERSION);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_CURRENT_RSSI);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_CURRENT_BATTERY_LEVEL);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_RETRIEVE_FULL_PS_KEY, 0x02, 0x99); // PSKEY_FEATURE_BLOCK
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_RETRIEVE_FULL_PS_KEY, 0x02, 0x93); // PSKEY_USER_CONFIGURATION_9
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_RETRIEVE_FULL_PS_KEY, 0x02, 0xb9); // PSKEY_FEATURE_BLOCK
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_LED_CONTROL);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_BOOT_MODE);
                /*Below code is just for demo, set PSKEY configuration data 47 as initial value 
                 *0x0000 means whole partitions are unused.
                 */
                byte[] Pskeydata47 = new byte[4];
                Pskeydata47[0] = (byte) 0x00;
                Pskeydata47[1] = (byte) 0x2f;
                Pskeydata47[2] = (byte) 0x00;
                Pskeydata47[3] = (byte) 0x00;
                isStorePskey47 = true;
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_STORE_PS_KEY, Pskeydata47);
                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_MOUNTED_PARTITIONS);
            }
            catch (IOException e) {
                toast("Failed to retrieve info from remote device.");
                Log.d(TAG,"sendCommand failed in getDeviceInfo: " + e.getMessage());
            }
        }
    }
    
    /**
     * Reset variables that hold information about the remote device to default values.
     */
    private void clearDeviceInfo() {
        mAPIVersion = getResources().getString(R.string.null_string);
        mCurrentLanguageID = -1;
        mBootMode = -1;
        mRssi = 0;
        mBattery = 0;
        
        mInfoAdapter.notifyDataSetChanged();
        mControlListView.setEnabled(false);
        mControlAdapter.notifyDataSetChanged();
    }

    Handler mGaiaReceiveHandler = new Handler() {
        /**
         * Handle all incoming messages received through GAIA from the remote device.
         * Basic messages for connect, disconnect and error are handled here.
         * Other messages are passed off to handleTheUnhandled() for processing.
         */
        @Override
        public void handleMessage(Message msg) {

            switch (GaiaLink.Message.valueOf(msg.what)) {
                case DEBUG:
                    break;

                case UNHANDLED:
                    handleTheUnhandled(msg);
                    if (mBluetoothConnectionProgressDialog != null)
                        mBluetoothConnectionProgressDialog.dismiss();
                    break;

                case CONNECTED:
                    mConnectIndicator.setText(R.string.connected);
                    handleGaiaConnected();
                    if (mBluetoothConnectionProgressDialog != null)
                        mBluetoothConnectionProgressDialog.dismiss();
                    
                    updateSelectionMenuItem();
                    mControlListView.setEnabled(true);
                    mControlAdapter.notifyDataSetChanged();
                    break;

                case DISCONNECTED:
                    // Update the UI to show we are disconnected.
                    mConnectIndicator.setText(R.string.disconnected);
                    mDataIndicator.setImageLevel(mDataIndicatorInt);
                    mDeviceName.setText(mConnectionDescription);
                    toast("Disconnected");
                    // Disconnect the link locally.
                    try {
                        mGaiaLink.disconnect();
                    } catch (IOException e) {
                        Log.d(TAG, "Disconnect failed: " + e.getMessage());
                    } finally {
                        // Ensure state is correct even if the disconnect failed.
                        mIsConnected = false;
                        if (mBluetoothConnectionProgressDialog != null)
                            mBluetoothConnectionProgressDialog.dismiss();
                        clearDeviceInfo();
                        updateSelectionMenuItem();
                        if(isRebootDevice)
                        {
                            mReconnectionHandler.removeCallbacks(reconnectDevice);
                            mReconnectionHandler.postDelayed(reconnectDevice, 6500);
                        }
                    }
                    break;
                    
                case ERROR:
                    // Connection error received from GAIA library.
                    mConnectIndicator.setText(R.string.connection_error);
                    mDeviceName.setText(mConnectionDescription);
                    toast("GAIA connection failed.");
                    Log.d(TAG,"GAIA: " + ((Exception) msg.obj).toString());
                    if (mBluetoothConnectionProgressDialog != null)
                        mBluetoothConnectionProgressDialog.dismiss();
                    
                    mIsConnected = false;
                    clearDeviceInfo();
                    mIsConnected = false;
                    updateSelectionMenuItem();
                    break;
            }
        }

        /**
         * Event handler triggered when the GAIA link connects.
         */
        private void handleGaiaConnected() {
            DebugLog.d(DebugLog.TAG, "VoicePromptUpdater:handleGaiaConnected " + "");
            toast(mGaiaLink.getName() + " connected");
            mDeviceName.setText(mGaiaLink.getName() + "\n[" + mBtAddress + "]");

            isRebootDevice = false;
            mIsConnected = true;

            mDeviceInfoHandler.removeCallbacks(updateDeviceInfo);
            mDeviceInfoHandler.postDelayed(updateDeviceInfo, 1000);
        }

        /**
         * Get information about the remote device.
         */
        private Runnable updateDeviceInfo = new Runnable() {
            public void run() {
                getDeveiceInfo();
            }
        };
        
		/**
         * Thread to reconnect the link.
         */
        private Runnable reconnectDevice = new Runnable() {
            public void run() {
                if(!"".equalsIgnoreCase(mBtAddress)){
                    try {
                        mGaiaLink.connect(mBtAddress);
                        showConnectProgress();
                    } catch (IOException e) {                        
                        Log.d(TAG, "Error when reconnecting device: " + e.getMessage());
                    }
                }
            }
        };

        /**
         * Handle a command not handled by GaiaLink.
         * @param msg The Message object containing the command.
         */
        private void handleTheUnhandled(Message msg) {
            mCommand = (GaiaCommand) msg.obj;

            Gaia.Status status = mCommand.getStatus();
            int command_id = mCommand.getCommand();

            // Handle acks for commands we have sent.
            if (mCommand.isAcknowledgement()) {
                if (status == Gaia.Status.SUCCESS) {
                    Log.d(TAG, Integer.toHexString(mCommand.getVendorId()) + ":" + Integer.toHexString(mCommand.getCommandId()) + " = " + mCommand.getPayload().length);

                    // Act on an acknowledgement
                    switch (command_id) {
                        case Gaia.COMMAND_DEVICE_RESET:
                            try {
                                if (mIsConnected) {
                                    isRebootDevice = true;
                                    mGaiaLink.disconnect();
                                    toast("Device reboot started, will reconnect it in 6 seconds");
                                    mIsConnected = false;
                                }
                            }
                            catch (IOException e) {
                                Log.d(TAG, "Disconnect failed: " + e.getMessage());
                            }
                            break;

                        case Gaia.COMMAND_GET_BOOT_MODE:
                            if (mCommand.getByte(0) == 0x00) {
                                mBootMode = mCommand.getByte(1);
                            }
                            break;

                        case Gaia.COMMAND_GET_STORAGE_PARTITION_STATUS:
                            break;

                        case Gaia.COMMAND_OPEN_STORAGE_PARTITION:
                            // We received an acknowledgement that the partition
                            // was opened on the remote device.
                            startTime = System.currentTimeMillis();
                            if (mCommand.getByte(0) == 0x00) {
                                mStreamId = (byte) mCommand.getByte(1);
                                // Send the first packet of the file data to write into the partition.
                                writePartition();
                                mUpdateStatus = sOtaUpdateLabel_Updating;
                                mControlAdapter.notifyDataSetChanged();
                            } else {
                                mUpdateStatus = sOtaUpdateLabel_Noupdate;
                                mControlAdapter.notifyDataSetChanged();
                            }
                            break;

                        case Gaia.COMMAND_WRITE_STORAGE_PARTITION:
                            // Partition write of last packet payload was successful. Increase the number of sent packets.                            
                            mSentPacketsNum++;
                            // There may be more data to send so call writePartition again.
                            writePartition();
                            break;

                        case Gaia.COMMAND_CLOSE_STORAGE_PARTITION:
                            long time = System.currentTimeMillis() - startTime;
                            Log.d(TAG,"Time spent:" + time);
                            /*byte[] Pskeydata47 = new byte[4];
                            Pskeydata47[0] = (byte) 0x00;
                            Pskeydata47[1] = (byte) 0x2f;
                            Pskeydata47[2] = (byte) 0x00;
                            Pskeydata47[3] = (byte) 0x00;

                            isStorePskey47 = true;
                            try {
                                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_STORE_PS_KEY, Pskeydata47);
                            }
                            catch (IOException e) {
                                toast("Failure sending command to set ps key.");
                                Log.d(TAG, "Failure when handling ACK for COMMAND_CLOSE_STORAGE_PARTITION " +
                                		"- sendCommand STORE_PS_KEY failed: " + e.getMessage());
                            }*/
                            storePreference();
                            try {
                                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_MOUNTED_PARTITIONS);
                            } catch (IOException e1) {
                                Log.d(TAG, "Failed to get mounted partition");
                            }
                            break;

                        case Gaia.COMMAND_RETRIEVE_FULL_PS_KEY:
                            if (mCommand.getByte(0) == 0x00) {
                                String retrieveKey = String.format("%02x", mCommand.getByte(1)) + String.format("%02x", mCommand.getByte(2));
                                Log.d(TAG, "retrieveKey = " + retrieveKey);
                                if (PSKEY_USER_47.equalsIgnoreCase(retrieveKey)) {
                                } else if (PSKEY_FEATURE_BLOCK.equalsIgnoreCase(retrieveKey)) {
                                    for (int i = 0; i < mCommand.getPayload().length; ++i)
                                        Log.d(TAG, "PSKEY_FEATURE_BLOCK[" + i + "]=" + String.format("%02x", mCommand.getPayload()[i]));

                                    mPskeydata15 = new byte[14];
                                    mPskeydata15[0] = 0x00;
                                    mPskeydata15[1] = 0x0f;
                                    System.arraycopy(mCommand.getPayload(), 3, mPskeydata15, 2, 12);

                                    int value = mCommand.getPayload()[APTX_BYTE_OFFSET];

                                    if ((value & 0x03) == 0x03) {
                                        isAptXOn = true;
                                    } else {
                                        isAptXOn = false;
                                    }

                                    value = mPskeydata15[DISCOVMODEPOWERON_BYTE_OFFSET];
                                    if ((value & 0x01) == 1) {
                                        isDiscoverableModeOnPowerOn = true;
                                    } else {
                                        isDiscoverableModeOnPowerOn = false;
                                        mPskeydata15[DISCOVMODEPOWERON_BYTE_OFFSET] |= 0x01;
                                    }
                                    Log.d(TAG, "discoverable mode on power on :" + (isDiscoverableModeOnPowerOn ? "true" : "false"));

                                    value = mPskeydata15[REMAINDISCOVMODEPOWERON_BYTE_OFFSET];
                                    if ((value & 0x04) == 4) {
                                        isRemainDiscoverableAlltime = true;
                                    } else {
                                        isRemainDiscoverableAlltime = false;
                                        mPskeydata15[REMAINDISCOVMODEPOWERON_BYTE_OFFSET] |= 0x04;
                                    }

                                    if (!isDiscoverableModeOnPowerOn || !isRemainDiscoverableAlltime) {
                                        isStorePskey15forDiscoverableMode = true;
                                        try {
                                            mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_STORE_PS_KEY, mPskeydata15);
                                        }
                                        catch (IOException e) {
                                            toast("Failure to set ps key data.");
                                            Log.d(TAG,"Failure when attemting to set pskey data 15: " + e.getMessage());
                                        }
                                    }

                                    Log.d(TAG, "remain discoverable at all time :" + (isRemainDiscoverableAlltime ? "true" : "false"));

                                } else if (PSKEY_USER_9.equalsIgnoreCase(retrieveKey)) {
                                    for (int i = 0; i < mCommand.getPayload().length; ++i)
                                        Log.d(TAG, "PSKEY_USER_9[" + i + "]=" + String.format("%02x", mCommand.getPayload()[i]));

                                    mTotalLanguageNum = mCommand.getPayload()[5] << 8 | mCommand.getPayload()[6];
                                    Log.d(TAG, "total language number is:" + mTotalLanguageNum);
                                }
                            }
                            break;

                        case Gaia.COMMAND_GET_MOUNTED_PARTITIONS:
                            if (mCommand.getByte(0) == 0x00) {
                                mPartitionID = mCommand.getByte(1);
                                int value = mPartitionID;
                                int bit = 0;
                                while (value > 0) {
                                    if ((value & 0x01) == 1)
                                        Log.d(TAG, "Partition [" + (bit++) + "] mounted");
                                    value >>= 1;
                                }
                                toast("Mounted Partition:" + String.format("%d", mPartitionID));
                                mInfoAdapter.notifyDataSetChanged();
                            }
                            break;
                        case Gaia.COMMAND_GET_CURRENT_BATTERY_LEVEL:
                            if (mCommand.getByte(0) == 0x00) {
                                mBattery = ((mCommand.getByte(1) & 0xff) << 8) | (mCommand.getByte(2) & 0xff);
                            } else
                                mBattery = 0;
                            break;
                        case Gaia.COMMAND_GET_API_VERSION: {
                            if (mCommand.getByte(0) == 0x00) {
                                mAPIVersion = "" + mCommand.getByte(1) + "." + mCommand.getByte(2) + "." + mCommand.getByte(3);
                            } else
                                mAPIVersion = getResources().getString(R.string.null_string);
                        }
                            break;
                        case Gaia.COMMAND_GET_CURRENT_RSSI:
                            if (mCommand.getByte(0) == 0x00) {
                                mRssi = mCommand.getByte(1);
                            } else {
                                mRssi = 0;
                            }
                            break;
                        case Gaia.COMMAND_GET_APPLICATION_VERSION: {
                            if (mCommand.getByte(0) == 0x00) {
                                String ApplicationVersionValue = "";
                                for (Byte data : mCommand.getPayload()) {
                                    String temp = Gaia.hexb(data);
                                    ApplicationVersionValue += "" + Integer.valueOf(temp, 16).intValue();
                                }
                            }
                        }
                            break;
                        case Gaia.COMMAND_SET_TTS_LANGUAGE:
                            try {
                                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_TTS_LANGUAGE);
                                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_MOUNTED_PARTITIONS);
                            }
                            catch (IOException e) {
                                toast("Failed to set language");
                                Log.d(TAG, "Failure when sending set language command: " + e.getMessage());
                            }
                            break;
                        case Gaia.COMMAND_MOUNT_STORAGE_PARTITION:
                            try {
                                mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_MOUNTED_PARTITIONS);
                            }
                            catch (IOException e) {
                                toast("Failed to get mounted storage partitions");
                                Log.d(TAG,"Failure when getting mounted partitions: " + e.getMessage());
                            }
                            break;
                        case Gaia.COMMAND_GET_TTS_LANGUAGE:
                            Log.d(TAG, "Got Switch language command response");
                            if (mCommand.getByte(0) == 0x00) {
                                mCurrentLanguageID = mCommand.getByte(1);
                                mControlAdapter.notifyDataSetChanged();
                            }
                            break;
                        case Gaia.COMMAND_SET_LED_CONTROL:
                            Log.d(TAG, "Set LED control result");
                            if (mCommand.getByte(0) == 0x00) {
                                try {
                                    mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_GET_LED_CONTROL);
                                }
                                catch (IOException e) {
                                    toast("Failed to get LED status");
                                    Log.d(TAG, "Failed when getting LED control: " + e.getMessage());
                                }
                            } else if (mCommand.getByte(0) == 0x05) {
                                Log.d(TAG, "Invalid Parameter");
                            }
                            break;
                        case Gaia.COMMAND_GET_LED_CONTROL:
                            if (mCommand.getByte(0) == 0x00) {
                                Log.d(TAG, "Get LED successfully");
                                if (mCommand.getByte(1) == 0x00)
                                    isLedOn = false;
                                else if (mCommand.getByte(1) == 0x01)
                                    isLedOn = true;

                                mControlAdapter.notifyDataSetChanged();
                            }
                            break;
                        case Gaia.COMMAND_STORE_PS_KEY: {
                            if (mWaitingProgressDialog != null)
                                mWaitingProgressDialog.dismiss();

                            if (mCommand.getByte(0) == 0x00) {
                                if (isStorePskey47) {
                                    Log.d(TAG, "write pskey47 successfully");
                                    isStorePskey47 = false;
                                } else if (isStorePskey15forDiscoverableMode) {
                                    isStorePskey15forDiscoverableMode = false;
                                    rebootDialog(RESTORE_PSKEY15_FORDISCOVERABLEMODE_ACTION);
                                } else {
                                    isAptXOn = !isAptXOn;
                                    Log.d(TAG, "set Aptx successfully");
                                    mControlAdapter.notifyDataSetChanged();
                                    rebootDialog(ENABLE_APTX_ACTION);
                                }

                            } else
                                Log.d(TAG, "set Aptx failed");
                        }
                            break;
                        case Gaia.COMMAND_REGISTER_NOTIFICATION: {
                            Log.d(TAG, "Register notification successfully");
                        }
                            break;
                    }
                } 
                else {
                    // Acknowledgement received with non-success result code, 
                    // so display the friendly message as a toast and dismiss any dialogs.
                    toast("Error from remote device: command_id=" + command_id + ",status=" + Gaia.statusText(status));                    
                    if (mWaitingProgressDialog != null)
                        mWaitingProgressDialog.dismiss();
                    if (mProgressBar != null)
                        mProgressBar.dismiss();
                }
            }
            else if (command_id == Gaia.COMMAND_EVENT_NOTIFICATION) {
                EventId event_id = mCommand.getEventId();
                Log.d(TAG, "Event " + event_id.toString());

                switch (event_id) {
                    case CHARGER_CONNECTION:
                        if (mCommand.getBoolean())
                            toast("Charger connected");
                        else
                            toast("Charger disconnected");
                        break;

                    case USER_ACTION:
                        int user_action = mCommand.getShort(1);
                        Log.d(TAG, String.format("HS Event 0x%04X --> %s", user_action, uaName(user_action)));
                        break;
                    case AV_COMMAND:
                        Log.d(TAG, "AV command : " + new String(mCommand.getPayload()));
                        break;
                    case DEVICE_STATE_CHANGED:
                        Log.d(TAG, "current state : " + new String(mCommand.getPayload()));
                        break;
                    case DEBUG_MESSAGE:
                        Log.d(TAG, "DEBUG_MESSAGE : " + new String(mCommand.getPayload()));
                        break;
                    case KEY:
                        Log.d(TAG, "Key Event " + event_id.toString());
                        break;
                    default:
                        Log.d(TAG, "Event " + event_id.toString());
                        break;
                }

                sendAcknowledgement(mCommand, Gaia.Status.SUCCESS, event_id);
            }
        }
    };

    /**
     * Wrapper for Gaia library ACK command.
     * @param command
     * @param status
     * @param payload
     */
    private void sendAcknowledgement(GaiaCommand command, Status status, int... payload) {
        try {
            mGaiaLink.sendAcknowledgement(command, status, payload);
        }
        catch (IOException e) {
            toast(e.toString());
        }
    }

    /**
     * Wrapper for Gaia library ACK command.
     * @param command
     * @param success
     * @param event_id
     */
    private void sendAcknowledgement(GaiaCommand command, Status success, EventId event_id) {
        sendAcknowledgement(command, success, event_id.ordinal());
    }
    
    /**
     * Get event name as a string from a numerical event ID.
     * @param id The event ID.
     * @return Event name as a string.
     */
    private String uaName(int id) {
        int idx = id - HS_EVENT_MESSAGE_BASE;

        if (idx < 0 || idx >= mEventNameArray.length)
            return "Unknown";

        return mEventNameArray[idx];
    }

    /**
     * Write to a partition on the remote device with the data from the input stream.
     * File will be split into packets of size WRITE_STORAGE_MAX_DATA_SIZE.
     * This method sends one packet per invocation and will be called until all packets have been sent.
     * The file attached to the input stream should already have been opened.
     */
    protected void writePartition() {
       
            // The format of the payload for the "write storage partition" command is defined by the indices below.
            final int INDEX_STREAM_ID = 0;
            final int INDEX_SEQUENCE_NUM_24to31 = 1;
            final int INDEX_SEQUENCE_NUM_16to23 = 2;
            final int INDEX_SEQUENCE_NUM_8to15 = 3;
            final int INDEX_SEQUENCE_NUM_0to7 = 4;
            final int INDEX_START_OF_DATA = 5;            
            
            int length = 0;
            try {
                // Read from the file into the payload buffer at the correct offset.
                length = mInputStream.read(mPacketPayloadBuffer, INDEX_START_OF_DATA, WRITE_STORAGE_MAX_DATA_SIZE);
            }
            catch (IOException e) {
                toast("Failed to read from file");
                Log.d(TAG,"writePartition: failed to read file: " + e.getMessage());
                return;
            }
            
            // Is there any more data to send?
            if (length >= 0) {
                Log.d(TAG, String.format("Write %d %d, %d", mStreamId, mSentPacketsNum, length));

                // Set header fields in payload required by the write storage partition command.
                mPacketPayloadBuffer[INDEX_STREAM_ID] = mStreamId;
                mPacketPayloadBuffer[INDEX_SEQUENCE_NUM_24to31] = (byte) (mSentPacketsNum >> 24);
                mPacketPayloadBuffer[INDEX_SEQUENCE_NUM_16to23] = (byte) (mSentPacketsNum >> 16);
                mPacketPayloadBuffer[INDEX_SEQUENCE_NUM_8to15] = (byte) (mSentPacketsNum >> 8);
                mPacketPayloadBuffer[INDEX_SEQUENCE_NUM_0to7] = (byte) mSentPacketsNum;
                try {
                    mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_WRITE_STORAGE_PARTITION, mPacketPayloadBuffer, length + INDEX_START_OF_DATA);
                }
                catch (IOException e) {
                    toast("Failed to open partition");
                    Log.d(TAG,"writePartition: failed to open partition: " + e.getMessage());
                }                
            }
            else {
                // No more packets to send so perform any tasks required after the transfer has finished.
                updateLanguageComplete();
            }       
    }

    /**
     * Called when transfer of a voice prompt file is complete.
     */
    protected void updateLanguageComplete() {
        try {
            Log.d(TAG, String.format("Close %d", mStreamId));
            // Tell the remote device that the partition can be closed now.
            mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_CLOSE_STORAGE_PARTITION, mStreamId);
            Log.d(TAG, "updateLanguageFinished : Verifying Image ");
        } catch (IOException e) {
            toast("Failed to close partition");
            Log.d(TAG,"Failure when closing partition after transferring file: " + e.getMessage());
        } finally {
            // Make sure the file gets closed.
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    // The user doesn't care if this fails but we should log it.
                    Log.d(TAG, "Failed to close file stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Simple wrapper to make it easier to show a toast on the screen for a short time.
     * @param message The string to display in the toast.
     */
    private void toast(CharSequence message) {
        Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle results returned from other Activities we started.
     */
    @Override
    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                break;
            case REQUEST_CONNECT_DEVICE:
                // The result from the Bluetooth settings dialog?
                if (resultCode == Activity.RESULT_OK) {
                    Intent intentBluetooth = new Intent();
                    intentBluetooth.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intentBluetooth);
                    return;
                }
                break;
            case REQUEST_SETTINGS:
                break;
            case REQUEST_FILEDIALOG_OK:
                handleVoicePromptFileSelected(requestCode, resultCode, data);
                break;
        }
    }

    /**
     * Open a file from a path string ready for sending and open a partition on the remote device.
     * @param filepath Path of file on local device to update the partition with.
     * @param partition Partition number to update on the remote device.
     */
    private void prepareToSendFile(String filepath, int partition) {
        try {
            Log.d(TAG, "open file =" + filepath);
            mInputStream = new FileInputStream(filepath);
            prepareToSendFile(partition);
        }
        catch (Exception e) {            
            toast("Failed to open file for sending");
            Log.d(TAG, "Failed to open file: " + e.getMessage());
        }
        
    }

    /**
     * Open a file from a resource ready for sending and open a partition on the remote device.
     * @param resourceID The resource ID of the file.
     * @param partition Partition number to update on the remote device.
     */
    private void prepareToSendFile(int resourceID, int partition) {
        try {
            mInputStream = getResources().openRawResource(resourceID);
            prepareToSendFile(partition);            
        } catch (Exception e) {
            toast("Failed to open file for sending");
            Log.d(TAG, "Failed to open file: " + e.getMessage());
        }
    }
    
    /**
     * Open a file ready for sending and open a partition on the remote device.
     * Assumes mInputStream is already open.
     * @param partition Partition number to update on the remote device.
     * @throws IOException
     */
    private void prepareToSendFile(int partition) throws IOException {
        byte[] crc = new byte[4];
        mSentPacketsNum = 0;
        
        mReadByteSize = mInputStream.available();
        // Calculate how many packets are required to send this data - this will be used to calculate progress.
        mNumPacketsInFile = (mReadByteSize + WRITE_STORAGE_MAX_DATA_SIZE - 1) / WRITE_STORAGE_MAX_DATA_SIZE;            
        Log.d(TAG, "Reading packets =" + mNumPacketsInFile);
        
        showProgressbar();
        
        Log.d(TAG, String.format("Send %d %d", mInputStream.available(), mNumPacketsInFile));
        
        // Read the CRC from the file. Any exceptions should be handled by our caller.
        mInputStream.read(crc);    
        Log.d(TAG, String.format("CRC %02X %02X %02X %02X", crc[3], crc[2], crc[1], crc[0]));
        
        final int DEVICE_ID = 1;
        final int ACCESS_MODE_OVERWRITE = 2;
        
        // Send the command to open the partition on the remote device. Any exceptions should be handled by our caller.
        mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_OPEN_STORAGE_PARTITION, DEVICE_ID, partition, ACCESS_MODE_OVERWRITE, 
                              crc[3], crc[2], crc[1], crc[0]);            
    }

    /**
     * Create a new thread to display the progress bar and start that thread.
     */
    private void showProgressbar() {
        mProgressBar = new ProgressDialog(this);
        mProgressBar.setCancelable(true);
        mProgressBar.setMessage("Uploading language voice prompts");
        mProgressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressBar.setProgress(0);
        mProgressBar.setMax(100);
        mProgressBar.show();

        // Reset progress bar status.
        mProgressBarStatus = 0;

        new Thread(new Runnable() {
            public void run() {

                while (mProgressBarStatus < 100) {

                    // Process some tasks.
                    mProgressBarStatus = calculateProgress();

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Update the progress bar.
                    mProgressBarHandler.post(new Runnable() {
                        public void run() {
                            mProgressBar.setProgress(mProgressBarStatus);
                        }
                    });
                }

                // Once the progress bar has reached the maximum value, stop displaying it.
                if (mProgressBarStatus >= 100) {
                    mProgressBar.dismiss();
                }
            }
        }).start();
    }

    /**
     * Calculate progress of a data transfer.
     * @return Progress as an integer percentage in the range 0-100.
     */
    private int calculateProgress() {
        int percentComplete = 0;
        if (mSentPacketsNum > 10) {
            int remain = (mNumPacketsInFile - mSentPacketsNum) * 100 / mNumPacketsInFile;
            if (remain <= 0)
                percentComplete = 100;
            else
                percentComplete = 100 - remain;
        }
        return percentComplete;
    }

    /**
     * Display the connection progress dialog.
     */
    public void showConnectProgress() {
        mBluetoothConnectionProgressDialog = ProgressDialog.show(this, null, getResources().getString(R.string.progress_message1), false, true);
    }

    /**
     * Display the waiting progress dialog.
     */
    public void showWaitingProgress() {
        mWaitingProgressDialog = ProgressDialog.show(this, null, getResources().getString(R.string.progress_message2), false, true);
    }
    
    /**
     * Build and display the list view of controls for
     * the update tab that allows toggling the LED,
     * changing the language, rebooting the remote device, etc.
     */
    private void displayCotrolListView() {
        ArrayList<ControlItem> controlList = new ArrayList<ControlItem>();
        ControlItem aptx = new ControlItem(getResources().getString(R.string.aptx_control), false, "");
        controlList.add(aptx);
        ControlItem led = new ControlItem(getResources().getString(R.string.led_control), false, "");
        controlList.add(led);

        String changeLanguageDetail = "Change voice prompt language";
        if (mCurrentLanguageID >= 0)
            changeLanguageDetail = "Current language is : " + mLanguageList[mCurrentLanguageID];
        ControlItem changelanguage = new ControlItem(getResources().getString(R.string.change_language), false, changeLanguageDetail);
        controlList.add(changelanguage);

        String initUpdateTime = getString(R.string.no_update);
        if (mUpdateStatus != null && !"".equals(mUpdateStatus)) {
            initUpdateTime = mUpdateStatus;
        }
        ControlItem updateLanguage = new ControlItem(getResources().getString(R.string.update_language), false, initUpdateTime);

        controlList.add(updateLanguage);

        ControlItem changeMode = new ControlItem(getResources().getString(R.string.change_mode), false, "Reboot device");
        controlList.add(changeMode);

        mControlAdapter = new ControllistAdapter(this, controlList);
        mControlListView = (ListView) findViewById(R.id.control_listview);
        mControlListView.setAdapter(mControlAdapter);

        mControlListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == CONTROL_LISTVIEW_LED_ACTION) {
                    if (!mIsConnected)
                        return;
                    showWaitingProgress();
                    try {
                        mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_SET_LED_CONTROL, isLedOn ? 0 : 1);
                    }
                    catch (IOException e) {
                        toast("Failed to toggle LED");
                        Log.d(TAG, "Failure when sending LED set command: " + e.getMessage());
                    }
                    return;
                }
                if (position == CONTROL_LISTVIEW_APTX_ACTION) {
                    if (!mIsConnected || mPskeydata15 == null)
                        return;
                    showWaitingProgress();
                    if (isAptXOn)
                        mPskeydata15[12] = (byte) (mPskeydata15[12] & ~0x03);
                    else
                        mPskeydata15[12] = (byte) (mPskeydata15[12] | 0x03);

                    for (int i = 0; i < 14; ++i)
                        Log.d(TAG, "update Pskeydata15[" + i + "]=" + String.format("%02x", mPskeydata15[i]));
                    try {
                        mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_STORE_PS_KEY, mPskeydata15);
                    }
                    catch (IOException e) {
                        toast("Failed to set ps key for Apt-X");
                        Log.d(TAG,"Failed to set ps key data 15: " + e.getMessage());
                    }
                    return;
                }
                if (position == CONTROL_LISTVIEW_SWITCHLANGUAGE_ACTION) {
                    switchLanguage();
                    return;
                }
                if (position == CONTROL_LISTVIEW_UPDATELANGUAGE_ACTION) {
                    updateLanguage(view);
                    return;
                }
                if (position == CONTROL_LISTVIEW_REBOOT_ACTION) {
                    changeMode();
                }
            }
        });
    }

    /**
     * Build and display the list view that displays the items in the status tab.
     */
    private void displayInfoListView() {
        String nullString = getResources().getString(R.string.null_string);
        ArrayList<DeviceInfoItem> infoList = new ArrayList<DeviceInfoItem>();
        DeviceInfoItem signal = new DeviceInfoItem(getResources().getString(R.string.signal_label), true, "");
        infoList.add(signal);

        DeviceInfoItem battery = new DeviceInfoItem(getResources().getString(R.string.battery_label), true, "");
        infoList.add(battery);

        DeviceInfoItem version = new DeviceInfoItem(getResources().getString(R.string.api_version), false, nullString);
        infoList.add(version);

        DeviceInfoItem languageId = new DeviceInfoItem(getResources().getString(R.string.language_id_label), false, nullString);
        infoList.add(languageId);

        DeviceInfoItem bootmode = new DeviceInfoItem(getResources().getString(R.string.boot_mode), false, nullString);
        infoList.add(bootmode);

        mInfoAdapter = new InfolistAdapter(this, infoList);
        ListView listView = (ListView) findViewById(R.id.info_listview);
        listView.setAdapter(mInfoAdapter);
    }

    /**
     * List Adapter to display items in the status tab. Used by displayInfoListView()
     */
    private class InfolistAdapter extends BaseAdapter {

        private ArrayList<DeviceInfoItem> mInfolList;
        private LayoutInflater mInflater;

        public InfolistAdapter(Context context, ArrayList<DeviceInfoItem> list) {
            mInfolList = list;
            mInflater = LayoutInflater.from(context);
        }

        private class ViewHolder {
            TextView name;
            ImageView imageView;
            TextView description;
        }

        @Override
        public int getCount() {
            return mInfolList.size();
        }

        @Override
        public Object getItem(int position) {
            return mInfolList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.info_list, null);

                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.info_label);
                holder.imageView = (ImageView) convertView.findViewById(R.id.info_image);
                holder.description = (TextView) convertView.findViewById(R.id.info_text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            holder.name.setEnabled(mIsConnected);
            holder.imageView.setEnabled(mIsConnected);
            holder.description.setEnabled(mIsConnected);

            DeviceInfoItem selected = mInfolList.get(position);
            String item = selected.getName();

            holder.name.setText(item);
            if (selected.hasImage()) {
                if (item.equalsIgnoreCase(getResources().getString(R.string.signal_label))) {
                    holder.name.setText(getResources().getString(R.string.current_rssi_label) + mRssi + RssiUnit);
                    int resourceID = R.drawable.stat_sys_signal_null;
                    if (mRssi >= 0)
                        resourceID = R.drawable.stat_sys_signal_null;
                    else if (mRssi < -80)
                        resourceID = SIGNALINDICATORICON0 + 0;
                    else if (mRssi < -60)
                        resourceID = SIGNALINDICATORICON0 + 1;
                    else if (mRssi < -50)
                        resourceID = SIGNALINDICATORICON0 + 2;
                    else if (mRssi < -40)
                        resourceID = SIGNALINDICATORICON0 + 3;
                    else
                        resourceID = SIGNALINDICATORICON0 + 4;

                    holder.imageView.setImageResource(resourceID);
                }

                if (item.equalsIgnoreCase(getResources().getString(R.string.battery_label))) {
                    int level = mBattery * 100 / BATTERYTHRESHOLD;
                    if (mBattery >= BATTERYTHRESHOLD)
                        level = 100;
                    holder.name.setText(getResources().getString(R.string.battery_level_label) + mBattery + BatteryUnit);
                    holder.imageView.setImageResource(BATTERYINDICATORICON0 + level);
                }

                holder.imageView.setVisibility(android.view.View.VISIBLE);
                holder.description.setVisibility(android.view.View.INVISIBLE);
                return convertView;
            }

            if (!selected.hasImage()) {
                if (item.equalsIgnoreCase(getResources().getString(R.string.api_version)))
                    holder.description.setText(mAPIVersion);

                if (item.equalsIgnoreCase(getResources().getString(R.string.language_id_label)))
                    holder.description.setText(mCurrentLanguageID < 0 ? getResources().getString(R.string.null_string) : mLanguageList[mCurrentLanguageID]);

                if (item.equalsIgnoreCase(getResources().getString(R.string.boot_mode)))
                    holder.description.setText(mBootMode < 0 ? getResources().getString(R.string.null_string) : "" + mBootMode);

                holder.imageView.setVisibility(android.view.View.INVISIBLE);
                holder.description.setVisibility(android.view.View.VISIBLE);
            }
            return convertView;
        }
    }

    /**
     * List adapter to display items in the Update tab. Used in displayCotrolListView()
     */
    private class ControllistAdapter extends BaseAdapter {

        private ArrayList<ControlItem> controlList;
        private LayoutInflater mInflater;

        public ControllistAdapter(Context context, ArrayList<ControlItem> list) {
            controlList = list;
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return controlList.size();
        }

        public ControlItem getItem(int position) {
            return controlList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        private class ViewHolder {
            TextView name;
            CheckBox checkbox;
            TextView detail;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.control_list1, null);

                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.control_checkbox_label);
                holder.checkbox = (CheckBox) convertView.findViewById(R.id.control_checkbox_value);
                holder.detail = (TextView) convertView.findViewById(R.id.details);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            holder.name.setEnabled(mIsConnected);
            holder.checkbox.setEnabled(mIsConnected);
            holder.detail.setEnabled(mIsConnected);
            
            ControlItem selected = controlList.get(position);
            holder.name.setText(selected.getName());
            holder.detail.setText(selected.getDetails());

            if (selected.getName().equalsIgnoreCase(convertView.getResources().getString(R.string.aptx_control))) {
                holder.checkbox.setChecked(isAptXOn);
                holder.checkbox.setVisibility(android.view.View.VISIBLE);
            }

            if (selected.getName().equalsIgnoreCase(convertView.getResources().getString(R.string.led_control))) {
                holder.checkbox.setChecked(isLedOn);
                holder.checkbox.setVisibility(android.view.View.VISIBLE);
            }

            if (holder.name.getText().toString().equalsIgnoreCase(convertView.getResources().getString(R.string.change_language))) {
                holder.checkbox.setVisibility(android.view.View.INVISIBLE);
                if (mCurrentLanguageID >= 0)
                    holder.detail.setText("Current language is : " + mLanguageList[mCurrentLanguageID]);
            }

            if (holder.name.getText().toString().equalsIgnoreCase(convertView.getResources().getString(R.string.update_language))) {
                holder.checkbox.setVisibility(android.view.View.INVISIBLE);
                if (mUpdateStatus != null && !"".equals(mUpdateStatus)) {
                    holder.detail.setText(mUpdateStatus);
                }
            }

            if (holder.name.getText().toString().equalsIgnoreCase(convertView.getResources().getString(R.string.change_mode))) {
                holder.checkbox.setVisibility(android.view.View.INVISIBLE);
            }

            if (mWaitingProgressDialog != null)
                mWaitingProgressDialog.dismiss();

            return convertView;
        }
    }

    /**
     * Build and show a dialog that allows the voice prompt language to be changed.
     */
    public void switchLanguage() {
        if (!mIsConnected)
            return;

        ArrayList<String> lists = new ArrayList<String>();
        if (mTotalLanguageNum > mLanguageList.length) {
            Log.d(TAG, "Language array config is not right, please modify it!");
            return;
        }

        for (int i = 0; i < mTotalLanguageNum; i++)
            lists.add(mLanguageList[i]);

        final CharSequence[] items = lists.toArray(new CharSequence[lists.size()]);

        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Dialog));
        builder.setTitle("Switch Language");
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                AlertDialog dlg = (AlertDialog) arg0;
                int selected = dlg.getListView().getCheckedItemPosition();
                Log.d(TAG, "language choosed : " + (selected + 1));
                if (dlg.getListView().getCheckedItemCount() > 0 && selected != mCurrentLanguageID) {
                    try {
                        mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_SET_TTS_LANGUAGE, selected);
                    }
                    catch(IOException e) {
                        toast("Failed to change language.");
                        Log.d(TAG, "Failure when sending set language command: " + e.getMessage());
                    }
                    arg0.cancel();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                arg0.cancel();
            }
        });
        builder.setSingleChoiceItems(items, mCurrentLanguageID, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
            }
        });

        AlertDialog alert = builder.create();

        alert.show();
    }

    /**
     * Show a file dialog to allow the language to be updated.
     * @param view
     */
    public void updateLanguage(View view) {
        if (!mIsConnected)
            return;

        Intent intent = new Intent(view.getContext(), FileDialog.class);
        intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath() + "/voiceprompt");
        intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
        intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {"xuv", "ptn", "bin"});
        // Display the file dialog. Result returned from the dialog is processed in handleVoicePromptFileSelected()
        startActivityForResult(intent, REQUEST_FILEDIALOG_OK);
    }

    /**
     * Called from onActivityResult to handle the selected option in the update language dialogue.
     * @param requestCode The request code, used for debugging.
     * @param resultCode The result code (OK / CANCEL).
     * @param data Extra data from the dialogue - contains the file path.
     */
    void handleVoicePromptFileSelected(final int requestCode, final int resultCode, final Intent data) {
        // The result from the Update language file dialog.
        if (resultCode == Activity.RESULT_OK) {
            String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
            Log.d(TAG, "requestCode = " + requestCode + " Filepath=" + filePath);
            int updatepartition = checkUnmountedPartition();
            if (updatepartition >= 0) {
                Log.d(TAG, "going to write partition[" + updatepartition + "]");
                prepareToSendFile(filePath, updatepartition);
            } else {
                rebootDialog(NO_VALID_PARTITION);
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "file choose canceled ");
            File file = new File(Environment.getExternalStorageDirectory().getPath() + "/voiceprompt");
            if (!file.isDirectory()) {
                final CharSequence[] items = mBuiltinVoicePromptArray;
    
                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Dialog));
                builder.setTitle(getString(R.string.voice_prompt_dialog_title));
                builder.setCancelable(false);
				builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        AlertDialog dlg = (AlertDialog) arg0;
                        int selected = dlg.getListView().getCheckedItemPosition();
                        int resId = -1;
                        if (selected == 0)
                            resId = R.raw.vp1;
                        else if (selected == 1)
                            resId = R.raw.vp2;
                        int updatepartition = checkUnmountedPartition();
                        if (resId > 0 && checkUnmountedPartition() >= 0) {
                            Log.d(TAG, "going to write partition[" + updatepartition + "]");
                            prepareToSendFile(resId, updatepartition);
                        }
                        arg0.cancel();
                    }
                });
                builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        arg0.cancel();
                    }
                });
                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                    }
                });
    
                AlertDialog alert = builder.create();
    
                alert.show();    
            }
        }
    }

    /**
     * Launch the system Bluetooth settings to allow the user to scan for and pair with a device.
     */
    private void scanDevice() {
        Intent intentBluetooth = new Intent();
        intentBluetooth.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intentBluetooth);
    }

    /**
     * Reset the remote device.
     */
    private void changeMode() {

        if (!mIsConnected)
            return;

        // Display a dialog to ask the user to confirm the reboot.
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Dialog));
        builder.setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.confirmation).setMessage(R.string.confirmation_reboot).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_DEVICE_RESET);
                } 
                catch (IOException e) {
                    toast("Failed to send reboot command");
                    Log.d(TAG,"Failure when sending reboot command: " + e.getMessage());
                }
                dialog.dismiss();
            }
        }).setNegativeButton(R.string.no, null).show();
    }

    /**
     * Add a tab to the user interface.
     * @param context Application's context.
     * @param text The text to display in the tab. 
     * @return A View object for the tab.
     */
    private static View createTabView(final Context context, final String text) {
        View view = LayoutInflater.from(context).inflate(R.layout.tabs_bg, null);
        TextView tv = (TextView) view.findViewById(R.id.tabsText);
        ImageView tabImage = (ImageView) view.findViewById(R.id.tabimage);
        // Image to display in the tab depends on its text. There is a special image for the "Information" tab.
        if (text.equalsIgnoreCase("Information"))
            tabImage.setImageResource(R.drawable.information);
        else
            tabImage.setImageResource(R.mipmap.star_big_on);
        tv.setText(text);
        return view;
    }

    /**
     * Show a dialog that allows the remote device to be reset.
     * @param reasonId The reason the reboot was requested. Controls the message displayed in the dialog.
     */
    private void rebootDialog(int reasonId) {

        int messageId;
        if (reasonId == ENABLE_APTX_ACTION)
            messageId = R.string.confirmation_reboot_for_aptx;
        else if (reasonId == UPLOAD_LANGUAGE_ACTION)
            messageId = R.string.confirmation_reboot_for_languagechange;
        else if (reasonId == NO_VALID_PARTITION)
            messageId = R.string.no_valid_partition;
        else if (reasonId == RESTORE_PSKEY15_FORDISCOVERABLEMODE_ACTION)
            messageId = R.string.restore_discoverablemode;
        else
            return;

        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Dialog));
        builder.setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.confirmation).setMessage(messageId).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                try {
                    mGaiaLink.sendCommand(Gaia.VENDOR_CSR, Gaia.COMMAND_DEVICE_RESET);
                } 
                catch (IOException e) {
                    toast("Failed to send reboot command");
                    Log.d(TAG,"Failure when sending reboot command: " + e.getMessage());
                }
                dialog.dismiss();
            }
        }).setNegativeButton(R.string.no, null).show();
    }


    /**
     * Save local preferences.
     */
    private void storePreference() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy h:mmaa");
        mUpdateStatus = getString(R.string.update_at)+ sdf.format(new Date());
        prefs.edit().putString(LANGUAGE_UPDATE_TIME_PREF_KEY, mUpdateStatus);
        if (prefs.edit().commit())
            Log.d(TAG, "store preference successfully at :" + mUpdateStatus);
        mControlAdapter.notifyDataSetChanged();
    }


    /**
     * Find one unmounted partition before opening and writing a storage partition.
     * @return The partition number found or -1 if no partition was found.
     */
    private int checkUnmountedPartition() {
        int value = mPartitionID;
        int unmounted = 0;
        while (((value) & 1) == 1 && unmounted < mTotalLanguageNum) {
            unmounted++;
            value >>= 1;
        }

        Log.d(TAG, "mountedPartition=" + unmounted);
        if (unmounted == mTotalLanguageNum)
            return -1;

        return unmounted;
    }

    private void updateSelectionMenuItem() {
        if( mOptionMenu != null )
        {
            if(mIsConnected)
                mOptionMenu.getItem(SELECTION_ID).setTitle(getString(R.string.disconnect));
            else
                mOptionMenu.getItem(SELECTION_ID).setTitle(getString(R.string.selection));
        }
    }
    
    private void showAboutDialog(){
        final TextView message = new TextView(this);
        final SpannableString s = new SpannableString(getText(R.string.about_dialog_message));
        Linkify.addLinks(s, Linkify.WEB_URLS);
        message.setText(s);
        message.setMovementMethod(LinkMovementMethod.getInstance());

        new AlertDialog.Builder(this)
         .setTitle(R.string.about_dialog_title)
         .setCancelable(true)
         .setIcon(android.R.drawable.ic_dialog_info)
         .setPositiveButton(R.string.yes, null)
         .setView(message)
         .create().show();
    }
}
