package com.ubtrobot.cerebra.model;


public class WakeupEvent {
    public static final int TYPE_VOICE = 1;
    public static final int TYPE_KEY = 2;
    public static final int TYPE_HUMAN_IN = 3;
    public static final int TYPE_HUMAN_OUT = 4;

    private int type;
    private float angle;
    private float distance;
    private long timeStamp;

    public boolean isWakedByVoice() {
        return type == TYPE_VOICE;
    }


    public boolean isWakedByKey() {
        return type == TYPE_KEY;
    }


    public boolean isWakedByHumanIN() {
        return type == TYPE_HUMAN_IN;
    }

    public boolean isWakedByHumanOut() {
        return type == TYPE_HUMAN_OUT;
    }

    public static WakeupEvent newInstance() {
        return new WakeupEvent()
                .setTimeStamp(System.currentTimeMillis());
    }

    public int getType() {
        return type;
    }

    public WakeupEvent setType(int type) {
        this.type = type;
        return this;
    }

    public float getAngle() {
        return angle;
    }

    public WakeupEvent setAngle(float angle) {
        this.angle = angle;
        return this;
    }

    public float getDistance() {
        return distance;
    }

    public WakeupEvent setDistance(float distance) {
        this.distance = distance;
        return this;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public WakeupEvent setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }


}
