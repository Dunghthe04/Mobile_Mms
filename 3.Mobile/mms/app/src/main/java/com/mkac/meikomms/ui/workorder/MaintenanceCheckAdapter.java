package com.mkac.meikomms.ui.workorder;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.databinding.ItemMaintenanceCheckRowBinding;
import com.mkac.meikomms.ui.workorder.model.MaintenanceItem;

import java.util.List;

public class MaintenanceCheckAdapter extends RecyclerView.Adapter<MaintenanceCheckAdapter.ViewHolder> {
    private final List<MaintenanceItem> items;
    private final OnItemActionListener actionListener;

    public interface OnItemActionListener {
        void onInfoClick(MaintenanceItem item);
        void onHistoryClick(MaintenanceItem item);
        void onUploadClick(MaintenanceItem item);
    }

    public MaintenanceCheckAdapter(List<MaintenanceItem> items, OnItemActionListener actionListener) {
        this.items = items;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMaintenanceCheckRowBinding binding = ItemMaintenanceCheckRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, actionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MaintenanceItem item = items.get(position);
        if (item == null) return;

        LanguageAPIUtils.setLang(holder.binding.getRoot());
        item.ensureImagePaths();

        holder.binding.tvItemCheckName.setText(item.checkName == null ? "" : item.checkName);
        holder.binding.tvItemSubDesc.setText(item.subDesc == null ? "" : item.subDesc);

        // Cấu hình trạng thái tương tác bảng con
        boolean hasChildren = item.childCount > 0;
        holder.binding.btnItemInfo.setEnabled(hasChildren);
        holder.binding.btnItemInfo.setAlpha(hasChildren ? 1.0f : 0.25f);

        // - Nếu có hạng mục con (hasChildren = true): khóa cứng nút history ở mục cha
        // - Nếu KHÔNG có hạng mục con (hasChildren = false): mở khóa nút history bình thường
        boolean isHistoryEnabled = !hasChildren;
        holder.binding.btnItemHistory.setEnabled(isHistoryEnabled);
        holder.binding.btnItemHistory.setAlpha(isHistoryEnabled ? 1.0f : 0.25f);

        holder.binding.btnItemInfo.setOnClickListener(v -> actionListener.onInfoClick(item));
        holder.binding.btnItemHistory.setOnClickListener(v -> actionListener.onHistoryClick(item));
        holder.binding.btnItemUpload.setOnClickListener(v -> actionListener.onUploadClick(item));

        // Khóa nếu đã được phê duyệt OK HOẶC đây là mục cha có hạng mục con
        boolean isLocked = "OK".equalsIgnoreCase(item.initialStatus);
        boolean disableManualInput = isLocked || hasChildren;

        // Quản lý ô nhập dữ liệu Ghi chú/Comment an toàn
        holder.binding.etItemComment.setText(item.comment == null ? "" : item.comment);
        if (holder.commentWatcher != null) {
            holder.binding.etItemComment.removeTextChangedListener(holder.commentWatcher);
        }
        holder.commentWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                item.comment = s.toString();
                item.refreshChangedState();
            }
        };
        holder.binding.etItemComment.addTextChangedListener(holder.commentWatcher);

        // Xóa sạch Listener cũ trước khi nạp dữ liệu mới để tránh hiện tượng kích hoạt đè vòng lặp
        holder.binding.rgAssessment.setOnCheckedChangeListener(null);
        if (holder.displayWatcher != null) {
            holder.binding.etCheckValueDisplay.removeTextChangedListener(holder.displayWatcher);
        }
        if (holder.actualWatcher != null) {
            holder.binding.etCheckValueActual.removeTextChangedListener(holder.actualWatcher);
        }

        // --- 4.1 PHÂN LOẠI HIỂN THỊ INPUT ---
        if (item.isRadioInput()) {
            holder.binding.rgAssessment.setVisibility(View.VISIBLE);
            holder.binding.layoutNumericInputs.setVisibility(View.GONE);

            holder.binding.rgAssessment.clearCheck();
            if ("OK".equalsIgnoreCase(item.checkValue)) {
                holder.binding.rbOk.setChecked(true);
            } else if ("NG".equalsIgnoreCase(item.checkValue)) {
                holder.binding.rbNg.setChecked(true);
            }

            holder.binding.rbOk.setEnabled(!disableManualInput);
            holder.binding.rbNg.setEnabled(!disableManualInput);

            holder.binding.rgAssessment.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == -1) return;
                item.checkValue = (checkedId == holder.binding.rbOk.getId()) ? "OK" : "NG";
                item.initialStatus = item.checkValue;
                updateStatusIndicator(holder, item.initialStatus);
                item.refreshChangedState();
            });
        } else {
            holder.binding.rgAssessment.setVisibility(View.GONE);
            holder.binding.layoutNumericInputs.setVisibility(View.VISIBLE);

            holder.binding.etCheckValueDisplay.setText(item.checkValue == null ? "" : item.checkValue);
            holder.binding.etCheckValueActual.setText(item.checkValue2 == null ? "" : item.checkValue2);

            holder.binding.etCheckValueDisplay.setEnabled(!disableManualInput);
            holder.binding.etCheckValueActual.setEnabled(!disableManualInput);

            holder.displayWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    item.checkValue = s.toString().trim();
                    validateNumericInputsRealtime(holder, item);
                    item.refreshChangedState();
                }
            };
            holder.actualWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    item.checkValue2 = s.toString().trim();
                    validateNumericInputsRealtime(holder, item);
                    item.refreshChangedState();
                }
            };
            holder.binding.etCheckValueDisplay.addTextChangedListener(holder.displayWatcher);
            holder.binding.etCheckValueActual.addTextChangedListener(holder.actualWatcher);

            validateNumericInputsRealtime(holder, item);
        }

        holder.binding.getRoot().setTag(item);
        if (holder.binding.rvCheckPreviews != null) {
            bindImagePreviews(holder, item);
        }

        updateStatusIndicator(holder, item.initialStatus);
    }

    private void bindImagePreviews(ViewHolder holder, MaintenanceItem item) {
        if (holder.previewAdapter == null) {
            holder.binding.rvCheckPreviews.setLayoutManager(
                    new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            holder.binding.rvCheckPreviews.setNestedScrollingEnabled(false);
            holder.previewAdapter = new MaintenanceImagePreviewAdapter(holder.itemView.getContext());
            holder.binding.rvCheckPreviews.setAdapter(holder.previewAdapter);
        }

        java.util.List<String> images = item.getImagePathsSnapshot();
        holder.previewAdapter.submitImages(images);
        holder.binding.rvCheckPreviews.setVisibility(images.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void validateNumericInputsRealtime(ViewHolder holder, MaintenanceItem item) {
        String displayStr = item.checkValue;
        String actualStr = item.checkValue2;

        if (displayStr == null || displayStr.isEmpty() || actualStr == null || actualStr.isEmpty()) {
            item.initialStatus = "";
            holder.binding.etCheckValueDisplay.setTextColor(Color.BLACK);
            holder.binding.etCheckValueActual.setTextColor(Color.BLACK);
            updateStatusIndicator(holder, "");
            return;
        }

        try {
            double min = (item.min == null || item.min.trim().isEmpty()) ? Double.NEGATIVE_INFINITY : Double.parseDouble(item.min.trim());
            double max = (item.max == null || item.max.trim().isEmpty()) ? Double.POSITIVE_INFINITY : Double.parseDouble(item.max.trim());
            double displayVal = Double.parseDouble(displayStr);
            double actualVal = Double.parseDouble(actualStr);

            boolean isOk = (displayVal >= min && displayVal <= max) && (actualVal >= min && actualVal <= max);
            item.initialStatus = isOk ? "OK" : "NG";

            int colorResult = isOk ? Color.parseColor("#047857") : Color.parseColor("#B91C1C");
            holder.binding.etCheckValueDisplay.setTextColor(colorResult);
            holder.binding.etCheckValueActual.setTextColor(colorResult);

            updateStatusIndicator(holder, item.initialStatus);
        } catch (Exception e) {
            item.initialStatus = "NG";
            holder.binding.etCheckValueDisplay.setTextColor(Color.parseColor("#B91C1C"));
            holder.binding.etCheckValueActual.setTextColor(Color.parseColor("#B91C1C"));
            updateStatusIndicator(holder, "NG");
        }
    }

    private void updateStatusIndicator(ViewHolder holder, String status) {
        int color;
        if ("OK".equalsIgnoreCase(status)) {
            color = Color.parseColor("#047857");
        } else if ("NG".equalsIgnoreCase(status)) {
            color = Color.parseColor("#B91C1C");
        } else {
            color = Color.parseColor("#E5E7EB");
        }
        holder.binding.viewStatusIndicator.setBackgroundColor(color);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemMaintenanceCheckRowBinding binding;
        TextWatcher displayWatcher;
        TextWatcher actualWatcher;
        TextWatcher commentWatcher;
        MaintenanceImagePreviewAdapter previewAdapter;

        ViewHolder(ItemMaintenanceCheckRowBinding binding, OnItemActionListener actionListener) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}