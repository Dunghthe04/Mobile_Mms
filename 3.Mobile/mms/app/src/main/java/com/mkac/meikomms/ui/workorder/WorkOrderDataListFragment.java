package com.mkac.meikomms.ui.workorder;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.StringRes;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

public class WorkOrderDataListFragment extends Fragment {
    private static final String TAG = "WorkOrderDataListFrag";
    private static final String ARG_MACHINE_ID = "arg_machine_id";
    private static final String EXTRA_MACHINE_ID = "MACHINE_ID";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView tvWorkOrderSummary;
    private WorkOrderDataAdapter adapter;
    private final List<JSONObject> fullWorkOrderList = new ArrayList<>();
    private int currentStatusPosition = 0;
    private String currentDateFilter = "";

    private String serverDynamic = "";
    private String schemaData = "";
    private String schemaCore = "";
    private String machineId = "";
    private boolean isLoading = false;
    private static final String[] FILTER_STATUS_KEYS = {
            "Incomplete",
            "Overdue",
            "Completed",
            "Canceled"
    };

    public static String[] getLocalizedStatusLabels() {
        String[] localizedLabels = new String[FILTER_STATUS_KEYS.length];
        for (int i = 0; i < FILTER_STATUS_KEYS.length; i++) {
            // Gọi qua LanguageAPIUtils để bảo đảm an toàn luồng ngữ cảnh tĩnh
            localizedLabels[i] = LanguageAPIUtils.i18n(FILTER_STATUS_KEYS[i]);
        }
        return localizedLabels;
    }

    public static WorkOrderDataListFragment newInstance(String machineId) {
        WorkOrderDataListFragment fragment = new WorkOrderDataListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MACHINE_ID, machineId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        machineId = resolveMachineId();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_work_order_data_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initConfiguration();
        LanguageAPIUtils.setLang(view);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_work_order);
        recyclerView = view.findViewById(R.id.rv_work_order_data);
        tvWorkOrderSummary = view.findViewById(R.id.tv_work_order_summary);

        adapter = new WorkOrderDataAdapter(item -> {
            if (getContext() != null) {
                WorkOrderEntryDialogHelper.show(
                        getContext(),
                        item,
                        data -> reloadData()
                );
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(Color.parseColor("#00A680"));
            swipeRefresh.setOnRefreshListener(this::reloadData);
        }

        if (!machineId.isEmpty()) {
            reloadData();
        } else {
            updateSummary(0, "--");
        }
    }

    public void setMachineId(String machineId) {
        this.machineId = safeText(machineId);
        updateSummary(0, this.machineId.isEmpty() ? "--" : this.machineId);
        if (isAdded()) {
            reloadData();
        }
    }

    public void reloadDataForMachineId(String machineId) {
        this.machineId = safeText(machineId);
        updateSummary(0, this.machineId.isEmpty() ? "--" : this.machineId);

        if (adapter != null) {
            adapter.setItems(new ArrayList<>());
        }
        isLoading = false;
        reloadData();
    }

    public void reloadData() {
        if (!isAdded() || getActivity() == null || isLoading) {
            return;
        }

        if (safeText(this.machineId).isEmpty()) {
            this.machineId = resolveMachineId();
        }

        if (safeText(this.machineId).isEmpty()) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            adapter.setItems(new ArrayList<>());
            updateSummary(0, "--");
            return;
        }

        initConfiguration();

        PreferenceHandler handler = new PreferenceHandler(requireContext());
        String token = handler.getString("api_key");

        if (token == null || token.isEmpty()) {
            JSONObject userObj = handler.getJsonObject("user");
            if (userObj != null) {
                token = userObj.optString("token", "");
            }
        }

        if (token == null || token.isEmpty()) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), t(R.string.work_order_data_list_session_expired), Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        if (swipeRefresh != null) {
            swipeRefresh.post(() -> swipeRefresh.setRefreshing(true));
        }

        HttpClient.initToken(token);
        final String targetMachineId = this.machineId;
        final Context appContext = requireContext().getApplicationContext();

        new Thread(() -> {
            try {
                HttpClient.APIReturn apiReturn = HttpClient.getAllWorkOrderByMachineId(
                        appContext, serverDynamic, schemaData, schemaCore,  targetMachineId, 0, 200
                );

                Log.d(TAG, "API CODE = " + (apiReturn != null ? apiReturn.code : "NULL"));
                final List<JSONObject> items = new ArrayList<>();

                if (apiReturn != null && apiReturn.code == 200 && apiReturn.data != null) {
                    if (apiReturn.data instanceof List) {
                        List<?> rawList = (List<?>) apiReturn.data;
                        for (Object raw : rawList) {
                            processRawObject(raw, items);
                        }
                    } else if (apiReturn.data instanceof JSONArray) {
                        JSONArray arr = (JSONArray) apiReturn.data;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.optJSONObject(i);
                            if (obj != null) processNestedTable(obj, items);
                        }
                    } else if (apiReturn.data instanceof JSONObject) {
                        String rawStr = apiReturn.data.toString().trim();
                        if (rawStr.startsWith("[")) {
                            JSONArray arr = new JSONArray(rawStr);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.optJSONObject(i);
                                if (obj != null) processNestedTable(obj, items);
                            }
                        } else if (rawStr.startsWith("{")) {
                            JSONObject obj = new JSONObject(rawStr);
                            processNestedTable(obj, items);
                        }
                    }
                }

                Activity activity = getActivity();
                if (activity == null || !isAdded()) {
                    isLoading = false;
                    return;
                }

                activity.runOnUiThread(() -> {
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                    fullWorkOrderList.clear();
                    fullWorkOrderList.addAll(items);

                    applyCombinedFilters();

                    if (recyclerView != null) {
                        recyclerView.setVisibility(View.VISIBLE);
                    }

                    if (items.isEmpty()) {
                        Toast.makeText(requireContext(), t(R.string.work_order_data_list_no_machine_data) + ": " + targetMachineId, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "reloadData error", e);
                Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        isLoading = false;
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    });
                }
            }
        }).start();
    }

    private void processRawObject(Object raw, List<JSONObject> items) {
        if (raw instanceof JSONObject) {
            processNestedTable((JSONObject) raw, items);
        } else if (raw instanceof Map) {
            JSONObject obj = new JSONObject((Map<?, ?>) raw);
            processNestedTable(obj, items);
        } else if (raw != null) {
            try {
                JSONObject obj = new JSONObject(raw.toString());
                processNestedTable(obj, items);
            } catch (Exception ignored) {}
        }
    }

    private void processNestedTable(JSONObject obj, List<JSONObject> items) {
        JSONArray nestedArray = obj.optJSONArray("Table");
        if (nestedArray == null) nestedArray = obj.optJSONArray("data");
        if (nestedArray == null) nestedArray = obj.optJSONArray("Data");

        if (nestedArray != null) {
            for (int i = 0; i < nestedArray.length(); i++) {
                JSONObject row = nestedArray.optJSONObject(i);
                if (row != null) items.add(row);
            }
        } else {
            items.add(obj);
        }
    }

    private void initConfiguration() {
        try {
            PreferenceHandler handler = new PreferenceHandler(requireContext());
            ConfigManager configManager = new ConfigManager(requireContext());

            String baseUrl = handler.getString("server_url");
            if (baseUrl == null || baseUrl.isEmpty()) baseUrl = configManager.getProperty("server_url");

            serverDynamic = handler.getString("server_dynamic_url");
            if (serverDynamic == null || serverDynamic.isEmpty()) serverDynamic = configManager.getProperty("server_dynamic_url");

            if (serverDynamic == null || serverDynamic.isEmpty()) {
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    serverDynamic = baseUrl.endsWith("/") ? baseUrl + "api/dynamics" : baseUrl + "/api/dynamics";
                } else {
                    serverDynamic = "http://192.86.0.225:9101/api/dynamics";
                }
            }

            schemaData = handler.getString("schema_mms");
            if (schemaData == null || schemaData.isEmpty()) schemaData = configManager.getProperty("schema_mms");
            if (schemaData == null || schemaData.isEmpty()) schemaData = handler.getString("schema_data");
            if (schemaData == null || schemaData.isEmpty()) schemaData = configManager.getProperty("schema_data");
            if (schemaData == null || schemaData.isEmpty()) schemaData = "MES_MMS_MKHC";

            schemaCore = handler.getString("schema_core");
            if (schemaCore == null || schemaCore.isEmpty()) schemaCore = configManager.getProperty("schema_core");
            if (schemaCore == null || schemaCore.isEmpty()) schemaCore = "MES_CORE_MKHC";

        } catch (Exception e) {
            Log.e(TAG, "Config error", e);
        }
    }

    private String resolveMachineId() {
        if (!safeText(machineId).isEmpty()) return machineId;
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_MACHINE_ID)) {
            return safeText(args.getString(ARG_MACHINE_ID));
        }
        if (getActivity() != null && getActivity().getIntent() != null) {
            String mid = getActivity().getIntent().getStringExtra(EXTRA_MACHINE_ID);
            if (mid == null || mid.isEmpty()) {
                mid = getActivity().getIntent().getStringExtra("machine_id");
            }
            return safeText(mid);
        }
        return "";
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String t(@StringRes int resId) {
        return LanguageAPIUtils.i18n(requireContext(), resId);
    }

    private void updateSummary(int count, String currentMachine) {
        if (tvWorkOrderSummary != null) {
            tvWorkOrderSummary.setText(i18n("Machine Code") + ": " +
                    (currentMachine.isEmpty() ? "--" : currentMachine) +
                    " | " + i18n("Quantity") + ": " + count);
        }
    }

    // --- ADAPTER BINDING WORK ORDER DATA INTO DATA CARD ---
    static class WorkOrderDataAdapter extends RecyclerView.Adapter<WorkOrderDataAdapter.VH> {
        interface OnEnterClickListener { void onEnter(JSONObject item); }

        private final List<JSONObject> items = new ArrayList<>();
        private final OnEnterClickListener listener;

        WorkOrderDataAdapter(OnEnterClickListener listener) {
            this.listener = listener;
        }

        void setItems(List<JSONObject> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_enter_work_order_data_card, parent, false);
            LanguageAPIUtils.setLang(v);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject data = items.get(position);

            // 1. Số thứ tự thẻ
            holder.tvItemIndex.setText(String.valueOf(position + 1));

            // 2. Mã Lệnh Work Order (Gắn vào tv_param_name)
            String woCode = safeGet(data, "WO_CODE", "Wo_Code", "wo_code", "WO_Code_Today");
            holder.tvParamName.setText(woCode.isEmpty() ? "—" : woCode);

            // 3. Mã Máy (Gắn nhanh vào tag Đo số)
            String machineInline = safeGet(data, "Machine_Id", "MACHINE_ID", "machine_id", "MachineId", "Machine_Name");
            holder.tvTagParamType.setText(machineInline.isEmpty() ? i18n("Machine") : machineInline);

            // 4. Loại hình công việc nghiệp vụ (Gắn nhanh vào tag Bar)
            String workTypeInline = safeGet(data, "Work_Type", "WORK_TYPE", "work_type", "WorkType");
            holder.tvTagUom.setText(workTypeInline.isEmpty() ? "BM" : workTypeInline);
            holder.tvTagUom.setVisibility(workTypeInline.isEmpty() ? View.GONE : View.VISIBLE);

            // 5. Ngày & Giờ yêu cầu (Gắn vào lịch sử kỳ trước)
            holder.tvLabelLastValue.setText(i18n("Request date") + ":");
            String reqDate = safeGet(data, "Request_Date", "REQUEST_DATE", "request_date");
            String reqTime = safeGet(data, "Request_Time", "REQUEST_TIME", "request_time");
            String formattedDate = formatDate(reqDate);
            String fullTimeDisplay = (formattedDate + " " + reqTime).trim();
            holder.tvTagLastValue.setText(fullTimeDisplay.isEmpty() ? "—" : fullTimeDisplay);

            // =========================================================================
            // HOÁN ĐỔI LỚP 1: tv_tag_entry_status HIỂN THỊ TRẠNG THÁI MA (Status_1)
            // =========================================================================
            String status1Value = safeGet(data, "Status_1", "STATUS_1", "status_1").trim();
            String status1Display;
            String status1BgColor;
            String status1TextColor;

            switch (status1Value) {
                case "0":
                    status1Display = i18n("Incomplete");
                    status1BgColor = "#F3F4F6";   // Nền xám nhạt
                    status1TextColor = "#4B5563"; // Chữ đen xám
                    break;
                case "1":
                    status1Display = i18n("Completed");
                    status1BgColor = "#D1FAE5";   // Nền xanh lá nhạt
                    status1TextColor = "#047857"; // Chữ xanh lá đậm
                    break;
                case "4":
                    status1Display = i18n("Canceled");
                    status1BgColor = "#FEE2E2";   // Nền đỏ nhạt
                    status1TextColor = "#B91C1C"; // Chữ đỏ đậm
                    break;
                case "5":
                    status1Display = i18n("Overdue");
                    status1BgColor = "#FFEEEE";   // Nền đỏ tươi nhạt
                    status1TextColor = "#E11D48"; // Chữ hồng đỏ
                    break;
                default:
                    status1Display = status1Value.isEmpty() ? "—" : status1Value;
                    status1BgColor = "#F3F4F6";
                    status1TextColor = "#4B5563";
                    break;
            }

            holder.tvTagEntryStatus.setText(status1Display);
            holder.tvTagEntryStatus.setTextColor(Color.parseColor(status1TextColor));
            androidx.core.view.ViewCompat.setBackgroundTintList(
                    holder.tvTagEntryStatus,
                    android.content.res.ColorStateList.valueOf(Color.parseColor(status1BgColor))
            );

            // =========================================================================
            // 7. Khối Metadata Trái (CẬP NHẬT: BÓC TÁCH CÔNG ĐOẠN TỪ Physical_Group_Name)
            // =========================================================================
            // Đã ưu tiên bóc tách từ khóa kết quả "Physical_Group_Name" từ log hình debug lên đầu dải tìm kiếm
            String process = safeGet(data, "Physical_Group_Name", "Physical_group_name", "physical_group_name", "Process", "PROCESS", "process_name");
            holder.tvSpecMin.setText(i18n("Process") + ": " + (process.isEmpty() ? "—" : process));

            String workTypeDetail = safeGet(data, "Work_Type_Name", "Work_Type", "WORK_TYPE");
            holder.tvSpecMax.setText(i18n("Type") + ": " + (workTypeDetail.isEmpty() ? "BM" : workTypeDetail));

            // HOÁN ĐỔI LỚP 2: tv_spec_target CHUYỂN SANG HIỂN THỊ TRẠNG THÁI VẬN HÀNH (Status 0,1,2,5,6)
            String status = safeGet(data, "Status", "status", "WO_STATUS", "Wo_Status").trim();
            String statusDisplay;
            String statusColor;

            switch (status) {
                case "0":
                    statusDisplay = i18n("Machine Breakdown");
                    statusColor = "#B91C1C"; // Chữ đỏ đậm
                    break;
                case "1":
                    statusDisplay = i18n("Preparing operation");
                    statusColor = "#2563EB"; // Chữ xanh dương đậm
                    break;
                case "2":
                    statusDisplay = i18n("Stop due to shortage");
                    statusColor = "#D97706"; // Chữ cam đậm
                    break;
                case "5":
                    statusDisplay = i18n("Stop by production plan");
                    statusColor = "#4B5563"; // Chữ xám đậm
                    break;
                case "6":
                    statusDisplay = i18n("Maintenance and repair");
                    statusColor = "#047857"; // Chữ xanh lá đậm
                    break;
                default:
                    statusDisplay = status.isEmpty() ? "—" : status;
                    statusColor = "#4B5563";
                    break;
            }

            holder.tvSpecTarget.setText(i18n("Status") + ": " + statusDisplay);
            holder.tvSpecTarget.setTextColor(Color.parseColor(statusColor));

            // =========================================================================
            // 8. Khối Metadata Phải (Người tạo, Người yêu cầu, Số ngày trôi qua)
            // =========================================================================
            String creator = safeGet(data, "Creator", "CREATOR", "Create_By", "create_by", "Create_Full_Name", "Full_Name");
            holder.tvActualValue.setText(i18n("Creator") + ": " + (creator.isEmpty() ? "—" : creator));

            String requester = safeGet(data, "Request_User", "Request_user", "request_user", "Requester", "Request_By", "Full_Name");
            holder.tvEntryMethod.setText(i18n("Requester") + ": " + (requester.isEmpty() ? "—" : requester));

            String elapsed = safeGet(data, "Elapsed_Days", "Elapsed", "elapsed_days", "ELAPSED_DAYS");
            holder.tvDeviationAlert.setText(i18n("Elapsed days") + ": " + (elapsed.isEmpty() ? "0 " + i18n("Day") : elapsed));

            // 9. Nội dung mô tả chi tiết yêu cầu sửa chữa
            String content = safeGet(data, "Request_Reason", "REQUEST_REASON", "Content", "content", "Note");
            holder.tvEntryInstruction.setText(content.isEmpty() ? i18n("Request reason") : content);

            // 10. Gán ID ngầm phục vụ xử lý logic sự kiện nếu cần
            holder.tvHiddenParamId.setText(woCode);

            // 11. Đăng ký sự kiện click nút bấm hành động trên thẻ
            holder.tvBtnEnterWo.setText(i18n("Enter Work Order Data"));
            holder.tvBtnEnterWo.setOnClickListener(v -> {
                if (listener != null) listener.onEnter(data);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvItemIndex, tvParamName, tvTagParamType, tvTagUom, tvLabelLastValue, tvTagLastValue;
            TextView tvTagEntryStatus, tvSpecMin, tvSpecMax, tvSpecTarget;
            TextView tvActualValue, tvEntryMethod, tvDeviationAlert;
            TextView tvHiddenParamId, tvHiddenStepId, tvEntryInstruction, tvBtnEnterWo;

            VH(@NonNull View itemView) {
                super(itemView);
                tvItemIndex = itemView.findViewById(R.id.tv_item_index);
                tvParamName = itemView.findViewById(R.id.tv_param_name);
                tvTagParamType = itemView.findViewById(R.id.tv_tag_param_type);
                tvTagUom = itemView.findViewById(R.id.tv_tag_uom);
                tvLabelLastValue = itemView.findViewById(R.id.tv_label_last_value);
                tvTagLastValue = itemView.findViewById(R.id.tv_tag_last_value);
                tvTagEntryStatus = itemView.findViewById(R.id.tv_tag_entry_status);
                tvSpecMin = itemView.findViewById(R.id.tv_spec_min);
                tvSpecMax = itemView.findViewById(R.id.tv_spec_max);
                tvSpecTarget = itemView.findViewById(R.id.tv_spec_target);
                tvActualValue = itemView.findViewById(R.id.tv_actual_value);
                tvEntryMethod = itemView.findViewById(R.id.tv_entry_method);
                tvDeviationAlert = itemView.findViewById(R.id.tv_deviation_alert);
                tvHiddenParamId = itemView.findViewById(R.id.tv_hidden_param_id);
                tvHiddenStepId = itemView.findViewById(R.id.tv_hidden_step_id);
                tvEntryInstruction = itemView.findViewById(R.id.tv_entry_instruction);
                tvBtnEnterWo = itemView.findViewById(R.id.tv_btn_enter_wo);
            }
        }

        private static String safeGet(JSONObject obj, String... keys) {
            if (obj == null) return "";
            for (String key : keys) {
                if (obj.has(key)) {
                    String val = obj.optString(key, "").trim();
                    if (!val.isEmpty() && !"null".equalsIgnoreCase(val)) {
                        return val;
                    }
                }
            }
            return "";
        }

        private static String formatDate(String dateStr) {
            if (dateStr == null || dateStr.isEmpty() || "null".equalsIgnoreCase(dateStr)) {
                return "";
            }
            try {
                String[] formats = {
                        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy"
                };
                for (String fmt : formats) {
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat(fmt, Locale.getDefault());
                        Date d = parser.parse(dateStr);
                        if (d != null) {
                            return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(d);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            return dateStr;
        }
    }

    public void filterByStatus(int position) {
        this.currentStatusPosition = position;
        applyCombinedFilters();
    }

    private static String safeGetFromObject(JSONObject obj, String... keys) {
        if (obj == null) return "";
        for (String key : keys) {
            if (obj.has(key)) {
                String val = obj.optString(key, "").trim();
                if (!val.isEmpty() && !"null".equalsIgnoreCase(val)) {
                    return val;
                }
            }
        }
        return "";
    }

    public void filterByDate(String dateStr) {
        this.currentDateFilter = dateStr == null ? "" : dateStr.trim();
        applyCombinedFilters();
    }

    private void applyCombinedFilters() {
        if (!isAdded() || adapter == null) return;

        List<JSONObject> filteredList = new ArrayList<>();

        for (JSONObject item : fullWorkOrderList) {
            if (item == null) continue;

            String rawRequestDate = safeGetFromObject(item, "Request_Date", "REQUEST_DATE", "request_date");
            boolean matchesDate = true;
            if (!currentDateFilter.isEmpty()) {
                matchesDate = rawRequestDate.startsWith(currentDateFilter);
            }

            String status = safeGetFromObject(item, "Status", "status", "WO_STATUS");
            boolean matchesStatus = false;
            switch (currentStatusPosition) {
                case 0:
                    if (status.isEmpty() || "0".equals(status) || "1".equals(status) || "2".equals(status) || "Pending".equalsIgnoreCase(status)) {
                        matchesStatus = true;
                    }
                    break;
                case 1:
                    if ("5".equals(status) || "Overdue".equalsIgnoreCase(status)) {
                        matchesStatus = true;
                    }
                    break;
                case 2:
                    if ("6".equals(status) || "Completed".equalsIgnoreCase(status)) {
                        matchesStatus = true;
                    }
                    break;
                case 3:
                    if ("4".equals(status) || "Cancelled".equalsIgnoreCase(status)) {
                        matchesStatus = true;
                    }
                    break;
            }

            if (matchesDate && matchesStatus) {
                filteredList.add(item);
            }
        }

        adapter.setItems(filteredList);
        updateSummary(filteredList.size(), machineId);
    }
}