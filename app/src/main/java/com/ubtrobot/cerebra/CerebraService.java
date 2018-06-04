package com.ubtrobot.cerebra;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.ubtrobot.Robot;
import com.ubtrobot.async.rx.ObservableFromProgressivePromise;
import com.ubtrobot.async.rx.ObservableFromPromise;
import com.ubtrobot.cerebra.model.RobotSystemConfig;
import com.ubtrobot.cerebra.utils.ContentProviderHelper;
import com.ubtrobot.cerebra.utils.ToneHelper;
import com.ubtrobot.exception.AccessServiceException;
import com.ubtrobot.master.competition.CompetitionSession;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.master.interactor.MasterInteractor;
import com.ubtrobot.master.skill.SkillIntent;
import com.ubtrobot.master.skill.SkillsProxy;
import com.ubtrobot.master.transport.message.CallGlobalCode;
import com.ubtrobot.master.transport.message.parcel.ParcelableParam;
import com.ubtrobot.motion.MotionManager;
import com.ubtrobot.speech.RecognizeException;
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
import com.ubtrobot.wakeup.WakeupEvent;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.ubtrobot.cerebra.model.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_NONE;
import static com.ubtrobot.cerebra.model.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_SPEECH;
import static com.ubtrobot.cerebra.model.RobotSystemConfig.WakeupConfig.WakeupRingConfig.WAKEUP_RING_TYPE_TONE;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_SIMULATE;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_VISION;
import static com.ubtrobot.wakeup.WakeupEvent.TYPE_VOICE;


/**
 * Cerebra service, the main service of this application.
 */
public class CerebraService extends Service {

    public static final String WAKEUP_EVENT = "WAKEUP_EVENT";

    private static final String CHAT_SKILL_UNDERSTAND = "/chat/result/understand";
    private static final String CHAT_SKILL_RECOGNIZE = "/chat/result/recognize";

    private static final String MASTER_INTERACTOR = "MasterInteractor";

    private static final String ROBOT_YAW_ID = "HeadYaw";

    private static final Logger LOGGER = ULog.getLogger("CerebraService");

    private MasterContext mMasterContext;
    private SpeechManager mSpeechManager;
    private MotionManager mMotionManager;
    private MasterInteractor mMasterInteractor;
    private SkillsProxy mSkillsProxy;
    private CompetitionSession mSession;

    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    private ToneHelper mToneHelper;
    private ContentProviderHelper mContentProviderHelper;

    @Override
    public void onCreate() {
        super.onCreate();

        mMasterContext = Robot.master().getGlobalContext();
        mSpeechManager = new SpeechManager(mMasterContext);
        mMotionManager = new MotionManager(mMasterContext);
        mMasterInteractor = Robot.master().getOrCreateInteractor(MASTER_INTERACTOR);
        mSkillsProxy = mMasterInteractor.createSkillsProxy();

        mToneHelper = new ToneHelper(this);
        mContentProviderHelper = new ContentProviderHelper(this);
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
        return mContentProviderHelper.getRobotSystemConfig();
    }

    private void turnToCustomer(WakeupEvent wakeupEvent,
                                RobotSystemConfig robotSystemConfig) {

        if (!robotSystemConfig.getWakeupConfig().isRotateRobotOn()
                || wakeupEvent.getType() != TYPE_VOICE) {
            return;
        }

        Disposable disposable = new ObservableFromPromise<>(mMotionManager.getJointAngle("HeadYaw"))
                .subscribe(angle -> {

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


                    Disposable disposableYaw = new ObservableFromProgressivePromise<>(
                            mMotionManager.jointRotateBy(ROBOT_YAW_ID, (-angleYawTurn), duration))
                            .subscribeOn(Schedulers.single())
                            .observeOn(Schedulers.single())
                            .subscribe(o -> {
                                    }
                                    , throwable -> LOGGER.e(throwable));

                    mCompositeDisposable.add(disposableYaw);

                    Disposable disposableMotion = new ObservableFromPromise<>(
                            mMotionManager.turnBy(angleLocomoter, duration))
                            .subscribeOn(Schedulers.single())
                            .observeOn(Schedulers.single())
                            .subscribe(o -> {
                                    }
                                    , throwable -> LOGGER.e(throwable));

                    mCompositeDisposable.add(disposableMotion);

                }, throwable -> LOGGER.e(throwable));

        mCompositeDisposable.add(disposable);

    }

    private Observable palyWakeupNotification(WakeupEvent wakeupEvent,
                                              RobotSystemConfig robotSystemConfig) {
        RobotSystemConfig.WakeupConfig.WakeupRingConfig wakeupRingConfig =
                robotSystemConfig.getWakeupConfig().getWakeupRingConfig(wakeupEvent.getType());

        turnToCustomer(wakeupEvent, robotSystemConfig);

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
            if (!mCompositeDisposable.isDisposed()) {
                LOGGER.i("Ignore vision wakeup event in wakeup status.");
                return;
            }
        }

        Disposable disposable = stopTTs()
                .flatMap(o -> palyWakeupNotification(wakeupEvent, robotSystemConfig))
                .subscribeOn(Schedulers.single())
                .observeOn(Schedulers.single())
                .ignoreElements()
                .andThen(new ObservableFromProgressivePromise<>(mSpeechManager.recognize()))
                .filter(progressOrDone -> progressOrDone.isDone())
                .flatMap(progressOrDone -> dispatchEventLocally(progressOrDone.getDone()))
                .doOnNext(recognizeResult -> sendRecognizeResultToVoiceAssistant(recognizeResult))
                .flatMap(recognizeResult -> understand(recognizeResult))
                .flatMap(speechInteraction -> dispatchEventOnline(speechInteraction))
                .subscribe(o -> {
                    LOGGER.i("Wakeup process started.");
                }, throwable -> {
                    if (throwable instanceof RecognizeException) {
                        mCompositeDisposable.add(
                                talk(getString(R.string.i_do_not_understand_what_you_are_talking_about))
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
                    LOGGER.e("Wakeup process error.");
                    LOGGER.e((Throwable) throwable);
                }, () -> LOGGER.i("Wakeup process completed."));

        mCompositeDisposable.add(disposable);
    }


    private void sendRecognizeResultToVoiceAssistant(Recognizer.RecognizeResult recognizeResult) {
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
                    emitter.onError(e);
                } else {
                    LOGGER.i("DispatchEventLocally: Calling skill failed.");

                    emitter.onNext(recognizeResult);
                    emitter.onComplete();
                }
            }
        });

        return observable;
    }

    private Observable<SpeechInteraction> understand(final Recognizer.RecognizeResult recognizeResult) {
        LOGGER.i("recognizeResult.getText():" + recognizeResult.getText());

        UnderstandOption.Builder builderOpt = new UnderstandOption.Builder();
        builderOpt.appendStringParam("city", "深圳市");

        return new ObservableFromPromise<>(mSpeechManager.understand(recognizeResult.getText(), builderOpt.build()))
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

                Response response = mSkillsProxy.call(action, ParcelableParam.create(speechInteraction));
                LOGGER.i("Calling skill succeeded." + response.toString());
                emitter.onComplete();
            } catch (CallException e) {

                LOGGER.i("DispatchEventOnline: Calling skill failed.");
                LOGGER.e(e);

                if (CallGlobalCode.NOT_FOUND != e.getCode()) {
                    emitter.onError(e);
                } else {
                    Response response = mSkillsProxy.call(CHAT_SKILL_UNDERSTAND, ParcelableParam.create(speechInteraction));
                    LOGGER.i("Calling chat skill succeeded." + response.toString());
                    emitter.onComplete();
                }
            }
        });
    }

    private Observable talk(String msg) {
        if (!TextUtils.isEmpty(msg)) {
            LOGGER.i("Talk: " + msg);
            return new ObservableFromProgressivePromise(mSpeechManager.synthesize(msg));
        } else {
            return Observable.empty();
        }
    }

    private Observable stopTTs() {
        return new ObservableFromProgressivePromise(mSpeechManager.synthesize(" "));
    }

    @Override
    public void onDestroy() {
        stopRobotBackgroundTask();
        mToneHelper.release();
        mContentProviderHelper.release();

        super.onDestroy();
    }
}
