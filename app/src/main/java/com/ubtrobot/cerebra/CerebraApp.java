package com.ubtrobot.cerebra;

import android.app.Application;

import com.ubtrobot.framework.Robot;
import com.ubtrobot.ulog.LoggerFactory;
import com.ubtrobot.ulog.ULog;
import com.ubtrobot.ulog.logger.android.AndroidLoggerFactory;

/**
 * 初始化进程
 *
 */
public class CerebraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化
        Robot.initialize(this);
        LoggerFactory loggerFactory = new AndroidLoggerFactory();
        ULog.setup(loggerFactory);

        // 配置 Master SDK 打印调试日志
        Robot.setLoggerFactory(loggerFactory);
    }
}
