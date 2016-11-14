package com.fuglsang_electronics.neoandroidapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class ProgressListViewAdapter extends ArrayAdapter<LogHeader> {
    public ProgressListViewAdapter(Context context, int resource, List<LogHeader> logHeaders) {
        super(context, resource, logHeaders);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.progress_listview_item, null);
        }

        LogHeader logHeader = getItem(position);

        if (logHeader != null) {
            Context context = getContext();

            TextView txt = (TextView)convertView.findViewById(R.id.progressListViewItem_TextView);
            txt.setText(context.getString(R.string.progressDialog_Log) + " " + Integer.toString(position + 1) + " - " + context.getString(R.string.progressDialog_LogSize) + " " + Integer.toString(logHeader.size));
        }

        return convertView;
    }
}
