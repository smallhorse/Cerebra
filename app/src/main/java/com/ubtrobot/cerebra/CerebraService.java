package com.ubtrobot.cerebra;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.ubtrobot.async.Cancelable;
import com.ubtrobot.async.DoneCallback;
import com.ubtrobot.async.FailCallback;
import com.ubtrobot.cerebra.util.CancelableAdapter;
import com.ubtrobot.framework.Robot;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.master.interactor.MasterInteractor;
import com.ubtrobot.master.skill.SkillIntent;
import com.ubtrobot.master.skill.SkillsProxy;
import com.ubtrobot.master.transport.message.CallGlobalCode;
import com.ubtrobot.speech.RecognizeException;
import com.ubtrobot.speech.Recognizer;
import com.ubtrobot.speech.SpeechManager;
import com.ubtrobot.speech.UnderstandException;
import com.ubtrobot.speech.UnderstandOption;
import com.ubtrobot.speech.Understander;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.Request;
import com.ubtrobot.transport.message.Response;
import com.ubtrobot.transport.message.ResponseCallback;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;
import com.ubtrobot.wakeup.WakeupEvent;

import java.util.Stack;

import static com.ubtrobot.wakeup.WakeupEvent.TYPE_VOICE;


/**
 * 中控的服务
 */
public class CerebraService extends Service {

    public static final String WAKEUP_EVENT = "WAKEUP_EVENT";
    private static final String MASTER_INTERACTOR = "MasterInteractor";

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private MasterContext mMasterContext;
    private SpeechManager mSpeechManager;
    private MasterInteractor mMasterInteractor;
    private SkillsProxy mSkillsProxy;

    // 用于管理 promise
    private Stack<Cancelable> promiseStack = new Stack<>();

    @Override
    public void onCreate() {
        super.onCreate();

        mMasterContext = Robot.master().getGlobalContext();
        mSpeechManager = new SpeechManager(mMasterContext);
        mMasterInteractor = Robot.master().getOrCreateInteractor(MASTER_INTERACTOR);
        mSkillsProxy = mMasterInteractor.createSkillsProxy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            WakeupEvent event = intent.getParcelableExtra(WAKEUP_EVENT);
            if (event != null) {
                handleWakeupEvent(event);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleWakeupEvent(WakeupEvent wakeupEvent) {
        // 在唤醒状态，如果是是语言唤醒，则中断当前的操作;否则忽略这个唤醒
        if (!promiseStack.empty()) {
            if (wakeupEvent.getType() == TYPE_VOICE) {
                LOGGER.i("Voice wakeup event in Wakeup state. Interrupt");

                cancelPromises();
            } else {
                LOGGER.i("Wakeup event ignored");
                return;
            }
        }

        com.ubtrobot.async.Cancelable cancelable =
                mSpeechManager.recognize().done(new DoneCallback<Recognizer.RecognizeResult>() {
                    @Override
                    public void onDone(final Recognizer.RecognizeResult recognizeResult) {
                        // 调用语音识别之后，优先调用 skill 处理
                        LOGGER.i("Recognize. Done. result is: " + recognizeResult.getText());

                        dispatchLocally(recognizeResult);
                    }
                }).fail(new FailCallback<RecognizeException>() {
                    @Override
                    public void onFail(RecognizeException e) {
                        LOGGER.e(e);

                        alertFailureMsg("我不知道你说的是什么");
                        promiseStack.clear();
                    }
                });

        promiseStack.push(cancelable);
    }

    private void dispatchLocally(final Recognizer.RecognizeResult recognizeResult) {
        SkillIntent skillIntent = new SkillIntent(recognizeResult.getText());
        com.ubtrobot.transport.message.Cancelable c = mSkillsProxy.call(skillIntent, new ResponseCallback() {
            @Override
            public void onResponse(Request request, Response response) {
                LOGGER.i("Calling skill succeed.");

                promiseStack.clear();
            }

            @Override
            public void onFailure(Request request, CallException e) {
                // skill 处理失败，调用语音识别
                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    alertFailureMsg("内部故障");
                    LOGGER.e(e);

                    promiseStack.clear();

                    return;
                } else {
                    dispatchOnline(recognizeResult);
                }
            }
        });
        promiseStack.push(new CancelableAdapter(c));
    }

    private void dispatchOnline(final Recognizer.RecognizeResult recognizeResult) {
        Cancelable cancelable = mSpeechManager
                .understand(recognizeResult.getText(), UnderstandOption.DEFAULT)
                .done(new DoneCallback<Understander.UnderstandResult>() {
                    @Override
                    public void onDone(Understander.UnderstandResult understandResult) {
                        LOGGER.i("Understand. Done. understandResult:" + understandResult.getAnswer());
                        //TODO: To replace after Dan releasing new speech library
//                        String xxx = understandResult.getIntent().getName(); // "weather"
//                        mSkillsProxy.call(xxx, understandResult)

                        //语音识别调用成功之后，调 skill 处理
                        SkillIntent udSkillIntent = new SkillIntent(understandResult.getAnswer());
                        com.ubtrobot.transport.message.Cancelable c = mSkillsProxy.call(udSkillIntent, new ResponseCallback() {
                            @Override
                            public void onResponse(Request request, Response response) {
                                promiseStack.clear();
                            }

                            @Override
                            public void onFailure(Request request, CallException e) {
                                LOGGER.e(e);

                                alertFailureMsg("我不能理解你说的是什么");
                                promiseStack.clear();
                            }
                        });
                        promiseStack.push(new CancelableAdapter(c));
                    }
                }).fail(new FailCallback<UnderstandException>() {
                    @Override
                    public void onFail(UnderstandException e) {
                        LOGGER.e(e);

                        alertFailureMsg("我不能理解你说的是什么");
                        promiseStack.clear();
                    }
                });
        promiseStack.push(cancelable);
    }

    private void alertFailureMsg(String msg) {
        if (!TextUtils.isEmpty(msg)) {
            mSpeechManager.synthesize(msg);
            cancelPromises();
        }
    }

    private void cancelPromises() {
        while (!promiseStack.empty()) {
            promiseStack.pop().cancel();
        }
    }
}
