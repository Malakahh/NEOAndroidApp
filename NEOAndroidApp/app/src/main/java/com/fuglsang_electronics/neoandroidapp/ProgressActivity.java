package com.fuglsang_electronics.neoandroidapp;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.fuglsang_electronics.neoandroidapp.ProgramParser.ProgramParser;
import com.nononsenseapps.filepicker.FilePickerActivity;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ProgressActivity extends AppCompatActivity {
    private static final String TAG = "NEO_ProgressActivity";

    public static final int MODE_FROM_FILE = 1;
    public static final int MODE_TO_FILE = 2;

    final ValContainer<Integer> maxByte = new ValContainer<>();
    int byteCount;

    ProgressBar progressBar;
    TextView statusText;

    DropboxAPI<AndroidAuthSession> mDBApi = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        progressBar = (ProgressBar) findViewById(R.id.progressActivity_progressbar);
        statusText = (TextView) findViewById(R.id.progressActivity_status);

        Intent intent = getIntent();
        int mode = intent.getIntExtra("Mode", 0);

        Intent i = new Intent(getBaseContext(), FilePickerActivity.class);

        if (mode == MODE_TO_FILE) {
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_NEW_FILE);
        }
        else if (mode == MODE_FROM_FILE) {
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
        }
        else {
            Log.w(TAG, "No valid mode selected");
            return;
        }

        // Configure initial directory by specifying a String.
        // You could specify a String like "/storage/emulated/0/", but that can
        // dangerous. Always use Android's API calls to get paths to the SD-card or
        // internal memory.
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

        startActivityForResult(i, mode);
    }

    private void getLog(final String path) {
        ChargerModel.getLogSize(new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                maxByte.setVal(value * 2);
                progressBar.setMax(maxByte.getVal());
            }
        });

        ChargerModel.getLog(new ChargerModel.ListCallback() {
            @Override
            public void response(List<Byte> value) {
                Log.w(TAG, "Writing to file..");
                try {
                    FileWriter fw = new FileWriter(path + ".txt");

                    for (int i = 0; i < value.size(); i++) {
                        fw.write(value.get(i));
                    }

                    fw.flush();
                    fw.close();

                } catch (IOException e) {
                    Log.w(TAG, "Unable to open file");
                }

            }
        }, new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                updateProgress(value);
            }
        });
    }

//    private void getProgram(final String path) {
//        Log.w(TAG, "Writing to file...");
//        ChargerModel.getProgram(new ChargerModel.ListCallback() {
//            @Override
//            public void response(List<Byte> value) {
//                try {
//                    FileWriter fw = new FileWriter(path + ".txt");
//
//                    for (int i = 0; i < value.size(); i++) {
//                        fw.write(value.get(i));
//                    }
//
//                    fw.flush();
//                    fw.close();
//                } catch (IOException e) {
//                    Log.w(TAG, "Unable to open file");
//                }
//            }
//        });
//    }

    private void writeProgram(final String path) {
        ProgramParser parser = new ProgramParser(path);

        byte[] program = parser.getConverterdProgram();
        maxByte.setVal(program.length);
        progressBar.setMax(maxByte.getVal());

        ChargerModel.writeProgramName(parser.ProgramName);
        ChargerModel.writeProgramSizeInWords(parser.WordCount);
        ChargerModel.writeProgram(program, new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                updateProgress(value);
            }
        });
    }

    private void updateProgress(final int current) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(byteCount);
                statusText.setText(Integer.toString(byteCount) + "/" + Integer.toString(maxByte.getVal()) + getString(R.string.unitsByte));
            }
        });
        byteCount++;
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        Log.w(TAG, "request: " + requestCode + " result: " + resultCode);

        if (data != null) {
            byteCount = 1;

            final String path = data.getData().getPath();
            Log.w(TAG, "RESULT!! " + path);

            if (requestCode == MODE_TO_FILE) {
                //getLog(path);
                ChargerModel.dataTest();
            }
            else if (requestCode == MODE_FROM_FILE) {
                writeProgram(path);
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
