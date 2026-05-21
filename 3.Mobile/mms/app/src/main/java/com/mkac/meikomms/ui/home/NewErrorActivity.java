package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.MainActivity;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.ui.custom.UploadButton;
import com.mkac.meikomms.ui.taskdetail.ImageAdapter;
import com.mkac.meikomms.ui.taskdetail.ImageModel;
import com.mkac.meikomms.ui.taskdetail.ListMaterialAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class NewErrorActivity extends AppCompatActivity {


    private static final int PERMISSION_CODE = 1001;
    private static final int IMAGE_PICK_CODE = 1000;
    private static final int CAMERA_REQUEST_CODE = 1002;

    private String server_url = "";
    private String user_login="";
    private String Machine_Id_Select = "";
    private String Error_Id_Select = "";
    private AutoCompleteTextView cb_select_type_error,cb_select_machine;
    private  ArrayAdapter<String> adapter,adapter_error_type;
    private List<ImageModel> imageList;
    private ImageNewErrorAdapter imageAdapter;
    private Uri imageUri;
    private ArrayList<String> Listmachine;
    HashMap<String, String> machineMap; // Lưu Machine_Id_Name -> Machine_Id
    HashMap<String, String> errorMap; // Lưu Error_Type -> Error_Id
    private ImageView cameraBtn,btn_back,btn_select_machine,btn_type_error;
    private RecyclerView rcv_list_img_error;
    private RelativeLayout btn_add_error;
    private EditText txt_content;
    private TextView txtlabel_Directivecode;
    private TextView txt_lable_machine;
    private TextView txt_lable_type_error;
    private TextView txt_lable_content;
    private TextView lable_image;
    private TextView btn_save_error;
    private TextView txt_lable_upload;
    private UploadButton pickImageBtn;
    private String Issue_Type = "";
    private String Machine_Id = "";
    private ArrayList<Uri> selectedFileUris = new ArrayList<>();
    private RecyclerView rcv_list_error;
    private TextView txt_lable_list_error;
    private TextView error_code;
    private TextView machineCode;
    private TextView issueCode;
    private TextView Error_content;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_error);
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);
        server_url = configManager.getProperty("server_url");


        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }
        if(configManager.getProperty("vertical_lock").equals("true"))
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        txtlabel_Directivecode = findViewById(R.id.txtlabel_Directivecode);
        txt_lable_upload = findViewById(R.id.txt_lable_upload);
        txt_lable_machine = findViewById(R.id.txt_lable_machine);
        txt_lable_type_error = findViewById(R.id.txt_lable_error_type);
        txt_lable_content = findViewById(R.id.txt_lable_content);
        lable_image = findViewById(R.id.lable_image);
        txt_content = findViewById(R.id.txt_content);
        btn_add_error = findViewById(R.id.btn_add_error);
        btn_select_machine = findViewById(R.id.btn_select_machine);
        btn_type_error = findViewById(R.id.btn_type_error);
        cameraBtn = findViewById(R.id.cameraBtn);
        pickImageBtn = findViewById(R.id.pickImageBtn);
        btn_back = findViewById(R.id.btn_back);
        btn_save_error = findViewById(R.id.btn_save_error);
        rcv_list_error = findViewById(R.id.rcv_list_error);
        rcv_list_img_error = findViewById(R.id.rcv_list_img_error);
        txt_lable_list_error = findViewById(R.id.txt_lable_list_error);
        error_code = findViewById(R.id.error_code);
        machineCode = findViewById(R.id.machineCode);
        issueCode = findViewById(R.id.issueCode);
        Error_content = findViewById(R.id.Error_content);
        rcv_list_error.setLayoutManager(new LinearLayoutManager(this));
        rcv_list_img_error.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        Listmachine = new ArrayList<>();
        machineMap = new HashMap<>();
        errorMap = new HashMap<>();


        Thread getDataListMachine = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_list_machine_data(this, server_url, user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (rs.data != null)
                        {

                            if (!rs.data.isEmpty())
                            {
                                List<JSONObject> machine_list = rs.data;
                                for (int i = 0; i < machine_list.size(); i++)
                                {
                                    Listmachine.add(machine_list.get(i).getString("Machine_Id_Name"));
                                    machineMap.put(machine_list.get(i).getString("Machine_Id_Name"), machine_list.get(i).getString("Machine_Id"));
                                }

                                load();
                                cb_select_type_error.setText(adapter_error_type.getItem(0));
                                Error_Id_Select = "1";

                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataListMachine.start();


        cb_select_machine = findViewById(R.id.cb_select_machine);
        cb_select_machine.setThreshold(1);

        // Adapter cho AutoCompleteTextView
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, Listmachine);
        cb_select_machine.setAdapter(adapter);

        // Bắt sự kiện khi chọn một mục
        cb_select_machine.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter.getItem(position);
            cb_select_machine.setText(selectedItem); // Cập nhật giá trị khi chọn
            Machine_Id_Select = machineMap.get(selectedItem);
            cb_select_machine.setSelection(cb_select_machine.getText().length());
        });

        // Khai báo AutoCompleteTextView & Button
        cb_select_type_error = findViewById(R.id.cb_select_type_error);

        // Danh sách dữ liệu
        List<String> items = Arrays.asList(i18n("Impact on production"), i18n("Impact on safety"));
        errorMap.put(items.get(0), "1");
        errorMap.put(items.get(1), "2");

        // Adapter cho AutoCompleteTextView
        adapter_error_type = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, items);
        cb_select_type_error.setAdapter(adapter_error_type);


        // Chặn nhập nội dung thủ công
        cb_select_type_error.setInputType(InputType.TYPE_NULL);
        cb_select_type_error.setKeyListener(null);

        // Mở dropdown khi nhấn vào AutoCompleteTextView
        cb_select_type_error.setOnClickListener(v -> cb_select_type_error.showDropDown());


        // Bắt sự kiện khi chọn một mục
        cb_select_type_error.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter_error_type.getItem(position);
            cb_select_type_error.setText(selectedItem, false);
            Error_Id_Select = errorMap.get(selectedItem);
        });



        btn_select_machine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                cb_select_machine.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_machine.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_machine.postDelayed(cb_select_machine::showDropDown, 200);

            }
        });

        btn_type_error.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cb_select_type_error.setText("");
                cb_select_type_error.requestFocus();
                cb_select_type_error.postDelayed(cb_select_type_error::showDropDown, 200);
                Error_Id_Select = "";
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermissionAndOpenCamera();
            }
        });

        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btn_add_error.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(Machine_Id_Select.isEmpty())
                {
                    ErrorDialog( Gravity.CENTER,i18n("ERROR"),i18n("Please select machine"));
                }else if(Error_Id_Select.isEmpty())
                {
                    ErrorDialog( Gravity.CENTER,i18n("ERROR"),i18n("Please select error code"));
                }
                else if(txt_content.getText().toString().isEmpty())
                {
                    ErrorDialog( Gravity.CENTER,i18n("ERROR"),i18n("Please enter error content"));
                } else
                {
                    EndWorkDialog(Gravity.CENTER, "END WORK", "Do you want to save data?");
                }

            }
        });

        imageList = new ArrayList<>();
        imageAdapter = new ImageNewErrorAdapter( imageList,selectedFileUris,this);
        rcv_list_img_error.setAdapter(imageAdapter);


        txtlabel_Directivecode.setText(i18n("Report new error"));
        txt_lable_machine.setText(i18n("Machine code")+" - "+i18n("Machine name"));
        txt_lable_type_error.setText(i18n("Error type"));
        txt_lable_content.setText(i18n("Error content"));
        lable_image.setText(i18n("Condition photo"));
        btn_save_error.setText(i18n("Save"));
        cb_select_machine.setHint(i18n("Select information"));
        cb_select_type_error.setHint(i18n("Select information"));


        txt_lable_upload.setText(i18n("Upload file"));
        pickImageBtn.setLabel(i18n("Click here"));
        pickImageBtn.setLabel2(i18n("to upload image files"));
        txt_lable_list_error.setText(i18n("List of errors"));

        error_code.setText(i18n("Error code"));
        machineCode.setText(i18n("Machine code"));
        issueCode.setText(i18n("Error type"));
        Error_content.setText(i18n("Error content"));



        cb_select_machine.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                Machine_Id = "";
                Machine_Id_Select = "";
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                Machine_Id = "";
                Machine_Id_Select = "";
            }

            @Override
            public void afterTextChanged(Editable editable) {
                Machine_Id = "";
                Machine_Id_Select = "";
            }
        });


        pickImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImageFromGallery();
            }
        });


    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    hideKeyboard(v);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void pickImageFromGallery()
    {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Cho phép chọn nhiều ảnh
        startActivityForResult(intent, IMAGE_PICK_CODE);
    }

    private void EndWorkDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_yesno);
        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        TextView btnOK = (TextView) dialog.findViewById(R.id.btnlogout);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        txtnoidung.setText(i18n("Do you want to save the incident information ?"));
        tvTitle.setText(i18n("SAVE"));
        Window window = dialog.getWindow();
        btnOK.setText(i18n("Agree"));
        btncancel.setText(i18n("Close"));
        if(window == null){return;}
        else
        {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }

        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                Issue_Type = Error_Id_Select;
                String Issue_User_Id = user_login;
                Machine_Id = Machine_Id_Select;
                String Note = txt_content.getText().toString();
                if(cb_select_machine.getText().toString().isEmpty())
                {
                    Machine_Id = "";
                }

                if(selectedFileUris.isEmpty())
                {
                    Thread getDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.save_new_task_data(view.getContext(),Issue_Type,Issue_User_Id,Machine_Id,Note, server_url);
                        if (rs.code == 200)
                        {
                            runOnUiThread(() -> {
                                try
                                {
                                    load();
                                    dialog.dismiss();
                                    SuccessDialog(Gravity.CENTER,i18n("Success"));

                                } catch (Exception e)
                                {
                                    throw new RuntimeException(e);
                                }

                            });

                        }else
                        {
                            if (rs.code == 405)
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Enter complete information"));
                                    btnOK.setText(i18n("Retry"));
                                    btncancel.setText(i18n("Close"));
                                });

                            }else
                            if (rs.code == 422)
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Could not save data"));
                                    btnOK.setText(i18n("Retry"));
                                    btncancel.setText(i18n("Close"));
                                });
                            }else
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Could not save data"));
                                    btnOK.setText(i18n("Retry"));
                                    btncancel.setText(i18n("Close"));
                                });
                            }

                        }

                    });
                    getDataListMaterial.start();
                }
                else
                {
                    Thread getDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.save_new_task_file_data(view.getContext(),Issue_Type,Issue_User_Id,Machine_Id,selectedFileUris,Note, server_url);
                        if (rs.code == 200)
                        {
                            runOnUiThread(() -> {
                                try
                                {
                                    dialog.dismiss();
                                    SuccessDialog(Gravity.CENTER,i18n("Success"));

                                } catch (Exception e)
                                {
                                    throw new RuntimeException(e);
                                }

                            });

                        }else
                        {
                            if (rs.code == 405)
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Enter complete information"));
                                    btnOK.setText(i18n("Retry"));
                                });
                            }else
                            if (rs.code == 422)
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Could not save data"));
                                    btnOK.setText(i18n("Retry"));
                                });
                            }else
                            {
                                runOnUiThread(() -> {
                                    tvTitle.setText(i18n("ERROR"));
                                    txtnoidung.setText(i18n("Could not save data"));
                                    btnOK.setText(i18n("Retry"));
                                });
                            }

                        }

                    });
                    getDataListMaterial.start();
                }

            }
        });


        dialog.show();
    }
    private void SuccessDialog(int gravity, String noidung)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_success);

        TextView txtnoidung = (TextView) dialog.findViewById(R.id.dialog_Message);


        txtnoidung.setText(noidung);

        Window window = dialog.getWindow();

        if(window == null){return;}
        else
        {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }

        dialog.show();

        new Handler().postDelayed(() -> {
            if (dialog.isShowing())
            {
                dialog.dismiss();
                finish();
            }
        }, 1200);

    }
    private void checkPermissionAndOpenCamera()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_DENIED) {
                String[] permissions = {Manifest.permission.CAMERA};
                requestPermissions(permissions, PERMISSION_CODE);
            } else {
                openCamera();
            }
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_PICK_CODE && data != null) {
                if (data != null) {
                    if (data.getClipData() != null) { // Khi chọn nhiều ảnh
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri imageUri = data.getClipData().getItemAt(i).getUri();
                            // selectedFileUris.add(imageUri);
                            imageList.add(new ImageModel(imageUri));
                        }
                    } else if (data.getData() != null) { // Khi chỉ chọn 1 ảnh
                        Uri imageUri = data.getData();
                        imageList.add(new ImageModel(imageUri));
                    }
                    imageAdapter.notifyDataSetChanged();
                }
            } else if (requestCode == CAMERA_REQUEST_CODE) {
                imageList.add(new ImageModel(imageUri));
                imageAdapter.notifyDataSetChanged();
            }
        }
    }


    private void load()
    {
        rcv_list_error.setAdapter(null);
        Thread getDataListError = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_list_error_data(this, server_url, user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (rs.data != null)
                        {

                            if (!rs.data.isEmpty())
                            {
                                List<JSONObject> list_error = rs.data;
                                ListErrorNewAdapter listErrorNewAdapter = new ListErrorNewAdapter(list_error, this, new ListErrorNewAdapter.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(View view, int position) throws JSONException {
//                                        JSONObject error_select = list_error.get(position);
//                                        String Error_Id = error_select.getString("Issue_Id");
//                                        DeleteErrorDialog(Gravity.CENTER, i18n("DELETE ERROR"), i18n("Do you want to delete this error?"), Error_Id);

                                        ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Account not authorized to delete"));

                                    }
                                });
                                rcv_list_error.setAdapter(listErrorNewAdapter);
                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataListError.start();


    }


    private void DeleteErrorDialog(int gravity, String tieude, String noidung, String Error_Id)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_yesno);

        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        TextView btnOK = (TextView) dialog.findViewById(R.id.btnlogout);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        txtnoidung.setText(i18n(noidung));
        tvTitle.setText(i18n(tieude));
        Window window = dialog.getWindow();
        btnOK.setText(i18n("Confirm"));
        btncancel.setText(i18n("Cancel"));
        if(window == null){return;}
        else
        {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }

        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {


                Thread deleteDataListError = new Thread(() -> {
                    HttpClient.APIReturn rs_delete = HttpClient.delete_error_by_user(view.getContext(),Error_Id ,server_url, user_login);
                    if (rs_delete.code == 200)
                    {
                        runOnUiThread(() -> {
                            try {

                                dialog.dismiss();
                               // load();

                            } catch (Exception e) {

                                throw new RuntimeException(e);
                            }

                        });

                    }else
                    {
                        ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Clear error failed"));
                    }

                });
                deleteDataListError.start();

            }
        });

        dialog.show();
    }

    private void ErrorDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_error);

        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        txtnoidung.setText(noidung);
        tvTitle.setText(tieude);
        Window window = dialog.getWindow();
        btncancel.setText(i18n("Close"));
        if(window == null){return;}
        else
        {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }

        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.show();

        new Handler().postDelayed(() -> {
            if (dialog.isShowing())
            {
                dialog.dismiss();
            }
        }, 2500);

    }



    @Override
    public void onResume()
    {

        super.onResume();


    }




}