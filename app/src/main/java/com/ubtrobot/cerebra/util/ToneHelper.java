package com.ubtrobot.cerebra.util;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;

import com.ubtrobot.cerebra.R;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.schedulers.Schedulers;

/**
 * A helper for playing system ringtone.
 */

public class ToneHelper {

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private Context mContext;
    private volatile MediaPlayer mMediaPlayer;
    private String mDefaultUri;
    private String mCurrentUri;

    public ToneHelper(Context context) {
        mContext = context;
        mDefaultUri = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName()
                + "/" + R.raw.wakeup;
        mCurrentUri = mDefaultUri;

        LOGGER.i("default tone uri: " + mDefaultUri);
    }

    public Completable play(String uri) {
        return Completable
                .create(new CompletableOnSubscribe() {
                    @Override
                    public void subscribe(CompletableEmitter emitter) throws Exception {
                        try {
                            if (TextUtils.isEmpty(uri)) {
                                LOGGER.i("Invalid ringtone path, play default ringtone instead.");
                                mCurrentUri = mDefaultUri;
                            } else {
                                mCurrentUri = uri;
                            }

                            synchronized (this) {
                                if (mMediaPlayer == null) {
                                    mMediaPlayer = MediaPlayer.create(mContext, Uri.parse(mCurrentUri));

                                } else {
                                    if (mMediaPlayer.isPlaying()) {
                                        mMediaPlayer.stop();
                                    }
                                    mMediaPlayer.reset();
                                    mMediaPlayer.setDataSource(mContext, Uri.parse(mCurrentUri));
                                    mMediaPlayer.prepare();
                                }
                            }

                            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

                            mMediaPlayer.setOnCompletionListener(mp -> emitter.onComplete());

                            mMediaPlayer.start();
                        } catch (Exception e) {
                            LOGGER.e("Tone play error!");
                            LOGGER.e(e);
                            emitter.onError(e);
                        }
                    }
                })
                .subscribeOn(Schedulers.single())
                .observeOn(Schedulers.single())
                .onErrorComplete();
    }

    public void stop() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void release() {
        stop();
        mContext = null;
    }
}
