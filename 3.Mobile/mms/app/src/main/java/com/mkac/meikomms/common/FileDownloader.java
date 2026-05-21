package com.mkac.meikomms.common;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class FileDownloader {
    private final Context context; // ApplicationContext for general use
    private final Context originalContext; // Original context for permission requests and dialogs
    private final String downloadUrl;
    private final String fileName;
    private final String mimeType;
    private DownloadListener listener;
    private final OkHttpClient client;
    private final ExecutorService executorService; // ExecutorService for async tasks
    private Call downloadCall;
    private int lastProgress = -1;
    private ProgressDialog progressDialog;

    public interface DownloadListener {
        void onDownloadSuccess(String filePath);
        void onDownloadFailed(String error);
        void onProgressUpdate(int progress);
    }

    public FileDownloader(Context context, String downloadUrl, String fileName, String mimeType) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.originalContext = context;
        this.context = context.getApplicationContext();
        this.downloadUrl = downloadUrl;
        this.fileName = sanitizeFileName(fileName);
        this.mimeType = mimeType != null ? mimeType : "application/octet-stream";
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setDownloadListener(DownloadListener listener) {
        this.listener = listener;
    }

    public void startDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(originalContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isApkFile() &&
                !context.getPackageManager().canRequestPackageInstalls()) {
            requestInstallPermission();
            return;
        }

        showProgressDialog();

        Request request = new Request.Builder().url(downloadUrl).build();
        downloadCall = client.newCall(request);
        downloadCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                dismissProgressDialog();
                if (call.isCanceled()) {
                    notifyFailure("Download canceled");
                } else {
                    notifyFailure(e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    dismissProgressDialog();
                    notifyFailure("Server returned HTTP " + response.code());
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    dismissProgressDialog();
                    notifyFailure("Response body is null");
                    return;
                }

                long fileLength = body.contentLength();
                if (fileLength <= 0) {
                    executorService.execute(() -> {
                        if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                            ((Activity) originalContext).runOnUiThread(() -> {
                                if (progressDialog != null && progressDialog.isShowing()) {
                                    progressDialog.setIndeterminate(true);
                                    progressDialog.setMessage("Downloading (size unknown)...");
                                }
                            });
                        }
                    });
                }

                InputStream input = null;
                OutputStream output = null;
                File file = null;
                Uri uri = null;

                try {
                    input = body.byteStream();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) {
                            dismissProgressDialog();
                            notifyFailure("Failed to create MediaStore entry");
                            return;
                        }
                        output = context.getContentResolver().openOutputStream(uri);
                    } else {
                        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!directory.exists() && !directory.mkdirs()) {
                            dismissProgressDialog();
                            notifyFailure("Failed to create Downloads directory");
                            return;
                        }
                        file = new File(directory, fileName);
                        output = new FileOutputStream(file);
                    }

                    if (output == null) {
                        dismissProgressDialog();
                        notifyFailure("Failed to open output stream");
                        return;
                    }

                    byte[] data = new byte[4096];
                    long total = 0;
                    int count;

                    while ((count = input.read(data)) != -1) {
                        if (call.isCanceled()) {
                            if (uri != null) {
                                context.getContentResolver().delete(uri, null, null);
                            } else if (file != null && file.exists()) {
                                file.delete();
                            }
                            dismissProgressDialog();
                            notifyFailure("Download canceled");
                            return;
                        }
                        total += count;
                        output.write(data, 0, count);
                        if (fileLength > 0) {
                            int progress = (int) ((total * 100L) / fileLength);
                            notifyProgress(progress);
                        }
                    }
                    output.flush();

                    String filePath = file != null ? file.getAbsolutePath() : uri != null ? uri.toString() : Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
                    dismissProgressDialog();
                    notifySuccess(filePath);

                    if (isApkFile()) {
                        startApkInstallation(file, uri);
                    }
                } catch (IOException e) {
                    dismissProgressDialog();
                    notifyFailure(e.getMessage());
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException ignored) {}
                    }
                    if (output != null) {
                        try {
                            output.close();
                        } catch (IOException ignored) {}
                    }
                    body.close();
                }
            }
        });
    }

    public void cancelDownload() {
        if (downloadCall != null && !downloadCall.isCanceled()) {
            downloadCall.cancel();
            dismissProgressDialog();
        }
    }

    public void shutdown() {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    private void showProgressDialog() {
        executorService.execute(() -> {
            if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    try {
                        progressDialog = new ProgressDialog(originalContext);
                        progressDialog.setTitle("Downloading");
                        progressDialog.setMessage("Please wait...");
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        progressDialog.setMax(100);
                        progressDialog.setProgress(0);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    } catch (Exception e) {
                        notifyFailure("Failed to show progress dialog: " + e.getMessage());
                    }
                });
            } else {
                notifyFailure("Context must be an Activity and not finishing to show progress dialog.");
            }
        });
    }

    private void dismissProgressDialog() {
        executorService.execute(() -> {
            if (progressDialog != null && progressDialog.isShowing() && originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {}
                    progressDialog = null;
                });
            }
        });
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "download_" + UUID.randomUUID().toString();
        }
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private boolean isApkFile() {
        return mimeType.equals("application/vnd.android.package-archive") || fileName.endsWith(".apk");
    }

    private void startApkInstallation(File file, Uri uri) {
        executorService.execute(() -> {
            if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (file != null) {
                            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } else if (uri != null) {
                            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
                            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                    } else {
                        if (file != null) {
                            installIntent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                        } else if (uri != null) {
                            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
                        }
                    }

                    try {
                        originalContext.startActivity(installIntent);
                    } catch (Exception e) {
                        notifyFailure("Failed to start APK installation: " + e.getMessage());
                    }
                });
            } else {
                notifyFailure("Context must be an Activity and not finishing to start APK installation.");
            }
        });
    }

    private void requestStoragePermission() {
        executorService.execute(() -> {
            if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:" + context.getPackageName()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        originalContext.startActivity(intent);
                        notifyFailure("Please grant storage permission in app settings and retry.");
                    } catch (Exception e) {
                        notifyFailure("Failed to open settings for storage permission: " + e.getMessage());
                    }
                });
            } else {
                notifyFailure("Context must be an Activity and not finishing to request storage permission.");
            }
        });
    }

    private void requestInstallPermission() {
        executorService.execute(() -> {
            if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse("package:" + context.getPackageName()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        originalContext.startActivity(intent);
                        notifyFailure("Please enable 'Install unknown apps' permission in settings and retry.");
                    } catch (Exception e) {
                        notifyFailure("Failed to open settings for install permission: " + e.getMessage());
                    }
                });
            } else {
                notifyFailure("Context must be an Activity and not finishing to request install permission.");
            }
        });
    }

    private void notifySuccess(String filePath) {
        executorService.execute(() -> {
            if (listener != null && originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> listener.onDownloadSuccess(filePath));
            }
        });
    }

    private void notifyFailure(String error) {
        executorService.execute(() -> {
            if (listener != null && originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() ->
                        listener.onDownloadFailed(error != null ? error : "Unknown error"));
            }
        });
    }

    private void notifyProgress(int progress) {
        executorService.execute(() -> {
            if (originalContext instanceof Activity && !((Activity) originalContext).isFinishing()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onProgressUpdate(progress);
                    }
                    if (progressDialog != null && progressDialog.isShowing() && !progressDialog.isIndeterminate()) {
                        progressDialog.setProgress(progress);
                    }
                });
            }
        });
    }
}