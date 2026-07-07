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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

//    private static Uri selectedFileUri = null;
    private static List<Uri> selectedFileUris = new ArrayList<>();
    // Danh sách file đã tải lên server (tên file đầy đủ, lấy từ FILE_WO / kết quả upload)
    private static List<String> uploadedFileNames = new ArrayList<>();
    private static String currentTaskId = "";
    // Hai khối tài liệu MA / FE (giống web)
    private static TextView docMaEmptyView = null;
    private static LinearLayout docMaListView = null;
    private static TextView docFeEmptyView = null;
    private static LinearLayout docFeListView = null;
    private static boolean currentUserIsFe = true; // bộ phận tài khoản đăng nhập
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

    // Mã trạng thái WO thực tế (khớp getStatusType ở ListWorkOrderActivity) tương ứng
    // từng vị trí trong R.array.work_order_entry_status:
    //   vị trí 0 = "Chưa hoàn thành" -> 0
    //   vị trí 1 = "Đã thực hiện"    -> 1
    //   vị trí 2 = "Quá hạn"         -> 5
    // Trước đây lưu thẳng vị trí spinner (0,1,2) nên "Quá hạn" bị lưu 2 -> sai trạng thái.
    private static final int[] ENTRY_STATUS_CODES = {0, 1, 5};
//    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == 9999 && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
//            selectedFileUri = data.getData();
//            if (tvAttachmentStatusView != null && dialogContext != null) {
//                String fileName = getFileName(dialogContext, selectedFileUri);
//                tvAttachmentStatusView.setText(fileName);
//                tvAttachmentStatusView.setPaintFlags(tvAttachmentStatusView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
//                if (imgAttachmentIconView != null) {
//                    imgAttachmentIconView.setImageResource(R.drawable.ic_paperclip);
//                }
//            }
//        }
//    }

    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 9999 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            selectedFileUris.clear();

            // Trường hợp chọn nhiều file cùng lúc
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    selectedFileUris.add(data.getClipData().getItemAt(i).getUri());
                }
            }
            // Trường hợp chỉ chọn 1 file
            else if (data.getData() != null) {
                selectedFileUris.add(data.getData());
            }

            // Vẽ lại danh sách đính kèm (file đã chọn nhưng chưa tải lên)
            renderAttachmentList(dialogContext);
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

    /** Tách chuỗi FILE_WO (phân tách bằng dấu phẩy) thành danh sách tên file. */
    private static List<String> parseFileWo(String fileWo) {
        List<String> files = new ArrayList<>();
        if (fileWo == null) return files;
        for (String part : fileWo.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                files.add(name);
            }
        }
        return files;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Vẽ lại khung tài liệu đính kèm: mỗi file là 1 dòng kèm nút (X) để gỡ.
     * - File đã tải lên server (uploadedFileNames): nút X gọi API xóa trên server.
     * - File mới chọn nhưng chưa tải lên (selectedFileUris): nút X chỉ gỡ ở local.
     */
    /** File thuộc bộ phận FE nếu tên (sau tiền tố {taskId}_) bắt đầu bằng "FE_"; còn lại coi là MA. */
    private static boolean isFeFile(String fileName) {
        if (fileName == null) return false;
        String rest = fileName;
        if (currentTaskId != null && !currentTaskId.isEmpty() && rest.startsWith(currentTaskId + "_")) {
            rest = rest.substring(currentTaskId.length() + 1);
        }
        return rest.startsWith("FE_");
    }

    /** Bộ phận của tài khoản đăng nhập (ưu tiên cờ hub lưu; fallback đọc prefs). Mặc định FE. */
    private static boolean isFeAccount(Context ctx) {
        try {
            PreferenceHandler ph = new PreferenceHandler(ctx);
            String saved = ph.getString("wo_user_division");
            if ("FE".equalsIgnoreCase(saved)) return true;
            if ("MA".equalsIgnoreCase(saved)) return false;
            JSONObject u = ph.getJsonObject("user");
            if (u != null) {
                for (String k : new String[]{"Division_Id", "divisionId", "DIVISION_ID"}) {
                    if ("006".equals(u.optString(k, "").trim())) return true;
                }
                for (String k : new String[]{"departmentCode", "Department_Code", "divisionCode",
                        "divisionName", "Division_Name", "DIVISION_NAME"}) {
                    String v = u.optString(k, "").trim().toUpperCase(Locale.ROOT);
                    if (v.isEmpty()) continue;
                    if (v.contains("FE")) return true;
                    if (v.contains("MA")) return false;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static void renderAttachmentList(Context context) {
        renderDocBlock(context, docMaListView, docMaEmptyView, "MA");
        renderDocBlock(context, docFeListView, docFeEmptyView, "FE");
    }

    private static void renderDocBlock(Context context, LinearLayout listView, TextView emptyView, String division) {
        if (context == null || listView == null) return;
        listView.removeAllViews();

        boolean isFeBlock = "FE".equals(division);
        List<String> uploaded = new ArrayList<>();
        for (String f : uploadedFileNames) {
            if (isFeFile(f) == isFeBlock) uploaded.add(f);
        }
        // File mới chọn (chưa upload) hiển thị ở khối đúng bộ phận tài khoản đang đăng nhập.
        boolean pendingHere = (isFeBlock == currentUserIsFe);
        boolean hasAny = !uploaded.isEmpty() || (pendingHere && !selectedFileUris.isEmpty());

        if (!hasAny) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);

        // Giống web: màn nhập kết quả xóa được file ở CẢ 2 khối, nhưng dùng scope "FE" (bản làm việc)
        // -> chỉ gỡ khỏi FILE_WO, KHÔNG đụng kho gốc FILE_WO_MA/FILE_WO_FE -> không ảnh hưởng màn Chỉnh sửa WO.
        for (final String fileName : uploaded) {
            listView.addView(buildAttachmentRow(context, fileName, true, null, fileName, "FE", true));
        }
        if (pendingHere) {
            for (final Uri uri : new ArrayList<>(selectedFileUris)) {
                listView.addView(buildAttachmentRow(context, getFileName(context, uri), false, uri, null, "FE", true));
            }
        }
    }

    /**
     * Tạo 1 dòng hiển thị file: [icon] tên file (bấm để tải về nếu đã upload) ... [X]
     */
    private static View buildAttachmentRow(final Context context, String displayName,
                                           final boolean isUploaded, final Uri localUri,
                                           final String serverFileName, final String scope,
                                           final boolean canDelete) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(context, 2), 0, dp(context, 2));
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
        iconLp.setMarginEnd(dp(context, 8));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_paperclip);
        row.addView(icon);

        TextView tvName = new TextView(context);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLp);
        tvName.setText(displayName);
        tvName.setTextColor(Color.parseColor("#333333"));
        tvName.setTextSize(13);
        tvName.setPaintFlags(tvName.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (isUploaded) {
            tvName.setOnClickListener(v -> downloadUploadedFile(context, serverFileName));
        }
        row.addView(tvName);

        // Nút xóa: file đã upload chỉ hiện khi được phép (đúng bộ phận); file mới chọn luôn gỡ được.
        if (canDelete) {
            ImageView btnRemove = new ImageView(context);
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22));
            removeLp.setMarginStart(dp(context, 8));
            btnRemove.setLayoutParams(removeLp);
            btnRemove.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
            btnRemove.setImageResource(R.drawable.close_24);
            btnRemove.setClickable(true);
            btnRemove.setFocusable(true);
            btnRemove.setOnClickListener(v -> {
                if (isUploaded) {
                    confirmAndDeleteUploadedFile(context, serverFileName, scope);
                } else {
                    selectedFileUris.remove(localUri);
                    renderAttachmentList(context);
                }
            });
            row.addView(btnRemove);
        }

        return row;
    }

    /** Hỏi xác nhận rồi gọi API xóa file đã tải lên trên server. */
    private static void confirmAndDeleteUploadedFile(final Context context, final String serverFileName, final String scope) {
        new AlertDialog.Builder(context)
                .setTitle(i18n("Remove attachment"))
                .setMessage(i18n("Are you sure you want to remove this file?") + "\n" + serverFileName)
                .setNegativeButton(i18n("Cancel"), null)
                .setPositiveButton(i18n("Remove"), (d, w) -> deleteUploadedFile(context, serverFileName, scope))
                .show();
    }

    private static void deleteUploadedFile(final Context context, final String serverFileName, final String scope) {
        if (currentTaskId == null || currentTaskId.isEmpty()) {
            Toast.makeText(context, i18n("Work Order code not found for upload"), Toast.LENGTH_SHORT).show();
            return;
        }

        ConfigManager config = new ConfigManager(context);
        String serverDynamicUrl = config.getProperty("server_dynamic_url");
        if (serverDynamicUrl.isEmpty()) {
            serverDynamicUrl = "http://192.86.0.225:9101/api/dynamics";
        }

        final ProgressDialog progress = new ProgressDialog(context);
        progress.setMessage(i18n("Removing attachment..."));
        progress.setCancelable(false);
        progress.show();

        final String finalServerUrl = serverDynamicUrl;
        new Thread(() -> {
            HttpClient.APIReturn result = HttpClient.deleteWorkOrderFile(
                    context, finalServerUrl, currentTaskId, serverFileName, scope);

            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if (result != null && result.code == 200) {
                    uploadedFileNames.remove(serverFileName);
                    renderAttachmentList(context);
                    Toast.makeText(context, i18n("Attachment removed"), Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = (result != null) ? result.message : i18n("No response from server");
                    Toast.makeText(context, i18n("Remove failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static void downloadUploadedFile(Context context, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
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
        String downloadUrl = finalUrl + ":9101/api/v1/mms_file-img/" + fileName;
        downloadFile(context, downloadUrl, fileName);
    }

    private static void clearState() {
//        selectedFileUri = null;
        selectedFileUris.clear();
        uploadedFileNames.clear();
        currentTaskId = "";
        docMaEmptyView = null;
        docMaListView = null;
        docFeEmptyView = null;
        docFeListView = null;
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

        RadioGroup rgDailyReport = dialogView.findViewById(R.id.rg_daily_report);
        RadioButton rbDailyReportNo = dialogView.findViewById(R.id.rb_daily_report_no);
        LinearLayout layoutDailyReportReason = dialogView.findViewById(R.id.layout_daily_report_reason);
        EditText etDailyReportReason = dialogView.findViewById(R.id.et_daily_report_reason);

        TextView btnTabInfo = dialogView.findViewById(R.id.btn_tab_info);
        TextView btnTabMaterials = dialogView.findViewById(R.id.btn_tab_materials);
        LinearLayout layoutTabInfoContent = dialogView.findViewById(R.id.layout_tab_info_content);
        LinearLayout layoutTabMaterialsContent = dialogView.findViewById(R.id.layout_tab_materials_content);

        View btnChooseFile = dialogView.findViewById(R.id.btn_choose_file);
        View btnUploadFile = dialogView.findViewById(R.id.btn_upload_file);
        docMaEmptyView = dialogView.findViewById(R.id.tv_doc_ma_empty);
        docMaListView = dialogView.findViewById(R.id.layout_doc_ma_list);
        docFeEmptyView = dialogView.findViewById(R.id.tv_doc_fe_empty);
        docFeListView = dialogView.findViewById(R.id.layout_doc_fe_list);
        currentUserIsFe = isFeAccount(context);

        dialogContext = context;
//        selectedFileUri = null;
        selectedFileUris.clear();
        uploadedFileNames.clear();

        // taskId dùng cho upload/xóa file chính là WO_CODE
        currentTaskId = safeGet(workOrder, "WO_CODE");
        if (currentTaskId.isEmpty()) currentTaskId = safeGet(workOrder, "Wo_Code");
        if (currentTaskId.isEmpty()) currentTaskId = safeGet(workOrder, "TASK_ID");
        if (currentTaskId.isEmpty()) currentTaskId = safeGet(workOrder, "Task_Id");
        if (currentTaskId.isEmpty()) currentTaskId = safeGet(workOrder, "taskId");
        if (currentTaskId.isEmpty()) currentTaskId = safeGet(workOrder, "Task_ID");

        String existingFile = safeGet(workOrder, "File_Wo");
        if (existingFile.isEmpty()) existingFile = safeGet(workOrder, "FILE_WO");
        uploadedFileNames.addAll(parseFileWo(existingFile));

        renderAttachmentList(context);

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
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).startActivityForResult(
                        Intent.createChooser(intent, i18n("Select one or more photos")), 9999);
            }
        });

        btnUploadFile.setOnClickListener(v -> {
            if (selectedFileUris.isEmpty()) {
                Toast.makeText(context, i18n("Please select a file before uploading"), Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentTaskId.isEmpty()) {
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

            final String finalTaskId = currentTaskId;
            final String finalServerDynamicUrl = serverDynamicUrl;
            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.uploadWorkOrderFile(
                        context,
                        finalServerDynamicUrl,
                        finalTaskId,
//                        selectedFileUri
                        selectedFileUris,
                        // Upload vào kho tài liệu đúng bộ phận tài khoản đăng nhập (FE->FE, MA->MA).
                        currentUserIsFe ? "FE" : "MA"
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    uploadProgress.dismiss();

                    if (result != null && result.code == 200 && result.data != null && !result.data.isEmpty()) {
                        // Server trả về toàn bộ danh sách file (cũ + mới) của work order
                        List<String> serverFiles = new ArrayList<>();
                        for (JSONObject item : result.data) {
                            if (item == null) continue;
                            String fileVal = item.optString("value");
                            if (!fileVal.isEmpty()) serverFiles.add(fileVal);
                        }
                        if (!serverFiles.isEmpty()) {
                            uploadedFileNames.clear();
                            uploadedFileNames.addAll(serverFiles);
                            selectedFileUris.clear();
                            renderAttachmentList(context);
                            Toast.makeText(context, i18n("Photos uploaded successfully"), Toast.LENGTH_SHORT).show();
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

        PreferenceHandler initialPrefHandler = new PreferenceHandler(context);
        String initialWoCode = safeGet(workOrder, "WO_CODE");
        if (initialWoCode.isEmpty()) initialWoCode = safeGet(workOrder, "Wo_Code");
        
        String isWarehouseReqVal = safeGet(workOrder, "IS_WAREHOUSE_REQUEST");
        if (isWarehouseReqVal.isEmpty()) isWarehouseReqVal = safeGet(workOrder, "Is_Warehouse_Request");
        if (isWarehouseReqVal.isEmpty()) isWarehouseReqVal = safeGet(workOrder, "is_warehouse_request");

        boolean isAlreadyCreated = initialPrefHandler.getBoolean("wms_request_created_" + initialWoCode)
                || "1".equals(isWarehouseReqVal);

        if (isAlreadyCreated) {
            btnCreateWmsRequest.setEnabled(false);
            btnCreateWmsRequest.setClickable(false);
            btnCreateWmsRequest.setAlpha(0.35f);
        }

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
            final String finalWoCodeVal = woCodeVal;

            String tempTaskId = safeGet(workOrder, "TASK_ID");
            if (tempTaskId.isEmpty()) tempTaskId = safeGet(workOrder, "Task_Id");
            if (tempTaskId.isEmpty()) tempTaskId = safeGet(workOrder, "taskId");
            if (tempTaskId.isEmpty()) tempTaskId = safeGet(workOrder, "Task_ID");
            final String finalTaskIdVal = tempTaskId.isEmpty() ? woCodeVal : tempTaskId;

            new Thread(() -> {
                HttpClient.APIReturn result = HttpClient.save_request_material(
                        context, finalRequestDateUnix, "FE", finalRequestNote, finalMaterials,
                        finalServerUrl, finalUsername, finalRequestPurpose, finalMachineId
                );

                if (result != null && result.code == 200) {
                    try {
                        ConfigManager configManager = new ConfigManager(context);
                        String schemaMms = configManager.getProperty("schema_mms");
                        if (schemaMms.isEmpty()) schemaMms = "MES_MMS_MKHC";

                        JSONObject updateCondition = new JSONObject();
                        updateCondition.put("Schema_Mms", schemaMms);
                        updateCondition.put("taskId", finalTaskIdVal);

                        HttpClient.callDynamics(
                                context, finalServerUrl, "mes_mms", "UPDATE_TASK_WAREHOUSE_REQUEST", updateCondition
                        );
                    } catch (Exception e) {
                        android.util.Log.e("WMS_UPDATE", "Error updating task warehouse request", e);
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    wmsProgress.dismiss();
                    if (result != null && result.code == 200) {
                        Toast.makeText(context, i18n("Warehouse release request created successfully!"), Toast.LENGTH_SHORT).show();
                        btnCreateWmsRequest.setEnabled(false);
                        btnCreateWmsRequest.setClickable(false);
                        btnCreateWmsRequest.setAlpha(0.35f);
                        prefHandler.setBoolean("wms_request_created_" + finalWoCodeVal, true);
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
            try {
                int parsed = Integer.parseInt(rootCauseAnother);
                if (parsed > 0 && parsed < rawCauseOptions.length) {
                    causePosition = parsed;
                }
            } catch (NumberFormatException e) {
                // Bước 1: so khớp English key (cách lưu chuẩn cũ)
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
        }
        spinnerCause.setSelection(causePosition);

        String statusStr = safeGet(workOrder, "Status_1");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "STATUS_1");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "STATUS");
        if (statusStr.isEmpty()) statusStr = safeGet(workOrder, "Status");
        int statusPosition = 0;
        try {
            if (!statusStr.isEmpty()) {
                // DB lưu mã trạng thái thực (0/1/5) -> ánh xạ ngược về vị trí spinner.
                int statusCode = (int) Double.parseDouble(statusStr);
                for (int i = 0; i < ENTRY_STATUS_CODES.length; i++) {
                    if (ENTRY_STATUS_CODES[i] == statusCode) {
                        statusPosition = i;
                        break;
                    }
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

        // =========================================================================
        // Daily Report Declaration (chỉ hiển thị ở màn nhập kết quả WO của tài khoản FE)
        // =========================================================================
        String isDailyReportStr = safeGet(workOrder, "Is_Daily_Report");
        if (isDailyReportStr.isEmpty()) isDailyReportStr = safeGet(workOrder, "IS_DAILY_REPORT");
        int isDailyReportVal = 1; // Mặc định "Có" giống web
        try {
            if (!isDailyReportStr.isEmpty()) {
                isDailyReportVal = (int) Double.parseDouble(isDailyReportStr);
            }
        } catch (NumberFormatException e) { /* giữ mặc định 1 */ }

        String dailyReportReasonStr = safeGet(workOrder, "Daily_Report_Reason");
        if (dailyReportReasonStr.isEmpty()) dailyReportReasonStr = safeGet(workOrder, "DAILY_REPORT_REASON");
        etDailyReportReason.setText(dailyReportReasonStr);

        if (isDailyReportVal == 0) {
            rbDailyReportNo.setChecked(true);
            layoutDailyReportReason.setVisibility(View.VISIBLE);
        } else {
            layoutDailyReportReason.setVisibility(View.GONE);
        }

        rgDailyReport.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_daily_report_no) {
                layoutDailyReportReason.setVisibility(View.VISIBLE);
            } else {
                layoutDailyReportReason.setVisibility(View.GONE);
            }
        });

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

            // Lưu giá trị từ 1-8 thay vì chuỗi
            int selectedCausePos = spinnerCause.getSelectedItemPosition();
            String selectedCause = "";
            if (selectedCausePos > 0 && selectedCausePos < rawCauseOptions.length) {
                selectedCause = String.valueOf(selectedCausePos);
            }

            String causeDetail = etCause.getText().toString().trim();
            String impact = etImpact.getText().toString().trim();
            String action = etProcessContent.getText().toString().trim();
            String noteVal = etNote.getText().toString().trim();
            // Ánh xạ vị trí spinner -> mã trạng thái thực (0/1/5) để không lưu sai "Quá hạn".
            int statusPos = spinnerStatus.getSelectedItemPosition();
            int statusVal = (statusPos >= 0 && statusPos < ENTRY_STATUS_CODES.length)
                    ? ENTRY_STATUS_CODES[statusPos] : 0;

            // Daily report: 0 = No (bắt buộc nhập lý do), 1 = Yes
            int dailyReportFlag = rbDailyReportNo.isChecked() ? 0 : 1;
            String dailyReportReason = dailyReportFlag == 0
                    ? etDailyReportReason.getText().toString().trim() : "";
            if (dailyReportFlag == 0 && dailyReportReason.isEmpty()) {
                Toast.makeText(context,
                        i18n("Lý do không khai báo Daily report") + " " + i18n("is required"),
                        Toast.LENGTH_LONG).show();
                return;
            }

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
                conditionObj.put("IS_DAILY_REPORT", dailyReportFlag);
                conditionObj.put("DAILY_REPORT_REASON", dailyReportReason);
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
            // Giới hạn chiều cao dialog để vùng nội dung cuộn được và 2 nút Đóng/Lưu
            // luôn hiển thị (tránh bị đẩy khuất khi form dài).
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int) (context.getResources().getDisplayMetrics().heightPixels * 0.9));
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