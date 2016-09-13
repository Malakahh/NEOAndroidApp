package com.fuglsang_electronics.neoandroidapp;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private AnimationDrawable mAnimBattery;
    private ImageView mImgViewLEDGreen;
    private ImageView mImgViewLEDYellow;
    private ImageView mImgViewLEDRed;

    private final long mUpdateLEDDelayMS = 10000;

    private Timer mTimer = new Timer();
    private TimerTask mUpdateLEDRepeater = new TimerTask() {
        @Override
        public void run() {
            updateLED();
        }
    };
    private boolean mUpdateLEDRepeaterStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imgBattery = (ImageView) findViewById(R.id.imgViewBattery);
        imgBattery.setBackgroundResource(R.drawable.battery);
        mAnimBattery = (AnimationDrawable)imgBattery.getBackground();

        mImgViewLEDGreen = (ImageView) findViewById(R.id.imgViewLEDGreen);
        mImgViewLEDYellow = (ImageView) findViewById(R.id.imgViewLEDYellow);
        mImgViewLEDRed = (ImageView) findViewById(R.id.imgViewLEDRed);

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
    public void onWindowFocusChanged (boolean hasFocus)
    {
        mAnimBattery.start();

        if (!mUpdateLEDRepeaterStarted)
        {
            mUpdateLEDRepeaterStarted = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTimer.schedule(mUpdateLEDRepeater, 0, mUpdateLEDDelayMS);
                }
            });
        }
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
                }
                else if (yellow == ChargerModel.LEDStatus.SLOW_BLINK) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_yellow_blink_slow);
                    ((AnimationDrawable)mImgViewLEDYellow.getBackground()).start();
                }
                else if (yellow == ChargerModel.LEDStatus.FAST_BLINK) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_yellow_blink_fast);
                    ((AnimationDrawable)mImgViewLEDYellow.getBackground()).start();
                }
                else if (yellow == ChargerModel.LEDStatus.OFF) {
                    mImgViewLEDYellow.setBackgroundResource(R.drawable.led_off);
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
