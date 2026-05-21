package com.mkac.meikomms.common;


import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mkac.meikomms.ui.custom.LoadingDialog;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ja.burhanrashid52.photoeditor.PhotoEditorView;

public class Utils
{

    public static void showLogoutDialog(Context context, final OnLogoutListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(i18n("Logout"));
        builder.setMessage(i18n("Do you want to log out?"));

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Người dùng nhấn OK, gọi listener và thực hiện các hành động cần thiết
                if (listener != null) {
                    listener.onLogout();
                }
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(i18n("Cancel"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Người dùng nhấn Cancel, đóng hộp thoại
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public interface OnLogoutListener {
        void onLogout();
    }

    public static void clearInputData(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);

            if (view instanceof EditText) {
                // Clear input data for EditText
                ((EditText) view).setText("");
            } else if (view instanceof CheckBox) {
                // Uncheck CheckBox
                ((CheckBox) view).setChecked(false);
            } else if (view instanceof ViewGroup) {
                // Recursive call for nested ViewGroups
                clearInputData((ViewGroup) view);
            }
        }
    }

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void loadImage(Context context, ImageView imageView, String imageUrl) {
        // Post Method: allows you to run code after the view has been laid out
        /* post method là một cách để đặt một đoạn mã để chạy sau khi view đã được khởi tạo và hiển thị trên màn hình.
        Nó là một phương thức hữu ích để đảm bảo rằng mã của bạn sẽ chạy sau khi quá trình layout và rendering của view đã hoàn tất.
        Dưới đây là một số điểm quan trọng về post method:
            1) Chạy Mã Sau Khi View Đã Được Laid Out:
                post method giúp bạn đặt một công việc để chạy sau khi view đã được hiển thị và laid out trên màn hình.
                Điều này là quan trọng khi bạn cần truy cập kích thước hoặc thông tin về view sau khi nó đã được hiển thị.

            2) Tránh Lỗi NullPointerException:
                Khi bạn cố gắng truy cập các thuộc tính của một view (ví dụ như chiều rộng và chiều cao)
                ngay sau khi nó được thêm vào layout,
                có thể xảy ra lỗi NullPointerException nếu view chưa hoàn tất quá trình khởi tạo và layout.
                post giúp bạn tránh được tình huống này.*/
        imageView.post(new Runnable() {
            @Override
            public void run() {
                int width = imageView.getWidth();
                int height = imageView.getHeight();
                Picasso.get()
                        .load(imageUrl)
                        //.error(R.drawable.btn_background)
                        .resize(width, height)
                        .centerInside()
                        .into(imageView, new Callback() {
                            @Override
                            public void onSuccess() {
                                Log.d("Picasso", "Done");// Image loaded successfully
                            }
                            @Override
                            public void onError(Exception e) {
                                Log.e("Picasso", "Failed");// Image loaded successfully
                                e.printStackTrace();
                            }
                        });

            }
        });
    }

    public static void loadBase64Image(ImageView imageView, String base64Image)
    {
        if (base64Image == null && base64Image != "null") {
            return;
        }
        byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        int width = imageView.getWidth();
        int height = imageView.getHeight();
        Bitmap scaledBitmap = resizeBitmapAspectFit(decodedByte, width, height);
        if (scaledBitmap != null) {
            //activity.runOnUiThread(() -> imageView.setImageBitmap(scaledBitmap));
            imageView.setImageBitmap(scaledBitmap);
        }
    }
    public static Bitmap resizeBitmapAspectFit(Bitmap bitmap, int desiredWidth, int desiredHeight)
    {
        if (bitmap != null)
        {
            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            // Ratios of desired dimensions to actual dimensions
            float scaleWidth = ((float) desiredWidth) / bitmapWidth;
            float scaleHeight = ((float) desiredHeight) / bitmapHeight;

            // Choose the smallest ratio as scale factor to maintain aspect ratio
            float scaleFactor = Math.min(scaleWidth, scaleHeight);

            int newWidth = (int) (bitmapWidth * scaleFactor);
            int newHeight = (int) (bitmapHeight * scaleFactor);

            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }
        else {
            return null;
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String unixConverter(String timeStamp, String format) {
        String formattedDateTime = "";
        try {
            long unixTime = Long.parseLong(timeStamp);
            LocalDateTime dateTime = Instant.ofEpochSecond(unixTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            formattedDateTime = dateTime.format(formatter);
        } catch (NumberFormatException e) {
            e.printStackTrace(); // Handle the exception based on your requirements
        }
        return formattedDateTime;
    }

    public static void setIconTint(ImageView imageView, int colorResource) {
        int iconColor = ContextCompat.getColor(imageView.getContext(), colorResource);
        Drawable iconDrawable = DrawableCompat.wrap(imageView.getDrawable());
        DrawableCompat.setTint(iconDrawable, iconColor);
        imageView.setImageDrawable(iconDrawable);
    }

    public static void setLightMode(View rootView){
        rootView.setBackgroundColor(rootView.getResources().getColor(android.R.color.background_light));
    }

    // Method to hide the keyboard
    public static void autoHideKeyboard (View view, Activity activity){
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                View focusedView = activity.getCurrentFocus();
                if (focusedView != null) {
                    inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                }
                return false;
            }
        });
    }

    public static void setUriToPhotoEditorView(Context context, Uri uri, PhotoEditorView photoEditorView) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
            photoEditorView.getSource().setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setBase64ToPhotoEditorView(Context context, String base64Image, PhotoEditorView photoEditorView) {
        if (base64Image == null || "null".equals(base64Image)) {
            Log.e("setBase64ToPhotoEditor", "Input base64Image string is null or 'null'");
            return;
        }

        // Strip off any base64 prefix if present
        if (base64Image.startsWith("data:image/")) {
            base64Image = base64Image.substring(base64Image.indexOf(",") + 1);
        }

        try {
            // Decode the base64 encoded string
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            if (decodedBitmap != null) {
                // Set the bitmap to the PhotoEditorView
                photoEditorView.getSource().setImageBitmap(decodedBitmap);
            } else {
                Log.e("setBase64ToPhotoEditor", "Failed to decode Base64 string into Bitmap.");
            }
        } catch (IllegalArgumentException e) {
            Log.e("setBase64ToPhotoEditor", "Error decoding Base64 string.", e);
        }
    }


    // Convert image URI to base64
    public static String imageUriToBase64(Context context, Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                return Base64.encodeToString(byteArray, Base64.DEFAULT);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getSelectedIndex(Spinner spinner) {
        // Get the currently selected item value from the spinner
        String selectedValue = spinner.getSelectedItem().toString();

        // Get the array adapter associated with the spinner
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();

        // Get the index of the selected item
        return adapter.getPosition(selectedValue);
    }

    public static String arrayStringBuilder(List<String> list) {
        StringBuilder builder = new StringBuilder();
        if (list == null || list.isEmpty()) {
            return "";
        };
        builder.append("(");
        for (int i = 0; i < list.size(); i++) {
            builder.append("\'").append(list.get(i)).append("\'");
            if (i < list.size() - 1) {
                builder.append(",");
            }
        }
        builder.append(")");

        String formattedString = builder.toString();
        return formattedString;
    };

    public static List<String> mapStrings(List<String> inputList, HashMap<Integer, String> targetHashMap) {
        List<String> mappedList = new ArrayList<>();

        // Iterate through each string in the input list
        for (String string : inputList) {
            // Convert string to integer (assuming it represents an integer)
            int key;
            try {
                key = Integer.parseInt(string);
            } catch (NumberFormatException e) {
                // If the string cannot be parsed as an integer, add null to the mapped list
                mappedList.add(null);
                continue;
            }

            // Check if the integer key exists in the hashmap
            if (targetHashMap.containsKey(key)) {
                // If the key exists, append its mapped value to the mapped list
                mappedList.add(targetHashMap.get(key));
            } else {
                // If the key does not exist in the hashmap, append null to the mapped list
                mappedList.add(null);
            }
        }

        return mappedList;
    }

    public static boolean isNullOrWhiteSpace(String str) {
        return str == null || str.trim().isEmpty();
    }

//    public static void dismissLoading(View popup, LoadingDialog dialog, boolean isDelay) {
//        if (dialog == null) return;
//        if (dialog instanceof android.app.Dialog) {
//            android.app.Dialog androidDialog = (android.app.Dialog) dialog;
//
//            if (!androidDialog.isShowing()) return;
//
//            if (isDelay && popup != null) {
//                popup.postDelayed(() -> {
//                    try {
//                        Context context = androidDialog.getContext();
//                        if (context instanceof Activity) {
//                            if (((Activity) context).isFinishing() || ((Activity) context).isDestroyed()) {
//                                return;
//                            }
//                        }
//                        androidDialog.dismiss();
//                    } catch (Exception e) {
//                        Log.e("DEBUG", "Dismiss dialog error: " + e.getMessage());
//                    }
//                }, 1000);
//            } else {
//                try {
//                    androidDialog.dismiss();
//                } catch (Exception e) {}
//            }
//        } else {
//            try {
//                dialog.dismiss();
//            } catch (Exception e) {}
//        }
//    }

    public static void dismissLoading(Activity activity, View popup, LoadingDialog dialog, boolean isDelay) {
        if (dialog == null || activity == null) return;

        if (activity.isFinishing() || activity.isDestroyed()) return;

        try {
            if (isDelay && popup != null) {
                popup.postDelayed(() -> {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {}
                }, 500);
            } else {
                dialog.dismiss();
            }
        } catch (Exception ignored) {}
    }
}
