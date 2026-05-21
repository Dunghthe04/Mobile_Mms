package com.mkac.meikomms.common;


import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.net.ParseException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.mkac.meikomms.R;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ContinuousCaptureActivity extends AppCompatActivity {


    private String type_reader = "";
    private String device_name = "";
    private String server_url = "";
    private String server_url_short = "";
    private String master_post = "";
    private String server_dynamic_url = "";
    private String schema_data = "";
    private String schema_core = "";
    private JSONObject obj = null;
    private DecoratedBarcodeView barcodeView;
    private BeepManager beepManager;
    private String lastText;
    private String  typescan = "";
    private String  screenname = "";
    private String list_cell = ""; //Danh sách mã kệ
    private ArrayList<String> arraycode = new ArrayList<String>(); //Danh sách mã code đang quét
    public JSONArray list_scaned = new JSONArray(); // Danh sách item đã quét
    private JSONArray list_directive = new JSONArray(); //Danh sách item theo chỉ thị
    private Button btnPause,btnResume,btnSave;
    private MediaPlayer mp_error,mp_success;


    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashlightOn = false;

    private BarcodeCallback callback = new BarcodeCallback()
    {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if(result.getText() == null || result.getText().equals(lastText))
            {
                return;
            }
            lastText = result.getText();
            barcodeView.setStatusText(result.getText());
            beepManager.playBeepSoundAndVibrate();

            if(screenname.equals("UpdateLocationMainFragment"))
            {
                if(typescan.equals("shelf"))
                {
                    arraycode.add(result.getText());
                    if(!arraycode.isEmpty())
                    {
                        barcodeView.pause();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("barcode",arraycode.get(0));
                        setResult(RESULT_OK, resultIntent);
                    }
                    finish();
                }

            }


        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.continuous_scan);
        getSupportActionBar().hide();
        barcodeView = findViewById(R.id.barcode_scanner);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnSave = findViewById(R.id.btnSave);
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_39, BarcodeFormat.CODE_128);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.initializeFromIntent(getIntent());
        barcodeView.decodeContinuous(callback);
        beepManager = new BeepManager(this);
        barcodeView.setStatusText(i18n("Scanning..."));

        btnPause.setText(i18n("PAUSE"));
        btnResume.setText(i18n("RESUME"));
        btnSave.setText(i18n("SAVE"));
        barcodeView.resume();

        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        server_url = configManager.getProperty("gateway_url");
        server_url_short = configManager.getProperty("api_url_short");
        master_post = configManager.getProperty("master_post");
        server_dynamic_url = configManager.getProperty("gateway_dynamic_url");
        schema_data = configManager.getProperty("schema_data");
        schema_core = configManager.getProperty("schema_core");
        type_reader = configManager.getProperty("type_reader");

        if (!handler.getString("gateway_url").isEmpty()) {
            server_url = handler.getString("gateway_url");
        }
        if (!handler.getString("gateway_dynamic_url").isEmpty()) {
            server_dynamic_url = handler.getString("gateway_dynamic_url");
        }
        if (!handler.getString("api_url_short").isEmpty()) {
            server_url_short = handler.getString("api_url_short");
        }
        if (!handler.getString("master_post").isEmpty()) {
            master_post = handler.getString("master_post");
        }
        if (!handler.getString("schema_data").isEmpty()) {
            schema_data = handler.getString("schema_data");
        }
        if (!handler.getString("schema_core").isEmpty()) {
            schema_core = handler.getString("schema_core");
        }
        if (!handler.getString("type_reader").isEmpty()) {
            type_reader = handler.getString("type_reader");
        }
        if (!handler.getString("device_model").isEmpty()) {
            device_name = handler.getString("device_model");
        }



        String datarecive = getIntent().getStringExtra("TypeScan");
        String mydata = datarecive.toString();

        try
        {
            obj = new JSONObject(mydata);
        }
        catch (JSONException e)
        {
            throw new RuntimeException(e);
        }

        try
        {
            if(obj.has("type_scan"))
            {
                typescan = obj.getString("type_scan"); // Loại quét

            }

            if(obj.has("screenname"))
            {
                screenname = obj.getString("screenname"); // Tên class nhận dữ liệu

            }
            if(obj.has("list_scaned"))
            {

                list_scaned = obj.getJSONArray("list_scaned"); // Danh sách item đã quét

            }

            if(obj.has("list_cell"))
            {

                list_cell = obj.getString("list_cell"); // Danh sách item đã quét

            }


            if(obj.has("list_directive"))
            {

                list_directive = obj.getJSONArray("list_directive"); // Danh sách mã theo chỉ thị

            }

        }
        catch (JSONException e)
        {
            throw new RuntimeException(e);
        }

        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                barcodeView.pause();
            }
        });

        btnResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                barcodeView.resume();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                barcodeView.pause();

                if(arraycode.size()>0)
                {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("barcode",arraycode);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
                else
                {
                    finish();
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        }

//        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            cameraId = cameraManager.getCameraIdList()[0];
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }

        // Example to toggle flashlight when the activity starts
       // toggleFlashlight();


    }


//    private void toggleFlashlight() {
//        try {
//
//            if (isFlashlightOn) {
//                cameraManager.setTorchMode(cameraId, false);
//                isFlashlightOn = false;
//                Toast.makeText(this, "Flashlight turned off", Toast.LENGTH_SHORT).show();
//            } else {
//                cameraManager.setTorchMode(cameraId, true);
//                isFlashlightOn = true;
//                Toast.makeText(this, "Flashlight turned on", Toast.LENGTH_SHORT).show();
//            }
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to access camera", Toast.LENGTH_SHORT).show();
//        }
//    }

    public void sendResultBack()
    {
        barcodeView.pause();

        if(arraycode.size()>0)
        {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("barcode",arraycode);
            setResult(RESULT_OK, resultIntent);
            finish();
        }else
        {
            finish();
        }


    }

    private void showAutoHideDialog(String title,String message) {
        // Create the dialog

        if (mp_success.isPlaying())
        {
            mp_success.stop();
            mp_success.release();
            mp_success = MediaPlayer.create(getBaseContext(), R.raw.beep_up);
            mp_success.start();
        }else
        {

            mp_success.start();
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .create();
        // Show the dialog
        dialog.show();

        // Schedule it to dismiss after 3 seconds (3000ms)
        new Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 1000);
    }

    public boolean isCheckbox(String box_code)
    {
        try
        {
            if(!box_code.isEmpty() && !box_code.equals("null") && !box_code.equals("-"))
            {
                int box_code_lenght = box_code.length();
                if(box_code_lenght==14)
                {
                    String box_code_header = box_code.substring(0,1);
                    String box_code_year = box_code.substring(1,5);
                    String box_code_month = box_code.substring(5,7);
                    String box_code_day = box_code.substring(7,9);
                    String box_code_index = box_code.substring(9,14);

                    if(box_code_header.equals("V"))
                    {
                        if(isInteger(box_code_year) && isInteger(box_code_month) && isInteger(box_code_day) && isInteger(box_code_index))
                        {
                            if(Integer.parseInt(box_code_year) > 2000 && Integer.parseInt(box_code_year) < 2100 && Integer.parseInt(box_code_month) > 0 && Integer.parseInt(box_code_month) < 13 && Integer.parseInt(box_code_day) > 0 && Integer.parseInt(box_code_day) < 32)
                            {
                                if(isValidDate(box_code_year+box_code_month+box_code_day))
                                {
                                    return true;
                                }else
                                {
                                    return false;
                                }

                            }
                            else
                            {
                                return false;
                            }
                        }
                        else
                        {
                            return false;
                        }

                    }else
                    {
                        return false;
                    }

                }
                else
                {
                    return false;
                }
            }else
            {
                return false;
            }

        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isCheckpack(String pack_code) // Kiểm tra mã gói có hợp lệ
    {
        try
        {
            if(!pack_code.isEmpty() && !pack_code.equals("null") && !pack_code.equals("-"))
            {
                int pack_code_lenght = pack_code.length();
                if(pack_code_lenght==14)
                {
                    String pack_code_header = pack_code.substring(0, 1);
                    String pack_code_year = pack_code.substring(1, 5);
                    String pack_code_month = pack_code.substring(5, 7);
                    String pack_code_day = pack_code.substring(7, 9);
                    String pack_code_index = pack_code.substring(9, 14);

                    if(pack_code_header.equals("G") || pack_code_header.equals("R") )
                    {
                        if(isInteger(pack_code_year) && isInteger(pack_code_month) && isInteger(pack_code_day) && isInteger(pack_code_index))
                        {
                            if(Integer.parseInt(pack_code_year) > 2000 && Integer.parseInt(pack_code_year) < 2100 && Integer.parseInt(pack_code_month) > 0 && Integer.parseInt(pack_code_month) < 13 && Integer.parseInt(pack_code_day) > 0 && Integer.parseInt(pack_code_day) < 32)
                            {
                                if(isValidDate(pack_code_year+pack_code_month+pack_code_day))
                                {
                                    return true;
                                }else
                                {
                                    return false;
                                }
                            }
                            else
                            {
                                return false;
                            }
                        }
                        else
                        {
                            return false;
                        }

                    }else
                    {
                        return false;
                    }

                }
                else
                {
                    return false;
                }
            }else
            {
                return false;
            }

        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidDate(String dateStr)
    {
        // Define the date format (YYYYMMDD)
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        dateFormat.setLenient(false); // Strict date parsing (e.g., no February 30)

        try {
            // Try parsing the date
            dateFormat.parse(dateStr);
            return true; // If parsing succeeds, it's a valid date
        } catch (ParseException | java.text.ParseException e) {
            // If parsing fails, it's an invalid date
            return false;
        }
    }

    private boolean Check_item_in_list(JSONArray list, String carton_code)
    {
        for (int i = 0; i < list.length(); i++)
        {
            try {
                if(list.get(i).toString().contains(carton_code))
                {
                    return true;
                }
            }
            catch (Exception e)
            {
                return false;
            }

        }
        return false;

    }

    public boolean isInteger(String str)
    {
        try
        {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void triggerScan(View view) {
        barcodeView.decodeSingle(callback);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    boolean doubleBackToExitPressedOnce = false;
    @Override
    public void onBackPressed() // Xác nhận dừng xác minh đóng gói
    {
        if (doubleBackToExitPressedOnce)
        {
            super.onBackPressed();
            return;
        }

        sendResultBack();


    }

    @Override
    protected void onResume()
    {
        super.onResume();
        mp_error = MediaPlayer.create(getBaseContext(), R.raw.error_plus);
        mp_success = MediaPlayer.create(getBaseContext(), R.raw.beep_up);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        barcodeView.pause();
        barcodeView.removeAllViews();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        barcodeView.pause();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("barcode", lastText);
        setResult(RESULT_OK, resultIntent);
        finish();

    }

}
