package com.ubtrobot.cerebra.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.ubtrobot.cerebra.Constant.SettingConstant;
import com.ubtrobot.cerebra.model.RobotSystemConfig;
import com.ubtrobot.cerebra.model.WakeupConfig;
import com.ubtrobot.cerebra.model.WakeupRingConfig;
import com.ubtrobot.wakeup.WakeupEvent;

import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_HAND_MOTION_STATE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_SOUND_LOCALIZATION_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_VISUAL_WAKEUP_STATE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_FACEIN_RINGTONE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_FACEIN_RINGTONE_TYPE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_KEY_RINGTONE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_KEY_RINGTONE_TYPE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_SPEECH_RINGTONE_KEY;
import static com.ubtrobot.cerebra.Constant.SettingConstant.SETTING_WAKEUP_SPEECH_RINGTONE_TYPE_KEY;

/**
 * A helper that deals with content provider.
 */

public class ContentProviderHelper {

    private String[] settingKeys = {
            SETTING_HAND_MOTION_STATE_KEY,
            SETTING_SOUND_LOCALIZATION_KEY,
            SETTING_WAKEUP_SPEECH_RINGTONE_KEY,
            SETTING_VISUAL_WAKEUP_STATE_KEY,
            SETTING_WAKEUP_KEY_RINGTONE_KEY,
            SETTING_WAKEUP_FACEIN_RINGTONE_KEY,
            SETTING_WAKEUP_SPEECH_RINGTONE_TYPE_KEY,
            SETTING_WAKEUP_KEY_RINGTONE_TYPE_KEY,
            SETTING_WAKEUP_FACEIN_RINGTONE_TYPE_KEY};

    private Context mContext;
    private ContentResolver mContentResolver;

    private Disposable mDisposable;

    private RobotSystemConfig mRobotSystemConfig = new RobotSystemConfig();

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            refreshAsync();
        }
    };

    public ContentProviderHelper(@NonNull Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();

        Uri uri = Uri.parse(SettingConstant.SETTING_URI);
        mContentResolver.registerContentObserver(uri, true, mContentObserver);

        fetchData();
    }

    private void fetchData() {
        int defaultType = WakeupRingConfig.WAKEUP_RING_TYPE_TONE;
        boolean defaultSwitch = false;

        Map<String, String> map = ContentProviderUtil.getSettingsString(mContext,
                SettingConstant.SETTING_URI, settingKeys);

        // Get wakeup configure
        WakeupConfig config = mRobotSystemConfig.getWakeupConfig();

        config.setRotateRobotEnabled(StringUtil.strToBool(
                map.get(SETTING_SOUND_LOCALIZATION_KEY), defaultSwitch));

        config.setVisualWakeUpEnabled(StringUtil.strToBool(
                map.get(SETTING_VISUAL_WAKEUP_STATE_KEY), defaultSwitch));

        WakeupRingConfig wakeupRingConfig;

        wakeupRingConfig = config.getWakeupRingConfig(WakeupEvent.TYPE_VOICE);
        wakeupRingConfig.setWakeupRingType(StringUtil.strToInt(
                map.get(SETTING_WAKEUP_SPEECH_RINGTONE_TYPE_KEY), defaultType));
        wakeupRingConfig.setWakeupRingValue(map.get(SETTING_WAKEUP_SPEECH_RINGTONE_KEY));

        wakeupRingConfig = config.getWakeupRingConfig(WakeupEvent.TYPE_SIMULATE); //key
        wakeupRingConfig.setWakeupRingType(StringUtil.strToInt
                (map.get(SETTING_WAKEUP_KEY_RINGTONE_TYPE_KEY), defaultType));
        wakeupRingConfig.setWakeupRingValue(map.get(SETTING_WAKEUP_KEY_RINGTONE_KEY));

        wakeupRingConfig = config.getWakeupRingConfig(WakeupEvent.TYPE_VISION); //vison
        wakeupRingConfig.setWakeupRingType(StringUtil.strToInt(
                map.get(SETTING_WAKEUP_FACEIN_RINGTONE_TYPE_KEY), defaultType));
        wakeupRingConfig.setWakeupRingValue(map.get(SETTING_WAKEUP_FACEIN_RINGTONE_KEY));
    }

    private void refreshAsync() {
        mDisposable = Observable
                .create(emitter -> {
                    fetchData();
                    emitter.onComplete();
                })
                .subscribeOn(Schedulers.single())
                .subscribe();
    }

    public RobotSystemConfig getRobotSystemConfig() {
        return mRobotSystemConfig;
    }

    public void release() {
        if (mDisposable != null && !mDisposable.isDisposed()) {
            mDisposable.dispose();
        }

        mContentResolver.unregisterContentObserver(mContentObserver);
        mContext = null;
        mContentResolver = null;

    }
}
