package com.mkac.meikomms.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import com.mkac.meikomms.R;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

public class GlobalDialogManager {
    private static AlertDialog dialog;

    public static void show(Context context, String title, String message, Runnable checkAgain) {
        if (dialog != null && dialog.isShowing()) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getBaseContextActivity(context));
            builder.setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", (d, which) -> {
                        dialog.dismiss();
                        dialog = null;

                        // Gọi lại hàm kiểm tra kết nối sau khi người dùng nhấn OK
                        if (checkAgain != null) {
                            checkAgain.run();
                        }
                    });

            dialog = builder.create();

            if (!(context instanceof Activity)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (Settings.canDrawOverlays(context)) {
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    } else {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                } else {
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                }
            }

            dialog.show();
        });
    }



    public static void checkAndShowDialogIfDisconnected(Context context,String urlString) throws MalformedURLException {

        URL url = new URL(urlString);
        String host = url.getHost(); // Lấy IP hoặc domain
        int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort(); // nếu không có thì dùng port mặc định

        new Thread(() -> {
            boolean isConnected = isServerReachable(host, port, 3000); // IP & Port thật của bạn

            if (!isConnected) {
                show(context, i18n("CONNECTION ERROR"), i18n("Network connection problem or server not responding."), () -> {
                    // Khi người dùng nhấn OK, gọi lại kiểm tra
                    try {
                        checkAndShowDialogIfDisconnected(context,urlString);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }).start();
    }

    public static boolean isServerReachable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public static void hide() {
        if (dialog != null && dialog.isShowing()) {
            new Handler(Looper.getMainLooper()).post(() -> dialog.dismiss());
        }
    }

    private static Context getBaseContextActivity(Context context) {
        if (context instanceof Activity) return context;
        return new ContextThemeWrapper(context, R.style.TransparentDialogTheme);
    }
}
