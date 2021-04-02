package com.bresee.breseefaceapitest.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bresee.breseefaceapitest.activity.SplashActivity;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent thisIntent = new Intent(context, SplashActivity.class);//设置要启动的app
            thisIntent.setAction("android.intent.action.MAIN");
            thisIntent.addCategory("android.intent.category.LAUNCHER");
            thisIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(thisIntent);
        }
    }
}
