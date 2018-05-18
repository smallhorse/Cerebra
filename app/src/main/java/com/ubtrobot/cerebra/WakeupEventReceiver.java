package com.ubtrobot.cerebra;

import android.content.Intent;

import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;
import com.ubtrobot.wakeup.StaticWakeupEventReceiver;
import com.ubtrobot.wakeup.WakeupEvent;

/**
 * A Receiver that receives @{WakeupEvnet}
 */
public class WakeupEventReceiver extends StaticWakeupEventReceiver {
    private static final Logger LOGGER = ULog.getLogger("WakeupEventReceiver");

    @Override
    public void onWakeup(WakeupEvent wakeupEvent) {
        LOGGER.i("wakeup event received: " + wakeupEvent.toString());

        startService(new Intent(WakeupEventReceiver.this, CerebraService.class)
                .putExtra(CerebraService.WAKEUP_EVENT, wakeupEvent));
    }
}
