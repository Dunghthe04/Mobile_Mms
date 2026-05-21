package com.mkac.meikomms.ui.home;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.ui.taskdetail.TaskDetailActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class HistoryMachineActivity extends AppCompatActivity {


    private String server_url = "";
    private String user_login="";
    private String Machine_Id_Select = "";
    private String Tasktype_Id_Select = "";

    HashMap<String, String> machineMap;
    private ArrayList<String> Listmachine;
    private ArrayAdapter<String> adapter;
    private List<JSONObject> data_task_list_history_machine = new ArrayList<>();
    HashMap<String, String> tasktypeMap;
    private ArrayList<String> Listtasktype;
    private ArrayAdapter<String> tasktypeadapter;


    private ImageView btn_back;
    private TextView txtlabel_screen_name;
    private TextView txt_lable_machine;
    private AutoCompleteTextView cb_select_machine;
    private ImageView btn_select_machine;
    private TextView txt_from_lable;
    private TextView txtlabel_date;
    private ImageView btn_select_date;
    private TextView txt_todate;
    private TextView txtlabel_to_date;
    private ImageView btn_select_to_date;
    private TextView txt_lable_type_machine;
    private TextView txt_type_machine;
    private TextView txt_task_type_lable;
    private AutoCompleteTextView cb_select_task_type;
    private ImageView btn_select_task_type;
    private TextView txt_lable_table;
    private FlexboxLayout rcv_header;
    private TextView txt_lable_date;
    private TextView taskType;
    private TextView machineCode;
    private TextView maintenanceName;
    private TextView material;
    private TextView progess;
    private RecyclerView rcv_list_history_machine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history_machine);
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        btn_back = findViewById(R.id.btn_back);
        txtlabel_screen_name = findViewById(R.id.txtlabel_screen_name);
        txt_lable_machine = findViewById(R.id.txt_lable_machine);
        cb_select_machine = findViewById(R.id.cb_select_machine);
        btn_select_machine = findViewById(R.id.btn_select_machine);
        txt_from_lable = findViewById(R.id.txt_from_lable);
        txtlabel_date = findViewById(R.id.txtlabel_date);
        btn_select_date = findViewById(R.id.btn_select_date);
        txt_todate = findViewById(R.id.txt_todate);
        txtlabel_to_date = findViewById(R.id.txtlabel_to_date);
        btn_select_to_date = findViewById(R.id.btn_select_to_date);
        txt_lable_type_machine = findViewById(R.id.txt_lable_type_machine);
        txt_type_machine = findViewById(R.id.txt_type_machine);
        txt_task_type_lable = findViewById(R.id.txt_task_type_lable);
        cb_select_task_type = findViewById(R.id.cb_select_task_type);
        btn_select_task_type = findViewById(R.id.btn_select_task_type);
        txt_lable_table = findViewById(R.id.txt_lable_table);
        rcv_header = findViewById(R.id.rcv_header);
        txt_lable_date = findViewById(R.id.txt_lable_date_header);
        taskType = findViewById(R.id.taskType);
        machineCode = findViewById(R.id.machineCode);
        maintenanceName = findViewById(R.id.maintenanceName);
        material = findViewById(R.id.material);
        progess = findViewById(R.id.progess);
        rcv_list_history_machine = findViewById(R.id.rcv_list_history_machine);

        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);
        rcv_list_history_machine.setLayoutManager( new LinearLayoutManager(this));
        server_url = configManager.getProperty("server_url");


        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }

        Listmachine = new ArrayList<>();
        machineMap = new HashMap<>();

        tasktypeMap = new HashMap<>();
        Listtasktype = new ArrayList<>();

        txtlabel_date.setText(TimeUtils.getFirstDayOfMonth("dd/MM/yyyy"));

        txtlabel_to_date.setText(TimeUtils.getLastDayOfMonth("dd/MM/yyyy"));

        if(configManager.getProperty("vertical_lock").equals("true"))
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

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

                                if (!Listmachine.isEmpty())
                                {
//                                    cb_select_machine.setText(adapter.getItem(0));
//                                    cb_select_machine.setSelection(cb_select_machine.getText().length());
//                                    Machine_Id_Select = machineMap.get(cb_select_machine.getText().toString());

                                }

                                load();

                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataListMachine.start();


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
            load();
        });


        btn_select_machine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                cb_select_machine.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_machine.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_machine.postDelayed(cb_select_machine::showDropDown, 200);

            }
        });



        List<String> items = Arrays.asList(i18n("All"),i18n("Repair"), i18n("Maintenance"));
        tasktypeMap.put(items.get(0), "");
        tasktypeMap.put(items.get(1), "0");
        tasktypeMap.put(items.get(2), "1");

        // Adapter cho AutoCompleteTextView
        tasktypeadapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, items);
        cb_select_task_type.setAdapter(tasktypeadapter);



        // Chặn nhập nội dung thủ công
        cb_select_task_type.setInputType(InputType.TYPE_NULL);
        cb_select_task_type.setKeyListener(null);

        cb_select_task_type.setText(tasktypeadapter.getItem(0));
        cb_select_task_type.setSelection(cb_select_task_type.getText().length());

        // Mở dropdown khi nhấn vào AutoCompleteTextView
        cb_select_task_type.setOnClickListener(v -> cb_select_task_type.showDropDown());


        // Bắt sự kiện khi chọn một mục
        cb_select_task_type.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = tasktypeadapter.getItem(position);
            cb_select_task_type.setText(selectedItem, false);
            Tasktype_Id_Select = tasktypeMap.get(selectedItem);
            load();
        });


        btn_select_task_type.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                cb_select_task_type.setText(""); // Xóa text nhập trước đó để không lọc danh sách
                cb_select_task_type.requestFocus(); // Giữ focus để danh sách không bị mất
                cb_select_task_type.postDelayed(cb_select_task_type::showDropDown, 200);

            }
        });

        txtlabel_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(view.getContext(), txtlabel_date, new HomeFragment.OnDateSelectedListener() {
                    @Override
                    public void onDateSelected(String formattedDate)
                    {
                        rcv_list_history_machine.setAdapter(null);
                        load();
                    }
                });
            }
        });

        txtlabel_to_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(view.getContext(), txtlabel_to_date, new HomeFragment.OnDateSelectedListener() {
                    @Override
                    public void onDateSelected(String formattedDate)
                    {
                        rcv_list_history_machine.setAdapter(null);
                        load();
                    }
                });
            }
        });

        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
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


    private void load()
    {
        Thread getDataListTaskEnd = new Thread(() -> {
            HttpClient.APIReturn rs = HttpClient.get_list_history_by_machine(this,Machine_Id_Select,convertDateFormat(txtlabel_date.getText().toString()),convertDateFormat(txtlabel_to_date.getText().toString()),Tasktype_Id_Select, server_url, user_login);
            if (rs.code == 200)
            {
                runOnUiThread(() -> {
                    try {

                        if (rs.data != null)
                        {


                            data_task_list_history_machine = fillter_task_type(rs.data,Tasktype_Id_Select);

                            if(!data_task_list_history_machine.isEmpty())
                            {

                                String Machine_Type_Id = data_task_list_history_machine.get(0).getString("Machine_Type_Id");
                                txt_type_machine.setText(Machine_Type_Id);
                                ListTaskHistoryMachineAdapter listTaskHistoryMachineAdapter = new ListTaskHistoryMachineAdapter(data_task_list_history_machine, this, new ListTaskAdapter.OnItemClickListener()
                                {
                                    @Override
                                    public void onItemClick(View view, int position) throws JSONException
                                    {
                                        JSONObject jsonObject = data_task_list_history_machine.get(position);
                                        String Category_Id = jsonObject.optString("Task_Id");
                                        String Category_Name = jsonObject.optString("Category_Name");
                                        String Status = jsonObject.optString("Status");
                                        String Image_List = jsonObject.optString("Image_List");
                                        String Task_Type = jsonObject.optString("Task_Type");
                                        JSONObject new_list = new JSONObject();
                                        new_list.put("Task_Id",Category_Id);
                                        new_list.put("Task_Type",Task_Type);
                                        new_list.put("Category_Name",Category_Name);
                                        new_list.put("Status",Status);
                                        new_list.put("Image_List",Image_List);
                                        Intent intent = new Intent(view.getContext(), TaskDetailActivity.class);
                                        intent.putExtra("Datasend",String.valueOf(new_list));
                                        startActivity(intent);
                                    }
                                });
                                rcv_list_history_machine.setAdapter(listTaskHistoryMachineAdapter);

                            }
                            else
                            {
                                rcv_list_history_machine.setAdapter(null);
                                txt_type_machine.setText("");
                            }

                        }

                    } catch (Exception e) {

                        throw new RuntimeException(e);
                    }

                });

            }

        });
        getDataListTaskEnd.start();
    }


    private List<JSONObject> fillter_task_type(List<JSONObject> data,String task_type)
    {

        List<JSONObject> dataTemp = new ArrayList<>();

        for (JSONObject jsonObject : data)
        {
            try {

                {



                    String tasktype = jsonObject.optString("Task_Type");


                    if(task_type.equals(""))
                    {
                        dataTemp.add(jsonObject);
                    }
                    else if(task_type.equals("0"))
                    {
                        if(tasktype.equals("0"))
                        {
                            dataTemp.add(jsonObject);
                        }

                    }
                    else if(task_type.equals("1"))
                    {
                        if(tasktype.equals("1"))
                        {
                            dataTemp.add(jsonObject);
                        }

                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return dataTemp;

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
        // datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis()); // Không cho chọn ngày quá khứ
        // datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 7)); // Giới hạn 7 ngày tới

        datePickerDialog.show();
    }

    public static String convertDateFormat(String inputDate)
    {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        try {
            Date date = inputFormat.parse(inputDate); // Chuyển đổi chuỗi thành đối tượng Date
            return outputFormat.format(date); // Định dạng lại Date thành chuỗi mới
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Trả về null nếu có lỗi
        }
    }

    @Override
    public void onResume() {

        super.onResume();

        txt_from_lable.setText(i18n("From"));
        txt_todate.setText(i18n("To"));
        txt_lable_machine.setText(i18n("Machine code")+" - "+i18n("Machine name"));
        txt_lable_type_machine.setText(i18n("Machine type"));
        txt_task_type_lable.setText(i18n("Job type"));
        txt_lable_table.setText(i18n("List of tasks"));
        cb_select_machine.setHint(i18n("Select information"));
        cb_select_task_type.setHint(i18n("Select information"));
        txt_lable_date.setText(i18n("Date"));
        taskType.setText(i18n("Type"));
        machineCode.setText(i18n("Machine code"));
        maintenanceName.setText(i18n("Category Name"));
        material.setText(i18n("Materials"));
        progess.setText(i18n("Progress"));
        txtlabel_screen_name.setText(i18n("Machine repair and maintenance history"));






    }

}