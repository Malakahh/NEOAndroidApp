package com.fuglsang_electronics.neoandroidapp;

public class CallbackItem {
    public byte[] mQuery;
    public ChargerModel.Callback mCallback;
    public int mBytesToRead;

    public  CallbackItem(byte[] query, int bytesToRead, ChargerModel.Callback callback)
    {
        this.mQuery = query;
        this.mBytesToRead = bytesToRead;
        this.mCallback = callback;
    }
}
