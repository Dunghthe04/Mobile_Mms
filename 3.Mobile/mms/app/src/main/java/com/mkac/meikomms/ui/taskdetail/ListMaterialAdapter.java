package com.mkac.meikomms.ui.taskdetail;

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
import java.util.List;

public class ListMaterialAdapter extends RecyclerView.Adapter<ListMaterialAdapter.ItemViewHolder>
{
    private List<JSONObject> mtaskListData;
    private Context mcontext;

    public ListMaterialAdapter(List<JSONObject> mtaskListData, Context mcontext) {
        this.mtaskListData = mtaskListData;
        this.mcontext = mcontext;
    }

    @NonNull
    @Override
    public ListMaterialAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_material,parent,false);
        ListMaterialAdapter.ItemViewHolder evh = new ListMaterialAdapter.ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListMaterialAdapter.ItemViewHolder holder, int position)
    {
        JSONObject jsonObject = mtaskListData.get(position);
        String label_materialCode = jsonObject.optString("Material_Id");
        String label_materialName = jsonObject.optString("Material_Name");
        String label_materialQty  = jsonObject.optString("Material_Qty");
        String label_materialUnit = jsonObject.optString("Material_Unit");

        holder.txt_no.setText(String.valueOf(position+1));
        holder.materialCode.setText((label_materialCode));
        holder.materialName.setText(Specialcharacters(label_materialName));
        holder.materialQty.setText(String.valueOf(label_materialQty));
        holder.materialUnit.setText((label_materialUnit));

    }

    public class ItemViewHolder extends RecyclerView.ViewHolder
    {

        TextView txt_no,materialCode,materialName,materialQty,materialUnit;


        public ItemViewHolder(@NonNull View itemView)
        {
            super(itemView);
            txt_no = itemView.findViewById(R.id.txt_no);
            materialCode = itemView.findViewById(R.id.materialCode);
            materialName = itemView.findViewById(R.id.materialName);
            materialQty = itemView.findViewById(R.id.materialQty);
            materialUnit = itemView.findViewById(R.id.materialUnit);

        }

    }

    public void updateData(List<JSONObject> newData)
    {
        if (mtaskListData != null) {
            mtaskListData.clear();
        }
        if (newData != null) {
            this.mtaskListData = new ArrayList<>(newData);
        } else {
            this.mtaskListData = new ArrayList<>();
        }
        notifyDataSetChanged();
    }


    public void addData(List<JSONObject> newData)
    {

        if (newData != null)
        {
            int check = 0;
           for (int i = 0; i < newData.size(); i++)
           {
               for (int j = 0; j < mtaskListData.size(); j++)
               {
                   try {
                       if (newData.get(i).getString("Material_Id").equals(mtaskListData.get(j).getString("Material_Id")))
                       {
                          String old_qty = mtaskListData.get(j).getString("Material_Qty");
                          String new_qty = newData.get(i).getString("Material_Qty");
                          int old_qty_int = Integer.parseInt(old_qty);
                          int new_qty_int = Integer.parseInt(new_qty);
                          int total_qty = old_qty_int + new_qty_int;
                          mtaskListData.get(j).put("Material_Qty",String.valueOf(total_qty));
                          check = 1;
                          break;
                       }

                   }catch (Exception ex)
                   {

                   }
               }
               if(check == 0)
               {
                   mtaskListData.add(newData.get(i));
               }

           }
        }
        notifyDataSetChanged();
    }



    public String Specialcharacters(String input_text)
    {
        String output_text = "";
        output_text = input_text.replace("&#47;","/");
        output_text = output_text.replace("&#39;","'");
        output_text = output_text.replace("&#34;","\"");
        output_text = output_text.replace("&#92;","\\");
        output_text = output_text.replace("¦","|");
        output_text = output_text.replace("µ","u");
        return  output_text;
    }

    public List<JSONObject> getListMaterialData()
    {
        return mtaskListData;
    }

    @Override
    public int getItemCount()
    {
        if(mtaskListData == null)
        {
            return 0;
        }
        else
        {
            return mtaskListData.size();
        }

    }
}
