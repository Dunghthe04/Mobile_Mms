package com.mkac.meikomms.common;

import android.os.Build;
import android.os.FileUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;

public class ProgressRequestBody extends RequestBody
{

    private final RequestBody requestBody;
    private final FileUtils.ProgressListener listener;

    public ProgressRequestBody(RequestBody requestBody, FileUtils.ProgressListener listener) {
        this.requestBody = requestBody;
        this.listener = listener;
    }

    @Override
    public MediaType contentType() {
        return requestBody.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return requestBody.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        long totalBytes = contentLength();
        BufferedSink progressSink = Okio.buffer(new okio.ForwardingSink(sink) {
            long bytesWritten = 0;

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                bytesWritten += byteCount;
                if (listener != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        listener.onProgress(bytesWritten);
                    }
                }
            }
        });
        requestBody.writeTo(progressSink);
        progressSink.flush();
    }



}
