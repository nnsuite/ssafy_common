package org.freedesktop.gstreamer.nnstreamer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.Builder;
import com.afollestad.materialdialogs.Theme;

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

    private TextView viewDesc;
    private ImageButton buttonCam;
    private ToggleButton buttonModel1;
    private ToggleButton buttonModel2;
    private ToggleButton buttonModel3;
    private ToggleButton buttonModel4;
    private Button buttonModel5;
    private TimerTask timerTask;
    private Timer timer = new Timer();

    /* Functions used by onBackPressed */
    private final long FINISH_INTERVAL_TIME = 2000;
    private long backPressedTime = 0;

    /* Functions used by startTimerTask */
    private int rectX = 0, rectY = 0, rectW = 0, rectH = 0;
    private String strUP = "", strDOWN = "", strLEFT = "", strRIGHT = "";
    private ArrayList<Integer> resultArrayX = new ArrayList();
    private ArrayList<Integer> resultArrayY = new ArrayList();

    /* Functions used by appRunFunction */
    private int layout_cnt = 1, schedule_cnt = 0;

    /* Functions used by initActivity */
    private RelativeLayout main_surface_area, main_root;
    private LinearLayout main_pipeline_area, plus_pipeline_area, sub_pipeline_area;
    private Spinner up_spinner, down_spinner, left_spinner, right_spinner;

    private SurfaceView sv;
    private List CardList;

    private int temp_cnt = 0, print_cnt = 0;
    private String resultText = "";

    /* Function used to create */
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
        /* activity settings, timer starts */
        initActivity();

        /* Service Start */
        enableAutoStart();
        Intent intent = new Intent(this, NNStreamerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        }
        else {
            startService(intent);
        }

        /* Plus - startTimer */
        startTimerTask();
    }

    /* Function used when paused */
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

    /* Function used to resume */
    @Override
    public void onResume() {
        super.onResume();

        if (schedule_cnt == 0) {
            /* Start pipeline */
            if (initialized) {
                if (downloadTask != null && downloadTask.isProgress()) {
                    Log.d(TAG, "모델 파일 다운로드 시작");
                } else {
                    startPipeline(PIPELINE_ID);
                }
            }
        }
    }

    /* Function used to exit */
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

        Intent intent = new Intent(this, NNStreamerService.class);
        stopService(intent);
    }

    /* Pressing the previous button twice completely shuts down */
    /* Stored data in SharedPreferences */
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

    /* Timer start, gesture judge (left, right, top, bottom) */
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

    /* Timer End */
    private void stopTimerTask(){
	if(timerTask != null){
	    timerTask.cancel();
	    timerTask=null;
	}
    }

    /* Follow the gestures to run the application */
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
                    Log.e(TAG, "중단되는 에러 : " + e.getMessage());
                }

                nativePlay();

                /* Update UI (buttons and other components) */
                enableButton(true);
            }
        });
    }

    /* Load library */
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("nnstreamer-jni");
        nativeClassInit();
    }

    /* SurfaceHolder.Callback interface implementation */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Log.d(TAG, "Surface 포맷 변경 " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit(holder.getSurface());
    }

    /* SurfaceHolder.Callback interface implementation */
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "Surface 생성: " + holder.getSurface());
    }

    /* SurfaceHolder.Callback interface implementation */
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "Surface 파괴");
        nativeSurfaceFinalize();
    }

    /* Event that appears when you click the button */
    @Override
    public void onClick(View v) {
        /* View.OnClickListener interface implementation */
        final int viewId = v.getId();

        if (pipelineTimer != null) {
            /* Do nothing, new pipeline will be started soon. */
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

                SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
                editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
                editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
                editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

                editor.commit();
                break;

            case R.id.main_button_m4:
                String packageName = "org.freedesktop.gstreamer.nnstreamer.multi";
                String url = "market://details?id=" + packageName;
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(i);
                break;

            /* fallthrough */
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

    /* Permission Acceptance Function */
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
     * Check the given permission is granted.
     */
    private boolean checkPermission(final String permission) {
        return (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Create toast with given message.
     */
    private void showToast(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Initialize GStreamer and the layout.
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

        viewDesc = (TextView) findViewById(R.id.main_text_desc);

        buttonCam = (ImageButton) findViewById(R.id.main_button_cam);
        buttonCam.setOnClickListener(this);

        /* Add event listener for models */
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

        /* Save existing variables, update card view */
        main_surface_area = (RelativeLayout) findViewById(R.id.main_surface_area);
        main_surface_area.setVisibility(View.VISIBLE);

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

        /* Spinner, Button setting */
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

        /* CardList setting */
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

        /* Video surface for camera */
        sv = (SurfaceView) this.findViewById(R.id.main_surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        /* Start with disabled buttons, until the pipeline in native code is initialized. */
        enableButton(false);

        initialized = true;

        /* Start hand sensing right away. */
        buttonModel2.performClick();

        /* Real-Time Spinner */
        up_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
                editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
                editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
                editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /* Real-Time Spinner */
        down_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
                editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
                editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
                editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /* Real-Time Spinner */
        left_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
                editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
                editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
                editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /* Real-Time Spinner */
        right_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences sharedPreferences = getSharedPreferences("sFile",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("up", Integer.toString(up_spinner.getSelectedItemPosition()));
                editor.putString("down", Integer.toString(down_spinner.getSelectedItemPosition()));
                editor.putString("left", Integer.toString(left_spinner.getSelectedItemPosition()));
                editor.putString("right", Integer.toString(right_spinner.getSelectedItemPosition()));

                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    /**
     * Enable (or disable) buttons to launch model.
     */
    public void enableButton(boolean enabled) {
        buttonCam.setEnabled(enabled);
        buttonModel1.setEnabled(enabled);
        buttonModel2.setEnabled(enabled);
        buttonModel3.setEnabled(enabled);
        buttonModel4.setEnabled(enabled);
    }

    /**
     * Start pipeline and update UI.
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
     * Cancel pipeline timer.
     */
    private void stopPipelineTimer() {
        if (pipelineTimer != null) {
            pipelineTimer.cancel();
            pipelineTimer = null;
        }
    }

    /**
     * Set timer to start new pipeline.
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
                    if (buttonModel2.isChecked()) option |= (1 << 2);
                    if (useFrontCamera) option |= (1 << 8);
                }

                nativeStart(pipelineId, option);
            }
        }.start();
    }

    /**
     * Check a model file exists in specific directory.
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
     * Start to download model files.
     */
    private void downloadModels() {
        downloadTask = new DownloadModel(this, downloadPath);
        downloadTask.execute(downloadList);
    }

    /**
     * Check all necessary files exists in specific directory.
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
     * Show dialog to download model files.
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

    /**
     * Voice Recognition Auto Start.
     */
    private void enableAutoStart() {
        for (Intent intent : Constants.AUTO_START_INTENTS) {
            if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                new Builder(this).title(R.string.enable_autostart)
                        .content(R.string.ask_permission)
                        .theme(Theme.LIGHT)
                        .positiveText(getString(R.string.allow))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                try {
                                    for (Intent intent1 : Constants.AUTO_START_INTENTS)
                                        if (NNStreamerActivity.this.getPackageManager().resolveActivity(intent1, PackageManager.MATCH_DEFAULT_ONLY)
                                                != null) {
                                            NNStreamerActivity.this.startActivity(intent1);
                                            break;
                                        }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                        .show();
                break;
            }
        }
    }
}
