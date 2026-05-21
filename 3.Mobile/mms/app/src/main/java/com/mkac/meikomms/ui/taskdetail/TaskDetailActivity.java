package com.mkac.meikomms.ui.taskdetail;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mkac.meikomms.MainActivity;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.FileUtils;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.JsonConverter;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.data.MaintenanceStep;
import com.mkac.meikomms.ui.custom.UploadButton;
import com.mkac.meikomms.ui.home.HomeFragment;
import com.mkac.meikomms.ui.home.ListAddMaterialAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TaskDetailActivity extends AppCompatActivity {


    private static final int PERMISSION_CODE = 1001;
    private static final int IMAGE_PICK_CODE = 1000;
    private static final int CAMERA_REQUEST_CODE = 1002;
    private List<ImageModel> imageList;
    private ImageAdapter imageAdapter;
    private ImageView imageView;
    private Uri imageUri;
    private ArrayList<String> selectedFileName = new ArrayList<>();
    private ArrayList<Uri> selectedFileUris = new ArrayList<>();
    private Boolean check1 = false;
    private Boolean check2 = true;
    private Boolean check3 = true;
    private Boolean check4 = true;
    private String api_creat_export_material = "";
    private String server_url = "";
    private String user_login="";
    private RelativeLayout btn_pause_work,btn_start_work,btn_end_work,btn_add_list_material;
    private LinearLayout btn_show_list,btn_show_list_pre_maintain,lable_pre_maintain_header,lable_post_maintain_header,btn_show_list_post_maintain;
    private RecyclerView rcv_list_intruction,rcv_image,rcv_material;
    private ImageView img_show_list,btn_back,cameraBtn,img_show_list_pre_maintain ;
    private UploadButton pickImageBtn;
    private ListIntructionAdapter listIntructionAdapter;
    private  ListMaterialAdapter listMaterialAdapter;
    public List<JSONObject> data_intruction_list = new ArrayList<>();
    public List<JSONObject> data_material_list = new ArrayList<>();
    private String Task_Id = "";
    private String Task_Type = "";
    private String Status = "";
    private String Image_list = "";
    private JSONObject obj = new JSONObject();
    List<JSONObject> materialList = new ArrayList<>();
    private TextView user_id_login;
    private TextView lable_screen_header;
    private TextView txtsteplabel;
    private TextView txt_lable_show_list;
    private TextView txtpre_maintainlabel;
    private TextView txt_requests_arise_lable;
    private TextView txtlabel_note;
    private TextView txtlabel_image;
    private TextView txt_lable_upload;
    private TextView txt_lable_table_material;
    private TextView txt_note;
    private TextView txt_no;
    private TextView materialCode;
    private TextView materialName;
    private TextView material_qty;
    private TextView materialUnit;
    private TextView btn_startwork_lable;
    private TextView btn_dowork_lable;
    private TextView btn_endwork_lable;
    private TextView txt_requests_arise;
    private TextView txt_lable_show_list_post_maintain;
    private TextView txtlabel_Current_Situation;
    private TextView txtlabel_Root_Cause;
    private TextView txtlabel_Action_Taken;
    private TextView txtlabel_Countermeasure;
    private ImageView img_show_list_post_maintain;
    private EditText txt_Current_Situation;
    private EditText txt_Root_Cause;
    private EditText txt_Action_Taken;
    private EditText txt_Countermeasure;
    private TextView txt_lable_show_list_pre_maintain;
    private TextView red_start_1;
    private TextView red_start_2;
    private TextView red_start_3;
    private TextView red_start_4;
    private RelativeLayout lay_out_pre;
    private TextView txt_lable_machine_infor;
    private TextView txt_machine_infor;
    private String Material_Export = "";
    private String progress_value = "";
    private ArrayAdapter<String> adapter;
    private String WareHouse_Id_Select = "";
    private ArrayList<String> Listwarehouse;
    HashMap<String, String> warehouseMap; // Lưu Wh_Id_Name -> Wh_Id
    List<JSONObject> warehouseList = new ArrayList<>();
    private JSONArray materialsArray;
    private TextView btn_material_lable;
    private ArrayAdapter<String> adapter_material_list;
    private String Material_Id_Select = "";
    private ArrayList<String> Listmaterial;
    HashMap<String, String> materialMap; // Lưu Wh_Id_Name -> Wh_Id
    List<JSONObject> materialList_cb = new ArrayList<>();

    private MaintenanceAdapter adapter_pre,adapter_post;

    private RecyclerView rcv_list_pre_maintain;
    private RecyclerView rcv_list_post_maintain;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_task_detail);
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);
        rcv_list_pre_maintain = findViewById(R.id.rcv_list_pre_maintain);
        rcv_list_post_maintain = findViewById(R.id.rcv_list_post_maintain);
        rcv_list_intruction = findViewById(R.id.rcv_list_intruction);
        btn_show_list_post_maintain = findViewById(R.id.btn_show_list_post_maintain);
        txt_lable_show_list_pre_maintain = findViewById(R.id.txt_lable_show_list_pre_maintain);
        txt_lable_show_list_post_maintain = findViewById(R.id.txt_lable_show_list_post_maintain);
        btn_show_list = findViewById(R.id.btn_show_list);
        img_show_list_post_maintain = findViewById(R.id.img_show_list_post_maintain);
        lable_pre_maintain_header = findViewById(R.id.lable_pre_maintain_header);
        lable_post_maintain_header = findViewById(R.id.lable_post_maintain_header);
        btn_show_list_pre_maintain = findViewById(R.id.btn_show_list_pre_maintain);
        img_show_list = findViewById(R.id.img_show_list);
        img_show_list_pre_maintain = findViewById(R.id.img_show_list_pre_maintain);
        btn_add_list_material = findViewById(R.id.btn_add_list_material);
        btn_start_work = findViewById(R.id.btn_start_work);
        btn_pause_work = findViewById(R.id.btn_pause_work);
        btn_end_work = findViewById(R.id.btn_end_work);
        btn_back = findViewById(R.id.btn_back);
        cameraBtn = findViewById(R.id.cameraBtn);
        pickImageBtn = findViewById(R.id.pickImageBtn);
        rcv_image = findViewById(R.id.rcv_image);
        rcv_material = findViewById(R.id.rcv_material);
        user_id_login = findViewById(R.id.user_id_login);
        txt_note = findViewById(R.id.txt_note);
        txtpre_maintainlabel = findViewById(R.id.txtpre_maintainlabel);
        txt_Current_Situation = findViewById(R.id.txt_Current_Situation);
        txt_Root_Cause = findViewById(R.id.txt_Root_Cause);
        txt_Action_Taken = findViewById(R.id.txt_Action_Taken);
        txt_Countermeasure = findViewById(R.id.txt_Countermeasure);
        lay_out_pre = findViewById(R.id.lay_out_pre);
        txtlabel_Current_Situation = findViewById(R.id.txtlabel_Current_Situation);
        txtlabel_Root_Cause = findViewById(R.id.txtlabel_Root_Cause);
        txtlabel_Action_Taken = findViewById(R.id.txtlabel_Action_Taken);
        txtlabel_Countermeasure = findViewById(R.id.txtlabel_Countermeasure);

        red_start_1 = findViewById(R.id.red_start_1);
        red_start_2 = findViewById(R.id.red_start_2);
        red_start_3 = findViewById(R.id.red_start_3);
        red_start_4 = findViewById(R.id.red_start_4);

        txt_no = findViewById(R.id.txt_no);
        materialCode = findViewById(R.id.materialCode);
        materialName = findViewById(R.id.materialName);
        material_qty = findViewById(R.id.material_qty);
        materialUnit = findViewById(R.id.materialUnit);
        btn_startwork_lable = findViewById(R.id.btn_startwork_lable);
        btn_dowork_lable = findViewById(R.id.btn_dowork_lable);
        btn_endwork_lable = findViewById(R.id.btn_endwork_lable);
        lable_screen_header = findViewById(R.id.lable_screen_header);
        txtsteplabel = findViewById(R.id.txtsteplabel);
        txt_lable_show_list = findViewById(R.id.txt_lable_show_list);
        txt_requests_arise_lable = findViewById(R.id.txt_requests_arise_lable);
        txtlabel_note = findViewById(R.id.txtlabel_note);
        txtlabel_image = findViewById(R.id.txtlabel_image);
        txt_lable_upload = findViewById(R.id.txt_lable_upload);
        txt_lable_table_material = findViewById(R.id.txt_lable_table_material);
        txt_requests_arise = findViewById(R.id.txt_requests_arise);
        btn_material_lable = findViewById(R.id.btn_material_lable);
        txt_lable_machine_infor = findViewById(R.id.txt_lable_machine_infor);
        txt_machine_infor = findViewById(R.id.txt_machine_infor);
        rcv_list_intruction.setLayoutManager(new LinearLayoutManager(this));
        rcv_list_pre_maintain.setLayoutManager(new LinearLayoutManager(this));
        rcv_list_post_maintain.setLayoutManager(new LinearLayoutManager(this));
        rcv_material.setLayoutManager(new LinearLayoutManager(this));
        rcv_image.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        api_creat_export_material = configManager.getProperty("api_creat_export_material");
        server_url = configManager.getProperty("server_url");

        Listwarehouse = new ArrayList<>();
        Listmaterial = new ArrayList<>();
        warehouseMap = new HashMap<>();
        materialMap = new HashMap<>();
        if(configManager.getProperty("vertical_lock").equals("true"))
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }
        if (!handler.getString("api_creat_export_material").isEmpty()) {
            api_creat_export_material = handler.getString("api_creat_export_material");
        }


        String datarecive = getIntent().getStringExtra("Datasend");
        String mydata = datarecive.toString();

        try
        {
            imageList = new ArrayList<>();
            obj = new JSONObject(mydata);
            Task_Id = obj.getString("Task_Id");
            Status = obj.getString("Status");
            Image_list = obj.getString("Image_List");
            Task_Type = obj.getString("Task_Type");
            materialList.add(obj);

            if(Task_Type.equals("0"))
            {
                txt_Current_Situation.setVisibility(View.VISIBLE);
                txt_Root_Cause.setVisibility(View.VISIBLE);
                txt_Action_Taken.setVisibility(View.VISIBLE);
                txt_Countermeasure.setVisibility(View.VISIBLE);

                txtlabel_Current_Situation.setVisibility(View.VISIBLE);
                txtlabel_Root_Cause.setVisibility(View.VISIBLE);
                txtlabel_Action_Taken.setVisibility(View.VISIBLE);
                txtlabel_Countermeasure.setVisibility(View.VISIBLE);

                lable_pre_maintain_header.setVisibility(View.GONE);
                rcv_list_pre_maintain.setVisibility(View.GONE);
                lable_post_maintain_header.setVisibility(View.GONE);
                rcv_list_post_maintain.setVisibility(View.GONE);

            }
            else
            {
                txt_Current_Situation.setVisibility(View.GONE);
                txt_Root_Cause.setVisibility(View.GONE);
                txt_Action_Taken.setVisibility(View.GONE);
                txt_Countermeasure.setVisibility(View.GONE);

                txtlabel_Current_Situation.setVisibility(View.GONE);
                txtlabel_Root_Cause.setVisibility(View.GONE);
                txtlabel_Action_Taken.setVisibility(View.GONE);
                txtlabel_Countermeasure.setVisibility(View.GONE);

                red_start_1.setVisibility(View.GONE);
                red_start_2.setVisibility(View.GONE);
                red_start_3.setVisibility(View.GONE);
                red_start_4.setVisibility(View.GONE);

                lable_pre_maintain_header.setVisibility(View.VISIBLE);
                rcv_list_pre_maintain.setVisibility(View.VISIBLE);
                lable_post_maintain_header.setVisibility(View.VISIBLE);
                rcv_list_post_maintain.setVisibility(View.VISIBLE);
            }


            if(Status.equals("0") || Status.equals("5"))
            {
                btn_start_work.setVisibility(View.VISIBLE);
            }
            else if(Status.equals("4"))
            {
                btn_start_work.setVisibility(View.INVISIBLE);
                btn_end_work.setVisibility(View.VISIBLE);
                btn_pause_work.setVisibility(View.VISIBLE);
                btn_add_list_material.setVisibility(View.VISIBLE);
            }else
            {
                btn_start_work.setVisibility(View.INVISIBLE);
                btn_end_work.setVisibility(View.INVISIBLE);
                btn_pause_work.setVisibility(View.INVISIBLE);
                btn_add_list_material.setVisibility(View.INVISIBLE);
            }

            if(!Image_list.isEmpty())
            {

                String[] url = Image_list.split(",");
                for (String s : url)
                {
                    Uri uri = Uri.parse(server_url+"/api/v1/mms_mobile/file/preview/"+s);
                    imageList.add(new ImageModel(uri));
                }

            }

        }
        catch (JSONException e)
        {
            throw new RuntimeException(e);
        }


        Thread getDataTaskInfor = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_task_infor_data(this,server_url,Task_Id,user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (rs.data != null)
                        {

                            if (!rs.data.isEmpty())
                            {

                                JSONArray premaintenanceArray = rs.data.get(0).optJSONArray("Premaintenance");
                                List<MaintenanceStep> steps = new ArrayList<>();
                                for (int i = 0; i < premaintenanceArray.length(); i++)
                                {
                                    JSONObject jsonObject = premaintenanceArray.getJSONObject(i);
                                    String check_id = jsonObject.getString("Check_Id");
                                    String type = jsonObject.getString("Check_Type");
                                    String content = jsonObject.getString("Check_Content");
                                    String min = jsonObject.getString("Check_Content_Min");
                                    String max = jsonObject.getString("Check_Content_Max");
                                    String value = jsonObject.getString("Check_Value");
                                    steps.add(new MaintenanceStep(check_id,Integer.parseInt(type),content,Boolean.parseBoolean(value),value,min,(max)));

                                }

                                adapter_pre = new MaintenanceAdapter(this, steps);
                                rcv_list_pre_maintain.setAdapter(adapter_pre);



                                JSONArray postmaintenanceArray = rs.data.get(0).optJSONArray("Postemaintenance");
                                List<MaintenanceStep> steps_post = new ArrayList<>();
                                for (int i = 0; i < postmaintenanceArray.length(); i++)
                                {
                                    JSONObject jsonObject = postmaintenanceArray.getJSONObject(i);
                                    String check_id = jsonObject.getString("Check_Id");
                                    String type = jsonObject.getString("Check_Type");
                                    String content = jsonObject.getString("Check_Content");
                                    String min = jsonObject.getString("Check_Content_Min");
                                    String max = jsonObject.getString("Check_Content_Max");
                                    String value = jsonObject.getString("Check_Value");
                                    steps_post.add(new MaintenanceStep(check_id,Integer.parseInt(type),content,Boolean.parseBoolean(value),value,min,(max)));

                                }

                                adapter_post = new MaintenanceAdapter(this, steps_post);
                                rcv_list_post_maintain.setAdapter(adapter_post);



                                JSONArray jsonArray = rs.data.get(0).optJSONArray("Material");
                                data_material_list = JsonConverter.jsonArrayToList(jsonArray);
                                listMaterialAdapter = new ListMaterialAdapter(data_material_list, this);
                                rcv_material.setAdapter(listMaterialAdapter);

                                String Requirementtask = rs.data.get(0).getString("Requirementtask");
                                String Note = rs.data.get(0).getString("Note");

                                String Current_Situation = rs.data.get(0).getString("Current_Situation");
                                String Root_Cause = rs.data.get(0).getString("Root_Cause");
                                String Action_Taken = rs.data.get(0).getString("Action_Taken");
                                String Countermeasure = rs.data.get(0).getString("Countermeasure");


                                String Category_Id = rs.data.get(0).getString("Category_Id");
                                String Category_Name = rs.data.get(0).getString("Category_Name");
                                String Content = rs.data.get(0).getString("Content");

                                String Machine_Id = rs.data.get(0).getString("Machine_Id");
                                String Machine_Name = rs.data.get(0).getString("Machine_Name");
                                txt_machine_infor.setText(Machine_Id+" - "+Machine_Name);

                                Material_Export = rs.data.get(0).getString("Material_Export");
                                progress_value = rs.data.get(0).getString("Progress");
                                String category =
                                        "[{ \"Category_Id\": \""+Category_Id+"\",\n" +
                                                "  \"Category_Name\": \""+Category_Name+"\",\n" +
                                                "  \"Content\": \""+Content+"\"" +
                                                "}]" ;

                                data_intruction_list = JsonConverter.jsonArrayToList(new JSONArray(category));
                                listIntructionAdapter = new ListIntructionAdapter(data_intruction_list, this, new ListIntructionAdapter.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(View view, int position) throws JSONException {

                                    }
                                });
                                rcv_list_intruction.setAdapter(listIntructionAdapter);

                                txt_note.setText(Note);
                                txt_Current_Situation.setText(Current_Situation);
                                txt_Root_Cause.setText(Root_Cause);
                                txt_Action_Taken.setText(Action_Taken);
                                txt_Countermeasure.setText(Countermeasure);
                                txt_requests_arise.setText(Requirementtask);

                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataTaskInfor.start();

        btn_show_list.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(check1)
                {
                    check1 = false;
                    img_show_list.setImageResource(R.drawable.arrow_line);
                    rcv_list_intruction.setVisibility(View.GONE);
                    txt_lable_show_list.setText(i18n("See more"));

                }
                else
                {
                    check1 = true;
                    img_show_list.setImageResource(R.drawable.uprow_line);
                    rcv_list_intruction.setVisibility(View.VISIBLE);
                    txt_lable_show_list.setText(i18n("Collapse"));

                }

            }
        });

        btn_show_list_pre_maintain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(check3)
                {
                    check3 = false;
                    img_show_list_pre_maintain.setImageResource(R.drawable.arrow_line);
                    rcv_list_pre_maintain.setVisibility(View.GONE);
                    txt_lable_show_list_pre_maintain.setText(i18n("See more"));
                }
                else
                {
                    check3 = true;
                    img_show_list_pre_maintain.setImageResource(R.drawable.uprow_line);
                    rcv_list_pre_maintain.setVisibility(View.VISIBLE);
                    txt_lable_show_list_pre_maintain.setText(i18n("Collapse"));
                }
            }
        });

        btn_show_list_post_maintain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(check4)
                {
                    check4 = false;
                    img_show_list_post_maintain.setImageResource(R.drawable.arrow_line);
                    rcv_list_post_maintain.setVisibility(View.GONE);
                    txt_lable_show_list_post_maintain.setText(i18n("See more"));
                }
                else
                {
                    check4 = true;
                    img_show_list_post_maintain.setImageResource(R.drawable.uprow_line);
                    rcv_list_post_maintain.setVisibility(View.VISIBLE);
                    txt_lable_show_list_post_maintain.setText(i18n("Collapse"));
                }
            }
        });

        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btn_pause_work.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PauseWorkDialog(Gravity.CENTER,"Pause task","How much (%) of the work have you completed ?");
            }
        });

        btn_start_work.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                try
                {
                    List<JSONObject> data_select = new ArrayList<>();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("Task_Id", Task_Id);
                    jsonObject.put("Category_Name", "");
                    data_select.add(jsonObject);

                    Thread getDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.select_task_by_user(view.getContext(),data_select, server_url, user_login);
                        if (rs.code == 200)
                        {
                            runOnUiThread(() -> {
                                try
                                {
                                    if (Task_Type.equals("0"))
                                    {
                                        btn_add_list_material.setVisibility(View.VISIBLE);
                                    }else
                                    {
                                        btn_add_list_material.setVisibility(View.GONE);
                                    }

                                    btn_pause_work.setVisibility(View.VISIBLE);
                                    btn_end_work.setVisibility(View.VISIBLE);
                                    btn_start_work.setVisibility(View.INVISIBLE);
                                } catch (Exception e)
                                {

                                    throw new RuntimeException(e);
                                }

                            });

                        }

                    });
                    getDataListMaterial.start();


                }
                catch (JSONException e)
                {
                    throw new RuntimeException(e);
                }

            }
        });

        btn_end_work.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EndWorkDialog(Gravity.CENTER,"Complete task","Do you want to complete the maintenance items ?");
            }
        });


        btn_add_list_material.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddMaterialDialog(Gravity.CENTER,"Add material",view.getContext());
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermissionAndOpenCamera();
            }
        });

        pickImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImageFromGallery();
            }
        });

        imageAdapter = new ImageAdapter(this, imageList,selectedFileUris,Task_Id);

        rcv_image.setAdapter(imageAdapter);

        if (Task_Type.equals("1"))
        {
            btn_add_list_material.setVisibility(View.GONE);
        }

    }

    @Override
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



    private void checkPermissionAndOpenCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Cho phép chọn nhiều ảnh
        startActivityForResult(intent, IMAGE_PICK_CODE);
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageFromGallery();
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK)
        {
            if (requestCode == IMAGE_PICK_CODE && data != null)
            {
                if (data != null)
                {
                    if (data.getClipData() != null)
                    { // Khi chọn nhiều ảnh
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
            }
            else if (requestCode == CAMERA_REQUEST_CODE)
            {
                imageList.add(new ImageModel(imageUri));
                imageAdapter.notifyDataSetChanged();
            }
        }


    }


    private void AddMaterialDialog(int gravity, String tieude,Context context)
    {
        check2 = true;
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_add_material);
        EditText txt_material_qty = dialog.findViewById(R.id.txt_material_qty);
        TextView txtwarehouseinformation = dialog.findViewById(R.id.txtwarehouseinformation);
        TextView txt_lable_warehouse = dialog.findViewById(R.id.txt_lable_warehouse);
        TextView txt_lable_date = dialog.findViewById(R.id.txt_lable_date);
        TextView txtlabel_note = dialog.findViewById(R.id.txtlabel_note);
        TextView txt_note_add_material = dialog.findViewById(R.id.txt_note_add_material);
        TextView txt_lable_material = dialog.findViewById(R.id.txt_lable_material);
        TextView txt_lable_material_qty = dialog.findViewById(R.id.txt_lable_material_qty);
        TextView txt_lable_table_material = dialog.findViewById(R.id.txt_lable_table_material);
        TextView txt_no = dialog.findViewById(R.id.txt_no);
        TextView materialCode = dialog.findViewById(R.id.materialCode);
        TextView materialName = dialog.findViewById(R.id.materialName);
        TextView material_qty = dialog.findViewById(R.id.material_qty);
        TextView materialUnit = dialog.findViewById(R.id.materialUnit);
        TextView txt_lable_show_list_infor = dialog.findViewById(R.id.txt_lable_show_list_infor);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        ImageView btn_select_material = (ImageView) dialog.findViewById(R.id.btn_select_material);
        ImageView btn_select_warehouse = (ImageView) dialog.findViewById(R.id.btn_select_warehouse);
        ImageView btn_show_datepicker = (ImageView) dialog.findViewById(R.id.btn_show_datepicker);
        ImageView btn_add_material = (ImageView) dialog.findViewById(R.id.btn_add_material);
        ImageView img_show_list = (ImageView) dialog.findViewById(R.id.img_show_list);
        TextView btnclose = (TextView) dialog.findViewById(R.id.btnclose);
        TextView btnsave = (TextView) dialog.findViewById(R.id.btnsave);
        TextView btn_creat_material_export = (TextView) dialog.findViewById(R.id.btn_creat_material_export);
        AutoCompleteTextView cb_select_warehouse = (AutoCompleteTextView) dialog.findViewById(R.id.cb_select_warehouse);
        AutoCompleteTextView cb_select_material = (AutoCompleteTextView) dialog.findViewById(R.id.cb_select_material);
        AutoCompleteTextView cb_select_date = (AutoCompleteTextView) dialog.findViewById(R.id.cb_select_date);
        RecyclerView rcv_list_add_material = (RecyclerView) dialog.findViewById(R.id.rcv_list_add_material);

        RelativeLayout layout_export_material = (RelativeLayout) dialog.findViewById(R.id.layout_export_material);

        tvTitle.setText(i18n(tieude));
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

        txtwarehouseinformation.setText(i18n("Warehouse Dispatch Information"));
        txt_lable_warehouse.setText(i18n("Select warehouse supplies"));
        txt_lable_date.setText(i18n("Required Completion Date"));
        txtlabel_note.setText(i18n("Note"));
        txt_lable_material.setText(i18n("Select materials"));
        txt_lable_material_qty.setText(i18n("Quantity"));
        txt_lable_table_material.setText(i18n("Materials list"));
        txt_no.setText(i18n("No."));
        materialCode.setText(i18n("Material code"));
        materialName.setText(i18n("Material name"));
        material_qty.setText(i18n("Quantity"));
        materialUnit.setText(i18n("Unit"));
        txt_lable_show_list_infor.setText(i18n("Collapse"));
        btnclose.setText(i18n("Cancel"));
        btnsave.setText(i18n("Save"));
        btn_creat_material_export.setText(i18n("Warehouse Release Request"));


        Listmaterial.clear();
        Listwarehouse.clear();

        rcv_list_add_material.setLayoutManager( new LinearLayoutManager(this));

        cb_select_date.setText(TimeUtils.getCurrentDateStringByFormat("dd/MM/yyyy"));

        List<JSONObject> list_material_current = new ArrayList<>();

        if(Material_Export.equals("0"))
        {
            list_material_current = deepCopyList(listMaterialAdapter.getListMaterialData());

        }else
        {
            btnsave.setVisibility(View.GONE);
        }


        ListAddMaterialAdapter listAddMaterialAdapter = new ListAddMaterialAdapter(list_material_current, this);
        rcv_list_add_material.setAdapter(listAddMaterialAdapter);

        Thread getDataListMaterial = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_list_warehouse_and_material_by_task_data(this,Task_Id, server_url, user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (!rs.data.isEmpty())
                        {

                            materialsArray = rs.data.get(0).getJSONArray("Materials");
                            JSONArray warehousesArray = rs.data.get(0).getJSONArray("Warehouses");
                            List<JSONObject> warehousesList = convertJSONArrayToList(warehousesArray);
                            List<JSONObject> materialsList = convertJSONArrayToList(materialsArray);
                            for (int j = 0; j < warehousesList.size(); j++)
                            {
                                Listwarehouse.add(warehousesList.get(j).optString("Wh_Id_Name",""));
                                warehouseMap.put(warehousesList.get(j).optString("Wh_Id_Name",""), warehousesList.get(j).optString("Wh_Id",""));
                            }


                            for (int j = 0; j < materialsList.size(); j++)
                            {
                                Listmaterial.add(materialsList.get(j).optString("Material_Id_Name",""));
                                materialMap.put(materialsList.get(j).optString("Material_Id_Name",""), materialsList.get(j).optString("Material_Id",""));
                            }


                            if(Listwarehouse.size() > 0)
                            {
                                adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, Listwarehouse);
                                cb_select_warehouse.setAdapter(adapter);
                                cb_select_warehouse.setText(adapter.getItem(0));
                                cb_select_warehouse.setSelection(0);
                                WareHouse_Id_Select = warehouseMap.get(cb_select_warehouse.getText().toString());

                            }


                            if(Listmaterial.size() > 0)
                            {
                                adapter_material_list = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, Listmaterial);
                                cb_select_material.setAdapter(adapter_material_list);
                                cb_select_material.setText(adapter_material_list.getItem(0));
                                cb_select_material.setSelection(0);
                                Material_Id_Select = materialMap.get(cb_select_material.getText().toString());

                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataListMaterial.start();


        cb_select_material.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter_material_list.getItem(position);
            cb_select_material.setText(selectedItem, false);
            Material_Id_Select = materialMap.get(cb_select_material.getText().toString());
            cb_select_material.setError(null);
        });

        cb_select_warehouse.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter.getItem(position);
            cb_select_warehouse.setText(selectedItem, false);
            WareHouse_Id_Select = warehouseMap.get(cb_select_warehouse.getText().toString());
        });

        img_show_list.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {

                if(check2)
                {
                    check2 = false;
                    img_show_list.setImageResource(R.drawable.arrow_line);
                    layout_export_material.setVisibility(View.GONE);
                }
                else
                {
                    check2 = true;
                    img_show_list.setImageResource(R.drawable.uprow_line);
                    layout_export_material.setVisibility(View.VISIBLE);

                }


            }
        });

        btn_add_material.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (Material_Id_Select.isEmpty())
                {
                    cb_select_material.setError(i18n("Please select material"));
                }
                else if (txt_material_qty.getText().toString().isEmpty() || txt_material_qty.getText().toString().equals("0"))
                {
                    txt_material_qty.setError(i18n("Invalid quantity"));
                }
                else
                {
                    JSONObject new_material = new JSONObject();
                    try
                    {
                        new_material.put("Task_Id", Task_Id);
                        new_material.put("Material_Id", Material_Id_Select);
                        new_material.put("Material_Qty", txt_material_qty.getText().toString());
                        for (int i = 0; i < materialsArray.length(); i++)
                        {
                            JSONObject material = materialsArray.getJSONObject(i);
                            String material_id = material.getString("Material_Id");
                            if(material_id.equals(Material_Id_Select))
                            {
                                String material_name = material.getString("Material_Name");
                                String material_unit = material.getString("Material_Unit");
                                new_material.put("Material_Name", material_name);
                                new_material.put("Material_Unit", material_unit);
                                break;
                            }

                        }
                        listAddMaterialAdapter.addMaterial(new_material);

                    }
                    catch (JSONException e)
                    {
                        throw new RuntimeException(e);
                    }

                }

            }
        });

        btn_select_material.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cb_select_material.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_material.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_material.postDelayed(cb_select_material::showDropDown, 200);
                Material_Id_Select = "";
            }
        });

        btn_select_warehouse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cb_select_warehouse.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_warehouse.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_warehouse.postDelayed(cb_select_warehouse::showDropDown, 200);
                WareHouse_Id_Select = "";
            }
        });

        btn_show_datepicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(view.getContext(), cb_select_date, new HomeFragment.OnDateSelectedListener() {
                    @Override
                    public void onDateSelected(String formattedDate)
                    {


                    }
                });
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        btnclose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                dialog.dismiss();

            }
        });

        btnsave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                List<JSONObject> list_material_new = listAddMaterialAdapter.getMtaskListData();

                Thread addDataListMaterial = new Thread(() -> {
                    HttpClient.APIReturn rs = HttpClient.add_material_by_task(context,Task_Id,list_material_new ,server_url, user_login);
                    if (rs.code == 200)
                    {
                        runOnUiThread(() -> {
                            try {
                                listMaterialAdapter.updateData(list_material_new);
                                dialog.dismiss();

                            } catch (Exception e) {

                                throw new RuntimeException(e);
                            }

                        });

                    }

                });
                addDataListMaterial.start();

            }
        });

        btn_creat_material_export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(cb_select_warehouse.getText().toString().isEmpty() || WareHouse_Id_Select.isEmpty())
                {
                    Toast.makeText(context,i18n("Please select warehouse"),Toast.LENGTH_SHORT).show();

                }else
                if(listAddMaterialAdapter.getMtaskListData().size() == 0)
                {
                    Toast.makeText(context,i18n("Blank material list"),Toast.LENGTH_SHORT).show();

                }else  if( cb_select_date.getText().toString().isEmpty())
                {

                    Toast.makeText(context,i18n("No completion date selected"),Toast.LENGTH_SHORT).show();
                }else  if(txt_note_add_material.getText().toString().isEmpty())
                {

                    Toast.makeText(context,i18n("Enter the note requesting the release of stock"),Toast.LENGTH_SHORT).show();
                }
                else
                {
                    List<JSONObject> list_material_new = listAddMaterialAdapter.getMtaskListData();
                    Thread addDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.add_material_by_task(context, Task_Id, list_material_new, server_url, user_login);
                        if (rs.code == 200) {
                            runOnUiThread(() -> {
                                try
                                {

                                    if(Material_Export.equals("0"))
                                    {
                                        listMaterialAdapter.updateData(list_material_new);
                                    }else
                                    {
                                        listMaterialAdapter.addData(list_material_new);
                                    }


                                    List<JSONObject> list_material_create = new ArrayList<>();
                                    for (int i = 0; i < list_material_new.size(); i++)
                                    {
                                        JSONObject item = list_material_new.get(i);
                                        String material_id = item.optString("Material_Id", "");
                                        String material_qty = item.optString("Material_Qty", "");
                                        JSONObject obj = new JSONObject();
                                        try {
                                            obj.put("Item_Id", material_id);
                                            obj.put("Item_Qty", material_qty);
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                        list_material_create.add(obj);

                                    }

                                    String Request_Date_Unix = String.valueOf(TimeUtils.getUnixTimeMillisFromString(cb_select_date.getText().toString()));
                                    Thread getDataListMaterial = new Thread(() -> {
                                        HttpClient.APIReturn rs_save_request_material = HttpClient.save_request_material(view.getContext(), Request_Date_Unix, WareHouse_Id_Select, txt_note_add_material.getText().toString(), list_material_create, api_creat_export_material, user_id_login.getText().toString());
                                        if (rs_save_request_material.code == 200) {
                                            runOnUiThread(() -> {
                                                try {

                                                    List<JSONObject> task_update = new ArrayList<>();
                                                    JSONObject task_data_update = new JSONObject();
                                                    task_data_update.put("Task_Id", Task_Id);
                                                    task_data_update.put("Category_Name", "");
                                                    task_update.add(task_data_update);


                                                    Thread updateStatusMaterial = new Thread(() -> {
                                                        HttpClient.APIReturn materialrs = HttpClient.update_material_status(view.getContext(), task_update, server_url, user_login);
                                                        if (materialrs.code == 200)
                                                        {
                                                            runOnUiThread(() -> {
                                                                try {

                                                                    Material_Export = "1";
                                                                    dialog.dismiss();
                                                                    SuccessDialog(Gravity.CENTER,i18n("Success"));

                                                                } catch (Exception e) {

                                                                    throw new RuntimeException(e);
                                                                }

                                                            });

                                                        } else {

                                                            Toast.makeText(context, i18n("Update failed warehouse release request status"), Toast.LENGTH_SHORT).show();
                                                        }

                                                    });
                                                    updateStatusMaterial.start();

                                                } catch (Exception e) {

                                                    throw new RuntimeException(e);
                                                }

                                            });

                                        } else {

                                            Toast.makeText(context, i18n("Creating a warehouse release request failed"), Toast.LENGTH_SHORT).show();
                                        }

                                    });
                                    getDataListMaterial.start();


                                } catch (Exception e) {

                                    throw new RuntimeException(e);
                                }

                            });

                        } else {
                            Toast.makeText(context, i18n("Save materials list failed"), Toast.LENGTH_SHORT).show();

                        }

                    });
                    addDataListMaterial.start();
                }
            }
        });

        dialog.show();
    }

    // Hàm sao chép sâu danh sách JSONObject
    private List<JSONObject> deepCopyList(List<JSONObject> source) {
        List<JSONObject> copiedList = new ArrayList<>();
        for (JSONObject jsonObject : source) {
            copiedList.add(deepCopyJSONObject(jsonObject));
        }
        return copiedList;
    }

    // Hàm sao chép sâu một JSONObject
    private JSONObject deepCopyJSONObject(JSONObject original) {
        JSONObject copy = new JSONObject();
        Iterator<String> keys = original.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                copy.put(key, original.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return copy;
    }


    public void showDatePicker(Context context, TextView txtlabel_input, HomeFragment.OnDateSelectedListener listener) {
        Calendar calendar = Calendar.getInstance();

        // Kiểm tra nếu TextView đã có ngày trước đó
        String currentText = txtlabel_input.getText().toString();
        if (!currentText.isEmpty()) {
            String[] parts = currentText.split("/");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1; // Tháng bắt đầu từ 0
                int year = Integer.parseInt(parts[2]);
                calendar.set(year, month, day);
            }
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                context, (view, year1, month1, dayOfMonth) -> {
            // Định dạng ngày thành "dd/MM/yyyy"
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            calendar.set(year1, month1, dayOfMonth);
            String formattedDate = sdf.format(calendar.getTime());
            listener.onDateSelected(formattedDate);
            txtlabel_input.setText(formattedDate);
        }, year, month, day);

        // Giới hạn ngày (tùy chọn)
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis()); // Không cho chọn ngày quá khứ
        // datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 7)); // Giới hạn 7 ngày tới

        datePickerDialog.show();
    }

    public static List<JSONObject> convertJSONArrayToList(JSONArray jsonArray) throws JSONException {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            list.add(jsonArray.getJSONObject(i));
        }
        return list;
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
            }
        }, 1200);

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
        txtnoidung.setText(i18n(noidung));
        tvTitle.setText(i18n(tieude));
        Window window = dialog.getWindow();
        btnOK.setText(i18n("Complete"));
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


                List<JSONObject> data_select = new ArrayList<>();
                JSONObject jsonObject = new JSONObject();
                try
                {
                    if(isEditTextEmpty(txt_Current_Situation) && Task_Type.equals("0"))
                    {
                        Toast.makeText(getBaseContext(),i18n("Fill in the current situation information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Root_Cause) && Task_Type.equals("0"))
                    {
                        Toast.makeText(getBaseContext(),i18n("Fill in the reason information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Action_Taken) && Task_Type.equals("0"))
                    {
                        Toast.makeText(getBaseContext(),i18n("Fill in the action taken information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Countermeasure) && Task_Type.equals("0"))
                    {
                        Toast.makeText(getBaseContext(),i18n("Fill in countermeasure information"),Toast.LENGTH_SHORT).show();
                    }
                    else
                    {

                        JSONArray list_pre_step = adapter_pre.getUserInputAsJsonArray(Task_Id);
                        JSONArray list_post_step = adapter_post.getUserInputAsJsonArray(Task_Id);

                        jsonObject.put("Task_Id", Task_Id);
                        jsonObject.put("Category_Name", "");
                        data_select.add(jsonObject);
                        if (!selectedFileUris.isEmpty() || !imageAdapter.getlistFileDel().isEmpty())
                        {
                            Thread saveDataTaskWithFile = new Thread(() ->
                            {
                                HttpClient.APIReturn rs = HttpClient.save_end_task_file_data(view.getContext(),list_pre_step,list_post_step, data_select, selectedFileUris, imageAdapter.getmselectedFileUris(), imageAdapter.getlistFileDel(), txt_note.getText().toString(), txt_Current_Situation.getText().toString(), txt_Root_Cause.getText().toString(), txt_Action_Taken.getText().toString(), txt_Countermeasure.getText().toString(), server_url, user_login);
                                if (rs.code == 200) {
                                    runOnUiThread(() -> {
                                        try {
                                            dialog.dismiss();
                                            finish();

                                        } catch (Exception e) {

                                            throw new RuntimeException(e);
                                        }

                                    });

                                }

                            });
                            saveDataTaskWithFile.start();
                        }
                        else
                        {

                            JSONArray list_pre_step_ = adapter_pre.getUserInputAsJsonArray(Task_Id);
                            JSONArray list_post_step_ = adapter_post.getUserInputAsJsonArray(Task_Id);

                            Thread saveDataTaskNoFile = new Thread(() ->
                            {
                                HttpClient.APIReturn rs = HttpClient.save_end_task_data(view.getContext(),list_pre_step_,list_post_step_, data_select, txt_note.getText().toString(), txt_Current_Situation.getText().toString(), txt_Root_Cause.getText().toString(), txt_Action_Taken.getText().toString(), txt_Countermeasure.getText().toString(), server_url, user_login);
                                if (rs.code == 200) {
                                    runOnUiThread(() -> {
                                        try {
                                            dialog.dismiss();
                                            finish();

                                        } catch (Exception e) {

                                            throw new RuntimeException(e);
                                        }

                                    });

                                }

                            });
                            saveDataTaskNoFile.start();
                        }

                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);}


            }
        });

        dialog.show();
    }

    private void PauseWorkDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_progress);
        final int stepSize = 5; // Step size
        TextView btncancel = (TextView) dialog.findViewById(R.id.btnCancel);
        TextView btnOK = (TextView) dialog.findViewById(R.id.btnConfirm);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.tvQuestion);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        TextView tvProgress = (TextView) dialog.findViewById(R.id.tvProgress);
        SeekBar seekBar = dialog.findViewById(R.id.seekBar);
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
                boolean check_feil = true;
                if(tvProgress.getText().toString().equals("100%") && Task_Type.equals("0"))
                {
                    if(isEditTextEmpty(txt_Current_Situation))
                    {
                        check_feil = false;
                        Toast.makeText(getBaseContext(),i18n("Fill in the current situation information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Root_Cause))
                    {
                        check_feil = false;
                        Toast.makeText(getBaseContext(),i18n("Fill in the reason information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Action_Taken))
                    {
                        check_feil = false;
                        Toast.makeText(getBaseContext(),i18n("Fill in the action taken information"),Toast.LENGTH_SHORT).show();
                    }else
                    if(isEditTextEmpty(txt_Countermeasure))
                    {
                        check_feil = false;
                        Toast.makeText(getBaseContext(),i18n("Fill in countermeasure information"),Toast.LENGTH_SHORT).show();
                    }

                }

                if(!check_feil)
                {
                    return;
                }

                if(!selectedFileUris.isEmpty() || !imageAdapter.getlistFileDel().isEmpty())
                {
                    JSONArray list_pre_step = adapter_pre.getUserInputAsJsonArray(Task_Id);
                    JSONArray list_post_step = adapter_post.getUserInputAsJsonArray(Task_Id);
                    Thread getDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.pause_task_file_by_user(view.getContext(),list_pre_step,list_post_step,Task_Id,tvProgress.getText().toString().replace("%",""),imageAdapter.getmselectedFileUris(),imageAdapter.getlistFileDel(),selectedFileUris,txt_note.getText().toString(),txt_Current_Situation.getText().toString(),txt_Root_Cause.getText().toString(),txt_Action_Taken.getText().toString(),txt_Countermeasure.getText().toString(),server_url, user_login);
                        if (rs.code == 200)
                        {
                            runOnUiThread(() -> {
                                try
                                {
                                    dialog.dismiss();
                                    finish();

                                } catch (Exception e)
                                {

                                    throw new RuntimeException(e);
                                }

                            });

                        }

                    });
                    getDataListMaterial.start();
                }else
                {
                    JSONArray list_pre_step = adapter_pre.getUserInputAsJsonArray(Task_Id);
                    JSONArray list_post_step = adapter_post.getUserInputAsJsonArray(Task_Id);

                    Thread getDataListMaterial = new Thread(() -> {
                        HttpClient.APIReturn rs = HttpClient.pause_task_by_user(view.getContext(),list_pre_step,list_post_step,Task_Id,tvProgress.getText().toString().replace("%",""),txt_note.getText().toString(),txt_Current_Situation.getText().toString(),txt_Root_Cause.getText().toString(),txt_Action_Taken.getText().toString(),txt_Countermeasure.getText().toString() ,server_url, user_login);
                        if (rs.code == 200)
                        {
                            runOnUiThread(() -> {
                                try
                                {
                                    dialog.dismiss();
                                    finish();

                                } catch (Exception e)
                                {

                                    throw new RuntimeException(e);
                                }

                            });

                        }

                    });
                    getDataListMaterial.start();
                }


            }
        });


        seekBar.setProgress(Integer.parseInt(progress_value));
        tvProgress.setText(progress_value+"%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int steppedProgress = (progress / stepSize) * stepSize; // Round to nearest step
                seekBar.setProgress(steppedProgress); // Set adjusted progress
                tvProgress.setText(String.valueOf(steppedProgress) + "%");
                if(steppedProgress == 100)
                {
                    btnOK.setText(i18n("Complete task"));
                }else
                {
                    btnOK.setText(i18n("Pause task"));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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

    public boolean isEditTextEmpty(EditText editText) {
        if (editText == null) return true;

        String input = editText.getText().toString().trim().replaceAll("\\s+", "");
        return input.isEmpty();
    }

    @Override
    public void onResume() {

        super.onResume();
        user_id_login.setText(user_login);
        lable_screen_header.setText(i18n("MAINTENANCE MANAGEMENT SYSTEM"));
        txtsteplabel.setText(i18n("Maintenance steps"));
        txt_lable_show_list.setText(i18n("See more"));
        txt_requests_arise_lable.setText(i18n("Requests arise"));
        txtlabel_note.setText(i18n("Note"));
        txtlabel_image.setText(i18n("Maintenance photo"));
        txt_lable_upload.setText(i18n("Upload file"));
        txt_lable_table_material.setText(i18n("Materials list"));
        txt_note.setHint(i18n("Fill in note information"));
        pickImageBtn.setLabel(i18n("Click here"));
        pickImageBtn.setLabel2(i18n("to upload image files"));
        btn_material_lable.setText(i18n("Add material"));
        txt_no.setText(i18n("No."));
        materialCode.setText(i18n("Material code"));
        materialName.setText(i18n("Material name"));
        material_qty.setText(i18n("Quantity"));
        materialUnit.setText(i18n("Unit"));
        btn_startwork_lable.setText(i18n("Start maintenance"));
        btn_dowork_lable.setText(i18n("Pause task"));
        btn_endwork_lable.setText(i18n("Complete task"));
        txt_lable_machine_infor.setText((i18n("Machine code")+" - "+i18n("Machine name")));
        txtlabel_Current_Situation.setText(i18n("Current situation"));
        txtlabel_Root_Cause.setText(i18n("Root cause"));
        txtlabel_Action_Taken.setText(i18n("Action taken"));
        txtlabel_Countermeasure.setText(i18n("Countermeasure"));
        txt_Current_Situation.setHint(i18n("Fill in the current situation information"));
        txt_Root_Cause.setHint(i18n("Fill in the reason information"));
        txt_Action_Taken.setHint(i18n("Fill in the action taken information"));
        txt_Countermeasure.setHint(i18n("Fill in countermeasure information"));

        Glide.get(this).clearMemory();
        new Thread(() -> {
            Glide.get(this).clearDiskCache();
        }).start();



    }


}