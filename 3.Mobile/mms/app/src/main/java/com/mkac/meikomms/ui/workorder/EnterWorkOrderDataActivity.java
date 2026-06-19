package com.mkac.meikomms.ui.workorder;

import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mkac.meikomms.R;
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
import java.util.TimeZone;
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
    private String isWarehouseRequestFromIntent = "";

    private MaintenanceItem pendingUploadItem;
    private String lastSaveErrorMessage = null;
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty() && pendingUploadItem != null) {
                    uploadMultipleImagesForItem(pendingUploadItem, uris);
                }
            }
    );

    private void uploadMultipleImagesForItem(MaintenanceItem item, List<Uri> uris) {
        if (item == null || uris == null || uris.isEmpty()) return;

        Toast.makeText(this, i18n("Uploading " + uris.size() + " images..."), Toast.LENGTH_SHORT).show();

        // Display local images immediately in the preview list
        runOnUiThread(() -> {
            item.ensureImagePaths();
            for (Uri uri : uris) {
                item.addImagePath(uri.toString());
            }
            item.refreshChangedState();
            if (currentChildAdapter != null) currentChildAdapter.notifyDataSetChanged();
            if (parentAdapter != null) parentAdapter.notifyDataSetChanged();
        });

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.uploadMultiplePreventiveImages(this, serverUrl, taskId, item.checkId, uris);

            if (rs != null && rs.code == 200 && rs.data != null) {
                List<String> uploadedReferences = new ArrayList<>();

                try {
                    for (JSONObject obj : rs.data) {
                        String rawJsonStr = obj.optString("value");
                        if (rawJsonStr.isEmpty()) {
                            rawJsonStr = obj.toString();
                        }

                        if (rawJsonStr.contains("[")) {
                            int start = rawJsonStr.indexOf("[");
                            int end = rawJsonStr.lastIndexOf("]") + 1;
                            String finalJsonArray = rawJsonStr.substring(start, end);

                            JSONArray arr = new JSONArray(finalJsonArray);
                            for (int i = 0; i < arr.length(); i++) {
                                String rawPath = arr.optString(i);
                                if (!rawPath.isEmpty()) {
                                    uploadedReferences.add(normalizeImageReference(rawPath));
                                }
                            }
                        } else if (!rawJsonStr.isEmpty() && !rawJsonStr.startsWith("{")) {
                            uploadedReferences.add(normalizeImageReference(rawJsonStr));
                        }
                    }
                } catch (Exception e) {
                    Log.e("MAINTENANCE_UPLOAD_PARSE_ERR", "Lỗi phân rã chuỗi JSON Array trả về từ Server", e);
                }

                if (!uploadedReferences.isEmpty()) {
                    runOnUiThread(() -> {
                        // Gỡ bỏ triệt để tất cả các URI xem trước nội bộ (content:// hoặc file://) và đồng bộ lại primary imagePath
                        if (item.imagePaths != null) {
                            List<String> cleanPaths = new ArrayList<>();
                            for (String path : item.imagePaths) {
                                if (path != null && !path.startsWith("content:") && !path.startsWith("file:")) {
                                    cleanPaths.add(path);
                                }
                            }
                            item.setImagePaths(cleanPaths);
                        }
                        // Merge the verified server URLs
                        mergeUploadedImages(item, uploadedReferences);

                        if (currentChildAdapter != null) currentChildAdapter.notifyDataSetChanged();
                        if (parentAdapter != null) parentAdapter.notifyDataSetChanged();
                        Toast.makeText(this, i18n("Uploaded successfully " + uploadedReferences.size() + " images for: ") + item.checkName, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
            }

            // Rollback local Uris if upload failed
            runOnUiThread(() -> {
                // Gỡ bỏ triệt để tất cả các URI xem trước nội bộ (content:// hoặc file://) và đồng bộ lại primary imagePath
                if (item.imagePaths != null) {
                    List<String> cleanPaths = new ArrayList<>();
                    for (String path : item.imagePaths) {
                        if (path != null && !path.startsWith("content:") && !path.startsWith("file:")) {
                            cleanPaths.add(path);
                        }
                    }
                    item.setImagePaths(cleanPaths);
                }
                item.refreshChangedState();
                if (currentChildAdapter != null) currentChildAdapter.notifyDataSetChanged();
                if (parentAdapter != null) parentAdapter.notifyDataSetChanged();
            });

            String errMsg = (rs != null) ? rs.message : i18n("No response from server");
            runOnUiThread(() -> Toast.makeText(this, i18n("Images upload error") + ": " + errMsg, Toast.LENGTH_LONG).show());
        });
    }

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
        if (binding.btnCreateWmsRequest != null) {
            binding.btnCreateWmsRequest.setText(i18n("Tạo yêu cầu xuất kho"));
            PreferenceHandler prefHandler = new PreferenceHandler(this);
            boolean isAlreadyWmsCreated = prefHandler.getBoolean("wms_request_created_" + taskId)
                    || "1".equals(isWarehouseRequestFromIntent);
            if (isAlreadyWmsCreated) {
                binding.btnCreateWmsRequest.setEnabled(false);
                binding.btnCreateWmsRequest.setClickable(false);
                binding.btnCreateWmsRequest.setAlpha(0.35f);
            }
            binding.btnCreateWmsRequest.setOnClickListener(v -> createWmsRequestFromActivity());
        }
    }

    private void loadConfiguration() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"));
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), handler.getString("schema_data"), configManager.getProperty("schema_data"));

        JSONObject userObj = handler.getJsonObject("user");
        if (userObj != null) {
            currentUserId = userObj.optString("userId", "");
            if (currentUserId.isEmpty()) currentUserId = userObj.optString("Id", "");
            if (currentUserId.isEmpty()) currentUserId = userObj.optString("username", "");
        }
        if (currentUserId.isEmpty()) {
            currentUserId = handler.getString("Userlogin");
        }
    }

    private void parseIntentData() {
        taskId = safe(getIntent().getStringExtra("TASK_ID"));
        machineId = safe(getIntent().getStringExtra("MACHINE_ID"));
        machineName = safe(getIntent().getStringExtra("MACHINE_NAME"));
        categoryId = safe(getIntent().getStringExtra("CATEGORY_ID"));
        categoryName = safe(getIntent().getStringExtra("CATEGORY_NAME"));
        taskDateUnix = getIntent().getLongExtra("TASK_DATE_UNIX", 0L);
        taskStatus = safe(getIntent().getStringExtra("STATUS"));
        isWarehouseRequestFromIntent = safe(getIntent().getStringExtra("IS_WAREHOUSE_REQUEST"));

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
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#F3F4F6"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#4B5563"));
                break;
            case "1":
                binding.tvPlanStatus.setText(i18n("Done") + " / " + i18n("Approve"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#EFF6FF"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#2563EB"));
                break;
            case "2":
                binding.tvPlanStatus.setText(i18n("Checksheet OK"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#D1FAE5"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#047857"));
                break;
            case "3":
                binding.tvPlanStatus.setText(i18n("Checksheet NG"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#FEE2E2"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#B91C1C"));
                break;
            case "5":
                binding.tvPlanStatus.setText(i18n("Overdue"));
                binding.tvPlanStatus.setBackgroundColor(Color.parseColor("#FFEEEE"));
                binding.tvPlanStatus.setTextColor(Color.parseColor("#E11D48"));
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

        if (dialogBinding.btnConfirmChildData != null) {
            dialogBinding.btnConfirmChildData.setText(i18n("CONFIRM CHILD ITEMS"));
        }

        dialogBinding.tvDialogParentTitle.setText(parentItem.checkName);
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

        if (childItemsByParent.containsKey(parentItem.checkId) && childItemsByParent.get(parentItem.checkId) != null && !childItemsByParent.get(parentItem.checkId).isEmpty()) {
            childItems.clear();
            childItems.addAll(childItemsByParent.get(parentItem.checkId));
            childAdapter.notifyDataSetChanged();
        } else {
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
        }

        bottomSheetDialog.setOnDismissListener(dialog -> currentChildAdapter = null);

        dialogBinding.btnConfirmChildData.setOnClickListener(v -> {
            syncParentFromChildren(parentItem, childItems);
            parentAdapter.notifyDataSetChanged();
            bottomSheetDialog.dismiss();
        });
        dialogBinding.btnCloseDialog.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
        android.widget.FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            android.view.ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
            lp.height = (int) (metrics.heightPixels * 1.1); // Set to 100% of screen height
            bottomSheet.setLayoutParams(lp);
            com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setPeekHeight(lp.height);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void syncParentFromChildren(MaintenanceItem parentItem, List<MaintenanceItem> childItems) {
        if (childItems == null || childItems.isEmpty()) return;

        boolean allOk = true;
        boolean anyNg = false;
        boolean anyEmpty = false;

        for (MaintenanceItem child : childItems) {
            String childStatus = resolveInitialStatus(child);

            if ("NG".equalsIgnoreCase(childStatus)) {
                anyNg = true;
                break;
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

                    if (children != null && !children.isEmpty()) {
                        syncParentFromChildren(parentItem, children);
                    }

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

                String overallStatus = resolveOverallStatus();

                if (overallStatus.isEmpty()) {
                    final int finalSavedCount = savedCount;
                    runOnUiThread(() -> {
                        Toast.makeText(this, i18n("Temporarily saved successfully") + " " + finalSavedCount + " " + i18n("detailed items") + "!", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } else {
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

    private void createWmsRequestFromActivity() {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage(i18n("Creating warehouse release request..."));
        progressDialog.setCancelable(false);
        progressDialog.show();

        executorService.execute(() -> {
            PreferenceHandler prefHandler = new PreferenceHandler(this);
            String username = prefHandler.getString("Userlogin");
            JSONObject userProfile = prefHandler.getJsonObject("user");
            if (username.isEmpty() && userProfile != null) {
                username = userProfile.optString("username", userProfile.optString("User_Name", ""));
            }
            if (username.isEmpty()) {
                username = currentUserId;
            }

            List<MaintenanceItem> allChildItems = new ArrayList<>();
            boolean fetchSuccess = true;
            String errorMsg = null;

            for (MaintenanceItem parent : parentItems) {
                if (parent.childCount > 0) {
                    List<MaintenanceItem> children = childItemsByParent.get(parent.checkId);
                    if (children == null) {
                        HttpClient.APIReturn rs = HttpClient.getChildMaintenanceItems(this, serverUrl, schemaMms, parent.checkId, taskId);
                        if (rs != null && rs.code == 200 && rs.data != null) {
                            children = new ArrayList<>();
                            for (JSONObject row : rs.data) {
                                children.add(parseItem(row, parent.checkId));
                            }
                            childItemsByParent.put(parent.checkId, children);
                        } else {
                            fetchSuccess = false;
                            errorMsg = (rs != null) ? rs.message : "Error fetching children for " + parent.checkName;
                            break;
                        }
                    }
                    if (children != null) {
                        allChildItems.addAll(children);
                    }
                }
            }

            if (!fetchSuccess) {
                final String finalErr = errorMsg;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, i18n("Error fetching child materials") + ": " + finalErr, Toast.LENGTH_LONG).show();
                });
                return;
            }

            if (allChildItems.isEmpty()) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, i18n("No materials available to create warehouse request"), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // Group by Item_Id and sum the quantities
            java.util.Map<String, Integer> materialQuantities = new java.util.HashMap<>();
            for (MaintenanceItem child : allChildItems) {
                String matId = child.checkId;
                if (matId == null || matId.isEmpty()) continue;
                int currentQty = materialQuantities.containsKey(matId) ? materialQuantities.get(matId) : 0;
                materialQuantities.put(matId, currentQty + 1);
            }

            List<JSONObject> addMaterialsList = new ArrayList<>();
            try {
                for (java.util.Map.Entry<String, Integer> entry : materialQuantities.entrySet()) {
                    JSONObject matObj = new JSONObject();
                    matObj.put("Item_Id", entry.getKey());
                    matObj.put("Item_Qty", entry.getValue());
                    matObj.put("Machine_Id", machineId);
                    matObj.put("Purpose", "Maintain");
                    matObj.put("User_Export", username);
                    addMaterialsList.add(matObj);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, i18n("Error parsing material list") + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            String displayMachine = machineName.isEmpty() ? machineId : machineId + "-" + machineName;
            String requestNote = "Yêu cầu xuất kho cho bảo dưỡng máy " + displayMachine;
            String requestDateUnixStr = String.valueOf(System.currentTimeMillis() / 1000L);
            String generatedRequestId = "REQ_001_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + "_" + (int)(Math.random() * 100);

            HttpClient.APIReturn result = HttpClient.createWmsExportRequest(
                    this,
                    requestDateUnixStr,
                    "FE",
                    requestNote,
                    addMaterialsList,
                    serverUrl,
                    username,
                    "Maintain",
                    machineId,
                    generatedRequestId
            );

            if (result != null && result.code == 200) {
                try {
                    JSONObject updateCondition = new JSONObject();
                    updateCondition.put("Schema_Mms", schemaMms);
                    updateCondition.put("taskId", taskId);

                    HttpClient.callDynamics(
                            this, serverUrl, "mes_mms", "UPDATE_TASK_WAREHOUSE_REQUEST", updateCondition
                    );
                } catch (Exception e) {
                    Log.e("WMS_UPDATE", "Error updating maintenance task warehouse request", e);
                }
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (result != null && result.code == 200) {
                    Toast.makeText(this, i18n("Warehouse release request created successfully!"), Toast.LENGTH_SHORT).show();
                    PreferenceHandler prefHandler2 = new PreferenceHandler(this);
                    prefHandler2.setBoolean("wms_request_created_" + taskId, true);
                    if (binding.btnCreateWmsRequest != null) {
                        binding.btnCreateWmsRequest.setEnabled(false);
                        binding.btnCreateWmsRequest.setClickable(false);
                        binding.btnCreateWmsRequest.setAlpha(0.35f);
                    }
                } else {
                    String errMsg = (result != null) ? result.message : i18n("No response from server");
                    Toast.makeText(this, i18n("Error creating warehouse release request") + ": " + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private int saveSingleItemWithHistory(MaintenanceItem item) {
        JSONArray historyArray = parseHistoryArray(item.historyJson);

        JSONObject newHistoryEntry = new JSONObject();
        try {
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            newHistoryEntry.put("time", currentTime);
            newHistoryEntry.put("value", safe(item.checkValue));
            newHistoryEntry.put("value_2", item.isRadioInput() ? "" : safe(item.checkValue2));
            newHistoryEntry.put("updateBy", currentUserId);

            historyArray.put(newHistoryEntry);
        } catch (Exception e) {
            Log.e("HISTORY_APPEND_ERR", "Không thể append lịch sử cho mục: " + item.checkId, e);
        }

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
            item.snapshotOriginalValues();
            item.historyJson = historyArray.toString();
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

        HttpClient.APIReturn historyRs = HttpClient.getHistoryChildItems(this, serverUrl, schemaMms, item.checkId, taskId);

        if (historyRs != null && historyRs.code == 200 && historyRs.data != null) {
            for (JSONObject row : historyRs.data) {
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

        try {
            JSONObject newHistoryEntry = new JSONObject();
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            newHistoryEntry.put("time", currentTime);
            newHistoryEntry.put("value", safe(item.checkValue));
            newHistoryEntry.put("value_2", item.isRadioInput() ? "" : safe(item.checkValue2));
            newHistoryEntry.put("updateBy", currentUserId);

            historyArray.put(newHistoryEntry);
        } catch (Exception e) {
            Log.e("HISTORY_APPEND_ERR", "Không thể append lịch sử thời gian thực cho mục: " + item.checkId, e);
        }

        HttpClient.APIReturn saveRs = HttpClient.saveMaintenanceItemDetail(
                this, serverUrl, schemaMms, taskId, item.checkId,
                safe(item.checkValue), item.isRadioInput() ? "" : safe(item.checkValue2),
                historyArray.toString(), safe(item.comment),
                buildImageListPayload(item), ""
        );

        if (saveRs != null && saveRs.code == 200) {
            item.snapshotOriginalValues();
            item.historyJson = historyArray.toString();
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
            if ("NG".equalsIgnoreCase(parent.initialStatus)) { anyNg = true; break; }
            if (!"OK".equalsIgnoreCase(parent.initialStatus)) allOk = false;
        }
        if (anyNg) return "3";
        if (allOk && !parentItems.isEmpty()) return "2";
        return "";
    }

//    private MaintenanceItem parseItem(JSONObject row, String parentId) {
//        MaintenanceItem item = new MaintenanceItem();
//
//        String parsedId = "";
//        if (parentId != null && !parentId.isEmpty()) {
//            // ĐANG PHÂN GIẢI HẠNG MỤC CON (DETAIL TABLE):
//            String checkId1 = row.optString("Check_Id_1");
//            String checkIdReal = row.optString("Check_Id");
//            String checkIdCamel = row.optString("checkId");
//            String childCheckId = row.optString("Child_Check_Id");
//
//            // Nếu Check_Id bị trùng khít với ID cha, ép hệ thống cào từ Check_Id_1 để ra mã con chuẩn
//            if (checkIdReal.equalsIgnoreCase(parentId)) {
//                parsedId = pickFirst(checkId1, childCheckId, row.optString("childCheckId"), checkIdCamel);
//            } else {
//                parsedId = pickFirst(checkIdReal, checkId1, checkIdCamel, childCheckId);
//            }
//
//            if (parsedId.isEmpty() || parsedId.equalsIgnoreCase(parentId)) {
//                parsedId = pickFirst(checkIdReal, checkId1, checkIdCamel);
//            }
//        } else {
//            parsedId = pickFirst(row.optString("Check_Id"), row.optString("checkId"));
//        }
//
//        item.checkId = parsedId;
//        item.parentCheckId = pickFirst(parentId, row.optString("Parent_Check_Id"), row.optString("Parent"));
//        item.checkName = pickFirst(row.optString("Check_Name"), row.optString("Check_Content"));
//        item.min = pickFirst(row.optString("Check_Content_Min"), row.optString("Check_Content_Min"));
//        item.max = pickFirst(row.optString("Check_Content_Max"), row.optString("Check_Content_Max"));
//        item.checkValue = pickFirst(row.optString("Check_Value"), row.optString("Value"));
//        item.checkValue2 = pickFirst(row.optString("Check_Value_2"), row.optString("Value_2"));
//        item.childCount = row.optInt("Child_Count", 0);
//        item.comment = row.optString("Comment", "");
//        item.historyJson = row.optString("History", "");
//        item.setImagePaths(parseImagePathsFromRow(row));
//
//        item.method = pickFirst(row.optString("Method"), row.optString("method"));
//        item.testContent = pickFirst(row.optString("Test_Content"), row.optString("testContent"), row.optString("Visual_Standard"));
//        item.unit = pickFirst(row.optString("Unit"), row.optString("unit"));
//
//        StringBuilder sb = new StringBuilder();
//        if (!item.method.isEmpty()) {
//            sb.append(i18n("Method")).append(": ").append(item.method).append("\n");
//        }
//
//        if (item.isNumericInput()) {
//            sb.append(i18n("Minimum")).append(": ").append(item.min);
//            if (!item.unit.isEmpty()) sb.append(" ").append(item.unit);
//            sb.append(" | ").append(i18n("Maximum")).append(": ").append(item.max);
//            if (!item.unit.isEmpty()) sb.append(" ").append(item.unit);
//        } else {
//            if (!item.testContent.isEmpty()) {
//                sb.append(i18n("Visual Standard")).append(": ").append(item.testContent);
//            } else {
//                sb.append(i18n("Visual inspection"));
//            }
//        }
//        item.subDesc = sb.toString().trim();
//
//        item.initialStatus = resolveInitialStatus(item);
//        if (item.childCount > 0) {
//            item.locked = true;
//        } else {
//            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
//        }
//
//        item.snapshotOriginalValues();
//        return item;
//    }

    private MaintenanceItem parseItem(JSONObject row, String parentId) {
        MaintenanceItem item = new MaintenanceItem();

        String parsedId = "";
        if (parentId != null && !parentId.isEmpty()) {
            String checkId1 = row.optString("Check_Id_1");
            String checkIdReal = row.optString("Check_Id");
            String checkIdCamel = row.optString("checkId");
            String childCheckId = row.optString("Child_Check_Id");

            if (checkIdReal.equalsIgnoreCase(parentId)) {
                parsedId = pickFirst(checkId1, childCheckId, row.optString("childCheckId"), checkIdCamel);
            } else {
                parsedId = pickFirst(checkIdReal, checkId1, checkIdCamel, childCheckId);
            }

            if (parsedId.isEmpty() || parsedId.equalsIgnoreCase(parentId)) {
                parsedId = pickFirst(checkIdReal, checkId1, checkIdCamel);
            }
        } else {
            parsedId = pickFirst(row.optString("Check_Id"), row.optString("checkId"));
        }

        item.checkId = parsedId;
        item.parentCheckId = pickFirst(parentId, row.optString("Parent_Check_Id"), row.optString("Parent"));
        item.checkName = pickFirst(row.optString("Check_Name"), row.optString("Check_Content"));
        item.min = pickFirst(row.optString("Check_Content_Min"), row.optString("Check_Content_Min"));
        item.max = pickFirst(row.optString("Check_Content_Max"), row.optString("Check_Content_Max"));
        item.checkValue = pickFirst(row.optString("Check_Value"), row.optString("Value"));
        item.checkValue2 = pickFirst(row.optString("Check_Value_2"), row.optString("Value_2"));
        item.childCount = row.optInt("Child_Count", 0);
        item.comment = pickFirst(
                row.optString("Comment"),
                row.optString("comment"),
                row.optString("COMMENT"),
                row.optString("Remark"),
                row.optString("remark"),
                row.optString("REMARK"),
                row.optString("NOTE"),
                row.optString("Note"),
                row.optString("note")
        );
        item.historyJson = row.optString("History", "");
        item.setImagePaths(parseImagePathsFromRow(row));

        // CHỈ NẠP DỮ LIỆU SẠCH - KHÔNG ĐÚNG NGHĨA TẠO CHUỖI GIAO DIỆN TẠI ĐÂY
        item.method = pickFirst(row.optString("Method"), row.optString("method"));
        item.testContent = pickFirst(row.optString("Test_Content"), row.optString("testContent"), row.optString("Visual_Standard"));
        item.unit = pickFirst(row.optString("Unit"), row.optString("unit"));
        item.subDesc = ""; // Bỏ trống chuỗi tĩnh cũ
        item.isWarehouseRequest = pickFirst(row.optString("Is_Warehouse_Request"), row.optString("isWarehouseRequest"), row.optString("IS_WAREHOUSE_REQUEST"));

        item.initialStatus = resolveInitialStatus(item);
        if (item.childCount > 0) {
            item.locked = true;
        } else {
            item.locked = "OK".equalsIgnoreCase(item.initialStatus);
        }

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
        if (item == null) return;

        Toast.makeText(this, i18n("Downloading history..."), Toast.LENGTH_SHORT).show();

        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        // Trích xuất trực tiếp server_dynamic_url cấu hình nhà máy từ file config
        String activeDynamicUrl = handler.getString("server_dynamic_url");
        if (activeDynamicUrl == null || activeDynamicUrl.isEmpty()) {
            activeDynamicUrl = configManager.getProperty("server_dynamic_url");
        }
        if (activeDynamicUrl == null || activeDynamicUrl.isEmpty()) {
            if (serverUrl != null && !serverUrl.isEmpty()) {
                activeDynamicUrl = serverUrl.endsWith("/") ? serverUrl + "api/dynamics" : serverUrl + "/api/dynamics";
            } else {
                activeDynamicUrl = "http://192.86.0.225:9101/api/dynamics";
            }
        }
        final String finalServerDynamicUrl = activeDynamicUrl;

        executorService.execute(() -> {
            HttpClient.APIReturn rs = HttpClient.getHistoryChildItems(this, finalServerDynamicUrl, schemaMms, item.checkId, taskId);

            runOnUiThread(() -> {
                if (rs == null || rs.code != 200 || rs.data == null) {
                    Toast.makeText(this, i18n("No response from server"), Toast.LENGTH_SHORT).show();
                    return;
                }

                List<JSONObject> historyRows = new ArrayList<>();

                for (JSONObject row : rs.data) {
                    String nestedHistoryStr = row.has("History") ? row.optString("History") : row.optString("HISTORY");

                    if (nestedHistoryStr != null && !nestedHistoryStr.trim().isEmpty() && !"null".equalsIgnoreCase(nestedHistoryStr.trim())) {
                        try {
                            JSONArray historyArr = new JSONArray(nestedHistoryStr.trim());
                            for (int i = 0; i < historyArr.length(); i++) {
                                JSONObject logRow = historyArr.optJSONObject(i);
                                if (logRow != null) historyRows.add(logRow);
                            }
                        } catch (Exception e) {
                            Log.e("HISTORY_NESTED_ERR", "Lỗi bóc tách chuỗi History", e);
                        }
                    } else {
                        JSONArray tableArray = row.optJSONArray("Table");
                        if (tableArray == null) tableArray = row.optJSONArray("data");
                        if (tableArray == null) tableArray = row.optJSONArray("Data");

                        if (tableArray != null) {
                            for (int i = 0; i < tableArray.length(); i++) {
                                JSONObject subRow = tableArray.optJSONObject(i);
                                if (subRow != null) historyRows.add(subRow);
                            }
                        } else {
                            if (row.has("time") || row.has("Time") || row.has("TIME") || row.has("Update_Date")) {
                                historyRows.add(row);
                            }
                        }
                    }
                }

                if (historyRows.isEmpty() && item.historyJson != null && !item.historyJson.trim().isEmpty() && !"null".equalsIgnoreCase(item.historyJson.trim())) {
                    try {
                        JSONArray localArray = new JSONArray(item.historyJson.trim());
                        for (int i = 0; i < localArray.length(); i++) {
                            JSONObject localRow = localArray.optJSONObject(i);
                            if (localRow != null) historyRows.add(localRow);
                        }
                    } catch (Exception e) {
                        Log.e("HISTORY_CACHE_ERR", "Lỗi đọc dữ liệu lịch sử từ bộ nhớ tạm", e);
                    }
                }

                if (historyRows.isEmpty()) {
                    Toast.makeText(this, i18n("No maintenance history recorded"), Toast.LENGTH_SHORT).show();
                    return;
                }

                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_maintenance_history, null);

                TextView tvHeaderTime = dialogView.findViewById(R.id.tv_header_time);
                TextView tvHeaderEditor = dialogView.findViewById(R.id.tv_header_editor);
                TextView tvHeaderResult = dialogView.findViewById(R.id.tv_header_result);
                Button btnHistoryClose = dialogView.findViewById(R.id.btn_history_close);
                if (tvHeaderTime != null) tvHeaderTime.setText(i18n("Modification Time"));
                if (tvHeaderEditor != null) tvHeaderEditor.setText(i18n("Editor"));
                if (tvHeaderResult != null) tvHeaderResult.setText(i18n("Result"));
                if (btnHistoryClose != null) btnHistoryClose.setText(i18n("Close"));

                TableLayout tableHistoryContent = dialogView.findViewById(R.id.table_history_content);
                if (tableHistoryContent != null) {
                    tableHistoryContent.removeAllViews();
                }

                int index = 1;
                for (JSONObject row : historyRows) {
                    View rowView = inflater.inflate(R.layout.item_history_row, tableHistoryContent, false);

                    TextView tvIndex = rowView.findViewById(R.id.tv_history_index);
                    TextView tvTime = rowView.findViewById(R.id.tv_history_time);
                    TextView tvEditor = rowView.findViewById(R.id.tv_history_editor);
                    TextView tvDisplay = rowView.findViewById(R.id.tv_history_display);
                    TextView tvActual = rowView.findViewById(R.id.tv_history_actual);
                    TextView tvResult = rowView.findViewById(R.id.tv_history_result);

                    String rawTime = pickFirst(row.optString("time"), row.optString("Time"), row.optString("TIME"), row.optString("Update_Date"), row.optString("UPDATE_DATE"), row.optString("Create_Date"));
                    String editor = pickFirst(row.optString("updateBy"), row.optString("UpdateBy"), row.optString("UPDATE_BY"), row.optString("User_Name"), row.optString("Update_By_Name"), row.optString("Full_Name"));
                    String displayVal = pickFirst(row.optString("value"), row.optString("Value"), row.optString("VALUE"), row.optString("Check_Value"));
                    String actualVal = pickFirst(row.optString("value_2"), row.optString("Value_2"), row.optString("VALUE_2"), row.optString("Check_Value_2"));

                    if (rawTime.contains("T")) {
                        try {
                            String cleanTime = rawTime.endsWith("Z") ? rawTime.substring(0, rawTime.length() - 1) : rawTime;
                            if (cleanTime.contains(".")) {
                                cleanTime = cleanTime.substring(0, cleanTime.indexOf('.'));
                            }
                            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                            Date parsedDate = parser.parse(cleanTime);
                            if (parsedDate != null) {
                                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy- HH:mm:ss", Locale.getDefault());
                                formatter.setTimeZone(TimeZone.getDefault());
                                rawTime = formatter.format(parsedDate);
                            }
                        } catch (Exception ignored) {}
                    }

                    String resultText = "—";
                    int resultColor = Color.BLACK;

                    // Nội suy kiểu dữ liệu Số và Ngoại quan linh hoạt theo cặp thông số thực tế
                    boolean isNumericMode = item.isNumericInput()
                            || (item.min != null && !item.min.isEmpty() && item.max != null && !item.max.isEmpty())
                            || (actualVal != null && !actualVal.isEmpty());

                    if (isNumericMode) {
                        if (!displayVal.isEmpty() && !actualVal.isEmpty()) {
                            try {
                                double v1 = Double.parseDouble(displayVal);
                                double v2 = Double.parseDouble(actualVal);
                                double min = (item.min == null || item.min.trim().isEmpty()) ? Double.NEGATIVE_INFINITY : Double.parseDouble(item.min.trim());
                                double max = (item.max == null || item.max.trim().isEmpty()) ? Double.POSITIVE_INFINITY : Double.parseDouble(item.max.trim());

                                boolean isOk = (v1 >= min && v1 <= max) && (v2 >= min && v2 <= max);
                                resultText = isOk ? "OK" : "NG";
                                resultColor = isOk ? Color.parseColor("#047857") : Color.parseColor("#B91C1C");
                            } catch (Exception e) {
                                resultText = "NG";
                                resultColor = Color.parseColor("#B91C1C");
                            }
                        } else {
                            resultText = "NG";
                            resultColor = Color.parseColor("#B91C1C");
                        }
                    } else {
                        if (!displayVal.isEmpty()) {
                            resultText = displayVal.toUpperCase();
                            resultColor = "OK".equalsIgnoreCase(resultText) ? Color.parseColor("#047857") : Color.parseColor("#B91C1C");
                            actualVal = "";
                        }
                    }

                    if (tvIndex != null) tvIndex.setText(String.valueOf(index++));
                    if (tvTime != null) tvTime.setText(rawTime.isEmpty() ? "—" : rawTime);
                    if (tvEditor != null) tvEditor.setText(editor.isEmpty() ? "—" : editor);
                    if (tvDisplay != null) tvDisplay.setText(displayVal.isEmpty() ? "—" : displayVal);
                    if (tvActual != null) tvActual.setText(actualVal.isEmpty() ? "—" : actualVal);

                    if (tvResult != null) {
                        tvResult.setText(resultText);
                        tvResult.setTextColor(resultColor);
                    }

                    if (tableHistoryContent != null) {
                        tableHistoryContent.addView(rowView);

                        View divider = new View(EnterWorkOrderDataActivity.this);
                        TableLayout.LayoutParams dividerLp = new TableLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, 1);
                        divider.setLayoutParams(dividerLp);
                        divider.setBackgroundColor(Color.parseColor("#E5E7EB"));
                        tableHistoryContent.addView(divider);
                    }
                }

                AlertDialog historyDialog = new AlertDialog.Builder(this)
                        .setTitle(i18n("Modification History") + ": " + item.checkName)
                        .setView(dialogView)
                        .show();

                if (btnHistoryClose != null) {
                    btnHistoryClose.setOnClickListener(v -> historyDialog.dismiss());
                }
            });
        });
    }

    private String resolveStatusLabel(String status) {
        if ("0".equals(status)) return i18n("Incomplete");
        if ("1".equals(status)) return i18n("Approve");
        if ("2".equals(status)) return i18n("Checksheet OK");
        if ("3".equals(status)) return i18n("Checksheet NG");
        if ("5".equals(status)) return i18n("Overdue");
        return i18n("");
    }

    private String pickFirst(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim();
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

    public void showLargeImagePreview(final String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty() || "null".equalsIgnoreCase(imagePath.trim())) {
            return;
        }

        runOnUiThread(() -> {
            // Khởi tạo Dialog phóng đại dạng Fullscreen không viền tiêu đề
            final Dialog dialog = new Dialog(EnterWorkOrderDataActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            // Dựng Layout cha bằng code Java để không bị phụ thuộc vào tệp XML giao diện bên ngoài
            android.widget.FrameLayout layout = new android.widget.FrameLayout(EnterWorkOrderDataActivity.this);
            layout.setBackgroundColor(Color.BLACK);

            // Thiết lập ImageView nhận diện khung hiển thị căn giữa
            final ImageView imageView = new ImageView(EnterWorkOrderDataActivity.this);
            android.widget.FrameLayout.LayoutParams imgParams = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            imageView.setLayoutParams(imgParams);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(imageView);

            // Thiết lập nút Đóng (X) màu trắng ở góc trên bên phải màn hình
            ImageView btnClose = new ImageView(EnterWorkOrderDataActivity.this);
            android.widget.FrameLayout.LayoutParams closeParams = new android.widget.FrameLayout.LayoutParams(80, 80);
            closeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            closeParams.topMargin = 60;
            closeParams.rightMargin = 60;
            btnClose.setLayoutParams(closeParams);
            btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            btnClose.setColorFilter(Color.WHITE);
            layout.addView(btnClose);

            dialog.setContentView(layout);

            // Bấm nút Đóng hoặc bấm ra vùng màn hình đen đều tự động thoát Trình xem ảnh
            btnClose.setOnClickListener(v -> dialog.dismiss());
            layout.setOnClickListener(v -> dialog.dismiss());

            final String cleanPath = normalizeImageReference(imagePath.trim());

            Object loadModel = cleanPath;
            if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
                String token = HttpClient.getToken();
                if (token != null && !token.trim().isEmpty()) {
                    loadModel = new GlideUrl(cleanPath, new LazyHeaders.Builder()
                            .addHeader("Authorization", "Bearer " + token.trim())
                            .build());
                }
            }

            Glide.with(EnterWorkOrderDataActivity.this)
                    .load(loadModel)
                    .fitCenter()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            Log.e("IMAGE_PREVIEW_LOAD_ERR", "Lỗi tải ảnh preview: " + String.valueOf(model), e);
                            runOnUiThread(() -> Toast.makeText(EnterWorkOrderDataActivity.this, i18n("Image load error"), Toast.LENGTH_SHORT).show());
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);

            dialog.show();
        });
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

        if (cleaned.startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(cleaned);
                if (arr.length() > 0) cleaned = arr.optString(0).trim();
            } catch (Exception ignored) {}
        }

        int queryIndex = cleaned.indexOf('?');
        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }

        int lastSlash = cleaned.lastIndexOf('/');
        int lastBackslash = cleaned.lastIndexOf('\\');
        int cutIndex = Math.max(lastSlash, lastBackslash);

        if (cutIndex >= 0 && cutIndex < cleaned.length() - 1) {
            String fileName = cleaned.substring(cutIndex + 1).replace("\"", "").trim();
            return fileName;
        }

        return cleaned.replace("\"", "").trim();
    }

    private String resolveUploadedImageReference(HttpClient.APIReturn rs, Uri fallbackUri) {
        if (rs != null && rs.data != null) {
            for (JSONObject row : rs.data) {
                String candidate = cleanNull(pickFirst(
                        row.optString("Images_1"),
                        row.optString("Images"),
                        row.optString("Image_List"),
                        row.optString("value"),
                        row.optString("Value"),
                        row.optString("fileName"),
                        row.optString("filename")
                ));

                if (!candidate.isEmpty()) {
                    if (candidate.startsWith("[")) {
                        try {
                            JSONArray arr = new JSONArray(candidate);
                            if (arr.length() > 0) {
                                candidate = arr.optString(0); // Lấy phần tử đầu tiên vừa upload
                            }
                        } catch (Exception ignored) {}
                    }

                    String finalRef = normalizeImageReference(candidate);
                    if (!finalRef.isEmpty()) return finalRef;
                }
            }
        }

        String fallback = fallbackUri == null ? "" : fallbackUri.toString();
        if (fallback.startsWith("http://") || fallback.startsWith("https://") || fallback.startsWith("content:") || fallback.startsWith("file:")) {
            return fallback;
        }
        return "";
    }
}