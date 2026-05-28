package com.mkac.meikomms.ui.workorder;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.databinding.ItemMaintenancePlanCardBinding;
import com.mkac.meikomms.ui.workorder.model.MaintenancePlan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MaintenancePlanAdapter extends RecyclerView.Adapter<MaintenancePlanAdapter.ViewHolder> {
    private static final String KEY_LABEL_CATEGORY = "Category Name";
    private static final String KEY_LABEL_ASSIGNEE = "Person in charge";
    private static final String KEY_LABEL_EXECUTOR = "Execute task";
    private static final String KEY_LABEL_PLANNED = "Plan";
    private static final String KEY_LABEL_COMPLETED = "Done";
    private static final String KEY_STATUS_DONE = "Done";
    private static final String KEY_STATUS_OVERDUE = "Overdue";
    private static final String KEY_STATUS_ON_HOLD = "On hold";
    private static final String KEY_ACTION_INSPECT = "Proceed with inspection";

    private final List<MaintenancePlan> list;
    private final OnPlanClickListener listener;
    private long lastClickTime = 0;

    public interface OnPlanClickListener {
        void onEditClick(MaintenancePlan plan);
    }

    public MaintenancePlanAdapter(List<MaintenancePlan> list, OnPlanClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMaintenancePlanCardBinding binding = ItemMaintenancePlanCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        LanguageAPIUtils.setLang(binding.getRoot());
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MaintenancePlan plan = list.get(position);
        if (plan == null) return;

        // 1. Máy (Machine): Mã máy và tên máy (Ghép nối an toàn, rỗng thì hiển thị "")
        String mId = safe(plan.machineId);
        String mName = safe(plan.machineName);
        String machineTitle = "";
        if (!mId.isEmpty() && !mName.isEmpty()) {
            machineTitle = mId + " - " + mName;
        } else if (!mId.isEmpty()) {
            machineTitle = mId;
        } else if (!mName.isEmpty()) {
            machineTitle = mName;
        }
        holder.binding.tvPlanMachineTitle.setText(machineTitle);

        // 2. Hạng mục bảo dưỡng (Nếu rỗng thì hiển thị "")
        String catName = safe(plan.categoryName);
        holder.binding.tvPlanCategoryName.setText(catName.isEmpty() ? "" : i18n(KEY_LABEL_CATEGORY) + ": " + catName);

        // 3. Người phụ trách (Nếu rỗng thì hiển thị "")
        String pic = safe(plan.assigneeName);
        holder.binding.tvPlanAssigneeUser.setText(pic.isEmpty() ? "" : i18n(KEY_LABEL_ASSIGNEE) + ": " + pic);

        // 4. Người thực hiện (Nếu rỗng thì hiển thị "")
        String exe = safe(plan.executorName);
        holder.binding.tvPlanExecutorUser.setText(exe.isEmpty() ? "" : i18n(KEY_LABEL_EXECUTOR) + ": " + exe);

        // 5. Ngày dự kiến thực hiện (YYYY-MM-DD, Nếu lỗi/rỗng hiển thị "")
        if (plan.taskDateUnix <= 0) {
            holder.binding.tvPlanTaskDate.setText("");
        } else {
            holder.binding.tvPlanTaskDate.setText(i18n(KEY_LABEL_PLANNED) + ": " + formatUnixDate(plan.taskDateUnix));
        }

        // 6. Ngày hoàn thành (Nếu không có hiển thị "")
        String completedStr = safe(plan.completedDate);
        holder.binding.tvPlanDoneDate.setText(completedStr.isEmpty() ? "" : i18n(KEY_LABEL_COMPLETED) + ": " + completedStr);

        // 7. Cấu hình màu sắc trạng thái
        String statusValue = safe(plan.status);
        if (statusValue.isEmpty()) {
            holder.binding.tvPlanStatusBadge.setText("");
            holder.binding.tvPlanStatusBadge.setVisibility(View.INVISIBLE);
        } else {
            holder.binding.tvPlanStatusBadge.setVisibility(View.VISIBLE);

            switch (statusValue) {
                case "0":
                    holder.binding.tvPlanStatusBadge.setText(plan.getStatusLabel());
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#F3F4F6")); // Nền Xám
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#4B5563"));       // Chữ Đen Xám
                    break;
                case "1":
                    holder.binding.tvPlanStatusBadge.setText(i18n(KEY_STATUS_DONE) + " / " + i18n("Approve"));
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#EFF6FF")); // Nền Xanh dương nhạt
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#2563EB"));       // Chữ Xanh dương đậm
                    break;
                case "2":
                    holder.binding.tvPlanStatusBadge.setText(i18n("Checksheet OK"));
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#D1FAE5")); // Nền Xanh lá nhạt
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#047857"));       // Chữ Xanh lá đậm
                    break;
                case "3":
                    holder.binding.tvPlanStatusBadge.setText(i18n("Checksheet NG"));
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#FEE2E2")); // Nền Đỏ nhạt
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#B91C1C"));       // Chữ Đỏ đậm
                    break;
                case "5":
                    holder.binding.tvPlanStatusBadge.setText(i18n(KEY_STATUS_OVERDUE));
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#FFEEEE")); // Nền Đỏ tươi nhạt
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#E11D48"));       // Chữ Hồng Đỏ đậm
                    break;
                default:
                    holder.binding.tvPlanStatusBadge.setText(i18n(KEY_STATUS_ON_HOLD));
                    holder.binding.tvPlanStatusBadge.setBackgroundColor(Color.parseColor("#F3F4F6"));
                    holder.binding.tvPlanStatusBadge.setTextColor(Color.parseColor("#4B5563"));
                    break;
            }
        }


        // 8. Nút bấm hành động sửa/kiểm tra [Edit]
        holder.binding.btnPlanEdit.setText(i18n(KEY_ACTION_INSPECT));
        holder.binding.btnPlanEdit.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 800) return;
            lastClickTime = currentTime;

            if (listener != null) {
                listener.onEditClick(plan);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private static String formatUnixDate(long unixValue) {
        if (unixValue <= 0) return "";
        try {
            long normalized = unixValue > 9999999999L ? unixValue : unixValue * 1000L;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            formatter.setTimeZone(TimeZone.getDefault());
            return formatter.format(new Date(normalized));
        } catch (Exception e) {
            return "";
        }
    }

    private String safe(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private String i18n(String key) {
        return LanguageAPIUtils.i18n(key);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemMaintenancePlanCardBinding binding;
        ViewHolder(ItemMaintenancePlanCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
