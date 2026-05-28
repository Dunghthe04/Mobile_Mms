package com.mkac.meikomms.ui.workorder;

import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.databinding.ActivityEnterWorkOrderDataBinding;
import com.mkac.meikomms.databinding.DialogChildMaintenanceItemsBinding;
import com.mkac.meikomms.ui.workorder.model.MaintenanceItem;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EnterWorkOrderDataActivity extends AppCompatActivity {
    private ActivityEnterWorkOrderDataBinding binding;
    private MaintenanceCheckAdapter parentAdapter;
    private MaintenanceCheckAdapter currentChildAdapter = null;
    private final List<MaintenanceItem> parentItems = new ArrayList<>();
    private final Map<String, List<MaintenanceItem>> childItemsByParent = new HashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private String serverUrl = "";
    private String schemaCore = "";
    private String schemaMms = "";
    private String currentUserId = "";
    private String taskId = "";
    private String machineId = "";
    private String machineName = "";
    private String categoryId = "";
    private String categoryName = "";
    private long taskDateUnix = 0L;
    private String taskStatus = "";

    private MaintenanceItem pendingUploadItem;
    private String lastSaveErrorMessage = null;
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && pendingUploadItem != null) {
                    uploadImageForItem(pendingUploadItem, uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEnterWorkOrderDataBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LanguageAPIUtils.init(this);
        loadConfiguration();
        parseIntentData();
        applyLanguage();
        LanguageAPIUtils.setLang(binding.getRoot());
        setupParentRecyclerView();
        loadParentCheckList();

        binding.btnSaveMaintenance.setOnClickListener(v -> executeSaveAction());
    }

    private void loadConfiguration() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"));
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), handler.getString("schema_data"), configManager.getProperty("schema_data"));

    }

    private void parseIntentData() {
        taskId = safe(getIntent().getStringExtra("TASK_ID"));
        machineId = safe(getIntent().getStringExtra("MACHINE_ID"));
        machineName = safe(getIntent().getStringExtra("MACHINE_NAME"));
        categoryId = safe(getIntent().getStringExtra("CATEGORY_ID"));
        categoryName = safe(getIntent().getStringExtra("CATEGORY_NAME"));
        taskDateUnix = getIntent().getLongExtra("TASK_DATE_UNIX", 0L);
        taskStatus = safe(getIntent().getStringExtra("STATUS"));

        String assignee = safe(getIntent().getStringExtra("ASSIGNEE_NAME"));
        String executor = safe(getIntent().getStringExtra("EXECUTOR_NAME"));

        binding.tvPlanMachine.setText(machineId.isEmpty() ? machineName : machineId + " - " + machineName);
        binding.tvPlanCategory.setText(i18n("Category Name") + ": " + categoryName);

        updateTaskStatusBadgeUi(taskStatus);

        binding.tvPlanStatus.setText(resolveStatusLabel(taskStatus));
        binding.tvPlanAssignee.setText(i18n("Person in charge") + ": " + (assignee.isEmpty() ? "--" : assignee));
        binding.tvPlanExecutor.setText(i18n("Execute task") + ": " + (executor.isEmpty() ? "--" : executor));
        binding.tvPlanScheduledDate.setText(i18n("Plan") + ": " + formatUnix(taskDateUnix));
        binding.tvPlanCompletedDate.setText(i18n("Done") + ": --");
    }

    private void updateTaskStatusBadgeUi(String status) {
        if (binding.tvPlanStatus == null) return;

        String cleanStatus = safe(status);
        switch (cleanStatus) {
            case "0":
                binding.tvPlanStatus.setText(i18n("Pending"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#F3F4F6")); // Nền Xám
                binding.tvPlanStatus.setTextColor(Color.parseColor("#4B5563"));       // Chữ Đen Xám
                break;
            case "1":
                binding.tvPlanStatus.setText(i18n("Done") + " / " + i18n("Approve"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#EFF6FF")); // Nền Xanh dương nhạt
                binding.tvPlanStatus.setTextColor(Color.parseColor("#2563EB"));       // Chữ Xanh dương đậm
                break;
            case "2":
                binding.tvPlanStatus.setText(i18n("Checksheet OK"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#D1FAE5")); // Nền Xanh lá nhạt
                binding.tvPlanStatus.setTextColor(Color.parseColor("#047857"));       // Chữ Xanh lá đậm
                break;
            case "3":
                // ĐỒNG BỘ CHUẨN XÁC CHỮ MÀU ĐỎ ĐẬM TRÊN NỀN ĐỎ NHẠT KHỚP VỚI ADAPTER OUTSIDE
                binding.tvPlanStatus.setText(i18n("Checksheet NG"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#FEE2E2")); // Nền Đỏ nhạt
                binding.tvPlanStatus.setTextColor(Color.parseColor("#B91C1C"));       // Chữ Đỏ đậm
                break;
            case "5":
                binding.tvPlanStatus.setText(i18n("Overdue"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#FFEEEE")); // Nền Đỏ tươi nhạt
                binding.tvPlanStatus.setTextColor(Color.parseColor("#E11D48"));       // Chữ Hồng Đỏ đậm
                break;
            default:
                binding.tvPlanStatus.setText(i18n("Pending"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#F3F4F6"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#4B5563"));
                break;
        }
    }

    private void applyLanguage() {
        binding.tvTitle.setText(i18n("Enter maintenance data"));
        binding.tvChecklistHeader.setText(i18n("Checklist"));
        binding.tvTableIndicator.setText(i18n("Parent"));
        binding.btnEditPlan.setText(i18n("Edit"));
        binding.btnBackToParent.setText(i18n("Back"));
        binding.btnSaveMaintenance.setText(i18n("Save"));
    }

    private void setupParentRecyclerView() {
        binding.rvMaintenanceItems.setLayoutManager(new LinearLayoutManager(this));
        parentAdapter = new MaintenanceCheckAdapter(parentItems, new MaintenanceCheckAdapter.OnItemActionListener() {
            @Override
            public void onInfoClick(MaintenanceItem item) { openChildBottomSheetDialog(item); }
            @Override
            public void onHistoryClick(MaintenanceItem item) { loadHistoryForItem(item); }
            @Override
            public void onUploadClick(MaintenanceItem item) {
                pendingUploadItem = item;
                imagePickerLauncher.launch("image/*");
            }
        });
        binding.rvMaintenanceItems.setAdapter(parentAdapter);
    }

    private void uploadImageForItem(MaintenanceItem item, Uri uri) {
        if (item == null || uri == null) return;
        Toast.makeText(this, i18n("Uploading image..."), Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.uploadPreventiveImage(this, serverUrl, taskId, item.checkId, uri);
            if (rs != null && rs.code == 200) {
                String uploadedReference = resolveUploadedImageReference(rs, uri);
                if (!uploadedReference.isEmpty()) {
                    List<String> uploaded = new ArrayList<>();
                    uploaded.add(uploadedReference);
                    mergeUploadedImages(item, uploaded);
                    runOnUiThread(() -> {
                        if (currentChildAdapter != null) currentChildAdapter.notifyDataSetChanged();
                        if (parentAdapter != null) parentAdapter.notifyDataSetChanged();
                        Toast.makeText(this, i18n("Uploaded 1 image for") + ": " + item.checkName, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
            }
            runOnUiThread(() -> Toast.makeText(this, i18n("Image upload error") + ": " + i18n("No image data received from server"), Toast.LENGTH_LONG).show());
        });
    }



    private void loadParentCheckList() {
        if (serverUrl.isEmpty() || taskId.isEmpty()) return;

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getParentMaintenanceItems(this, serverUrl, schemaCore, schemaMms, categoryId, taskId, machineId);

            if (rs.code == 200 && rs.data != null) {
                List<MaintenanceItem> loadedItems = new ArrayList<>();

                for (JSONObject row : rs.data) loadedItems.add(parseItem(row, ""));
                runOnUiThread(() -> {
                    parentItems.clear();
                    parentItems.addAll(loadedItems);
                    parentAdapter.notifyDataSetChanged();
                });
            }
        });
    }

    // Hàm bổ trợ dựng link ảnh theo đường dẫn web public/imagesForComponentPreventive trên server
    private String buildMaintenanceImageUrl(String baseUrl, String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        if (fileName.startsWith("http://") || fileName.startsWith("https://")) return fileName;
        String finalUrl = buildPublicImageBaseUrl(baseUrl);
        if (finalUrl.isEmpty()) return "/public/imagesForComponentPreventive/" + fileName;
        return finalUrl + "/public/imagesForComponentPreventive/" + fileName;
    }

    private String buildPublicImageBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) return "";

        try {
            String protocol = value.contains("://") ? value.substring(0, value.indexOf("://")) : "http";
            String hostPart = value.contains("://") ? value.substring(value.indexOf("://") + 3) : value;
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf('/'));
            }
            if (hostPart.contains(":")) {
                hostPart = hostPart.substring(0, hostPart.indexOf(':'));
            }
            return protocol + "://" + hostPart + ":9100";
        } catch (Exception e) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }

    private void openChildBottomSheetDialog(MaintenanceItem parentItem) {
        if (parentItem == null || parentItem.checkId == null) return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        DialogChildMaintenanceItemsBinding dialogBinding = DialogChildMaintenanceItemsBinding.inflate(getLayoutInflater());
        bottomSheetDialog.setContentView(dialogBinding.getRoot());
        LanguageAPIUtils.setLang(dialogBinding.getRoot());
        dialogBinding.tvDialogParentTitle.setText(i18n("Parent Item") + ": " + parentItem.checkName);
        dialogBinding.rvChildItems.setLayoutManager(new LinearLayoutManager(this));
        List<MaintenanceItem> childItems = new ArrayList<>();
        MaintenanceCheckAdapter childAdapter = new MaintenanceCheckAdapter(childItems, new MaintenanceCheckAdapter.OnItemActionListener() {
            @Override public void onInfoClick(MaintenanceItem item) {}
            @Override public void onHistoryClick(MaintenanceItem item) { loadHistoryForItem(item); }
            @Override public void onUploadClick(MaintenanceItem item) {
                pendingUploadItem = item;
                imagePickerLauncher.launch("image/*");
            }
        });
        dialogBinding.rvChildItems.setAdapter(childAdapter);

        currentChildAdapter = childAdapter;

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getChildMaintenanceItems(this, serverUrl, schemaMms, parentItem.checkId, taskId);
            if (rs.code == 200 && rs.data != null) {
                List<MaintenanceItem> loadedChildren = new ArrayList<>();
                for (JSONObject row : rs.data) loadedChildren.add(parseItem(row, parentItem.checkId));
                runOnUiThread(() -> {
                    childItems.clear();
                    childItems.addAll(loadedChildren);
                    childItemsByParent.put(parentItem.checkId, new ArrayList<>(childItems));
                    childAdapter.notifyDataSetChanged();
                });
            }
        });

        bottomSheetDialog.setOnDismissListener(dialog -> currentChildAdapter = null);

        dialogBinding.btnConfirmChildData.setOnClickListener(v -> {
            syncParentFromChildren(parentItem, childItems);
            parentAdapter.notifyDataSetChanged();
            bottomSheetDialog.dismiss();
        });
        dialogBinding.btnCloseDialog.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }

    // TODO: thuật toán phán định
    private void syncParentFromChildren(MaintenanceItem parentItem, List<MaintenanceItem> childItems) {
        if (childItems == null || childItems.isEmpty()) return;

        boolean allOk = true;
        boolean anyNg = false;
        boolean anyEmpty = false;

        for (MaintenanceItem child : childItems) {
            String childStatus = resolveInitialStatus(child);

            if ("NG".equalsIgnoreCase(childStatus)) {
                anyNg = true;
                break; // Chỉ cần 1 mục con lỗi sập hệ thống -> Cha thành NG ngay
            }
            if (childStatus.isEmpty()) {
                anyEmpty = true;
            }
            if (!"OK".equalsIgnoreCase(childStatus)) {
                allOk = false;
            }
        }

        if (anyNg) {
            parentItem.checkValue = "NG";
            parentItem.initialStatus = "NG";
        } else if (anyEmpty || !allOk) {
            parentItem.checkValue = "";
            parentItem.initialStatus = "";
        } else {
            parentItem.checkValue = "OK";
            parentItem.initialStatus = "OK";
        }

        parentItem.locked = true;
        parentItem.refreshChangedState();
    }

    // TODO: Lưu
    private void executeSaveAction() {
        Toast.makeText(this, i18n("Saving maintenance data..."), Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            int savedCount = 0;
            boolean isAllSaveSuccess = true;
            String itemErrorMessage = null;

            try {
                for (MaintenanceItem parentItem : parentItems) {
                    List<MaintenanceItem> children = childItemsByParent.get(parentItem.checkId);
                    if (children != null) {
                        for (MaintenanceItem child : children) {
                            child.refreshChangedState();
                            if (child.changed) {
                                int result = saveSingleItemWithHistory(child);
                                if (result > 0) savedCount++;
                                else {
                                    isAllSaveSuccess = false;
                                    itemErrorMessage = (lastSaveErrorMessage != null) ? lastSaveErrorMessage : (i18n("Save details failed at child item") + ": " + child.checkName);
                                    break;
                                }
                            }
                        }
                    }
                    if (!isAllSaveSuccess) break;

                    // 2. Sau khi lưu các con, nội suy lại trạng thái Cha từ danh sách con (nếu có)
                    if (children != null && !children.isEmpty()) {
                        syncParentFromChildren(parentItem, children);
                    }

                    // 3. Lưu bản thân Cha (nếu có thay đổi sau khi nội suy)
                    parentItem.refreshChangedState();
                    if (parentItem.changed) {
                        boolean result = saveItemWithLatestRemoteHistory(parentItem);
                        if (result) savedCount++;
                        else {
                            isAllSaveSuccess = false;
                            itemErrorMessage = (lastSaveErrorMessage != null) ? lastSaveErrorMessage : (i18n("Save details failed at parent item") + ": " + parentItem.checkName);
                            break;
                        }
                    }
                }

                if (!isAllSaveSuccess) {
                    runOnUiThread(() -> Toast.makeText(this, i18n("Save detailed info failed. Please try again!"), Toast.LENGTH_LONG).show());
                    return;
                }

                // --- BƯỚC 5.2: CẬP NHẬT TRẠNG THÁI TỔNG CỦA TASK (CÓ ĐIỀU KIỆN CHẶN CHƯA NHẬP ĐỦ) ---
                String overallStatus = resolveOverallStatus();

                if (overallStatus.isEmpty()) {
                    // NGHIỆP VỤ: Nếu trạng thái tổng trả về rỗng (Do đang làm dở, các mục khác chưa nhập gì)
                    // CHỈ lưu thành công chi tiết hạng mục vừa nhập (Bước 5.1) và THOÁT, KHÔNG cập nhật trạng thái Task tổng.
                    final int finalSavedCount = savedCount;
                    runOnUiThread(() -> {
                        Toast.makeText(this, i18n("Temporarily saved successfully") + " " + finalSavedCount + " " + i18n("detailed items") + "!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } else {
                    // Khi đã phán định rõ ràng: Hoặc tất cả đều OK ("2") hoặc có bất kỳ mục nào lỗi NG ("3")
                    // Validate required fields before calling server to avoid generic validation error
                    StringBuilder missing = new StringBuilder();
                    if (taskId == null || taskId.isEmpty()) missing.append("Task_Id ");
                    if (machineId == null || machineId.isEmpty()) missing.append("Machine_Id ");
                    if (taskDateUnix <= 0) missing.append("Task_Date_Unix ");
                    if (currentUserId == null || currentUserId.isEmpty()) missing.append("Maintainer_Id ");
                    if (categoryId == null || categoryId.isEmpty()) missing.append("Category_Id ");

                    if (missing.length() > 0) {
                        final String miss = missing.toString().trim();
                        runOnUiThread(() -> Toast.makeText(this, i18n("Missing required fields") + ": " + miss + " - " + i18n("Do not send status update"), Toast.LENGTH_LONG).show());
                        return;
                    }

                    HttpClient.APIReturn updateTaskResult = HttpClient.updateOverallTaskStatus(
                            this, serverUrl, taskId, machineId, taskDateUnix, currentUserId, categoryId, overallStatus
                    );

                    final int finalSavedCount = savedCount;
                    runOnUiThread(() -> {
                        if (updateTaskResult != null && updateTaskResult.code == 200) {
                            Toast.makeText(this, i18n("Saved successfully") + " " + finalSavedCount + " " + i18n("items") + " " + i18n("and updated Task status") + "!", Toast.LENGTH_LONG).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            String serverMsg = (updateTaskResult != null) ? updateTaskResult.message : i18n("No conclusion response from Server");
                            String detail = "";
                            if (updateTaskResult != null && updateTaskResult.data != null && !updateTaskResult.data.isEmpty()) {
                                try { detail = " | " + i18n("Details") + ": " + updateTaskResult.data.toString(); } catch (Exception ignored) {}
                            }
                            String toastMsg = i18n("Save details OK but update Task status error") + ": " + serverMsg + detail;
                            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                }

            } catch (Exception e) {
                Log.e("SAVE_ACTION_ERROR", "Lỗi trong quá trình thực thi save", e);
                runOnUiThread(() -> Toast.makeText(this, i18n("System error while saving") + ": " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Hàm bổ trợ xử lý bóc tách lịch sử, append bản ghi mới và đẩy lên API chi tiết
     */
    private int saveSingleItemWithHistory(MaintenanceItem item) {
        // 1. Lấy mảng lịch sử cũ từ Json hiện tại của item
        JSONArray historyArray = parseHistoryArray(item.historyJson);

        // 2. Tạo đối tượng lịch sử mới ({ time, value, value_2, updateBy }) theo đúng tài liệu quy định
        JSONObject newHistoryEntry = new JSONObject();
        try {
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            newHistoryEntry.put("time", currentTime);
            newHistoryEntry.put("value", safe(item.checkValue));
            // Nếu là dạng Ngoại quan Radio thì gửi rỗng trường value_2
            newHistoryEntry.put("value_2", item.isRadioInput() ? "" : safe(item.checkValue2));
            newHistoryEntry.put("updateBy", currentUserId);

            // 3. Đẩy (push) phần tử mới vào mảng lịch sử cũ
            historyArray.put(newHistoryEntry);
        } catch (Exception e) {
            Log.e("HISTORY_APPEND_ERR", "Không thể append lịch sử cho mục: " + item.checkId, e);
        }

        // 3. Thực hiện lệnh gọi API đồng bộ đẩy lên Server dữ liệu chuỗi Stringify
        HttpClient.APIReturn rs = HttpClient.saveMaintenanceItemDetail(
                this, serverUrl, schemaMms, taskId, item.checkId,
                safe(item.checkValue), item.isRadioInput() ? "" : safe(item.checkValue2),
            historyArray.toString(), safe(item.comment),
            buildImageListPayload(item), ""
        );

        if (rs != null) {
            Log.d("SAVE_ITEM", "Save item response code=" + rs.code + " message=" + rs.message);
        } else {
            Log.e("SAVE_ITEM", "Save item returned null response for checkId=" + item.checkId);
        }

        if (rs != null && rs.code == 200) {
            // Chụp ảnh lại giá trị làm mốc lịch sử mới, đổi trạng thái locked và reset changed cờ hiệu
            item.snapshotOriginalValues();
            item.historyJson = historyArray.toString(); // Đồng bộ lại chuỗi lịch sử cục bộ
            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
            lastSaveErrorMessage = null;
            return 1;
        }

        lastSaveErrorMessage = i18n("Error saving item") + ": " + item.checkName + " (" + item.checkId + ") - ";
        if (rs != null) lastSaveErrorMessage += rs.message + " (code=" + rs.code + ")";
        else lastSaveErrorMessage += i18n("No response from server");
        Log.e("SAVE_ITEM_ERR", lastSaveErrorMessage);
        return 0;
    }

    private boolean saveItemWithLatestRemoteHistory(MaintenanceItem item) {
        JSONArray historyArray = new JSONArray();

        // 1. Gọi đồng bộ API GET_HISTORY_CHILD lấy mảng lịch sử mới nhất đang lưu trên Server
        HttpClient.APIReturn historyRs = HttpClient.getHistoryChildItems(this, serverUrl, schemaMms, item.checkId, taskId);

        if (historyRs != null && historyRs.code == 200 && historyRs.data != null) {
            for (JSONObject row : historyRs.data) {
                // Giải bọc mảng DataSet lồng nếu Backend trả về qua hộp Table/data
                JSONArray tableArray = row.optJSONArray("Table");
                if (tableArray == null) tableArray = row.optJSONArray("data");
                if (tableArray == null) tableArray = row.optJSONArray("Data");

                if (tableArray != null) {
                    for (int i = 0; i < tableArray.length(); i++) {
                        JSONObject subRow = tableArray.optJSONObject(i);
                        if (subRow != null) historyArray.put(subRow);
                    }
                } else {
                    historyArray.put(row);
                }
            }
        }

        // 2. Tạo đối tượng lịch sử mới { time, value, value_2, updateBy } và push vào mảng
        try {
            JSONObject newHistoryEntry = new JSONObject();
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            newHistoryEntry.put("time", currentTime);
            newHistoryEntry.put("value", safe(item.checkValue));
            // Quy tắc: Nếu là dạng Radio ngoại quan thì gửi rỗng trường value_2 lên Server
            newHistoryEntry.put("value_2", item.isRadioInput() ? "" : safe(item.checkValue2));
            newHistoryEntry.put("updateBy", currentUserId);

            historyArray.put(newHistoryEntry);
        } catch (Exception e) {
            Log.e("HISTORY_APPEND_ERR", "Không thể append lịch sử thời gian thực cho mục: " + item.checkId, e);
        }

        // 3. Thực hiện đẩy gói Condition lên hàm lưu chi tiết phần tử
        HttpClient.APIReturn saveRs = HttpClient.saveMaintenanceItemDetail(
                this, serverUrl, schemaMms, taskId, item.checkId,
                safe(item.checkValue), item.isRadioInput() ? "" : safe(item.checkValue2),
            historyArray.toString(), safe(item.comment),
            buildImageListPayload(item), ""
        );

        if (saveRs != null && saveRs.code == 200) {
            // Chốt hạ Snapshot cục bộ thiết lập lại mốc gốc, khóa dữ liệu nếu đạt trạng thái OK
            item.snapshotOriginalValues();
            item.historyJson = historyArray.toString(); // Đồng bộ lại chuỗi lịch sử cục bộ mới
            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
            return true;
        }
        return false;
    }

    private int saveItemIfNeeded(MaintenanceItem item) {
        if (item == null) return 0;
        item.refreshChangedState();
        if (!item.changed) return 0;
        JSONArray historyArray = buildHistoryArray(item);
        HttpClient.APIReturn rs = HttpClient.saveMaintenanceItemDetail(this, serverUrl, schemaMms, taskId, item.checkId, safe(item.checkValue), item.isRadioInput() ? "" : safe(item.checkValue2), historyArray.toString(), safe(item.comment), buildImageListPayload(item), "");
        if (rs != null && rs.code == 200) {
            item.snapshotOriginalValues();
            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
            lastSaveErrorMessage = null;
            return 1;
        }
        lastSaveErrorMessage = i18n("Error saving item") + ": " + item.checkName + " (" + item.checkId + ") - ";
        if (rs != null) lastSaveErrorMessage += rs.message + " (code=" + rs.code + ")";
        else lastSaveErrorMessage += i18n("No response from server");
        Log.e("SAVE_ITEM_ERR", lastSaveErrorMessage);
        return 0;
    }

    private JSONArray buildHistoryArray(MaintenanceItem item) {
        JSONArray historyArray = parseHistoryArray(item.historyJson);
        JSONObject entry = new JSONObject();
        try {
            entry.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            entry.put("value", safe(item.checkValue));
            entry.put("value_2", safe(item.checkValue2));
            entry.put("updateBy", currentUserId);
        } catch (Exception ignored) {}
        historyArray.put(entry);
        return historyArray;
    }

    private JSONArray parseHistoryArray(String json) {
        try { return (json == null || json.isEmpty() || "null".equals(json)) ? new JSONArray() : new JSONArray(json); }
        catch (Exception e) { return new JSONArray(); }
    }

    private String resolveOverallStatus() {
        boolean anyNg = false;
        boolean allOk = true;

        for (MaintenanceItem parent : parentItems) {
            // Tìm thấy bất kỳ một mục Cha nào NG -> Trả kết quả toàn Task là NG (mã 3)
            if ("NG".equalsIgnoreCase(parent.initialStatus)) { anyNg = true; break; }
            // Tồn tại mục chưa hoàn thành nhập liệu
            if (!"OK".equalsIgnoreCase(parent.initialStatus)) allOk = false;
        }
        if (anyNg) return "3";// Mã trạng thái: Checksheet NG
        if (allOk && !parentItems.isEmpty()) return "2";// Mã trạng thái: Checksheet OK
        return "";// Trạng thái mặc định nếu chưa làm xong hoặc rơi vào trạng thái chờ trung lập
    }

    private MaintenanceItem parseItem(JSONObject row, String parentId) {
        MaintenanceItem item = new MaintenanceItem();
        item.checkId = pickFirst(row.optString("Check_Id"), row.optString("checkId"));
        item.parentCheckId = pickFirst(parentId, row.optString("Parent_Check_Id"));
        item.checkName = pickFirst(row.optString("Check_Name"), row.optString("Check_Content"));
        item.min = pickFirst(row.optString("Check_Content_Min"));
        item.max = pickFirst(row.optString("Check_Content_Max"));
        item.checkValue = pickFirst(row.optString("Check_Value"), row.optString("Value"));
        item.checkValue2 = pickFirst(row.optString("Check_Value_2"), row.optString("Value_2"));
        item.childCount = row.optInt("Child_Count", 0);
        item.comment = row.optString("Comment", "");
        item.historyJson = row.optString("History", "");
        item.setImagePaths(parseImagePathsFromRow(row));
        Log.d("WORKORDER_IMAGE_DBG", "parseItem checkId=" + item.checkId + ", imageCount=" + item.getImagePathsSnapshot().size() + ", imagePaths=" + item.getImagePathsSnapshot());

        item.initialStatus = resolveInitialStatus(item);
        if (item.childCount > 0) {
            item.locked = true;
        } else {
            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
        }

        item.subDesc = item.isNumericInput()
            ? i18n("Minimum") + ": " + safe(item.min) + " | " + i18n("Maximum") + ": " + safe(item.max)
            : (item.childCount > 0 ? i18n("Has") + " " + item.childCount + " " + i18n("child items") : i18n("Visual inspection"));
        item.snapshotOriginalValues();
        return item;
    }

    private String resolveInitialStatus(MaintenanceItem item) {
        if (item.isNumericInput()) {
            if (item.checkValue.isEmpty() || item.checkValue2.isEmpty()) return "";
            try {
                double  v1 = Double.parseDouble(item.checkValue),
                        v2 = Double.parseDouble(item.checkValue2),
                        min = parseD(item.min, Double.NEGATIVE_INFINITY),
                        max = parseD(item.max, Double.POSITIVE_INFINITY);
                return (v1 >= min && v1 <= max && v2 >= min && v2 <= max) ? "OK" : "NG";
            } catch (Exception e) { return "NG"; }
        }
        return item.checkValue;
    }

    private void loadHistoryForItem(MaintenanceItem item) {
        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getHistoryChildItems(this, serverUrl, schemaMms, item.checkId, taskId);
            runOnUiThread(() -> { if (rs.code == 200 && rs.data != null) new AlertDialog.Builder(this).setTitle(i18n("Lịch sử") + ": " + item.checkName).setMessage(rs.data.toString()).show(); });
        });
    }

    private String resolveStatusLabel(String status) {
        if ("2".equals(status)) return i18n("Checksheet OK");
        if ("3".equals(status)) return i18n("Checksheet NG");
        return i18n("Pending");
    }

    private String pickFirst(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty() && !"null".equals(s)) return s.trim();
        return "";
    }
    private String safe(String s) { return s == null ? "" : s.trim(); }
    private double parseD(String s, double f) { try { return Double.parseDouble(s); } catch (Exception e) { return f; } }
    private String formatUnix(long u) { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(u * 1000L)); }
    private String cleanNull(String s) {
        if (s == null || "null".equalsIgnoreCase(s.trim())) return "";
        return s.trim();
    }

    private void mergeUploadedImages(MaintenanceItem item, List<String> uploadedImagePaths) {
        if (item == null || uploadedImagePaths == null || uploadedImagePaths.isEmpty()) return;
        Set<String> merged = new LinkedHashSet<>(item.getImagePathsSnapshot());
        for (String path : uploadedImagePaths) {
            if (path != null && !path.trim().isEmpty()) {
                merged.add(path.trim());
            }
        }
        item.setImagePaths(new ArrayList<>(merged));
        Log.d("WORKORDER_IMAGE_DBG", "mergeUploadedImages checkId=" + item.checkId + ", imageCount=" + item.getImagePathsSnapshot().size() + ", imagePaths=" + item.getImagePathsSnapshot());
    }

    private String buildImageListPayload(MaintenanceItem item) {
        if (item == null) return "";
        List<String> imagePaths = item.getImagePathsSnapshot();
        if (imagePaths.isEmpty()) return "";

        List<String> fileNames = new ArrayList<>();
        for (String path : imagePaths) {
            String cleaned = cleanNull(path);
            if (cleaned.isEmpty()) continue;
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                int slashIndex = cleaned.lastIndexOf('/');
                fileNames.add(slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned);
            } else {
                fileNames.add(cleaned);
            }
        }
        return String.join(",", fileNames);
    }

    private List<String> parseImagePathsFromRow(JSONObject row) {
        List<String> rawValues = new ArrayList<>();
        rawValues.add(row.optString("Image_List"));
        rawValues.add(row.optString("Image_List_1"));
        rawValues.add(row.optString("Image_List_2"));
        rawValues.add(row.optString("Image_List_3"));
        rawValues.add(row.optString("image_list"));
        rawValues.add(row.optString("imageList"));
        rawValues.add(row.optString("ImageList"));
        rawValues.add(row.optString("Images"));
        rawValues.add(row.optString("images"));
        rawValues.add(row.optString("Images_1"));
        rawValues.add(row.optString("Images_2"));
        rawValues.add(row.optString("Images_3"));
        rawValues.add(row.optString("Image"));
        rawValues.add(row.optString("image"));
        rawValues.add(row.optString("Image_Path"));
        rawValues.add(row.optString("ImagePath"));
        rawValues.add(row.optString("imagePath"));
        rawValues.add(row.optString("ImagePaths"));
        rawValues.add(row.optString("imagePaths"));
        rawValues.add(row.optString("Photos"));
        rawValues.add(row.optString("photos"));

        List<String> normalized = new ArrayList<>();
        for (String rawValue : rawValues) {
            normalized.addAll(expandImageReferences(rawValue));
        }
        return normalized;
    }

    private List<String> expandImageReferences(String rawValue) {
        List<String> result = new ArrayList<>();
        String value = cleanNull(rawValue);
        if (value.isEmpty()) return result;

        if (value.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(value);
                for (int i = 0; i < array.length(); i++) {
                    String entry = cleanNull(array.optString(i));
                    if (!entry.isEmpty()) {
                        result.add(normalizeImageReference(entry));
                    }
                }
                return result;
            } catch (Exception ignored) {
                // Fall through to delimiter-based parsing.
            }
        }

        String[] tokens = value.split("[;,|\\n]");
        for (String token : tokens) {
            String entry = cleanNull(token);
            if (!entry.isEmpty()) {
                result.add(normalizeImageReference(entry));
            }
        }
        return result;
    }

    private String normalizeImageReference(String reference) {
        String cleaned = cleanNull(reference);
        if (cleaned.isEmpty()) return "";
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            if (isMalformedMaintenanceImageUrl(cleaned)) {
                String fileName = extractImageFileName(cleaned);
                return fileName.isEmpty() ? cleaned : buildMaintenanceImageUrl(serverUrl, fileName);
            }
            return cleaned;
        }
        if (cleaned.startsWith("content:") || cleaned.startsWith("file:") || cleaned.startsWith("android.resource:")) {
            return cleaned;
        }
        String fileName = extractImageFileName(cleaned);
        return fileName.isEmpty() ? "" : buildMaintenanceImageUrl(serverUrl, fileName);
    }

    private boolean isMalformedMaintenanceImageUrl(String value) {
        return value.contains("/mms_file-img/") && value.matches(".*[A-Za-z]:/.*");
    }

    private String extractImageFileName(String value) {
        String cleaned = cleanNull(value);
        if (cleaned.isEmpty()) return "";

        int queryIndex = cleaned.indexOf('?');
        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }

        int slashIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < cleaned.length() - 1) {
            return cleaned.substring(slashIndex + 1).trim();
        }
        return cleaned.trim();
    }

    private String resolveUploadedImageReference(HttpClient.APIReturn rs, Uri fallbackUri) {
        if (rs != null && rs.data != null) {
            for (JSONObject row : rs.data) {
                String candidate = cleanNull(pickFirst(
                        row.optString("value"),
                        row.optString("Value"),
                        row.optString("fileName"),
                        row.optString("FileName"),
                        row.optString("filename"),
                        row.optString("file"),
                        row.optString("File"),
                        row.optString("image"),
                        row.optString("Image"),
                        row.optString("path"),
                        row.optString("Path"),
                        row.optString("url"),
                        row.optString("Url")
                ));
                if (!candidate.isEmpty()) {
                    return normalizeImageReference(candidate);
                }
            }
        }

        String fallback = fallbackUri == null ? "" : fallbackUri.toString();
        if (fallback.startsWith("http://") || fallback.startsWith("https://") || fallback.startsWith("content:") || fallback.startsWith("file:")) {
            return fallback;
        }
        return "";
    }

    private String i18n(String key) {
        return LanguageAPIUtils.i18n(key);
    }

}
