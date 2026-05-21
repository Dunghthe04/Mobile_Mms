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

import java.util.List;

public class ListIntructionAdapter extends RecyclerView.Adapter<ListIntructionAdapter.ItemViewHolder>
{

    private List<JSONObject> mtaskListData;
    private Context mcontext;
    private ListIntructionAdapter.OnItemClickListener mOnItemClickListener;

    public ListIntructionAdapter(List<JSONObject> taskListData, Context context, ListIntructionAdapter.OnItemClickListener OnItemClickListener) {
        this.mtaskListData = taskListData;
        this.mcontext = context;
        this.mOnItemClickListener = OnItemClickListener;
    }

    public interface OnItemClickListener {
        public void onItemClick(View view, int position) throws JSONException;
    }

    @NonNull
    @Override
    public ListIntructionAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_intruction,parent,false);
        ItemViewHolder evh = new ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListIntructionAdapter.ItemViewHolder holder, int position) {
        JSONObject jsonObject = mtaskListData.get(position);
        String label_intruction = jsonObject.optString("Category_Name");
        String connten_intruction = jsonObject.optString("Content");
        holder.txtlabel_intruction.setText(Specialcharacters(label_intruction));
        holder.txt_connten_intruction.setText(connten_intruction);
        holder.txt_step_number.setText(String.valueOf(position+1));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mOnItemClickListener.onItemClick(view, holder.getPosition());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        if(position==(mtaskListData.size()-1)){holder.img_arrow.setVisibility(View.INVISIBLE);}

    }

    public class ItemViewHolder extends RecyclerView.ViewHolder
    {

        TextView txt_step_number,txtlabel_intruction,txt_connten_intruction;
        ImageView img_arrow;


        public ItemViewHolder(@NonNull View itemView)
        {

            super(itemView);
            txt_step_number = itemView.findViewById(R.id.txt_step_number);
            txtlabel_intruction = itemView.findViewById(R.id.txtlabel_intruction);
            txt_connten_intruction = itemView.findViewById(R.id.txt_connten_intruction);
            img_arrow = itemView.findViewById(R.id.img_arrow);


        }

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
