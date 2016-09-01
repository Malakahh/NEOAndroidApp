package com.fuglsang_electronics.neoandroidapp;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;


public class BluetoothActivity extends AppCompatActivity {
    private BluetoothListViewAdapter mBluetoothListViewAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        BluetoothController.setupBluetooth(this);
        final Context context = this.getBaseContext();

        ListView listView = (ListView)findViewById(R.id.bluetooth_ListView);
        mBluetoothListViewAdapter = new BluetoothListViewAdapter(this, R.layout.bluetooth_listview_item, BluetoothController.mDevices);
        listView.setAdapter(mBluetoothListViewAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, View view, int position, long id) {
                BluetoothController.connectBluetooth(BluetoothController.mDevices.get(position), context);
                finish();
            }
        });

        BluetoothController.addOnLeScanListener(new BluetoothController.onLeScan() {
            @Override
            public void onDeviceDiscovered(BluetoothDevice device) {
                mBluetoothListViewAdapter.notifyDataSetInvalidated();
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        BluetoothController.scanBLEDevices(true);
    }

}


