package org.freedesktop.gstreamer.nnstreamer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.widget.CardView;
import android.text.Html;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.freedesktop.gstreamer.GStreamer;
import org.freedesktop.gstreamer.GStreamerSurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
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

    private TextView viewDesc;
    private ImageButton buttonCam;
    private ToggleButton buttonModel1;
    private ToggleButton buttonModel2;
    private ToggleButton buttonModel3;
    private ToggleButton buttonModel4;
    private Button buttonModel5;
    private TimerTask timerTask;
    private Timer timer = new Timer();

    private final long FINISH_INTERVAL_TIME = 2000;
    private long backPressedTime = 0;
    static final int REQUEST_SELECT_CONTACT = 1;

    private RelativeLayout main_surface_area, main_root;
    private int temp_cnt = 0;
    private int rectX = 0, rectY = 0, rectW = 0, rectH = 0;
    private int print_cnt = 0, schedule_cnt = 0;
    private String resultText = "";
    private ArrayList<Integer> resultArrayX = new ArrayList();
    private ArrayList<Integer> resultArrayY = new ArrayList();

    private String strUP = "", strDOWN = "", strLEFT = "", strRIGHT = "";
    private SurfaceView sv;

    private List CardList;
    private LinearLayout sub_pipeline_area, main_pipeline_area, plus_pipeline_area;
    private Spinner up_spinner, down_spinner, left_spinner, right_spinner;
    private int layout_cnt = 1;

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

        if (schedule_cnt == 0) {
            stopPipelineTimer();
            stopTimerTask();
            startTimerTask();
            nativePause();
        }
    }

    /* 재개 할때 사용하는 함수 */
    @Override
    public void onResume() {
        super.onResume();

        if (schedule_cnt == 0) {
            /* 파이프라인 시작 */
            if (initialized) {
                if (downloadTask != null && downloadTask.isProgress()) {
                    Log.d(TAG, "모델 파일 다운로드 시작");
                } else {
                    startPipeline(PIPELINE_ID);
                }
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

        SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
        editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
        editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
        editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

        editor.commit();
    }

    /* 두 번 이전 버튼 누를시 완전 종료 */
    @Override
    public void onBackPressed() {
        long tempTime = System.currentTimeMillis();
        long intervalTime = tempTime - backPressedTime;

        if (0 <= intervalTime && FINISH_INTERVAL_TIME >= intervalTime)
        {
            timer.cancel();
            stopPipelineTimer();
            stopTimerTask();
            nativeFinalize();

            SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
            editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
            editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
            editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

            editor.commit();

            finishAffinity();
            System.runFinalization();
            System.exit(0);
        }
        else
        {
            backPressedTime = tempTime;
            Toast.makeText(getApplicationContext(), " 한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
        }
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
                    schedule_cnt = 0;
                }
                else if (rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0 && print_cnt == 0) {
                    print_cnt = 1;
                    resultText = "너무 빨리 움직이거나 설정이 안되어있는 상태입니다";
                    if (resultArrayX.size() > 100) {
                        if (Math.abs(240 - resultArrayX.get(resultArrayX.size() - 1)) > Math.abs(240 - resultArrayY.get(resultArrayY.size() - 1))) {
                            if (resultArrayX.get(0) > resultArrayX.get(resultArrayX.size() - 1)) {
                                resultText = "Left : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                                schedule_cnt = 3;
                                viewDesc.setText(resultText);
                                appRunFunction(schedule_cnt);
                                return;
                            }
                            else {
                                resultText = "Right : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                                schedule_cnt = 4;
                                viewDesc.setText(resultText);
                                appRunFunction(schedule_cnt);
                                return;
                            }
                        }
                        else {
                            if (resultArrayY.get(0) > resultArrayY.get(resultArrayX.size() - 1)) {
                                resultText = "Up : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                                schedule_cnt = 1;
                                viewDesc.setText(resultText);
                                appRunFunction(schedule_cnt);
                                return;
                            }
                            else {
                                resultText = "Down : " + resultArrayX.get(0) + " " + resultArrayY.get(0) + " " +
                                        resultArrayX.get(resultArrayX.size() - 1) + " " + resultArrayY.get(resultArrayY.size() - 1) + "";
                                schedule_cnt = 2;
                                viewDesc.setText(resultText);
                                appRunFunction(schedule_cnt);
                                return;
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
                    schedule_cnt = 0;
                }
                else if ( !(rectX == 0 && rectY == 0 && rectW == 0 && rectH == 0) && print_cnt == 0) {
                    resultArrayX.add(rectX + (int)(rectW/2));
                    resultArrayY.add(rectY + (int)(rectH/2));
                    schedule_cnt = 0;
                }
                else {
                    schedule_cnt = 0;
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

    /* 제스처에 따라 어플리케이션 실행 */
    private void appRunFunction(int cnt) {
        if (layout_cnt == 1) {
            timer.cancel();
            stopTimerTask();

            timer = new Timer();
            startTimerTask();

            int mode = 0;
            if (schedule_cnt == 1) {
                mode = up_spinner.getSelectedItemPosition();
            }
            else if (schedule_cnt == 2) {
                mode = down_spinner.getSelectedItemPosition();
            }
            else if (schedule_cnt == 3) {
                mode = left_spinner.getSelectedItemPosition();
            }
            else if (schedule_cnt == 4) {
                mode = right_spinner.getSelectedItemPosition();
            }

            if (mode != 0) {
                String packageName = CardList.get(mode).toString();
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                catch (Exception e) {
                    String url = "market://details?id=" + packageName;
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                }
            }
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
            case R.id.main_button_m1:
                sub_pipeline_area.setVisibility(View.GONE);
                main_pipeline_area.setVisibility(View.GONE);
                plus_pipeline_area.setVisibility(View.VISIBLE);
                layout_cnt = 0;
                break;

            case R.id.main_button_m5:
                sub_pipeline_area.setVisibility(View.VISIBLE);
                main_pipeline_area.setVisibility(View.VISIBLE);
                plus_pipeline_area.setVisibility(View.GONE);
                layout_cnt = 1;
                break;

            case R.id.main_button_m3:
                up_spinner.setSelection(0);
                down_spinner.setSelection(0);
                left_spinner.setSelection(0);
                right_spinner.setSelection(0);
                Toast.makeText(getApplicationContext(), "세팅이 초기화 되었습니다.", Toast.LENGTH_SHORT).show();
                break;

            case R.id.main_button_m4:
                String packageName = "org.freedesktop.gstreamer.nnstreamer.multi";
                String url = "market://details?id=" + packageName;
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
                break;

            case R.id.main_button_cam:
                useFrontCamera = !useFrontCamera;

            case R.id.main_button_m2:
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

        viewDesc = (TextView) findViewById(R.id.main_text_desc);

        buttonCam = (ImageButton) findViewById(R.id.main_button_cam);
        buttonCam.setOnClickListener(this);

        /* 모델에 대한 이벤트 수신기 추가 */
        String model1 = "Hand Gesture 설정 (상, 하, 좌, 우)";
        buttonModel1 = (ToggleButton) findViewById(R.id.main_button_m1);
        buttonModel1.setOnClickListener(this);
        buttonModel1.setText(model1);
        buttonModel1.setTextOn(model1);
        buttonModel1.setTextOff(model1);

        String model2 = "Hand Detecting Switch (Default : On)";
        buttonModel2 = (ToggleButton) findViewById(R.id.main_button_m2);
        buttonModel2.setOnClickListener(this);
        buttonModel2.setText(model2);
        buttonModel2.setTextOn(model2);
        buttonModel2.setTextOff(model2);

        String model3 = "Hand Gesture 세팅 초기화";
        buttonModel3 = (ToggleButton) findViewById(R.id.main_button_m3);
        buttonModel3.setOnClickListener(this);
        buttonModel3.setText(model3);
        buttonModel3.setTextOn(model3);
        buttonModel3.setTextOff(model3);

        String model4 = "NNStreamer Original App Download";
        buttonModel4 = (ToggleButton) findViewById(R.id.main_button_m4);
        buttonModel4.setOnClickListener(this);
        buttonModel4.setText(model4);
        buttonModel4.setTextOn(model4);
        buttonModel4.setTextOff(model4);

        main_surface_area = (RelativeLayout) findViewById(R.id.main_surface_area);
        main_surface_area.setVisibility(View.VISIBLE);

        /* 기존 변수 저장, 카드 뷰 업데이트 */
        SharedPreferences sf = getSharedPreferences("sFile",MODE_PRIVATE);
        strUP = sf.getString("up","");
        strDOWN = sf.getString("down","");
        strLEFT = sf.getString("left","");
        strRIGHT = sf.getString("right","");

        sub_pipeline_area = (LinearLayout) findViewById(R.id.sub_pipeline_area);
        sub_pipeline_area.setVisibility(View.VISIBLE);

        main_pipeline_area = (LinearLayout) findViewById(R.id.main_pipeline_area);
        main_pipeline_area.setVisibility(View.VISIBLE);

        plus_pipeline_area = (LinearLayout) findViewById(R.id.plus_pipeline_area);
        plus_pipeline_area.setVisibility(View.GONE);

        up_spinner = (Spinner) findViewById(R.id.up_spinner);
        List<String> list1 = new ArrayList<String>();
        list1.add("UP 제스처 : 선택 안함");
        list1.add("UP 제스처 : 카메라 앱 실행");
        list1.add("UP 제스처 : 갤러리 앱 실행");
        list1.add("UP 제스처 : 메시지 앱 실행");
        list1.add("UP 제스처 : 플레이스토어 실행");
        list1.add("UP 제스처 : 설정 앱 실행");
        list1.add("UP 제스처 : 카카오톡 앱 실행");
        list1.add("UP 제스처 : 네이버 앱 실행");
        list1.add("UP 제스처 : 유튜브 앱 실행");
        list1.add("UP 제스처 : 멜론 앱 실행");
        list1.add("UP 제스처 : 페이스북 앱 실행");
        list1.add("UP 제스처 : 구글 메일 앱 실행");

        ArrayAdapter<String> categoriesAdapter1 = new ArrayAdapter<String>(NNStreamerActivity.this, android.R.layout.simple_spinner_item, list1);
        up_spinner.setAdapter(categoriesAdapter1);
        up_spinner.setBackgroundColor(Color.DKGRAY);
        if (strUP != "") {
            up_spinner.setSelection(Integer.parseInt(strUP));
        }

        down_spinner = (Spinner) findViewById(R.id.down_spinner);
        List<String> list2 = new ArrayList<String>();
        list2.add("DOWN 제스처 : 선택 안함");
        list2.add("DOWN 제스처 : 카메라 앱 실행");
        list2.add("DOWN 제스처 : 갤러리 앱 실행");
        list2.add("DOWN 제스처 : 메시지 앱 실행");
        list2.add("DOWN 제스처 : 플레이스토어 실행");
        list2.add("DOWN 제스처 : 설정 앱 실행");
        list2.add("DOWN 제스처 : 카카오톡 앱 실행");
        list2.add("DOWN 제스처 : 네이버 앱 실행");
        list2.add("DOWN 제스처 : 유튜브 앱 실행");
        list2.add("DOWN 제스처 : 멜론 앱 실행");
        list2.add("DOWN 제스처 : 페이스북 앱 실행");
        list2.add("DOWN 제스처 : 구글 메일 앱 실행");

        ArrayAdapter<String> categoriesAdapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list2);
        down_spinner.setAdapter(categoriesAdapter2);
        down_spinner.setBackgroundColor(Color.DKGRAY);
        if (strDOWN != "") {
            down_spinner.setSelection(Integer.parseInt(strDOWN));
        }

        left_spinner = (Spinner) findViewById(R.id.left_spinner);
        List<String> list3 = new ArrayList<String>();
        list3.add("LEFT 제스처 : 선택 안함");
        list3.add("LEFT 제스처 : 카메라 앱 실행");
        list3.add("LEFT 제스처 : 갤러리 앱 실행");
        list3.add("LEFT 제스처 : 메시지 앱 실행");
        list3.add("LEFT 제스처 : 플레이스토어 실행");
        list3.add("LEFT 제스처 : 설정 앱 실행");
        list3.add("LEFT 제스처 : 카카오톡 앱 실행");
        list3.add("LEFT 제스처 : 네이버 앱 실행");
        list3.add("LEFT 제스처 : 유튜브 앱 실행");
        list3.add("LEFT 제스처 : 멜론 앱 실행");
        list3.add("LEFT 제스처 : 페이스북 앱 실행");
        list3.add("LEFT 제스처 : 구글 메일 앱 실행");

        ArrayAdapter<String> categoriesAdapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list3);
        left_spinner.setAdapter(categoriesAdapter3);
        left_spinner.setBackgroundColor(Color.DKGRAY);
        if (strLEFT != "") {
            left_spinner.setSelection(Integer.parseInt(strLEFT));
        }

        right_spinner = (Spinner) findViewById(R.id.right_spinner);
        List<String> list4 = new ArrayList<String>();
        list4.add("RIGHT 제스처 : 선택 안함");
        list4.add("RIGHT 제스처 : 카메라 앱 실행");
        list4.add("RIGHT 제스처 : 갤러리 앱 실행");
        list4.add("RIGHT 제스처 : 메시지 앱 실행");
        list4.add("RIGHT 제스처 : 플레이스토어 실행");
        list4.add("RIGHT 제스처 : 설정 앱 실행");
        list4.add("RIGHT 제스처 : 카카오톡 앱 실행");
        list4.add("RIGHT 제스처 : 네이버 앱 실행");
        list4.add("RIGHT 제스처 : 유튜브 앱 실행");
        list4.add("RIGHT 제스처 : 멜론 앱 실행");
        list4.add("RIGHT 제스처 : 페이스북 앱 실행");
        list4.add("RIGHT 제스처 : 구글 메일 앱 실행");

        ArrayAdapter<String> categoriesAdapter4 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list4);
        right_spinner.setAdapter(categoriesAdapter4);
        right_spinner.setBackgroundColor(Color.DKGRAY);

        if (strRIGHT != "") {
            right_spinner.setSelection(Integer.parseInt(strRIGHT));
        }

        buttonModel5 = (Button) findViewById(R.id.main_button_m5);
        buttonModel5.setOnClickListener(this);
        buttonModel5.setText("이전 화면으로 돌아가기");

        CardList = new ArrayList();

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

        main_root = (RelativeLayout) findViewById(R.id.main_root);

        /* surface 카메라 */
        sv = (SurfaceView) this.findViewById(R.id.main_surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        /* 기본 코드의 파이프라인이 초기화될 때까지 비활성화된 버튼부터 시작하십시오. */
        enableButton(false);

        initialized = true;

        /* 손 감지를 바로 시작한다. */
        buttonModel2.performClick();
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
                    if (buttonModel2.isChecked()) option |= (1 << 2);
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
