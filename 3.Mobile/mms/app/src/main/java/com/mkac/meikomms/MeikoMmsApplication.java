package com.mkac.meikomms;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import com.mkac.meikomms.common.LanguageAPIUtils;

public class MeikoMmsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LanguageAPIUtils.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                LanguageAPIUtils.init(activity);
                View rootView = activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    LanguageAPIUtils.setLang(rootView);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
