package com.mkac.meikomms.common;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScannerUtil
{
    private static final int CAMERA_REQUEST_CODE = 101;
    private static ExecutorService cameraExecutor;

    public static void initializeCamera(Activity activity, PreviewView previewView, BarcodeResultCallback callback) {
        // Request camera permissions
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(activity, previewView, callback);
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private static void startCamera(Activity activity, PreviewView previewView, BarcodeResultCallback callback) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(activity, cameraProvider, previewView, callback);
            } catch (ExecutionException | InterruptedException e) {
                // Handle any errors (including cancellation)
            }
        }, ContextCompat.getMainExecutor(activity));
    }


    private static void bindPreview(Activity activity, @NonNull ProcessCameraProvider cameraProvider, PreviewView previewView, BarcodeResultCallback callback) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                @ExperimentalGetImage
                @OptIn(markerClass = ExperimentalGetImage.class)
                android.media.Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    BarcodeScanner scanner = BarcodeScanning.getClient();
                    scanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                for (Barcode barcode : barcodes) {
                                    String rawValue = barcode.getRawValue();
                                    if (rawValue != null) {
                                        callback.onBarcodeResult(rawValue);
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                // Handle failure
                            })
                            .addOnCompleteListener(task -> {
                                imageProxy.close();
                            });
                }
            }
        });

        cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector, imageAnalysis, preview);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
    }

    public static void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    public static void onRequestPermissionsResult(Activity activity, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, PreviewView previewView, BarcodeResultCallback callback) {
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera(activity, previewView, callback);
        } else {
            Toast.makeText(activity, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    public interface BarcodeResultCallback {
        void onBarcodeResult(String barcode);
    }
}
