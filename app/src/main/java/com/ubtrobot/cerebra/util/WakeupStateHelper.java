package com.ubtrobot.cerebra.util;

import com.ubtrobot.cerebra.Constant.SensorConstant;
import com.ubtrobot.cerebra.model.WakeupEvent;
import com.ubtrobot.sensor.Sensor;
import com.ubtrobot.sensor.SensorEvent;
import com.ubtrobot.sensor.SensorListener;
import com.ubtrobot.sensor.SensorManager;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

import java.util.ArrayDeque;
import java.util.Queue;


public class WakeupStateHelper {

    private static final Logger LOGGER = ULog.getLogger("WakeupStateHelper");


    private static final int MAX_QUEUE_SIZE = 6;

    private SensorManager mSensorManager;
    private SensorListener mHumanDetectListener;
    private SensorListener mSoundDetectListener;
    private ContentProviderHelper mContentProviderHelper;

    private Queue<WakeupEvent> mWakeupQueue = new ArrayDeque<>();


    public WakeupStateHelper(SensorManager sensorManager, ContentProviderHelper contentProviderHelper) {
        this.mSensorManager = sensorManager;
        this.mContentProviderHelper = contentProviderHelper;

        mHumanDetectListener = (sensor, sensorEvent) -> {
            int direction = Math.round(sensorEvent.getValues()[0]);

            try {
                switch (direction) {
                    case SensorConstant.HUMAN_AWAY:
                        LOGGER.i("HUMAN_AWAY");

                        if (sensorEvent.getValues()[1] > SensorConstant.HUMAN_DETECT_DISTANT_THRESHOLD) {
                            LOGGER.i("HUMAN_AWAY: wakeup");

                            sendVisualWakeupEvent(WakeupEvent.newInstance()
                                    .setType(WakeupEvent.TYPE_HUMAN_OUT));
                        }

                        break;
                    case SensorConstant.HUMAN_CLOSER:
                        LOGGER.i("HUMAN_CLOSER");

                        if (sensorEvent.getValues()[1] < SensorConstant.HUMAN_DETECT_DISTANT_THRESHOLD) {
                            LOGGER.i("HUMAN_CLOSER: wakeup");

                            sendVisualWakeupEvent(
                                    WakeupEvent.newInstance()
                                            .setType(WakeupEvent.TYPE_HUMAN_IN));

                        }
                        break;
                }
            } catch (Exception e) {
                LOGGER.e("HumanDetectListener Error");
                LOGGER.e(e);
            }

        };

        mSoundDetectListener = (sensor, sensorEvent) -> {
            float angle = sensorEvent.getValues()[0];

            sendSoundWakeupEvent(
                    WakeupEvent.newInstance()
                            .setType(WakeupEvent.TYPE_VOICE)
                            .setAngle(angle));
        };

        mSensorManager.registerSensorListener(SensorConstant.HUMAN_DETECT, mHumanDetectListener);

        mSensorManager.registerSensorListener(SensorConstant.SOUND_DETECT, mSoundDetectListener);
    }

    public void addWakeupEvent(WakeupEvent wakeupEvent) {
        mWakeupQueue.add(wakeupEvent);
        if (mWakeupQueue.size() > MAX_QUEUE_SIZE) {
            mWakeupQueue.remove();
        }
    }

    public boolean isNobodyHere() {
        int humanIn = 0;
        int humanOut = 0;

        for(WakeupEvent wakeupEvent : mWakeupQueue) {
            humanIn += wakeupEvent.isWakedByHumanIN() ? 0 : 1;
            humanOut += wakeupEvent.isWakedByHumanOut() ? 0: 1;
        }

        return humanIn > 0 && humanOut >= humanIn;
    }

    private void sendVisualWakeupEvent(WakeupEvent wakeupEvent) {
        if (mContentProviderHelper
                .getRobotSystemConfig()
                .getWakeupConfig()
                .isVisualWakeUpEnabled()) {
            RxBus.getInstance().post(wakeupEvent);
        }
    }

    private void sendSoundWakeupEvent(WakeupEvent wakeupEvent) {
        RxBus.getInstance().post(wakeupEvent);
    }

    public void release() {

        mSensorManager.unregisterSensorListener(SensorConstant.SOUND_DETECT, mSoundDetectListener);

        mSensorManager.unregisterSensorListener(SensorConstant.HUMAN_DETECT, mHumanDetectListener);
    }
}
