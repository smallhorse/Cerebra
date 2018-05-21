package com.ubtrobot.cerebra;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.ubtrobot.Robot;
import com.ubtrobot.async.rx.ObservableFromProgressivePromise;
import com.ubtrobot.async.rx.ObservableFromPromise;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.master.interactor.MasterInteractor;
import com.ubtrobot.master.skill.SkillIntent;
import com.ubtrobot.master.skill.SkillsProxy;
import com.ubtrobot.master.transport.message.CallGlobalCode;
import com.ubtrobot.master.transport.message.parcel.ParcelableParam;
import com.ubtrobot.speech.Recognizer;
import com.ubtrobot.speech.SpeechManager;
import com.ubtrobot.speech.UnderstandOption;
import com.ubtrobot.speech.UnderstandResult;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.Response;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;
import com.ubtrobot.wakeup.WakeupEvent;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.ubtrobot.cerebra.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_NONE;
import static com.ubtrobot.cerebra.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_SPEECH;
import static com.ubtrobot.cerebra.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_TONE;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_SIMULATE;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_VISION;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_VOICE;


/**
 * Cerebra service, the main service of this application.
 */
public class CerebraService extends Service {

    public static final String WAKEUP_EVENT = "WAKEUP_EVENT";
    private static final String MASTER_INTERACTOR = "MasterInteractor";

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private MasterContext mMasterContext;
    private SpeechManager mSpeechManager;
    private MasterInteractor mMasterInteractor;
    private SkillsProxy mSkillsProxy;

    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    private ToneHelper mToneHelper;

    @Override
    public void onCreate() {
        super.onCreate();

        mMasterContext = Robot.master().getGlobalContext();
        mSpeechManager = new SpeechManager(mMasterContext);
        mMasterInteractor = Robot.master().getOrCreateInteractor(MASTER_INTERACTOR);
        mSkillsProxy = mMasterInteractor.createSkillsProxy();

        mToneHelper = new ToneHelper(this);
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

    private void stopRobotBackgroundTask() {
        mCompositeDisposable.clear();
    }

    /**
     * Get Robot System Configure from another apk.
     *
     * @return Current system configure
     */
    private RobotSystemConfig getRobotSystemConfig() {
        return new RobotSystemConfig();
    }


    private Observable palyWakeupNotification(WakeupEvent wakeupEvent,
                                              RobotSystemConfig robotSystemConfig) {
        RobotSystemConfig.WakeupConfig.WakeupRingConfig wakeupRingConfig =
                robotSystemConfig.getWakeupConfig().getWakeupRingConfig(wakeupEvent.getType());

        switch (wakeupRingConfig.getWakeupRingType()) {
            case WAKEUP_RING_TYPE_TONE:
                return mToneHelper.play(wakeupRingConfig.getWakeupRingValue());
            case WAKEUP_RING_TYPE_SPEECH:
                return talk(wakeupRingConfig.getWakeupRingValue());
            case WAKEUP_RING_TYPE_NONE:
            default:
                return Observable.empty();
        }
    }

    private void handleWakeupEvent(WakeupEvent wakeupEvent) {

        RobotSystemConfig robotSystemConfig = getRobotSystemConfig();

        // Return if it is power off mode
        if (robotSystemConfig.isPowerOffMode()) {
            LOGGER.i("Power off mode! Wakeup event is ignored.");
            return;
        }

        // Interrupt current robot background task
        if (wakeupEvent.getType() == TYPE_VOICE || wakeupEvent.getType() == TYPE_SIMULATE) {
            LOGGER.i("Robot background task is interrupted.");
            stopRobotBackgroundTask();
        } else if (wakeupEvent.getType() == TYPE_VISION) {
            // Ignore vision wakeup event in wakeup status.
            if(!mCompositeDisposable.isDisposed()) {
                LOGGER.i("Ignore vision wakeup event in wakeup status.");
                return;
            }
        }


        Disposable disposable = palyWakeupNotification(wakeupEvent, robotSystemConfig)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .ignoreElements()
                .andThen(new ObservableFromProgressivePromise<>(mSpeechManager.recognize()))
                .filter(progressOrDone -> progressOrDone.isDone())
                .flatMap(progressOrDone -> dispatchEventLocally(progressOrDone.getDone()))
                .flatMap(recognizeResult -> understand(recognizeResult))
                .flatMap(understandResult -> dispatchEventOnline(understandResult))
                .subscribe(o -> {
                    LOGGER.i("Wakeup process started.");
                }, throwable -> {
                    String msg = ((Throwable) throwable).getMessage();
                    LOGGER.e(msg);
                    mCompositeDisposable.add(
                            talk(msg).subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .subscribe());
                }, () -> LOGGER.i("Wakeup process completed."));

        mCompositeDisposable.add(disposable);
    }

    private Observable<Recognizer.RecognizeResult> dispatchEventLocally(final Recognizer.RecognizeResult recognizeResult) {

        SkillIntent skillIntent = new SkillIntent(SkillIntent.CATEGORY_SPEECH);
        skillIntent.setSpeechUtterance(recognizeResult.getText());
        Observable<Recognizer.RecognizeResult> observable = Observable.create(emitter -> {
            try {
                Response response = mSkillsProxy.call(skillIntent);
                LOGGER.i("Calling skill succeeded." + response.toString());
                emitter.onComplete();
            } catch (CallException e) {
                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    emitter.onError(new Exception(getString(R.string.system_failure)));
                    LOGGER.e(e);
                } else {
                    LOGGER.i("DispatchEventLocally: Calling skill failed.");

                    emitter.onNext(recognizeResult);
                    emitter.onComplete();
                }
            }
        });

        return observable;
    }

    private Observable<UnderstandResult> understand(final Recognizer.RecognizeResult recognizeResult) {
        LOGGER.i("recognizeResult.getText():" + recognizeResult.getText());
        return new ObservableFromPromise<>(mSpeechManager.understand(recognizeResult.getText(), UnderstandOption.DEFAULT));
    }

    private Observable dispatchEventOnline(UnderstandResult understandResult) {
        return Observable.create((ObservableOnSubscribe<UnderstandResult>) emitter -> {

            String action = understandResult.getIntent().getName();
            LOGGER.i("UnderstandResult:" + action);
            try {
                Response response = mSkillsProxy.call(action, ParcelableParam.create(understandResult));
                LOGGER.i("Calling skill succeeded." + response.toString());
                emitter.onComplete();
            } catch (CallException e) {
                LOGGER.i("DispatchEventOnline: Calling skill failed.");
                LOGGER.e(e);

                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    emitter.onError(new Exception(getString(R.string.system_failure)));
                } else {
                    emitter.onError(new Exception(getString(R.string.i_do_not_understand_what_you_are_talking_about)));
                }
            } catch (Exception e) {
                LOGGER.i("DispatchEventOnline: Calling skill failed. Other exception.");
                LOGGER.e(e);

                emitter.onError(new Exception(getString(R.string.system_failure)));

            }
        });
    }

    private Observable talk(String msg) {
        if (!TextUtils.isEmpty(msg)) {
            LOGGER.i("Talk: " + msg);
            return new ObservableFromPromise(mSpeechManager.synthesize(msg));
        } else {
            return Observable.empty();
        }
    }

    @Override
    public void onDestroy() {
        stopRobotBackgroundTask();
        mToneHelper.release();

        super.onDestroy();
    }
}
