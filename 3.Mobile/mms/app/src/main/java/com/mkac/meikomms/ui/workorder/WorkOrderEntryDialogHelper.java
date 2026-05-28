package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.os.Environment;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
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
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class WorkOrderEntryDialogHelper {

    public interface OnSaveListener {
        void onSave(JSONObject workOrderData);
    }

    private WorkOrderEntryDialogHelper() {}

    private static Uri selectedFileUri = null;
    private static String uploadedFileName = "";
    private static TextView tvAttachmentStatusView = null;
    private static ImageView imgAttachmentIconView = null;
    private static Context dialogContext = null;

    // Danh sách key chuẩn (tiếng Anh) — dùng để lưu DB và tra dịch qua i18n()
    private static final List<String> REASON_KEYS = Arrays.asList(
            "Select Cause",
            "Component Wear",
            "Electrical Control Failure",
            "Operator Error",
            "Initial Installation",
            "Setting Change",
            "Misalignment",
            "Unknown Cause",
            "Other Cause"
    );

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

    /**
     * Chuyển index (1-based) sang chuỗi đã được dịch theo ngôn ngữ hiện tại.
     * Dùng để hiển thị giá trị đã lưu trong DB ra UI.
     */
    private static String convertStatus(int status) {
        if (status >= 1 && status <= REASON_KEYS.size()) {
            return i18n(REASON_KEYS.get(status - 1));
        }
        return "";
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
        LanguageAPIUtils.setLang(dialogView);

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

        TextView btnTabInfo = dialogView.findViewById(R.id.btn_tab_info);
        TextView btnTabMaterials = dialogView.findViewById(R.id.btn_tab_materials);
        LinearLayout layoutTabInfoContent = dialogView.findViewById(R.id.layout_tab_info_content);
        LinearLayout layoutTabMaterialsContent = dialogView.findViewById(R.id.layout_tab_materials_content);

        View btnChooseFile = dialogView.findViewById(R.id.btn_choose_file);
        View btnUploadFile = dialogView.findViewById(R.id.btn_upload_file);
        tvAttachmentStatusView = dialogView.findViewById(R.id.tv_attachment_status);
        imgAttachmentIconView = dialogView.findViewById(R.id.img_attachment_icon);

        dialogContext = context;
        selectedFileUri = null;
        uploadedFileName = "";

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

        if (tvAttachmentStatusView != null) {
            tvAttachmentStatusView.setOnClickListener(v -> {
                if (uploadedFileName == null || uploadedFileName.isEmpty()) {
                    Toast.makeText(context, i18n("No attachment file to download"), Toast.LENGTH_SHORT).show();
                    return;
                }

                ConfigManager config = new ConfigManager(context);
                String serverDynamicUrl = config.getProperty("server_dynamic_url");
                if (serverDynamicUrl.isEmpty()) {
                    serverDynamicUrl = "http://192.86.0.225:9101/api/dynamics";
                }

                String finalUrl = serverDynamicUrl;
                if (finalUrl.contains("://")) {
                    String protocol = finalUrl.split("://")[0];
                    String addressWithPort = finalUrl.split("://")[1];
                    if (addressWithPort.contains(":")) {
                        finalUrl = protocol + "://" + addressWithPort.split(":")[0];
                    } else {
                        finalUrl = protocol + "://" + addressWithPort;
                    }
                }
                if (finalUrl.endsWith("/")) {
                    finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
                }
                String downloadUrl = finalUrl + ":9101/api/v1/mms_file-img/" + uploadedFileName;
                downloadFile(context, downloadUrl, uploadedFileName);
            });
        }

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

        btnChooseFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).startActivityForResult(
                        Intent.createChooser(intent, i18n("Select attachment document")), 9999);
            }
        });

        btnUploadFile.setOnClickListener(v -> {
            if (selectedFileUri == null) {
                Toast.makeText(context, i18n("Please select a file before uploading"), Toast.LENGTH_SHORT).show();
                return;
            }

            String taskId = safeGet(workOrder, "WO_CODE");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Wo_Code");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "TASK_ID");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_Id");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "taskId");
            if (taskId.isEmpty()) taskId = safeGet(workOrder, "Task_ID");

            if (taskId.isEmpty()) {
                Toast.makeText(context, i18n("Work Order code not found for upload"), Toast.LENGTH_SHORT).show();
                return;
            }

            ConfigManager config = new ConfigManager(context);
            String serverDynamicUrl = config.getProperty("server_dynamic_url");
            if (serverDynamicUrl.isEmpty()) {
                serverDynamicUrl = "http://192.86.0.225:9101/api/dynamics";
            }

            ProgressDialog uploadProgress = new ProgressDialog(context);
            uploadProgress.setMessage(i18n("Uploading document..."));
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
                                tvAttachmentStatusView.setPaintFlags(tvAttachmentStatusView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                            }
                            Toast.makeText(context, i18n("Document uploaded successfully"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, i18n("Uploaded successfully but failed to get file name"), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        String errMsg = (result != null) ? result.message : i18n("No response from server");
                        Toast.makeText(context, i18n("Upload failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        // =========================================================================
        // CREATE WMS REQUEST
        // =========================================================================
        final View btnCreateWmsRequest = dialogView.findViewById(R.id.btn_create_wms_request);
        btnCreateWmsRequest.setOnClickListener(v -> {
            PreferenceHandler prefHandler = new PreferenceHandler(context);
            JSONObject userProfile = prefHandler.getJsonObject("user");
            String userLoginId = "";
            if (userProfile != null) {
                userLoginId = userProfile.optString("userId");
                if (userLoginId.isEmpty()) userLoginId = userProfile.optString("Id");
            }
            if (userLoginId.isEmpty()) {
                userLoginId = prefHandler.getString("Userlogin");
            }

            if (userLoginId.isEmpty()) {
                Toast.makeText(context, i18n("User login information not found"), Toast.LENGTH_SHORT).show();
                return;
            }

            String username = prefHandler.getString("Userlogin");
            if (username.isEmpty() && userProfile != null) {
                username = userProfile.optString("username");
                if (username.isEmpty()) username = userProfile.optString("User_Name");
            }

            String machineIdVal = safeGet(workOrder, "Machine_Id");
            if (machineIdVal.isEmpty()) machineIdVal = safeGet(workOrder, "MACHINE_ID");
            String machineNameVal = safeGet(workOrder, "Machine_Name");
            if (machineNameVal.isEmpty()) machineNameVal = safeGet(workOrder, "MACHINE_NAME");

            int woTypeVal = workOrder.optInt("WO_TYPE", 0);
            if (woTypeVal == 0) woTypeVal = workOrder.optInt("Wo_Type", 3);
            String requestPurpose;
            switch (woTypeVal) {
                case 1: requestPurpose = "CM"; break;
                case 2: requestPurpose = "PM"; break;
                case 3:
                default: requestPurpose = "BM"; break;
            }

            List<JSONObject> addMaterialsList = new ArrayList<>();
            String materialInfoStr = safeGet(workOrder, "Material_Info");
            if (materialInfoStr.isEmpty()) materialInfoStr = safeGet(workOrder, "MATERIAL_INFO");

            if (materialInfoStr.isEmpty() || "[]".equals(materialInfoStr)) {
                Toast.makeText(context, i18n("No materials available to create warehouse request"), Toast.LENGTH_SHORT).show();
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

                    String qtyStr = (qty == (long) qty) ? String.valueOf((long) qty) : String.valueOf(qty);
                    JSONObject matObj = new JSONObject();
                    matObj.put("Item_Id", matId);
                    matObj.put("Item_Qty", qtyStr);
                    matObj.put("Machine_Id", machineIdVal);
                    matObj.put("Purpose", requestPurpose);
                    matObj.put("User_Export", username);
                    addMaterialsList.add(matObj);
                }
            } catch (Exception e) {
                Toast.makeText(context, i18n("Error parsing material list") + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (addMaterialsList.isEmpty()) {
                Toast.makeText(context, i18n("No valid materials for warehouse release"), Toast.LENGTH_SHORT).show();
                return;
            }

            String woCodeVal = safeGet(workOrder, "WO_CODE");
            if (woCodeVal.isEmpty()) woCodeVal = safeGet(workOrder, "Wo_Code");

            String machineDisplay = machineNameVal;
            if (machineDisplay.isEmpty()) machineDisplay = machineIdVal;
            if (machineDisplay.startsWith("Máy ")) {
                machineDisplay = machineDisplay.substring(4);
            } else if (machineDisplay.startsWith("Máy")) {
                machineDisplay = machineDisplay.substring(3);
            }
            String requestNote = i18n("Warehouse request for Work Order") + " " + woCodeVal
                    + " - " + i18n("Machine") + " " + machineDisplay;
            String requestDateUnixStr = String.valueOf(System.currentTimeMillis() / 1000L);

            String serverUrlStr = prefHandler.getString("server_url");
            if (serverUrlStr.isEmpty()) {
                ConfigManager config = new ConfigManager(context);
                serverUrlStr = config.getProperty("server_url");
            }
            if (serverUrlStr.isEmpty()) {
                serverUrlStr = "http://192.86.0.225:9103";
            }

            btnCreateWmsRequest.setEnabled(false);
            btnCreateWmsRequest.setClickable(false);
            btnCreateWmsRequest.setAlpha(0.5f);

            ProgressDialog wmsProgress = new ProgressDialog(context);
            wmsProgress.setMessage(i18n("Creating warehouse release request..."));
            wmsProgress.setCancelable(false);
            wmsProgress.show();

            final String finalRequestDateUnix = requestDateUnixStr;
            final String finalUsername = username;
            final String finalRequestNote = requestNote;
            final List<JSONObject> finalMaterials = addMaterialsList;
            final String finalServerUrl = serverUrlStr;
            final String finalRequestPurpose = requestPurpose;
            final String finalMachineId = machineIdVal;

            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.save_request_material(
                        context, finalRequestDateUnix, "FE", finalRequestNote, finalMaterials,
                        finalServerUrl, finalUsername, finalRequestPurpose, finalMachineId
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    wmsProgress.dismiss();
                    if (result != null && result.code == 200) {
                        Toast.makeText(context, i18n("Warehouse release request created successfully!"), Toast.LENGTH_SHORT).show();
                        btnCreateWmsRequest.setEnabled(false);
                        btnCreateWmsRequest.setClickable(false);
                        btnCreateWmsRequest.setAlpha(0.35f);
                    } else {
                        String errMsg = (result != null) ? result.message : i18n("No response from server");
                        Toast.makeText(context, i18n("Error creating warehouse release request") + ": " + errMsg, Toast.LENGTH_LONG).show();
                        btnCreateWmsRequest.setEnabled(true);
                        btnCreateWmsRequest.setClickable(true);
                        btnCreateWmsRequest.setAlpha(1.0f);
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
                        if (i < matIds.size() - 1) sb.append(",");
                    }
                    sb.append(")");
                    String whereClause = sb.toString();

                    ConfigManager configManager = new ConfigManager(context);
                    String serverDynamic = configManager.getProperty("server_dynamic_url");
                    if (serverDynamic.isEmpty()) serverDynamic = "http://192.86.0.225:9101/api/dynamics";
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
                                context, finalServerDynamic, "mes_mms", "MMS_GET_LIST_MATERIALS_225", finalConditionObj
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
                            new Handler(Looper.getMainLooper()).post(() ->
                                    populateMaterialTable(context, tableMaterials, parsedMaterials, nameMap));
                        }
                    }).start();
                }
            }
        }

        String woCode = safeGet(workOrder, "WO_CODE");
        if (woCode.isEmpty()) woCode = safeGet(workOrder, "Wo_Code");
        tvTitle.setText(i18n("Enter data") + ": " + woCode);

        String requestDate = safeGet(workOrder, "Request_Date");
        tvOccurrence.setText(formatDateTimeDisplay(requestDate));

        // =========================================================================
        // SPINNER CAUSE — dùng REASON_KEYS + i18n() thay vì R.array
        // =========================================================================
        // rawCauseOptions: English keys để so sánh/lưu DB
        final String[] rawCauseOptions = REASON_KEYS.toArray(new String[0]);
        // causeOptions: chuỗi đã dịch để hiển thị lên Spinner
        final String[] causeOptions = new String[REASON_KEYS.size()];
        for (int i = 0; i < REASON_KEYS.size(); i++) {
            causeOptions[i] = i18n(REASON_KEYS.get(i));
        }

        String[] rawStatusOptions = context.getResources().getStringArray(R.array.work_order_entry_status);
        String[] statusOptions = new String[rawStatusOptions.length];
        for (int i = 0; i < rawStatusOptions.length; i++) {
            statusOptions[i] = LanguageAPIUtils.i18n(rawStatusOptions[i]);
        }

        // Adapter cho spinnerCause: vô hiệu hóa item đầu (placeholder "Select Cause")
        ArrayAdapter<String> causeAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, causeOptions) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0;
            }
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == 0 ? Color.GRAY : Color.BLACK);
                return view;
            }
        };
        causeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCause.setAdapter(causeAdapter);
        spinnerStatus.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, statusOptions));

        // =========================================================================
        // Điền dữ liệu từ workOrder vào các field
        // =========================================================================
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
        etProcessContent.setText(actionTaken);

        String note = safeGet(workOrder, "Task_Note");
        if (note.isEmpty()) note = safeGet(workOrder, "NOTE");
        if (note.isEmpty()) note = safeGet(workOrder, "Note");
        etNote.setText(note);

        // =========================================================================
        // Khôi phục vị trí spinnerCause từ DB
        // Ưu tiên so sánh với rawCauseOptions (English key),
        // fallback so sánh với causeOptions (đã dịch) để tương thích dữ liệu cũ
        // =========================================================================
        String rootCauseAnother = safeGet(workOrder, "Task_Root_Cause_Another");
        if (rootCauseAnother.isEmpty()) rootCauseAnother = safeGet(workOrder, "ROOT_CAUSE_ANOTHER");
        if (rootCauseAnother.isEmpty()) rootCauseAnother = safeGet(workOrder, "Root_Cause_Another");

        int causePosition = 0;
        if (!rootCauseAnother.isEmpty()) {
            // Bước 1: so khớp English key (cách lưu chuẩn)
            for (int i = 0; i < rawCauseOptions.length; i++) {
                if (rawCauseOptions[i].equalsIgnoreCase(rootCauseAnother)) {
                    causePosition = i;
                    break;
                }
            }
            // Bước 2: nếu chưa tìm thấy, so khớp chuỗi đã dịch (dữ liệu cũ)
            if (causePosition == 0) {
                for (int i = 0; i < causeOptions.length; i++) {
                    if (causeOptions[i].equalsIgnoreCase(rootCauseAnother)) {
                        causePosition = i;
                        break;
                    }
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
                Toast.makeText(context, i18n("Work Order TASK_ID not found"), Toast.LENGTH_SHORT).show();
                return;
            }

            String startTimeSecStr = convertDateTimeToUnix(etStart.getText().toString().trim());
            String endTimeSecStr = convertDateTimeToUnix(etEnd.getText().toString().trim());
            String currentSit = etCurrentStatus.getText().toString().trim();

            // Lưu English key vào DB (không lưu chuỗi đã dịch)
            int selectedCausePos = spinnerCause.getSelectedItemPosition();
            String selectedCause = "";
            if (selectedCausePos > 0 && selectedCausePos < rawCauseOptions.length) {
                selectedCause = rawCauseOptions[selectedCausePos];
            }

            String causeDetail = etCause.getText().toString().trim();
            String impact = etImpact.getText().toString().trim();
            String action = etProcessContent.getText().toString().trim();
            String noteVal = etNote.getText().toString().trim();
            int statusVal = spinnerStatus.getSelectedItemPosition();

            long actualTaskDateUnixVal = 0;
            try { actualTaskDateUnixVal = Long.parseLong(startTimeSecStr); } catch (NumberFormatException e) {}

            long approveTaskDateUnixVal = 0;
            try { approveTaskDateUnixVal = Long.parseLong(endTimeSecStr); } catch (NumberFormatException e) {}

            ConfigManager configManager = new ConfigManager(context);
            String serverDynamic = configManager.getProperty("server_dynamic_url");
            if (serverDynamic.isEmpty()) serverDynamic = "http://192.86.0.225:9101/api/dynamics";
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
                conditionObj.put("ROOT_CAUSE_ANOTHER", selectedCause); // English key
                conditionObj.put("ROOT_CAUSE", causeDetail);
                conditionObj.put("QUALITY_IMPACT", impact);
                conditionObj.put("ACTION_TAKEN", action);
                conditionObj.put("NOTE", noteVal);
                conditionObj.put("STATUS", statusVal);
                conditionObj.put("TASK_ID", taskId);
            } catch (Exception e) {
                Toast.makeText(context, i18n("System error while saving") + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(i18n("Saving..."));
            progressDialog.setCancelable(false);
            progressDialog.show();

            final String finalServerDynamic = serverDynamic;
            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.callDynamics(
                        context, finalServerDynamic, "mes_mms", "MES_TASK_UPDATE_WORK_ORDER_OTHER", conditionObj
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    if (result != null && result.code == 200) {
                        Toast.makeText(context, i18n("Data saved successfully"), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        clearState();
                        if (listener != null) {
                            listener.onSave(workOrder);
                        }
                    } else {
                        String errMsg = (result != null) ? result.message : i18n("No response from server");
                        Toast.makeText(context, i18n("Save failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
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
            if (clean.contains("T") && clean.length() >= 19) clean = clean.substring(0, 19);
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
            if (date != null) return String.valueOf(date.getTime() / 1000L);
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf24 = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date date = sdf24.parse(dateStr.trim());
                if (date != null) return String.valueOf(date.getTime() / 1000L);
            } catch (Exception e2) { /* ignore */ }
        }
        return "0";
    }

    private static String safeGet(JSONObject obj, String key) {
        if (obj == null) return "";
        String val = obj.optString(key, "").trim();
        return "null".equalsIgnoreCase(val) ? "" : val;
    }

    private static void downloadFile(Context context, String url, String fileName) {
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(i18n("Download file") + ": " + fileName);
            request.setDescription(i18n("Downloading attachment..."));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            PreferenceHandler handler = new PreferenceHandler(context);
            String token = handler.getString("api_key");
            if (token != null && !token.isEmpty()) {
                request.addRequestHeader("Authorization", "Bearer " + token);
            }

            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (fileExtension != null) {
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                if (mimeType != null) request.setMimeType(mimeType);
            }

            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toast.makeText(context, i18n("Starting file download..."), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, i18n("Cannot initialize Download Manager"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, i18n("File download error") + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(context, i18n("Cannot open link") + ": " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}