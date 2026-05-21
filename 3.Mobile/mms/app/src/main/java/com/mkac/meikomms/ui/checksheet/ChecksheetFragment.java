package com.mkac.meikomms.ui.checksheet;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.ui.FragmentProfile;
import com.mkac.meikomms.ui.workorder.ListWorkOrderActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChecksheetFragment extends Fragment {

    private FragmentProfile currentProfile;
    private String serverDynamic = "";
    private String schemaData = "";
    private String schemaCore = "";
    private String currentDSACondition = "1=1";
    private String dateColumn = "Create_Date";

    private TextView btnFilterDay, btnFilterWeek, btnFilterMonth, tvStartDate, tvEndDate, tvCountdown;
    private TextView btnWorkOrder;
    private CheckBox cbMA, cbFETiepNhan, cbFEPhanCong, cbFEHoanTat, cbHuy;
    private LinearLayout lnMA, lnFETiepNhan, lnFEPhanCong, lnFEHoanTat, lnHuy;
    private TextView tvCountMA, tvCountFETiepNhan, tvCountFEPhanCong, tvCountFEHoanTat, tvCountHuy;
    private TextView tvProgressMA, tvProgressFETiepNhan, tvProgressFEPhanCong, tvProgressFEHoanTat, tvProgressHuy;

    private RecyclerView rvChecksheet;
    private ChecksheetAdapter adapter;
    private CountDownTimer countDownTimer;

    private List<JSONObject> originalDataList = new ArrayList<>();
    private final Set<String> selectedStatuses = new HashSet<>();

    public static ChecksheetFragment newInstance(FragmentProfile profile) {
        ChecksheetFragment checkSheetFragment = new ChecksheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("PROFILE", profile);
        checkSheetFragment.setArguments(args);
        return checkSheetFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentProfile = (FragmentProfile) getArguments().getSerializable("PROFILE");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_checksheet, container, false);

        ConfigManager configManager = new ConfigManager(getContext());
        serverDynamic = configManager.getProperty("server_dynamic_url");
        schemaData = configManager.getProperty("schema_data");
        schemaCore = configManager.getProperty("schema_core");

        // Main views
        btnFilterDay = view.findViewById(R.id.btn_filter_day);
        btnFilterWeek = view.findViewById(R.id.btn_filter_week);
        btnFilterMonth = view.findViewById(R.id.btn_filter_month);
        tvStartDate = view.findViewById(R.id.tv_start_date);
        tvEndDate = view.findViewById(R.id.tv_end_date);
        tvCountdown = view.findViewById(R.id.tv_countdown);
        
        // Nút mở danh sách Work Order
        btnWorkOrder = view.findViewById(R.id.btn_list_wo);
        if (btnWorkOrder == null) {
            btnWorkOrder = view.findViewById(R.id.btn_list_wo);
        }

        // Containers & CheckBoxes
        lnMA = view.findViewById(R.id.ln_ma);
        cbMA = view.findViewById(R.id.cb_ma_bao_su_co);
        tvCountMA = view.findViewById(R.id.tv_count_ma);
        tvProgressMA = view.findViewById(R.id.tv_progress_ma);

        lnFETiepNhan = view.findViewById(R.id.ln_fe_tiep_nhan);
        cbFETiepNhan = view.findViewById(R.id.cb_fe_tiep_nhan);
        tvCountFETiepNhan = view.findViewById(R.id.tv_count_fe_tiep_nhan);
        tvProgressFETiepNhan = view.findViewById(R.id.tv_progress_fe_tiep_nhan);

        lnFEPhanCong = view.findViewById(R.id.ln_fe_phan_cong);
        cbFEPhanCong = view.findViewById(R.id.cb_fe_phan_cong);
        tvCountFEPhanCong = view.findViewById(R.id.tv_count_fe_phan_cong);
        tvProgressFEPhanCong = view.findViewById(R.id.tv_progress_fe_phan_cong);

        lnFEHoanTat = view.findViewById(R.id.ln_fe_hoan_tat);
        cbFEHoanTat = view.findViewById(R.id.cb_fe_hoan_tat);
        tvCountFEHoanTat = view.findViewById(R.id.tv_count_fe_hoan_tat);
        tvProgressFEHoanTat = view.findViewById(R.id.tv_progress_fe_hoan_tat);

        lnHuy = view.findViewById(R.id.ln_huy);
        cbHuy = view.findViewById(R.id.cb_huy);
        tvCountHuy = view.findViewById(R.id.tv_count_huy);
        tvProgressHuy = view.findViewById(R.id.tv_progress_huy);

        // RecyclerView setup
        rvChecksheet = view.findViewById(R.id.rv_checksheet);
        rvChecksheet.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChecksheetAdapter();
        rvChecksheet.setAdapter(adapter);

        initSelectedStatuses();
        setupBlockClickListeners();

        if (tvStartDate != null) tvStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        if (tvEndDate != null) tvEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        btnFilterDay.setOnClickListener(v -> performFilter(0));
        btnFilterWeek.setOnClickListener(v -> performFilter(8));
        btnFilterMonth.setOnClickListener(v -> performFilter(31));

        if(btnWorkOrder != null){
            btnWorkOrder.setOnClickListener(v -> {
                ListWorkOrderActivity.start(getContext());
            });
        }

        startReloadTimer();
        performFilter(8);

        return view;
    }

    private void showDatePickerDialog(boolean isStartDate) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, month, dayOfMonth) -> {
            String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
            if (isStartDate) tvStartDate.setText(selectedDate);
            else tvEndDate.setText(selectedDate);
            performManualFilter();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void performManualFilter() {
        String startStr = tvStartDate.getText().toString();
        String endStr = tvEndDate.getText().toString();
        if (startStr.contains("/") && endStr.contains("/")) {
            try {
                SimpleDateFormat sdfUI = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                SimpleDateFormat sdfDB = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String startDB = sdfDB.format(sdfUI.parse(startStr));
                String endDB = sdfDB.format(sdfUI.parse(endStr));
                currentDSACondition = "TRUNC(" + dateColumn + ") BETWEEN TO_DATE('" + startDB + "', 'YYYY-MM-DD') AND TO_DATE('" + endDB + "', 'YYYY-MM-DD')";
                loadChecksheetData(currentDSACondition);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void startReloadTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvCountdown != null) tvCountdown.setText(String.format(Locale.getDefault(), "00:%02d", millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                loadChecksheetData(currentDSACondition);
                startReloadTimer();
            }
        }.start();
    }

    private void setupBlockClickListeners() {
        View.OnClickListener toggleListener = v -> {
            CheckBox cb = null;
            int id = v.getId();
            if (id == R.id.ln_ma) cb = cbMA;
            else if (id == R.id.ln_fe_tiep_nhan) cb = cbFETiepNhan;
            else if (id == R.id.ln_fe_phan_cong) cb = cbFEPhanCong;
            else if (id == R.id.ln_fe_hoan_tat) cb = cbFEHoanTat;
            else if (id == R.id.ln_huy) cb = cbHuy;
            if (cb != null) {
                cb.setChecked(!cb.isChecked());
                updateSelectedStatuses();
                filterAndPopulateList();
            }
        };
        lnMA.setOnClickListener(toggleListener);
        lnFETiepNhan.setOnClickListener(toggleListener);
        lnFEPhanCong.setOnClickListener(toggleListener);
        lnFEHoanTat.setOnClickListener(toggleListener);
        lnHuy.setOnClickListener(toggleListener);
    }

    private void initSelectedStatuses() {
        cbMA.setChecked(true); cbFETiepNhan.setChecked(true); cbFEPhanCong.setChecked(true);
        cbFEHoanTat.setChecked(true); cbHuy.setChecked(true);
        updateSelectedStatuses();
    }

    private void updateSelectedStatuses() {
        selectedStatuses.clear();
        if (cbMA.isChecked()) selectedStatuses.add("ISSUE_1");
        if (cbFETiepNhan.isChecked()) selectedStatuses.add("ISSUE_4");
        if (cbFEPhanCong.isChecked()) selectedStatuses.add("ISSUE_2");
        if (cbFEHoanTat.isChecked()) selectedStatuses.add("TASK_2");
        if (cbHuy.isChecked()) selectedStatuses.add("ISSUE_3");

        updateCheckBoxStyle(cbMA, "#FF99FF");
        updateCheckBoxStyle(cbFETiepNhan, "#FEFE9A");
        updateCheckBoxStyle(cbFEPhanCong, "#98FE69");
        updateCheckBoxStyle(cbFEHoanTat, "#66CCFD");
        updateCheckBoxStyle(cbHuy, "#ABABAB");
    }

    private void updateCheckBoxStyle(CheckBox cb, String colorHex) {
        if (cb == null) return;
        int color = Color.parseColor(colorHex);
        cb.setButtonTintList(ColorStateList.valueOf(color));
        cb.setBackgroundColor(cb.isChecked() ? color : Color.TRANSPARENT);
    }

    private void performFilter(int daysBack) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdfUI = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat sdfDB = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String endDateUI = sdfUI.format(cal.getTime());
        String endDateDB = sdfDB.format(cal.getTime());
        if (tvEndDate != null) tvEndDate.setText(endDateUI);

        if (daysBack == 0) {
            if (tvStartDate != null) tvStartDate.setText(endDateUI);
            currentDSACondition = "TRUNC(" + dateColumn + ") = TO_DATE('" + endDateDB + "', 'YYYY-MM-DD')";
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1));
            String startUI = sdfUI.format(cal.getTime());
            String startDB = sdfDB.format(cal.getTime());
            if (tvStartDate != null) tvStartDate.setText(startUI);
            currentDSACondition = "TRUNC(" + dateColumn + ") BETWEEN TO_DATE('" + startDB + "', 'YYYY-MM-DD') AND TO_DATE('" + endDateDB + "', 'YYYY-MM-DD')";
        }
        loadChecksheetData(currentDSACondition);
    }

    private void loadChecksheetData(String dsaCondition) {
        PreferenceHandler handler = new PreferenceHandler(getContext());
        HttpClient.initToken(handler.getString("api_key"));
        new Thread(() -> {
            HttpClient.APIReturn apiReturn = HttpClient.getDataTable(getContext(), serverDynamic, schemaData, schemaCore, dsaCondition);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (apiReturn.code == 200 && apiReturn.data != null) {
                        originalDataList = apiReturn.data;
                        updateCountsAndProgress(originalDataList);
                        filterAndPopulateList();
                    }
                });
            }
        }).start();
    }

    private void updateCountsAndProgress(List<JSONObject> dataList) {
        int cMA = 0, cFET = 0, cFEPC = 0, cFEHT = 0, cHuy = 0;
        for (JSONObject data : dataList) {
            String status = getStatusKey(data);
            switch (status) {
                case "ISSUE_1": cMA++; break;
                case "ISSUE_4": cFET++; break;
                case "ISSUE_2": cFEPC++; break;
                case "TASK_2": cFEHT++; break;
                case "ISSUE_3": cHuy++; break;
            }
        }
        tvCountMA.setText("MA báo sự cố " + cMA);
        tvCountFETiepNhan.setText("FE đã tiếp nhận " + cFET);
        tvCountFEPhanCong.setText("FE đã phân công " + cFEPC);
        tvCountFEHoanTat.setText("FE đã hoàn tất " + cFEHT);
        tvCountHuy.setText("Hủy " + cHuy);

        int total = dataList.size();
        updateProgressBar(cMA, cFET, cFEPC, cFEHT, cHuy, total);
    }

    private String getStatusKey(JSONObject data) {
        String finalStatus = data.optString("Final_Status", "");
        if ("TASK_2".equals(finalStatus)) return "TASK_2";
        int issueStatus = data.optInt("Issue_Status", -1);
        switch (issueStatus) {
            case 1: return "ISSUE_1";
            case 2: return "ISSUE_2";
            case 3: return "ISSUE_3";
            case 4: return "ISSUE_4";
            default: return "UNKNOWN";
        }
    }

    private void filterAndPopulateList() {
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject data : originalDataList) {
            if (selectedStatuses.contains(getStatusKey(data))) filtered.add(data);
        }
        adapter.setItems(filtered);
    }

    private void updateProgressBar(int ma, int tiep, int phan, int hoan, int huy, int total) {
        if (total == 0) return;
        updatePart(tvProgressMA, ma, total);
        updatePart(tvProgressFETiepNhan, tiep, total);
        updatePart(tvProgressFEPhanCong, phan, total);
        updatePart(tvProgressFEHoanTat, hoan, total);
        updatePart(tvProgressHuy, huy, total);
    }

    private void updatePart(TextView tv, int count, int total) {
        float percentage = (count * 100.0f) / total;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tv.getLayoutParams();
        params.weight = percentage > 0 ? percentage : 0.001f;
        tv.setLayoutParams(params);
        tv.setText(percentage > 0 ? String.format(Locale.getDefault(), "%.0f%%", percentage) : "");
        tv.setVisibility(percentage > 0 ? View.VISIBLE : View.GONE);
    }

    private class ChecksheetAdapter extends RecyclerView.Adapter<ChecksheetAdapter.RowVH> {
        private final List<JSONObject> items = new ArrayList<>();
        void setItems(List<JSONObject> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }
        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setBackgroundColor(Color.BLACK);
            for (int i = 0; i < 10; i++) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.MATCH_PARENT));
                if (i == 0) tv.getLayoutParams().width = dp(50);
                tv.setPadding(dp(8), dp(12), dp(8), dp(12));
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(10);
                tv.setTextColor(Color.BLACK);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
                lp.setMargins(1, 1, 1, 1);
                row.addView(tv);
            }
            return new RowVH(row);
        }
        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            JSONObject data = items.get(position);
            String[] texts = { String.valueOf(position + 1), data.optString("Issue_Id", ""), data.optString("Floor_Id", ""), data.optString("Physical_Group_Name", ""), data.optString("Machine_Name", ""), data.optString("Note", ""), data.optString("Current_Situation", ""), data.optString("Machine_Status", ""), data.optString("Create_Date", ""), getStatusKey(data) };
            for (int i = 0; i < holder.cells.length; i++) {
                holder.cells[i].setBackgroundColor(Color.WHITE);
                holder.cells[i].setText(texts[i]);
            }
        }
        @Override
        public int getItemCount() { return items.size(); }
        class RowVH extends RecyclerView.ViewHolder {
            TextView[] cells;
            RowVH(View v) {
                super(v);
                cells = new TextView[((LinearLayout)v).getChildCount()];
                for (int i = 0; i < cells.length; i++) cells[i] = (TextView) ((LinearLayout)v).getChildAt(i);
            }
        }
    }

    private int dp(float dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
