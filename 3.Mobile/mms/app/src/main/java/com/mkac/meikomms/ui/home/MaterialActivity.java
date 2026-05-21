package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.ui.taskdetail.ListMaterialAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MaterialActivity extends AppCompatActivity {

    private String api_creat_export_material = "";
    private String server_url = "";
    private String user_login="";
    private String WareHouse_Id_Select = "";
    private RelativeLayout btn_back_home,btn_export_material;
    private ImageView btn_back;
    private  ListMaterialAdapter listMaterialAdapter;
    public List<JSONObject> data_material_list = new ArrayList<>();
    private RecyclerView rcv_list_material;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> Listwarehouse;
    private ArrayList<String> Listmachine;
    HashMap<String, String> warehouseMap; // Lưu Wh_Id_Name -> Wh_Id
    List<JSONObject> materialList = new ArrayList<>();
    List<JSONObject> warehouseList = new ArrayList<>();

    private TextView lable_screen_header;
    private TextView txt_no;
    private TextView materialCode;
    private TextView materialName;
    private TextView material_qty;
    private TextView materialUnit;
    private TextView user_id_login;
    private TextView btn_creat_material_export_lable;
    private TextView btn_back_lable;
    private TextView txt_lable_show_list;
    private TextView txtlabel_note;
    private TextView txt_lable_table_material;

    private TextView txtwarehouseinformation;
    private TextView txt_lable_warehouse;
    private AutoCompleteTextView cb_select_warehouse;
    private ImageView btn_select_warehouse;

    private TextView txt_lable_date;
    private AutoCompleteTextView cb_select_date;
    private ImageView btn_show_datepicker;

    private TextView txt_note ;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_material);
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        user_id_login = findViewById(R.id.user_id_login);
        btn_back_home = findViewById(R.id.btn_back_home);
        btn_export_material = findViewById(R.id.btn_export_material);
        btn_back = findViewById(R.id.btn_back);
        rcv_list_material = findViewById(R.id.rcv_list_material);
        lable_screen_header = findViewById(R.id.lable_screen_header);
        txt_no = findViewById(R.id.txt_no);
        materialCode = findViewById(R.id.materialCode);
        materialName = findViewById(R.id.materialName);
        material_qty = findViewById(R.id.material_qty);
        materialUnit = findViewById(R.id.materialUnit);
        btn_creat_material_export_lable = findViewById(R.id.btn_creat_material_export_lable);
        btn_back_lable = findViewById(R.id.btn_back_lable);
        txt_lable_show_list = findViewById(R.id.txt_lable_show_list);
        txtlabel_note = findViewById(R.id.txtlabel_note);
        txt_lable_table_material = findViewById(R.id.txt_lable_table_material);
        txtwarehouseinformation = findViewById(R.id.txtwarehouseinformation);
        txt_lable_warehouse = findViewById(R.id.txt_lable_warehouse);
        cb_select_warehouse = findViewById(R.id.cb_select_warehouse);
        btn_select_warehouse = findViewById(R.id.btn_select_warehouse);

        txt_lable_date = findViewById(R.id.txt_lable_date);
        cb_select_date = findViewById(R.id.cb_select_date);
        btn_show_datepicker = findViewById(R.id.btn_show_datepicker);
        txt_note = findViewById(R.id.txt_note);


        Listwarehouse = new ArrayList<>();
        warehouseMap = new HashMap<>();


        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        rcv_list_material.setLayoutManager(new LinearLayoutManager(this));
        api_creat_export_material = configManager.getProperty("api_creat_export_material");
        server_url = configManager.getProperty("server_url");


        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }
        if (!handler.getString("api_creat_export_material").isEmpty()) {
            api_creat_export_material = handler.getString("api_creat_export_material");
        }

        if(configManager.getProperty("vertical_lock").equals("true"))
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }


        String datarecive = getIntent().getStringExtra("DataSend");
        String mydata = datarecive.toString();

        try
        {

            JSONArray jsonArray = new JSONArray(mydata);
            for (int i = 0; i < jsonArray.length(); i++) {
                materialList.add(jsonArray.getJSONObject(i));
            }

        }
        catch (JSONException e)
        {
            throw new RuntimeException(e);
        }

        btn_back_home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btn_export_material.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(data_material_list.size() == 0)
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Blank material list"));
                }else  if( cb_select_date.getText().toString().isEmpty())
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("No completion date selected"));
                }else  if(txt_note.getText().toString().isEmpty())
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Enter the note requesting the release of stock"));
                }else
                {
                    CreatExportMaterialDialog(Gravity.CENTER,"Create a warehouse request","Do you want to create this warehouse request ?");
                }

            }
        });




        cb_select_warehouse.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter.getItem(position);
            cb_select_warehouse.setText(selectedItem); // Cập nhật giá trị khi chọn
            WareHouse_Id_Select = warehouseMap.get(selectedItem);
            cb_select_warehouse.setSelection(cb_select_warehouse.getText().length());
        });


        btn_select_warehouse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                cb_select_warehouse.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_warehouse.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_warehouse.postDelayed(cb_select_warehouse::showDropDown, 200);

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
        }, 1000);

    }

    private List<JSONObject> getMaterialList(String jsonData) throws JSONException
    {
        JSONArray jsonArray = new JSONArray(jsonData);
        HashMap<String, JSONObject> materialMap = new HashMap<>();

        // Duyệt qua danh sách vật liệu và tổng hợp số lượng theo Material_Id
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String materialId = obj.getString("Material_Id");
            int qty = Integer.parseInt(obj.getString("Material_Qty"));

            if (materialMap.containsKey(materialId)) {
                // Nếu vật liệu đã tồn tại, cập nhật số lượng
                JSONObject existingObj = materialMap.get(materialId);
                int updatedQty = existingObj.getInt("Material_Qty") + qty;
                existingObj.put("Material_Qty", updatedQty);
            } else {
                // Nếu chưa có, thêm mới vào danh sách tổng hợp
                materialMap.put(materialId, new JSONObject(obj.toString()));
            }
        }

        // Chuyển HashMap thành List<JSONObject>
        List<JSONObject> resultList = new ArrayList<>(materialMap.values());

        return resultList;


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


    private void CreatExportMaterialDialog(int gravity, String tieude, String noidung)
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
        btnOK.setText(i18n("Yes"));
        btncancel.setText(i18n("No"));
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
                if(data_material_list.size() > 0)
                {
                    List<JSONObject> list_material_create = new ArrayList<>();
                    for (int i = 0; i < data_material_list.size(); i++) {
                        JSONObject item = data_material_list.get(i);
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
                        HttpClient.APIReturn rs = HttpClient.save_request_material(view.getContext(), Request_Date_Unix, WareHouse_Id_Select, txt_note.getText().toString(), list_material_create, api_creat_export_material, user_id_login.getText().toString());
                        if (rs.code == 200) {
                            runOnUiThread(() -> {
                                try {

                                    SuccessDialog(Gravity.CENTER, i18n("Success"));
                                    Thread updateStatusMaterial = new Thread(() -> {
                                        HttpClient.APIReturn materialrs = HttpClient.update_material_status(view.getContext(), materialList, server_url, user_login);
                                        if (materialrs.code == 200) {
                                            runOnUiThread(() -> {
                                                try {

                                                    new Handler().postDelayed(() -> {
                                                        if (dialog.isShowing())
                                                        {
                                                            dialog.dismiss();
                                                            finish();
                                                        }
                                                    }, 1000);


                                                } catch (Exception e) {

                                                    throw new RuntimeException(e);
                                                }

                                            });

                                        }

                                    });
                                    updateStatusMaterial.start();

                                } catch (Exception e) {

                                    throw new RuntimeException(e);
                                }

                            });

                        } else {
                            runOnUiThread(() -> {
                                tvTitle.setText(i18n("ERROR"));
                                txtnoidung.setText(i18n("Error creating warehouse release request"));
                                btnOK.setText(i18n("Retry"));
                                btncancel.setText(i18n("Close"));
                            });
                        }

                    });
                    getDataListMaterial.start();
                }else
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Blank material list"));
                }

            }
        });


        dialog.show();
    }

    public static List<JSONObject> convertJSONArrayToList(JSONArray jsonArray) throws JSONException {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            list.add(jsonArray.getJSONObject(i));
        }
        return list;
    }


    private List<JSONObject> deepCopyList(List<JSONObject> source) {
        List<JSONObject> copiedList = new ArrayList<>();
        for (JSONObject jsonObject : source) {
            copiedList.add(deepCopyJSONObject(jsonObject));
        }
        return copiedList;
    }

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

        public static void replaceMaterialKeys(Map<String, Object> data) {
            if (data.containsKey("Material_Id")) {
                Object value = data.remove("Material_Id");
                data.put("Item_Id", value);
            }

            if (data.containsKey("Material_Qty")) {
                Object value = data.remove("Material_Qty");
                data.put("Item_Qty", value);
            }
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

    @Override
    public void onResume() {

        super.onResume();
        user_id_login.setText(user_login);
        lable_screen_header.setText(i18n("MATERIALS LIST"));

        txt_no.setText(i18n("No."));
        materialCode.setText(i18n("Material code"));
        materialName.setText(i18n("Material name"));
        material_qty.setText(i18n("Quantity"));
        materialUnit.setText(i18n("Unit"));
        btn_creat_material_export_lable.setText(i18n("Create a warehouse request"));
        btn_back_lable.setText(i18n("Back"));
        txtwarehouseinformation.setText(i18n("Warehouse Dispatch Information"));
        txt_lable_warehouse.setText(i18n("Select warehouse supplies"));
        txt_lable_date.setText(i18n("Required Completion Date"));
        txt_lable_show_list.setText(i18n("Collapse"));
        txtlabel_note.setText(i18n("Note"));
        txt_lable_table_material.setText(i18n("Materials list"));
        txt_note.setText(i18n("Fill in note information"));
        cb_select_warehouse.setHint(i18n("Select information"));

        Thread getDataListMaterial = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_list_material_warehouse_data(this,materialList, server_url, user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (!rs.data.isEmpty())
                        {

                            JSONArray materialsArray = rs.data.get(0).getJSONArray("Materials");
                            JSONArray warehousesArray = rs.data.get(0).getJSONArray("Warehouses");

                            data_material_list = convertJSONArrayToList(materialsArray);
                            List<JSONObject> warehousesList = convertJSONArrayToList(warehousesArray);

                            listMaterialAdapter = new ListMaterialAdapter(data_material_list, this);
                            rcv_list_material.setAdapter(listMaterialAdapter);


                            for (int j = 0; j < warehousesList.size(); j++)
                            {

                                Listwarehouse.add(warehousesList.get(j).optString("Wh_Id_Name",""));
                                warehouseMap.put(warehousesList.get(j).optString("Wh_Id_Name",""), warehousesList.get(j).optString("Wh_Id",""));
                            }

                            if(Listwarehouse.size() > 0)
                            {
                                adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, Listwarehouse);
                                cb_select_warehouse.setAdapter(adapter);


                                cb_select_warehouse.setText(adapter.getItem(0));
                                cb_select_warehouse.setSelection(cb_select_warehouse.getText().length());
                                WareHouse_Id_Select = warehouseMap.get(cb_select_warehouse.getText().toString());

                            }

                        }
                        else
                        {


                        }


                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }
            else
            {
                ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Error while getting list of materials"));
            }

        });
        getDataListMaterial.start();

    }


}