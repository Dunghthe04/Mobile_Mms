package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ListTaskHistoryMachineAdapter extends RecyclerView.Adapter<ListTaskHistoryMachineAdapter.ItemViewHolder>
{

    private List<JSONObject> mtaskListData;
    private Context mcontext;
    private ListTaskAdapter.OnItemClickListener mOnItemClickListener;

    public ListTaskHistoryMachineAdapter(List<JSONObject> taskListData, Context context, ListTaskAdapter.OnItemClickListener onItemClickListener) {
        this.mtaskListData = taskListData;
        this.mcontext = context;
        this.mOnItemClickListener = onItemClickListener;
    }

    public interface OnItemClickListener {
        public void onItemClick(View view, int position) throws JSONException;
    }

    @NonNull
    @Override
    public ListTaskHistoryMachineAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_list_task_history_machine,parent,false);
        ListTaskHistoryMachineAdapter.ItemViewHolder evh = new ListTaskHistoryMachineAdapter.ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListTaskHistoryMachineAdapter.ItemViewHolder holder, int position)
    {
        JSONObject jsonObject = mtaskListData.get(position);
        String Machine_Id = jsonObject.optString("Machine_Id");
        String Task_Date = jsonObject.optString("Task_Date_Unix");
        String Task_Type = jsonObject.optString("Task_Type");
        String Category_Name = jsonObject.optString("Category_Name");
        String Status = jsonObject.optString("Status");
        String Progess = jsonObject.optString("Progress");
        String Material = jsonObject.optString("Material");




        holder.txt_task_date.setText(convertTaskDate(Long.parseLong(Task_Date)));
        holder.machineCode.setText(Specialcharacters(Machine_Id));

        if(Task_Type.equals("0"))
        {
            holder.taskType.setText(i18n("Repair"));
        }else
        {
            holder.taskType.setText(i18n("Maintenance"));
        }

        if(Category_Name.equals("")){ holder.maintenanceName.setText("--");}
        else {holder.maintenanceName.setText(Specialcharacters(Category_Name));}

        holder.progess.setText(Progess+" %");
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



    }

    public class ItemViewHolder extends RecyclerView.ViewHolder
    {

        TextView machineCode,maintenanceName,progess,taskType,txt_task_date;
        TextViewCustom material;


        public ItemViewHolder(@NonNull View itemView)
        {

            super(itemView);
            txt_task_date = itemView.findViewById(R.id.txt_task_date);
            machineCode = itemView.findViewById(R.id.machineCode);
            maintenanceName = itemView.findViewById(R.id.maintenanceName);
            progess = itemView.findViewById(R.id.progess);
            material = itemView.findViewById(R.id.material);
            taskType = itemView.findViewById(R.id.taskType);


        }

    }

    public void updateData(List<JSONObject> newData) {
        this.mtaskListData = newData; // Update dataset
        notifyDataSetChanged(); // Refresh RecyclerView
    }

    public static String convertTaskDate(long unixTimestamp) {


        try {
            TimeZone timeZone = TimeZone.getTimeZone("GMT+07:00");
            // Lấy thời gian hiện tại theo giây
            Calendar currentCal = Calendar.getInstance(timeZone);
            long currentTime = currentCal.getTimeInMillis() / 1000;

            // Để debug (tùy chọn)
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setTimeZone(timeZone);

            Date inputDate = new Date(unixTimestamp * 1000);


            return sdf.format(inputDate);
        } catch (Exception e) {
            Log.e("DateCheck", "Error checking date: " + e.getMessage());
            return null;
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
