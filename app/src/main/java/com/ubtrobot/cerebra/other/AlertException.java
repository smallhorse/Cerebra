package com.ubtrobot.cerebra.other;


public class AlertException extends Exception {

    public String alertMsg;

    public static AlertException newInstance(String alertMsg) {
        return new AlertException().setAlertMsg(alertMsg);
    }

    public String getAlertMsg() {
        return alertMsg;
    }

    public AlertException setAlertMsg(String alertMsg) {
        this.alertMsg = alertMsg;
        return this;
    }
}
