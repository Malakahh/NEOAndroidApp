package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class BluetoothActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD_MS = 10000;

    BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    BluetoothListViewAdapter mBluetoothListViewAdapter;
    List<BluetoothDevice> mDevices = new ArrayList<>();

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    if (!mDevices.contains(device))
                    {
                        mDevices.add(device);
                        mBluetoothListViewAdapter.notifyDataSetInvalidated();
                    }
                }
            };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        setupBluetooth();

        ListView listView = (ListView)findViewById(R.id.bluetooth_ListView);
        mBluetoothListViewAdapter = new BluetoothListViewAdapter(this, R.layout.bluetooth_listview_item, mDevices);
        listView.setAdapter(mBluetoothListViewAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //TODO: React properly
                Toast.makeText(BluetoothActivity.this, "Click, yay: " + mDevices.get(position).getName(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        scanBLEDevices(true);
    }

    private void setupBluetooth() {
        mHandler = new Handler();

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


