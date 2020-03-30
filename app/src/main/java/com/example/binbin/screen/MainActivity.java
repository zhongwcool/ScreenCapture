package com.example.binbin.screen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;
    private static final int RC_CAMERA_AND_LOCATION = 123;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private int mScreenDensity;//密度
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;//很老的类啊
    private String[] perms = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    /*
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    */

    @AfterPermissionGranted(RC_CAMERA_AND_LOCATION)
    private boolean checkBasicPermission() {
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            return true;
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.camera_and_location_rationale),
                    RC_CAMERA_AND_LOCATION,
                    perms);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        // Some permissions have been granted
        // ...
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());

        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).build().show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;//获取屏幕密度 int

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);//获取服务

        mToggleButton = findViewById(R.id.toggle);
        mToggleButton.setOnClickListener(v -> {
            if (checkBasicPermission()) {
                onToggleScreenShare(v);
                mToggleButton.setChecked(true);
            } else {
                mToggleButton.setChecked(false);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // Do something after user returned from app settings screen, like showing a Toast.
            checkBasicPermission();
        }

        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
        //应用最小化
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mMediaProjectionCallback = new MediaProjectionCallback();//回调类
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);//注册回调类
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            initRecorder();//初始化路径
            shareScreen();//开始录像

        } else {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            Log.v(TAG, "Stopping Recording");
            stopScreenSharing();
            //跳转录屏结果
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            startActivity(intent);
        }
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);//开始录像
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();//声音录制？
    }

    /*
    参数说明：
    * 第1个参数：虚拟画面名称
    * 第2个参数：虚拟画面的宽度
    * 第3个参数：虚拟画面的高度
    * 第4个参数：录制画面的dpi
    * 第5个参数：虚拟画面的标志
    * 第6个参数：画面输出的Surface
    * 第7个参数：虚拟画面回调接口
    * 其中最重要的就是第6个参数，录制画面输出的地方，他这里介绍的是一个Surface类型，那么我们如果想操作录制之后的视频数据，就需要创建一个Surface类型即可。
    * 1、如果想截屏，那么可以使用ImageReader类的getSurface方法获取
    * 2、如果想录制视频进行编码，可以使用MediaRecorder类的getSurface方法获取
    */
    /*surface不同于surfaceView*/
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay(
                "MainActivity",
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                /*surface是mediaRecorder的，以mediaProjection为源输出到surface，捕获*/
                mMediaRecorder.getSurface(),
                null/*Callbacks*/,
                null/*Handler*/
        );
    }

    /*
    MediaRecorder这个类相对简单，因为他封装的很好，直接就是几个接口来完成视频录制，
    比如视频的编码格式，视频的保存路径，视频来源等，用法简单
    */

    private void initRecorder() {
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //MediaRecorder这个类也是可以通过setVideoSource方法设置Surface类型的视频输入源的
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setOutputFile(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/video.mp4"
            );
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
            mMediaRecorder.setVideoFrameRate(30);
            //int rotation = getWindowManager().getDefaultDisplay().getRotation();
            //int orientation = ORIENTATIONS.get(rotation + 90);
            //mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
            }
            mMediaProjection = null;
            stopScreenSharing();
        }
    }
}
