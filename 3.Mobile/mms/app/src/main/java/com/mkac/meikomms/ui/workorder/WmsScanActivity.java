package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

public class WmsScanActivity extends AppCompatActivity {

    private static final int SCAN_REQUEST_CODE = 1001;

    private TextView tvRequestIdHeader;
    private TextView tvWarehouseHeader;
    private TextView tvNoteHeader;
    private View layoutConfigWarning;
    private TextView tvConfigWarningMsg;
    private RecyclerView rcvMaterials;
    private ProgressBar progressLoading;
    private TextView tvFeedback;
    private View cardFeedback;
    private Button btnScanQr;
    private Button btnComplete;

    private String serverUrl = "";
    private String schemaWms = "";
    private String schemaCore = "";
    private String schemaMms = "";
    private String userLogin = "";

    private String requestId = "";
    private String whId = "";
    private String whName = "";
    private String note = "";
    private String transCode = ""; // "001" = Export, "002" = Import

    private List<JSONObject> materialList = new ArrayList<>();
    private MaterialAdapter adapter;

    private boolean isScanningEnabled = false;

    private MediaPlayer mpSuccess;
    private MediaPlayer mpError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wms_scan);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        LanguageAPIUtils.init(this);
        loadConfigurations();
        initIntentData();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvRequestIdHeader = findViewById(R.id.tv_info_request_id);
        tvWarehouseHeader = findViewById(R.id.tv_info_warehouse);
        tvNoteHeader = findViewById(R.id.tv_info_note);
        layoutConfigWarning = findViewById(R.id.layout_config_warning);
        tvConfigWarningMsg = findViewById(R.id.tv_config_warning_msg);
        rcvMaterials = findViewById(R.id.rcv_materials);
        progressLoading = findViewById(R.id.progress_loading);
        tvFeedback = findViewById(R.id.tv_feedback);
        cardFeedback = findViewById(R.id.card_feedback);
        btnScanQr = findViewById(R.id.btn_scan_qr);
        btnComplete = findViewById(R.id.btn_complete);

        // Set Info
        tvRequestIdHeader.setText("Mã phiếu: " + requestId);
        tvWarehouseHeader.setText("Kho: " + whName);
        tvNoteHeader.setText("Ghi chú: " + note);

        rcvMaterials.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MaterialAdapter();
        rcvMaterials.setAdapter(adapter);

        btnScanQr.setOnClickListener(v -> {
            if (!isScanningEnabled) {
                showConfigWarningDialog();
                return;
            }
            try {
                Class<?> barcodeClass = Class.forName("com.mkac.meikomms.common.Barcode");
                Intent intent = new Intent(WmsScanActivity.this, barcodeClass);
                startActivityForResult(intent, SCAN_REQUEST_CODE);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Không tìm thấy chức năng quét mã!", Toast.LENGTH_SHORT).show();
            }
        });

        btnComplete.setOnClickListener(v -> {
            int totalScanned = 0;
            for (JSONObject item : materialList) {
                totalScanned += item.optInt("Actual_Qty", 0);
            }
            if (totalScanned == 0) {
                Toast.makeText(this, "Vui lòng quét ít nhất một vật tư trước khi hoàn tất!", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmAndSubmitTransaction();
        });

        initSounds();
        checkSystemConfig();
        loadMaterialsList();

        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    private void initIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            requestId = intent.getStringExtra("Request_Id");
            whId = intent.getStringExtra("Wh_Id");
            whName = intent.getStringExtra("Wh_Name");
            note = intent.getStringExtra("Note");
            transCode = intent.getStringExtra("Trans_Code");
        }
    }

    private void loadConfigurations() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = handler.getString("server_url");
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = configManager.getProperty("server_url");
        }

        schemaWms = handler.getString("schema_wms");
        if (schemaWms == null || schemaWms.isEmpty()) {
            schemaWms = configManager.getProperty("schema_wms");
        }
        if (schemaWms == null || schemaWms.isEmpty()) {
            schemaWms = "MES_WMS";
        }

        schemaCore = handler.getString("schema_core");
        if (schemaCore == null || schemaCore.isEmpty()) {
            schemaCore = configManager.getProperty("schema_core");
        }
        if (schemaCore == null || schemaCore.isEmpty()) {
            schemaCore = "MES_CORE";
        }

        schemaMms = handler.getString("schema_mms");
        if (schemaMms == null || schemaMms.isEmpty()) {
            schemaMms = configManager.getProperty("schema_mms");
        }
        if (schemaMms == null || schemaMms.isEmpty()) {
            schemaMms = handler.getString("schema_data");
        }
        if (schemaMms == null || schemaMms.isEmpty()) {
            schemaMms = configManager.getProperty("schema_data");
        }
        if (schemaMms == null || schemaMms.isEmpty()) {
            schemaMms = "MES_MMS";
        }

        userLogin = handler.getString("Userlogin");
        if (userLogin == null || userLogin.isEmpty()) {
            JSONObject userProfile = handler.getJsonObject("user");
            if (userProfile != null) {
                userLogin = userProfile.optString("username", userProfile.optString("User_Name", ""));
            }
        }
    }

    private void initSounds() {
        try {
            mpSuccess = MediaPlayer.create(this, R.raw.beep_up);
            mpError = MediaPlayer.create(this, R.raw.error_plus);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playSuccessSound() {
        if (mpSuccess != null) {
            try {
                if (mpSuccess.isPlaying()) {
                    mpSuccess.stop();
                    mpSuccess.prepare();
                }
                mpSuccess.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void playErrorSound() {
        if (mpError != null) {
            try {
                if (mpError.isPlaying()) {
                    mpError.stop();
                    mpError.prepare();
                }
                mpError.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkSystemConfig() {
        new Thread(() -> {
            try {
                String baseUrl = serverUrl;
                if (baseUrl.contains("://")) {
                    String protocol = baseUrl.split("://")[0];
                    String addressWithPort = baseUrl.split("://")[1];
                    if (addressWithPort.contains(":")) {
                        baseUrl = protocol + "://" + addressWithPort.split(":")[0];
                    } else {
                        baseUrl = protocol + "://" + addressWithPort;
                    }
                }
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                String getConfigUrl = baseUrl + ":3500/api/v1/WMS_FE/getConfig";

                HttpClient.APIReturn rs = HttpClient.callPostRaw(this, getConfigUrl, new JSONObject());

                runOnUiThread(() -> {
                    boolean enabled = false;
                    String configKey = "001".equals(transCode) ? "WMS_ENABLE_EXPORT_QR" : "WMS_ENABLE_IMPORT_QR";

                    if (rs.code == 200 && rs.data != null) {
                        for (int i = 0; i < rs.data.size(); i++) {
                            JSONObject configObj = rs.data.get(i);
                            String key = configObj.optString("CONFIG_KEY", configObj.optString("Config_Key", configObj.optString("ConfigKey", "")));
                            String value = configObj.optString("CONFIG_VALUE", configObj.optString("Config_Value", configObj.optString("ConfigValue", "")));
                            if (configKey.equalsIgnoreCase(key)) {
                                enabled = "true".equalsIgnoreCase(value);
                                break;
                            }
                        }
                    }

                    isScanningEnabled = enabled;
                    if (!enabled) {
                        layoutConfigWarning.setVisibility(View.VISIBLE);
                        tvConfigWarningMsg.setText("Chức năng quét QR đối chiếu chưa được kích hoạt bởi quản trị viên!");
                        btnScanQr.setEnabled(false);
                        btnScanQr.setAlpha(0.5f);
                    } else {
                        layoutConfigWarning.setVisibility(View.GONE);
                        btnScanQr.setEnabled(true);
                        btnScanQr.setAlpha(1.0f);
                    }
                });

            } catch (Exception e) {
                Log.e("WMS_CONFIG_ERROR", e.getMessage());
                runOnUiThread(() -> {
                    isScanningEnabled = false;
                    layoutConfigWarning.setVisibility(View.VISIBLE);
                    tvConfigWarningMsg.setText("Lỗi kiểm tra quyền cấu hình từ hệ thống!");
                    btnScanQr.setEnabled(false);
                    btnScanQr.setAlpha(0.5f);
                });
            }
        }).start();
    }

    private void showConfigWarningDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cảnh báo cấu hình")
                .setMessage("Chức năng quét mã QR đối chiếu số lượng lệch chưa được quản trị viên bật trên hệ thống web. Vui lòng liên hệ quản trị viên để kích hoạt!")
                .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void loadMaterialsList() {
        progressLoading.setVisibility(View.VISIBLE);
        rcvMaterials.setVisibility(View.GONE);

        Log.d("WMS_SCAN_DBG", "loadMaterialsList -> serverUrl=" + serverUrl + ", schemaWms=" + schemaWms + ", schemaCore=" + schemaCore + ", schemaMms=" + schemaMms + ", requestId=" + requestId);

        new Thread(() -> {
            try {
                JSONObject condition = new JSONObject();
                condition.put("Schema_WMS", schemaWms);
                condition.put("Schema_Wms", schemaWms);
                condition.put("Schema_CORE", schemaCore);
                condition.put("Schema_Core", schemaCore);
                condition.put("Schema_MMS", schemaMms);
                condition.put("Schema_Mms", schemaMms);
                condition.put("Request_Id", requestId);
                condition.put("Request_ID", requestId);
                condition.put("REQUEST_ID", requestId);

                HttpClient.APIReturn rs = HttpClient.callDynamics(
                        WmsScanActivity.this,
                        serverUrl,
                        "mes_wms",
                        "MES_FE_GET_REQUEST_ORDER_DETAIL",
                        condition
                );

                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    rcvMaterials.setVisibility(View.VISIBLE);
                    materialList.clear();

                    if (rs.code == 200 && rs.data != null) {
                        for (int i = 0; i < rs.data.size(); i++) {
                            JSONObject item = rs.data.get(i);
                            try {
                                // Mặc định số lượng quét thực tế ban đầu = 0
                                item.put("Actual_Qty", 0);
                                item.put("Deviation_Qty", -item.optInt("Item_Qty", item.optInt("ITEM_QTY", 0)));
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            materialList.add(item);
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Log.e("WMS_SCAN_DBG", "loadMaterialsList failed -> code=" + rs.code + ", message=" + rs.message);
                        Toast.makeText(WmsScanActivity.this, "Lỗi tải chi tiết vật tư!", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("WMS_SCAN_DBG", "loadMaterialsList Exception", e);
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    Toast.makeText(WmsScanActivity.this, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String scannedCode = data.getStringExtra("barcode");
            if (scannedCode != null) {
                processScannedCode(scannedCode.trim());
            }
        }
    }

    private void processScannedCode(String code) {
        String itemId = code;
        int scanQty = 1;

        // Xử lý mã có định dạng chia tách ví dụ: MA|SL hoặc MA;SL
        String[] separators = {"|", ";", ","};
        for (String sep : separators) {
            if (code.contains(sep)) {
                String[] parts = code.split("\\" + sep);
                if (parts.length > 0) {
                    itemId = parts[0].trim();
                    if (parts.length > 1) {
                        try {
                            scanQty = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            scanQty = 1;
                        }
                    }
                }
                break;
            }
        }

        final String finalItemId = itemId;
        final int finalScanQty = scanQty;

        // Tìm tất cả các dòng khớp với mã vật tư
        final List<Integer> matchedIndices = new ArrayList<>();
        for (int i = 0; i < materialList.size(); i++) {
            JSONObject item = materialList.get(i);
            String itemCode = item.optString("ITEM_ID", item.optString("Item_Id", ""));
            if (itemCode.equalsIgnoreCase(itemId)) {
                matchedIndices.add(i);
            }
        }

        if (matchedIndices.isEmpty()) {
            // Cảnh báo không thuộc phiếu
            playErrorSound();
            cardFeedback.setBackgroundColor(Color.parseColor("#FADBD8"));
            tvFeedback.setTextColor(Color.parseColor("#C0392B"));
            tvFeedback.setText("Cảnh báo: Vật tư mã " + itemId + " không nằm trong phiếu yêu cầu này!");
            Toast.makeText(this, "Vật tư không thuộc phiếu yêu cầu này!", Toast.LENGTH_SHORT).show();
        } else if (matchedIndices.size() == 1) {
            // Chỉ có 1 dòng khớp, cập nhật trực tiếp
            updateMaterialQty(matchedIndices.get(0), itemId, scanQty);
        } else {
            // Có nhiều dòng khớp, hiển thị dialog chọn dòng
            String[] itemsText = new String[matchedIndices.size()];
            for (int i = 0; i < matchedIndices.size(); i++) {
                int actualIndex = matchedIndices.get(i);
                JSONObject item = materialList.get(actualIndex);
                int reqQty = item.optInt("Item_Qty", item.optInt("ITEM_QTY", 0));
                int actualQty = item.optInt("Actual_Qty", 0);
                String cell = item.optString("Cell", item.optString("CELL", ""));
                String createDate = item.optString("Create_Date", item.optString("CREATE_DATE", ""));
                String machine = item.optString("Machine_Id", item.optString("MACHINE_ID", ""));

                StringBuilder sb = new StringBuilder();
                sb.append("Dòng ").append(actualIndex + 1);
                sb.append(" | Yêu cầu: ").append(reqQty).append(" - Đã quét: ").append(actualQty);
                if (!cell.isEmpty() && !"null".equalsIgnoreCase(cell)) {
                    sb.append(" | Cell: ").append(cell);
                }
                if (!createDate.isEmpty() && !"null".equalsIgnoreCase(createDate)) {
                    sb.append(" | ").append(createDate);
                }
                if (!machine.isEmpty() && !"null".equalsIgnoreCase(machine)) {
                    sb.append(" | Máy: ").append(machine);
                }
                itemsText[i] = sb.toString();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Chọn dòng cập nhật cho: " + itemId)
                    .setItems(itemsText, (dialog, which) -> {
                        int targetIndex = matchedIndices.get(which);
                        updateMaterialQty(targetIndex, finalItemId, finalScanQty);
                    })
                    .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
        }
    }

    private void updateMaterialQty(int index, String itemId, int scanQty) {
        JSONObject item = materialList.get(index);
        try {
            int currentActual = item.optInt("Actual_Qty", 0);
            int reqQty = item.optInt("Item_Qty", item.optInt("ITEM_QTY", 0));
            int newActual = currentActual + scanQty;

            item.put("Actual_Qty", newActual);
            item.put("Deviation_Qty", newActual - reqQty);

            adapter.notifyItemChanged(index);

            // Cập nhật feedback thành công
            playSuccessSound();
            cardFeedback.setBackgroundColor(Color.parseColor("#EAFAF1"));
            tvFeedback.setTextColor(Color.parseColor("#27AE60"));
            String itemName = item.optString("ITEM_NAME", item.optString("Item_Name", "Vật tư"));
            tvFeedback.setText("Đã quét Dòng " + (index + 1) + ": " + itemId + " - " + itemName + " (Số lượng: +" + scanQty + ", Thực tế: " + newActual + "/" + reqQty + ")");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void confirmAndSubmitTransaction() {
        boolean hasDiscrepancy = false;
        for (JSONObject item : materialList) {
            int dev = item.optInt("Deviation_Qty", 0);
            if (dev != 0) {
                hasDiscrepancy = true;
                break;
            }
        }

        String confirmMsg = "Bạn có chắc chắn muốn thực hiện giao dịch cho phiếu " + requestId + "?";
        if (hasDiscrepancy) {
            confirmMsg = "⚠️ Cảnh báo: Có sự chênh lệch giữa số lượng thực tế quét và số lượng yêu cầu!\n\n" + confirmMsg;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hoàn tất")
                .setMessage(confirmMsg)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Đồng ý", (dialog, which) -> {
                    dialog.dismiss();
                    submitTransaction();
                })
                .show();
    }

    private void submitTransaction() {
        if (whId == null || whId.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Thiếu thông tin kho")
                    .setMessage("Không tìm thấy Mã kho (Wh_Id) của phiếu yêu cầu này. Vui lòng liên hệ Admin cập nhật câu SQL Action 'MES_FE_GET_REQUEST_LIST' trên server để trả về cột WH_ID!")
                    .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        if (userLogin == null || userLogin.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Thiếu tài khoản người dùng")
                    .setMessage("Không tìm thấy thông tin tài khoản người dùng đang đăng nhập. Vui lòng đăng nhập lại!")
                    .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        progressLoading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                // 1. Lấy count ID từ DB
                String timestamp = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
                JSONObject countCondition = new JSONObject();
                countCondition.put("Schema_WMS", schemaWms);
                countCondition.put("Timestamp", timestamp);

                String countAction = "001".equals(transCode) ? "MES_FE_EXPORT_GET_TRANS_ID_COUNT" : "MES_FE_IMPORT_TRANS_GET_TRANS_ID_COUNT";

                HttpClient.APIReturn countRs = HttpClient.callDynamics(
                        this,
                        serverUrl,
                        "mes_wms",
                        countAction,
                        countCondition
                );

                int count = 1;
                if (countRs.code == 200 && countRs.data != null && !countRs.data.isEmpty()) {
                    count = countRs.data.get(0).optInt("Id", countRs.data.get(0).optInt("ID", 1));
                }

                // Thêm hậu tố thời gian (HHmmss) để tránh trùng lặp mã giao dịch (lỗi duplicate key trên server)
                String timeSuffix = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
                String transId = "TRANS_" + transCode + "_" + timestamp + "_" + count + "_" + timeSuffix;

                // 2. Xây dựng danh sách vật tư đã quét
                JSONArray materialJsonList = new JSONArray();
                for (JSONObject m : materialList) {
                    int actual = m.optInt("Actual_Qty", 0);
                    if (actual > 0) {
                        JSONObject matObj = new JSONObject();
                        matObj.put("Item_Id", m.optString("ITEM_ID", m.optString("Item_Id", "")));
                        matObj.put("Item_Qty", actual);
                        matObj.put("Machine_Id", cleanNullString(m.optString("MACHINE_ID", m.optString("Machine_Id", ""))));
                        matObj.put("User_Export", userLogin);
                        matObj.put("Purpose", cleanNullString(m.optString("PURPOSE", m.optString("Purpose", ""))));
                        materialJsonList.put(matObj);
                    }
                }

                // 3. Xây dựng payload và gọi API tạo transaction
                JSONObject payload = new JSONObject();
                payload.put("Trans_Id", transId);
                payload.put("Trans_Code", transCode);
                payload.put("Trans_Note", ("001".equals(transCode) ? "Xuất" : "Nhập") + " kho quét QR phiếu " + requestId);
                payload.put("Request_Id", requestId);
                payload.put("Wh_Id", whId);
                payload.put("Trans_Date_Unix", (System.currentTimeMillis() / 1000));
                payload.put("User_Id", userLogin);
                payload.put("TransMaterialList", materialJsonList);

                String baseUrl = serverUrl;
                if (baseUrl.contains("://")) {
                    String protocol = baseUrl.split("://")[0];
                    String addressWithPort = baseUrl.split("://")[1];
                    if (addressWithPort.contains(":")) {
                        baseUrl = protocol + "://" + addressWithPort.split(":")[0];
                    } else {
                        baseUrl = protocol + "://" + addressWithPort;
                    }
                }
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                String createTransUrl = baseUrl + ":3500/api/v1/WMS_FE/createTrans";

                Log.d("WMS_PAYLOAD_DBG", "Payload: " + payload.toString());

                HttpClient.APIReturn submitRs = HttpClient.callPostRaw(this, createTransUrl, payload);

                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    if (submitRs.code == 200) {
                        Toast.makeText(this, "Hoàn tất giao dịch kho thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Log.e("WMS_SUBMIT_ERROR", "submitTransaction failed -> code=" + submitRs.code + ", message=" + submitRs.message);
                        new AlertDialog.Builder(this)
                                .setTitle("Lỗi lưu giao dịch")
                                .setMessage(submitRs.message != null ? submitRs.message : "Đã xảy ra lỗi khi hoàn tất phiếu!")
                                .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                                .show();
                    }
                });

            } catch (Exception e) {
                Log.e("WMS_SUBMIT_ERROR", "submitTransaction Exception", e);
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi kết nối hoặc cấu trúc gửi dữ liệu!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String cleanNullString(String str) {
        if (str == null || str.trim().isEmpty() || "null".equalsIgnoreCase(str.trim())) {
            return "";
        }
        return str.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mpSuccess != null) {
            mpSuccess.release();
            mpSuccess = null;
        }
        if (mpError != null) {
            mpError.release();
            mpError = null;
        }
    }

    // RecyclerView Adapter
    private class MaterialAdapter extends RecyclerView.Adapter<MaterialViewHolder> {

        @NonNull
        @Override
        public MaterialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wms_material, parent, false);
            return new MaterialViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MaterialViewHolder holder, int position) {
            JSONObject item = materialList.get(position);

            String materialCode = item.optString("ITEM_ID", item.optString("Item_Id", "-"));
            String materialName = item.optString("ITEM_NAME", item.optString("Item_Name", "-"));
            int reqQty = item.optInt("Item_Qty", item.optInt("ITEM_QTY", 0));
            int actualQty = item.optInt("Actual_Qty", 0);
            int deviationQty = item.optInt("Deviation_Qty", 0);

            holder.tvNo.setText(String.valueOf(position + 1));
            holder.tvMaterialCode.setText(materialCode);
            holder.tvMaterialName.setText(materialName);
            holder.tvReqQty.setText(String.valueOf(reqQty));
            holder.tvActualQty.setText(String.valueOf(actualQty));
            holder.tvDeviationQty.setText((deviationQty > 0 ? "+" : "") + deviationQty);

            // Set colors for Actual and Deviation quantities
            if (actualQty > 0) {
                holder.tvActualQty.setTextColor(Color.parseColor("#31BFA6"));
            } else {
                holder.tvActualQty.setTextColor(Color.parseColor("#727272"));
            }

            if (deviationQty == 0) {
                holder.tvDeviationQty.setTextColor(Color.parseColor("#03BD57")); // Green
            } else {
                holder.tvDeviationQty.setTextColor(Color.parseColor("#ff6347")); // Red
            }
        }

        @Override
        public int getItemCount() {
            return materialList.size();
        }
    }

    private static class MaterialViewHolder extends RecyclerView.ViewHolder {
        TextView tvNo, tvMaterialCode, tvMaterialName, tvReqQty, tvActualQty, tvDeviationQty;

        public MaterialViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNo = itemView.findViewById(R.id.tv_no);
            tvMaterialCode = itemView.findViewById(R.id.tv_material_code);
            tvMaterialName = itemView.findViewById(R.id.tv_material_name);
            tvReqQty = itemView.findViewById(R.id.tv_req_qty);
            tvActualQty = itemView.findViewById(R.id.tv_actual_qty);
            tvDeviationQty = itemView.findViewById(R.id.tv_deviation_qty);
        }
    }
}
