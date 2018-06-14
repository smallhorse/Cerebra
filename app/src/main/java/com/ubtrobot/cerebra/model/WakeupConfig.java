package com.ubtrobot.cerebra.model;

import java.util.HashMap;
import java.util.Map;

public class WakeupConfig {

    private Map<Integer, WakeupRingConfig> wakeupRingConfigs = new HashMap<>();

    private boolean isRotateRobotEnabled = false;

    /**
     * It equals to the Continuous Speech Mode, in which speech recognize stops passively.
     */
    private boolean isVisualWakeUpEnabled = false;

    public WakeupConfig() {
        wakeupRingConfigs.put(WakeupEvent.TYPE_VOICE, new WakeupRingConfig());
        wakeupRingConfigs.put(WakeupEvent.TYPE_KEY, new WakeupRingConfig());
        wakeupRingConfigs.put(WakeupEvent.TYPE_HUMAN_IN, new WakeupRingConfig());
    }

    /**
     * Get a wakeup ring configure for a given wakeup type.
     *
     * @param wakeupType
     * @return a wakeup ring configure
     */
    public WakeupRingConfig getWakeupRingConfig(int wakeupType) {
        return wakeupRingConfigs.get(wakeupType);
    }

    public boolean isRotateRobotEnabled() {
        return isRotateRobotEnabled;
    }

    public void setRotateRobotEnabled(boolean rotateRobotEnabled) {
        this.isRotateRobotEnabled = rotateRobotEnabled;
    }

    public boolean isVisualWakeUpEnabled() {
        return isVisualWakeUpEnabled;
    }

    public void setVisualWakeUpEnabled(boolean visualWakeUpEnabled) {
        isVisualWakeUpEnabled = visualWakeUpEnabled;
    }
}