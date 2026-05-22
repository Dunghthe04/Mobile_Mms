package com.mkac.meikomms.ui.home;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Printer;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.ui.custom.TextViewCustom;
import com.mkac.meikomms.ui.taskdetail.TaskDetailActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ListTaskMonthAdapter extends RecyclerView.Adapter<ListTaskMonthAdapter.ViewHolder>
{


    private SparseIntArray itemStateArray= new SparseIntArray();
    private List<JSONObject> mtaskListMonthData;
    private Context mcontext;
    private TextView mtextView,machineCode;
    private ListTaskAdapter listTaskAdapter;
    private List<JSONObject> originalList ;
    private final Handler handler = new Handler(Looper.getMainLooper());


    public ListTaskMonthAdapter(List<JSONObject> taskListMonthData,TextView textView, Context mcontext) {
        this.mtaskListMonthData = new ArrayList<>(taskListMonthData);
        this.originalList = new ArrayList<>(taskListMonthData);
        this.mtextView = textView;
        this.mcontext = mcontext;
    }


    @NonNull
    @Override
    public ListTaskMonthAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rcv_list_task_in_month,parent,false);
        ListTaskMonthAdapter.ViewHolder evh = new ListTaskMonthAdapter.ViewHolder(view);
        return evh;
    }

    @Override
    public void onBindViewHolder(@NonNull ListTaskMonthAdapter.ViewHolder holder, @SuppressLint("RecyclerView") int position)
    {
        JSONObject jsonObject = mtaskListMonthData.get(position);
        // String Date =convertDateFormat(jsonObject.optString("Task_Date"));
        String Date = TimeUtils.convertUnixToDateString(Long.parseLong(jsonObject.optString("Task_Date"))*1000);
        try
        {
            String value_data = jsonObject.getString("DataTemp");
            if(!value_data.equals("[]"))
            {
                JSONArray DataTemp = jsonObject.getJSONArray("DataTemp");
                if (DataTemp.length() > 0)
                {
                    List<JSONObject> jsonObjectList_out = new ArrayList<>();
                    for (int i = 0; i < DataTemp.length(); i++) {
                        jsonObjectList_out.add(DataTemp.getJSONObject(i));
                    }

                    long Task_Date_Unix = getUnixTimeMillisFromString(Date);
                    listTaskAdapter = new ListTaskAdapter(jsonObjectList_out, mtextView, Date, mcontext, new ListTaskAdapter.OnItemClickListener()
                    {
                        @Override
                        public void onItemClick(View view, int vitri) throws JSONException
                        {
                            JSONObject jsonObject = jsonObjectList_out.get(vitri);
                            String Task_Id = jsonObject.optString("Task_Id");
                            String Task_Date_Unix = jsonObject.optString("Task_Date_Unix");
                            String Task_Type = jsonObject.optString("Task_Type");

                            if(Task_Type.equals("0"))
                            {

                                String Category_Name = jsonObject.optString("Category_Name");
                                String Status = jsonObject.optString("Status");
                                String Image_List = jsonObject.optString("Image_List");
                                JSONObject new_list = new JSONObject();
                                new_list.put("Task_Id",Task_Id);
                                new_list.put("Task_Type",Task_Type);
                                new_list.put("Category_Name",Category_Name);
                                new_list.put("Status",Status);
                                new_list.put("Image_List",Image_List);
                                Intent intent = new Intent(mcontext, TaskDetailActivity.class);
                                intent.putExtra("Datasend",String.valueOf(new_list));
                                mcontext.startActivity(intent);
                            }else
                            if(!isToday(Long.parseLong(Task_Date_Unix)))
                            {

                                String Category_Name = jsonObject.optString("Category_Name");
                                String Status = jsonObject.optString("Status");
                                String Image_List = jsonObject.optString("Image_List");
                                JSONObject new_list = new JSONObject();
                                new_list.put("Task_Id",Task_Id);
                                new_list.put("Task_Type",Task_Type);
                                new_list.put("Category_Name",Category_Name);
                                new_list.put("Status",Status);
                                new_list.put("Image_List",Image_List);
                                Intent intent = new Intent(mcontext, TaskDetailActivity.class);
                                intent.putExtra("Datasend",String.valueOf(new_list));
                                mcontext.startActivity(intent);
                            }else
                            {
                                Toast.makeText(mcontext,i18n("Maintenance not scheduled yet"),Toast.LENGTH_SHORT).show();
                            }



                        }
                    });
                    holder.rcv_list_task.setAdapter(listTaskAdapter);


                    holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
                    {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b)
                        {

                            if(!isToday(Task_Date_Unix))
                            {

                                JSONObject jsonObject = mtaskListMonthData.get(position);
                                try {
                                    JSONArray jsonArray = jsonObject.getJSONArray("DataTemp");
                                    for (int i = 0; i < jsonArray.length(); i++)
                                    {
                                        jsonArray.getJSONObject(i).put("check", b);
                                    }
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }


                                if (b) {
                                    itemStateArray.put(position, 1);
                                } else {
                                    itemStateArray.put(position, 0);
                                }
                                mtaskListMonthData.set(position, jsonObject);

                                // Schedule update via Handler
                                handler.post(() -> {

                                    notifyItemChanged(position);
                                    mtextView.setText("(" + String.valueOf(getItemMaterialCount()) + ")");
                                });

                            }else
                            {
                                holder.checkBox.setChecked(false);
                                Toast.makeText(mcontext,i18n("Maintenance not scheduled yet"),Toast.LENGTH_SHORT).show();
                            }

                        }
                    });

                }
            }


        }
        catch (JSONException e)
        {

        }

        holder.txt_date_task.setText(i18n("Date")+" "+Specialcharacters(Date));
        holder.machineCode.setText(i18n("Machine code"));
        holder.maintenanceName.setText(i18n("Category Name"));
        holder.material.setText(i18n("Materials"));
        holder.progess.setText(i18n("Progress"));
        holder.taskType.setText(i18n("Type"));
        holder.issueCode.setText(i18n("Error code"));
        holder.bind(position);

    }

    public class ViewHolder extends RecyclerView.ViewHolder
    {

        TextView txt_date_task,machineCode,maintenanceName,material,progess,taskType,issueCode;
        RecyclerView rcv_list_task;
        CheckBox checkBox;
        public ViewHolder(@NonNull View itemView)
        {
            super(itemView);
            progess = itemView.findViewById(R.id.progess);
            material = itemView.findViewById(R.id.material);
            maintenanceName = itemView.findViewById(R.id.maintenanceName);
            machineCode = itemView.findViewById(R.id.machineCode);
            rcv_list_task = itemView.findViewById(R.id.rcv_list_task);
            txt_date_task = itemView.findViewById(R.id.txt_date_task);
            checkBox = itemView.findViewById(R.id.checkBox);
            rcv_list_task.setLayoutManager(new LinearLayoutManager(mcontext));
            taskType = itemView.findViewById(R.id.taskType);
            issueCode = itemView.findViewById(R.id.issueCode);
        }

        void bind(int position)
        {

            if (itemStateArray.get(position,0) == 1)
            {
                checkBox.setChecked(true);
            }
            else if (itemStateArray.get(position,0) == 0)
            {
                checkBox.setChecked(false);
            }
        }

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

    public static String convertDateFormat(String inputDate) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);

        try {
            Date date = inputFormat.parse(inputDate);
            return outputFormat.format(date);
        } catch (ParseException | java.text.ParseException e) {
            e.printStackTrace();
            return "Invalid Date"; // Handle the error gracefully
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


    public List<JSONObject> getListItemSelect()
    {
        // Lấy danh sách hạng mục đã chọn

        List<JSONObject> data_list = new ArrayList<>();
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {
                JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                for (int j = 0; j < jsonArray.length(); j++)
                {
                    String Category_Id = jsonArray.getJSONObject(j).getString("Task_Id");
                    String Category_Name = jsonArray.getJSONObject(j).getString("Category_Name");
                    String material = jsonArray.getJSONObject(j).getString("Material");
                    String status = jsonArray.getJSONObject(j).getString("Status");
                    String check = jsonArray.getJSONObject(j).getString("check");

                    if (check.equals("true"))
                    {
                        JSONObject new_list = new JSONObject();
                        new_list.put("Task_Id",Category_Id);
                        new_list.put("Category_Name",Category_Name);
                        data_list.add(new_list);
                    }

                }
            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return data_list;
    }

    public List<JSONObject> getListItemMaterial()
    {
        // Lấy danh sách hạng mục đã chọn có sử dụng vật tư

        List<JSONObject> data_list = new ArrayList<>();
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {
                JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                for (int j = 0; j < jsonArray.length(); j++)
                {
                    String Category_Id = jsonArray.getJSONObject(j).getString("Task_Id");
                    String Category_Name = jsonArray.getJSONObject(j).getString("Category_Name");
                    String material = jsonArray.getJSONObject(j).getString("Material");
                    String status = jsonArray.getJSONObject(j).getString("Status");
                    String check = jsonArray.getJSONObject(j).getString("check");

                    if (material.equals("Y") && check.equals("true"))
                    {
                        JSONObject new_list = new JSONObject();
                        new_list.put("Task_Id",Category_Id);
                        new_list.put("Category_Name",Category_Name);
                        new_list.put("Task_Type", jsonArray.getJSONObject(j).optString("Task_Type", ""));
                        new_list.put("Machine_Id", jsonArray.getJSONObject(j).optString("Machine_Id", ""));
                        new_list.put("Machine_Name", jsonArray.getJSONObject(j).optString("Machine_Name", ""));
                        data_list.add(new_list);
                    }

                }
            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return data_list;
    }

    public int getItemMaterialCount()
    {
        // Lấy số lượng hạng mục đã chọn có sử dụng vật tư
        int material_count = 0;
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {
                JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                for (int j = 0; j < jsonArray.length(); j++)
                {
                    String material = jsonArray.getJSONObject(j).getString("Material");
                    String status = jsonArray.getJSONObject(j).getString("Status");
                    String check = jsonArray.getJSONObject(j).getString("check");

                    if (material.equals("Y") && check.equals("true"))
                    {
                        material_count++;
                    }

                }
            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return material_count;
    }

    public int getItemTaskCount()
    {
        // Lấy số lượng task quá hạn
        int so_luong_qua_han = 0;
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {

                JSONObject jsonObject = mtaskListMonthData.get(i);
                // String Date = convertDateFormat(jsonObject.optString("Task_Date"));
                // int date_qty = DateDifferenceCalculator(Date,getDate("dd/MM/yyyy"),"dd/MM/yyyy");

                String Date = jsonObject.optString("Task_Date");
                long date_unix = TimeUtils.getCurrentUnixTimestamp();
                int date_qty = DateDifferenceCalculatorTimeUnix(Date,String.valueOf(date_unix));

                if(date_qty>0)
                {
                    JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                    so_luong_qua_han = so_luong_qua_han + jsonArray.length();
                }

            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return so_luong_qua_han;
    }

    public int getItemTaskFreeCount()
    {
        // Lấy số lượng task đang chờ nhận
        int so_luong_cho_nhan = 0;
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {
                JSONObject jsonObject = mtaskListMonthData.get(i);
                // String Date = convertDateFormat(jsonObject.optString("Task_Date"));
                // int date_qty = DateDifferenceCalculator(Date,getDate("dd/MM/yyyy"),"dd/MM/yyyy");
                String Date = jsonObject.optString("Task_Date");
                long date_unix = TimeUtils.getCurrentUnixTimestamp();
                int date_qty = DateDifferenceCalculatorTimeUnix(Date,String.valueOf(date_unix));

                if(date_qty<=0)
                {
                    JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                    for (int j = 0; j < jsonArray.length(); j++)
                    {

                        String status = jsonArray.getJSONObject(j).getString("Status");

                        if (status.equals("0"))
                        {
                            so_luong_cho_nhan++;
                        }

                    }
                }

            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return so_luong_cho_nhan;
    }

    public int getItemTaskWorkingCount()
    {
        // Lấy số lượng task đang thực hiện
        int so_luong_dang_thuc_hien = 0;
        for (int i = 0; i < mtaskListMonthData.size(); i++)
        {
            try
            {
                JSONObject jsonObject = mtaskListMonthData.get(i);
                // String Date = convertDateFormat(jsonObject.optString("Task_Date"));
                //String Date = jsonObject.optString("Task_Date");
                // int date_qty = DateDifferenceCalculator(Date,getDate("dd/MM/yyyy"),"dd/MM/yyyy");
                String date = jsonObject.optString("Task_Date");
                long date_unix = TimeUtils.getCurrentUnixTimestamp();
                int date_qty = DateDifferenceCalculatorTimeUnix(date,String.valueOf(date_unix));
                if(date_qty<=0)
                {
                    JSONArray jsonArray = mtaskListMonthData.get(i).getJSONArray("DataTemp");
                    for (int j = 0; j < jsonArray.length(); j++)
                    {

                        String status = jsonArray.getJSONObject(j).getString("Status");

                        if (status.equals("4"))
                        {
                            so_luong_dang_thuc_hien++;
                        }

                    }
                }

            }
            catch (JSONException e)
            {
                throw new RuntimeException(e);
            }
        }
        return so_luong_dang_thuc_hien;
    }



    public void filterListNew(String query)
    {
        if (originalList.isEmpty()) {
            originalList.addAll(mtaskListMonthData); // Store original list when search starts
        }

        if (query.isEmpty())
        {
            // Reset to the original data
            mtaskListMonthData.clear();
            mtaskListMonthData.addAll(originalList);
        }
        else
        {
            List<JSONObject> filteredList = new ArrayList<>();
            for (JSONObject item : originalList)
            {
                String date = item.optString("Task_Date", "");
                String Date_out = TimeUtils.convertUnixToDateString(Long.parseLong(date)*1000);
                JSONArray dataArray = item.optJSONArray("DataTemp");

                // Check Task Name and Status inside "DataTemp"
                if (dataArray != null)
                {
                    JSONArray list_temp = new JSONArray();
                    for (int i = 0; i < dataArray.length(); i++)
                    {
                        try {
                            JSONObject task = dataArray.getJSONObject(i);
                            String taskName = task.optString("Category_Name", ""); // Change key as per your JSON
                            String machine_Id = task.optString("Machine_Id", "");
                            String Issue_Id = task.optString("Issue_Id", "");

                            if (taskName.toLowerCase().contains(query.toLowerCase())
                                    || machine_Id.toLowerCase().contains(query.toLowerCase())
                                    || Issue_Id.toLowerCase().contains(query.toLowerCase())
                                    || Date_out.toLowerCase().contains(query.toLowerCase())
                            )

                            {
                                list_temp.put(dataArray.get(i));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    if(list_temp.length()>0)
                    {
                        JSONObject new_list = new JSONObject();
                        try {
                            new_list.put("Task_Date",date);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            new_list.put("DataTemp",list_temp);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        filteredList.add(new_list);
                    }

                }


            }

            mtaskListMonthData.clear();
            mtaskListMonthData.addAll(filteredList);
        }
        notifyDataSetChanged();
    }

    public void removeItemTaskCount()
    {
        // Xóa task quá hạn
        Iterator<JSONObject> iterator = mtaskListMonthData.iterator();
        while (iterator.hasNext()) {
            JSONObject jsonObject = iterator.next();
            String Date = jsonObject.optString("Task_Date");
            // int date_qty = DateDifferenceCalculator(Date, getDate("dd/MM/yyyy"), "dd/MM/yyyy");
            long date_unix = TimeUtils.getCurrentUnixTimestamp();
            int date_qty = DateDifferenceCalculatorTimeUnix(Date,String.valueOf(date_unix));
            if (date_qty > 0)
            {
                iterator.remove();
            }
        }
        notifyDataSetChanged();
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

    private String getDate(String format)
    {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        return currentDate;

    }

    public static long getUnixTimeMillisFromString(String dateStr)
    {
        // Define the formatter to parse the string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Parse the string into a LocalDateTime object
        LocalDate  dateTime = LocalDate .parse(dateStr, formatter);

        // Convert the LocalDateTime to an Instant
        Instant instant = dateTime.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Get the Unix timestamp (number of seconds since the epoch)
        long unixTimestamp = instant.getEpochSecond();

        // Print the Unix timestamp
        // System.out.println("Unix timestamp: " + unixTimestamp);
        return unixTimestamp;
    }

    private int DateDifferenceCalculatorTimeUnix ( String startDateStr,String endDateStr)
    {

        try {
            long startMillis = Long.parseLong(startDateStr) * 1000;
            long endMillis = Long.parseLong(endDateStr) * 1000;

            long diffInMillis = endMillis - startMillis;
            return (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }

    }

    @Override
    public int getItemCount()
    {
        if(mtaskListMonthData == null)
        {
            return 0;
        }
        else
        {
            return mtaskListMonthData.size();
        }

    }
}