package com.fuglsang_electronics.neoandroidapp;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Splash extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent bt = new Intent(Splash.this, BluetoothActivity.class);
                Splash.this.startActivity(bt);
                Splash.this.finish();
            }
        }, SPLASH_DISPLAY_LENGTH);
    }
}
