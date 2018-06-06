package com.ubtrobot.cerebra.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;

/**
 * A util that deals with Content Provider
 */

public class ContentProviderUtil {

    public static HashMap<String, String> getSettingsString(Context context,
                                                            String settingUri,
                                                            String[] selectionArgs) {

        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.parse(settingUri);
        String[] projection = {"key", "value"};

        StringBuilder builder = new StringBuilder();
        builder.append("key IN (");

        for (int i = 0; i < selectionArgs.length - 1; i++) {
            builder.append("?,");
        }
        builder.append("?)");

        Cursor result = null;
        HashMap<String, String> rets = new HashMap<>();

        try {
            result = cr.query(uri, projection, builder.toString(), selectionArgs, null);
            if (result != null) {
                while (result.moveToNext()) {
                    String key = result.getString(result.getColumnIndex("key"));
                    String info = result.getString(result.getColumnIndex("value"));
                    rets.put(key, info);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (result != null) {
                result.close();
            }
        }
        return rets;
    }
}
