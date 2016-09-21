package com.fuglsang_electronics.neoandroidapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

public class BluetoothController {
    interface ConnectionCallback {
        void onConnectionEstablished();
    }

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD_MS = 10000;
    private static final String TAG = "NEO_BluetoothController";

    public static boolean mConnected = false;

    private static Handler mHandler;
    private static BluetoothAdapter mBluetoothAdapter;
    private static BluetoothGatt mBluetoothGatt;
    private static boolean mScanning;

    public static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();
    private static Vector _leScanEventListeners;

    private static ConnectionCallback mConnectionCallback;

    public interface onLeScan {
        void onDeviceDiscovered(final BluetoothDevice device);
    }

    public static void addOnLeScanListener(onLeScan listener)
    {
        if (_leScanEventListeners == null)
            _leScanEventListeners = new Vector();

        _leScanEventListeners.add(listener);
    }

    private static void fireOnLeScan(final BluetoothDevice device)
    {
        if (_leScanEventListeners != null && !_leScanEventListeners.isEmpty())
        {
            Enumeration e = _leScanEventListeners.elements();
            while (e.hasMoreElements())
            {
                onLeScan event = (onLeScan)e.nextElement();
                event.onDeviceDiscovered(device);
            }
        }
    }

    // Device scan callback.
    private static BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    if (!mDevices.contains(device))
                    {
                        mDevices.add(device);
                        fireOnLeScan(device);
                    }
                }
            };

    private static BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.w(TAG, "Connected");

                mBluetoothGatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT_SUCCESS");

                //_ChargerValues.readValues(gatt);
                ChargerModel.collectCharacteristics(gatt);
                mConnectionCallback.onConnectionEstablished();
            }
            else {
                Log.w(TAG, "GATT_SUCCESS failed");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            super.onCharacteristicRead(gatt, characteristic, status);
            ChargerModel.onCharacteristicRead(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            gatt.readCharacteristic(characteristic);
        }
    };

    public static void setupBluetooth(Activity activity) {
        mHandler = new Handler();

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) activity.getBaseContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mBluetoothAdapter.enable();
    }

    public static void scanBLEDevices(final boolean enable) {
        if (enable) {
            mScanning = true;

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public static void connectBluetooth(BluetoothDevice device, Context context, ConnectionCallback callback)
    {
        mConnectionCallback = callback;
        mBluetoothGatt = device.connectGatt(context, true, mBluetoothGattCallback);
        mConnected = true;
    }

}