package com.mkac.meikomms.common;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileUploader
{
    private static final String SERVER_URL = "https://yourserver.com/upload"; // Change this to your server URL

    public void uploadFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        OkHttpClient client = new OkHttpClient();

        // Create RequestBody for file
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("multipart/form-data"));

        // Create MultipartBody to send the file
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        // Create request
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(requestBody)
                .build();

        // Execute request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                System.out.println("Upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    System.out.println("Upload successful: " + response.body().string());
                } else {
                    System.out.println("Upload failed with response code: " + response.code());
                }
            }
        });
    }
}
