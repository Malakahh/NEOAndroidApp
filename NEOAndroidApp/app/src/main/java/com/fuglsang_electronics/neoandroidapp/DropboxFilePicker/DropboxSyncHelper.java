/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.fuglsang_electronics.neoandroidapp.DropboxFilePicker;

import android.content.Context;
import android.preference.PreferenceManager;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;

/**
 * This class has some utility functions for dealing with Dropbox. You need
 * to input your API keys below.
 * See Dropbox for more information:
 * https://www.dropbox.com/developers/core/start/android
 * <p/>
 * You also need to drop your APP_KEY in the manifest in
 * com.dropbox.client2.android.AuthActivity
 * See here for info:
 * https://www.dropbox.com/developers/core/sdks/android
 */
public class DropboxSyncHelper {
    // Change these two lines to your app's stuff
    final static public String APP_KEY = "2bzh054sl4653ke";
    final static public String APP_SECRET = "q5iqg9ez8xn7hb1";

    public static final String PREF_DROPBOX_TOKEN = "dropboxtoken";

    public static DropboxAPI<AndroidAuthSession> getDBApi(
            final Context context) {
        final DropboxAPI<AndroidAuthSession> mDBApi;

        final AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
        final AndroidAuthSession session;

        if (PreferenceManager.getDefaultSharedPreferences(context)
                .contains(PREF_DROPBOX_TOKEN)) {
            session = new AndroidAuthSession(appKeys,
                    PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(PREF_DROPBOX_TOKEN, ""));
        } else {
            session = new AndroidAuthSession(appKeys);
        }
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        return mDBApi;
    }

    /**
     * Save the dropbox oauth token so we can reuse the session without
     * logging in again.
     * @param context
     * @param token
     */
    public static void saveToken(final Context context, final String token) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_DROPBOX_TOKEN, token).apply();
    }
}
