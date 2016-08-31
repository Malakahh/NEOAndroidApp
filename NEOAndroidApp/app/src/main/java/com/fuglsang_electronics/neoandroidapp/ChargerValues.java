package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.List;

public class ChargerValues {
    public static List<BluetoothGattService> mServices;

    private static String TAG = "ChargerValues";
    private static boolean mReading = false;
    private static boolean mBlockReading = false;
    private static Thread mReadThread;

    private static BluetoothGatt mGatt;


    public static void readValues(BluetoothGatt gatt) {
        //Escape
        if (mReading || gatt == null)
            return;

        mGatt = gatt;
        mServices = mGatt.getServices();
        mReading = true;

        mReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doRead();
            }
        });
        mReadThread.start();
    }

    private static void doRead()
    {
        for (int i = 0; i < mServices.size(); i++) {
            List<BluetoothGattCharacteristic> characteristics = mServices.get(i).getCharacteristics();
            for (int k = 0; k < characteristics.size(); k++) {
                while (true) {
                    if (!mBlockReading) {
                        mBlockReading = true;
                        mGatt.readCharacteristic(characteristics.get(k));
                        break;
                    }
                }
            }
        }

        mReading = false;
    }

    public static void onValueRead(BluetoothGattCharacteristic characteristic, int status)
    {
        String s = "";
        byte[] bytes = characteristic.getValue();

        if (bytes == null) {
            Log.w(TAG, "Bytes == null");
            mBlockReading = false;
            return;
        }

        for (byte b : bytes) {
            s += String.format("%02X", b);
        }

        Log.w(TAG, "UUID: " + characteristic.getUuid() + " char: " + s);
        mBlockReading = false;
    }
}
