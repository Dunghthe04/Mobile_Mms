package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorkOrderEntryDialogHelper {

    public interface OnSaveListener {
        void onSave(JSONObject workOrderData);
    }

    private WorkOrderEntryDialogHelper() {}

    // Static dialog state for onActivityResult callback
    private static Uri selectedFileUri = null;
    private static String uploadedFileName = "";
    private static TextView tvAttachmentStatusView = null;
    private static ImageView imgAttachmentIconView = null;
    private static Context dialogContext = null;

    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 9999 && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedFileUri = data.getData();
            if (tvAttachmentStatusView != null && dialogContext != null) {
                String fileName = getFileName(dialogContext, selectedFileUri);
                tvAttachmentStatusView.setText(fileName);
                tvAttachmentStatusView.setPaintFlags(tvAttachmentStatusView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                if (imgAttachmentIconView != null) {
                    imgAttachmentIconView.setImageResource(R.drawable.ic_paperclip);
                }
            }
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private static void clearState() {
        selectedFileUri = null;
        uploadedFileName = "";
        tvAttachmentStatusView = null;
        imgAttachmentIconView = null;
        dialogContext = null;
    }

    private static void populateMaterialTable(Context context, TableLayout table, List<JSONObject> materials, Map<String, String> nameMap) {
        int childCount = table.getChildCount();
        if (childCount > 1) {
            table.removeViews(1, childCount - 1);
        }

        for (JSONObject item : materials) {
            String matId = item.optString("materialId");
            if (matId.isEmpty()) matId = item.optString("Material_Id");
            if (matId.isEmpty()) matId = item.optString("material_id");

            double qty = item.optDouble("quantity", 0);
            if (qty == 0) qty = item.optDouble("Quantity", 0);

            String name = nameMap.get(matId);
            String displayName;
            if (name == null || name.trim().isEmpty()) {
                displayName = matId;
            } else {
                displayName = matId + " - " + name.trim();
            }

            TableRow row = new TableRow(context);
            row.setPadding(0, 1, 0, 1);
            row.setBackgroundColor(Color.parseColor("#E0E0E0"));

            TextView tvName = new TextView(context);
            TableRow.LayoutParams lpName = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f);
            lpName.setMargins(1, 1, 1, 1);
            tvName.setLayoutParams(lpName);
            tvName.setPadding(10, 12, 10, 12);
            tvName.setBackgroundColor(Color.WHITE);
            tvName.setTextColor(Color.BLACK);
            tvName.setTextSize(13);
            tvName.setText(displayName);

            TextView tvQty = new TextView(context);
            TableRow.LayoutParams lpQty = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f);
            lpQty.setMargins(0, 1, 1, 1);
            tvQty.setLayoutParams(lpQty);
            tvQty.setPadding(10, 12, 10, 12);
            tvQty.setBackgroundColor(Color.WHITE);
            tvQty.setTextColor(Color.BLACK);
            tvQty.setTextSize(13);
            tvQty.setGravity(Gravity.CENTER);
            if (qty == (long) qty) {
                tvQty.setText(String.valueOf((long) qty));
            } else {
                tvQty.setText(String.valueOf(qty));
            }

            row.addView(tvName);
            row.addView(tvQty);
            table.addView(row);
        }
    }

    public static void show(Context context, JSONObject workOrder, OnSaveListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.layout_dialog_work_order_entry, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvOccurrence = dialogView.findViewById(R.id.tv_occurrence_time);
        EditText etStart = dialogView.findViewById(R.id.et_start_time);
        EditText etEnd = dialogView.findViewById(R.id.et_end_time);
        EditText etCurrentStatus = dialogView.findViewById(R.id.et_current_status);
        Spinner spinnerCause = dialogView.findViewById(R.id.spinner_cause_category);
        EditText etCause = dialogView.findViewById(R.id.et_cause);
        EditText etImpact = dialogView.findViewById(R.id.et_impact);
        EditText etProcessContent = dialogView.findViewById(R.id.et_process_content);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spinnerStatus = dialogView.findViewById(R.id.spinner_status);

        // Tab Views
        TextView btnTabInfo = dialogView.findViewById(R.id.btn_tab_info);
        TextView btnTabMaterials = dialogView.findViewById(R.id.btn_tab_materials);
        LinearLayout layoutTabInfoContent = dialogView.findViewById(R.id.layout_tab_info_content);
        LinearLayout layoutTabMaterialsContent = dialogView.findViewById(R.id.layout_tab_materials_content);

        // Attachment Views
        View btnChooseFile = dialogView.findViewById(R.id.btn_choose_file);
        View btnUploadFile = dialogView.findViewById(R.id.btn_upload_file);
        tvAttachmentStatusView = dialogView.findViewById(R.id.tv_attachment_status);
        imgAttachmentIconView = dialogView.findViewById(R.id.img_attachment_icon);

        // Dialog Context
        dialogContext = context;
        selectedFileUri = null;
        uploadedFileName = "";

        // Existing file display
        String existingFile = safeGet(workOrder, "File_Wo");
        if (existingFile.isEmpty()) existingFile = safeGet(workOrder, "FILE_WO");
        if (!existingFile.isEmpty()) {
            uploadedFileName = existingFile;
            if (tvAttachmentStatusView != null) {
                tvAttachmentStatusView.setText(existingFile);
                tvAttachmentStatusView.setPaintFlags(tvAttachmentStatusView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            }
            if (imgAttachmentIconView != null) {
                imgAttachmentIconView.setImageResource(R.drawable.ic_paperclip);
            }
        }

        // Tab switches
        btnTabInfo.setOnClickListener(v -> {
            btnTabInfo.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabInfo.setTextColor(Color.WHITE);
            btnTabMaterials.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabMaterials.setTextColor(Color.BLACK);
            layoutTabInfoContent.setVisibility(View.VISIBLE);
            layoutTabMaterialsContent.setVisibility(View.GONE);
        });

        btnTabMaterials.setOnClickListener(v -> {
            btnTabInfo.setBackgroundResource(R.drawable.bg_tab_inactive);
            btnTabInfo.setTextColor(Color.BLACK);
            btnTabMaterials.setBackgroundResource(R.drawable.bg_tab_active);
            btnTabMaterials.setTextColor(Color.WHITE);
            layoutTabInfoContent.setVisibility(View.GONE);
            layoutTabMaterialsContent.setVisibility(View.VISIBLE);
        });

        // Choose file click listener
        btnChooseFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).startActivityForResult(
                        Intent.createChooser(intent, "Chọn tài liệu đính kèm"), 9999);
            }
        });

        // Upload file click listener
        btnUploadFile.setOnClickListener(v -> {
            if (selectedFileUri == null) {
                Toast.makeText(context, "Vui lòng chọn file trước khi tải lên", Toast.LENGTH_SHORT).show();
                return;
            }

            String taskId = safeGet(workOrder, "WO_CODE");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Wo_Code");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "TASK_ID");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_Id");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "taskId");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_ID");

            if (taskId.isEmpty()) {
                Toast.makeText(context, "Không tìm thấy mã Work Order để tải lên", Toast.LENGTH_SHORT).show();
                return;
            }

            ConfigManager config = new ConfigManager(context);
            String serverDynamicUrl = config.getProperty("server_dynamic_url");
            if (serverDynamicUrl.isEmpty()) {
                serverDynamicUrl = "http://192.86.0.225:9101/api/dynamics";
            }

            ProgressDialog uploadProgress = new ProgressDialog(context);
            uploadProgress.setMessage("Đang tải lên tài liệu...");
            uploadProgress.setCancelable(false);
            uploadProgress.show();

            final String finalTaskId = taskId;
            final String finalServerDynamicUrl = serverDynamicUrl;
            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.uploadWorkOrderFile(
                        context,
                        finalServerDynamicUrl,
                        finalTaskId,
                        selectedFileUri
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    uploadProgress.dismiss();
                    if (result != null && result.code == 200 && result.data != null && !result.data.isEmpty()) {
                        String fileVal = result.data.get(0).optString("value");
                        if (!fileVal.isEmpty()) {
                            uploadedFileName = fileVal;
                            if (tvAttachmentStatusView != null) {
                                tvAttachmentStatusView.setText(fileVal);
                            }
                            Toast.makeText(context, "Tải lên tài liệu thành công", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Tải lên thành công nhưng không lấy được tên file", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        String errMsg = (result != null) ? result.message : "Không phản hồi từ máy chủ";
                        Toast.makeText(context, "Tải lên thất bại: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        // WMS Export click listener
        View btnCreateWmsRequest = dialogView.findViewById(R.id.btn_create_wms_request);
        btnCreateWmsRequest.setOnClickListener(v -> {
            List<JSONObject> addMaterialsList = new ArrayList<>();
            String materialInfoStr = safeGet(workOrder, "Material_Info");
            if (materialInfoStr.isEmpty()) materialInfoStr = safeGet(workOrder, "MATERIAL_INFO");

            if (materialInfoStr.isEmpty() || "[]".equals(materialInfoStr)) {
                Toast.makeText(context, "Không có vật tư nào để tạo yêu cầu xuất kho", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONArray matArray = new JSONArray(materialInfoStr);
                for (int i = 0; i < matArray.length(); i++) {
                    JSONObject item = matArray.getJSONObject(i);
                    String matId = item.optString("materialId");
                    if (matId.isEmpty()) matId = item.optString("Material_Id");
                    if (matId.isEmpty()) matId = item.optString("material_id");

                    double qty = item.optDouble("quantity", 0);
                    if (qty == 0) qty = item.optDouble("Quantity", 0);

                    JSONObject matObj = new JSONObject();
                    matObj.put("Material_Id", matId);
                    matObj.put("Quantity", qty);
                    addMaterialsList.add(matObj);
                }
            } catch (Exception e) {
                Toast.makeText(context, "Lỗi phân tích danh sách vật tư: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (addMaterialsList.isEmpty()) {
                Toast.makeText(context, "Không có vật tư hợp lệ để xuất kho", Toast.LENGTH_SHORT).show();
                return;
            }

            PreferenceHandler prefHandler = new PreferenceHandler(context);
            JSONObject userProfile = prefHandler.getJsonObject("user");
            String userLoginId = "";
            if (userProfile != null) {
                userLoginId = userProfile.optString("Id");
                if (userLoginId.isEmpty()) userLoginId = userProfile.optString("username");
                if (userLoginId.isEmpty()) userLoginId = userProfile.optString("User_Name");
            }
            if (userLoginId.isEmpty()) {
                userLoginId = prefHandler.getString("Userlogin");
            }

            if (userLoginId.isEmpty()) {
                Toast.makeText(context, "Không tìm thấy thông tin đăng nhập người dùng", Toast.LENGTH_SHORT).show();
                return;
            }

            String machineIdVal = safeGet(workOrder, "Machine_Id");
            if (machineIdVal.isEmpty()) machineIdVal = safeGet(workOrder, "MACHINE_ID");
            String machineNameVal = safeGet(workOrder, "Machine_Name");
            if (machineNameVal.isEmpty()) machineNameVal = safeGet(workOrder, "MACHINE_NAME");

            String woCodeVal = safeGet(workOrder, "WO_CODE");
            if (woCodeVal.isEmpty()) woCodeVal = safeGet(workOrder, "Wo_Code");

            String requestNote = "Yêu cầu xuất kho cho Work Order " + woCodeVal + "-" + machineIdVal + " " + machineNameVal;
            String requestDateUnixStr = String.valueOf(System.currentTimeMillis() / 1000L);

            String serverUrlStr = prefHandler.getString("server_url");
            if (serverUrlStr.isEmpty()) {
                ConfigManager config = new ConfigManager(context);
                serverUrlStr = config.getProperty("server_url");
            }
            if (serverUrlStr.isEmpty()) {
                serverUrlStr = "http://192.86.0.225:9103";
            }

            ProgressDialog wmsProgress = new ProgressDialog(context);
            wmsProgress.setMessage("Đang tạo yêu cầu xuất kho...");
            wmsProgress.setCancelable(false);
            wmsProgress.show();

            final String finalRequestDateUnix = requestDateUnixStr;
            final String finalUserLoginId = userLoginId;
            final String finalRequestNote = requestNote;
            final List<JSONObject> finalMaterials = addMaterialsList;
            final String finalServerUrl = serverUrlStr;

            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.save_request_material(
                        context,
                        finalRequestDateUnix,
                        "FE",
                        finalRequestNote,
                        finalMaterials,
                        finalServerUrl,
                        finalUserLoginId
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    wmsProgress.dismiss();
                    if (result != null && result.code == 200) {
                        Toast.makeText(context, "Tạo yêu cầu xuất kho thành công!", Toast.LENGTH_SHORT).show();
                    } else {
                        String errMsg = (result != null) ? result.message : "Không phản hồi";
                        Toast.makeText(context, "Lỗi tạo yêu cầu xuất kho: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        // Materials Table Display
        TableLayout tableMaterials = dialogView.findViewById(R.id.table_materials);
        TextView tvNoMaterials = dialogView.findViewById(R.id.tv_no_materials);

        String materialInfoStr = safeGet(workOrder, "Material_Info");
        if (materialInfoStr.isEmpty()) materialInfoStr = safeGet(workOrder, "MATERIAL_INFO");

        if (materialInfoStr.isEmpty() || "[]".equals(materialInfoStr)) {
            tableMaterials.setVisibility(View.GONE);
            tvNoMaterials.setVisibility(View.VISIBLE);
        } else {
            tableMaterials.setVisibility(View.VISIBLE);
            tvNoMaterials.setVisibility(View.GONE);

            final List<JSONObject> parsedMaterials = new ArrayList<>();
            try {
                JSONArray matArray = new JSONArray(materialInfoStr);
                for (int i = 0; i < matArray.length(); i++) {
                    parsedMaterials.add(matArray.getJSONObject(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (parsedMaterials.isEmpty()) {
                tableMaterials.setVisibility(View.GONE);
                tvNoMaterials.setVisibility(View.VISIBLE);
            } else {
                populateMaterialTable(context, tableMaterials, parsedMaterials, new HashMap<>());

                List<String> matIds = new ArrayList<>();
                for (JSONObject item : parsedMaterials) {
                    String matId = item.optString("materialId");
                    if (matId.isEmpty()) matId = item.optString("Material_Id");
                    if (matId.isEmpty()) matId = item.optString("material_id");
                    if (!matId.isEmpty() && !matIds.contains(matId)) {
                        matIds.add(matId);
                    }
                }

                if (!matIds.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Item_Id IN (");
                    for (int i = 0; i < matIds.size(); i++) {
                        sb.append("'").append(matIds.get(i)).append("'");
                        if (i < matIds.size() - 1) {
                            sb.append(",");
                        }
                    }
                    sb.append(")");
                    String whereClause = sb.toString();

                    ConfigManager configManager = new ConfigManager(context);
                    String serverDynamic = configManager.getProperty("server_dynamic_url");
                    if (serverDynamic.isEmpty()) {
                        serverDynamic = "http://192.86.0.225:9101/api/dynamics";
                    }
                    String schemaMms = configManager.getProperty("schema_mms");
                    if (schemaMms.isEmpty()) schemaMms = "MES_MMS_MKHC";
                    String schemaCore = configManager.getProperty("schema_core");
                    if (schemaCore.isEmpty()) schemaCore = "MES_CORE_MKHC";
                    String schemaWms = configManager.getProperty("schema_wms");
                    if (schemaWms.isEmpty()) schemaWms = "MES_WMS_MKHC";

                    JSONObject conditionObj = new JSONObject();
                    try {
                        conditionObj.put("dsa", "1=1");
                        conditionObj.put("where", whereClause);
                        conditionObj.put("Schema_Core", schemaCore);
                        conditionObj.put("Schema_MMS", schemaMms);
                        conditionObj.put("Schema_WMS", schemaWms);
                        conditionObj.put("Offset", 0);
                        conditionObj.put("Limit", 1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    final String finalServerDynamic = serverDynamic;
                    final JSONObject finalConditionObj = conditionObj;

                    new Thread(() -> {
                        HttpClient.APIReturn result = HttpClient.callDynamics(
                                context,
                                finalServerDynamic,
                                "mes_mms",
                                "MMS_GET_LIST_MATERIALS_225",
                                finalConditionObj
                        );

                        if (result != null && result.code == 200 && result.data != null) {
                            Map<String, String> nameMap = new HashMap<>();
                            for (JSONObject mat : result.data) {
                                String itemId = mat.optString("Item_Id");
                                String itemName = mat.optString("Item_Name");
                                if (!itemId.isEmpty() && !itemName.isEmpty()) {
                                    nameMap.put(itemId, itemName);
                                }
                            }
                            new Handler(Looper.getMainLooper()).post(() -> {
                                populateMaterialTable(context, tableMaterials, parsedMaterials, nameMap);
                            });
                        }
                    }).start();
                }
            }
        }

        String woCode = safeGet(workOrder, "WO_CODE");
        if (woCode.isEmpty()) woCode = safeGet(workOrder, "Wo_Code");
        tvTitle.setText("Nhập dữ liệu: " + woCode);

        String requestDate = safeGet(workOrder, "Request_Date");
        tvOccurrence.setText(formatDateTimeDisplay(requestDate));

        String[] causeOptions = context.getResources().getStringArray(R.array.work_order_cause_categories);
        String[] statusOptions = context.getResources().getStringArray(R.array.work_order_entry_status);

        spinnerCause.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, causeOptions));
        spinnerStatus.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, statusOptions));

        // Bind fields
        String actualTaskDateUnix = safeGet(workOrder, "Actual_Task_Date_Unix");
        if (actualTaskDateUnix.isEmpty()) actualTaskDateUnix = safeGet(workOrder, "ACTUAL_TASK_DATE_UNIX");
        etStart.setText(formatUnixTimestamp(actualTaskDateUnix));

        String approveTaskDateUnix = safeGet(workOrder, "Approve_Task_Date_Unix");
        if (approveTaskDateUnix.isEmpty()) approveTaskDateUnix = safeGet(workOrder, "APPROVE_TASK_DATE_UNIX");
        etEnd.setText(formatUnixTimestamp(approveTaskDateUnix));

        String currentSituation = safeGet(workOrder, "Task_Current_Situation");
        if (currentSituation.isEmpty()) currentSituation = safeGet(workOrder, "CURRENT_SITUATION");
        if (currentSituation.isEmpty()) currentSituation = safeGet(workOrder, "Current_Situation");
        etCurrentStatus.setText(currentSituation);

        String rootCause = safeGet(workOrder, "Task_Root_Cause");
        if (rootCause.isEmpty()) rootCause = safeGet(workOrder, "ROOT_CAUSE");
        if (rootCause.isEmpty()) rootCause = safeGet(workOrder, "Root_Cause");
        etCause.setText(rootCause);

        String qualityImpact = safeGet(workOrder, "Task_Quality_Impact");
        if (qualityImpact.isEmpty()) qualityImpact = safeGet(workOrder, "QUALITY_IMPACT");
        if (qualityImpact.isEmpty()) qualityImpact = safeGet(workOrder, "Quality_Impact");
        etImpact.setText(qualityImpact);

        String actionTaken = safeGet(workOrder, "Task_Action_Taken");
        if (actionTaken.isEmpty()) actionTaken = safeGet(workOrder, "ACTION_TAKEN");
        if (actionTaken.isEmpty()) actionTaken = safeGet(workOrder, "Action_Taken");
        if (actionTaken.isEmpty()) actionTaken = safeGet(workOrder, "Request_Reason");
        etProcessContent.setText(actionTaken);

        String note = safeGet(workOrder, "Task_Note");
        if (note.isEmpty()) note = safeGet(workOrder, "NOTE");
        if (note.isEmpty()) note = safeGet(workOrder, "Note");
        etNote.setText(note);

        String rootCauseAnother = safeGet(workOrder, "Task_Root_Cause_Another");
        if (rootCauseAnother.isEmpty()) rootCauseAnother = safeGet(workOrder, "ROOT_CAUSE_ANOTHER");
        if (rootCauseAnother.isEmpty()) rootCauseAnother = safeGet(workOrder, "Root_Cause_Another");
        int causePosition = 0;
        if (!rootCauseAnother.isEmpty()) {
            for (int i = 0; i < causeOptions.length; i++) {
                if (causeOptions[i].equalsIgnoreCase(rootCauseAnother)) {
                    causePosition = i;
                    break;
                }
            }
        }
        spinnerCause.setSelection(causePosition);

        String statusStr = safeGet(workOrder, "Status_1");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "STATUS_1");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "STATUS");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "Status");
        int statusPosition = 0;
        try {
            if (!statusStr.isEmpty()) {
                double statusD = Double.parseDouble(statusStr);
                int statusVal = (int) statusD;
                if (statusVal >= 0 && statusVal < statusOptions.length) {
                    statusPosition = statusVal;
                }
            }
        } catch (NumberFormatException e) {
            for (int i = 0; i < statusOptions.length; i++) {
                if (statusOptions[i].equalsIgnoreCase(statusStr)) {
                    statusPosition = i;
                    break;
                }
            }
        }
        spinnerStatus.setSelection(statusPosition);

        etStart.setOnClickListener(v -> pickDateTime(context, etStart));
        etEnd.setOnClickListener(v -> pickDateTime(context, etEnd));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_dialog_close).setOnClickListener(v -> {
            dialog.dismiss();
            clearState();
        });
        dialog.setOnDismissListener(d -> clearState());

        dialogView.findViewById(R.id.btn_dialog_save).setOnClickListener(v -> {
            String taskId = safeGet(workOrder, "TASK_ID");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_Id");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "taskId");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_ID");

            if (taskId.isEmpty()) {
                Toast.makeText(context, "Không tìm thấy TASK_ID của Work Order", Toast.LENGTH_SHORT).show();
                return;
            }

            String startTimeSecStr = convertDateTimeToUnix(etStart.getText().toString().trim());
            String endTimeSecStr = convertDateTimeToUnix(etEnd.getText().toString().trim());
            String currentSit = etCurrentStatus.getText().toString().trim();

            int selectedCausePos = spinnerCause.getSelectedItemPosition();
            String selectedCause = "";
            if (selectedCausePos > 0 && selectedCausePos < causeOptions.length) {
                selectedCause = causeOptions[selectedCausePos];
            }

            String causeDetail = etCause.getText().toString().trim();
            String impact = etImpact.getText().toString().trim();
            String action = etProcessContent.getText().toString().trim();
            String noteVal = etNote.getText().toString().trim();
            int statusVal = spinnerStatus.getSelectedItemPosition();

            long actualTaskDateUnixVal = 0;
            try {
                actualTaskDateUnixVal = Long.parseLong(startTimeSecStr);
            } catch (NumberFormatException e) {}

            long approveTaskDateUnixVal = 0;
            try {
                approveTaskDateUnixVal = Long.parseLong(endTimeSecStr);
            } catch (NumberFormatException e) {}

            ConfigManager configManager = new ConfigManager(context);
            String serverDynamic = configManager.getProperty("server_dynamic_url");
            if (serverDynamic.isEmpty()) {
                serverDynamic = "http://192.86.0.225:9101/api/dynamics";
            }
            String schemaMms = configManager.getProperty("schema_mms");
            if (schemaMms.isEmpty()) schemaMms = "MES_MMS_MKHC";
            String schemaCore = configManager.getProperty("schema_core");
            if (schemaCore.isEmpty()) schemaCore = "MES_CORE_MKHC";

            JSONObject conditionObj = new JSONObject();
            try {
                conditionObj.put("Schema_MMS", schemaMms);
                conditionObj.put("Schema_Core", schemaCore);
                conditionObj.put("ACTUAL_TASK_DATE_UNIX", actualTaskDateUnixVal);
                conditionObj.put("APPROVE_TASK_DATE_UNIX", approveTaskDateUnixVal);
                conditionObj.put("CURRENT_SITUATION", currentSit);
                conditionObj.put("ROOT_CAUSE_ANOTHER", selectedCause);
                conditionObj.put("ROOT_CAUSE", causeDetail);
                conditionObj.put("QUALITY_IMPACT", impact);
                conditionObj.put("ACTION_TAKEN", action);
                conditionObj.put("NOTE", noteVal);
                conditionObj.put("STATUS", statusVal);
                conditionObj.put("TASK_ID", taskId);
            } catch (Exception e) {
                Toast.makeText(context, "Lỗi tạo dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Đang lưu...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            final String finalServerDynamic = serverDynamic;
            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.callDynamics(
                        context,
                        finalServerDynamic,
                        "mes_mms",
                        "MES_TASK_UPDATE_WORK_ORDER_OTHER",
                        conditionObj
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    if (result != null && result.code == 200) {
                        Toast.makeText(context, "Lưu dữ liệu thành công", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        clearState();
                        if (listener != null) {
                            listener.onSave(workOrder);
                        }
                    } else {
                        String errMsg = (result != null) ? result.message : "Không phản hồi";
                        Toast.makeText(context, "Lưu thất bại: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void pickDateTime(Context context, EditText target) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(context, (view, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(context, (tView, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
                target.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String formatDateTimeDisplay(String raw) {
        if (raw == null || raw.isEmpty() || "null".equalsIgnoreCase(raw)) return "";
        try {
            String clean = raw;
            if (clean.contains("T") && clean.length() >= 19) {
                clean = clean.substring(0, 19);
            }
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
            out.setTimeZone(java.util.TimeZone.getDefault());
            
            Date parsed = in.parse(clean);
            return parsed != null ? out.format(parsed) : raw;
        } catch (Exception e) {
            try {
                SimpleDateFormat inShort = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                inShort.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
                out.setTimeZone(java.util.TimeZone.getDefault());
                Date parsed = inShort.parse(raw);
                return parsed != null ? out.format(parsed) : raw;
            } catch (Exception ex) {
                return raw;
            }
        }
    }


    private static String formatUnixTimestamp(String unixSecStr) {
        if (unixSecStr == null || unixSecStr.trim().isEmpty()) return "";
        try {
            long seconds = Long.parseLong(unixSecStr.trim());
            if (seconds <= 0) return "";
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(seconds * 1000L);
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
            return out.format(cal.getTime());
        } catch (Exception e) {
            return unixSecStr;
        }
    }

    private static String convertDateTimeToUnix(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "0";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
            Date date = sdf.parse(dateStr.trim());
            if (date != null) {
                return String.valueOf(date.getTime() / 1000L);
            }
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf24 = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date date = sdf24.parse(dateStr.trim());
                if (date != null) {
                    return String.valueOf(date.getTime() / 1000L);
                }
            } catch (Exception e2) {
                // ignore
            }
        }
        return "0";
    }

    private static String safeGet(JSONObject obj, String key) {
        if (obj == null) return "";
        String val = obj.optString(key, "").trim();
        return "null".equalsIgnoreCase(val) ? "" : val;
    }
}

