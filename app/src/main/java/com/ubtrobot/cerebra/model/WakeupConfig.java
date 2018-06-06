package com.ubtrobot.cerebra.model;

import com.ubtrobot.wakeup.WakeupEvent;

import java.util.HashMap;
import java.util.Map;

public class WakeupConfig {

    private Map<Integer, WakeupRingConfig> wakeupRingConfigs = new HashMap<>();

    private boolean isRotateRobotOn = false;

    public WakeupConfig() {
        wakeupRingConfigs.put(WakeupEvent.TYPE_VOICE, new WakeupRingConfig());
        wakeupRingConfigs.put(WakeupEvent.TYPE_SIMULATE, new WakeupRingConfig());
        wakeupRingConfigs.put(WakeupEvent.TYPE_VISION, new WakeupRingConfig());
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

    public boolean isRotateRobotOn() {
        return isRotateRobotOn;
    }

    public void setRotateRobotOn(boolean rotateRobotOn) {
        this.isRotateRobotOn = rotateRobotOn;
    }

}