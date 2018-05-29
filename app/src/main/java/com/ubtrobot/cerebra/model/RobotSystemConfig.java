package com.ubtrobot.cerebra.model;

import com.ubtrobot.wakeup.WakeupEvent;

import java.util.HashMap;
import java.util.Map;

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


    public static class WakeupConfig {
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


        public static class WakeupRingConfig {

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
    }


}
