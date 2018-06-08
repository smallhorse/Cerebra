package com.ubtrobot.cerebra;

import android.app.Application;

import com.ubtrobot.Robot;
import com.ubtrobot.ulog.LoggerFactory;
import com.ubtrobot.ulog.ULog;
import com.ubtrobot.ulog.logger.android.AndroidLoggerFactory;

/**
 * Cerebra Application
 *
 */
public class CerebraApp extends Application {

    private static CerebraApp mApp;

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = CerebraApp.this;

        // Init
        Robot.initialize(this);
        LoggerFactory loggerFactory = new AndroidLoggerFactory();
        ULog.setup(loggerFactory);

        // Print debug log
        Robot.setLoggerFactory(loggerFactory);
    }

    public static CerebraApp getInstance() {
        return mApp;
    }
}
