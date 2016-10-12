package com.fuglsang_electronics.neoandroidapp;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.nononsenseapps.filepicker.FilePickerActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ProgressActivity extends AppCompatActivity {
    private static final String TAG = "NEO_ProgressActivity";

    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;

    DropboxAPI<AndroidAuthSession> mDBApi = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        Intent intent = getIntent();
        int mode = intent.getIntExtra("Mode", 0);

        Intent i = new Intent(getBaseContext(), FilePickerActivity.class);

        // Set these depending on your use case.
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_NEW_FILE);

        // Configure initial directory by specifying a String.
        // You could specify a String like "/storage/emulated/0/", but that can
        // dangerous. Always use Android's API calls to get paths to the SD-card or
        // internal memory.
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

        startActivityForResult(i, mode);
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (data != null) {
            String path = data.getData().getPath();
            Log.w(TAG, "RESULT!! " + path);

            try {
                FileWriter fw = new FileWriter(path + ".txt");
                fw.write("Teeeest" + path);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                Log.w(TAG, "Unable to open file");
            }
        }
        else {
            Intent intent = new Intent(getBaseContext(), ServiceMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);

            finish();
        }
    }

    @Override
    public void onBackPressed() {
        //Intercept and do nothing
    }
}
