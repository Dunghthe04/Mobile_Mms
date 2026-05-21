package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.ui.custom.TextViewCustom;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ListErrorNewAdapter extends RecyclerView.Adapter<ListErrorNewAdapter.ItemViewHolder>
{
    private List<JSONObject> mtaskListErrorData;
    private Context mcontext;
    private ListErrorNewAdapter.OnItemClickListener mOnItemClickListener;

    public ListErrorNewAdapter(List<JSONObject> taskListErrorData, Context context, ListErrorNewAdapter.OnItemClickListener mOnItemClickListener) {
        this.mtaskListErrorData = taskListErrorData;
        this.mcontext = context;
        this.mOnItemClickListener = mOnItemClickListener;
    }

    public interface OnItemClickListener {
        public void onItemClick(View view, int position) throws JSONException;
    }

    @NonNull
    @Override
    public ListErrorNewAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_error,parent,false);
        ListErrorNewAdapter.ItemViewHolder evh = new ListErrorNewAdapter.ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListErrorNewAdapter.ItemViewHolder holder, int position)
    {
        JSONObject jsonObject = mtaskListErrorData.get(position);
        String Machine_Id = jsonObject.optString("Machine_Id");
        String Issue_Id = jsonObject.optString("Issue_Id");
        String Issue_Type = jsonObject.optString("Issue_Type");
        String Note = jsonObject.optString("Note");

        holder.error_Code.setText(Issue_Id);
        holder.machineCode.setText(Machine_Id);

        holder.contentError.setText(Note);

        if(Issue_Type.equals("1"))
        {
            holder.typeCode.setText(i18n("Impact on production"));
        }
        else
        {
            holder.typeCode.setText(i18n("Impact on safety"));
        }

        holder.btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mOnItemClickListener.onItemClick(view, holder.getPosition());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }

    public class ItemViewHolder extends RecyclerView.ViewHolder
    {

        TextView error_Code,machineCode,typeCode,contentError;
        ImageView btn_delete;

        public ItemViewHolder(@NonNull View itemView)
        {

            super(itemView);
            error_Code = itemView.findViewById(R.id.error_Code);
            machineCode = itemView.findViewById(R.id.machineCode);
            typeCode = itemView.findViewById(R.id.typeCode);
            contentError = itemView.findViewById(R.id.contentError);
            btn_delete = itemView.findViewById(R.id.btn_delete);


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
        if(mtaskListErrorData == null)
        {
            return 0;
        }
        else
        {
            return mtaskListErrorData.size();
        }

    }
}
