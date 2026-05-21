package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class WorkOrderEntryDialogHelper {

    public interface OnSaveListener {
        void onSave(JSONObject workOrderData);
    }

    private WorkOrderEntryDialogHelper() {}

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
        String actualTaskDateUnix = safeGet(workOrder, "ACTUAL_TASK_DATE_UNIX");
        if (actualTaskDateUnix.isEmpty()) actualTaskDateUnix = safeGet(workOrder, "Actual_Task_Date_Unix");
        etStart.setText(formatUnixTimestamp(actualTaskDateUnix));

        String approveTaskDateUnix = safeGet(workOrder, "APPROVE_TASK_DATE_UNIX");
        if (approveTaskDateUnix.isEmpty()) approveTaskDateUnix = safeGet(workOrder, "Approve_Task_Date_Unix");
        etEnd.setText(formatUnixTimestamp(approveTaskDateUnix));

        String currentSituation = safeGet(workOrder, "CURRENT_SITUATION");
        if (currentSituation.isEmpty()) currentSituation = safeGet(workOrder, "Current_Situation");
        etCurrentStatus.setText(currentSituation);

        String rootCause = safeGet(workOrder, "ROOT_CAUSE");
        if (rootCause.isEmpty()) rootCause = safeGet(workOrder, "Root_Cause");
        etCause.setText(rootCause);

        String qualityImpact = safeGet(workOrder, "QUALITY_IMPACT");
        if (qualityImpact.isEmpty()) qualityImpact = safeGet(workOrder, "Quality_Impact");
        etImpact.setText(qualityImpact);

        String actionTaken = safeGet(workOrder, "ACTION_TAKEN");
        if (actionTaken.isEmpty()) actionTaken = safeGet(workOrder, "Action_Taken");
        if (actionTaken.isEmpty()) actionTaken = safeGet(workOrder, "Request_Reason");
        etProcessContent.setText(actionTaken);

        String note = safeGet(workOrder, "NOTE");
        if (note.isEmpty()) note = safeGet(workOrder, "Note");
        etNote.setText(note);

        String rootCauseAnother = safeGet(workOrder, "ROOT_CAUSE_ANOTHER");
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

        String statusStr = safeGet(workOrder, "STATUS");
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

        dialogView.findViewById(R.id.btn_dialog_close).setOnClickListener(v -> dialog.dismiss());
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
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
            return out.format(in.parse(raw.replace("Z", "")));
        } catch (Exception e) {
            return raw;
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
        return obj.optString(key, "").trim();
    }
}
