package com.fuglsang_electronics.neoandroidapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;


public class BluetoothActivity extends AppCompatActivity {
    private static final String TAG = "NEO_BluetoothActivity";

    private BluetoothListViewAdapter mBluetoothListViewAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_bluetooth);

        BluetoothController.setupBluetooth(this);
        final Context context = this.getBaseContext();
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(getString(R.string.bt_connectingSpinnerHeadline));
        progress.setMessage(getString(R.string.bt_connectingSpinnerMessage));
        progress.setCancelable(false);
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        ListView listView = (ListView)findViewById(R.id.bluetooth_ListView);
        mBluetoothListViewAdapter = new BluetoothListViewAdapter(this, R.layout.bluetooth_listview_item, BluetoothController.mDevices);
        listView.setAdapter(mBluetoothListViewAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, View view, int position, long id) {
                progress.show();

                BluetoothController.connectBluetooth(BluetoothController.mDevices.get(position), context, new BluetoothController.ConnectionCallback() {
                    @Override
                    public void onConnectionEstablished() {
                        progress.dismiss();

                        Intent intent = new Intent(getBaseContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        overridePendingTransition(0, 0);
                    }
                });
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


