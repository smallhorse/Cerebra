package com.ubtrobot.cerebra.Constant;

/**
 * Constant values in Setting Content Provider
 */

public class SettingConstant {

    // Setting URI
    public static final String SETTING_URI = "content://com.ubtechinc.settings.provider/CruiserSettings";

    // A switch that enables hand
    public static final String SETTING_HAND_MOTION_STATE_KEY = "cruiser_hand_motion_state";

    // A switch that enables chassis movement
    public static final String SETTING_CHASSIS_MOTION_STATE_KEY = "cruiser_chassis_motion_state";

    // A switch that enables robot to turn to the direction where the wakeup sound come from.
    public static final String SETTING_SOUND_LOCALIZATION_KEY = "key_sound_localization";


    public static final String SETTING_WAKEUP_SPEECH_RINGTONE_KEY = "wakeup_ringtone_voice";
    public static final String SETTING_WAKEUP_KEY_RINGTONE_KEY = "wakeup_ringtone_touch";
    public static final String SETTING_WAKEUP_FACEIN_RINGTONE_KEY = "wakeup_ringtone_visual";

    // The type for wakeup event notification, a speech or ringtone.
    public static final String SETTING_WAKEUP_SPEECH_RINGTONE_TYPE_KEY = "wakeup_ringtone_voice_type";
    public static final String SETTING_WAKEUP_KEY_RINGTONE_TYPE_KEY = "wakeup_ringtone_touch_type";
    public static final String SETTING_WAKEUP_FACEIN_RINGTONE_TYPE_KEY = "wakeup_ringtone_visual_type";

}
