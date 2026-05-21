package com.mkac.meikomms.common;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.mkac.meikomms.R;


public class Barcode extends AppCompatActivity
{
    private PreviewView previewView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode);
        getSupportActionBar().hide();
        previewView = findViewById(R.id.previewView);

        // Initialize barcode scanner
        BarcodeScannerUtil.initializeCamera(this, previewView, barcode -> {
            // Return the barcode result to the calling activity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("barcode", barcode);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BarcodeScannerUtil.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        BarcodeScannerUtil.onRequestPermissionsResult(this, requestCode, permissions, grantResults, previewView, barcode -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("barcode", barcode);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

}

