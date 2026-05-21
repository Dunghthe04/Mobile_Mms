package com.mkac.meikomms.common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import static com.mkac.meikomms.common.GlobalDialogManager.checkAndShowDialogIfDisconnected;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import java.net.MalformedURLException;

public class ConnectionMonitorService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int interval = 10000;
    private String server_url = "";
    private boolean wasConnected = true;
    private final Context context;
    ConfigManager configManager;
    public ConnectionMonitorService(Context context) {
        this.context = context.getApplicationContext();
        configManager = new ConfigManager(context);
        server_url = configManager.getProperty("server_url")+"/api/v1/mms_mobile/ping";
    }

    private final Runnable checker = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                boolean isConnected = ApiConnectionChecker.isApiReachable(server_url, 10000);

                if (!isConnected && wasConnected) {
                    GlobalDialogManager.show(
                            context,
                            i18n("CONNECTION ERROR"),
                            i18n("Network connection problem or server not responding."),
                            () -> {
                                try {
                                    checkAndShowDialogIfDisconnected(context, server_url);
                                } catch (MalformedURLException e) {
                                    throw new RuntimeException(e);
                                }
                            } // callback kiểm tra lại khi nhấn OK
                    );
                } else if (isConnected && !wasConnected) {
                    GlobalDialogManager.hide();
                }
                wasConnected = isConnected;
                handler.postDelayed(this, interval);
            }).start();
        }
    };

    public void start() {
        handler.post(checker);
    }

    public void stop() {
        handler.removeCallbacks(checker);
        GlobalDialogManager.hide();
    }
}
