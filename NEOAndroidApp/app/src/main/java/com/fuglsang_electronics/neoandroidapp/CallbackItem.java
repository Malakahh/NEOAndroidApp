package com.fuglsang_electronics.neoandroidapp;

public class CallbackItem {
    public ChargerModel.Callback mCallback;
    public int mBytesToRead;

    public  CallbackItem(int bytesToRead, ChargerModel.Callback callback)
    {
        this.mBytesToRead = bytesToRead;
        this.mCallback = callback;
    }
}
