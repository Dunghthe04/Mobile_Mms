package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ListTaskEndAdapter extends RecyclerView.Adapter<ListTaskEndAdapter.ItemViewHolder>
{

    private List<JSONObject> mtaskListData;
    private String mdate_input;
    private Context mcontext;
    private ListTaskEndAdapter.OnItemClickListener mOnItemClickListener;

    public ListTaskEndAdapter(List<JSONObject> mtaskListData, ListTaskEndAdapter.OnItemClickListener onItemClickListener, Context mcontext) {
        this.mtaskListData = mtaskListData;
        this.mOnItemClickListener = onItemClickListener;
        this.mcontext = mcontext;
    }

    public interface OnItemClickListener {
        public void onItemClick(View view, int position) throws JSONException;
    }

    @NonNull
    @Override
    public ListTaskEndAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_list_task,parent,false);
        ListTaskEndAdapter.ItemViewHolder evh = new ListTaskEndAdapter.ItemViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListTaskEndAdapter.ItemViewHolder holder, int position) {
        JSONObject jsonObject = mtaskListData.get(position);
        String Machine_Id = jsonObject.optString("Machine_Id");
        String Issue_Id = jsonObject.optString("Issue_Id");
        String Task_Type = jsonObject.optString("Task_Type");
        String Category_Name = jsonObject.optString("Category_Name");
        String Status = jsonObject.optString("Status");
        String Progess = jsonObject.optString("Progress");
        String Material = jsonObject.optString("Material");
        Boolean checkvalue = jsonObject.optBoolean("check");
        holder.issueCode.setText(Issue_Id);
        if(Issue_Id.equals("")){holder.issueCode.setText("--");}
        holder.checkBox.setChecked(checkvalue);
        holder.machineCode.setText(Specialcharacters(Machine_Id));
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

        holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b)
            {
                try
                {
                   jsonObject.put("check",b);

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        if(Task_Type.equals("0"))
        {
            holder.taskType.setText(i18n("Repair"));
        }else
        {
            holder.taskType.setText(i18n("Maintenance"));
        }


            if(Status.equals("1"))
            {

                holder.progess.setTextColor(Color.parseColor("#806C00"));
                holder.machineCode.setTextColor(Color.parseColor("#806C00"));
                holder.maintenanceName.setTextColor(Color.parseColor("#806C00"));
                holder.material.setTextColor(Color.parseColor("#806C00"));
                holder.taskType.setTextColor(Color.parseColor("#806C00"));
                holder.issueCode.setTextColor(Color.parseColor("#806C00"));
                holder.taskType.setTextColor(Color.parseColor("#806C00"));
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{
                                new int[]{-android.R.attr.state_checked}, // unchecked
                                new int[]{android.R.attr.state_checked}   // checked
                        },
                        new int[]{
                                Color.parseColor("#737373"),  // unchecked color
                                Color.parseColor("#806C00")  // checked color
                        }
                );
                holder.checkBox.setButtonTintList(colorStateList);


            }else if(Status.equals("2"))
            {
                holder.progess.setTextColor(Color.parseColor("#01A789"));
                holder.machineCode.setTextColor(Color.parseColor("#01A789"));
                holder.maintenanceName.setTextColor(Color.parseColor("#01A789"));
                holder.material.setTextColor(Color.parseColor("#01A789"));
                holder.taskType.setTextColor(Color.parseColor("#01A789"));
                holder.issueCode.setTextColor(Color.parseColor("#01A789"));
                holder.taskType.setTextColor(Color.parseColor("#01A789"));
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{
                                new int[]{-android.R.attr.state_checked}, // unchecked
                                new int[]{android.R.attr.state_checked}   // checked
                        },
                        new int[]{
                                Color.parseColor("#737373"),  // unchecked color
                                Color.parseColor("#01A789")  // checked color
                        }
                );
                holder.checkBox.setButtonTintList(colorStateList);
                holder.checkBox.setEnabled(false);

            }else if(Status.equals("3"))
            {
                holder.progess.setTextColor(Color.parseColor("#F44949"));
                holder.machineCode.setTextColor(Color.parseColor("#F44949"));
                holder.maintenanceName.setTextColor(Color.parseColor("#F44949"));
                holder.material.setTextColor(Color.parseColor("#F44949"));
                holder.taskType.setTextColor(Color.parseColor("#F44949"));
                holder.issueCode.setTextColor(Color.parseColor("#F44949"));
                holder.taskType.setTextColor(Color.parseColor("#F44949"));
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{
                                new int[]{-android.R.attr.state_checked}, // unchecked
                                new int[]{android.R.attr.state_checked}   // checked
                        },
                        new int[]{
                                Color.parseColor("#737373"),  // unchecked color
                                Color.parseColor("#F44949")  // checked color
                        }
                );
                holder.checkBox.setButtonTintList(colorStateList);
                holder.checkBox.setEnabled(false);

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
