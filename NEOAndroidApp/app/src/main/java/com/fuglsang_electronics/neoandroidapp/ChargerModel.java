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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

class ChargerModel {
    enum LEDStatus {
        ON, OFF, SLOW_BLINK, FAST_BLINK
    }

    interface Callback {
        void response(final byte[] msg);
    }

    interface LEDStatusCallback {
        void response(LEDStatus green, LEDStatus yellow, LEDStatus red);
    }

    interface StringCallback {
        void response(String programmeName);
    }

    interface IntCallback {
        void response(int value);
    }

    interface ListCallback {
        void response(List<Byte> value);
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
    //private static final byte END_BYTE = '|';

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
    private static final byte ee_log_ee_size = (byte)0x1D;
    private static final byte ee_program_name_1_2 = (byte)0x1E;
    private static final byte ee_program_name_3_4 = (byte)0x1F;
    private static final byte ee_program_name_5_6 = (byte)0x20;
    private static final byte ee_program_name_7_8 = (byte)0x21;
    private static final byte ee_program_size = (byte)0x22;
    private static final byte ee_program_area = (byte)0x23;

    private static final LinkedBlockingQueue<Byte> readBuffer = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<CallbackItem> callbacks = new LinkedBlockingQueue<>();

    private static final int mNotificationDelay = 200;

    private static Timer mTimeoutResponseTimer = new Timer();
    private static final int mTimeout = mNotificationDelay * 2;
    private static boolean mRunning = false;

    static void collectCharacteristics(BluetoothGatt gatt) {
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

    static void onCharacteristicRead(BluetoothGattCharacteristic characteristic)
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

    static void writeCharacteristic(byte[] msg)
    {
        writer.setValue(msg);
        mGatt.writeCharacteristic(writer);

        //Delay to allow for bluetooth notification to take place
        SystemClock.sleep(mNotificationDelay);
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
            public void response(byte[] msg) {
                Log.w(TAG, "one: " + String.format("%02X" ,msg[0]) + " two: " + String.format("%02X" ,msg[1]));
            }
        });
    }
*/

    private static void TimeoutResponse() {
        mRunning = false;

        Log.w(TAG + "Timeout", "Timeout!");

        readBuffer.clear();
        NextCommand();
    }

    public static void StartTimeoutTimer() {
        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                TimeoutResponse();
            }
        };
        mTimeoutResponseTimer = new Timer(); //This is new
        mTimeoutResponseTimer.schedule(timertask, mTimeout * callbacks.peek().mBytesToRead);
    }

    private static void PostResponse() {
        Log.w(TAG, "readBuffer size: " + readBuffer.size());

        if (mRunning && !callbacks.isEmpty() && callbacks.peek().mBytesToRead > 0 && readBuffer.size() >= callbacks.peek().mBytesToRead) {
            mTimeoutResponseTimer.cancel();

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

            callbackItem.mCallback.response(msg);
            mRunning = false;
        }

        NextCommand();
    }

    private static void NextCommand()
    {
        Log.w(TAG, "NextCommand");

        if (callbacks.isEmpty()) {
            Log.w(TAG, "callbacks is empty");
        }
        else {
            Log.w(TAG, "callbacks is NOT empty");
        }

        if (!mRunning && !callbacks.isEmpty()) {
            mRunning = true;

            if (callbacks.peek().mBytesToRead > 0) {
                ChargerModel.writeCharacteristic(callbacks.peek().mQuery);

                StartTimeoutTimer();

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
                    mRunning = false;
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

    static void clearBuffer()
    {
        readBuffer.clear();
        callbacks.clear();
    }

    static void getLEDStatus(final LEDStatusCallback callback)
    {
        byte[] msg = new byte[] {
                START_BYTE,
                0x01,
                c_led_mode | readReg
        };

        WaitForResponse(msg, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
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
                        callback.response(g, y, r);
                    }
                });
            }
        });
    }

    static void getProgrammeName(final StringCallback callback)
    {
        final byte[] response = new byte[8];

        //read ee_program_name_1_2
        byte[] msg_1_2 = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_1_2,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_3_4
        byte[] msg_3_4 = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_3_4,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_5_6
        byte[] msg_5_6 = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_5_6,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_7_8
        byte[] msg_7_8 = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_7_8,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        WaitForResponse(msg_1_2, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_1_2 - " + msg[0] + " - " + msg[1]);
                response[0] = msg[0];
                response[1] = msg[1];
            }
        });

        WaitForResponse(msg_3_4, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_3_4 - " + msg[0] + " - " + msg[1]);
                response[2] = msg[0];
                response[3] = msg[1];
            }
        });

        WaitForResponse(msg_5_6, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_5_6 - " + msg[0] + " - " + msg[1]);
                response[4] = msg[0];
                response[5] = msg[1];
            }
        });

        WaitForResponse(msg_7_8, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_7_8 - " + msg[0] + " - " + msg[1]);
                response[6] = msg[0];
                response[7] = msg[1];

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(new String(response));
                    }
                });
            }
        });
    }

    static void getLogCounterCharges(final IntCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_charg,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        Log.w(TAG, "LogChargers");

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - Charges: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(i);
                    }
                });
            }
        });
    }

    public static void getLogCountersErrors(final IntCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_error,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - Errors: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(i);
                    }
                });
            }
        });
    }

    public static void getLogCountersDepthDischarges(final IntCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_depth,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        WaitForResponse(msg, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                final int i = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                Log.w(TAG, "LogCount - DepthDischarges: " + i);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(i);
                    }
                });
            }
        });
    }

    public static void getChargeVoltage(final IntCallback callback) {
        byte[] msg_high = new byte[] {
                START_BYTE,
                0x01,
                c_charge_volt_meas_high | readReg
        };

        byte[] msg_low = new byte[] {
                START_BYTE,
                0x01,
                c_charge_volt_meas_low | readReg
        };

        final byte[] response = new byte[2];

        WaitForResponse(msg_high, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeVoltage - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        WaitForResponse(msg_low, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeVoltage - low: " + (msg[0] & 0xFF));
                response[1] = msg[0];

                final int i = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                Log.w(TAG, "ChargeVolt - total: " + i);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(i);
                    }
                });
            }
        });
    }

    public static void getChargeCurrent(final IntCallback callback) {
        byte[] msg_high = new byte[] {
                START_BYTE,
                0x01,
                c_charge_curr_meas_high | readReg
        };

        byte[] msg_low = new byte[] {
                START_BYTE,
                0x01,
                c_charge_curr_meas_low | readReg
        };

        final byte[] response = new byte[2];

        WaitForResponse(msg_high, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeCurrent - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        WaitForResponse(msg_low, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeCurrent - low: " + (msg[0] & 0xFF));
                response[1] = msg[0];

                final int i = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                Log.w(TAG, "ChargeCurrent - total: " + i);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(i);
                    }
                });
            }
        });
    }

    public static void getChargeProgramStep(final IntCallback callback) {
        byte[] msg = new byte[] {
                START_BYTE,
                0x01,
                c_charge_pstep_number | readReg
        };

        WaitForResponse(msg, 1, new Callback() {
            @Override
            public void response(final byte[] msg) {
                Log.w(TAG, "ChargeProgramStep: " + (msg[0] & 0xFF));

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.response(msg[0] & 0xFF);
                    }
                });
            }
        });
    }

    public static void clearLogCounters()
    {
        byte[] msg_charge = new byte[] {
                START_BYTE,
                0x08,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_charg
        };

        byte[] msg_error = new byte[] {
                START_BYTE,
                0x08,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_error
        };

        byte[] msg_depthDiscarges = new byte[] {
                START_BYTE,
                0x08,
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_depth
        };

        SendQuery(msg_charge);
        SendQuery(msg_error);
        SendQuery(msg_depthDiscarges);
    }

    public static void getProgramSize(final IntCallback callback) {
        byte[] msg_programSize = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_size,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        WaitForResponse(msg_programSize, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                callback.response(((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF));
            }
        });
    }

    public static void getLogSize(final IntCallback callback) {
        byte[] msg_logSize = new byte[] {
                START_BYTE,
                0x06,
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_ee_size,
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        WaitForResponse(msg_logSize, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                callback.response(((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF));
            }
        });
    }

    public static void getLog(final ListCallback callback, final IntCallback onByteReceivedCallback) {
        final ValContainer<Integer> programSize = new ValContainer<>();

        getProgramSize(new IntCallback() {
            @Override
            public void response(int value) {
                programSize.setVal(value);
            }
        });

        getLogSize(new IntCallback() {
            @Override
            public void response(final int logSize) {
                final List<Byte> log = new ArrayList<>();

                for (int i = 0; i < logSize; i++) {
                    final int current = i;

                    int fullAddr = ee_program_area + programSize.getVal() + i;
                    byte addrHigh = (byte)((fullAddr & (0xFF << 8)) >> 8);
                    byte addrLow = (byte)(fullAddr & 0xFF);

                    Log.w(TAG, "AddrHigh: " + addrHigh + " - AddrLow: " + addrLow);

                    byte[] msgLogByte = new byte[] {
                            START_BYTE,
                            0x06,
                            c_cmd_ee_addr_high | writeReg,
                            addrHigh,
                            c_cmd_ee_addr_low | writeReg,
                            addrLow,
                            c_cmd_ee_data_high | readReg,
                            c_cmd_ee_data_low | readReg
                    };

                    WaitForResponse(msgLogByte, 2, new Callback() {
                        @Override
                        public void response(byte[] msg) {
                            log.add(msg[0]);
                            log.add(msg[1]);

                            if (onByteReceivedCallback != null) {
                                onByteReceivedCallback.response(msg[0]);
                                onByteReceivedCallback.response(msg[1]);
                            }

                            if (current == logSize - 1) {
                                callback.response(log);
                            }
                        }
                    });
                }
            }
        });
    }

    public static void getLog(final ListCallback callback) {
        getLog(callback, null);
    }

}
