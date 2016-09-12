package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class ChargerModel {
    interface Callback {
        void Response(byte[] msg);
    }

    private static final String PRIVATE_SERVICE_UUID = "f4f232be-5a53-11e6-8b77-86f30ca893d3";
    private static final String READER_CHARACTERISTIC_UUID = "1d4b745a-5a54-11e6-8b77-86f30ca893d3";
    private static final String WRITER_CHARACTERISTIC_UUID = "e25328b0-5a54-11e6-8b77-86f30ca893d3";

    private static final String TAG = "NEO_ChargerModel";

    private static BluetoothGatt mGatt;
    private static BluetoothGattCharacteristic reader;
    private static BluetoothGattCharacteristic writer;

    private static final byte START_BYTE = '|';
    private static final byte END_BYTE = '|';

    private static final byte writeReg = (byte)0x80;
    private static final byte readReg = (byte)0x00;
    private static final byte writeEEprom = (byte)0x40;
    private static final byte readEEprom = (byte)0x00;
    private static final byte c_cmd_ee_data_high = (byte)0x05;
    private static final byte c_cmd_ee_data_low = (byte)0x06;
    private static final byte c_cmd_ee_addr_high = (byte)0x07;
    private static final byte c_cmd_ee_addr_low = (byte)0x08;

    private static final LinkedList<Byte> readBuffer = new LinkedList<>();
    private static final LinkedList<CallbackItem> callbacks = new LinkedList<>();

    private static Thread callbackManager;

    public static void collectCharacteristics(BluetoothGatt gatt) {
        //Escape
        if (gatt == null)
            return;

        mGatt = gatt;

        for (BluetoothGattService service : mGatt.getServices()) {
            if (service.getUuid().compareTo(UUID.fromString(PRIVATE_SERVICE_UUID)) == 0)
            {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    if (characteristic.getUuid().compareTo(UUID.fromString(READER_CHARACTERISTIC_UUID)) == 0) {
                        reader = characteristic;
                    }
                    else if (characteristic.getUuid().compareTo(UUID.fromString(WRITER_CHARACTERISTIC_UUID)) == 0) {
                        writer = characteristic;
                    }
                }

                break;
            }
        }

        List<BluetoothGattDescriptor> list = reader.getDescriptors();

        BluetoothGattDescriptor descriptor = list.get(0);

        mGatt.setCharacteristicNotification(reader, true);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        mGatt.writeDescriptor(descriptor);
    }

    public static void onCharacteristicRead(BluetoothGattCharacteristic characteristic)
    {
        byte[] bytes = characteristic.getValue();

        if (bytes == null) {
            Log.w(TAG, "Bytes == null");
            return;
        }

        for (byte b : bytes) {
            readBuffer.add(b);
        }
    }

    public static void writeCharacteristic(byte[] msg)
    {
        writer.setValue(msg);
        mGatt.writeCharacteristic(writer);
    }

    public static void getCableResistance()
    {
        byte[] msg = new byte[]{
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                0x01,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };
        ChargerModel.writeCharacteristic(msg);

        WaitForResponse(2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "one: " + String.format("%02X" ,msg[0]) + " two: " + String.format("%02X" ,msg[1]));
            }
        });
    }

    private static void WaitForResponse(int bytesToWait, Callback callback)
    {
        callbacks.add(new CallbackItem(bytesToWait, callback));

        if (callbackManager == null || !callbackManager.isAlive())
        {
            callbackManager = new Thread()
            {
                public void run() {
                    while (true)
                    {
                        while (!callbacks.isEmpty())
                        {
                            CallbackItem callbackItem = callbacks.removeFirst();

                            byte[] msg = new byte[callbackItem.mBytesToRead];
                            int bytesReadCount = 0;

                            while (bytesReadCount < callbackItem.mBytesToRead)
                            {
                                if (!readBuffer.isEmpty())
                                {
                                    msg[bytesReadCount++] = readBuffer.removeFirst();
                                }
                            }

                            callbackItem.mCallback.Response(msg);
                        }
                    }
                }
            };
            callbackManager.start();
        }
    }
}
