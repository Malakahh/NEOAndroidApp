package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;

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

    interface ListByteCallback {
        void response(List<Byte> value);
    }

    interface ListLogHeaderCallback {
        void response(List<LogHeader> value);
    }

    interface LogHeaderCallback {
        void response(LogHeader value);
    }

    interface ActionCallback {
        void response();
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
    private static final byte CHECKSUM_LENGTH_BYTES = 4;

    //Register Layout
    private static final byte m_gbc_operation_mode = (byte)0x04;
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
    public static final byte ee_program_area = (byte)0x23;

    private static final LinkedBlockingQueue<Byte> readBuffer = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<CallbackItem> callbacks = new LinkedBlockingQueue<>();

    private static Timer mTimeoutResponseTimer = new Timer();
    private static final int mTimeout = 4000;
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

    static void onCharacteristicRead(BluetoothGattCharacteristic characteristic) {
        byte[] bytes = characteristic.getValue();

        if (bytes == null) {
            Log.w(TAG, "Bytes == null");
            return;
        }

        for (byte b : bytes) {
            readBuffer.add(b);
            Log.w("BUFFER", String.format("%02X", (int)b & 0xFF));
        }

        PostResponse();
    }

    private static void writeCharacteristic(byte[] cmd) {
        byte[] msg = new byte[cmd.length + 2 + CHECKSUM_LENGTH_BYTES];

        msg[0] = START_BYTE;
        msg[1] = (byte)cmd.length;

        CRC32 crc = new CRC32();
        crc.update(cmd);
        long checksum = crc.getValue();

        for (int i = 0; i < CHECKSUM_LENGTH_BYTES; i++) {
            msg[2 + i] = (byte)(checksum >> (CHECKSUM_LENGTH_BYTES - 1 - i) * 8);
        }

        //Log.w("CHKSUM", "eh: " + String.format("%08X", checksum) + " - " + String.format("%02X", msg[2]) + String.format("%02X", msg[3]) + String.format("%02X", msg[4]) + String.format("%02X", msg[5]));
        Log.w("CHKSUM", "eh: " + String.format("%08X", checksum));

        for (int i = 0; i < cmd.length; i++) {
            msg[2 + CHECKSUM_LENGTH_BYTES + i] = cmd[i];
        }

        writer.setValue(msg);
        mGatt.writeCharacteristic(writer);
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

        QueueQuery(2, new Callback() {
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

    private static void StartTimeoutTimer() {
        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                TimeoutResponse();
            }
        };

        if (mTimeoutResponseTimer != null) {
            mTimeoutResponseTimer.cancel();
        }

        mTimeoutResponseTimer = new Timer();

        CallbackItem ci = callbacks.peek();
        if (ci != null) {
            mTimeoutResponseTimer.schedule(timertask, mTimeout);
        }
    }

    private static boolean ValidateResponse(byte[] msg, ValContainer<byte[]> dataOut) {
        byte[] data = new byte[(msg.length - 1) / 5];

        //Validate PIC checksum response byte
        if (msg[0] != START_BYTE) {
            Log.w(TAG, "Validation failed - START_BYTE");
            return false;
        }

        //Validate checksums
        for (int i = 1; i < msg.length; i += 5) {
            long checksum = 0;

            for (int k = 0; k < CHECKSUM_LENGTH_BYTES; k++) {
                checksum |= ((long)msg[i + k] & 0xFF) << (8 * (CHECKSUM_LENGTH_BYTES - 1 - k));
            }

            CRC32 crc = new CRC32();
            crc.update(msg[i + 4]);

            data[(i - 1) / 5] = msg[i + 4];

            if (crc.getValue() != checksum) {
                Log.w(TAG, "Validation failed - checksum");
                return false;
            }
        }

        if (dataOut != null) {
            dataOut.setVal(data);
        }

        return true;
    }

    private static void PostResponse() {
        Log.w("BUFFER", "readBuffer size: " + readBuffer.size());
//        Byte[] b = new Byte[readBuffer.size()];
//        readBuffer.toArray(b);
//        for (int i = 0; i < b.length; i++) {
//            Log.w("BUFFER", String.format("%02X", (int)b[i] & 0xFF));
//        }

        if (mRunning && !callbacks.isEmpty() && readBuffer.size() >= callbacks.peek().mBytesToRead) {
            mTimeoutResponseTimer.cancel();

            CallbackItem callbackItem = callbacks.peek();
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

            ValContainer<byte[]> data = new ValContainer<>();

            if (ValidateResponse(msg, data)) {
                if (callbackItem.mCallback != null) {
                    callbackItem.mCallback.response(data.getVal());
                }

                callbacks.poll();
            }
            else {
                readBuffer.clear();
            }

            mRunning = false;
            NextCommand();
        }
    }

    private static void NextCommand() {
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
        }
    }

    private static void QueueQuery(byte[] query, int bytesToWait, final Callback callback) {
        //We expect a checksum
        if (bytesToWait > 0) {
            bytesToWait += CHECKSUM_LENGTH_BYTES * bytesToWait;
        }

        //We always expect at least an OK on the PIC checksum comparison
        bytesToWait++;

        callbacks.add(new CallbackItem(query, bytesToWait, callback));

        NextCommand();
    }

    private static void QueueQuery(byte[] query) {
        QueueQuery(query, 0, null);
    }

    static void clearBuffers()
    {
        mTimeoutResponseTimer.cancel();
        callbacks.clear();
        readBuffer.clear();
    }

    static void getLEDStatus(final LEDStatusCallback callback)
    {
        byte[] msg = new byte[] {
                c_led_mode | readReg
        };

        QueueQuery(msg, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg: " + msg[0]);

                byte bitmask = msg[0];
                LEDStatus green = LEDStatus.OFF;
                LEDStatus yellow = LEDStatus.OFF;
                LEDStatus red = LEDStatus.OFF;

                if ((bitmask & 1) == 1) {
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
        byte[] msg_1_2a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_1_2
        };
        byte[] msg_1_2b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_3_4
        byte[] msg_3_4a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_3_4
        };
        byte[] msg_3_4b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_5_6
        byte[] msg_5_6a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_5_6
        };
        byte[] msg_5_6b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        //read ee_program_name_7_8
        byte[] msg_7_8a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_name_7_8
        };
        byte[] msg_7_8b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_1_2a);
        QueueQuery(msg_1_2b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_1_2 - " + msg[0] + " - " + msg[1]);
                response[0] = msg[0];
                response[1] = msg[1];
            }
        });

        QueueQuery(msg_3_4a);
        QueueQuery(msg_3_4b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_3_4 - " + msg[0] + " - " + msg[1]);
                response[2] = msg[0];
                response[3] = msg[1];
            }
        });

        QueueQuery(msg_5_6a);
        QueueQuery(msg_5_6b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "msg_5_6 - " + msg[0] + " - " + msg[1]);
                response[4] = msg[0];
                response[5] = msg[1];
            }
        });

        QueueQuery(msg_7_8a);
        QueueQuery(msg_7_8b, 2, new Callback() {
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
        byte[] msg_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_charg,
        };

        byte[] msg_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        Log.w(TAG, "LogChargers");

        QueueQuery(msg_a);
        QueueQuery(msg_b, 2, new Callback() {
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
        byte[] msg_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_error,
        };

        byte[] msg_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_a);
        QueueQuery(msg_b, 2, new Callback() {
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
        byte[] msg_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_cnt_depth
        };

        byte[] msg_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_a);
        QueueQuery(msg_b, 2, new Callback() {
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
                c_charge_volt_meas_high | readReg
        };

        byte[] msg_low = new byte[] {
                c_charge_volt_meas_low | readReg
        };

        final byte[] response = new byte[2];

        QueueQuery(msg_high, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeVoltage - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        QueueQuery(msg_low, 1, new Callback() {
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
                c_charge_curr_meas_high | readReg
        };

        byte[] msg_low = new byte[] {
                c_charge_curr_meas_low | readReg
        };

        final byte[] response = new byte[2];

        QueueQuery(msg_high, 1, new Callback() {
            @Override
            public void response(byte[] msg) {
                Log.w(TAG, "ChargeCurrent - high: " + (msg[0] & 0xFF));
                response[0] = msg[0];
            }
        });

        QueueQuery(msg_low, 1, new Callback() {
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
                c_charge_pstep_number | readReg
        };

        QueueQuery(msg, 1, new Callback() {
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
        byte[] msg_charge_a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
        };

        byte[] msg_charge_b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_charg
        };

        byte[] msg_error_a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
        };

        byte[] msg_error_b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_error
        };

        byte[] msg_depthDiscarges_a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                0x00,
                c_cmd_ee_data_low | writeReg,
                0x00,
        };

        byte[] msg_depthDiscarges_b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_log_cnt_depth
        };

        QueueQuery(msg_charge_a);
        QueueQuery(msg_charge_b);
        QueueQuery(msg_error_a);
        QueueQuery(msg_error_b);
        QueueQuery(msg_depthDiscarges_a);
        QueueQuery(msg_depthDiscarges_b);
    }

    public static void getProgramSize(final IntCallback callback) {
        byte[] msg_programSize_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_program_size,
        };

        byte[] msg_programSize_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_programSize_a);
        QueueQuery(msg_programSize_b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                callback.response(((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF));
            }
        });
    }

    //This is actually EEprom size, but to keep with conventions of the charger, it is named log size
    public static void getLogSize(final IntCallback callback) {
        byte[] msg_logSize_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg,
                ee_log_ee_size,
        };

        byte[] msg_logSize_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_logSize_a);
        QueueQuery(msg_logSize_b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                callback.response(((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF));
            }
        });
    }

    private static void retrieveLogHeaderRecursively(final int logStart, final int eePromSize, final LogHeaderCallback logHeaderCallback, final ActionCallback onFinished) {
        byte[] msg_a = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                (byte)((logStart >> 8) & 0xFF),
                c_cmd_ee_addr_low | writeReg,
                (byte)(logStart & 0xFF),
        };

        byte[] msg_b = new byte[] {
                c_cmd_ee_data_high | readReg,
                c_cmd_ee_data_low | readReg
        };

        QueueQuery(msg_a);
        QueueQuery(msg_b, 2, new Callback() {
            @Override
            public void response(byte[] msg) {
                LogHeader logHeader = new LogHeader();
                logHeader.size = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);
                logHeader.address = logStart;
                logHeaderCallback.response(logHeader);

                if (logHeader.address + logHeader.size + 1 < eePromSize) {
                    retrieveLogHeaderRecursively(logHeader.address + logHeader.size + 1, eePromSize, logHeaderCallback, onFinished);
                }
                else {
                    onFinished.response();
                }
            }
        });
    }

    public static void getLogHeaders(final ListLogHeaderCallback callback) {
        final ValContainer<Integer> programSize = new ValContainer<>();

        final List<LogHeader> headers = new ArrayList<>();

        enterProgMode();

        getProgramSize(new IntCallback() {
            @Override
            public void response(int value) {
                programSize.setVal(value);
            }
        });

        getLogSize(new IntCallback() {
            @Override
            public void response(final int eePromSize) {
                final int logFileStart = ee_program_area + programSize.getVal();

                byte[] msg_initialOffset_a = new byte[] {
                        c_cmd_ee_addr_high | writeReg,
                        (byte)(((logFileStart + 1) >> 8) & 0xFF),
                        c_cmd_ee_addr_low | writeReg,
                        (byte)((logFileStart + 1) & 0xFF),
                };

                byte[] msg_initialOffset_b = new byte[] {
                        c_cmd_ee_data_high | readReg,
                        c_cmd_ee_data_low | readReg
                };

                QueueQuery(msg_initialOffset_a);
                QueueQuery(msg_initialOffset_b, 2, new Callback() {
                    @Override
                    public void response(byte[] msg) {
                        int offset = ((msg[0] & 0xFF) << 8) | (msg[1] & 0xFF);

                        retrieveLogHeaderRecursively(logFileStart + offset, eePromSize, new LogHeaderCallback() {
                            @Override
                            public void response(LogHeader logHeader) {
                                headers.add(logHeader);
                            }
                        }, new ActionCallback() {
                            @Override
                            public void response() {
                                enterNormalMode();
                                callback.response(headers);
                            }
                        });
                    }
                });
            }
        });
    }

    public static void getLog(final LogHeader logHeader, final ListByteCallback callback, final IntCallback onByteReceivedCallback) {
        enterProgMode();

        final List<Byte> log = new ArrayList<>();

        final int logEnd = logHeader.address + logHeader.size;

        for (int i = logHeader.address; i < logEnd; i++) {
            final int current = i;

            byte[] msg_logByte_a = new byte[] {
                    c_cmd_ee_addr_high | writeReg,
                    (byte)(((i + 1) >> 8) & 0xFF),
                    c_cmd_ee_addr_low | writeReg,
                    (byte)((i + 1) & 0xFF),
            };

            byte[] msg_logByte_b = new byte[] {
                    c_cmd_ee_data_high | readReg,
                    c_cmd_ee_data_low | readReg
            };

            QueueQuery(msg_logByte_a);
            QueueQuery(msg_logByte_b, 2, new Callback() {
                @Override
                public void response(byte[] msg) {
                    log.add(msg[0]);
                    log.add(msg[1]);

                    if (onByteReceivedCallback != null) {
                        onByteReceivedCallback.response(msg[0]);
                        onByteReceivedCallback.response(msg[1]);
                    }

                    if (current == logEnd - 1) {
                        Log.w(TAG, "LOG GOTTEN");
                        callback.response(log);
                        enterNormalMode();
                    }
                }
            });
        }




//        final ValContainer<Integer> programSize = new ValContainer<>();
//
//        enterProgMode();
//
//        getProgramSize(new IntCallback() {
//            @Override
//            public void response(int value) {
//                programSize.setVal(value);
//            }
//        });
//
//        getLogSize(new IntCallback() {
//            @Override
//            public void response(final int logSize) {
//                final List<Byte> log = new ArrayList<>();
//
//                Log.w("logsize", "logsize: " + logSize);
//
//                for (int i = ee_program_area + programSize.getVal(); i < logSize; i++) {
//                    final int current = i;
//
//                    byte addrHigh = (byte)((i & ((byte)0xFF << 8)) >> 8);
//                    byte addrLow = (byte)(i & (byte)0xFF);
//
//                    byte[] msgLogByte_a = new byte[] {
//                            c_cmd_ee_addr_high | writeReg,
//                            addrHigh,
//                            c_cmd_ee_addr_low | writeReg,
//                            addrLow,
//                    };
//
//                    byte[] msgLogByte_b = new byte[] {
//                            c_cmd_ee_data_high | readReg,
//                            c_cmd_ee_data_low | readReg
//                    };
//
//                    QueueQuery(msgLogByte_a);
//                    QueueQuery(msgLogByte_b, 2, new Callback() {
//                        @Override
//                        public void response(byte[] msg) {
//                            log.add(msg[0]);
//                            log.add(msg[1]);
//
//                            Log.w("Hejsa", "current: " + current + " programSize: " + programSize.getVal() + " logSize: " + logSize);
//
//                            if (onByteReceivedCallback != null) {
//                                onByteReceivedCallback.response(msg[0]);
//                                onByteReceivedCallback.response(msg[1]);
//                            }
//
//                            if (current == logSize - 1) {
//                                Log.w(TAG, "LOG GOTTEN");
//                                callback.response(log);
//                                enterNormalMode();
//                            }
//                        }
//                    });
//                }
//            }
//        });
    }

    public static void getProgram(final ListByteCallback callback) {
        readBuffer.clear();
        getProgramSize(new IntCallback() {
            @Override
            public void response(int value) {
                final int programSize = value;
                final List<Byte> program = new ArrayList<>();

                for (int i = 0; i < programSize; i++) {
                    final int current = i;

                    int fullAddr = ee_program_area + i;
                    byte addrHigh = (byte)((fullAddr & ((byte)0xFF << 8)) >> 8);
                    byte addrLow = (byte)(fullAddr & (byte)0xFF);

                    byte[] msgProgramBytes_a = new byte[] {
                            c_cmd_ee_addr_high | writeReg,
                            addrHigh,
                            c_cmd_ee_addr_low | writeReg,
                            addrLow,
                    };

                    byte[] msgProgramBytes_b = new byte[] {
                            c_cmd_ee_data_high | readReg,
                            c_cmd_ee_data_low | readReg
                    };

                    QueueQuery(msgProgramBytes_a);
                    QueueQuery(msgProgramBytes_b, 2, new Callback() {
                        @Override
                        public void response(byte[] msg) {
                            program.add(msg[0]);
                            program.add(msg[1]);

                            if (current == programSize - 1)
                            {
                                callback.response(program);
                            }
                        }
                    });
                }
            }
        });
    }

    public static void writeProgramName(byte[] name) {
        byte[] msg_1_2a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                name[0],
                c_cmd_ee_data_low | writeReg,
                name[1],
        };

        byte[] msg_1_2b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_program_name_1_2
        };

        byte[] msg_3_4a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                name[2],
                c_cmd_ee_data_low | writeReg,
                name[3],
        };

        byte[] msg_3_4b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_program_name_3_4
        };

        byte[] msg_5_6a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                name[4],
                c_cmd_ee_data_low | writeReg,
                name[5],
        };

        byte[] msg_5_6b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_program_name_5_6
        };

        byte[] msg_7_8a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                name[6],
                c_cmd_ee_data_low | writeReg,
                name[7],
        };

        byte[] msg_7_8b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_program_name_7_8
        };

        QueueQuery(msg_1_2a);
        QueueQuery(msg_1_2b);
        QueueQuery(msg_3_4a);
        QueueQuery(msg_3_4b);
        QueueQuery(msg_5_6a);
        QueueQuery(msg_5_6b);
        QueueQuery(msg_7_8a);
        QueueQuery(msg_7_8b);
    }

    public static void writeProgramSizeInWords(byte[] size) {
        byte[] msg_a = new byte[] {
                c_cmd_ee_data_high | writeReg,
                size[0],
                c_cmd_ee_data_low | writeReg,
                size[1],
        };

        byte[] msg_b = new byte[] {
                c_cmd_ee_addr_high | writeReg,
                0x00,
                c_cmd_ee_addr_low | writeReg | writeEEprom,
                ee_program_size
        };

        QueueQuery(msg_a);
        QueueQuery(msg_b);
    }

    public static void writeProgram(byte[] program, IntCallback progressCallback) {
        for (int i = 0; i <  program.length; i += 2) {
            int fullAddr = ee_program_area + i / 2;
            byte addrHigh = (byte)((fullAddr & 0x0000FF00) >> 8);
            byte addrLow = (byte)(fullAddr & 0x000000FF);

            byte[] msg_a = new byte[] {
                    c_cmd_ee_data_high | writeReg,
                    program[i],
                    c_cmd_ee_data_low | writeReg,
                    program[i + 1],
            };

            byte[] msg_b = new byte[] {
                    c_cmd_ee_addr_high | writeReg,
                    addrHigh,
                    c_cmd_ee_addr_low | writeReg | writeEEprom,
                    addrLow
            };

            QueueQuery(msg_a);
            QueueQuery(msg_b);

            progressCallback.response(program[i]);
            progressCallback.response(program[i + 1]);
        }
    }

    public static void dataTest() {
        while (true) {
            byte[] msg_1 = new byte[] {
                    0x41,
                    0x42,
                    0x43,
                    0x44
            };

            byte[] msg_2 = new byte[] {
                    0x45,
                    0x46,
                    0x47,
                    0x48
            };

            QueueQuery(msg_1);
            QueueQuery(msg_2);
        }
    }

    public static void enterProgMode() {
        byte[] msg = new byte[] {
                m_gbc_operation_mode | writeReg,
                0x20
        };

        QueueQuery(msg, 0, null);
    }

    public static void enterNormalMode() {
        byte[] msg = new byte[] {
                m_gbc_operation_mode | writeReg,
                -0x80
        };

        QueueQuery(msg, 0, null);
    }
}
