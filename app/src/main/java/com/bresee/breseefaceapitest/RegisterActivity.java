package com.bresee.breseefaceapitest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;

import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bresee.breseefaceapitest.camera.CameraManager;
import com.bresee.breseefaceapitest.camera.CameraPreview;
import com.bresee.breseefaceapitest.camera.CameraPreviewData;
import com.bresee.breseefaceapitest.camera.ComplexFrameHelper;
import com.bresee.breseefacelib.FaceConfigSetting;
import com.bresee.breseefacelib.FaceDetResult;
import com.bresee.breseefacelib.FaceExtrResult;
import com.bresee.breseefacelib.FaceSDKManager;
import com.bresee.breseefacelib.InputImageParam;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_180;
import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_270;
import static com.bresee.breseefaceapitest.ActivityHelper.NV21_rotate_to_90;
import static com.bresee.breseefaceapitest.ActivityHelper.getAllFileName;
import static com.bresee.breseefaceapitest.ActivityHelper.getBmpFromFilePath;
import static com.bresee.breseefaceapitest.ActivityHelper.getRGBByteFromBitmap;

public class RegisterActivity extends Activity implements CameraManager.CameraListener {
    public final String TAG = "RegisterActivity";

    private FaceSDKManager faceSDKManager;
    private FaceConfigSetting faceConfigSetting;
    private PointerByReference faceDetHandle;
    private PointerByReference faceExtrHandle;
    private InputImageParam inputFdImageParam;
    private InputImageParam inputFrImageParam;
    private FaceDetResult faceDetResult;
    private FaceDetResult faceRegInput;
    private FaceExtrResult faceExtrResult;

    private CameraManager manager_r;
    private CameraPreview cameraView_r;
    private FaceView faceView_r;
    private Handler mHandler;
    private TextView resultOut;
    private VedioRegisterThread mVedioRegisterThread;
    private ArrayBlockingQueue<ActivityHelper.RecognizeData> mRecognizeDataQueue;
    private ArrayBlockingQueue<CameraPreviewData> mDetFrameQueue;

    private String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
    List<String> mPermissionList = new ArrayList<>();
    private static final int REQUEST_ALL_PERMISSION = 11;

    private int FrameSeqNum = 0;
    private int inDbFaceNum = 0;//????????????
    private int angleFailNum = 0;//???????????????
    private int qualityFailNum = 0;//???????????????

    private String dbResultOut = "";
    private int recStateColor = 0;
    private String faceIdInfo = "";
    private boolean vedioRegisterButton = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        mRecognizeDataQueue = new ArrayBlockingQueue<>(1);
        mDetFrameQueue = new ArrayBlockingQueue<>(1);
        mHandler = new Handler();

        initView();
        checkPermission();
        sdkInit();

        mVedioRegisterThread = new VedioRegisterThread();
        mVedioRegisterThread.start();
    }

    @Override
    protected void onResume() {
        manager_r.open(getWindowManager(), false, ConfigParam.cameraWidth, ConfigParam.cameraHeight);//?????????
        super.onResume();
    }

    @Override
    protected void onStop() {
        mRecognizeDataQueue.clear();
        if (manager_r != null) {
            manager_r.release();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mVedioRegisterThread.isInterrupt = true;
        mVedioRegisterThread.interrupt();
        if (manager_r != null) {
            manager_r.release();
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
        sdkDestroy();
    }

    @Override
    protected void onRestart() {
        faceView_r.clear();
        faceView_r.invalidate();
        super.onRestart();
    }

    @Override
    public void onPictureTaken(CameraPreviewData cameraPreviewData) {
        mDetFrameQueue.offer(cameraPreviewData);
        //Log.e(TAG, "###feedframe");
    }

    public void onBackPressed(String imgRegisterResult) {
        new AlertDialog.Builder(this)
                .setTitle("????????????")
                .setMessage(imgRegisterResult)
                //.setNegativeButton( "??????",null )
                .setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.exit(0);
                    }
                })
                .show();
    }

    public void alertEdit(View view) {
        final EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle("???????????????ID")
                .setIcon(android.R.drawable.sym_def_app_icon)
                .setView(et)
                .setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        faceIdInfo = et.getText().toString();
                    }
                }).setNegativeButton("??????", null).show();
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

    /**
     * SDK?????????.
     */
    private void sdkInit() {
        int codeReturn;
        faceConfigSetting = new FaceConfigSetting();
        faceConfigSetting.qualityButton = ConfigParam.qualityButton;
        faceConfigSetting.angleButton = ConfigParam.angleButton;
        faceConfigSetting.livenessButton = ConfigParam.liveButton;
        faceConfigSetting.livenessLevel = ConfigParam.liveLevel;
        faceConfigSetting.extractButton = ConfigParam.extraButton;
        faceConfigSetting.faceLibNmame = ConfigParam.faceLibName;

        faceDetHandle = new PointerByReference(Pointer.NULL);
        faceExtrHandle = new PointerByReference(Pointer.NULL);
        inputFdImageParam = new InputImageParam();
        inputFrImageParam = new InputImageParam();
        faceDetResult = new FaceDetResult();
        faceRegInput = new FaceDetResult();
        faceExtrResult = new FaceExtrResult();

        //SDK?????????
        codeReturn = faceSDKManager.isfFaceInit(ConfigParam.workBasePath, faceConfigSetting, faceDetHandle, faceExtrHandle);
        Log.e(TAG, "###IsfFaceInit :" + codeReturn);
    }

    /**
     * SDK??????.
     */
    private void sdkDestroy() {
        //???????????????
        int iret = faceSDKManager.isfFaceDestroy(faceDetHandle, faceExtrHandle);
        Log.e(TAG, "###IsfDestroy down :" + iret);
    }

    /**
     * ?????????????????????????????????.
     */
    private void sdkImageRegister() {
        int inDbImgFaceNum = 0;//????????????
        int angleFailImgNum = 0;//???????????????
        int qualityFailImgNum = 0;//???????????????
        int imageSeqNum = 0;

        ArrayList<String> fileNameList = new ArrayList<String>();
        int allFileNum = getAllFileName(ConfigParam.registerImgPath, fileNameList);
        for (String fileName : fileNameList) {
            imageSeqNum++;
            String imagePath = ConfigParam.registerImgPath + fileName;
            Bitmap inputBmp = getBmpFromFilePath(imagePath);
            if (inputBmp != null) {
                inputFdImageParam.bgrImgWidht = (short) inputBmp.getWidth();
                inputFdImageParam.bgrImgHeight = (short) inputBmp.getHeight();
                inputFdImageParam.bgrImgStride = (short) inputBmp.getWidth();
                inputFdImageParam.bgrImgStyle = 0;
                inputFdImageParam.bgrImgSeq = imageSeqNum;
                inputFdImageParam.bgrByteData = getRGBByteFromBitmap(inputBmp);
                inputFdImageParam.riBgrByteData = null;
                int mDetectState = faceSDKManager.isfFaceDetector(inputFdImageParam, faceDetResult, faceDetHandle);
                Log.e(TAG, "###ISF_FD_InterfaceWork down :" + mDetectState);

                String faceIdInfo = imagePath.substring(imagePath.lastIndexOf("/") + 1);
                faceIdInfo = faceIdInfo.substring(0, faceIdInfo.lastIndexOf("."));
                if (mDetectState == 0 && faceDetResult.detTargetNum > 0) {
                    Log.e(TAG, "###faceDetResult.qualityScore down :" + faceDetResult.qualityScore);
                    if ((faceDetResult.qualityScore > ConfigParam.qualityThreshold) || (0 == ConfigParam.qualityButton)) {//????????????
                        if (faceDetResult.angleScore > ConfigParam.angleThreshold) {//????????????
                            //????????????
                            faceDetResult.extractPattern = 0;
                            int mRecognuseState = faceSDKManager.isfFaceExtractor(inputFdImageParam, faceDetResult, faceExtrResult, faceExtrHandle);
                            Log.e(TAG, "###isfFaceExtractor down :" + mRecognuseState);

                            //???????????????????????????
                            if ((null != faceExtrResult) && (0 == mRecognuseState)) {
                                inDbImgFaceNum++;
                                int mFeatureFileState = faceSDKManager.isfSaveFaceFeature(ConfigParam.faceLibFile, faceExtrResult, faceIdInfo);
                                if (mFeatureFileState == 0) {
                                    Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                                } else {
                                    Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                                }
                            } else {
                                Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                            }
                        } else {
                            angleFailImgNum++;
                            Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                        }
                    } else {
                        qualityFailImgNum++;
                        Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                    }
                } else {
                    Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                }
            }

            //??????????????????
            if (imageSeqNum > 4) break;
        }
        String outProcessResult = "????????????????????? 5 ??????" + " \n?????????????????????" + inDbImgFaceNum +
                " \n?????????????????????" + (5 - inDbImgFaceNum) + " \n?????????????????????" + angleFailImgNum +
                " \n?????????????????????" + qualityFailImgNum;
        Log.e(TAG, "###???????????????" + outProcessResult);
        onBackPressed(outProcessResult);
    }

    /**
     * ???????????????.
     */
    private void initView() {
        //????????????????????????
        final int mCurrentOrientation = getResources().getConfiguration().orientation;
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.e(TAG, "###screenState-??????");
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.e(TAG, "###screenState-??????");
        }

        //??????????????????
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        Log.e(TAG, "###displayMetrics height :" + displayMetrics.heightPixels);
        Log.e(TAG, "###displayMetrics width :" + displayMetrics.widthPixels);

        setContentView(R.layout.activity_register);

        faceSDKManager = FaceSDKManagerSingleton.getInstance();

        /* ??????????????? */
        resultOut = findViewById(R.id.result);
        faceView_r = (FaceView) this.findViewById(R.id.fcview_r);
        cameraView_r = (CameraPreview) findViewById(R.id.preview_r);
        findViewById(R.id.regJpg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sdkImageRegister();
            }
        });
        findViewById(R.id.regVedio).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vedioRegisterButton = true;
                alertEdit(v);
            }
        });
        findViewById(R.id.clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sdkClearLibray();
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });

        manager_r = new CameraManager();
        manager_r.setPreviewDisplay(cameraView_r);

        manager_r.setListener(this);
    }

    /**
     * ???????????????.
     */
    private void sdkClearLibray() {
        int clearFaceDbState = faceSDKManager.isfClearFeatureLibrary(ConfigParam.faceLibFile);
        Log.e(TAG, "###sdkClearLibray down :" + clearFaceDbState);
        if (clearFaceDbState == 0) {
            faceView_r.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RegisterActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            faceView_r.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RegisterActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private class VedioRegisterThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                CameraPreviewData cameraPreviewData = null;
                try {
                    cameraPreviewData = mDetFrameQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (faceSDKManager == null || inputFdImageParam == null) {
                    continue;
                }

                /*??????????????????*/
                if (ConfigParam.cameraFrameRotation == 0) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrByteData = cameraPreviewData.nv21Data;
                } else if (ConfigParam.cameraFrameRotation == 90) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_90(cameraPreviewData.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                } else if (ConfigParam.cameraFrameRotation == 180) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_180(cameraPreviewData.nv21Data, inputFdImageParam.bgrImgWidht, inputFdImageParam.bgrImgHeight);
                } else if (ConfigParam.cameraFrameRotation == 270) {
                    inputFdImageParam.bgrImgWidht = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrImgHeight = (short) cameraPreviewData.width;
                    inputFdImageParam.bgrImgStride = (short) cameraPreviewData.height;
                    inputFdImageParam.bgrByteData = NV21_rotate_to_270(cameraPreviewData.nv21Data, inputFdImageParam.bgrImgHeight, inputFdImageParam.bgrImgWidht);
                }
                inputFdImageParam.bgrImgStyle = 1;
                inputFdImageParam.bgrImgSeq = ++FrameSeqNum;
                inputFdImageParam.riBgrByteData = null;

                //saveYuvImage(cameraPreviewData.nv21Data,cameraPreviewData.width, cameraPreviewData.height);
                int mDetectState = faceSDKManager.isfFaceDetector(inputFdImageParam, faceDetResult, faceDetHandle);
                //Log.e(TAG, "###isfFaceDetector :" +mDetectState);
                if (mDetectState != 0 || inputFdImageParam.bgrByteData == null || faceDetResult == null ||
                        faceDetResult.detTargetNum == 0) {
                    /* ??????????????????????????? */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            faceView_r.clear();
                            faceView_r.invalidate();
                        }
                    });
                    showRegResult(0, "");

                    Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                } else {
                    /* ?????????????????????????????????????????????*/
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFaceTrackRect(faceDetResult);
                        }
                    });

                    if (vedioRegisterButton) {
                        if ((faceDetResult.qualityScore > ConfigParam.qualityThreshold) || (0 == ConfigParam.qualityButton)) {//????????????
                            if (faceDetResult.angleScore > ConfigParam.angleThreshold) {//????????????
                                //????????????
                                faceDetResult.extractPattern = 0;
                                int mRecognuseState = faceSDKManager.isfFaceExtractor(inputFdImageParam, faceDetResult, faceExtrResult, faceExtrHandle);
                                Log.e(TAG, "###isfFaceExtractor down :" + mRecognuseState);

                                //???????????????????????????
                                if ((null != faceExtrResult) && (0 == mRecognuseState)) {
                                    inDbFaceNum++;
                                    int mFeatureFileState = faceSDKManager.isfSaveFaceFeature(ConfigParam.faceLibFile, faceExtrResult, faceIdInfo);
                                    if (mFeatureFileState == 0) {
                                        dbResultOut = faceIdInfo + " ????????????";
                                        recStateColor = Color.GREEN;
                                        vedioRegisterButton = false;
                                        Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                                    } else {
                                        dbResultOut = faceIdInfo + " ??????????????????";
                                        recStateColor = Color.RED;
                                        vedioRegisterButton = true;
                                        Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                                    }
                                } else {
                                    dbResultOut = faceIdInfo + " ??????????????????";
                                    recStateColor = Color.RED;
                                    vedioRegisterButton = true;
                                    Log.e(TAG, "########" + faceIdInfo + " ??????????????????########");
                                }
                            } else {
                                angleFailNum++;
                                dbResultOut = faceIdInfo + " ????????????";
                                recStateColor = Color.RED;
                                vedioRegisterButton = true;
                                Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                            }
                        } else {
                            qualityFailNum++;
                            dbResultOut = faceIdInfo + " ????????????";
                            recStateColor = Color.RED;
                            vedioRegisterButton = true;
                            Log.e(TAG, "########" + faceIdInfo + " ????????????########");
                        }
                    }
                }
                showRegResult(recStateColor, dbResultOut);
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    private void showRegResult(final int showColor, final String showMsg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                resultOut.setText(showMsg);
                resultOut.setTextColor(showColor);
            }
        });

    }

    private void showFaceTrackRect(FaceDetResult detectResult) {
        faceView_r.clear();
        Matrix mat = new Matrix();
        int viewWidth = cameraView_r.getMeasuredWidth();
        int viewHeight = cameraView_r.getMeasuredHeight();
        int cameraHeight = manager_r.getCameraheight();
        int cameraWidth = manager_r.getCameraWidth();
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
        faceView_r.addRect(drect);
        //faceView.addId(faceIdString.toString());
        faceView_r.invalidate();
    }
}

