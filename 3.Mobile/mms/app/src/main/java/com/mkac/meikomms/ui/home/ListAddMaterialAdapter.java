package com.mkac.meikomms.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ListAddMaterialAdapter extends RecyclerView.Adapter<ListAddMaterialAdapter.ItemViewHolder>
{
    private List<JSONObject> mtaskListData;
    private List<JSONObject> initialListData; // Lưu trữ danh sách ban đầu với sao chép sâu
    private HashMap<String, Integer> materialIdMap; // Ánh xạ Material_Id -> vị trí trong mtaskListData
    private Context mcontext;

    public ListAddMaterialAdapter(List<JSONObject> mtaskListData, Context context) {
        this.mtaskListData = mtaskListData != null ? new ArrayList<>(mtaskListData) : new ArrayList<>();
        this.initialListData = mtaskListData != null ? deepCopyList(mtaskListData) : new ArrayList<>(); // Sao chép sâu
        this.mcontext = context;
        this.materialIdMap = new HashMap<>();
        // Khởi tạo HashMap từ danh sách ban đầu
        for (int i = 0; i < this.mtaskListData.size(); i++) {
            String materialId = this.mtaskListData.get(i).optString("Material_Id");
            materialIdMap.put(materialId, i);
        }
    }

    // Hàm sao chép sâu danh sách JSONObject
    private List<JSONObject> deepCopyList(List<JSONObject> source) {
        List<JSONObject> copiedList = new ArrayList<>();
        for (JSONObject jsonObject : source) {
            copiedList.add(deepCopyJSONObject(jsonObject));
        }
        return copiedList;
    }

    // Hàm sao chép sâu một JSONObject
    private JSONObject deepCopyJSONObject(JSONObject original) {
        JSONObject copy = new JSONObject();
        Iterator<String> keys = original.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                copy.put(key, original.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return copy;
    }

    @NonNull
    @Override
    public ListAddMaterialAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_add_material, parent, false);
        return new ListAddMaterialAdapter.ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListAddMaterialAdapter.ItemViewHolder holder, int position) {
        JSONObject jsonObject = mtaskListData.get(position);
        String label_materialCode = jsonObject.optString("Material_Id");
        String label_materialName = jsonObject.optString("Material_Name");
        String label_materialQty = jsonObject.optString("Material_Qty");
        String label_materialUnit = jsonObject.optString("Material_Unit");

        holder.txt_no.setText(String.valueOf(position + 1));
        holder.materialCode.setText(label_materialCode);
        holder.materialName.setText(Specialcharacters(label_materialName));
        holder.materialQty.setText(String.valueOf(label_materialQty));
        holder.btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    removeMaterial(adapterPosition); // Gọi hàm xóa
                }
            }
        });
    }

    // Hàm thêm mới material hoặc cộng dồn số lượng với HashMap
    public void addMaterial(JSONObject newMaterial)
    {
        if (newMaterial == null) return;

        String newMaterialId = newMaterial.optString("Material_Id");
        String newMaterialQtyStr = newMaterial.optString("Material_Qty", "0");
        int newMaterialQty = Integer.parseInt(newMaterialQtyStr.isEmpty() ? "0" : newMaterialQtyStr);

        // Kiểm tra xem Material_Id đã tồn tại trong HashMap chưa
        if (materialIdMap.containsKey(newMaterialId)) {
            // Material đã tồn tại, cập nhật số lượng
            int position = materialIdMap.get(newMaterialId);
            JSONObject existingMaterial = mtaskListData.get(position);
            String existingQtyStr = existingMaterial.optString("Material_Qty", "0");
            int existingQty = Integer.parseInt(existingQtyStr.isEmpty() ? "0" : existingQtyStr);
            int updatedQty = existingQty + newMaterialQty;

            try {
                existingMaterial.put("Material_Qty", String.valueOf(updatedQty));
                notifyItemChanged(position); // Cập nhật giao diện tại vị trí cụ thể
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            // Material chưa tồn tại, thêm mới
            mtaskListData.add(deepCopyJSONObject(newMaterial)); // Sao chép sâu khi thêm
            int newPosition = mtaskListData.size() - 1;
            materialIdMap.put(newMaterialId, newPosition);
            notifyItemInserted(newPosition); // Thông báo thêm mục mới
        }
    }

    // Hàm xóa material để đồng bộ HashMap
    private void removeMaterial(int position) {
        if (position >= 0 && position < mtaskListData.size()) {
            String materialId = mtaskListData.get(position).optString("Material_Id");
            mtaskListData.remove(position);
            materialIdMap.remove(materialId);

            // Cập nhật lại vị trí trong HashMap cho các mục sau vị trí bị xóa
            for (int i = position; i < mtaskListData.size(); i++) {
                String id = mtaskListData.get(i).optString("Material_Id");
                materialIdMap.put(id, i);
            }
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mtaskListData.size() - position);
        }
    }

    // Hàm khôi phục danh sách ban đầu
    public void resetToInitialList() {
        // Xóa dữ liệu hiện tại
        mtaskListData.clear();
        materialIdMap.clear();

        // Sao chép lại từ danh sách ban đầu với sao chép sâu
        mtaskListData.addAll(deepCopyList(initialListData));

        // Cập nhật lại HashMap
        for (int i = 0; i < mtaskListData.size(); i++) {
            String materialId = mtaskListData.get(i).optString("Material_Id");
            materialIdMap.put(materialId, i);
        }

        // Thông báo RecyclerView cập nhật toàn bộ danh sách
        notifyDataSetChanged();
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView txt_no, materialCode, materialName, materialQty;
        ImageView btn_delete;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            txt_no = itemView.findViewById(R.id.txt_no);
            materialCode = itemView.findViewById(R.id.materialCode);
            materialName = itemView.findViewById(R.id.materialName);
            materialQty = itemView.findViewById(R.id.materialQty);
            btn_delete = itemView.findViewById(R.id.btn_delete);
        }
    }

    public List<JSONObject> getMtaskListData()
    {
        return mtaskListData;
    }

    public String Specialcharacters(String input_text) {
        String output_text = input_text.replace("/", "/")
                .replace("'", "'")
                .replace("\"", "\"")
                .replace("\\", "\\")
                .replace("¦", "|")
                .replace("µ", "u");
        return output_text;
    }

    @Override
    public int getItemCount() {
        return mtaskListData.size();
    }
}