package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        final Button btnBluetooth = (Button)findViewById(R.id.btnBluetooth);
        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] msg = new byte[]{
                        '|', 't', 'h', 'i', 's',' ','i','s',' ','a',' ','t','e','s','t','|'
                };
                ChargerModel.writeCharacteristic(msg);
            }
        });


        if (!BluetoothController.mConnected) {
            Intent intent = new Intent(getBaseContext(), BluetoothActivity.class);
            startActivity(intent);
        }
    }



}
