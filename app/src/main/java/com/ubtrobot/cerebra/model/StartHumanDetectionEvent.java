package com.ubtrobot.cerebra.model;

/**
 * Enable Human Detection
 */
public class StartHumanDetectionEvent {
    private boolean enable;

    public static StartHumanDetectionEvent newInstance() {

        return new StartHumanDetectionEvent();
    }

    public boolean isEnable() {
        return enable;
    }

    public StartHumanDetectionEvent setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }
}
