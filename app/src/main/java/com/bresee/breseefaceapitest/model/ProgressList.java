package com.bresee.breseefaceapitest.model;

public class ProgressList {
    /**
     * 进度条标识
     */
    private boolean isProgress;
    /**
     * 人脸库数量
     */
    private int faceLibNum;
    /**
     * 成功数量
     */
    private int success;
    /**
     * 失败数量
     */
    private int unsuccess;

    public boolean isProgress() {
        return isProgress;
    }

    public void setProgress(boolean progress) {
        isProgress = progress;
    }

    public int getFaceLibNum() {
        return faceLibNum;
    }

    public void setFaceLibNum(int faceLibNum) {
        this.faceLibNum = faceLibNum;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getUnsuccess() {
        return unsuccess;
    }

    public void setUnsuccess(int unsuccess) {
        this.unsuccess = unsuccess;
    }
}
