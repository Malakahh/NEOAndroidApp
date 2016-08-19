package com.fuglsang_electronics.neoandroidapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


public class BluetoothActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD_MS = 10000;

    BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    TextView mTextView;

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    Toast.makeText(getBaseContext(), "Found device: " + device.getName(),  Toast.LENGTH_SHORT).show();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        mTextView = (TextView)findViewById(R.id.textView2);
        mHandler = new Handler();

        setupBluetooth();
    }

    @Override
    protected void onResume()
    {
        super.onResume();



        scanBLEDevices(true);
    }

    private void setupBluetooth() {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mBluetoothAdapter.enable();
    }

    private void scanBLEDevices(final boolean enable) {
        BluetoothLeScanner eh = mBluetoothAdapter.getBluetoothLeScanner();
/*
        if (enable) {
            //Toast.makeText(getBaseContext(), "Scan Started", Toast.LENGTH_SHORT).show();
            eh.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    Toast.makeText(getBaseContext(), "ScanResult: " + result.getDevice().getName(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                    Toast.makeText(getBaseContext(), "BatchScanResults: " + results.get(0).getDevice().getName(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Toast.makeText(getBaseContext(), "ScanFailed, errorCode: " + errorCode, Toast.LENGTH_SHORT).show();
                }
            });
        }
*/

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD_MS);

            mScanning = true;

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

    }

}


