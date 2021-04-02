package com.bresee.breseefaceapitest.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.bresee.breseefaceapitest.MainActivity;
import com.bresee.breseefaceapitest.R;
import com.bresee.breseefaceapitest.model.ProgressList;
import com.bresee.breseefaceapitest.service.TcpService;
import com.bresee.breseefaceapitest.utils.LiveDataBus;
import com.bresee.breseefaceapitest.utils.NavigationBarUtil;
import com.bresee.breseefaceapitest.utils.NetWorkUtils;
import com.bresee.breseefaceapitest.utils.Utils;
import com.bumptech.glide.Glide;

import java.util.Arrays;

public class SplashActivity extends BaseActivity {


    private Context mContext;
    private final Handler handler = new Handler();

    /**
     * 进度条
     */
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mContext = this;

        initView();

        if (checkPermissions()) {
            openService();
        }

    }

    @SuppressLint("SetTextI18n")
    private void initView() {

        String ip = NetWorkUtils.getLocalIpAddress();
        if (ip != null && !ip.equals("")) {
            String newIP = ip.replace(".", "");
            String deviceID = newIP.substring(newIP.length() - 6);
            TextView tvDeviceID = findViewById(R.id.tvDeviceID);
            tvDeviceID.setText("设备ID：" + deviceID);
            Log.d("TAG", "onCreate: 设备ID" + deviceID);
        }

        ImageView imgGif = findViewById(R.id.imgGif);
        Glide.with(mContext).asGif().load(R.drawable.main_bg).into(imgGif);

        //获取后台人脸特征库
        LiveDataBus.get().with("progressData", ProgressList.class).observe(this, new Observer<ProgressList>() {
            @Override
            public void onChanged(@Nullable ProgressList progressList) {
                if (progressList != null) {
                    //下载人脸特征库进度条
                    onProgress(progressList);
                }
            }
        });
    }


    /**
     * 开启服务
     */
    private void openService() {
        //开启socket以及串口，先判断防止重复开启服务
        if (!Utils.isServiceRunning(mContext, "com.bresee.breseefaceapitest.service.TcpService")) {
            Intent intent = new Intent(SplashActivity.this, TcpService.class);
            startService(intent);
        }

        handler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        }, 10 * 1000);
    }

    /**
     * 检查权限
     *
     * @return
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkPermissions()) {
            openService();
        }
    }

    /**
     * 下载人脸特征库进度条
     */
    private void onProgress(ProgressList progressList) {
        if (!progressList.isProgress()) {
            progressDialog = new ProgressDialog(mContext);
            progressDialog.setTitle("下载人脸库");
            progressDialog.setMessage("正在下载中...");
            progressDialog.setMax(progressList.getFaceLibNum());
            progressDialog.setCancelable(false);
            progressDialog.setIndeterminate(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            NavigationBarUtil.dialogShow(progressDialog);
        }
        if (progressDialog != null) {
            progressDialog.setProgress(progressList.getSuccess());
            if (progressList.getFaceLibNum() - progressList.getSuccess() == 0) {
                progressDialog.dismiss();
            }
        }
    }

}
