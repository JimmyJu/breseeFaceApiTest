package com.bresee.breseefaceapitest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bresee.breseefaceapitest.activity.BaseActivity;
import com.bresee.breseefaceapitest.camera.CameraManager;
import com.bresee.breseefaceapitest.camera.CameraPreview;
import com.bresee.breseefaceapitest.camera.CameraPreviewData;
import com.bresee.breseefaceapitest.camera.ComplexFrameHelper;
import com.bresee.breseefaceapitest.utils.LiveDataBus;
import com.bresee.breseefaceapitest.utils.LogUtils;
import com.bresee.breseefaceapitest.utils.Utils;
import com.bresee.breseefacelib.FaceCompareResult;
import com.bresee.breseefacelib.FaceConfigSetting;
import com.bresee.breseefacelib.FaceDetResult;
import com.bresee.breseefacelib.FaceExtrResult;
import com.bresee.breseefacelib.FaceSDKManager;
import com.bresee.breseefacelib.InputImageParam;
import com.bresee.breseefacelib.VersionInfos;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_180;
import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_270;
import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_90;
import static com.bresee.breseefaceapitest.ConfigParam.onlineLicenseButton;

public class MainActivity extends BaseActivity {
    public final String TAG = "MainActivity";

    private FaceConfigSetting faceConfigSetting;
    private PointerByReference faceDetHandle;
    private PointerByReference faceExtrHandle;
    private InputImageParam inputFdImageParam;
    private InputImageParam inputFrImageParam;
    private FaceDetResult faceDetResult;
    private FaceDetResult faceRegInput;
    private FaceExtrResult faceExtrResult;
    private FaceCompareResult faceCompareResult;

    private CameraPreview cameraView;
    private CameraPreview cameraView_ir;
    private ArrayBlockingQueue<ActivityHelper.RecognizeData> mRecognizeDataQueue;
    private CameraManager manager;
    private CameraManager manager_ir;
    private RecognizeThread mRecognizeThread;
    private DetectThread mDetectThread;

    private String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
    List<String> mPermissionList = new ArrayList<>();
    private static final int REQUEST_ALL_PERMISSION = 11;

    private Handler mHandler;
    private FaceView faceView;
    private TextView resultOut, version;
    private FaceSDKManager faceSDKManager;
    private int FrameSeqNum = 0;
    private boolean isBusyRecognise = false;

    //上报策略（可根据需求自行修改）
    private int faceTrackIdSubTmp = 0;
    //private int faceGlobalTrackIdTmp=0;
    private int initReturn = 0;

    /**
     * 卡号
     */
    private final byte[] cardNumberByte = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    /**
     * 人员名称
     */
    private final byte[] personnelNameByte = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    /**
     * 检测人脸间隔标识
     */
    private boolean faceFlag = false;
    /**
     * 照片信息头
     */
    private final byte[] imageHead = new byte[]{(byte) 0xFF, (byte) 0xD8};
    /**
     * 照片信息尾
     */
    private final byte[] imageEnd = new byte[]{(byte) 0xFF, (byte) 0xD9};
    /**
     * 存储人脸照片
     */
    private final Hashtable<String, byte[]> faceImage = new Hashtable<>();
    /**
     * 识别成功、失败view
     */
    private RelativeLayout rlDiscernBg;
    private ImageView detect_reg_image_item;
    private TextView tvDiscernSucceed;
    private ImageView imgLine;
    private TextView tvName;
    private TextView tvDiscernFailure;

    /**
     * 底部状态View
     */
    private TextView mLiveTextView, mAdoptTextView, mErrorTextView, mSendTextView, mServerStateTextView;

    private Bitmap mRBmp;

    /**
     * 串口、声音相关
     */
    private final Hashtable<String, Long> faceTime = new Hashtable<>();
    private int mLiveNum, mAdoptNum, mErrorNum;
    /**
     * 播报mp3
     */
    private SoundPool mSoundPool = null;
    private final HashMap<Integer, Integer> soundID = new HashMap<>();
    private float volume;

    private final String reg = "[^\u4e00-\u9fa5]";
    private final String regEx = "[^0-9]";
    private final Pattern p = Pattern.compile(regEx);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        mRecognizeDataQueue = new ArrayBlockingQueue<>(1);
        mHandler = new Handler();

        if (ConfigParam.liveButton == 1 && ConfigParam.liveLevel == 1) {
            initView();
        } else {
            initViewSimple();
        }
        //声音播报
        initSP();

        checkPermission();

        sdkAllFlow();

        liveDataBus();
    }

    @Override
    protected void onResume() {
        if (ConfigParam.liveButton == 1 && ConfigParam.liveLevel == 1) {
            manager_ir.open(getWindowManager(), true, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//红外
            manager.open(getWindowManager(), false, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//可见光
        } else {
            manager.open(getWindowManager(), false, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//可见光
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        mRecognizeDataQueue.clear();
        if (manager != null) {
            manager.release();
        }
        if (manager_ir != null) {
            manager_ir.release();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mRecognizeThread.isInterrupt = true;
        mDetectThread.isInterrupt = true;
        mRecognizeThread.interrupt();
        mDetectThread.interrupt();
        if (manager != null) {
            manager.release();
        }
        if (manager_ir != null) {
            manager_ir.release();
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        sdkDestroy();
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        faceView.clear();
        faceView.invalidate();
        super.onRestart();
    }

    private boolean checkPermission() {
        mPermissionList.clear();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permission);
            }
        }
        if (!mPermissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, mPermissionList.toArray(new String[mPermissionList.size()]), REQUEST_ALL_PERMISSION);
            return false;
        }
        return true;
    }

    public void alertEditForDetect(View view) {
        final EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle("请输入删除ID")
                .setIcon(android.R.drawable.sym_def_app_icon)
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String deleteFaceId = et.getText().toString();
                        sdkDeleteFace(deleteFaceId);
                    }
                }).setNegativeButton("取消", null).show();
    }

    /**
     * 调用SDK人脸算法库.
     */
    private void sdkAllFlow() {
        if (onlineLicenseButton == 1) {
            if (sdkOnlineLicense())//在线授权（联网可自动授权）
            {
                sdkVersion();
                sdkLoadLibray();
                sdkInit();

                mRecognizeThread = new RecognizeThread();
                mRecognizeThread.start();
                mDetectThread = new DetectThread();
                mDetectThread.start();
            } else {
                Log.e(TAG, "###SDK在线授权失败,请检测网络连接!!");
            }
        } else {//离线授权
            sdkVersion();
            sdkLoadLibray();
            sdkInit();

            mRecognizeThread = new RecognizeThread();
            mRecognizeThread.start();
            mDetectThread = new DetectThread();
            mDetectThread.start();
        }
    }

    /**
     * 库人脸删除.
     */
    private void sdkDeleteFace(String faceInfo) {
        int deleteFaceDbState = faceSDKManager.isfDeleteFaceFeature(ConfigParam.faceLibName, faceInfo, ConfigParam.faceLibFile);
        Log.e(TAG, "###sdkClearLibray down :" + deleteFaceDbState);
        final String dbNameId = faceInfo;
        faceView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, dbNameId + "删除成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 双目初始化界面.
     */
    private void initView() {
        //获取屏幕横竖信息
        final int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "###screenState-竖屏");
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "###screenState-横屏");
        }

        //获取屏幕大小
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        Log.i(TAG, "###displayMetrics height :" + displayMetrics.heightPixels);
        Log.i(TAG, "###displayMetrics width :" + displayMetrics.widthPixels);

        setContentView(R.layout.activity_main);

        faceSDKManager = FaceSDKManagerSingleton.getInstance();

        /* 初始化界面 */
        resultOut = findViewById(R.id.result);
        faceView = (FaceView) this.findViewById(R.id.fcview);
        cameraView = (CameraPreview) findViewById(R.id.preview);
        cameraView_ir = (CameraPreview) findViewById(R.id.preview_ir);
        version = findViewById(R.id.vesion);
        findViewById(R.id.setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.release();
                final Intent intent = new Intent();
                intent.setClass(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertEditForDetect(v);
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });

        manager = new CameraManager();
        manager.setPreviewDisplay(cameraView);

        manager_ir = new CameraManager();
        manager_ir.setPreviewDisplay(cameraView_ir);
        cameraView_ir.setZOrderOnTop(true);

        /* 可见光相机回调函数 */
        manager.setListener(new CameraManager.CameraListener() {
            @Override
            public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                ComplexFrameHelper.addRgbFrameAndMakeComplex(cameraPreviewData);
            }
        });
        /* 红外相机回调函数 */
        manager_ir.setListener(new CameraManager.CameraListener() {
            @Override
            public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                ComplexFrameHelper.addIRFrame(cameraPreviewData);
            }
        });

        rlDiscernBg = findViewById(R.id.rlDiscernBg);
        detect_reg_image_item = findViewById(R.id.detect_reg_image_item);
        tvDiscernSucceed = findViewById(R.id.tvDiscernSucceed);
        imgLine = findViewById(R.id.imgLine);
        tvName = findViewById(R.id.tvName);
        tvDiscernFailure = findViewById(R.id.tvDiscernFailure);

        mLiveTextView = findViewById(R.id.live);
        mAdoptTextView = findViewById(R.id.adopt);
        mErrorTextView = findViewById(R.id.error);
        mSendTextView = findViewById(R.id.send);
        mServerStateTextView = findViewById(R.id.server);
    }

    /**
     * 单目初始化界面.
     */
    private void initViewSimple() {
        //获取屏幕横竖信息
        final int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "###screenState-竖屏");
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "###screenState-横屏");
        }

        //获取屏幕大小
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        Log.i(TAG, "###displayMetrics height :" + displayMetrics.heightPixels);
        Log.i(TAG, "###displayMetrics width :" + displayMetrics.widthPixels);

        setContentView(R.layout.activity_main);

        faceSDKManager = FaceSDKManagerSingleton.getInstance();

        /* 初始化界面 */
        resultOut = findViewById(R.id.result);
        faceView = (FaceView) this.findViewById(R.id.fcview);
        cameraView = (CameraPreview) findViewById(R.id.preview);
        version = findViewById(R.id.vesion);
        findViewById(R.id.setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.release();
                final Intent intent = new Intent();
                intent.setClass(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertEditForDetect(v);
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });

        manager = new CameraManager();
        manager.setPreviewDisplay(cameraView);

        /* 可见光相机回调函数 */
        manager.setListener(new CameraManager.CameraListener() {
            @Override
            public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                ComplexFrameHelper.addRgbFrame(cameraPreviewData);
            }
        });

        rlDiscernBg = findViewById(R.id.rlDiscernBg);
        detect_reg_image_item = findViewById(R.id.detect_reg_image_item);
        tvDiscernSucceed = findViewById(R.id.tvDiscernSucceed);
        imgLine = findViewById(R.id.imgLine);
        tvName = findViewById(R.id.tvName);
        tvDiscernFailure = findViewById(R.id.tvDiscernFailure);

        mLiveTextView = findViewById(R.id.live);
        mAdoptTextView = findViewById(R.id.adopt);
        mErrorTextView = findViewById(R.id.error);
        mSendTextView = findViewById(R.id.send);
        mServerStateTextView = findViewById(R.id.server);
    }

    /**
     * SDK在线激活.
     */
    private boolean sdkOnlineLicense() {
        //SDK在线激活(如果已存在，则不二次申请)
        String userName = "shejinlong@bresee.cn";
        String passWord = "shejinlong!123";
        String licSavePathDir = ConfigParam.workBasePath;
        int codeReturn = faceSDKManager.isfGetLicense(userName, passWord, licSavePathDir);
        Log.e(TAG, "###IsfGetLicense down :" + codeReturn);
        if (codeReturn == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取SDK版本.
     */
    private void sdkVersion() {
        VersionInfos versionInfos = new VersionInfos();
        int codeReturn = faceSDKManager.isfGetVersion(versionInfos);
        Log.e(TAG, "###sdkVersion down :" + codeReturn);
        version.setText(versionInfos.version);
    }

    /**
     * 加载人脸库.
     */
    private void sdkLoadLibray() {
        //加载人脸库
        String featureLibName = ConfigParam.faceLibName;
        int loadFaceDbState = faceSDKManager.isfLoadFeatureLibrary(featureLibName, ConfigParam.faceLibFile);
        Log.e(TAG, "###sdkLoadLibray :" + loadFaceDbState);
    }

    /**
     * SDK初始化.
     */
    private void sdkInit() {
        /*File workFile=new File(ConfigParam.workBasePath1);
        if(!workFile.exists())
        {
            workFile.mkdir();
        }*/

        faceView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "请优先注册人脸", Toast.LENGTH_LONG).show();
            }
        });

        faceConfigSetting = new FaceConfigSetting();
        faceConfigSetting.qualityButton = ConfigParam.qualityButton;
        faceConfigSetting.angleButton = ConfigParam.angleButton;
        faceConfigSetting.maskButton = ConfigParam.maskButton;
        faceConfigSetting.livenessButton = ConfigParam.liveButton;
        faceConfigSetting.livenessLevel = ConfigParam.liveLevel;
        faceConfigSetting.extractButton = ConfigParam.extraButton;
        faceConfigSetting.faceLibNmame = ConfigParam.faceLibName;
        faceConfigSetting.fullScreen = ConfigParam.fullScreenButton;
        faceConfigSetting.detectAreaPoints = ConfigParam.screenROI;

        faceDetHandle = new PointerByReference(Pointer.NULL);
        faceExtrHandle = new PointerByReference(Pointer.NULL);
        inputFdImageParam = new InputImageParam();
        inputFrImageParam = new InputImageParam();
        faceDetResult = new FaceDetResult();
        faceRegInput = new FaceDetResult();
        faceExtrResult = new FaceExtrResult();
        faceCompareResult = new FaceCompareResult();

        //SDK初始化
        initReturn = faceSDKManager.isfFaceInit(ConfigParam.workBasePath, faceConfigSetting, faceDetHandle, faceExtrHandle);
        Log.e(TAG, "###IsfFaceInit :" + initReturn);
    }

    /**
     * SDK销毁.
     */
    private void sdkDestroy() {
        int codeReturn = faceSDKManager.isfFaceDestroy(faceDetHandle, faceExtrHandle);
        Log.i(TAG, "###IsfDestroy down :" + codeReturn);
    }

    private class DetectThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                Pair<CameraPreviewData, CameraPreviewData> cameraPreviewData = null;
                try {
                    cameraPreviewData = ComplexFrameHelper.takeComplexFrame();
                    //saveYuvImage(cameraPreviewData.first.nv21Data,cameraPreviewData.first.width, cameraPreviewData.first.height);//保存NV21图片
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (faceSDKManager == null || inputFdImageParam == null) {
                    continue;
                }

                /*图像角度矫正*/
                if (ConfigParam.cameraFrameRotation == 0) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrByteData = cameraPreviewData.first.nv21Data;
                    if (null != cameraPreviewData.second) {
                        inputFdImageParam.riBgrByteData = cameraPreviewData.second.nv21Data;
                    } else {
                        inputFdImageParam.riBgrByteData = null;
                    }
                } else if (ConfigParam.cameraFrameRotation == 90) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_90(cameraPreviewData.first.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                    if (null != cameraPreviewData.second) {
                        inputFdImageParam.riBgrByteData = NV21_rotate_to_90(cameraPreviewData.second.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                    } else {
                        inputFdImageParam.riBgrByteData = null;
                    }
                } else if (ConfigParam.cameraFrameRotation == 180) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_180(cameraPreviewData.first.nv21Data, inputFdImageParam.bgrImgWidht, inputFdImageParam.bgrImgHeight);
                    if (null != cameraPreviewData.second) {
                        inputFdImageParam.riBgrByteData = NV21_rotate_to_180(cameraPreviewData.second.nv21Data, inputFdImageParam.bgrImgWidht, inputFdImageParam.bgrImgHeight);
                    } else {
                        inputFdImageParam.riBgrByteData = null;
                    }
                } else if (ConfigParam.cameraFrameRotation == 270) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.first.width;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.first.height;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_270(cameraPreviewData.first.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                    if (null != cameraPreviewData.second) {
                        inputFdImageParam.riBgrByteData = NV21_rotate_to_270(cameraPreviewData.second.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                    } else {
                        inputFdImageParam.riBgrByteData = null;
                    }
                }
                inputFdImageParam.bgrImgStyle = 1;
                inputFdImageParam.bgrImgSeq = ++FrameSeqNum;

                //saveYuvImage(cameraPreviewData.first.nv21Data,cameraPreviewData.first.width, cameraPreviewData.first.height);//保存NV21图片
                int mDetectState = faceSDKManager.isfFaceDetector(inputFdImageParam, faceDetResult, faceDetHandle);
                //Log.i(TAG, "###isfFaceDetector :" +mDetectState);

                /*上报策略*/
                boolean isDetectReport = true;
                if (faceTrackIdSubTmp != faceDetResult.faceTrackId) {
                    isDetectReport = false;
                }
                faceTrackIdSubTmp = faceDetResult.faceTrackId;

                if (mDetectState != 0 || inputFdImageParam.bgrByteData == null || faceDetResult == null ||
                        faceDetResult.detTargetNum == 0) {

                    /* 清除人脸框 */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            faceView.clear();
                            faceView.invalidate();
                        }
                    });
                    showResult(0, "");

                    //license空时上报
                    if (initReturn == 7) {
                        showResult(Color.RED, "算法库未授权");
                    }
                } else {
                    if (!isBusyRecognise && isDetectReport) {
                        //送入识别数据类
                        inputFrImageParam.bgrByteData = inputFdImageParam.bgrByteData.clone();
                        if (inputFdImageParam.riBgrByteData != null) {
                            inputFrImageParam.riBgrByteData = inputFdImageParam.riBgrByteData.clone();
                        } else {
                            inputFrImageParam.riBgrByteData = null;
                        }
                        inputFrImageParam.bgrImgHeight = inputFdImageParam.bgrImgHeight;
                        inputFrImageParam.bgrImgWidht = inputFdImageParam.bgrImgWidht;
                        inputFrImageParam.bgrImgStride = inputFdImageParam.bgrImgStride;
                        inputFrImageParam.bgrImgSeq = inputFdImageParam.bgrImgSeq;
                        inputFrImageParam.bgrImgStyle = inputFdImageParam.bgrImgStyle;

                        faceRegInput.faceRectangle = faceDetResult.faceRectangle;
                        faceRegInput.detTargetNum = faceDetResult.detTargetNum;
                        faceRegInput.faceTrackId = faceDetResult.faceTrackId;
                        faceRegInput.faceLandmarks = faceDetResult.faceLandmarks.clone();
                        faceRegInput.landmarkNum = faceDetResult.landmarkNum;
                        faceRegInput.angleScore = faceDetResult.angleScore;
                        faceRegInput.qualityScore = faceDetResult.qualityScore;
                        faceRegInput.extractPattern = 2;

                        ActivityHelper.RecognizeData mRecData = new ActivityHelper.RecognizeData(inputFrImageParam, faceRegInput);
                        mRecognizeDataQueue.offer(mRecData);
                    }

                    /* 将识别到的人脸在预览界面中画出*/
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFaceTrackRect(faceDetResult);
                        }
                    });

                    //清理结果显示
                    if (FrameSeqNum % 15 == 0) {
                        showResult(0, "");
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    private class RecognizeThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            //String comResultOut = "";
            //int recStateColor = 0;
            while (!isInterrupt) {
                try {
                    ActivityHelper.RecognizeData recognizeData = mRecognizeDataQueue.take();
                    isBusyRecognise = true;
                    int faceTrackIdReg = recognizeData.faceRegInputData.faceTrackId;
                    /**人脸特征提取*/
                    int mRecognuseState = faceSDKManager.isfFaceExtractor(recognizeData.inputFrImageParamData, recognizeData.faceRegInputData, faceExtrResult, faceExtrHandle);
                    Log.i(TAG, "###isfFaceExtractor down :" + mRecognuseState);
                    Log.i(TAG, "###口罩佩戴情况 :" + faceExtrResult.maskResult);/**口罩状态 0-未戴 1-佩戴*/

                    if (faceTrackIdSubTmp == faceTrackIdReg) {
                        if ((0 == mRecognuseState) && (1 == ConfigParam.extraButton)) {
                            /**人脸库检索*/
                            int mCompareState = faceSDKManager.isfGetTopOfFaceLib(ConfigParam.faceLibName, faceExtrResult, faceCompareResult);
                            Log.i(TAG, "###IsfGetTopOfFaceLib down :" + mCompareState);

                            if ((null != faceCompareResult) && (mCompareState == 0)) {
                                if (faceCompareResult.compareSimilarScore > ConfigParam.recogniseThreshold) {
                                    //comResultOut = "识别成功\n" + new String(faceCompareResult.compareFaceInfo);
                                    //recStateColor = Color.GREEN;
                                    Log.i(TAG, "###识别成功 :" + faceCompareResult.compareSimilarScore);

                                    String info = new String(faceCompareResult.compareFaceInfo);
                                    //只提取中文汉字
                                    String userInfo = info.replaceAll(reg, "");
                                    //只提取数字
                                    Matcher m = p.matcher(info);
                                    byte[] image = YuvToNV21(
                                            recognizeData.inputFrImageParamData.bgrByteData,
                                            recognizeData.inputFrImageParamData.bgrImgWidht,
                                            recognizeData.inputFrImageParamData.bgrImgHeight);
                                    //延迟3秒发送给后台图片，特征，人名，卡号
                                    delaySendData(true, image, faceExtrResult.faceFeatureByte, userInfo, m.replaceAll("").trim());
                                    if (faceImage.containsKey(userInfo)) {
                                        //识别成功
                                        discernSucceedView(userInfo, Bytes2Bitmap(faceImage.get(userInfo)));
                                    } else {
                                        faceImage.put(userInfo, image);
                                        //识别成功
                                        discernSucceedView(userInfo, Bytes2Bitmap(image));
                                    }
                                    //发送串口数据
                                    sendSerialPortData(m.replaceAll("").trim());
                                } else {
                                    //recStateColor = Color.RED;
                                    //comResultOut = "调整人脸位置";
                                    Log.e(TAG, "###人员未注册 :" + faceCompareResult.compareSimilarScore);

                                    byte[] image = YuvToNV21(
                                            recognizeData.inputFrImageParamData.bgrByteData,
                                            recognizeData.inputFrImageParamData.bgrImgWidht,
                                            recognizeData.inputFrImageParamData.bgrImgHeight);
                                    //识别失败
                                    discernFailureView();
                                    //延迟3秒发送给后台图片、特征值
                                    byte[] imageData = Utils.addBytes(imageHead, image, imageEnd);
                                    delaySendData(false, imageData, faceExtrResult.faceFeatureByte, null, null);
                                    //发送串口数据
                                    sendSerialPortData(null);
                                }
                            } else {
                                //comResultOut = "";
                                byte[] image = YuvToNV21(
                                        recognizeData.inputFrImageParamData.bgrByteData,
                                        recognizeData.inputFrImageParamData.bgrImgWidht,
                                        recognizeData.inputFrImageParamData.bgrImgHeight);
                                //识别失败
                                discernFailureView();
                                //延迟3秒发送给后台图片、特征值
                                byte[] imageData = Utils.addBytes(imageHead, image, imageEnd);
                                delaySendData(false, imageData, faceExtrResult.faceFeatureByte, null, null);
                                //发送串口数据
                                sendSerialPortData(null);
                            }
                        } else if (ConfigParam.maskButton == 2 && mRecognuseState == 1) {
                            //recStateColor = Color.RED;
                            //comResultOut = "请佩戴口罩";
                            Log.e(TAG, "run...: 请佩戴口罩");
                        } else if (mRecognuseState == 2) {
                            //recStateColor = Color.RED;
                            //comResultOut = "请正对设备";
                            Log.e(TAG, "run...: 请正对设备");
                        } else if (mRecognuseState == 3) {
                            //recStateColor = Color.RED;
                            //comResultOut = "请调整距离";
                            Log.e(TAG, "run...: 请调整距离");
                        } else {
                            //comResultOut = "";
                            mHandler.post(() -> {
                                //隐藏识别成功、失败窗口
                                rlDiscernBg.setVisibility(View.GONE);
                            });
                        }
                    } else {
                        //comResultOut = "";
                        mHandler.post(() -> {
                            //隐藏识别成功、失败窗口
                            rlDiscernBg.setVisibility(View.GONE);
                        });
                    }
                    //showResult(recStateColor, comResultOut);
                    isBusyRecognise = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    /**
     * 识别成功
     *
     * @param name   姓名
     * @param bitmap 照片
     */
    private void discernSucceedView(String name, Bitmap bitmap) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                rlDiscernBg.setVisibility(View.VISIBLE);
                rlDiscernBg.setBackgroundResource(R.mipmap.discern_succeed_bg);

                detect_reg_image_item.setImageBitmap(bitmap);

                tvDiscernSucceed.setVisibility(View.VISIBLE);

                imgLine.setVisibility(View.VISIBLE);

                tvName.setVisibility(View.VISIBLE);
                tvName.setText("姓名：" + name);

                tvDiscernFailure.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 识别失败
     */
    private void discernFailureView() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                rlDiscernBg.setVisibility(View.VISIBLE);
                rlDiscernBg.setBackgroundResource(R.mipmap.discern_failure_bg);

                detect_reg_image_item.setImageResource(R.mipmap.discern_failure_bg_1);

                tvDiscernSucceed.setVisibility(View.GONE);

                imgLine.setVisibility(View.GONE);

                tvName.setVisibility(View.GONE);

                tvDiscernFailure.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 延迟3秒发送给后台图片，特征，人名，卡号
     *
     * @param successFailureFlag 识别成功、失败标识。 true 成功 、false 失败
     * @param bgrByteData        输入图像数据
     * @param faceFeatureByte    人脸特征值
     * @param userName           人名
     * @param card               卡号
     */
    private void delaySendData(boolean successFailureFlag, byte[] bgrByteData, byte[] faceFeatureByte, String userName, String card) {
        if (!faceFlag) {
            faceFlag = true;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (successFailureFlag) { //成功
                    //拼接 特征、卡号、人名、照片，字节
                    try {
                        byte[] registerData = Utils.addBytes(
                                //拼接 特征、卡号
                                Utils.concat(faceFeatureByte, Utils.hexString2Bytes(card)),
                                //人名
                                userName.getBytes("GB2312"),
                                //照片
                                bgrByteData
                        );
                        LiveDataBus.get().with("registerData").postValue(registerData);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else { //失败
                    //发送给后台图片、特征值
                    byte[] registerData = Utils.concat(
                            //拼接特征值、卡号字节
                            Utils.concat(faceFeatureByte, cardNumberByte),
                            //拼接人员名称、照片信息字节
                            Utils.concat(personnelNameByte, bgrByteData)
                    );
                    LiveDataBus.get().with("registerData").postValue(registerData);
                }

                faceFlag = false;
            }
        }, 3 * 1000);
    }


    /**
     * 发送串口数据
     */
    @SuppressLint("SetTextI18n")
    private void sendSerialPortData(String userInfo) {
        if (userInfo == null) {
            if (faceTime.containsKey("fail")) {
                Long oldTime = faceTime.get("fail");
                Long newTime = System.currentTimeMillis();
                if (newTime - oldTime > 3000) {
                    faceTime.put("fail", newTime);
                    mHandler.post(() -> {
                        mErrorTextView.setText("" + mErrorNum++);
                        mLiveTextView.setText("" + mLiveNum++);
                        LiveDataBus.get().with("SerialData").setValue(Utils.getRedLightData());
                    });
                    //播放音频
                    mSoundPool.play(soundID.get(1), volume, volume, 0, 0, 1);
                }
            } else {
                Long time = System.currentTimeMillis();
                faceTime.put("fail", time);
                mHandler.post(() -> {
                    mErrorTextView.setText("" + mErrorNum++);
                    mLiveTextView.setText("" + mLiveNum++);
                    LiveDataBus.get().with("SerialData").setValue(Utils.getRedLightData());
                });
                //播放音频
                mSoundPool.play(soundID.get(1), volume, volume, 0, 0, 1);
            }
        } else {
            if (faceTime.containsKey(userInfo)) {
                Long oldTime = faceTime.get(userInfo);
                Long newTime = System.currentTimeMillis();
                if (newTime - oldTime > 3000) {
                    faceTime.put(userInfo, newTime);
//                                    String ids = new BigInteger(id, 10).toString(16);
                    //crc检验过后，拼接得到需要发送的卡号
                    byte[] crcUuid = Utils.getSendId(Utils.hexString2Bytes(Utils.addZero(userInfo)));
                    mHandler.post(() -> {
                        //发送串口数据
                        LiveDataBus.get().with("SerialData").setValue(crcUuid);
                        LiveDataBus.get().with("SerialData").setValue(Utils.getGreenLightData());
                        mLiveTextView.setText("" + mLiveNum++);
                        mAdoptTextView.setText("" + mAdoptNum++);
                    });
                    //播放音频
                    mSoundPool.play(soundID.get(2), volume, volume, 0, 0, 1);
                }
            } else {
                Long time = System.currentTimeMillis();
                faceTime.put(userInfo, time);
//                                String ids = new BigInteger(id, 10).toString(16);
                //crc检验过后，拼接得到需要发送的卡号
                byte[] crcUuid = Utils.getSendId(Utils.hexString2Bytes(Utils.addZero(userInfo)));
                mHandler.post(() -> {
                    mAdoptTextView.setText("" + mAdoptNum++);
                    mLiveTextView.setText("" + mLiveNum++);
                    //发送串口数据
                    LiveDataBus.get().with("SerialData").setValue(crcUuid);
                    LiveDataBus.get().with("SerialData").setValue(Utils.getGreenLightData());
                    //播放音频
                    mSoundPool.play(soundID.get(2), volume, volume, 1, 0, 1);
                });
            }
        }
    }

    /**
     * 声音播报
     */
    @SuppressLint("ObsoleteSdkInt")
    private void initSP() {
        if (Build.VERSION.SDK_INT > 21) {
            SoundPool.Builder builder = new SoundPool.Builder();
            //传入音频数量
            builder.setMaxStreams(1);
            //AudioAttributes是一个封装音频各种属性的方法
            AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
            //设置音频流的合适的属性
            attrBuilder.setLegacyStreamType(AudioManager.STREAM_MUSIC);//STREAM_MUSIC
            //加载一个AudioAttributes
            builder.setAudioAttributes(attrBuilder.build());
            mSoundPool = builder.build();
        } else {
            mSoundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
        }
        soundID.put(1, mSoundPool.load(this, R.raw.unregistered, 1));
        soundID.put(2, mSoundPool.load(this, R.raw.success, 1));
        AudioManager mgr = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        float streamVolumeCurrent = mgr.getStreamVolume(AudioManager.STREAM_MUSIC);
        float streamVolumeMax = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = streamVolumeCurrent / streamVolumeMax;
    }

    private void showResult(final int showColor, final String showMsg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                resultOut.setText(showMsg);
                resultOut.setTextColor(showColor);
            }
        });

    }

    private void showFaceTrackRect(FaceDetResult detectResult) {
        faceView.clear();
        Matrix mat = new Matrix();
        int viewWidth = cameraView.getMeasuredWidth();
        int viewHeight = cameraView.getMeasuredHeight();
        int cameraHeight = manager.getCameraheight();
        int cameraWidth = manager.getCameraWidth();
        float left = 0;
        float top = 0;
        float right = 0;
        float bottom = 0;

        if (ConfigParam.cameraPreviewRotation == 0 && ConfigParam.cameraPreviewScaleWidht == 1.0f && ConfigParam.cameraPreviewScaleHeight == 1.0f) {
            mat.postScale((float) viewWidth / (float) cameraWidth, (float) viewHeight / (float) cameraHeight);
            left = cameraWidth - detectResult.faceRectangle.left;
            top = detectResult.faceRectangle.top;
            right = cameraWidth - detectResult.faceRectangle.right;
            bottom = detectResult.faceRectangle.bottom;
        }
        if (ConfigParam.cameraPreviewRotation == 90 && ConfigParam.cameraPreviewScaleWidht == 1.0f && ConfigParam.cameraPreviewScaleHeight == 1.0f) {
            mat.postScale((float) viewWidth / (float) cameraHeight, (float) viewHeight / (float) cameraWidth);
            left = detectResult.faceRectangle.left;
            top = detectResult.faceRectangle.top;
            right = detectResult.faceRectangle.right;
            bottom = detectResult.faceRectangle.bottom;
        }
        if (ConfigParam.cameraPreviewRotation == 180 && ConfigParam.cameraPreviewScaleWidht == 1.0f && ConfigParam.cameraPreviewScaleHeight == 1.0f) {
            mat.postScale((float) viewWidth / (float) cameraWidth, (float) viewHeight / (float) cameraHeight);
            left = detectResult.faceRectangle.right;
            top = viewHeight - detectResult.faceRectangle.bottom;
            right = detectResult.faceRectangle.left;
            bottom = viewHeight - detectResult.faceRectangle.top;
        }
        if (ConfigParam.cameraPreviewRotation == 270 && ConfigParam.cameraPreviewScaleWidht == 1.0f && ConfigParam.cameraPreviewScaleHeight == 1.0f) {
            mat.postScale((float) viewWidth / (float) cameraHeight, (float) viewHeight / (float) cameraWidth);
            left = detectResult.faceRectangle.right;
            top = detectResult.faceRectangle.bottom;
            right = detectResult.faceRectangle.left;
            bottom = detectResult.faceRectangle.top;
        }
        if (ConfigParam.cameraPreviewRotation == 0 && ConfigParam.cameraPreviewScaleWidht == 1.6f && ConfigParam.cameraPreviewScaleHeight == 1.0f) {
            mat.postScale((float) viewWidth * 0.62f / (float) cameraWidth, (float) viewHeight / (float) cameraHeight);
            left = cameraWidth - detectResult.faceRectangle.left;
            top = detectResult.faceRectangle.top;
            right = cameraWidth - detectResult.faceRectangle.right;
            bottom = detectResult.faceRectangle.bottom;
        }

        RectF drect = new RectF();
        RectF srect = new RectF(left, top, right, bottom);

        mat.mapRect(drect, srect);
        faceView.addRect(drect);
        //faceView.addId(faceIdString.toString());
        faceView.invalidate();
    }

    public Bitmap Bytes2Bitmap(byte[] b) {
        if (b.length != 0) {
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } else {
            return null;
        }
    }

    private void liveDataBus() {
        LiveDataBus.get().with("heart", Boolean.class).observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean heart) {
                if (heart) {
                    mServerStateTextView.setText("在线");
                } else {
                    mServerStateTextView.setText("离线");
                }
            }
        });

        LiveDataBus.get().with("sendNum", Integer.class).observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer num) {
                mSendTextView.setText("" + num);
            }
        });
    }


    public byte[] YuvToNV21(byte[] bgrByteData, short bgrImgWidht, short bgrImgHeight) {
        try {
            //相机预览获取到的data数据不能直接转为bitmap存储，因为该数据是YUV格式的，需要进行数据转换
            YuvImage img = new YuvImage(bgrByteData, ImageFormat.NV21, bgrImgWidht, bgrImgHeight, null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            img.compressToJpeg(new Rect(0, 0, bgrImgWidht, bgrImgHeight), 80, stream);
            stream.close();
            return stream.toByteArray();
        } catch (Exception ex) {
            LogUtils.e("Camera PreviewFrame", "Error:" + ex.getMessage());
            return null;
        }
    }
}

