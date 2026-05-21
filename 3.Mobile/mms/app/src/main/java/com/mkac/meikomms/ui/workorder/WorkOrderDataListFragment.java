package com.mkac.meikomms.ui.workorder;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkOrderDataListFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private WorkOrderDataAdapter adapter;
    private String serverDynamic = "";
    private String schemaData = "";
    private String schemaCore = "";
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_work_order_data_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initConfiguration();
        swipeRefresh = view.findViewById(R.id.swipe_refresh_work_order);
        recyclerView = view.findViewById(R.id.rv_work_order_data);

        adapter = new WorkOrderDataAdapter(item -> {
            if (getContext() != null) {
                WorkOrderEntryDialogHelper.show(getContext(), item, data -> reloadData());
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(Color.parseColor("#00A680"));
            swipeRefresh.setOnRefreshListener(this::reloadData);
        }

        reloadData();
    }

    public void reloadData() {
        if (getContext() == null || isLoading) return;

        PreferenceHandler handler = new PreferenceHandler(requireContext());
        JSONObject userObj = handler.getJsonObject("user");
        if (userObj == null || userObj.length() == 0) {
            Toast.makeText(getContext(), "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = userObj.optString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(getContext(), "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        HttpClient.initToken(token);
        new Thread(() -> {
            HttpClient.APIReturn apiReturn = HttpClient.getAllWorkOrder(
                    requireContext().getApplicationContext(),
                    serverDynamic,
                    schemaData,
                    schemaCore,
                    "1=1",
                    0,
                    100
            );

            List<JSONObject> items = new ArrayList<>();
            if (apiReturn != null && apiReturn.code == 200 && apiReturn.data != null) {
                items.addAll(apiReturn.data);
            }

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                adapter.setItems(items);
            });
        }).start();
    }

    private void initConfiguration() {
        try {
            ConfigManager configManager = new ConfigManager(requireContext());
            serverDynamic = configManager.getProperty("server_dynamic_url");
            if (serverDynamic == null || serverDynamic.isEmpty()) {
                serverDynamic = "http://192.86.0.225:9101/api/dynamics";
            }
            schemaData = configManager.getProperty("schema_mms");
            if (schemaData == null || schemaData.isEmpty()) schemaData = "MES_MMS_MKHC";
            schemaCore = configManager.getProperty("schema_core");
            if (schemaCore == null || schemaCore.isEmpty()) schemaCore = "MES_CORE_MKHC";
        } catch (Exception e) {
            serverDynamic = "http://192.86.0.225:9101/api/dynamics";
            schemaData = "MES_MMS_MKHC";
            schemaCore = "MES_CORE_MKHC";
        }
    }

    static class WorkOrderDataAdapter extends RecyclerView.Adapter<WorkOrderDataAdapter.VH> {

        interface OnEnterClickListener {
            void onEnter(JSONObject item);
        }

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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_work_order_data_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject data = items.get(position);
            holder.tvNo.setText(String.valueOf(position + 1));
            String woCode = safeGet(data, "WO_CODE");
            if (woCode.isEmpty()) woCode = safeGet(data, "Wo_Code");
            holder.tvWoCode.setText(woCode.isEmpty() ? "—" : woCode);

            String machineId = safeGet(data, "Machine_Id");
            String machineName = safeGet(data, "Machine_Name");
            String machine = machineId + (machineName.isEmpty() ? "" : " - " + machineName);
            holder.tvMachine.setText(machine.trim().isEmpty() ? "—" : machine);

            String content = safeGet(data, "Request_Reason");
            if (content.isEmpty()) content = safeGet(data, "Note");
            holder.tvContent.setText(content.isEmpty() ? "—" : content);
            holder.tvRequestDate.setText(formatDate(safeGet(data, "Request_Date")));

            holder.btnEnter.setOnClickListener(v -> {
                if (listener != null) listener.onEnter(data);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNo, tvWoCode, tvMachine, tvContent, tvRequestDate, btnEnter;

            VH(@NonNull View itemView) {
                super(itemView);
                tvNo = itemView.findViewById(R.id.tv_item_no);
                tvWoCode = itemView.findViewById(R.id.tv_item_wo_code);
                tvMachine = itemView.findViewById(R.id.tv_item_machine);
                tvContent = itemView.findViewById(R.id.tv_item_content);
                tvRequestDate = itemView.findViewById(R.id.tv_item_request_date);
                btnEnter = itemView.findViewById(R.id.btn_enter_work_order);
            }
        }

        private static String safeGet(JSONObject obj, String key) {
            if (obj == null) return "";
            return obj.optString(key, "").trim();
        }

        private static String formatDate(String raw) {
            if (raw == null || raw.isEmpty()) return "";
            try {
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return out.format(in.parse(raw.replace("Z", "")));
            } catch (Exception e) {
                return raw;
            }
        }
    }
}
