package com.fuglsang_electronics.neoandroidapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nononsenseapps.filepicker.FilePickerActivity;

import java.util.Timer;
import java.util.TimerTask;

public class ServiceMenuActivity extends AppCompatActivity {
    private static final String TAG = "NEO_ServiceMenuActivity";
    private static final int READ_LOG = 1;
    private static final int WRITE_PROGRAM = 2;

    private TextView txtViewVoltage;
    private TextView txtViewCurrent;
    private TextView txtViewProgramStep;

    private  TextView txtViewError;

    private TextView txtViewLogCounterCharges;
    private TextView txtViewLogCounterErrors;
    private TextView txtViewLogCounterDepthDischarges;

    private Button btnServiceResetCounters;
    private Button btnServiceReadLog;
    private Button btnServiceWriteProgram;

    private final long mUpdateVoltCurrStepDelay = 10000;
    private Timer mTimer = new Timer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_menu);

        txtViewVoltage = (TextView)findViewById(R.id.txtViewServiceVoltage);
        txtViewCurrent = (TextView)findViewById(R.id.txtViewServiceCurrent);
        txtViewProgramStep = (TextView)findViewById(R.id.txtViewServiceProgramStep);

        txtViewError = (TextView)findViewById(R.id.txtViewServiceError);

        txtViewLogCounterCharges = (TextView)findViewById(R.id.txtViewServiceLogCountersCharges);
        txtViewLogCounterErrors = (TextView)findViewById(R.id.txtViewServiceLogCountersErrors);
        txtViewLogCounterDepthDischarges = (TextView)findViewById(R.id.txtViewServiceLogCountersDepthDischarges);

        btnServiceResetCounters = (Button)findViewById(R.id.btnServiceResetCounters);
        btnServiceReadLog = (Button)findViewById(R.id.btnServiceReadLog);
        btnServiceWriteProgram = (Button)findViewById(R.id.btnServiceWriteProgram);

        btnServiceResetCounters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(TAG, "btnServiceResetCounters click");

                ChargerModel.ClearLogCounters();
                getLogCounters();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogLayout = View.inflate(this, R.layout.choose_file_selector_dialog, null);
        builder.setCancelable(true);
        builder.setView(dialogLayout);
        final AlertDialog diag = builder.create();

        btnServiceReadLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            Log.w(TAG, "btnServiceReadLog click");

            diag.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    final Intent intent = new Intent(getBaseContext(), ProgressActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    intent.putExtra("Mode", ProgressActivity.MODE_WRITE);

                    Button dropboxBtn = (Button)dialogLayout.findViewById(R.id.chooseFileSelectorDialog_DropboxBtn);
                    Button phoneBtn = (Button)dialogLayout.findViewById(R.id.chooseFileSelectorDialog_PhoneBtn);

                    dropboxBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            intent.putExtra("Storage", ProgressActivity.STORAGE_DROPBOX);
                            startActivity(intent);
                            overridePendingTransition(0, 0);

                            mTimer.cancel();
                            diag.dismiss();
                            finish();
                        }
                    });

                    phoneBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            intent.putExtra("Storage", ProgressActivity.STORAGE_PHONE);
                            startActivity(intent);
                            overridePendingTransition(0, 0);

                            mTimer.cancel();
                            diag.dismiss();
                            finish();
                        }
                    });
                }
            });

            diag.show();




//                Intent i = new Intent(getBaseContext(), FilePickerActivity.class);
//
//                // Set these depending on your use case. These are the defaults.
//                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
//                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_NEW_FILE);
//
//                // Configure initial directory by specifying a String.
//                // You could specify a String like "/storage/emulated/0/", but that can
//                // dangerous. Always use Android's API calls to get paths to the SD-card or
//                // internal memory.
//                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
//
//                startActivityForResult(i, READ_LOG);
            }
        });

        btnServiceWriteProgram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(TAG, "btnServiceWriteProgram click");

//                Intent i = new Intent(getBaseContext(), FilePickerActivity.class);
//
//                // Set these depending on your use case. These are the defaults.
//                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
//                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
//
//                // Configure initial directory by specifying a String.
//                // You could specify a String like "/storage/emulated/0/", but that can
//                // dangerous. Always use Android's API calls to get paths to the SD-card or
//                // internal memory.
//                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
//
//                startActivityForResult(i, WRITE_PROGRAM);
            }
        });
    }

    private void getLogCounters() {
        Log.w(TAG, "getLogCounters");
        ChargerModel.getLogCounterCharges(new ChargerModel.LogCounterCallback() {
            @Override
            public void Response(int count) {
                txtViewLogCounterCharges.setText(Integer.toString(count));
                Log.w(TAG, "Whuut: " + Integer.toString(count));
            }
        });

        ChargerModel.getLogCountersErrors(new ChargerModel.LogCounterCallback() {
            @Override
            public void Response(int count) {
                txtViewLogCounterErrors.setText(Integer.toString(count));
            }
        });

        ChargerModel.getLogCountersDepthDischarges(new ChargerModel.LogCounterCallback() {
            @Override
            public void Response(int count) {
                txtViewLogCounterDepthDischarges.setText(Integer.toString(count));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        ChargerModel.clearBuffer();

        getLogCounters();

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ChargerModel.getChargeVoltage(new ChargerModel.ChargeCallback() {
                    @Override
                    public void Response(int value) {
                        double fValue = value / 1000f;
                        txtViewVoltage.setText(String.format("%1$.3f" + getString(R.string.unitsVoltage), fValue));
                    }
                });

                ChargerModel.getChargeCurrent(new ChargerModel.ChargeCallback() {
                    @Override
                    public void Response(int value) {
                        double fValue = value / 1000f;
                        txtViewCurrent.setText(String.format("%1$.3f" + getString(R.string.unitsCurrent), fValue));
                    }
                });

                ChargerModel.getChargeProgramStep(new ChargerModel.ChargeCallback() {
                    @Override
                    public void Response(int value) {
                        txtViewProgramStep.setText(Integer.toString(value));
                    }
                });

                ChargerModel.getLEDStatus(new ChargerModel.LEDStatusCallback() {
                    @Override
                    public void Response(ChargerModel.LEDStatus green, ChargerModel.LEDStatus yellow, ChargerModel.LEDStatus red) {
                        if (red == ChargerModel.LEDStatus.ON) {
                            txtViewError.setText(getString(R.string.service_error_misc));
                        }
                        else if (red == ChargerModel.LEDStatus.SLOW_BLINK) {
                            txtViewError.setText(getString(R.string.service_error_reversePolarity));
                        }
                        else {
                            txtViewError.setText(getString(R.string.service_error_noError));
                        }
                    }
                });
            }
        }, 0, mUpdateVoltCurrStepDelay);
    }

    @Override
    public void onBackPressed() {
        mTimer.cancel();
        ChargerModel.clearBuffer();

        Intent main = new Intent(ServiceMenuActivity.this, MainActivity.class);
        startActivity(main);
        finish();
    }
}
