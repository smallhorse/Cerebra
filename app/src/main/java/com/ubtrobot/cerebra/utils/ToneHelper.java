package com.ubtrobot.cerebra.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.TextUtils;

import com.ubtrobot.cerebra.R;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;

/**
 * A helper for playing system ringtone.
 */

public class ToneHelper {

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private Context mContext;
    private Ringtone mRingtone;
    private String mUri;

    public ToneHelper(Context context) {
        mContext = context;
        mUri = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName()
                + "/" + R.raw.wakeup;
        LOGGER.i("default tone uri: " + mUri);

    }

    public Observable play(String uri) {
        return Observable.create((ObservableOnSubscribe<String>) emitter -> {

            if (!TextUtils.isEmpty(uri) && !uri.endsWith("/0")) {
                mUri = uri;
            } else {
                LOGGER.i("Invalid ringtone path, play default ringtone instead.");
            }

            if (mUri.equals(uri) && mRingtone != null) {
                mRingtone.play();
            } else {
                try {
                    mRingtone = RingtoneManager.getRingtone(mContext, Uri.parse(mUri));
                    mRingtone.setStreamType(AudioManager.STREAM_MUSIC);
                    mRingtone.play();
                }catch (Exception e) {
                    LOGGER.e("Play tone error!");
                    LOGGER.e(e);
                }
            }

            emitter.onNext(mUri);
            emitter.onComplete();
        }).delay(1, TimeUnit.SECONDS);
    }

    public void stop() {
        if (mRingtone != null && mRingtone.isPlaying()) {
            mRingtone.stop();
        }
    }

    public void release() {
        stop();
        mContext = null;
    }
}
