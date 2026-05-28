package com.mkac.meikomms.ui.workorder;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.databinding.FragmentMaintenanceTabBinding;
import com.mkac.meikomms.ui.workorder.model.MaintenancePlan;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaintenanceTabFragment extends Fragment {

    private FragmentMaintenanceTabBinding binding;
    private MaintenancePlanAdapter adapter;
    private final List<MaintenancePlan> planList = new ArrayList<>();
    private final List<MaintenancePlan> fullPlanList = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int currentStatusPosition = 0;
    private String currentDateFilter = "";

    private String serverUrl = "";
    private String schemaCore = "";
    private String schemaMms = "";
    private String machineId = "";

    private static final String[] FILTER_STATUS_KEYS = {
            "Machine Breakdown",
            "Preparing operation",
            "Stop due to shortage",
            "Stop by production plan",
            "Maintenance and repair"
    };

    public static String[] getLocalizedStatusLabels() {
        String[] localizedLabels = new String[FILTER_STATUS_KEYS.length];
        for (int i = 0; i < FILTER_STATUS_KEYS.length; i++) {
            localizedLabels[i] = LanguageAPIUtils.i18n(FILTER_STATUS_KEYS[i]);
        }
        return localizedLabels;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMaintenanceTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LanguageAPIUtils.setLang(binding.getRoot());

        initConfiguration();
        setupRecyclerView();

        binding.swipeRefreshMaintenance.setColorSchemeResources(R.color.green);
        binding.swipeRefreshMaintenance.setOnRefreshListener(this::reloadData);

        reloadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initConfiguration() {
        if (getContext() == null) return;
        PreferenceHandler handler = new PreferenceHandler(requireContext());
        ConfigManager configManager = new ConfigManager(requireContext());

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"));
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), handler.getString("schema_data"), configManager.getProperty("schema_data"));
    }

    public void reloadDataForMachineId(String machineId) {
        this.machineId = machineId == null ? "" : machineId.trim();
        planList.clear();
        fullPlanList.clear();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (isAdded()) {
            reloadData();
        }
    }

    public void reloadData() {
        if (!isAdded() || binding == null) return;

        binding.swipeRefreshMaintenance.setRefreshing(true);
        long[] range = resolveRange();

        if (this.machineId == null || this.machineId.trim().isEmpty()) {
            binding.swipeRefreshMaintenance.setRefreshing(false);
            planList.clear();
            fullPlanList.clear();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            return;
        }

        StringBuilder whereBuilder = new StringBuilder();
        whereBuilder.append("1=1 AND (mt.Task_Date_Unix BETWEEN ").append(range[0]).append(" AND ").append(range[1]).append(") ")
                .append("AND (mc.DEPARTMENT = 'FE' OR mt.TASK_TYPE = 1) ")
                .append("AND (mt.TASK_TYPE != '0' OR (iss.ISSUE_STATUS IS NOT NULL AND iss.ISSUE_STATUS != '3')) ")
                .append("AND mt.STATUS != 4 AND mt.TASK_TYPE = 1");

        String escapedMachine = this.machineId.replace("'", "''");
        whereBuilder.append(" AND (LOWER(m.Machine_Id) LIKE LOWER('%").append(escapedMachine).append("%') ")
                .append("OR LOWER(m.Machine_Name) LIKE LOWER('%").append(escapedMachine).append("%'))");

        android.content.Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getMaintainTaskList(
                    appContext, serverUrl, schemaMms, schemaCore, whereBuilder.toString(), 10000, 0, ""
            );

            List<MaintenancePlan> newList = new ArrayList<>();
            String errorMsg = null;

            if (rs != null && rs.code == 200 && rs.data != null) {
                List<String> seenTaskIds = new ArrayList<>();
                for (JSONObject row : rs.data) {
                    MaintenancePlan plan = parsePlan(row);

                    if (plan.taskId != null && !plan.taskId.isEmpty()) {
                        if (!seenTaskIds.contains(plan.taskId)) {
                            seenTaskIds.add(plan.taskId);
                            newList.add(plan);
                        }
                    }
                }
            } else {
                errorMsg = (rs != null) ? rs.message : i18n("Lost connection to maintenance API server");
            }

            if (!isAdded() || binding == null) return;

            final String finalError = errorMsg;
            requireActivity().runOnUiThread(() -> {
                binding.swipeRefreshMaintenance.setRefreshing(false);

                if (finalError != null) {
                    Toast.makeText(getContext(), finalError, Toast.LENGTH_SHORT).show();
                    return;
                }

                planList.clear();
                planList.addAll(newList);

                fullPlanList.clear();
                fullPlanList.addAll(newList);

                applyCombinedFilters();

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (newList.isEmpty() && !machineId.isEmpty()) {
                    Toast.makeText(getContext(), i18n("No maintenance plan found for machine") + ": " + machineId, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupRecyclerView() {
        binding.rvMaintenanceTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MaintenancePlanAdapter(planList, plan -> {
            if (!isAdded()) return;

            Intent intent = new Intent(requireContext(), EnterWorkOrderDataActivity.class);
            intent.putExtra("TASK_ID", safe(plan.taskId));
            intent.putExtra("MACHINE_ID", safe(plan.machineId));
            intent.putExtra("MACHINE_NAME", safe(plan.machineName));
            intent.putExtra("CATEGORY_ID", safe(plan.categoryId));
            intent.putExtra("CATEGORY_NAME", safe(plan.categoryName));
            intent.putExtra("TASK_DATE_UNIX", plan.taskDateUnix);
            intent.putExtra("STATUS", safe(plan.status));
            intent.putExtra("ASSIGNEE_NAME", safe(plan.assigneeName));
            intent.putExtra("EXECUTOR_NAME", safe(plan.executorName));
            startActivity(intent);
        });
        binding.rvMaintenanceTasks.setAdapter(adapter);
    }

    private MaintenancePlan parsePlan(JSONObject row) {
        MaintenancePlan plan = new MaintenancePlan();

        plan.taskId = pickFirst(row.optString("Task_Id"), row.optString("taskId"), row.optString("TASK_ID"));
        plan.machineId = pickFirst(row.optString("Machine_Id"), row.optString("machineId"));
        plan.machineName = pickFirst(row.optString("Machine_Name"), row.optString("machineName"));
        plan.categoryId = pickFirst(row.optString("Category_Id"), row.optString("categoryId"), row.optString("Category_Id_1"));
        plan.categoryName = pickFirst(row.optString("Category_Name"), row.optString("categoryName"));
        plan.assigneeName = pickFirst(row.optString("Full_Name_In_Charge"), row.optString("User_Name_In_Charge"), row.optString("Person_In_Charge"));
        plan.executorName = pickFirst(row.optString("Full_Name"), row.optString("Maintainer_Id"), row.optString("Actual_Maintaner_Id"));
        plan.status = pickFirst(row.optString("Status"), row.optString("status"));
        plan.taskDateUnix = parseLong(pickFirst(row.optString("Task_Date_Unix"), row.optString("taskDateUnix")));

        String doneUnix = pickFirst(row.optString("After_Approve_Task_Date_Unix"), row.optString("Approve_Task_Date_Unix"));
        if (!doneUnix.isEmpty() && !"0".equals(doneUnix)) {
            plan.completedDate = formatUnixDate(parseLong(doneUnix));
        } else {
            plan.completedDate = "";
        }

        return plan;
    }

//    private long[] resolveRange() {
//        Calendar start = Calendar.getInstance();
//        start.set(Calendar.DAY_OF_MONTH, 1);
//        start.set(Calendar.HOUR_OF_DAY, 0);
//        start.set(Calendar.MINUTE, 0);
//        start.set(Calendar.SECOND, 0);
//        start.set(Calendar.MILLISECOND, 0);
//
//        Calendar end = (Calendar) start.clone();
//        end.add(Calendar.MONTH, 1);
//        end.add(Calendar.SECOND, -1);
//        return new long[]{start.getTimeInMillis() / 1000L, end.getTimeInMillis() / 1000L};
//    }
    private long[] resolveRange() {
        long pastUnix = 0L;
        long presentUnix = System.currentTimeMillis() / 1000L;
        return new long[]{pastUnix, presentUnix};
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

    private String formatUnixDate(long unixValue) {
        if (unixValue <= 0) return "";
        try {
            long normalized = unixValue > 9999999999L ? unixValue : unixValue * 1000L;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            formatter.setTimeZone(TimeZone.getDefault());
            return formatter.format(new Date(normalized));
        } catch (Exception e) {
            return "";
        }
    }

    public void filterByStatus(int position) {
        this.currentStatusPosition = position;
        applyCombinedFilters();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String i18n(String key) {
        return LanguageAPIUtils.i18n(key);
    }

    public void filterByDate(String dateStr) {
        this.currentDateFilter = dateStr == null ? "" : dateStr.trim();
        applyCombinedFilters(); // Hàm chạy logic lọc song song cả Ngày UNIX + Trạng thái
    }

    private void applyCombinedFilters() {
        if (binding == null || adapter == null) return;

        List<MaintenancePlan> filteredList = new ArrayList<>();

        for (MaintenancePlan plan : fullPlanList) {
            if (plan == null) continue;

            boolean matchesDate = true;
            if (!currentDateFilter.isEmpty()) {
                String planDateStr = formatUnixToCompare(plan.taskDateUnix);
                matchesDate = currentDateFilter.equals(planDateStr);
            }

            String targetStatusCode = "";
            switch (currentStatusPosition) {
                case 0: targetStatusCode = "0"; break; // Chưa hoàn thành
                case 1: targetStatusCode = "1"; break; // Phê duyệt
                case 2: targetStatusCode = "2"; break; // Checksheet OK
                case 3: targetStatusCode = "3"; break; // Checksheet NG
                case 4: targetStatusCode = "5"; break; // Quá hạn
            }
            boolean matchesStatus = targetStatusCode.equalsIgnoreCase(plan.status);

            if (matchesDate && matchesStatus) {
                filteredList.add(plan);
            }
        }

        planList.clear();
        planList.addAll(filteredList);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static String formatUnixToCompare(long unixValue) {
        if (unixValue <= 0) return "";
        try {
            long normalized = unixValue > 9999999999L ? unixValue : unixValue * 1000L;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            formatter.setTimeZone(TimeZone.getDefault());
            return formatter.format(new Date(normalized));
        } catch (Exception e) {
            return "";
        }
    }
}
