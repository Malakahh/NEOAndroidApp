package com.fuglsang_electronics.neoandroidapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

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

    private TextView mTxtViewProgramName;

    private Button btnServiceResetCounters;
    private Button btnServiceReadLog;
    private Button btnServiceProgram;

    private final long mUpdateVoltCurrStepDelay = 10000;
    private Timer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_menu);

        final Context context = this;
        final Activity activity = this;

        txtViewVoltage = (TextView)findViewById(R.id.txtViewServiceVoltage);
        txtViewCurrent = (TextView)findViewById(R.id.txtViewServiceCurrent);
        txtViewProgramStep = (TextView)findViewById(R.id.txtViewServiceProgramStep);

        txtViewError = (TextView)findViewById(R.id.txtViewServiceError);

        btnServiceResetCounters = (Button)findViewById(R.id.btnServiceLogCounters);
        btnServiceReadLog = (Button)findViewById(R.id.btnServiceReadLog);
        btnServiceProgram = (Button)findViewById(R.id.btnServiceProgram);

        btnServiceResetCounters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopDataPolling();
                ChargerModel.enterProgMode();

                Dialog dialog = new Dialog(context);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.logcounters_dialog);
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                txtViewLogCounterCharges = (TextView) dialog.findViewById(R.id.txtViewLogCountersCharges);
                txtViewLogCounterErrors = (TextView) dialog.findViewById(R.id.txtViewLogCountersErrors);
                txtViewLogCounterDepthDischarges = (TextView) dialog.findViewById(R.id.txtViewLogCountersDepthDischarges);

                getLogCounters();

                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        ChargerModel.enterNormalMode();
                        startDataPolling();
                    }
                });

                Button btnReset = (Button) dialog.findViewById(R.id.btnLogCountersReset);
                btnReset.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ChargerModel.clearLogCounters();
                        getLogCounters();
                    }
                });

                dialog.show();
            }
        });

        btnServiceReadLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), ProgressActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                intent.putExtra("Mode", ProgressActivity.MODE_TO_FILE);

                startActivity(intent);
                overridePendingTransition(0, 0);

                mTimer.cancel();
                finish();
            }
        });

        btnServiceProgram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopDataPolling();
                ChargerModel.enterProgMode();

                Dialog dialog = new Dialog(context);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.program_dialog);
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                mTxtViewProgramName = (TextView) dialog.findViewById(R.id.txtViewProgramName);

                getProgramName();

                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        ChargerModel.enterNormalMode();
                        startDataPolling();
                    }
                });

                Button btnWriteProgram = (Button) dialog.findViewById(R.id.btnProgramWrite);
                btnWriteProgram.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(getBaseContext(), ProgressActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                        intent.putExtra("Mode", ProgressActivity.MODE_FROM_FILE);

                        startActivity(intent);
                        overridePendingTransition(0, 0);

                        mTimer.cancel();
                        activity.finish();
                    }
                });

                dialog.show();
            }
        });
    }

    private void getProgramName() {
        ChargerModel.getProgrammeName(new ChargerModel.StringCallback() {
            @Override
            public void response(String programmeName) {
                mTxtViewProgramName.setText(programmeName);
            }
        });
    }

    private void getLogCounters() {
        Log.w(TAG, "getLogCounters");
        ChargerModel.getLogCounterCharges(new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                txtViewLogCounterCharges.setText(Integer.toString(value));
            }
        });

        ChargerModel.getLogCountersErrors(new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                txtViewLogCounterErrors.setText(Integer.toString(value));
            }
        });

        ChargerModel.getLogCountersDepthDischarges(new ChargerModel.IntCallback() {
            @Override
            public void response(int value) {
                txtViewLogCounterDepthDischarges.setText(Integer.toString(value));
            }
        });
    }

    private void startDataPolling() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ChargerModel.getChargeVoltage(new ChargerModel.IntCallback() {
                    @Override
                    public void response(int value) {
                        double fValue = value / 1000f;
                        txtViewVoltage.setText(String.format("%1$.1f" + getString(R.string.unitsVoltage), fValue));
                    }
                });

                ChargerModel.getChargeCurrent(new ChargerModel.IntCallback() {
                    @Override
                    public void response(int value) {
                        double fValue = value / 1000f;
                        txtViewCurrent.setText(String.format("%1$.1f" + getString(R.string.unitsCurrent), fValue));
                    }
                });

                ChargerModel.getChargeProgramStep(new ChargerModel.IntCallback() {
                    @Override
                    public void response(int value) {
                        txtViewProgramStep.setText(Integer.toString(value));
                    }
                });

                ChargerModel.getLEDStatus(new ChargerModel.LEDStatusCallback() {
                    @Override
                    public void response(ChargerModel.LEDStatus green, ChargerModel.LEDStatus yellow, ChargerModel.LEDStatus red) {
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

    private void stopDataPolling() {
        mTimer.cancel();
        ChargerModel.clearBuffers();
    }

    @Override
    protected void onStart() {
        super.onStart();

        ChargerModel.clearBuffers();

        startDataPolling();
    }

    @Override
    public void onBackPressed() {
        mTimer.cancel();
        ChargerModel.clearBuffers();

        Intent main = new Intent(ServiceMenuActivity.this, MainActivity.class);
        startActivity(main);
        finish();
    }
}
