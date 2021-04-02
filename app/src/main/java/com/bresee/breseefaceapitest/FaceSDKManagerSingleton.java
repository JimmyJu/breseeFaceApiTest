package com.bresee.breseefaceapitest;

import com.bresee.breseefacelib.FaceSDKManager;

public class FaceSDKManagerSingleton {

    private static class SingletonClassInstance {
        private static final FaceSDKManager instance = new FaceSDKManager();
    }

    public static FaceSDKManager getInstance() {
        return SingletonClassInstance.instance;
    }
}
