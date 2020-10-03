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
import android.widget.RelativeLayout;
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

    private native void nativeInit(int w, int h); /* 네이티브 코드 초기화, 파이프라인 구축 등 */
    private native void nativeFinalize(); /* 파이프라인 파괴 및 기본 코드 종료 */
    private native void nativeStart(int id, int option); /* ID로 파이프라인 시작 */
    private native void nativeStop();     /* 파이프라인 중지 */
    private native void nativePlay();     /* 파이프라인을 PLAYING으로 설정 */
    private native void nativePause();    /* 파이프라인을 PASUED로 설정 */
    private static native boolean nativeClassInit(); /* 기본 클래스 초기화: 콜백에 대한 캐시 메서드 ID */
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private native String nativeGetName(int id, int option);
    private native String nativeGetDescription(int id, int option);
    private native String nativeGetTest(int id, int option);
    private long native_custom_data;      /* 기본 코드는 이를 사용하여 개인 데이터를 보관함 */

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

    private RelativeLayout main_surface_area;
    private int switch_cnt = 1;
    private int temp_cnt = 0;
    private int rectX = 0, rectY = 0, rectW = 0, rectH = 0;
    private int print_cnt = 0;
    private String resultText = "";
    private ArrayList<Integer> resultArrayX = new ArrayList();
    private ArrayList<Integer> resultArrayY = new ArrayList();

    /* 기본 세팅 (권한 설정, 타이머 시작) */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* 권한 확인 */
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

        /* 액티비티 설정, 타이머 시작 */
        initActivity();
        startTimerTask();
    }

    /* 잠시 멈췄을 때 사용하는 함수 */
    @Override
    public void onPause() {
        super.onPause();

        stopPipelineTimer();
        stopTimerTask();
        startTimerTask();
        nativePause();
    }

    /* 재개 할때 사용하는 함수 */
    @Override
    public void onResume() {
        super.onResume();

        /* 파이프라인 시작 */
        if (initialized) {
            if (downloadTask != null && downloadTask.isProgress()) {
                Log.d(TAG, "모델 파일 다운로드 시작");
            } else {
                startPipeline(PIPELINE_ID);
            }
        }
    }

    /* 종료할 때 사용하는 함수 */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        timer.cancel();
        stopPipelineTimer();
        stopTimerTask();
        nativeFinalize();
    }

    /* 타이머 시작, 제스처 판단 (왼쪽, 오른쪽, 위, 아래) */
    private void startTimerTask(){
	timerTask=new TimerTask(){
            public void run(){
                String temp = nativeGetTest(1, (1 << 2));
                String[] printText = temp.split(" ");
                rectX = Integer.parseInt(printText[0]);
                rectY = Integer.parseInt(printText[1]);
                rectW = Integer.parseInt(printText[2]);
                rectH = Integer.parseInt(printText[3]);

                if (rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0 && print_cnt == 0 && temp_cnt < 10) {
                    temp_cnt += 1;
                }
                else if (rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0 && print_cnt == 0) {
                    print_cnt = 1;
                    resultText = "너무 빨리 움직이거나 설정이 안되어있는 상태입니다";
                    if (resultArrayX.size() > 100) {
                        if (Math.abs(240 - resultArrayX.get(resultArrayX.size() - 1)) > Math.abs(240 - resultArrayY.get(resultArrayY.size() - 1))) {
                            if (resultArrayX.get(0) > resultArrayX.get(resultArrayX.size() - 1)) {
                                resultText = "Left : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                            }
                            else {
                                resultText = "Right : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                            }
                        }
                        else {
                            if (resultArrayY.get(0) > resultArrayY.get(resultArrayX.size() - 1)) {
                                resultText = "Up : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                            }
                            else {
                                resultText = "Down : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                            }
                        }
                    }
                }
                else if ( !(rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0) && print_cnt == 1) {
                    int tempX = rectX + (int)(rectW/2);
                    int tempY = rectY + (int)(rectH/2);

                    resultText = "손을 가운데로 가져다 주세요";

                    if (Math.abs(240 - tempX) < 50 && Math.abs((240 - tempY)) < 50) {
                        resultText = "손을 상, 하, 좌, 우로 움직여주세요";
                        print_cnt = 0;
                        temp_cnt = 0;

                        resultArrayX.clear();
                        resultArrayY.clear();

                        resultArrayX.add(rectX + (int)(rectW/2));
                        resultArrayY.add(rectY + (int)(rectH/2));
                    }
                }
                else if ( !(rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0) && print_cnt == 0) {
                    resultArrayX.add(rectX + (int)(rectW/2));
                    resultArrayY.add(rectY + (int)(rectH/2));
                }

                viewDesc.setText(resultText);
            }
	    };
	timer.schedule(timerTask, 0, 5);
    }

    /* 타이머 종료 */
    private void stopTimerTask(){
	if(timerTask != null){
	    timerTask.cancel();
	    timerTask=null;
	}
    }

    /**
     * 이것은 UI 스레드에서 나온 토스트를 보여준다.
     * 네이티브 코드에서 호출됨.
     */
    private void setMessage(final String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                showToast(message);
            }
        });
    }

    /**
     * 파이프라인을 생성하면 네이티브 코드가 호출하며
     * 메인 루프가 실행 중이므로 명령을 수신할 준비가 되어 있다.
     * 네이티브 코드에서 호출됨.
     */
    private void onGStreamerInitialized(final String title, final String desc) {
        /* GStreamer가 초기화되었으며 파이프라인을 재생할 준비가 되었다. */
        runOnUiThread(new Runnable() {
            public void run() {
                /* 여기서 파이프라인 제목 및 설명 업데이트 */
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "중단되는 에러 : " + e.getMessage());
                }

                nativePlay();

                /* UI 업데이트 (버튼 및 기타 구성 요소) */
                enableButton(true);
            }
        });
    }

    /* 라이브러리를 로드한다 */
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("nnstreamer-jni");
        nativeClassInit();
    }

    /* SurfaceHolder.콜백 인터페이스 구현 */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Log.d(TAG, "Surface 포맷 변경 " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit(holder.getSurface());
    }

    /* SurfaceHolder.콜백 인터페이스 구현 */
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "Surface 생성: " + holder.getSurface());
    }

    /* SurfaceHolder.콜백 인터페이스 구현 */
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "Surface 파괴");
        nativeSurfaceFinalize();
    }

    /* 버튼 클릭시 나오는 이벤트 */
    @Override
    public void onClick(View v) {
        /* View.OnClickListener 인터페이스 구현 */
        final int viewId = v.getId();

        if (pipelineTimer != null) {
            /* 새 파이프라인이 가동됨 */
            return;
        }

        switch (viewId) {
            case R.id.main_button_m4:
                if (switch_cnt == 0) {
                    switch_cnt = 1;
                    main_surface_area.setVisibility(View.VISIBLE);
                }
                else {
                    switch_cnt = 0;
                    main_surface_area.setVisibility(View.GONE);
                }
                break;
            case R.id.main_button_cam:
                useFrontCamera = !useFrontCamera;
                /* 중단 */
            case R.id.main_button_m1:
            case R.id.main_button_m2:
            case R.id.main_button_m3:
                stopTimerTask();
                startTimerTask();
                startPipeline(PIPELINE_ID);
                break;
            default:
                break;
        }
    }

    /* 권한 허용 함수 */
    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_ALL) {
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "권한 거부");
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
     * 지정된 권한이 부여되었는지 확인하십시오.
     */
    private boolean checkPermission(final String permission) {
        return (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * 토스트를 (메시지를) 띄워주는 함수
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

        /* GStreamer 초기화 및 실패 시 경고 */
        try {
            GStreamer.init(this);
        } catch(Exception e) {
            showToast(e.getMessage());
            finish();
            return;
        }

        /* 미디어 해상도를 사용하여 초기화하십시오. */
        nativeInit(GStreamerSurfaceView.media_width, GStreamerSurfaceView.media_height);

        setContentView(R.layout.main);

        viewTitle = (TextView) findViewById(R.id.main_text_title);
        viewDesc = (TextView) findViewById(R.id.main_text_desc);

        buttonCam = (ImageButton) findViewById(R.id.main_button_cam);
        buttonCam.setOnClickListener(this);

        /* 모델에 대한 이벤트 수신기 추가 */
        String model1 = nativeGetName(1, (1 << 1));
        model1 = "얼굴 탐지";
        buttonModel1 = (ToggleButton) findViewById(R.id.main_button_m1);
        buttonModel1.setOnClickListener(this);
        buttonModel1.setText(model1);
        buttonModel1.setTextOn(model1);
        buttonModel1.setTextOff(model1);

        String model2 = "손 움직임 탐지";
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

        String model4 = "카메라 숨기기 / 보이기";
        buttonModel4 = (ToggleButton) findViewById(R.id.main_button_m4);
        buttonModel4.setOnClickListener(this);
        buttonModel4.setText(model4);
        buttonModel4.setTextOn(model4);
        buttonModel4.setTextOff(model4);

        main_surface_area = (RelativeLayout) findViewById(R.id.main_surface_area);
        main_surface_area.setVisibility(View.VISIBLE);

        /* surface 카메라 */
        SurfaceView sv = (SurfaceView) this.findViewById(R.id.main_surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        /* 기본 코드의 파이프라인이 초기화될 때까지 비활성화된 버튼부터 시작하십시오. */
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

        /* 현재 파이프라인을 일시 중지하고 새 파이프라인 시작 */
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
                    /* 여기서 파이프라인 옵션 설정 */
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
            Log.d(TAG, "모델 파일 : " + fileName + " 을 찾을 수 없습니다.");
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
