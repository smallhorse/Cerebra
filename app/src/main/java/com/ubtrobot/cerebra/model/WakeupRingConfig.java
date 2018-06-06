package com.ubtrobot.cerebra.model;



public class WakeupRingConfig {

    public static final int WAKEUP_RING_TYPE_NONE = 0;
    public static final int WAKEUP_RING_TYPE_TONE = 1;
    public static final int WAKEUP_RING_TYPE_SPEECH = 2;

    private int wakeupRingType = WAKEUP_RING_TYPE_TONE;

    /**
     * It is the URI of a Tone, or the words to be said.
     */
    private String wakeupRingValue;


    public int getWakeupRingType() {
        return wakeupRingType;
    }

    public void setWakeupRingType(int wakeupRingType) {
        this.wakeupRingType = wakeupRingType;
    }

    public String getWakeupRingValue() {
        return wakeupRingValue;
    }

    public void setWakeupRingValue(String wakeupRingValue) {
        this.wakeupRingValue = wakeupRingValue;
    }
}