package com.mkac.meikomms.common;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class FileDownloadInFragment
{


    private final Context context; // ApplicationContext for general use
    private final Context originalContext; // Fragment context for UI operations
    private final String downloadUrl;
    private final String fileName;
    private final String fileSize;
    private final String mimeType;
    private DownloadListener listener;
    private final OkHttpClient client;
    private final ExecutorService executorService;
    private Call downloadCall;
    private ProgressDialog progressDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    public interface DownloadListener {
        void onDownloadSuccess(String filePath);
        void onDownloadFailed(String error);
        void onProgressUpdate(int progress);

        void onDownloadSuccess(String filePath, Uri fileUri);
    }

    public FileDownloadInFragment(Context context, String downloadUrl, String fileName,String fileSize, String mimeType)
    {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.originalContext = context; // Fragment context or Activity
        this.context = context.getApplicationContext();
        this.downloadUrl = downloadUrl;
        this.fileName = sanitizeFileName(fileName);
        this.fileSize = fileSize;
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
        // Permission checks
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(originalContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            notifyFailure("Storage permission required. Please grant it and retry.");
            return; // Permission handling is delegated to Fragment
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isApkFile() &&
                !context.getPackageManager().canRequestPackageInstalls()) {
            notifyFailure("Unknown sources permission required. Please enable it and retry.");
            return; // Permission handling is delegated to Fragment
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

                long fileLength = Long.parseLong(fileSize);
                if (fileLength <= 0) {
                    executorService.execute(() -> {
                        if (isUiSafe()) {
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
            if (isUiSafe()) {
                ((Activity) originalContext).runOnUiThread(() -> {
                    try {
                        progressDialog = new ProgressDialog(originalContext);
                        progressDialog.setTitle(i18n("Downloading"));
                        progressDialog.setMessage(i18n("Please wait..."));
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
                notifyFailure("Cannot show progress dialog: Invalid context or activity state.");
            }
        });
    }

    private void dismissProgressDialog() {
        executorService.execute(() -> {
            if (progressDialog != null && progressDialog.isShowing() && isUiSafe()) {
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
            if (isUiSafe()) {
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
                        // Delete the file after starting the installation
                        // deleteFile(file, uri);
                    } catch (Exception e) {
                        notifyFailure("Failed to start APK installation: " + e.getMessage());
                    }
                });
            } else {
                notifyFailure("Cannot start APK installation: Invalid context or activity state.");
            }
        });
    }

    private void deleteFile(File file, Uri uri) {
        executorService.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                    // Delete from MediaStore
                    context.getContentResolver().delete(uri, null, null);
                    Log.d("FileDownloader", "Deleted MediaStore entry: " + uri);
                } else if (file != null && file.exists()) {
                    // Delete physical file
                    if (file.delete()) {
                        Log.d("FileDownloader", "Deleted file: " + file.getAbsolutePath());
                    } else {
                        Log.w("FileDownloader", "Failed to delete file: " + file.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                Log.e("FileDownloader", "Error deleting file: " + e.getMessage());
            }
        });
    }

    private void notifySuccess(String filePath) {
        executorService.execute(() -> {
            if (listener != null && isUiSafe()) {
                ((Activity) originalContext).runOnUiThread(() -> listener.onDownloadSuccess(filePath));
            }
        });
    }

    private void notifyFailure(String error) {
        executorService.execute(() -> {
            if (listener != null && isUiSafe()) {
                ((Activity) originalContext).runOnUiThread(() ->
                        listener.onDownloadFailed(error != null ? error : "Unknown error"));
            }
        });
    }

    private void notifyProgress(int progress) {
        executorService.execute(() -> {
            if (isUiSafe()) {
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

    private boolean isUiSafe() {
        return originalContext instanceof Activity && !((Activity) originalContext).isFinishing();
    }


}