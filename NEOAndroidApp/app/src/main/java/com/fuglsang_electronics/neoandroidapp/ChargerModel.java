package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ChargerModel {
    public enum LEDStatus {
        ON, OFF, SLOW_BLINK, FAST_BLINK
    }

    interface Callback {
        void Response(final byte[] msg);
    }

    interface LEDStatusCallback {
        void Response(LEDStatus green, LEDStatus yellow, LEDStatus red);
    }

    interface ProgrammeNameCallback {
        void Response(String programmeName);
    }

    interface LogCounterCallback {
        void Response(int count);
    }

    interface ChargeCallback {
        void Response(int value);
    }

    private static final String PRIVATE_SERVICE_UUID = "f4f232be-5a53-11e6-8b77-86f30ca893d3";
    private static final String READER_CHARACTERISTIC_UUID = "1d4b745a-5a54-11e6-8b77-86f30ca893d3";
    private static final String WRITER_CHARACTERISTIC_UUID = "e25328b0-5a54-11e6-8b77-86f30ca893d3";

    private static final String TAG = "NEO_ChargerModel";

    private static BluetoothGatt mGatt;
    private static BluetoothGattCharacteristic reader;
    private static BluetoothGattCharacteristic writer;

    //Utility
    private static final byte writeReg = (byte)0x80;
    private static final byte readReg = (byte)0x00;
    private static final byte writeEEprom = (byte)0x40;
    //private static final byte readEEprom = (byte)0x00;
    private static final byte START_BYTE = '|';
    private static final byte END_BYTE = '|';

    //Register Layout
    private static final byte c_cmd_ee_data_high = (byte)0x05;
    private static final byte c_cmd_ee_data_low = (byte)0x06;
    private static final byte c_cmd_ee_addr_high = (byte)0x07;
    private static final byte c_cmd_ee_addr_low = (byte)0x08;
    private static final byte c_charge_volt_meas_low = (byte)0x12;
    private static final byte c_charge_volt_meas_high = (byte)0x13;
    private static final byte c_charge_curr_meas_low = (byte)0x14;
    private static final byte c_charge_curr_meas_high = (byte)0x15;
    private static final byte c_charge_pstep_number = (byte)0x18;
    private static final byte c_led_mode = (byte)0x1E;
    //private static final byte c_log_clear_control = (byte)0x21;

    //eeprom layout
    private static final byte ee_log_cnt_charg = (byte)0x1A;
    private static final byte ee_log_cnt_error = (byte)0x1B;
    private static final byte ee_log_cnt_depth = (byte)0x1C;
    private static final byte ee_program_name_1_2 = (byte)0x1E;
    private static final byte ee_program_name_3_4 = (byte)0x1F;
    private static final byte ee_program_name_5_6 = (byte)0x20;
    private static final byte ee_program_name_7_8 = (byte)0x21;

    private static final LinkedBlockingQueue<Byte> readBuffer = new LinkedBlockingQueue<Byte>();
    private static final LinkedBlockingQueue<CallbackItem> callbacks = new LinkedBlockingQueue<CallbackItem>();

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

        PostResponse();
    }

    public static void writeCharacteristic(byte[] msg)
    {
        writer.setValue(msg);
        mGatt.writeCharacteristic(writer);

        SystemClock.sleep(200);
    }

    /*
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
*/

    private static boolean running = false;
    private static void PostResponse() {
        Log.w(TAG, "readBuffer sie: " + readBuffer.size());
        if (running && !callbacks.isEmpty() && callbacks.peek().mBytesToRead > 0 && readBuffer.size() >= callbacks.peek().mBytesToRead) {
            CallbackItem callbackItem = callbacks.poll();
            byte[] msg = new byte[callbackItem.mBytesToRead];
            Log.w(TAG, "PostResponse, yay");
            for (int i = 0; i < msg.length; i++) {
                msg[i] = readBuffer.poll();

                if (msg[i] > 47) {
                    Log.w(TAG, "MSG: " + (char)msg[i]);
                }
                else {
                    Log.w(TAG, "MSG: " + msg[i]);
                }
            }

            callbackItem.mCallback.Response(msg);
            running = false;
        }

//        else if (running && !callbacks.isEmpty() && callbacks.peek().mBytesToRead == 0) {
//            callbacks.poll();
//            running = false;
//            Log.w(TAG, "Query");
//        }

        NextCommand();
    }

    private static void NextCommand()
    {
        Log.w(TAG, "NextCommand");
        if (!running && !callbacks.isEmpty()) {
            running = true;

            if (callbacks.peek().mBytesToRead > 0) {
                ChargerModel.writeCharacteristic(callbacks.peek().mQuery);
                Log.w(TAG, "Waiting for response");
            }
            else {
                Log.w(TAG, "Query");
                while (!callbacks.isEmpty() && callbacks.peek().mBytesToRead == 0) {
                    ChargerModel.writeCharacteristic(callbacks.poll().mQuery);
                }

                if (!callbacks.isEmpty()) {
                    Log.w(TAG, "ChargerWrite");
                    ChargerModel.writeCharacteristic(callbacks.peek().mQuery);
                }
                else {
                    Log.w(TAG, "Running = false");
                    running = false;
                }
            }
        }
    }

    private static void WaitForResponse(byte[] query, int bytesToWait, Callback callback)
    {
        callbacks.add(new CallbackItem(query, bytesToWait, callback));

        NextCommand();
    }

    private static void SendQuery(byte[] query) {
        WaitForResponse(query, 0, null);
    }

    public static void clearBuffer()
    {
        readBuffer.clear();
        callbacks.clear();
    }

    public static void getLEDStatus(final LEDStatusCallback callback)
    {
        byte[] msg = new byte[] {
                START_BYTE,
                c_led_mode | readReg,
                END_BYTE
        };

        WaitForResponse(msg, 1, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "msg: " + msg[0]);

                byte bitmask = msg[0];
                LEDStatus green = LEDStatus.OFF;
                LEDStatus yellow = LEDStatus.OFF;
                LEDStatus red = LEDStatus.OFF;

                if (((bitmask >> 0) & 1) == 1) {
                    green = LEDStatus.ON;
                }
                else if (((bitmask >> 1) & 1) == 1) {
                    green = LEDStatus.SLOW_BLINK;
                }

                if (((bitmask >> 2) & 1) == 1) {
                    yellow = LEDStatus.ON;
                }
                else if (((bitmask >> 3) & 1) == 1) {
                    yellow = LEDStatus.SLOW_BLINK;
                }
                else if (((bitmask >> 4) & 1) == 1) {
                    yellow = LEDStatus.FAST_BLINK;
                }

                if (((bitmask >> 5) & 1) == 1) {
                    red = LEDStatus.ON;
                }
                else if (((bitmask >> 6) & 1) == 1) {
                    red = LEDStatus.SLOW_BLINK;
                }

                final LEDStatus g = green;
                final LEDStatus y = yellow;
                final LEDStatus r = red;

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(g, y, r);
                    }
                });
            }
        });
    }

    public static void getProgrammeName(final ProgrammeNameCallback callback)
    {
        final byte[] response = new byte[8];

        //read ee_program_name_1_2
        byte[] msg_1_2 = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_1_2,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        //read ee_program_name_3_4
        byte[] msg_3_4 = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_3_4,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        //read ee_program_name_5_6
        byte[] msg_5_6 = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_5_6,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        //read ee_program_name_7_8
        byte[] msg_7_8 = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_7_8,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        WaitForResponse(msg_1_2, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "msg_1_2 - " + msg[0] + " - " + msg[1]);
                response[0] = msg[0];
                response[1] = msg[1];
            }
        });

        WaitForResponse(msg_3_4, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "msg_3_4 - " + msg[0] + " - " + msg[1]);
                response[2] = msg[0];
                response[3] = msg[1];
            }
        });

        WaitForResponse(msg_5_6, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "msg_5_6 - " + msg[0] + " - " + msg[1]);
                response[4] = msg[0];
                response[5] = msg[1];
            }
        });

        WaitForResponse(msg_7_8, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "msg_7_8 - " + msg[0] + " - " + msg[1]);
                response[6] = msg[0];
                response[7] = msg[1];

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(new String(response));
                    }
                });
            }
        });
    }

    public static void getLogCounterCharges(final LogCounterCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_charg,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        Log.w(TAG, "LogChargers");

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - Charges: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(i);
                    }
                });
            }
        });
    }

    public static void getLogCountersErrors(final LogCounterCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_error,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - Errors: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(i);
                    }
                });
            }
        });
    }

    public static void getLogCountersDepthDischarges(final LogCounterCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_depth,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg,
                END_BYTE
        };

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void Response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - DepthDischarges: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(i);
                    }
                });
            }
        });
    }

    public static void getChargeVoltage(final ChargeCallback callback) {
        byte[] msg_high = new byte[] {
                START_BYTE,
                c_charge_volt_meas_high | readReg,
                END_BYTE
        };

        byte[] msg_low = new byte[] {
                START_BYTE,
                c_charge_volt_meas_low | readReg,
                END_BYTE
        };

        final byte[] response = new byte[2];

        WaitForResponse(msg_high, 1, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "ChargeVoltage - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        WaitForResponse(msg_low, 1, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "ChargeVoltage - low: " + (msg[0] & 0xFF));
                response[1] = msg[0];

                final int i = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                Log.w(TAG, "ChargeVolt - total: " + i);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(i);
                    }
                });
            }
        });
    }

    public static void getChargeCurrent(final ChargeCallback callback) {
        byte[] msg_high = new byte[] {
                START_BYTE,
                c_charge_curr_meas_high | readReg,
                END_BYTE
        };

        byte[] msg_low = new byte[] {
                START_BYTE,
                c_charge_curr_meas_low | readReg,
                END_BYTE
        };

        final byte[] response = new byte[2];

        WaitForResponse(msg_high, 1, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "ChargeCurrent - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        WaitForResponse(msg_low, 1, new Callback() {
            @Override
            public void Response(byte[] msg) {
                Log.w(TAG, "ChargeCurrent - low: " + (msg[0] & 0xFF));
                response[1] = msg[0];

                final int i = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                Log.w(TAG, "ChargeCurrent - total: " + i);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(i);
                    }
                });
            }
        });
    }

    public static void getChargeProgramStep(final ChargeCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                c_charge_pstep_number | readReg,
                END_BYTE
        };

        WaitForResponse(msg, 1, new Callback() {
            @Override
            public void Response(final byte[] msg) {
                Log.w(TAG, "ChargeProgramStep: " + (msg[0] & 0xFF));

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.Response(msg[0] & 0xFF);
                    }
                });
            }
        });
    }

    public static void ClearLogCounters()
    {
        byte[] msg_charge = new byte[] {
                START_BYTE,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_charg,
                END_BYTE
        };

        byte[] msg_error = new byte[] {
                START_BYTE,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_error,
                END_BYTE
        };

        byte[] msg_depthDiscarges = new byte[] {
                START_BYTE,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_depth,
                END_BYTE
        };

        SendQuery(msg_charge);
        SendQuery(msg_error);
        SendQuery(msg_depthDiscarges);
    }
}
