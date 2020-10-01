package org.freedesktop.gstreamer.nnstreamer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.text.Html;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.freedesktop.gstreamer.GStreamer;
import org.freedesktop.gstreamer.GStreamerSurfaceView;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class NNStreamerActivity extends Activity implements
        SurfaceHolder.Callback,
        View.OnClickListener {
    private static final String TAG = "NNStreamer";
    private static final int PERMISSION_REQUEST_ALL = 3;
    private static final int PIPELINE_ID = 1;
    private static final String downloadPath = Environment.getExternalStorageDirectory().getPath() + "/nnstreamer/tflite_model";

    private native void nativeInit(int w, int h); /* Initialize native code, build pipeline, etc */
    private native void nativeFinalize(); /* Destroy pipeline and shutdown native code */
    private native void nativeStart(int id, int option); /* Start pipeline with id */
    private native void nativeStop();     /* Stop the pipeline */
    private native void nativePlay();     /* Set pipeline to PLAYING */
    private native void nativePause();    /* Set pipeline to PAUSED */
    private static native boolean nativeClassInit(); /* Initialize native class: cache Method IDs for callbacks */
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private native String nativeGetName(int id, int option);
    private native String nativeGetDescription(int id, int option);
    private native String nativeGetTest(int id, int option);
    private long native_custom_data;      /* Native code will use this to keep private data */

    private int pipelineId = 0;
    private CountDownTimer pipelineTimer = null;
    private boolean initialized = false;
    private boolean useFrontCamera = true;

    private DownloadModel downloadTask = null;
    private ArrayList<String> downloadList = new ArrayList<>();

    private TextView viewTitle;
    private TextView viewDesc;
    private ImageButton buttonCam;
    private ToggleButton buttonModel1;
    private ToggleButton buttonModel2;
    private ToggleButton buttonModel3;
    private ToggleButton buttonModel4;
    private TimerTask timerTask;
    private Timer timer = new Timer();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Check permissions */
        if (!checkPermission(Manifest.permission.CAMERA) ||
            !checkPermission(Manifest.permission.INTERNET) ||
            !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ||
            !checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
            !checkPermission(Manifest.permission.WAKE_LOCK)) {
            ActivityCompat.requestPermissions(this,
                new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.WAKE_LOCK
                }, PERMISSION_REQUEST_ALL);
            return;
        }

        initActivity();
        startTimerTask();
    }

    @Override
    public void onPause() {
        super.onPause();

        stopPipelineTimer();
        stopTimerTask();
        startTimerTask();
        nativePause();
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Start pipeline */
        if (initialized) {
            if (downloadTask != null && downloadTask.isProgress()) {
                Log.d(TAG, "Now downloading model files");
            } else {
                startPipeline(PIPELINE_ID);
            }
        }
    }

    @Override

    protected void onDestroy() {
        super.onDestroy();

        timer.cancel();
        stopPipelineTimer();
        stopTimerTask();
        nativeFinalize();
    }

    private void startTimerTask(){
	timerTask=new TimerTask(){
            public void run(){
                viewDesc.setText(nativeGetTest(1, (1 << 2)));
            }
	    };
	timer.schedule(timerTask, 0, 1000);
    }

    private void stopTimerTask(){
	if(timerTask != null){
	    timerTask.cancel();
	    timerTask=null;
	}
    }

    /**
     * This shows the toast from the UI thread.
     * Called from native code.
     */
    private void setMessage(final String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                showToast(message);
            }
        });
    }

    /**
     * Native code calls this once it has created its pipeline and
     * the main loop is running, so it is ready to accept commands.
     * Called from native code.
     */
    private void onGStreamerInitialized(final String title, final String desc) {
        /* GStreamer is initialized and ready to play pipeline. */
        runOnUiThread(new Runnable() {
            public void run() {
                /* Update pipeline title and description here */
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException " + e.getMessage());
                }

                nativePlay();

                /* Update UI (buttons and other components) */
                enableButton(true);
            }
        });
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("nnstreamer-jni");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        /* SurfaceHolder.Callback interface implementation */
        Log.d(TAG, "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit(holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        /* SurfaceHolder.Callback interface implementation */
        Log.d(TAG, "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        /* SurfaceHolder.Callback interface implementation */
        Log.d(TAG, "Surface destroyed");
        nativeSurfaceFinalize();
    }

    @Override
    public void onClick(View v) {
        /* View.OnClickListener interface implementation */
        final int viewId = v.getId();

        if (pipelineTimer != null) {
            /* Do nothing, new pipeline will be started soon. */
            return;
        }

        switch (viewId) {
        case R.id.main_button_cam:
            useFrontCamera = !useFrontCamera;
            /* fallthrough */
        case R.id.main_button_m1:
        case R.id.main_button_m2:
        case R.id.main_button_m3:
        case R.id.main_button_m4:
            stopTimerTask();
            startTimerTask();
            startPipeline(PIPELINE_ID);
            break;
        default:
            break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_ALL) {
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission denied, close app.");
                    finish();
                    return;
                }
            }

            initActivity();
            return;
        }

        finish();
    }

    /**
     * Check the given permission is granted.
     */
    private boolean checkPermission(final String permission) {
        return (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * 주어진 메시지로 토스트를 만드세요.
     */
    private void showToast(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * GStreamer와 레이아웃을 초기화하십시오.
     */
    private void initActivity() {
        if (initialized) {
            return;
        }

        /* Initialize GStreamer and warn if it fails */
        try {
            GStreamer.init(this);
        } catch(Exception e) {
            showToast(e.getMessage());
            finish();
            return;
        }

        /* Initialize with media resolution. */
        nativeInit(GStreamerSurfaceView.media_width, GStreamerSurfaceView.media_height);

        setContentView(R.layout.main);

        viewTitle = (TextView) findViewById(R.id.main_text_title);
        viewDesc = (TextView) findViewById(R.id.main_text_desc);

        buttonCam = (ImageButton) findViewById(R.id.main_button_cam);
        buttonCam.setOnClickListener(this);

        /* Add event listener for models */
        String model1 = nativeGetName(1, (1 << 1));
        model1 = "얼굴 탐지";
        buttonModel1 = (ToggleButton) findViewById(R.id.main_button_m1);
        buttonModel1.setOnClickListener(this);
        buttonModel1.setText(model1);
        buttonModel1.setTextOn(model1);
        buttonModel1.setTextOff(model1);

        String model2 = nativeGetName(1, (1 << 2));
        model2 = "손 움직임 탐지";
        buttonModel2 = (ToggleButton) findViewById(R.id.main_button_m2);
        buttonModel2.setOnClickListener(this);
        buttonModel2.setText(model2);
        buttonModel2.setTextOn(model2);
        buttonModel2.setTextOff(model2);

        String model3 = nativeGetName(1, (1 << 3));
        model3 = "객체 탐지";
        buttonModel3 = (ToggleButton) findViewById(R.id.main_button_m3);
        buttonModel3.setOnClickListener(this);
        buttonModel3.setText(model3);
        buttonModel3.setTextOn(model3);
        buttonModel3.setTextOff(model3);

        String model4 = nativeGetName(1, (1 << 4));
        model4 = "포즈(움직임) 탐지";
        buttonModel4 = (ToggleButton) findViewById(R.id.main_button_m4);
        buttonModel4.setOnClickListener(this);
        buttonModel4.setText(model4);
        buttonModel4.setTextOn(model4);
        buttonModel4.setTextOff(model4);

        /* Video surface for camera */
        SurfaceView sv = (SurfaceView) this.findViewById(R.id.main_surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        /* Start with disabled buttons, until the pipeline in native code is initialized. */
        enableButton(false);

        initialized = true;
    }

    /**
     * 모델을 시작하려면 버튼을 활성화(또는 비활성화)하십시오.
     */
    public void enableButton(boolean enabled) {
        buttonCam.setEnabled(enabled);
        buttonModel1.setEnabled(enabled);
        buttonModel2.setEnabled(enabled);
        buttonModel3.setEnabled(enabled);
        buttonModel4.setEnabled(enabled);
    }

    /**
     * 파이프라인 시작 및 UI 업데이트.
     */
    private void startPipeline(int newId) {
        pipelineId = newId;
        enableButton(false);

        /* Pause current pipeline and start new pipeline */
        nativePause();

        if (checkModels()) {
            setPipelineTimer();
        } else {
            showDownloadDialog();
        }
    }

    /**
     * 파이프라인 타이머를 취소하십시오.
     */
    private void stopPipelineTimer() {
        if (pipelineTimer != null) {
            pipelineTimer.cancel();
            pipelineTimer = null;
        }
    }

    /**
     * 새 파이프라인을 시작하도록 타이머를 설정하십시오.
     */
    private void setPipelineTimer() {
        final long time = 200;

        stopPipelineTimer();
        pipelineTimer = new CountDownTimer(time, time) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                int option = 0;

                pipelineTimer = null;
                if (pipelineId == PIPELINE_ID) {
                    /* Set pipeline option here */
                    if (buttonModel1.isChecked()) option |= (1 << 1);
                    if (buttonModel2.isChecked()) option |= (1 << 2);
                    if (buttonModel3.isChecked()) option |= (1 << 3);
                    if (buttonModel4.isChecked()) option |= (1 << 4);
                    if (useFrontCamera) option |= (1 << 8);
                }

                nativeStart(pipelineId, option);
            }
        }.start();
    }

    /**
     * 모델 파일이 특정 디렉터리에 있는지 확인하십시오.
     */
    private boolean checkModelFile(String fileName) {
        File modelFile;

        modelFile = new File(downloadPath, fileName);
        if (!modelFile.exists()) {
            Log.d(TAG, "Cannot find model file " + fileName);
            downloadList.add(fileName);
            return false;
        }

        return true;
    }

    /**
     * 모델 파일 다운로드를 시작하십시오.
     */
    private void downloadModels() {
        downloadTask = new DownloadModel(this, downloadPath);
        downloadTask.execute(downloadList);
    }

    /**
     * 특정 디렉터리에 필요한 모든 파일이 있는지 확인하십시오.
     */
    private boolean checkModels() {
        downloadList.clear();

        checkModelFile("box_priors.txt");
        checkModelFile("ssd_mobilenet_v2_coco.tflite");
        checkModelFile("coco_labels_list.txt");
        checkModelFile("detect_face.tflite");
        checkModelFile("labels_face.txt");
        checkModelFile("detect_hand.tflite");
        checkModelFile("labels_hand.txt");
        checkModelFile("detect_pose.tflite");

        return !(downloadList.size() > 0);
    }

    /**
     * 모델 파일을 다운로드하는 대화 상자 표시
     */
    private void showDownloadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setCancelable(false);
        builder.setMessage(R.string.download_model_file);

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        builder.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                downloadModels();
            }
        });

        builder.show();
    }
}
