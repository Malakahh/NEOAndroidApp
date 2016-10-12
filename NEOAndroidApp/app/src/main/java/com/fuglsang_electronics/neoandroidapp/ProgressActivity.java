package com.fuglsang_electronics.neoandroidapp;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.fuglsang_electronics.neoandroidapp.DropboxFilePicker.DropboxFilePickerActivity;
import com.fuglsang_electronics.neoandroidapp.DropboxFilePicker.DropboxSyncHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ProgressActivity extends AppCompatActivity {
    private static final String TAG = "NEO_ProgressActivity";
    private static final int CODE_DB = 1;

    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;
    public static final int STORAGE_DROPBOX = 1;
    public static final int STORAGE_PHONE = 2;
    //NOTE: STORAGE_SERVICE exists, but it is not related to this file picker

    DropboxAPI<AndroidAuthSession> mDBApi = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        Intent intent = getIntent();
        int storage = intent.getIntExtra("Storage", 0);
        int mode = intent.getIntExtra("Mode", 0);

        if (storage == STORAGE_PHONE) {
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
        else if (storage == STORAGE_DROPBOX) {
            // First we must authorize the user
            if (mDBApi == null) {
                mDBApi = DropboxSyncHelper
                        .getDBApi(ProgressActivity.this);
            }

            // If not authorized, then ask user for login/permission
            if (!mDBApi.getSession().isLinked()) {
                mDBApi.getSession().startOAuth2Authentication(
                        ProgressActivity.this);
            } else {  // User is authorized, open file picker
                startActivity(CODE_DB, DropboxFilePickerActivity.class);
            }
        }
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

    protected void startActivity(final int code, final Class<?> klass) {
        final Intent i = new Intent(this, klass);

        i.setAction(Intent.ACTION_GET_CONTENT);

//        i.putExtra(SUPickerActivity.EXTRA_ALLOW_MULTIPLE,
//                checkAllowMultiple.isChecked());
//        i.putExtra(FilePickerActivity.EXTRA_SINGLE_CLICK, true);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_EXISTING_FILE, true);

        // What mode is selected
//        final int mode;
//        switch (radioGroup.getCheckedRadioButtonId()) {
//            case R.id.radioDir:
//                mode = AbstractFilePickerFragment.MODE_DIR;
//                break;
//            case R.id.radioFilesAndDirs:
//                mode = AbstractFilePickerFragment.MODE_FILE_AND_DIR;
//                break;
//            case R.id.radioNewFile:
//                mode = AbstractFilePickerFragment.MODE_NEW_FILE;
//                break;
//            case R.id.radioFile:
//            default:
//                mode = AbstractFilePickerFragment.MODE_FILE;
//                break;
//        }

        final int mode = AbstractFilePickerFragment.MODE_NEW_FILE;

        i.putExtra(FilePickerActivity.EXTRA_MODE, mode);

        startActivityForResult(i, code);
    }

    /**
     * This is entirely for Dropbox's benefit
     */
    protected void onResume() {
        super.onResume();

        if (mDBApi != null && mDBApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                mDBApi.getSession().finishAuthentication();

                String accessToken = mDBApi.getSession().getOAuth2AccessToken();
                DropboxSyncHelper.saveToken(this, accessToken);
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }
    }

    @Override
    public void onBackPressed() {
        //Intercept and do nothing
    }
}
