package com.ubtrobot.cerebra;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.ubtrobot.Robot;
import com.ubtrobot.async.DoneCallback;
import com.ubtrobot.async.ProgressivePromise;
import com.ubtrobot.async.Promise;
import com.ubtrobot.async.rx.ObservableFromProgressivePromise;
import com.ubtrobot.async.rx.ObservableFromPromise;
import com.ubtrobot.cerebra.model.RobotSystemConfig;
import com.ubtrobot.cerebra.model.WakeupEvent;
import com.ubtrobot.cerebra.model.WakeupRingConfig;
import com.ubtrobot.cerebra.other.AlertException;
import com.ubtrobot.cerebra.util.ContentProviderHelper;
import com.ubtrobot.cerebra.util.LocationHelper;
import com.ubtrobot.cerebra.util.RxBus;
import com.ubtrobot.cerebra.util.ToneHelper;
import com.ubtrobot.cerebra.util.WakeupStateHelper;
import com.ubtrobot.exception.AccessServiceException;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.master.interactor.MasterInteractor;
import com.ubtrobot.master.skill.SkillIntent;
import com.ubtrobot.master.skill.SkillsProxy;
import com.ubtrobot.master.transport.message.CallGlobalCode;
import com.ubtrobot.master.transport.message.parcel.ParcelableParam;
import com.ubtrobot.motion.JointException;
import com.ubtrobot.motion.JointRotatingProgress;
import com.ubtrobot.motion.LocomotionException;
import com.ubtrobot.motion.LocomotionProgress;
import com.ubtrobot.motion.MotionManager;
import com.ubtrobot.sensor.SensorManager;
import com.ubtrobot.speech.RecognizeException;
import com.ubtrobot.speech.RecognizeOption;
import com.ubtrobot.speech.Recognizer;
import com.ubtrobot.speech.SpeechInteraction;
import com.ubtrobot.speech.SpeechManager;
import com.ubtrobot.speech.UnderstandOption;
import com.ubtrobot.speech.UnderstandResult;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.Request;
import com.ubtrobot.transport.message.Response;
import com.ubtrobot.transport.message.ResponseCallback;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.ubtrobot.cerebra.Constant.CerebraConstant.CHAT_SKILL_RECOGNIZE;
import static com.ubtrobot.cerebra.Constant.CerebraConstant.CHAT_SKILL_UNDERSTAND;
import static com.ubtrobot.cerebra.Constant.CerebraConstant.KEY_WAKEUP_INTENT_ACTION;
import static com.ubtrobot.cerebra.Constant.CerebraConstant.MASTER_INTERACTOR;
import static com.ubtrobot.cerebra.Constant.CerebraConstant.ROBOT_YAW_ID;
import static com.ubtrobot.cerebra.Constant.CerebraConstant.WAKE_UP_TURN_RANGE;
import static com.ubtrobot.cerebra.Constant.SensorConstant.SOUND_DETECT;
import static com.ubtrobot.cerebra.model.WakeupRingConfig.WAKEUP_RING_TYPE_NONE;
import static com.ubtrobot.cerebra.model.WakeupRingConfig.WAKEUP_RING_TYPE_SPEECH;
import static com.ubtrobot.cerebra.model.WakeupRingConfig.WAKEUP_RING_TYPE_TONE;


/**
 * Cerebra service, the main service of this application.
 */
public class CerebraService extends Service {

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private MasterContext mMasterContext;
    private SpeechManager mSpeechManager;
    private MotionManager mMotionManager;
    private SensorManager mSensorManager;
    private MasterInteractor mMasterInteractor;
    private SkillsProxy mSkillsProxy;

    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    private ToneHelper mToneHelper;
    private ContentProviderHelper mContentProviderHelper;
    private WakeupStateHelper mWakeupStateHelper;

    private volatile boolean mIsRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();

        mMasterContext = Robot.master().getGlobalContext();
        mSpeechManager = new SpeechManager(mMasterContext);
        mMotionManager = new MotionManager(mMasterContext);
        mSensorManager = new SensorManager(mMasterContext);
        mMasterInteractor = Robot.master().getOrCreateInteractor(MASTER_INTERACTOR);
        mSkillsProxy = mMasterInteractor.createSkillsProxy();

        mToneHelper = new ToneHelper(this);
        mContentProviderHelper = new ContentProviderHelper(this);
        mWakeupStateHelper = new WakeupStateHelper(mSensorManager, mContentProviderHelper);

        LocationHelper.getInstance().requestLocation();

        RxBus.getInstance().register(
                WakeupEvent.class,
                AndroidSchedulers.mainThread(),
                wakeupEvent -> handleWakeupEvent(wakeupEvent));

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            if (KEY_WAKEUP_INTENT_ACTION.equals(intent.getAction())) {
                WakeupEvent event =
                        WakeupEvent.newInstance()
                                .setType(WakeupEvent.TYPE_KEY);

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

        mWakeupStateHelper.addWakeupEvent(wakeupEvent);

        RobotSystemConfig robotSystemConfig = getRobotSystemConfig();

        // Return if it is power off mode
        if (robotSystemConfig.isPowerOffMode()) {
            LOGGER.i("Power off mode! Wakeup event is ignored.");
            return;
        }

        // Interrupt current robot background task
        if (wakeupEvent.isWakedByVoice() || wakeupEvent.isWakedByKey()) {

            LOGGER.i("Robot background task is interrupted.");
            stopRobotBackgroundTask();
        } else if (wakeupEvent.isWakedByHumanIN()) {

            // Ignore vision wakeup event in wakeup status.
            if (isRunning()) {
                LOGGER.i("Ignore vision wakeup event in wakeup status.");
                return;
            }
        } else if (wakeupEvent.isWakedByHumanOut()) {
            if (mWakeupStateHelper.isNobodyHere()) {
                stopRobotBackgroundTask();
            }
        }

        boolean isContinuousMode =
                robotSystemConfig
                        .getWakeupConfig()
                        .isVisualWakeUpEnabled();

        setRunning(true);

        Disposable disposable = stopTTS()
                .subscribeOn(Schedulers.single())
                .observeOn(Schedulers.single())
                .andThen(turnToCustomer(wakeupEvent, robotSystemConfig))
                .andThen(playWakeupNotification(wakeupEvent, robotSystemConfig))
                .andThen(startSpeechRecognization(isContinuousMode))
                .flatMap(recognizeResult -> dispatchEventLocally(recognizeResult))
                .doOnNext(recognizeResult -> sendToVoiceAssistant(recognizeResult))
                .flatMap(recognizeResult -> understand(recognizeResult))
                .flatMap(speechInteraction -> dispatchEventOnline(speechInteraction))
                .doOnError(throwable -> {

                    if (throwable instanceof AlertException) {

                        String msg = ((AlertException) throwable).getAlertMsg();

                        mCompositeDisposable.add(
                                talk(msg)
                                        .subscribeOn(Schedulers.single())
                                        .observeOn(Schedulers.single())
                                        .subscribe());
                    } else if (throwable instanceof AccessServiceException) {
                        mCompositeDisposable.add(
                                talk(getString(R.string.system_failure))
                                        .subscribeOn(Schedulers.single())
                                        .observeOn(Schedulers.single())
                                        .subscribe());
                    }
                })
                .subscribe(o -> {
                    LOGGER.i("Wakeup process started.");
                    LOGGER.e("Subscribe");

                }, throwable -> {
                    setRunning(false);
                    LOGGER.e("Wakeup process error.");
                    LOGGER.e((Throwable) throwable);
                }, () -> {
                    setRunning(false);
                    LOGGER.i("Wakeup process completed.");
                });

        mCompositeDisposable.add(disposable);
    }


    private void stopRobotBackgroundTask() {
        mIsRunning = false;
        mCompositeDisposable.clear();
    }

    private synchronized boolean isRunning() {
        return mIsRunning;
    }

    private synchronized void setRunning(boolean mIsRunning) {
        this.mIsRunning = mIsRunning;
    }


    /**
     * Get Robot System Configure from another apk.
     *
     * @return Current system configure
     */
    private RobotSystemConfig getRobotSystemConfig() {
        return mContentProviderHelper.getRobotSystemConfig();
    }

    private void setBeam0() {
        Map<String, String> map = new HashMap<>();
        map.put("angle", "0");
        mSensorManager.controlSensor(SOUND_DETECT, "setBeam", map)
                .done(aVoid -> LOGGER.i("setBeam0 success"))
                .fail(e -> {
                    LOGGER.e("setBeam0 failure");
                    LOGGER.e(e);
                });
    }

    private Completable turnToCustomer(WakeupEvent wakeupEvent,
                                       RobotSystemConfig robotSystemConfig) {

        if (!robotSystemConfig.getWakeupConfig().isRotateRobotEnabled()
                || !wakeupEvent.isWakedByVoice()) {

            return Completable.complete();
        }

        return getYawAngle()
                .flatMapCompletable(angle -> {

                    // The real angle is scaled at a delta ratio to the tech manuals.
                    float delta = 55f / 155f;
                    float angleYawTurn = angle - 180;
                    float angleLocomoter = angleYawTurn * delta + wakeupEvent.getAngle();

                    long d0 = (long) (Math.abs(angleLocomoter / 70) * 1000);
                    long d1 = (long) (Math.abs(angleYawTurn / 70) * 1000);
                    long duration = Math.max(d0, d1);

                    LOGGER.i("TurnToCustomer, wakeup angle:" + wakeupEvent.getAngle());
                    LOGGER.i("TurnToCustomer, Yaw angle:" + angle);
                    LOGGER.i("TurnToCustomer, loco angle:" + angleLocomoter);

                    if (Math.abs(angleYawTurn) < WAKE_UP_TURN_RANGE
                            && Math.abs(angleLocomoter) < 30) {

                        return Completable.complete();
                    } else {

                        Completable cpYaw = RotateYaw(-angleYawTurn, duration);

                        Completable cpLocoMotor = RotateLocoMotor(angleLocomoter, duration);

                        return cpYaw.mergeWith(cpLocoMotor);

                    }
                })
                .doOnError(throwable -> LOGGER.e(throwable))
                .onErrorComplete();
    }

    private Completable playWakeupNotification(WakeupEvent wakeupEvent,
                                               RobotSystemConfig robotSystemConfig) {

        LOGGER.i("playWakeupNotification");

        setBeam0();

        WakeupRingConfig wakeupRingConfig = robotSystemConfig
                .getWakeupConfig()
                .getWakeupRingConfig(wakeupEvent.getType());

        switch (wakeupRingConfig.getWakeupRingType()) {
            case WAKEUP_RING_TYPE_TONE:
                return mToneHelper.play(wakeupRingConfig.getWakeupRingValue());
            case WAKEUP_RING_TYPE_SPEECH:
                return talk(wakeupRingConfig.getWakeupRingValue());
            case WAKEUP_RING_TYPE_NONE:
                return Completable.complete();
            default:
                return Completable.complete();
        }
    }

    private Observable<Recognizer.RecognizeResult> startSpeechRecognization(boolean isContinuousMode) {
        return Observable.create(emitter -> {
            RecognizeOption option = new RecognizeOption
                    .Builder(isContinuousMode ? RecognizeOption.MODE_CONTINUOUS : RecognizeOption.MODE_SINGLE)
                    .build();

            ProgressivePromise<Recognizer.RecognizeResult, RecognizeException, Recognizer.RecognizingProgress>
                    promise = mSpeechManager.recognize(option);

            LOGGER.i("StartSpeechRecognization");

            emitter.setDisposable(new Disposable() {
                @Override
                public void dispose() {
                    if (promise != null && !promise.isCanceled()) {
                        promise.cancel();
                    }
                }

                @Override
                public boolean isDisposed() {
                    return promise.isCanceled();
                }
            });

            promise.done(recognizeResult -> {
                LOGGER.i("recognize onDone: " + recognizeResult.getText());

                if (!emitter.isDisposed()) {
                    if (!isContinuousMode) {
                        if (TextUtils.isEmpty(recognizeResult.getText())) {
                            emitter.onError(AlertException.newInstance(
                                    getString(R.string.i_do_not_know_what_you_are_talking_about)));
                        } else {
                            emitter.onNext(recognizeResult);

                        }
                    }
                    emitter.onComplete();
                }
            }).progress(recognizingProgress -> {

                if (recognizingProgress.getState() == Recognizer.RecognizingProgress.STATE_RESULT) {
                    LOGGER.i("recognize onProgress: " + recognizingProgress.getResult().getText());

                    if (isContinuousMode) {
                        emitter.onNext(recognizingProgress.getResult());
                    }
                }
            }).fail(throwable -> {
                if (!emitter.isDisposed()) {
                    emitter.onError(throwable);
                }
            });

        });

    }

    private Observable<Float> getYawAngle() {

        return new Observable<Float>() {

            @Override
            protected void subscribeActual(Observer<? super Float> observer) {
                Promise<Float, AccessServiceException> promise
                        = mMotionManager.getJointAngle("HeadYaw");

                LOGGER.i("getYawAngle");

                observer.onSubscribe(new Disposable() {
                    @Override
                    public void dispose() {
                        if (promise != null && !promise.isCanceled()) {
                            promise.cancel();
                        }
                    }

                    @Override
                    public boolean isDisposed() {
                        return promise.isCanceled();
                    }
                });

                promise.done(f -> {
                    observer.onNext(f);
                    observer.onComplete();
                }).fail(throwable -> observer.onError(throwable));
            }
        };
    }

    private Completable RotateLocoMotor(Float angle, long duration) {

        return Completable.create(emitter -> {
            ProgressivePromise<Void, LocomotionException, LocomotionProgress>
                    promise = mMotionManager.turnBy(angle, duration);

            LOGGER.i("Start rotate locoMotor");

            promise.done((DoneCallback) o -> {
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            }).fail(t -> {
                if (!emitter.isDisposed()) {
                    emitter.onError((Throwable) t);
                }
            });

            emitter.setDisposable(new Disposable() {
                @Override
                public void dispose() {
                    if (promise != null && !promise.isCanceled()) {
                        promise.cancel();
                    }
                }

                @Override
                public boolean isDisposed() {
                    return promise.isCanceled();
                }
            });
        });
    }

    private Completable RotateYaw(Float angle, long duration) {

        return Completable.create(emitter -> {
            LOGGER.i("Start rotate Yaw");

            ProgressivePromise<Void, JointException, JointRotatingProgress> promise
                    = mMotionManager.jointRotateBy(ROBOT_YAW_ID, angle, duration);

            promise.done((DoneCallback) o -> {
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            }).fail(t -> {
                if (!emitter.isDisposed()) {
                    emitter.onError((Throwable) t);
                }
            });
            emitter.setDisposable(new Disposable() {
                @Override
                public void dispose() {
                    if (promise != null && !promise.isCanceled()) {
                        promise.cancel();
                    }
                }

                @Override
                public boolean isDisposed() {
                    return promise.isCanceled();
                }
            });
        });

    }

    private void sendToVoiceAssistant(Recognizer.RecognizeResult recognizeResult) {

        LOGGER.i("Send RecognizeResult To VoiceAssistant");

        // The stream continues no mather whether calling voice assistant succeed or not.
        mSkillsProxy.call(CHAT_SKILL_RECOGNIZE,
                ParcelableParam.create(recognizeResult),
                new ResponseCallback() {
                    @Override
                    public void onResponse(Request request, Response response) {
                        LOGGER.i("Send RecognizeResult To VoiceAssistant: succeed.");
                    }

                    @Override
                    public void onFailure(Request request, CallException e) {
                        LOGGER.e("Send RecognizeResult To VoiceAssistant: failed.");
                        LOGGER.e(e);
                    }
                });
    }

    private Observable<Recognizer.RecognizeResult> dispatchEventLocally(
            final Recognizer.RecognizeResult recognizeResult) {

        LOGGER.i("dispatchEventLocally: " + recognizeResult.getText());

        SkillIntent skillIntent = new SkillIntent(SkillIntent.CATEGORY_SPEECH);
        skillIntent.setSpeechUtterance(recognizeResult.getText());

        return Observable.create(emitter -> {

            try {
                Response response = mSkillsProxy.call(skillIntent);
                LOGGER.i("Calling skill succeeded." + response.toString());
                emitter.onComplete();
            } catch (CallException e) {

                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    emitter.onError(e);
                } else {
                    LOGGER.i("DispatchEventLocally: Calling skill failed.");

                    emitter.onNext(recognizeResult);
                    emitter.onComplete();
                }
            }
        });
    }

    private Observable<SpeechInteraction> understand(
            final Recognizer.RecognizeResult recognizeResult) {

        LOGGER.i("recognizeResult.getText():" + recognizeResult.getText());

        UnderstandOption.Builder builderOpt = new UnderstandOption.Builder();
        builderOpt.appendStringParam("city", LocationHelper.getInstance().getLocation());

        return new ObservableFromPromise<>(
                mSpeechManager
                        .understand(recognizeResult.getText(), builderOpt.build()))
                .map(understandResult -> {
                    SpeechInteraction.Builder builder = new SpeechInteraction.Builder();
                    builder.setRecognizeResult(recognizeResult);
                    builder.setUnderstandResult(understandResult);
                    return builder.build();
                });
    }

    private Observable dispatchEventOnline(SpeechInteraction speechInteraction) {

        return Observable.create((ObservableOnSubscribe<UnderstandResult>) emitter -> {

            String action = speechInteraction.getUnderstandResult().getIntent().getName();

            LOGGER.i("UnderstandResult:" + action);

            try {

                Response response = mSkillsProxy.call(action,
                        ParcelableParam.create(speechInteraction));

                LOGGER.i("Calling skill succeeded." + response.toString());
                emitter.onComplete();
            } catch (CallException e) {

                LOGGER.i("DispatchEventOnline: Calling skill failed.");
                LOGGER.e(e);

                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    emitter.onError(e);
                } else {
                    Response response = mSkillsProxy.call(CHAT_SKILL_UNDERSTAND,
                            ParcelableParam.create(speechInteraction));

                    LOGGER.i("Calling chat skill succeeded." + response.toString());
                    emitter.onComplete();
                }
            }
        });
    }

    private Completable talk(String msg) {

        if (!TextUtils.isEmpty(msg)) {
            LOGGER.i("Talk: " + msg);
            return new ObservableFromProgressivePromise(mSpeechManager.synthesize(msg))
                    .ignoreElements()
                    .doOnError(throwable -> LOGGER.e(throwable))
                    .onErrorComplete();
        } else {
            return Completable.complete();
        }
    }


    private Completable stopTTS() {

        return new ObservableFromProgressivePromise(mSpeechManager.synthesize(" "))
                .ignoreElements()
                .onErrorComplete();
    }

    @Override
    public void onDestroy() {

        stopRobotBackgroundTask();
        mToneHelper.release();
        mContentProviderHelper.release();
        mWakeupStateHelper.release();

        super.onDestroy();
    }
}
