package com.mkac.meikomms.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImageDownloader
{
    private static final OkHttpClient client = new OkHttpClient();

    public static Bitmap downloadImage(String imageUrl) {
        try {
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Async version with callback
    public static void downloadImageAsync(String imageUrl, OnImageDownloadListener listener) {
        new Thread(() -> {
            Bitmap bitmap = downloadImage(imageUrl);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (bitmap != null) {
                    listener.onImageDownloaded(bitmap);
                } else {
                    listener.onError("Download failed");
                }
            });
        }).start();
    }

    public interface OnImageDownloadListener {
        void onImageDownloaded(Bitmap bitmap);
        void onError(String error);
    }
}
