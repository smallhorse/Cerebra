package com.ubtrobot.cerebra.model;

/**
 * System configure of this robot, whose data are provided by other apk.
 */

public class RobotSystemConfig {

    private boolean isPowerOffMode = false;

    private WakeupConfig wakeupConfig = new WakeupConfig();

    public void setPowerOffMode(boolean powerOffMode) {
        isPowerOffMode = powerOffMode;
    }

    public boolean isPowerOffMode() {
        return isPowerOffMode;
    }

    public WakeupConfig getWakeupConfig() {
        return wakeupConfig;
    }

    public void setWakeupConfig(WakeupConfig wakeupConfig) {
        this.wakeupConfig = wakeupConfig;
    }

}
