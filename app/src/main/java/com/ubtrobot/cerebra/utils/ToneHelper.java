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
    private String mDefaultUri;
    private String mLastUri;

    public ToneHelper(Context context) {
        mContext = context;
        mDefaultUri = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName()
                + "/" + R.raw.wakeup;
        mLastUri = mDefaultUri;

        LOGGER.i("default tone uri: " + mDefaultUri);

    }

    public Observable play(String uri) {
        return Observable.create((ObservableOnSubscribe<String>) emitter -> {

            String currentUri;
            if (TextUtils.isEmpty(uri)) {
                LOGGER.i("Invalid ringtone path, play default ringtone instead.");
                currentUri = mDefaultUri;
            } else {
                currentUri = uri;
            }

            if (mLastUri.equals(currentUri) && mRingtone != null) {
                mRingtone.play();
            } else {
                try {
                    mRingtone = RingtoneManager.getRingtone(mContext, Uri.parse(currentUri));
                    mRingtone.setStreamType(AudioManager.STREAM_MUSIC);
                    mRingtone.play();
                    mLastUri = currentUri;
                }catch (Exception e) {
                    LOGGER.e("Play tone error!");
                    LOGGER.e(e);
                }
            }

            emitter.onNext(mLastUri);
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
