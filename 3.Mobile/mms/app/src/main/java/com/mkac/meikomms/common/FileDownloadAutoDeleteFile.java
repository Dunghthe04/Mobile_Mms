package com.mkac.meikomms.common;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;

import java.io.File;

public class FileDownloadAutoDeleteFile
{
    private Context context;
    private DownloadManager downloadManager;
    private long downloadID;
    private DownloadListener listener;
    private File downloadedFile;
    private BroadcastReceiver downloadReceiver;
    private ActivityResultLauncher<Intent> installLauncher;

    public interface DownloadListener {
        void onDownloadSuccess(String filePath, Uri fileUri);
        void onDownloadFailure();
    }

    public FileDownloadAutoDeleteFile(Context context, DownloadListener listener) {
        this.context = context;
        this.listener = listener;
        downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void setInstallLauncher(ActivityResultLauncher<Intent> launcher) {
        this.installLauncher = launcher;
    }

    public void downloadFile(String url, String fileName) {
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("Downloading " + fileName);
        request.setDescription("Please wait...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);
            downloadedFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        } else {
            downloadedFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            request.setDestinationUri(Uri.fromFile(downloadedFile));
        }

        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
        if (mimeType != null) {
            request.setMimeType(mimeType);
        }

        downloadID = downloadManager.enqueue(request);

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadID) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadID);
                    android.database.Cursor cursor = downloadManager.query(query);
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                            String downloadedFilePath = downloadedFile.getAbsolutePath();
                            Uri apkUri;

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", downloadedFile);
                            } else {
                                apkUri = Uri.fromFile(downloadedFile);
                            }

                            listener.onDownloadSuccess(downloadedFilePath, apkUri);

                            Intent installIntent = getInstallIntent(downloadedFile, apkUri);
                            if (installLauncher != null) {
                                installLauncher.launch(installIntent);
                            } else {
                                context.startActivity(installIntent);
                                deleteDownloadedFileAfterInstall();
                            }
                        } else {
                            listener.onDownloadFailure();
                        }
                    }
                    cursor.close();
                    context.unregisterReceiver(this);
                }
            }
        };

        context.registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public Intent getInstallIntent(File file, Uri uri) {
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

        return installIntent;
    }

    public void deleteDownloadedFileAfterInstall() {
        if (downloadedFile != null && downloadedFile.exists()) {
            boolean deleted = downloadedFile.delete();
            Log.d("FileDelete", "Delete result: " + deleted);
            if (!deleted) {
                downloadedFile.deleteOnExit();
            }
        }
    }

    public File getDownloadedFile() {
        return downloadedFile;
    }
}
