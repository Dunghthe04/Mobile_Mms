package com.mkac.meikomms.ui.workorder;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.databinding.ActivityMaintenancePlanListBinding;
import com.mkac.meikomms.ui.workorder.model.MaintenancePlan;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaintenancePlanListActivity extends AppCompatActivity {
    private ActivityMaintenancePlanListBinding binding;
    private MaintenancePlanAdapter adapter;
    private final List<MaintenancePlan> planList = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String serverUrl = "";
    private String schemaCore = "";
    private String schemaMms = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaintenancePlanListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LanguageAPIUtils.init(this);
        LanguageAPIUtils.setLang(binding.getRoot());
        loadConfiguration();
        setupRecyclerView();
        setupFilters();
        loadMaintenancePlans();

        binding.btnRefreshList.setOnClickListener(v -> loadMaintenancePlans());
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        loadConfiguration();
        LanguageAPIUtils.setLang(binding.getRoot());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void setupRecyclerView() {
        binding.rvMaintenancePlans.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MaintenancePlanAdapter(planList, plan -> {
            Intent intent = new Intent(this, EnterWorkOrderDataActivity.class);
            intent.putExtra("TASK_ID", safe(plan.taskId));
            intent.putExtra("MACHINE_ID", safe(plan.machineId));
            intent.putExtra("MACHINE_NAME", safe(plan.machineName));
            intent.putExtra("CATEGORY_ID", safe(plan.categoryId));
            intent.putExtra("CATEGORY_NAME", safe(plan.categoryName));
            intent.putExtra("TASK_DATE_UNIX", plan.taskDateUnix);
            intent.putExtra("STATUS", safe(plan.status));
            startActivity(intent);
        });
        binding.rvMaintenancePlans.setAdapter(adapter);
    }

    private void setupFilters() {
        binding.etSearchMachine.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                loadMaintenancePlans();
            }
        });

        binding.btnCalendarPicker.setOnClickListener(v -> loadMaintenancePlans());
    }

    private void loadConfiguration() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"));
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), handler.getString("schema_data"), configManager.getProperty("schema_data"));

        binding.tvFilterDateRange.setText(i18n("Time") + ": " + formatUnix(resolveRange()[0]) + " ~ " + formatUnix(resolveRange()[1]));
    }

    private void loadMaintenancePlans() {
        if (serverUrl.isEmpty()) {
            return;
        }

        long[] range = resolveRange();
        String searchText = binding.etSearchMachine.getText() == null ? "" : binding.etSearchMachine.getText().toString().trim();
        String whereTask = "1=1 AND Task_Type = '1' AND (Task_Date_Unix BETWEEN " + range[0] + " AND " + range[1] + ")";
        String whereMachine = searchText.isEmpty()
                ? "1=1"
                : "LOWER(Machine_Name) LIKE LOWER('%" + escapeSql(searchText) + "%') OR LOWER(Machine_Id) LIKE LOWER('%" + escapeSql(searchText) + "%')";

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getMaintenancePlanList(this, serverUrl, schemaCore, schemaMms, whereTask, whereMachine, range[0], range[1]);
            if (rs.code == 200 && rs.data != null) {
                List<MaintenancePlan> newList = new ArrayList<>();
                for (JSONObject row : rs.data) {
                    MaintenancePlan plan = parsePlan(row);
                    if (plan.taskId != null && !plan.taskId.trim().isEmpty()) {
                        newList.add(plan);
                    }
                }
                runOnUiThread(() -> {
                    planList.clear();
                    planList.addAll(newList);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private MaintenancePlan parsePlan(JSONObject row) {
        MaintenancePlan plan = new MaintenancePlan();
        plan.taskId = pickFirst(row.optString("Task_Id"), row.optString("taskId"), row.optString("TASK_ID"));
        plan.machineId = pickFirst(row.optString("Machine_Id"), row.optString("machineId"), row.optString("MACHINE_ID"));
        plan.machineName = pickFirst(row.optString("Machine_Name"), row.optString("machineName"), row.optString("MACHINE_NAME"));
        plan.categoryId = pickFirst(row.optString("Category_Id"), row.optString("categoryId"), row.optString("CATEGORY_ID"));
        plan.categoryName = pickFirst(row.optString("Category_Name"), row.optString("categoryName"), row.optString("CATEGORY_NAME"));
        plan.assigneeName = pickFirst(row.optString("Assignee_Name"), row.optString("Maintainer_Name"), row.optString("Assignee"), row.optString("User_Name"));
        plan.executorName = pickFirst(row.optString("Executor_Name"), row.optString("Executor"), row.optString("Check_By_Name"), row.optString("User_Check_Name"));
        plan.status = pickFirst(row.optString("Status"), row.optString("status"), row.optString("Task_Status"));
        plan.taskDateUnix = parseLong(pickFirst(row.optString("Task_Date_Unix"), row.optString("taskDateUnix"), row.optString("TASK_DATE_UNIX")));
        plan.completedDate = pickFirst(row.optString("Completed_Date"), row.optString("Complete_Date"), row.optString("completedDate"), row.optString("Done_Date"));
        return plan;
    }

    private long[] resolveRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.SECOND, -1);
        return new long[]{start.getTimeInMillis() / 1000L, end.getTimeInMillis() / 1000L};
    }

    private String pickFirst(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private long parseLong(String value) {
        try {
            return value == null || value.trim().isEmpty() ? 0L : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String formatUnix(long unixValue) {
        if (unixValue <= 0) return "--";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(unixValue * 1000L));
    }

    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String i18n(String key) {
        return LanguageAPIUtils.i18n(key);
    }
}
