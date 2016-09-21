package com.fuglsang_electronics.neoandroidapp;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NEO_MainActivity";

    private ImageView mImgViewPowercharge;

    private ImageView mImgViewBattery;
    private ImageView mImgViewLEDGreen;
    private ImageView mImgViewLEDYellow;
    private ImageView mImgViewLEDRed;
    private TextView mTxtViewProgrammeName;

    private final long mUpdateLEDDelayMS = 10000;
    private Timer mTimer = new Timer();

    private static final String serviceMenuPassword = "okay";

    private final long mLongTouchTime = 7000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogLayout = View.inflate(this, R.layout.password_dialog, null);
        builder.setCancelable(true)
                .setPositiveButton(getString(R.string.btnOK), null)
                .setNegativeButton(getString(R.string.btnCancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.setView(dialogLayout);
        final AlertDialog pwDiag = builder.create();
        pwDiag.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button b = pwDiag.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText et = (EditText)dialogLayout.findViewById(R.id.passwordDialogEditTextPassword);
                        if (et.getText().toString().compareTo(serviceMenuPassword) == 0) {
                            Intent intent = new Intent(getBaseContext(), ServiceMenuActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            overridePendingTransition(0, 0);

                            mTimer.cancel();
                            pwDiag.dismiss();
                            finish();
                        }
                        else {
                            et.setHint(getString(R.string.passwordDialog_HintWrongPassword));
                            et.setText("");
                        }
                    }
                });
            }
        });

        mImgViewPowercharge = (ImageView) findViewById(R.id.logoPowercharge);
        final Handler longclickPowerchargeHandler = new Handler();
        mImgViewPowercharge.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        longclickPowerchargeHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                pwDiag.show();
                            }
                        }, mLongTouchTime);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longclickPowerchargeHandler.removeCallbacksAndMessages(null);
                        break;
                }

                return true;
            }
        });

        mImgViewBattery = (ImageView) findViewById(R.id.imgViewBattery);
        mImgViewLEDGreen = (ImageView) findViewById(R.id.imgViewLEDGreen);
        mImgViewLEDYellow = (ImageView) findViewById(R.id.imgViewLEDYellow);
        mImgViewLEDRed = (ImageView) findViewById(R.id.imgViewLEDRed);
        mTxtViewProgrammeName = (TextView) findViewById(R.id.txtViewProgrammeName);

        if (!BluetoothController.mConnected) {
            Intent intent = new Intent(getBaseContext(), BluetoothActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        ChargerModel.clearBuffer();

        ChargerModel.getProgrammeName(new ChargerModel.ProgrammeNameCallback() {
            @Override
            public void Response(String programmeName) {
                mTxtViewProgrammeName.setText(programmeName);
            }
        });

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateLED();
            }
        }, 0, mUpdateLEDDelayMS);
    }


    private void updateLED()
    {
        ChargerModel.getLEDStatus(new ChargerModel.LEDStatusCallback() {
            @Override
            public void Response(ChargerModel.LEDStatus green, ChargerModel.LEDStatus yellow, ChargerModel.LEDStatus red) {
                if (green == ChargerModel.LEDStatus.ON) {
                    mImgViewLEDGreen.setBackgroundResource(R.drawable.led_green);
                }
                else if (green == ChargerModel.LEDStatus.SLOW_BLINK) {
                    mImgViewLEDGreen.setBackgroundResource(R.drawable.led_green_blink_slow);
                    ((AnimationDrawable)mImgViewLEDGreen.getBackground()).start();
                }
                else if (green == ChargerModel.LEDStatus.OFF) {
                    mImgViewLEDGreen.setBackgroundResource(R.drawable.led_off);
                }

                if (yellow == ChargerModel.LEDStatus.ON) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_yellow);
                    mImgViewBattery.setBackgroundResource(R.drawable.battery4);
                }
                else if (yellow == ChargerModel.LEDStatus.SLOW_BLINK) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_yellow_blink_slow);
                    ((AnimationDrawable)mImgViewLEDYellow.getBackground()).start();

                    mImgViewBattery.setBackgroundResource(R.drawable.battery);
                    ((AnimationDrawable)mImgViewBattery.getBackground()).start();
                }
                else if (yellow == ChargerModel.LEDStatus.FAST_BLINK) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_yellow_blink_fast);
                    ((AnimationDrawable)mImgViewLEDYellow.getBackground()).start();

                    mImgViewBattery.setBackgroundResource(R.drawable.battery);
                    ((AnimationDrawable)mImgViewBattery.getBackground()).start();
                }
                else if (yellow == ChargerModel.LEDStatus.OFF) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_off);
                    mImgViewBattery.setBackgroundResource(R.drawable.battery0);
                }

                if (red == ChargerModel.LEDStatus.ON) {
                    mImgViewLEDRed.setBackgroundResource(R.drawable.led_red);
                }
                else if (red == ChargerModel.LEDStatus.SLOW_BLINK) {
                    mImgViewLEDRed.setBackgroundResource(R.drawable.led_red_blink_slow);
                    ((AnimationDrawable)mImgViewLEDRed.getBackground()).start();
                }
                else if (red == ChargerModel.LEDStatus.OFF)
                {
                    mImgViewLEDRed.setBackgroundResource(R.drawable.led_off);
                }
            }
        });
    }
}
