package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.ui.custom.Checkbox_custom;
import com.mkac.meikomms.ui.custom.TextViewCustom;
import com.mkac.meikomms.ui.taskdetail.ListIntructionAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ListTaskAdapter extends RecyclerView.Adapter<ListTaskAdapter.ItemViewHolder>
{
    private List<JSONObject> mtaskListData;
    private String mdate_input;
    private Context mcontext;
    private TextView mtextView;
    private ListTaskAdapter.OnItemClickListener mOnItemClickListener;

    public ListTaskAdapter(List<JSONObject> mtaskListData,TextView textView,String date_input ,Context mcontext, OnItemClickListener mOnItemClickListener)
    {
        this.mtaskListData = mtaskListData;
        this.mdate_input = date_input;
        this.mtextView = textView;
        this.mcontext = mcontext;
        this.mOnItemClickListener = mOnItemClickListener;
    }

    public interface OnItemClickListener {
        public void onItemClick(View view, int position) throws JSONException;
    }
    @NonNull
    @Override
    public ListTaskAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_list_task,parent,false);
        ListTaskAdapter.ItemViewHolder evh = new ListTaskAdapter.ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListTaskAdapter.ItemViewHolder holder, int position)
    {
        JSONObject jsonObject = mtaskListData.get(position);
        String Machine_Id = jsonObject.optString("Machine_Id");
        String Issue_Id = jsonObject.optString("Issue_Id");
        String Task_Type = jsonObject.optString("Task_Type");
        String Category_Name = jsonObject.optString("Category_Name");
        String Status = jsonObject.optString("Status");
        String Progess = jsonObject.optString("Progress");
        String Material = jsonObject.optString("Material");
        Boolean checkvalue = jsonObject.optBoolean("check");
        String Task_Date_Unix = jsonObject.optString("Task_Date_Unix");
        holder.checkBox.setChecked(checkvalue);
        holder.machineCode.setText(Machine_Id);
        holder.issueCode.setText(Issue_Id);
        if(Issue_Id.equals("")){holder.issueCode.setText("--");}
        if(Task_Type.equals("0"))
        {
            holder.taskType.setText(i18n("Repair"));
        }else
        {
            holder.taskType.setText(i18n("Maintenance"));

        }

        if(Category_Name.equals("")){ holder.maintenanceName.setText("--");}
        else {holder.maintenanceName.setText(Category_Name);}

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

        holder.checkBox.setOnCheckedChangeListener((button, b) -> {
            try {
                try
                {

                    if(!Task_Type.equals("1"))
                    {
                        jsonObject.put("check",b);
                        String material = jsonObject.getString("Material");
                        String status = jsonObject.getString("Status");
                        String old_value = mtextView.getText().toString().replace("(", "").replace(")", "");
                        if (material.equals("Y"))
                        {
                            if(b)
                            {
                                int new_value = Integer.parseInt(old_value) + 1;
                                mtextView.setText("(" + String.valueOf(new_value) + ")");
                            }
                            else
                            {
                                int new_value = Integer.parseInt(old_value) - 1;
                                mtextView.setText("(" + String.valueOf(new_value) + ")");
                            }
                        }
                    }else if(Task_Type.equals("1"))
                    {

                        if(!isToday(Long.parseLong(Task_Date_Unix)))
                        {
                            jsonObject.put("check",b);
                            String material = jsonObject.getString("Material");
                            String status = jsonObject.getString("Status");
                            String old_value = mtextView.getText().toString().replace("(", "").replace(")", "");
                            if (material.equals("Y"))
                            {
                                if(b)
                                {
                                    int new_value = Integer.parseInt(old_value) + 1;
                                    mtextView.setText("(" + String.valueOf(new_value) + ")");
                                }
                                else
                                {
                                    int new_value = Integer.parseInt(old_value) - 1;
                                    mtextView.setText("(" + String.valueOf(new_value) + ")");
                                }
                            }
                        }else
                        {
                            String status = jsonObject.getString("Status");
                            if(status.equals("0"))
                            {
                                holder.checkBox.setChecked(false);
                                Toast.makeText(mcontext,i18n("Maintenance not scheduled yet"),Toast.LENGTH_SHORT).show();
                            }

                        }
                    }



                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                Log.e("ListTaskAdapter", "Error in CheckBox listener", e);
            }
        });

        int date_qty = DateDifferenceCalculator(mdate_input,getDate("dd/MM/yyyy"),"dd/MM/yyyy");

        if(date_qty > 0)
        {
            holder.progess.setTextColor(Color.parseColor("#F5B500"));
            holder.machineCode.setTextColor(Color.parseColor("#F5B500"));
            holder.maintenanceName.setTextColor(Color.parseColor("#F5B500"));
            holder.issueCode.setTextColor(Color.parseColor("#F5B500"));
            holder.material.setTextColor(Color.parseColor("#F5B500"));
            holder.taskType.setTextColor(Color.parseColor("#F5B500"));
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_checked}, // unchecked
                            new int[]{android.R.attr.state_checked}   // checked
                    },
                    new int[]{
                            Color.parseColor("#737373"),  // unchecked color
                            Color.parseColor("#F5B500")  // checked color
                    }
            );
            holder.checkBox.setButtonTintList(colorStateList);
            if(!Status.equals("0"))
            {
               // holder.checkBox.setChecked(true);
               // holder.checkBox.setEnabled(false);
            }
        }
        else
        {
            if(Status.equals("4"))
            {

                holder.progess.setTextColor(Color.parseColor("#2196F3"));
                holder.machineCode.setTextColor(Color.parseColor("#2196F3"));
                holder.maintenanceName.setTextColor(Color.parseColor("#2196F3"));
                holder.material.setTextColor(Color.parseColor("#2196F3"));
                holder.issueCode.setTextColor(Color.parseColor("#2196F3"));
                holder.taskType.setTextColor(Color.parseColor("#2196F3"));
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{
                                new int[]{-android.R.attr.state_checked}, // unchecked
                                new int[]{android.R.attr.state_checked}   // checked
                        },
                        new int[]{
                                Color.parseColor("#737373"),  // unchecked color
                                Color.parseColor("#2196F3")  // checked color
                        }
                );
                holder.checkBox.setButtonTintList(colorStateList);
              //  holder.checkBox.setEnabled(false);

            }else
            {
                holder.progess.setTextColor(Color.parseColor("#2D2D2D"));
                holder.machineCode.setTextColor(Color.parseColor("#2D2D2D"));
                holder.maintenanceName.setTextColor(Color.parseColor("#2D2D2D"));
                holder.material.setTextColor(Color.parseColor("#2D2D2D"));
                holder.issueCode.setTextColor(Color.parseColor("#2D2D2D"));
                holder.taskType.setTextColor(Color.parseColor("#2D2D2D"));
            }

        }

        if(Material.equals("Y"))
        {
            holder.material.setLabel(i18n("Yes"));
            holder.material.hideIcon();
            holder.material.showLabel();
        }
        else if(Material.equals("N"))
        {
            holder.material.setLabel(i18n("No"));
            holder.material.hideIcon();
            holder.material.showLabel();
        }
        else if(Material.equals("X"))
        {
            holder.material.hideLabel();
        }






    }

    public class ItemViewHolder extends RecyclerView.ViewHolder
    {

        CheckBox checkBox;
        TextView machineCode,maintenanceName,progess,taskType,issueCode;
        TextViewCustom material;


        public ItemViewHolder(@NonNull View itemView)
        {

            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBox);
            machineCode = itemView.findViewById(R.id.machineCode);
            maintenanceName = itemView.findViewById(R.id.maintenanceName);
            progess = itemView.findViewById(R.id.progess);
            material = itemView.findViewById(R.id.material);
            taskType = itemView.findViewById(R.id.taskType);
            issueCode = itemView.findViewById(R.id.issueCode);


        }

    }

    public void updateData(List<JSONObject> newData)
    {
        this.mtaskListData = newData; // Update dataset
        notifyDataSetChanged(); // Refresh RecyclerView
    }


    public void setcheckAll(boolean isChecked) {
        boolean dataChanged = false;
        for (JSONObject task : mtaskListData) {
            try {
                boolean currentCheck = task.optBoolean("check");
                if (currentCheck != isChecked) {
                    task.put("check", isChecked);
                    dataChanged = true;
                }
            } catch (JSONException e) {
                Log.e("ListTaskAdapter", "Error updating check state", e);
            }
        }
        notifyDataSetChanged(); // Always notify to enforce UI update
        Log.d("ListTaskAdapter", "setcheckAll: Notified UI with isChecked=" + isChecked + ", dataChanged=" + dataChanged);
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

    public static boolean isToday(long unixTimestamp) {
        try {
            TimeZone timeZone = TimeZone.getTimeZone("GMT+07:00");
            // Lấy thời gian hiện tại theo giây
            Calendar currentCal = Calendar.getInstance(timeZone);
            long currentTime = currentCal.getTimeInMillis() / 1000;

            // So sánh
            boolean result = unixTimestamp > currentTime;

            // Để debug (tùy chọn)
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            sdf.setTimeZone(timeZone);

            Date inputDate = new Date(unixTimestamp * 1000);
            Date currentDate = new Date(currentTime * 1000);

            Log.d("DateCheck", "Input date: " + sdf.format(inputDate));
            Log.d("DateCheck", "Current date: " + sdf.format(currentDate));

            return result;
        } catch (Exception e) {
            Log.e("DateCheck", "Error checking date: " + e.getMessage());
            return false;
        }
    }



    private String getDate(String format)
    {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        return currentDate;

    }


    private int DateDifferenceCalculator ( String startDateStr,String endDateStr,String format)
    {

        // Define date format
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());

        try {
            // Convert strings to Date objects
            Calendar startDate = Calendar.getInstance();
            startDate.setTime(dateFormat.parse(startDateStr));

            Calendar endDate = Calendar.getInstance();
            endDate.setTime(dateFormat.parse(endDateStr));

            // Calculate difference in milliseconds
            long diffInMillis = endDate.getTimeInMillis() - startDate.getTimeInMillis();

            // Convert milliseconds to days
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            return Integer.valueOf((int) diffInDays);

            // Print result
           // System.out.println("Number of days: " + diffInDays);
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }

        return -1;

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
