package com.ubtrobot.cerebra.util;



public class CancelableAdapter implements com.ubtrobot.async.Cancelable {
    private com.ubtrobot.transport.message.Cancelable transportCancelable;

    public CancelableAdapter(com.ubtrobot.transport.message.Cancelable cancelable) {
        transportCancelable = cancelable;
    }

    @Override
    public boolean cancel() {
        if (transportCancelable != null) {
            transportCancelable.cancel();
        }
        return false;
    }
}
