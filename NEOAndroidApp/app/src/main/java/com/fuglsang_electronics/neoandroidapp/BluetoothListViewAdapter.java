package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class BluetoothListViewAdapter extends ArrayAdapter<BluetoothDevice> {

    public BluetoothListViewAdapter(Context context, int resource) {
        super(context, resource);
    }

    public BluetoothListViewAdapter(Context context, int resource, List<BluetoothDevice> devices)
    {
        super(context, resource, devices);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        if (convertView == null)
        {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.bluetooth_listview_item, null);
        }

        BluetoothDevice d = getItem(position);

        if (d != null)
        {
            TextView txt = (TextView)convertView.findViewById(R.id.bluetoothListViewItem_TextView);
            txt.setText(d.getName());
        }

        return convertView;
    }
}
