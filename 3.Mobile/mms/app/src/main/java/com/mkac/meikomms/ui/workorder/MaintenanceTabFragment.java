package com.mkac.meikomms.ui.workorder;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Intent;
import android.util.Log;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int currentStatusPosition = 0;
    private String currentDateFilter = "";
    private String currentGroupFilter = "";

    // Phân trang: mỗi lần tải PAGE_SIZE dòng, cuộn gần cuối thì tải tiếp
    private static final int PAGE_SIZE = 50;
    private static final int LOAD_MORE_THRESHOLD = 10;
    private int nextOffset = 0;
    private boolean hasMore = true;
    private boolean isLoadingPage = false;
    private int requestToken = 0;
    private final java.util.Set<String> seenTaskIds = new java.util.HashSet<>();

    private String serverUrl = "";
    private String schemaCore = "";
    private String schemaMms = "";
    private String machineId = "";
    // Bộ phận của tài khoản: FE chỉ thấy hạng mục FE, MA chỉ thấy hạng mục MA (và nhóm lọc là nhóm MA)
    private boolean isMaAccount = false;
    private final ActivityResultLauncher<Intent> enterWorkOrderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && isAdded()) {
                    // Làm mới dữ liệu tab bảo trì ngay khi lưu thành công
                    reloadData();
                }
            }
    );

    private static final String[] FILTER_STATUS_KEYS = {
            "All",
            "Incomplete",        //chưa hoàn thành
            "Overdue",          //quá hạn

//            "Approve",          //phê duyệt
//            "Checksheet OK",    //Checksheet OK
            "Checksheet NG"    //Checksheet NG

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

        if (currentGroupFilter == null || currentGroupFilter.isEmpty()) {
            WorkOrderDataActivity activity = (WorkOrderDataActivity) getActivity();
            if (activity != null) {
                int selectedIndex = activity.getSelectedGroupIndex();
                List<WorkOrderDataActivity.FeTeam> teams = activity.getDynamicMaintGroups();
                if (selectedIndex >= 0 && selectedIndex < teams.size()) {
                    currentGroupFilter = teams.get(selectedIndex).teamId;
                }
            }
        }

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
        isMaAccount = !WorkOrderEntryDialogHelper.isFeAccount(requireContext());

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"));
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), handler.getString("schema_data"), configManager.getProperty("schema_data"));
    }

    public void reloadDataForMachineId(String machineId) {
        this.machineId = machineId == null ? "" : machineId.trim();
        planList.clear();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (isAdded()) {
            reloadData();
        }
    }

    private String mapGroupToId(String groupName) {
        if (groupName == null) return "";
        WorkOrderDataActivity activity = (WorkOrderDataActivity) getActivity();
        if (activity != null) {
            List<WorkOrderDataActivity.FeTeam> teams = activity.getDynamicMaintGroups();
            for (WorkOrderDataActivity.FeTeam t : teams) {
                if (t.teamName.equalsIgnoreCase(groupName.trim()) || t.teamId.equalsIgnoreCase(groupName.trim())) {
                    return t.teamId;
                }
            }
        }
        switch (groupName.trim().toUpperCase()) {
            case "FE1": return "00011";
            case "FE2": return "00014";
            case "FE3": return "00030";
            case "FE4": return "00031";
            default: return groupName;
        }
    }

    public void reloadData() {
        loadPage(true);
    }

    /** WHERE gửi server: gồm cả lọc trạng thái + ngày (lọc phía server để phân trang không bị thiếu dòng). */
    private String buildWhere() {
        long[] range = resolveRange();

        StringBuilder whereBuilder = new StringBuilder();
        whereBuilder.append("1=1 AND (mt.Task_Date_Unix BETWEEN ").append(range[0]).append(" AND ").append(range[1]).append(") ");
        
        final String teamMemberTable = isMaAccount ? "MA_TEAM_MEMBER" : "FE_TEAM_MEMBER";
        if (currentGroupFilter != null && !currentGroupFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(currentGroupFilter.trim())) {
            String mappedGroup = mapGroupToId(currentGroupFilter);
            whereBuilder.append("AND (")
                    .append("  ( ")
                    .append("    mt.TASK_TYPE IN (0,1,2,3,4,5) ")
                    .append("    AND EXISTS (")
                    .append("      SELECT 1 FROM ").append(schemaMms).append(".").append(teamMemberTable).append(" fm ")
                    .append("      LEFT JOIN ").append(schemaCore).append(".USERS ux ON (fm.USER_ID = ux.USER_NAME OR fm.USER_ID = TO_CHAR(ux.ID)) ")
                    .append("      WHERE fm.TEAM_ID = '").append(mappedGroup.replace("'", "''")).append("' ")
                    .append("        AND fm.DELETED = 0 ")
                    .append("        AND ux.DELETED = 'N' ")
                    .append("        AND ( ")
                    .append("          UPPER(TRIM(ux.USER_NAME)) = UPPER(TRIM(mt.MAINTAINER_ID)) ")
                    .append("          OR UPPER(TRIM(ux.USER_NAME)) = UPPER(TRIM(mt.ACTUAL_MAINTANER_ID)) ")
                    .append("        ) ")
                    .append("    ) ")
                    .append("  ) ")
                    .append("  OR ")
                    .append("  ( ")
                    .append("    mt.TASK_TYPE IN (2,3,4,5) ")
                    .append("    AND mt.MAINTAINER_ID IS NULL ")
                    .append("    AND mt.ACTUAL_MAINTANER_ID IS NULL ")
                    .append("    AND mt.APPROVE_MAINTANER_ID IS NULL ")
                    .append("    AND EXISTS ( ")
                    .append("      SELECT 1 FROM ").append(schemaMms).append(".").append(teamMemberTable).append(" fm0 ")
                    .append("      WHERE fm0.TEAM_ID = '").append(mappedGroup.replace("'", "''")).append("' ")
                    .append("        AND fm0.DELETED = 0 ")
                    .append("    ) ")
                    .append("  ) ")
                    .append(") ");
        } else {
            whereBuilder.append("AND (mc.DEPARTMENT = 'FE' OR mt.TASK_TYPE = 1) ");
        }

        // Chỉ hiện hạng mục đúng BỘ PHẬN của tài khoản (trước đây FE thấy cả hạng mục MA vì TASK_TYPE = 1 áp cho mọi bộ phận).
        // FE: hạng mục FE (chưa gán bộ phận coi là FE như cũ); MA: chỉ hạng mục MA.
        whereBuilder.append(isMaAccount
                ? "AND UPPER(TRIM(mc.DEPARTMENT)) = 'MA' "
                : "AND NVL(UPPER(TRIM(mc.DEPARTMENT)), 'FE') = 'FE' ");

        whereBuilder.append("AND (mt.TASK_TYPE != '0' OR (iss.ISSUE_STATUS IS NOT NULL AND iss.ISSUE_STATUS != '3')) ")
                .append("AND mt.STATUS != 4 AND mt.TASK_TYPE = 1");

        // Lọc trạng thái (trước đây lọc ở client: mặc định hiện Chưa hoàn thành 0, Quá hạn 5, Checksheet NG 3)
        switch (currentStatusPosition) {
            case 1: whereBuilder.append(" AND mt.STATUS = 0"); break;
            case 2: whereBuilder.append(" AND mt.STATUS = 5"); break;
            case 3: whereBuilder.append(" AND mt.STATUS = 3"); break;
            default: whereBuilder.append(" AND mt.STATUS IN (0, 5, 3)"); break;
        }

        // Lọc ngày: cả ngày (theo múi giờ máy) của currentDateFilter dạng yyyy-MM-dd
        if (!currentDateFilter.isEmpty()) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                f.setTimeZone(TimeZone.getDefault());
                Date d = f.parse(currentDateFilter);
                if (d != null) {
                    long dayStart = d.getTime() / 1000L;
                    whereBuilder.append(" AND mt.Task_Date_Unix BETWEEN ").append(dayStart)
                            .append(" AND ").append(dayStart + 86399L);
                }
            } catch (Exception ignored) { }
        }

        if (this.machineId != null && !this.machineId.trim().isEmpty()) {
            String cleanMachineId = this.machineId;
            if (cleanMachineId.contains(" - ")) {
                cleanMachineId = cleanMachineId.split(" - ")[0].trim();
            }
            String escapedMachine = cleanMachineId.replace("'", "''");
            whereBuilder.append(" AND (LOWER(m.Machine_Id) LIKE LOWER('%").append(escapedMachine).append("%') ")
                    .append("OR LOWER(m.Machine_Name) LIKE LOWER('%").append(escapedMachine).append("%'))");
        }

        return whereBuilder.toString();
    }

    /** Tải 1 trang (reset=true: từ đầu, thay danh sách; false: trang kế tiếp, nối vào cuối). */
    private void loadPage(boolean reset) {
        if (!isAdded() || binding == null) return;
        if (!reset && (isLoadingPage || !hasMore)) return;

        final int token;
        if (reset) {
            token = ++requestToken; // bỏ kết quả của các request cũ còn đang chạy
            nextOffset = 0;
            hasMore = true;
            seenTaskIds.clear();
            binding.swipeRefreshMaintenance.setRefreshing(true);
        } else {
            token = requestToken;
        }
        isLoadingPage = true;

        final String where = buildWhere();
        final int offset = nextOffset;
        final android.content.Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            String mappedGroupForApi = mapGroupToId(currentGroupFilter);
            HttpClient.APIReturn rs = HttpClient.getMaintainTaskList(
                    appContext, serverUrl, schemaMms, schemaCore, where, PAGE_SIZE, offset, mappedGroupForApi
            );

            List<MaintenancePlan> pageList = new ArrayList<>();
            String errorMsg = null;
            int rawCount = 0;

            if (rs != null && rs.code == 200 && rs.data != null) {
                rawCount = rs.data.size();
                for (JSONObject row : rs.data) {
                    MaintenancePlan plan = parsePlan(row);
                    if (plan.taskId != null && !plan.taskId.isEmpty() && !seenTaskIds.contains(plan.taskId)) {
                        seenTaskIds.add(plan.taskId);
                        pageList.add(plan);
                    }
                }
            } else {
                errorMsg = (rs != null) ? rs.message : i18n("Lost connection to maintenance API server");
            }

            if (!isAdded() || binding == null) return;

            final String finalError = errorMsg;
            final int finalRawCount = rawCount;
            requireActivity().runOnUiThread(() -> {
                if (binding == null || token != requestToken) return;
                binding.swipeRefreshMaintenance.setRefreshing(false);
                isLoadingPage = false;

                if (finalError != null) {
                    Toast.makeText(getContext(), finalError, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Trang đầy -> có thể còn trang sau
                nextOffset += finalRawCount;
                hasMore = finalRawCount >= PAGE_SIZE;

                if (reset) planList.clear();
                int insertAt = planList.size();
                planList.addAll(pageList);

                if (adapter != null) {
                    if (reset) adapter.notifyDataSetChanged();
                    else adapter.notifyItemRangeInserted(insertAt, pageList.size());
                }

                if (planList.isEmpty() && !machineId.isEmpty()) {
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
            intent.putExtra("IS_WAREHOUSE_REQUEST", safe(plan.isWarehouseRequest));
//            startActivity(intent);

            enterWorkOrderLauncher.launch(intent);
        });
        binding.rvMaintenanceTasks.setAdapter(adapter);
        // Cuộn gần cuối danh sách -> tải trang kế tiếp
        binding.rvMaintenanceTasks.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                if (lm.findLastVisibleItemPosition() >= planList.size() - LOAD_MORE_THRESHOLD) {
                    loadPage(false);
                }
            }
        });
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
        plan.status = pickFirst(row.optString("Status"), row.optString("status"), row.optString("Status_Check"));
        plan.taskDateUnix = parseLong(pickFirst(row.optString("Task_Date_Unix"), row.optString("taskDateUnix")));
        plan.isWarehouseRequest = pickFirst(row.optString("Is_Warehouse_Request"), row.optString("isWarehouseRequest"), row.optString("IS_WAREHOUSE_REQUEST"));

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
        // Task_Date_Unix của ngày D = 00:00 UTC của ngày D (giờ VN coi như UTC), tức 07:00 giờ VN.
        // Cắt ở giờ thật hiện tại thì từ 00:00 đến 07:00 kế hoạch HÔM NAY bị coi là "tương lai" và mất.
        // -> cắt ở hết ngày hôm nay theo múi giờ máy (cộng offset múi giờ vào giờ hiện tại rồi lấy cuối ngày).
        long nowUnix = System.currentTimeMillis() / 1000L;
        long offsetSec = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000L;
        long presentUnix = ((nowUnix + offsetSec) / 86400L) * 86400L + 86399L;
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
        reloadData();
    }

    public void filterByGroup(String group) {
        String newGroup = group == null ? "" : group.trim();
        if (newGroup.equalsIgnoreCase(this.currentGroupFilter)) {
            return;
        }
        this.currentGroupFilter = newGroup;
        reloadData();
    }



    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String i18n(String key) {
        return LanguageAPIUtils.i18n(key);
    }

    public void filterByDate(String dateStr) {
        this.currentDateFilter = dateStr == null ? "" : dateStr.trim();
        reloadData(); // lọc ngày + trạng thái ở server rồi tải lại từ trang đầu
    }
}
