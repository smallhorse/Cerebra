package com.ubtrobot.cerebra.util;

import com.ubtrobot.cerebra.Constant.SensorConstant;
import com.ubtrobot.cerebra.model.WakeupEvent;
import com.ubtrobot.sensor.SensorListener;
import com.ubtrobot.sensor.SensorManager;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

import java.util.ArrayDeque;
import java.util.Queue;

import static com.ubtrobot.cerebra.Constant.SensorConstant.HUMAN_DETECT;


public class WakeupStateHelper {

    private static final Logger LOGGER = ULog.getLogger("WakeupStateHelper");

    private static final int MAX_QUEUE_SIZE = 1;

    private SensorManager mSensorManager;
    private SensorListener mHumanDetectListener;
    private SensorListener mSoundDetectListener;
    private ContentProviderHelper mContentProviderHelper;

    // Wakeup event queue that holds effective events only, events that trigger wakeup process.
    private Queue<WakeupEvent> mWakeupQueue = new ArrayDeque<>();


    public WakeupStateHelper(SensorManager sensorManager, ContentProviderHelper contentProviderHelper) {
        this.mSensorManager = sensorManager;
        this.mContentProviderHelper = contentProviderHelper;

        mHumanDetectListener = (sensor, sensorEvent) -> {

            try {
                int direction = Math.round(sensorEvent.getValues()[0]);

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

        mSensorManager.registerSensorListener(HUMAN_DETECT, mHumanDetectListener);

        mSensorManager.registerSensorListener(SensorConstant.SOUND_DETECT, mSoundDetectListener);
    }

    public void addEffectiveWakeupEvent(WakeupEvent wakeupEvent) {
        mWakeupQueue.add(wakeupEvent);
        if (mWakeupQueue.size() > MAX_QUEUE_SIZE) {
            mWakeupQueue.remove();
        }
    }

    public boolean isLastEventWakedByVision() {
        if(mWakeupQueue.size() >= 1) {
            WakeupEvent wakeupEvent = mWakeupQueue.remove();
            return wakeupEvent.isWakedByHumanIN();
        } else {
            return false;
        }
    }

    // This function is not valid as HUMAN_CLOSER and HUMAN_AWAY are exclusive
    public boolean isNobodyHere() {
        int humanIn = 0;
        int humanOut = 0;

        for (WakeupEvent wakeupEvent : mWakeupQueue) {
            humanIn += wakeupEvent.isWakedByHumanIN() ? 0 : 1;
            humanOut += wakeupEvent.isWakedByHumanOut() ? 0 : 1;
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

        mSensorManager.unregisterSensorListener(HUMAN_DETECT, mHumanDetectListener);
    }
}
