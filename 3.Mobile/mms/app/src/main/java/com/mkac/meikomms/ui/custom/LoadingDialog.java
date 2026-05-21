package com.mkac.meikomms.ui.custom;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.mkac.meikomms.R;


public class LoadingDialog
{
    private Context context;
    private AlertDialog dialog;

    // Constructor with context parameter
    public LoadingDialog(Context context)
    {
        this.context = context;
    }

    @SuppressLint("InflateParams")
    public void show() {
        if (context == null) {
            throw new IllegalStateException("Context not set. Call LoadingDialog with a valid context first.");
        }

        // Using Method 1: Custom Theme
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.TransparentDialogTheme);

        // Using Method 2: Setting Background Directly
        // AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.loading, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        dialog = builder.create();

        // For Method 2: Setting the background directly
      //  dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        dialog.show();
    }

    // Dismiss method
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
