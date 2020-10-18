package org.freedesktop.gstreamer.nnstreamer;

import android.app.ActivityManager;
import android.app.Notification;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.net.Uri;
import android.Manifest.permission;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.sac.speech.GoogleVoiceTypingDisabledException;
import com.sac.speech.Speech;
import com.sac.speech.SpeechDelegate;
import com.sac.speech.SpeechRecognitionNotAvailable;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import rx.functions.Action1;

/* NNStreamer Voice Recognition Service */
public class NNStreamerService extends Service implements SpeechDelegate, Speech.stopDueToDelay {

    public static SpeechDelegate delegate;

    private String up, down, left, right;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1,new Notification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(getApplicationContext(), "NNStreamer 음성 서비스 모드 실행되었습니다.", Toast.LENGTH_SHORT).show();

        //TODO do something useful
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                ((AudioManager) Objects.requireNonNull(
                        getSystemService(Context.AUDIO_SERVICE))).setStreamMute(AudioManager.STREAM_SYSTEM, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Speech.init(this);
        delegate = this;
        Speech.getInstance().setListener(this);

        if (Speech.getInstance().isListening()) {
            Speech.getInstance().stopListening();
            muteBeepSoundOfRecorder();
        } else {
            System.setProperty("rx.unsafe-disable", "True");
            RxPermissions.getInstance(this).request(permission.RECORD_AUDIO).subscribe(new Action1<Boolean>() {
                @Override
                public void call(Boolean granted) {
                    if (granted) { // Always true pre-M
                        try {
                            Speech.getInstance().stopTextToSpeech();
                            Speech.getInstance().startListening(null, NNStreamerService.this);
                        } catch (SpeechRecognitionNotAvailable exc) {
                            //showSpeechNotSupportedDialog();

                        } catch (GoogleVoiceTypingDisabledException exc) {
                            //showEnableGoogleVoiceTyping();
                        }
                    } else {
                        Toast.makeText(NNStreamerService.this, R.string.permission_required, Toast.LENGTH_LONG).show();
                    }
                }
            });
            muteBeepSoundOfRecorder();
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        //TODO for communication return IBinder implementation
        return null;
    }

    @Override
    public void onStartOfSpeech() {
    }

    @Override
    public void onSpeechRmsChanged(float value) {

    }

    @Override
    public void onSpeechPartialResults(List<String> results) {
        for (String partial : results) {
            Log.d("Result", partial+"");
        }
    }

    @Override
    public void onSpeechResult(String result) {
        Log.d("Result", result+"");
        if (!TextUtils.isEmpty(result)) {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> info = manager.getRunningTasks(1);
            ComponentName componentName= info.get(0).topActivity;
            String ActivityName = componentName.getShortClassName().substring(1);

            if (ActivityName.equals("rg.freedesktop.gstreamer.nnstreamer.NNStreamerActivity")) {
                /* CardList setting */
                List CardList = new ArrayList();

                CardList.add("none");
                CardList.add("com.sec.android.app.camera");
                CardList.add("com.sec.android.gallery3d");
                CardList.add("com.samsung.android.messaging");
                CardList.add("com.android.vending");
                CardList.add("com.android.settings");
                CardList.add("com.kakao.talk");
                CardList.add("com.nhn.android.search");
                CardList.add("com.google.android.youtube");
                CardList.add("com.iloen.melon");
                CardList.add("com.facebook.katana");
                CardList.add("com.google.android.gm");

                SharedPreferences sf = getSharedPreferences("sFile",MODE_PRIVATE);
                up = CardList.get(Integer.parseInt(sf.getString("up",""))).toString();
                down = CardList.get(Integer.parseInt(sf.getString("down",""))).toString();
                left = CardList.get(Integer.parseInt(sf.getString("left",""))).toString();
                right = CardList.get(Integer.parseInt(sf.getString("right",""))).toString();

                if (result.equals("위") && !up.equals("none")) {
                    String packageName = up;
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                    catch (Exception e) {
                        String url = "market://details?id=" + packageName;
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    }
                }
                else if (result.equals("아래") && !down.equals("none")) {
                    String packageName = down;
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                    catch (Exception e) {
                        String url = "market://details?id=" + packageName;
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    }
                }
                else if (result.equals("왼쪽") && !left.equals("none")) {
                    String packageName = left;
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                    catch (Exception e) {
                        String url = "market://details?id=" + packageName;
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    }
                }
                else if (result.equals("오른쪽") && !right.equals("none")) {
                    String packageName = right;
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                    catch (Exception e) {
                        String url = "market://details?id=" + packageName;
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i);
                    }
                }
                else if (result.equals("원본")) {
                    String packageName = "org.freedesktop.gstreamer.nnstreamer.multi";
                    String url = "market://details?id=" + packageName;
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(i);
                }
                else if (result.equals("종료")) {
                    System.exit(0);
                }
                else {
                    Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onSpecifiedCommandPronounced(String event) {
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                ((AudioManager) Objects.requireNonNull(
                        getSystemService(Context.AUDIO_SERVICE))).setStreamMute(AudioManager.STREAM_SYSTEM, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Speech.getInstance().isListening()) {
            muteBeepSoundOfRecorder();
            Speech.getInstance().stopListening();
        } else {
            RxPermissions.getInstance(this).request(permission.RECORD_AUDIO).subscribe(new Action1<Boolean>() {
                @Override
                public void call(Boolean granted) {
                    if (granted) { // Always true pre-M
                        try {
                            Speech.getInstance().stopTextToSpeech();
                            Speech.getInstance().startListening(null, NNStreamerService.this);
                        } catch (SpeechRecognitionNotAvailable exc) {
                            //showSpeechNotSupportedDialog();

                        } catch (GoogleVoiceTypingDisabledException exc) {
                            //showEnableGoogleVoiceTyping();
                        }
                    } else {
                        Toast.makeText(NNStreamerService.this, R.string.permission_required, Toast.LENGTH_LONG).show();
                    }
                }
            });
            muteBeepSoundOfRecorder();
        }
    }

    /**
     * Function to remove the beep sound of voice recognizer.
     */
    private void muteBeepSoundOfRecorder() {
        AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (amanager != null) {
            amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
            amanager.setStreamMute(AudioManager.STREAM_ALARM, true);
            amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            amanager.setStreamMute(AudioManager.STREAM_RING, true);
            amanager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        //Restarting the service if it is removed.
        PendingIntent service =
                PendingIntent.getService(getApplicationContext(), new Random().nextInt(),
                        new Intent(getApplicationContext(), NNStreamerService.class), PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        assert alarmManager != null;
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        // Service Destory
        super.onDestroy();
        stopForeground(true);
    }
}