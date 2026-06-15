package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import java.util.ArrayList;
import java.util.List;

public class WmsRequestListActivity extends AppCompatActivity {

    private TextView tabExport;
    private TextView tabImport;
    private EditText edtSearch;
    private RecyclerView rcvRequests;
    private ProgressBar progressLoading;
    private TextView tvEmpty;
    private TextView tvUsername;
    private ImageView imgUserProfile;

    private String serverUrl = "";
    private String schemaWms = "";
    private String userLogin = "";
    private boolean isExportMode = true; // true = Export, false = Import

    private List<JSONObject> originalList = new ArrayList<>();
    private List<JSONObject> filteredList = new ArrayList<>();
    private RequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wms_request_list);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        LanguageAPIUtils.init(this);
        loadConfigurations();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvUsername = findViewById(R.id.tv_username_display);
        imgUserProfile = findViewById(R.id.img_user_profile);
        tabExport = findViewById(R.id.tab_export);
        tabImport = findViewById(R.id.tab_import);
        edtSearch = findViewById(R.id.edt_search);
        rcvRequests = findViewById(R.id.rcv_requests);
        progressLoading = findViewById(R.id.progress_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        displayUserInfo();

        rcvRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestAdapter();
        rcvRequests.setAdapter(adapter);

        tabExport.setOnClickListener(v -> {
            if (!isExportMode) {
                isExportMode = true;
                updateTabState();
                loadRequestList();
            }
        });

        tabImport.setOnClickListener(v -> {
            if (isExportMode) {
                isExportMode = false;
                updateTabState();
                loadRequestList();
            }
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (imgUserProfile != null) {
            imgUserProfile.setOnClickListener(v -> showLogoutConfirmationDialog());
        }

        updateTabState();
        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        displayUserInfo();
        loadRequestList();
        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
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

        userLogin = handler.getString("Userlogin");
        if (userLogin == null || userLogin.isEmpty()) {
            JSONObject userProfile = handler.getJsonObject("user");
            if (userProfile != null) {
                userLogin = userProfile.optString("username", userProfile.optString("User_Name", ""));
            }
        }
    }

    private void displayUserInfo() {
        if (tvUsername == null) return;
        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");
        String accountName = "";
        if (userObj != null) {
            accountName = userObj.optString("User_Name", userObj.optString("username", userObj.optString("Full_Name", ""))).trim();
        }
        if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
            accountName = handler.getString("Userlogin").trim();
        }
        if (accountName.isEmpty()) {
            accountName = "WMS_User";
        }
        tvUsername.setText(accountName);
    }

    private void updateTabState() {
        if (isExportMode) {
            tabExport.setBackgroundColor(getResources().getColor(R.color.blue));
            tabExport.setTextColor(Color.WHITE);
            tabImport.setBackgroundColor(Color.WHITE);
            tabImport.setTextColor(Color.BLACK);
        } else {
            tabImport.setBackgroundColor(getResources().getColor(R.color.blue));
            tabImport.setTextColor(Color.WHITE);
            tabExport.setBackgroundColor(Color.WHITE);
            tabExport.setTextColor(Color.BLACK);
        }
    }

    private void loadRequestList() {
        progressLoading.setVisibility(View.VISIBLE);
        rcvRequests.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                JSONObject condition = new JSONObject();
                condition.put("Schema_WMS", schemaWms);
                condition.put("Trans_Code", isExportMode ? "001" : "002");

                HttpClient.APIReturn rs = HttpClient.callDynamics(
                        WmsRequestListActivity.this,
                        serverUrl,
                        "mes_wms",
                        "MES_FE_GET_REQUEST_LIST",
                        condition
                );

                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    originalList.clear();
                    if (rs.code == 200 && rs.data != null) {
                        for (int i = 0; i < rs.data.size(); i++) {
                            originalList.add(rs.data.get(i));
                        }
                        filterList(edtSearch.getText().toString());
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                        Toast.makeText(WmsRequestListActivity.this, "Lỗi lấy danh sách phiếu!", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("WMS_LIST_ERROR", e.getMessage());
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(WmsRequestListActivity.this, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void filterList(String text) {
        filteredList.clear();
        String query = text.toLowerCase().trim();
        for (JSONObject item : originalList) {
            String requestId = item.optString("REQUEST_ID", item.optString("Request_Id", "")).toLowerCase();
            if (requestId.contains(query)) {
                filteredList.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rcvRequests.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rcvRequests.setVisibility(View.VISIBLE);
        }
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(LanguageAPIUtils.i18n("Logout"))
                .setMessage(LanguageAPIUtils.i18n("Do you want to log out?"))
                .setCancelable(true)
                .setPositiveButton(LanguageAPIUtils.i18n("Logout"), (dialog, which) -> executeLogoutAction())
                .setNegativeButton(LanguageAPIUtils.i18n("Cancel"), (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void executeLogoutAction() {
        try {
            PreferenceHandler handler = new PreferenceHandler(this);
            handler.setBoolean("isLoggedIn", false);
            handler.remove("user");
            handler.remove("api_key");

            Toast.makeText(this, LanguageAPIUtils.i18n("Logout") + " " + LanguageAPIUtils.i18n("Success"), Toast.LENGTH_SHORT).show();

            Class<?> loginActivityClass = Class.forName("com.mkac.meikomms.ui.login.LoginActivity");
            Intent intent = new Intent(this, loginActivityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // RecyclerView Adapter
    private class RequestAdapter extends RecyclerView.Adapter<RequestViewHolder> {

        @NonNull
        @Override
        public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wms_request, parent, false);
            return new RequestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
            JSONObject item = filteredList.get(position);

            String requestId = item.optString("REQUEST_ID", item.optString("Request_Id", "-"));
            String requestDate = item.optString("REQUEST_DATE", item.optString("Request_Date", "-"));
            if (requestDate.contains("T")) {
                requestDate = requestDate.split("T")[0];
            }
            String warehouseName = item.optString("WH_NAME", item.optString("Wh_Name", item.optString("WH_ID", "-")));
            String note = item.optString("NOTE", item.optString("Note", "-"));
            String statusStr = item.optString("STATUS", item.optString("Status", "0"));

            holder.tvRequestId.setText(requestId);
            holder.tvRequestDate.setText(requestDate);
            holder.tvWarehouse.setText(warehouseName);
            holder.tvNote.setText(note);

            // Set status tag
            // 0: Chờ xử lý, 1: Hoàn thành, 2: Lệch số lượng, 3: Đã hủy
            switch (statusStr) {
                case "1":
                    holder.tvStatusBadge.setText("Hoàn thành");
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#D1F2D9"));
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#0E622B"));
                    break;
                case "2":
                    holder.tvStatusBadge.setText("Lệch số lượng");
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFD9D9"));
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#C20F0F"));
                    break;
                case "3":
                    holder.tvStatusBadge.setText("Đã hủy");
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#E5E7EB"));
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#374151"));
                    break;
                default:
                    holder.tvStatusBadge.setText("Chờ xử lý");
                    holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFF3CD"));
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#856404"));
                    break;
            }

            holder.itemView.setOnClickListener(v -> {
                if ("3".equals(statusStr)) {
                    Toast.makeText(WmsRequestListActivity.this, "Phiếu này đã bị hủy!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(WmsRequestListActivity.this, WmsScanActivity.class);
                intent.putExtra("Request_Id", requestId);
                intent.putExtra("Wh_Id", item.optString("WH_ID", item.optString("Wh_Id", "")));
                intent.putExtra("Wh_Name", warehouseName);
                intent.putExtra("Note", note);
                intent.putExtra("Trans_Code", isExportMode ? "001" : "002");
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }
    }

    private static class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvRequestId, tvStatusBadge, tvRequestDate, tvWarehouse, tvNote;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRequestId = itemView.findViewById(R.id.tv_request_id);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvRequestDate = itemView.findViewById(R.id.tv_request_date);
            tvWarehouse = itemView.findViewById(R.id.tv_warehouse);
            tvNote = itemView.findViewById(R.id.tv_note);
        }
    }
}
