package org.freedesktop.gstreamer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

/* 외부에서 폭과 높이를 설정할 수 있는 간단한 SurfaceView */
public class GStreamerSurfaceView extends SurfaceView {
    public static int media_width = 480;
    public static int media_height = 480;

    public GStreamerSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public GStreamerSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GStreamerSurfaceView (Context context) {
        super(context);
    }

    /**
     * 레이아웃 매니저가 전화하여 사이즈를 파악하고 몇 가지 규칙을 알려준다.
     * 규모를 극대화하고, 미디어의 가로 세로 비율을 보존할 수 있도록 노력하겠다.
     * 그렇게 할 수 있는 자유가 주어진다.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0, height = 0;
        int wmode = View.MeasureSpec.getMode(widthMeasureSpec);
        int hmode = View.MeasureSpec.getMode(heightMeasureSpec);
        int wsize = View.MeasureSpec.getSize(widthMeasureSpec);
        int hsize = View.MeasureSpec.getSize(heightMeasureSpec);

        Log.i ("GStreamer", "onMeasure called with " + media_width + "x" + media_height);

        /* 폭 규칙 준수 */
        switch (wmode) {
        case View.MeasureSpec.AT_MOST:
            if (hmode == View.MeasureSpec.EXACTLY) {
                width = Math.min(hsize * media_width / media_height, wsize);
                break;
            }
        case View.MeasureSpec.EXACTLY:
            width = wsize;
            break;
        case View.MeasureSpec.UNSPECIFIED:
            width = media_width;
        }

        /* 높이 규칙 준수 */
        switch (hmode) {
        case View.MeasureSpec.AT_MOST:
            if (wmode == View.MeasureSpec.EXACTLY) {
                height = Math.min(wsize * media_height / media_width, hsize);
                break;
            }
        case View.MeasureSpec.EXACTLY:
            height = hsize;
            break;
        case View.MeasureSpec.UNSPECIFIED:
            height = media_height;
        }

        /* 마지막으로 양쪽 축이 비어 있을 때 최적의 크기를 계산하십시오. */
        if (hmode == View.MeasureSpec.AT_MOST && wmode == View.MeasureSpec.AT_MOST) {
            int correct_height = width * media_height / media_width;
            int correct_width = height * media_width / media_height;

            if (correct_height < height)
                height = correct_height;
            else
                width = correct_width;
        }

        /* 최소 크기 준수 */
        width = Math.max (getSuggestedMinimumWidth(), width);
        height = Math.max (getSuggestedMinimumHeight(), height);
        setMeasuredDimension(width, height);
    }
}
