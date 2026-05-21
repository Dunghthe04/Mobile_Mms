package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.net.ParseException;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mkac.meikomms.MainActivity;
import com.mkac.meikomms.R;
import com.mkac.meikomms.ViewPagerAdapter;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.ui.login.LoginActivity;
import com.mkac.meikomms.ui.taskdetail.ListIntructionAdapter;
import com.mkac.meikomms.ui.taskdetail.ListMaterialAdapter;
import com.mkac.meikomms.ui.taskdetail.TaskDetailActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class HomeFragment extends Fragment
{

    private String server_url = "";
    private String user_login="";
    private String approve_value="2";
    private ArrayList<String> fillter = new ArrayList<>();

    private String ck2_str = "0"; // Trạng thái chờ nhận
    private String ck1_str = "1"; // Trạng thái đang thực hiện
    private SwipeRefreshLayout swipeRefreshLayoutparam;
    private SwipeRefreshLayout swipe_refresh_layout_param_end_work;
    public static final int RESULT_OK = -1;
    private Boolean ck1 = true;
    private Boolean ck2 = true;
    private Boolean ck3 = true;
    private Boolean ck4 = true;
    private Boolean ck5 = true;
    private Boolean ck6 = true;


    private RelativeLayout btn_do_work,btn_end_work,btn_get_list_material,btn_add_new_error,btn_approve;
    private TextView btn_tab_1, btn_tab_2,btn_approve_lable;
    private EditText txt_find;
    private EditText txt_find_tab2;
    private TextView line_tab_1, line_tab_2;
    private TextView txt_material_number,btn_material_lable;
    private RelativeLayout layout_tab1,layout_tab2,btn_history_machine;
    private TextView txtlabel_date,txtlabel_to_date;
    private TextView txt_lable_ckeck3,txt_lable_ckeck2,txt_lable_ckeck1,txt_lable_ckeck4,txt_lable_ckeck5,txt_lable_ckeck6;
    private ImageView btn_select_date,btn_select_to_date,ckeck1,ckeck2,ckeck3,ckeck4,ckeck5,ckeck6;
    public List<JSONObject> data_task_list = new ArrayList<>();
    public List<JSONObject> data_task_list_end = new ArrayList<>();
    private ListTaskMonthAdapter listTaskMonthAdapter;
    private ListTaskMonthEndAdapter listTaskMonthEndAdapter;
    private RecyclerView rcv_list_task_month,rcv_list_task_succees;

    private TextView txtlabel_Directivecode,txt_add_new_error,btn_dowork_lable,btn_complete_lable,txt_from_lable,txt_todate,txt_history_machine;



    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View focusedView = requireActivity().getCurrentFocus();
                if (focusedView instanceof EditText) {
                    Rect outRect = new Rect();
                    focusedView.getGlobalVisibleRect(outRect);
                    if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        focusedView.clearFocus();
                        hideKeyboard(focusedView);
                    }
                }
            }
            return false;
        });
        swipeRefreshLayoutparam = view.findViewById(R.id.swipe_refresh_layout_param);
        swipe_refresh_layout_param_end_work = view.findViewById(R.id.swipe_refresh_layout_param_end_work);
        txt_from_lable = view.findViewById(R.id.txt_from_lable);
        txt_todate = view.findViewById(R.id.txt_todate);
        txtlabel_Directivecode = view.findViewById(R.id.txtlabel_Directivecode);
        txt_add_new_error = view.findViewById(R.id.txt_add_new_error);
        txt_material_number = view.findViewById(R.id.txt_material_number);
        btn_select_date = view.findViewById(R.id.btn_select_date);
        btn_select_to_date = view.findViewById(R.id.btn_select_to_date);
        btn_add_new_error = view.findViewById(R.id.btn_add_new_error);
        btn_approve = view.findViewById(R.id.btn_approve);
        btn_approve_lable = view.findViewById(R.id.btn_approve_lable);
        txtlabel_date = view.findViewById(R.id.txtlabel_date);
        txtlabel_to_date = view.findViewById(R.id.txtlabel_to_date);
        txt_find = view.findViewById(R.id.txt_find);
        txt_find_tab2 = view.findViewById(R.id.txt_find_tab2);
        btn_tab_1 = view.findViewById(R.id.btn_tab_1);
        btn_tab_2 = view.findViewById(R.id.btn_tab_2);
        line_tab_1 = view.findViewById(R.id.line_tab_1);
        line_tab_2 = view.findViewById(R.id.line_tab_2);
        layout_tab1 = view.findViewById(R.id.layout_tab1);
        layout_tab2 = view.findViewById(R.id.layout_tab2);
        btn_do_work = view.findViewById(R.id.btn_do_work);
        btn_end_work = view.findViewById(R.id.btn_end_work);
        btn_material_lable = view.findViewById(R.id.btn_material_lable);
        btn_dowork_lable = view.findViewById(R.id.btn_dowork_lable);
        btn_complete_lable = view.findViewById(R.id.btn_endwork_lable);
        btn_get_list_material = view.findViewById(R.id.btn_get_list_material);
        ckeck1 = view.findViewById(R.id.ckeck1);
        ckeck2 = view.findViewById(R.id.ckeck2);
        ckeck3 = view.findViewById(R.id.ckeck3);
        ckeck4 = view.findViewById(R.id.ckeck4);
        ckeck5 = view.findViewById(R.id.ckeck5);
        ckeck6 = view.findViewById(R.id.ckeck6);
        txt_lable_ckeck1 = view.findViewById(R.id.txt_lable_ckeck1);
        txt_lable_ckeck2 = view.findViewById(R.id.txt_lable_ckeck2);
        txt_lable_ckeck3 = view.findViewById(R.id.txt_lable_ckeck3);
        txt_lable_ckeck4 = view.findViewById(R.id.txt_lable_ckeck4);
        txt_lable_ckeck5 = view.findViewById(R.id.txt_lable_ckeck5);
        txt_lable_ckeck6 = view.findViewById(R.id.txt_lable_ckeck6);
        rcv_list_task_month = view.findViewById(R.id.rcv_list_task_month);
        rcv_list_task_succees = view.findViewById(R.id.rcv_list_task_succees);
        btn_history_machine = view.findViewById(R.id.btn_history_machine);
        txt_history_machine = view.findViewById(R.id.txt_history_machine);

        rcv_list_task_month.setLayoutManager(new LinearLayoutManager(getContext()));
        rcv_list_task_succees.setLayoutManager(new LinearLayoutManager(getContext()));

        PreferenceHandler handler = new PreferenceHandler(getContext());
        ConfigManager configManager = new ConfigManager(getContext());

        server_url = configManager.getProperty("server_url");
        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }


        txtlabel_date.setText(TimeUtils.getFirstDayOfMonth("dd/MM/yyyy"));

        txtlabel_to_date.setText(TimeUtils.getLastDayOfMonth("dd/MM/yyyy"));

        btn_do_work.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if (listTaskMonthAdapter != null)
                {
                    List<JSONObject> data_select = listTaskMonthAdapter.getListItemSelect();
                    if (data_select.size() > 0)
                    {
                        Thread getDataListMaterial = new Thread(() -> {
                            HttpClient.APIReturn rs = HttpClient.select_task_by_user(getContext(),data_select, server_url, user_login);
                            if (rs.code == 200)
                            {
                                getActivity().runOnUiThread(() -> {
                                    try
                                    {
                                        rcv_list_task_month.setAdapter(null);
                                        rcv_list_task_succees.setAdapter(null);
                                        load_data(true,"working");
                                        SuccessDialog(Gravity.CENTER,i18n("Success"));
                                    } catch (Exception e)
                                    {

                                        throw new RuntimeException(e);
                                    }

                                });

                            }

                        });
                        getDataListMaterial.start();
                    }
                    else
                    {
                        ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select maintenance category"));
                    }

                }else
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select maintenance category"));

                }

            }
        });

        btn_get_list_material.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(txt_material_number.getText().toString().equals("(0)"))
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select at least one item that has not created a material export request."));

                }else
                {
                    List<JSONObject> data_send = listTaskMonthAdapter.getListItemMaterial();
                    Intent intent = new Intent(getContext(), MaterialActivity.class);
                    intent.putExtra("DataSend", String.valueOf(data_send));
                    MaterialActivityLauncher.launch(intent);
                }

            }
        });

        btn_end_work.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if (listTaskMonthAdapter != null)
                {
                    List<JSONObject> data_select = listTaskMonthAdapter.getListItemSelect();
                    if (data_select.size() > 0)
                    {
                        EndWorkDialog(Gravity.CENTER, "Complete task", "Do you want to complete the maintenance items ?");

                    }else {
                        ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select maintenance category"));
                    }
                }

            }
        });

        btn_tab_1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_tab_1.setTextColor(Color.parseColor("#6A1039"));
                line_tab_1.setBackgroundColor(Color.parseColor("#6A1039"));
                btn_tab_2.setTextColor(Color.parseColor("#5C5C5C"));
                line_tab_2.setBackgroundColor(Color.parseColor("#F4F2F2"));
                layout_tab1.setVisibility(View.VISIBLE);
                layout_tab2.setVisibility(View.GONE);
                load_data(true,"working");
            }
        });

        btn_tab_2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_tab_1.setTextColor(Color.parseColor("#5C5C5C"));
                line_tab_1.setBackgroundColor(Color.parseColor("#F4F2F2"));
                btn_tab_2.setTextColor(Color.parseColor("#6A1039"));
                line_tab_2.setBackgroundColor(Color.parseColor("#6A1039"));
                layout_tab1.setVisibility(View.GONE);
                layout_tab2.setVisibility(View.VISIBLE);

                load_data(true,"end");

            }
        });

        btn_add_new_error.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), NewErrorActivity.class);
                NewErrorActivityLauncher.launch(intent);
            }
        });

        ckeck1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ck1)
                {
                    ckeck1.setImageResource(R.drawable.checkbox_untick);
                    ck1 = false;
                    load_data(false,"working");

                }
                else
                {
                    ckeck1.setImageResource(R.drawable.check_inprogess);
                    ck1 = true;
                    load_data(true,"working");
                }
            }
        });

        ckeck2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ck2)
                {
                    ckeck2.setImageResource(R.drawable.checkbox_untick);
                    ck2 = false;
                    load_data(false,"working");
                }
                else
                {
                    ckeck2.setImageResource(R.drawable.checkbox_pending);
                    ck2 = true;
                    load_data(true,"working");

                }
            }
        });

        ckeck3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ck3)
                {
                    ckeck3.setImageResource(R.drawable.checkbox_untick);
                    ck3 = false;
                    load_data(false,"working");
                }
                else
                {
                    ckeck3.setImageResource(R.drawable.checkbox_over);
                    ck3 = true;
                    load_data(true,"working");

                }
            }
        });

        ckeck4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ck4)
                {
                    ckeck4.setImageResource(R.drawable.checkbox_untick);
                    ck4 = false;
                    load_data(false,"end");
                }
                else
                {
                    ckeck4.setImageResource(R.drawable.check_type_3);
                    ck4 = true;
                    load_data(true,"end");

                }
            }
        });

        ckeck5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ck5)
                {
                    ckeck5.setImageResource(R.drawable.checkbox_untick);
                    ck5 = false;
                    load_data(false,"end");
                }
                else
                {
                    ckeck5.setImageResource(R.drawable.checkbox_tick);
                    ck5 = true;
                    load_data(true,"end");

                }
            }
        });

        ckeck6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ck6)
                {
                    ckeck6.setImageResource(R.drawable.checkbox_untick);
                    ck6 = false;
                    load_data(false,"end");
                }
                else
                {
                    ckeck6.setImageResource(R.drawable.check_type_red);
                    ck6 = true;
                    load_data(true,"end");

                }
            }
        });

        btn_select_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(getContext(), txtlabel_date, new OnDateSelectedListener() {
                    @Override
                    public void onDateSelected(String formattedDate)
                    {
                        rcv_list_task_succees.setAdapter(null);
                        load_data(true,"end");
                    }
                });
            }
        });

        btn_select_to_date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(getContext(), txtlabel_to_date, new OnDateSelectedListener() {
                    @Override
                    public void onDateSelected(String formattedDate)
                    {
                        rcv_list_task_succees.setAdapter(null);
                        load_data(true,"end");
                    }
                });
            }
        });

        txt_find.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable)
            {
                if(listTaskMonthAdapter != null)
                {
                    listTaskMonthAdapter.filterListNew(editable.toString());
                }

            }
        });

        txt_find_tab2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable)
            {
                if(listTaskMonthEndAdapter != null)
                {
                    listTaskMonthEndAdapter.filterListNew(editable.toString());
                }

            }
        });

        swipeRefreshLayoutparam.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh()
            {
                refreshData();
            }
        });

        swipe_refresh_layout_param_end_work.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh()
            {
                refreshDataEndwork();
            }
        });

        btn_approve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(listTaskMonthEndAdapter != null)
                {
                    List<JSONObject> data_select = listTaskMonthEndAdapter.getListItemSelect();
                    if (data_select.size() > 0)
                    {
                        ApproveDialog(Gravity.CENTER,i18n("Approve"));
                    }
                    else
                    {
                        ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select maintenance category"));
                    }
                }else
                {
                    ErrorDialog(Gravity.CENTER,i18n("ERROR"),i18n("Please select maintenance category"));
                }

            }
        });

        btn_history_machine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), HistoryMachineActivity.class);
                HistoryMachineActivityLauncher.launch(intent);
            }
        });


        return view;
    }

    public void refreshData()
    {
        swipeRefreshLayoutparam.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                load_data(true,"working");
                swipeRefreshLayoutparam.setRefreshing(false);
            }
        },1000);
    }

    public void refreshDataEndwork()
    {
        swipe_refresh_layout_param_end_work.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                load_data(true,"end");
                swipe_refresh_layout_param_end_work.setRefreshing(false);
            }
        },1000);
    }

    private void hideKeyboard(View view)
    {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(getContext().INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void EndWorkDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(getContext());
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

        btnOK.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                List<JSONObject> data_select = listTaskMonthAdapter.getListItemSelect();
                Thread getDataListMaterial = new Thread(() -> {
                    HttpClient.APIReturn rs = HttpClient.save_end_task_data(getContext(),new JSONArray(),new JSONArray(),data_select,"","","","","",server_url, user_login);
                    if (rs.code == 200)
                    {
                        getActivity().runOnUiThread(() -> {
                            try
                            {
                                rcv_list_task_month.setAdapter(null);
                                rcv_list_task_succees.setAdapter(null);
                                load_data(true,"working");
                                dialog.dismiss();
                                SuccessDialog(Gravity.CENTER,i18n("Success"));

                            } catch (Exception e)
                            {

                                throw new RuntimeException(e);
                            }

                        });

                    }else
                    {
                        tvTitle.setText(i18n("ERROR"));
                        txtnoidung.setText(i18n("Could not save data"));
                        btnOK.setText(i18n("Retry"));
                        btncancel.setText(i18n("Close"));
                    }

                });
                getDataListMaterial.start();

            }
        });


        dialog.show();
    }

    private void ErrorDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(getContext());
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

    private void ApproveDialog(int gravity, String tieude)
    {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_approve);
        RadioGroup app_rove_select = (RadioGroup) dialog.findViewById(R.id.app_rove_select);
        TextView btnOK = (TextView) dialog.findViewById(R.id.btnsaveapp_rove);
        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        RadioButton rb_OK = (RadioButton) dialog.findViewById(R.id.rb_OK);
        RadioButton rb_NG = (RadioButton) dialog.findViewById(R.id.rb_NG);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);

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


        rb_OK.setChecked(true);

        rb_OK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                approve_value = "2";
            }
        });

        rb_NG.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                approve_value = "3";
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                List<JSONObject> list_task = listTaskMonthEndAdapter.getListItemSelect();

                Thread app_rove_list = new Thread(() -> {
                    HttpClient.APIReturn rs = HttpClient.save_app_rove_task(getContext(),list_task,approve_value, server_url, user_login);
                    if (rs.code == 200)
                    {
                        getActivity().runOnUiThread(() -> {
                            try {

                                load_data(true,"end");
                                SuccessDialog(Gravity.CENTER,i18n("Success"));
                                dialog.dismiss();
                            } catch (Exception e) {

                                throw new RuntimeException(e);
                            }

                        });

                    }

                });
                app_rove_list.start();
            }
        });

        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                dialog.dismiss();
            }
        });

        dialog.show();



    }

    private void SuccessDialog(int gravity, String noidung)
    {
        final Dialog dialog = new Dialog(getContext());
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

    public void showDatePicker(Context context, TextView txtlabel_input,OnDateSelectedListener listener) {
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

    public interface OnDateSelectedListener {
        void onDateSelected(String formattedDate);
    }
    private final ActivityResultLauncher<Intent> TaskDetailActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                {
                    Intent data = result.getData();
                    if (data != null)
                    {

                    }
                }else
                {

                }
            }
    );

    private final ActivityResultLauncher<Intent> MaterialActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                {
                    Intent data = result.getData();
                    if (data != null)
                    {

                    }
                }else
                {

                }
            }
    );

    private final ActivityResultLauncher<Intent> NewErrorActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                {
                    Intent data = result.getData();
                    if (data != null)
                    {

                    }
                }else
                {

                }
            }
    );


    private final ActivityResultLauncher<Intent> HistoryMachineActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                {
                    Intent data = result.getData();
                    if (data != null)
                    {

                    }
                }else
                {

                }
            }
    );


    private int DateDifferenceCalculator ( String startDateStr,String endDateStr,String format)
    {

        // Define date format
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());

        try {
            // Convert strings to Date objects
            Calendar startDate = Calendar.getInstance();
            startDate.setTime(dateFormat.parse(startDateStr));

            Calendar endDate = Calendar.getInstance();
            endDate.setTime(dateFormat.parse(endDateStr));

            // Calculate difference in milliseconds
            long diffInMillis = endDate.getTimeInMillis() - startDate.getTimeInMillis();

            // Convert milliseconds to days
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            return Integer.valueOf((int) diffInDays);

            // Print result
            // System.out.println("Number of days: " + diffInDays);
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }

        return -1;

    }

    private int DateDifferenceCalculatorTimeUnix ( String startDateStr,String endDateStr)
    {

        try {
            long startMillis = Long.parseLong(startDateStr) * 1000;
            long endMillis = Long.parseLong(endDateStr) * 1000;

            long diffInMillis = endMillis - startMillis;
            return (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }

    }



    private String getDate(String format)
    {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        return currentDate;

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

    private List<JSONObject> fillter_status(List<JSONObject> data,Boolean ck1,Boolean ck2,Boolean ck3)
    {

        List<JSONObject> dataTemp = new ArrayList<>();

        for (JSONObject jsonObject : data) {
            try {
                String date = jsonObject.optString("Task_Date");
                long date_unix = TimeUtils.getCurrentUnixTimestamp();
                // Calculate date difference
                // int dateQty = DateDifferenceCalculator(date, getDate("M/dd/yyyy"), "M/dd/yyyy");
                int dateQty = DateDifferenceCalculatorTimeUnix(date,String.valueOf(date_unix));

                if (dateQty > 0)
                {
                    if (ck3)
                    {
                        dataTemp.add(jsonObject);
                    }
                }
                else
                {
                    JSONArray list = jsonObject.optJSONArray("DataTemp");
                    if (list == null || list.length() == 0) continue;

                    JSONArray listTemp = new JSONArray();
                    for (int j = 0; j < list.length(); j++)
                    {
                        JSONObject task = list.getJSONObject(j);
                        String status = task.optString("Status");

                        if ((ck1 && status.equals("4")) || (ck2 && status.equals("0") ))
                        {
                            listTemp.put(task);
                        }
                    }

                    if (listTemp.length() > 0)
                    {
                        JSONObject newList = new JSONObject();
                        newList.put("Task_Date", date);
                        newList.put("DataTemp", listTemp);
                        dataTemp.add(newList);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return dataTemp;

    }

    private List<JSONObject> fillter_status_history(List<JSONObject> data,Boolean ck4,Boolean ck5,Boolean ck6)
    {

        List<JSONObject> dataTemp = new ArrayList<>();

        for (JSONObject jsonObject : data)
        {
            try {
                String date = jsonObject.optString("Task_Date");

                // Calculate date difference
                {
                    JSONArray list = jsonObject.optJSONArray("DataTemp");
                    if (list == null || list.length() == 0) continue;

                    JSONArray listTemp = new JSONArray();
                    for (int j = 0; j < list.length(); j++) {
                        JSONObject task = list.getJSONObject(j);
                        String status = task.optString("Status");

                        if ((ck4 && status.equals("1")) || (ck5 && status.equals("2")) || (ck6 && status.equals("3")) )
                        {
                            listTemp.put(task);
                        }
                    }

                    if (listTemp.length() > 0) {
                        JSONObject newList = new JSONObject();
                        newList.put("Task_Date", date);
                        newList.put("DataTemp", listTemp);
                        dataTemp.add(newList);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return dataTemp;

    }

    private void load_data(Boolean item_cout, String type_get)
    {

        if(type_get.equals("end"))
        {
            Thread getDataListTaskEnd = new Thread(() -> {
                HttpClient.APIReturn rs = HttpClient.get_list_history(getContext(),convertDateFormat(txtlabel_date.getText().toString()),convertDateFormat(txtlabel_to_date.getText().toString()), server_url, user_login);
                if (rs.code == 200)
                {
                    getActivity().runOnUiThread(() -> {
                        try {

                            if (rs.data.size() > 0)
                            {

                                rcv_list_task_succees.setAdapter(null);
                                data_task_list_end.clear();
                                data_task_list_end = fillter_status_history(rs.data,ck4,ck5,ck6);
                                listTaskMonthEndAdapter = new ListTaskMonthEndAdapter(data_task_list_end ,getContext());
                                rcv_list_task_succees.setAdapter(listTaskMonthEndAdapter);
                                if(item_cout)
                                {
                                    txt_lable_ckeck4.setText(i18n("Done")+" (" + String.valueOf(listTaskMonthEndAdapter.getItemTaskDoneCount()) + ")");
                                    txt_lable_ckeck5.setText(i18n("Approved OK")+" (" + String.valueOf(listTaskMonthEndAdapter.getItemTaskOKCount()) + ")");
                                    txt_lable_ckeck6.setText(i18n("Approved NG")+" (" + String.valueOf(listTaskMonthEndAdapter.getItemTaskNGCount()) + ")");
                                }

                            }
                            else
                            {
                                rcv_list_task_succees.setAdapter(null);
                                data_task_list_end.clear();
                                txt_lable_ckeck4.setText(i18n("Done") + "(0)");
                                txt_lable_ckeck5.setText(i18n("Approved OK") + "(0)");
                                txt_lable_ckeck6.setText(i18n("Approved NG") + "(0)");

                            }
                        } catch (Exception e) {

                            throw new RuntimeException(e);
                        }

                    });

                }

            });
            getDataListTaskEnd.start();
        }
        else if(type_get.equals("working"))
        {
            Thread getDataListTask = new Thread(() -> {
                HttpClient.APIReturn rs = HttpClient.get_list_task_data(getContext(), server_url, user_login);
                if (rs.code == 200)
                {
                    getActivity().runOnUiThread(() -> {
                        try {

                            if (rs.data.size() > 0)
                            {

                                rcv_list_task_month.setAdapter(null);
                                data_task_list.clear();
                                txt_material_number.setText("(0)");
                                data_task_list = fillter_status(rs.data, ck1, ck2, ck3);
                                listTaskMonthAdapter = new ListTaskMonthAdapter(data_task_list, txt_material_number, getContext());
                                rcv_list_task_month.setAdapter(listTaskMonthAdapter);
                                if(item_cout)
                                {
                                    txt_lable_ckeck1.setText(i18n("In progress") + " (" + String.valueOf(listTaskMonthAdapter.getItemTaskWorkingCount()) + ")");
                                    txt_lable_ckeck2.setText(i18n("Pending") + " (" + String.valueOf(listTaskMonthAdapter.getItemTaskFreeCount()) + ")");
                                    txt_lable_ckeck3.setText(i18n("Overdue") + " (" + String.valueOf(listTaskMonthAdapter.getItemTaskCount()) + ")");
                                }

                            }else
                            {
                                rcv_list_task_month.setAdapter(null);
                                data_task_list.clear();
                                txt_material_number.setText("(0)");
                                txt_lable_ckeck1.setText(i18n("In progress") + "(0)");
                                txt_lable_ckeck2.setText(i18n("Pending") + "(0)");
                                txt_lable_ckeck3.setText(i18n("Overdue") + "(0)");
                            }
                        } catch (Exception e) {

                            throw new RuntimeException(e);
                        }

                    });

                }

            });
            getDataListTask.start();
        }
        else if(type_get.equals("all"))
        {
            Thread getDataListTask = new Thread(() -> {
                HttpClient.APIReturn rs = HttpClient.get_list_task_data(getContext(), server_url, user_login);
                if (rs.code == 200)
                {
                    getActivity().runOnUiThread(() -> {
                        try {

                            if (rs.data != null)
                            {

                                if (!rs.data.isEmpty())
                                {
                                    rcv_list_task_month.setAdapter(null);
                                    txt_material_number.setText("(0)");
                                    data_task_list = fillter_status(rs.data,ck1,ck2,ck3);
                                    listTaskMonthAdapter = new ListTaskMonthAdapter(data_task_list,txt_material_number ,getContext());
                                    rcv_list_task_month.setAdapter(listTaskMonthAdapter);
                                    if(item_cout)
                                    {
                                        txt_lable_ckeck1.setText(i18n("In progress")+" (" + String.valueOf(listTaskMonthAdapter.getItemTaskWorkingCount()) + ")");
                                        txt_lable_ckeck2.setText(i18n("Pending")+" (" + String.valueOf(listTaskMonthAdapter.getItemTaskFreeCount()) + ")");
                                        txt_lable_ckeck3.setText(i18n("Overdue")+" (" + String.valueOf(listTaskMonthAdapter.getItemTaskCount()) + ")");
                                    }

                                }
                                else
                                {


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

            });
            getDataListTask.start();

            Thread getDataListTaskEnd = new Thread(() -> {
                HttpClient.APIReturn rs = HttpClient.get_list_history(getContext(),convertDateFormat(txtlabel_date.getText().toString()),convertDateFormat(txtlabel_to_date.getText().toString()), server_url, user_login);
                if (rs.code == 200)
                {
                    getActivity().runOnUiThread(() -> {
                        try {

                            if (rs.data != null)
                            {

                                if (!rs.data.isEmpty())
                                {
                                    data_task_list_end = rs.data;
                                    listTaskMonthEndAdapter = new ListTaskMonthEndAdapter(data_task_list_end ,getContext());
                                    rcv_list_task_succees.setAdapter(listTaskMonthEndAdapter);


                                }
                                else
                                {


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

            });
            getDataListTaskEnd.start();
        }

    }


    @Override
    public void onResume() {

        super.onResume();
        TextView header_home_title = getActivity().findViewById(R.id.lable_screen_header);
        TextView menu_home_title = getActivity().findViewById(R.id.menu_home_lable);
        TextView menu_home_setting = getActivity().findViewById(R.id.menu_setting_lable);
        header_home_title.setText(i18n("MAINTENANCE MANAGEMENT SYSTEM"));
        txtlabel_Directivecode.setText(i18n("Maintenance and Repair Schedule"));
        menu_home_title.setText(i18n("Home"));
        menu_home_setting.setText(i18n("System settings"));
        btn_tab_1.setText(i18n("To-Do List"));
        btn_tab_2.setText(i18n("Completed To Do List"));
        txt_lable_ckeck1.setText(i18n("In progress"));
        txt_lable_ckeck2.setText(i18n("Pending"));
        txt_lable_ckeck3.setText(i18n("Overdue"));
        txt_add_new_error.setText(i18n("Report new error"));
        btn_material_lable.setText(i18n("Materials"));
        btn_dowork_lable.setText(i18n("Execute task"));
        btn_complete_lable.setText(i18n("Complete task"));
        txt_find.setHint(i18n("Search"));
        txt_find_tab2.setHint(i18n("Search"));
        txt_from_lable.setText(i18n("From"));
        txt_todate.setText(i18n("To"));
        btn_approve_lable.setText(i18n("Approve"));
        txt_history_machine.setText(i18n("Machine history"));
        load_data(true,"working");
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

    }

}