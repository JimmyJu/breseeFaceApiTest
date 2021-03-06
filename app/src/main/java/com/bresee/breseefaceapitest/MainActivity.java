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

    //?????????????????????????????????????????????
    private int faceTrackIdSubTmp = 0;
    //private int faceGlobalTrackIdTmp=0;
    private int initReturn = 0;

    /**
     * ??????
     */
    private final byte[] cardNumberByte = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    /**
     * ????????????
     */
    private final byte[] personnelNameByte = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    /**
     * ????????????????????????
     */
    private boolean faceFlag = false;
    /**
     * ???????????????
     */
    private final byte[] imageHead = new byte[]{(byte) 0xFF, (byte) 0xD8};
    /**
     * ???????????????
     */
    private final byte[] imageEnd = new byte[]{(byte) 0xFF, (byte) 0xD9};
    /**
     * ??????????????????
     */
    private final Hashtable<String, byte[]> faceImage = new Hashtable<>();
    /**
     * ?????????????????????view
     */
    private RelativeLayout rlDiscernBg;
    private ImageView detect_reg_image_item;
    private TextView tvDiscernSucceed;
    private ImageView imgLine;
    private TextView tvName;
    private TextView tvDiscernFailure;

    /**
     * ????????????View
     */
    private TextView mLiveTextView, mAdoptTextView, mErrorTextView, mSendTextView, mServerStateTextView;

    private Bitmap mRBmp;

    /**
     * ?????????????????????
     */
    private final Hashtable<String, Long> faceTime = new Hashtable<>();
    private int mLiveNum, mAdoptNum, mErrorNum;
    /**
     * ??????mp3
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
        //????????????
        initSP();

        checkPermission();

        sdkAllFlow();

        liveDataBus();
    }

    @Override
    protected void onResume() {
        if (ConfigParam.liveButton == 1 && ConfigParam.liveLevel == 1) {
            manager_ir.open(getWindowManager(), true, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//??????
            manager.open(getWindowManager(), false, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//?????????
        } else {
            manager.open(getWindowManager(), true, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//?????????
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
        new AlertDialog.Builder(this).setTitle("???????????????ID")
                .setIcon(android.R.drawable.sym_def_app_icon)
                .setView(et)
                .setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String deleteFaceId = et.getText().toString();
                        sdkDeleteFace(deleteFaceId);
                    }
                }).setNegativeButton("??????", null).show();
    }

    /**
     * ??????SDK???????????????.
     */
    private void sdkAllFlow() {
        if (onlineLicenseButton == 1) {
            if (sdkOnlineLicense())//???????????????????????????????????????
            {
                sdkVersion();
                sdkLoadLibray();
                sdkInit();

                mRecognizeThread = new RecognizeThread();
                mRecognizeThread.start();
                mDetectThread = new DetectThread();
                mDetectThread.start();
            } else {
                Log.e(TAG, "###SDK??????????????????,?????????????????????!!");
            }
        } else {//????????????
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
     * ???????????????.
     */
    private void sdkDeleteFace(String faceInfo) {
        int deleteFaceDbState = faceSDKManager.isfDeleteFaceFeature(ConfigParam.faceLibName, faceInfo, ConfigParam.faceLibFile);
        Log.e(TAG, "###sdkClearLibray down :" + deleteFaceDbState);
        final String dbNameId = faceInfo;
        faceView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, dbNameId + "????????????", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * ?????????????????????.
     */
    private void initView() {
        //????????????????????????
        final int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "###screenState-??????");
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "###screenState-??????");
        }

        //??????????????????
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        Log.i(TAG, "###displayMetrics height :" + displayMetrics.heightPixels);
        Log.i(TAG, "###displayMetrics width :" + displayMetrics.widthPixels);

        setContentView(R.layout.activity_main);

        faceSDKManager = FaceSDKManagerSingleton.getInstance();

        /* ??????????????? */
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

        /* ??????????????????????????? */
        manager.setListener(new CameraManager.CameraListener() {
            @Override
            public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                ComplexFrameHelper.addRgbFrameAndMakeComplex(cameraPreviewData);
            }
        });
        /* ???????????????????????? */
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
     * ?????????????????????.
     */
    private void initViewSimple() {
        //????????????????????????
        final int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "###screenState-??????");
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "###screenState-??????");
        }

        //??????????????????
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        Log.i(TAG, "###displayMetrics height :" + displayMetrics.heightPixels);
        Log.i(TAG, "###displayMetrics width :" + displayMetrics.widthPixels);

        setContentView(R.layout.activity_main);

        faceSDKManager = FaceSDKManagerSingleton.getInstance();

        /* ??????????????? */
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

        /* ??????????????????????????? */
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
     * SDK????????????.
     */
    private boolean sdkOnlineLicense() {
        //SDK????????????(????????????????????????????????????)
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
     * ??????SDK??????.
     */
    private void sdkVersion() {
        VersionInfos versionInfos = new VersionInfos();
        int codeReturn = faceSDKManager.isfGetVersion(versionInfos);
        Log.e(TAG, "###sdkVersion down :" + codeReturn);
        version.setText(versionInfos.version);
    }

    /**
     * ???????????????.
     */
    private void sdkLoadLibray() {
        //???????????????
        String featureLibName = ConfigParam.faceLibName;
        int loadFaceDbState = faceSDKManager.isfLoadFeatureLibrary(featureLibName, ConfigParam.faceLibFile);
        Log.e(TAG, "###sdkLoadLibray :" + loadFaceDbState);
    }

    /**
     * SDK?????????.
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
                Toast.makeText(MainActivity.this, "?????????????????????", Toast.LENGTH_LONG).show();
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

        //SDK?????????
        initReturn = faceSDKManager.isfFaceInit(ConfigParam.workBasePath, faceConfigSetting, faceDetHandle, faceExtrHandle);
        Log.e(TAG, "###IsfFaceInit :" + initReturn);
    }

    /**
     * SDK??????.
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
                    //saveYuvImage(cameraPreviewData.first.nv21Data,cameraPreviewData.first.width, cameraPreviewData.first.height);//??????NV21??????
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (faceSDKManager == null || inputFdImageParam == null) {
                    continue;
                }

                /*??????????????????*/
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

                //saveYuvImage(cameraPreviewData.first.nv21Data,cameraPreviewData.first.width, cameraPreviewData.first.height);//??????NV21??????
                int mDetectState = faceSDKManager.isfFaceDetector(inputFdImageParam, faceDetResult, faceDetHandle);
                //Log.i(TAG, "###isfFaceDetector :" +mDetectState);

                /*????????????*/
                boolean isDetectReport = true;
                if (faceTrackIdSubTmp != faceDetResult.faceTrackId) {
                    isDetectReport = false;
                }
                faceTrackIdSubTmp = faceDetResult.faceTrackId;

                if (mDetectState != 0 || inputFdImageParam.bgrByteData == null || faceDetResult == null ||
                        faceDetResult.detTargetNum == 0) {

                    /* ??????????????? */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            faceView.clear();
                            faceView.invalidate();
                        }
                    });
                    showResult(0, "");

                    //license????????????
                    if (initReturn == 7) {
                        showResult(Color.RED, "??????????????????");
                    }
                } else {
                    if (!isBusyRecognise && isDetectReport) {
                        //?????????????????????
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

                    /* ?????????????????????????????????????????????*/
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFaceTrackRect(faceDetResult);
                        }
                    });

                    //??????????????????
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
                    /**??????????????????*/
                    int mRecognuseState = faceSDKManager.isfFaceExtractor(recognizeData.inputFrImageParamData, recognizeData.faceRegInputData, faceExtrResult, faceExtrHandle);
                    Log.i(TAG, "###isfFaceExtractor down :" + mRecognuseState);
                    Log.i(TAG, "###?????????????????? :" + faceExtrResult.maskResult);/**???????????? 0-?????? 1-??????*/

                    if (faceTrackIdSubTmp == faceTrackIdReg) {
                        if ((0 == mRecognuseState) && (1 == ConfigParam.extraButton)) {
                            /**???????????????*/
                            int mCompareState = faceSDKManager.isfGetTopOfFaceLib(ConfigParam.faceLibName, faceExtrResult, faceCompareResult);
                            Log.i(TAG, "###IsfGetTopOfFaceLib down :" + mCompareState);

                            if ((null != faceCompareResult) && (mCompareState == 0)) {
                                if (faceCompareResult.compareSimilarScore > ConfigParam.recogniseThreshold) {
                                    //comResultOut = "????????????\n" + new String(faceCompareResult.compareFaceInfo);
                                    //recStateColor = Color.GREEN;
                                    Log.i(TAG, "###???????????? :" + faceCompareResult.compareSimilarScore);

                                    String info = new String(faceCompareResult.compareFaceInfo);
                                    //?????????????????????
                                    String userInfo = info.replaceAll(reg, "");
                                    //???????????????
                                    Matcher m = p.matcher(info);
                                    byte[] image = YuvToNV21(
                                            recognizeData.inputFrImageParamData.bgrByteData,
                                            recognizeData.inputFrImageParamData.bgrImgWidht,
                                            recognizeData.inputFrImageParamData.bgrImgHeight);
                                    //??????3???????????????????????????????????????????????????
                                    delaySendData(true, image, faceExtrResult.faceFeatureByte, userInfo, m.replaceAll("").trim());
                                    if (faceImage.containsKey(userInfo)) {
                                        //????????????
                                        discernSucceedView(userInfo, Bytes2Bitmap(faceImage.get(userInfo)));
                                    } else {
                                        faceImage.put(userInfo, image);
                                        //????????????
                                        discernSucceedView(userInfo, Bytes2Bitmap(image));
                                    }
                                    //??????????????????
                                    sendSerialPortData(m.replaceAll("").trim());
                                } else {
                                    //recStateColor = Color.RED;
                                    //comResultOut = "??????????????????";
                                    Log.e(TAG, "###??????????????? :" + faceCompareResult.compareSimilarScore);

                                    byte[] image = YuvToNV21(
                                            recognizeData.inputFrImageParamData.bgrByteData,
                                            recognizeData.inputFrImageParamData.bgrImgWidht,
                                            recognizeData.inputFrImageParamData.bgrImgHeight);
                                    //????????????
                                    discernFailureView();
                                    //??????3????????????????????????????????????
                                    byte[] imageData = Utils.addBytes(imageHead, image, imageEnd);
                                    delaySendData(false, imageData, faceExtrResult.faceFeatureByte, null, null);
                                    //??????????????????
                                    sendSerialPortData(null);
                                }
                            } else {
                                //comResultOut = "";
                                byte[] image = YuvToNV21(
                                        recognizeData.inputFrImageParamData.bgrByteData,
                                        recognizeData.inputFrImageParamData.bgrImgWidht,
                                        recognizeData.inputFrImageParamData.bgrImgHeight);
                                //????????????
                                discernFailureView();
                                //??????3????????????????????????????????????
                                byte[] imageData = Utils.addBytes(imageHead, image, imageEnd);
                                delaySendData(false, imageData, faceExtrResult.faceFeatureByte, null, null);
                                //??????????????????
                                sendSerialPortData(null);
                            }
                        } else if (ConfigParam.maskButton == 2 && mRecognuseState == 1) {
                            //recStateColor = Color.RED;
                            //comResultOut = "???????????????";
                            Log.e(TAG, "run...: ???????????????");
                        } else if (mRecognuseState == 2) {
                            //recStateColor = Color.RED;
                            //comResultOut = "???????????????";
                            Log.e(TAG, "run...: ???????????????");
                        } else if (mRecognuseState == 3) {
                            //recStateColor = Color.RED;
                            //comResultOut = "???????????????";
                            Log.e(TAG, "run...: ???????????????");
                        } else {
                            //comResultOut = "";
                            mHandler.post(() -> {
                                //?????????????????????????????????
                                rlDiscernBg.setVisibility(View.GONE);
                            });
                        }
                    } else {
                        //comResultOut = "";
                        mHandler.post(() -> {
                            //?????????????????????????????????
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
     * ????????????
     *
     * @param name   ??????
     * @param bitmap ??????
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
                tvName.setText("?????????" + name);

                tvDiscernFailure.setVisibility(View.GONE);
            }
        });
    }

    /**
     * ????????????
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
     * ??????3???????????????????????????????????????????????????
     *
     * @param successFailureFlag ?????????????????????????????? true ?????? ???false ??????
     * @param bgrByteData        ??????????????????
     * @param faceFeatureByte    ???????????????
     * @param userName           ??????
     * @param card               ??????
     */
    private void delaySendData(boolean successFailureFlag, byte[] bgrByteData, byte[] faceFeatureByte, String userName, String card) {
        if (!faceFlag) {
            faceFlag = true;
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (successFailureFlag) { //??????
                    //?????? ??????????????????????????????????????????
                    try {
                        byte[] registerData = Utils.addBytes(
                                //?????? ???????????????
                                Utils.concat(faceFeatureByte, Utils.hexString2Bytes(card)),
                                //??????
                                userName.getBytes("GB2312"),
                                //??????
                                bgrByteData
                        );
                        LiveDataBus.get().with("registerData").postValue(registerData);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else { //??????
                    //?????????????????????????????????
                    byte[] registerData = Utils.concat(
                            //??????????????????????????????
                            Utils.concat(faceFeatureByte, cardNumberByte),
                            //???????????????????????????????????????
                            Utils.concat(personnelNameByte, bgrByteData)
                    );
                    LiveDataBus.get().with("registerData").postValue(registerData);
                }

                faceFlag = false;
            }
        }, 3 * 1000);
    }


    /**
     * ??????????????????
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
                    //????????????
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
                //????????????
                mSoundPool.play(soundID.get(1), volume, volume, 0, 0, 1);
            }
        } else {
            if (faceTime.containsKey(userInfo)) {
                Long oldTime = faceTime.get(userInfo);
                Long newTime = System.currentTimeMillis();
                if (newTime - oldTime > 3000) {
                    faceTime.put(userInfo, newTime);
//                                    String ids = new BigInteger(id, 10).toString(16);
                    //crc????????????????????????????????????????????????
                    byte[] crcUuid = Utils.getSendId(Utils.hexString2Bytes(Utils.addZero(userInfo)));
                    mHandler.post(() -> {
                        //??????????????????
                        LiveDataBus.get().with("SerialData").setValue(crcUuid);
                        LiveDataBus.get().with("SerialData").setValue(Utils.getGreenLightData());
                        mLiveTextView.setText("" + mLiveNum++);
                        mAdoptTextView.setText("" + mAdoptNum++);
                    });
                    //????????????
                    mSoundPool.play(soundID.get(2), volume, volume, 0, 0, 1);
                }
            } else {
                Long time = System.currentTimeMillis();
                faceTime.put(userInfo, time);
//                                String ids = new BigInteger(id, 10).toString(16);
                //crc????????????????????????????????????????????????
                byte[] crcUuid = Utils.getSendId(Utils.hexString2Bytes(Utils.addZero(userInfo)));
                mHandler.post(() -> {
                    mAdoptTextView.setText("" + mAdoptNum++);
                    mLiveTextView.setText("" + mLiveNum++);
                    //??????????????????
                    LiveDataBus.get().with("SerialData").setValue(crcUuid);
                    LiveDataBus.get().with("SerialData").setValue(Utils.getGreenLightData());
                    //????????????
                    mSoundPool.play(soundID.get(2), volume, volume, 1, 0, 1);
                });
            }
        }
    }

    /**
     * ????????????
     */
    @SuppressLint("ObsoleteSdkInt")
    private void initSP() {
        if (Build.VERSION.SDK_INT > 21) {
            SoundPool.Builder builder = new SoundPool.Builder();
            //??????????????????
            builder.setMaxStreams(1);
            //AudioAttributes??????????????????????????????????????????
            AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
            //?????????????????????????????????
            attrBuilder.setLegacyStreamType(AudioManager.STREAM_MUSIC);//STREAM_MUSIC
            //????????????AudioAttributes
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
                    mServerStateTextView.setText("??????");
                } else {
                    mServerStateTextView.setText("??????");
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
            //????????????????????????data????????????????????????bitmap???????????????????????????YUV????????????????????????????????????
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

