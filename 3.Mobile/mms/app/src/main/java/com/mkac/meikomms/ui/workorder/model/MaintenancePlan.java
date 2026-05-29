package com.mkac.meikomms.ui.workorder.model;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

public class MaintenancePlan {
    public String taskId;
    public String machineId;
    public String machineName;
    public String categoryId;
    public String categoryName;
    public String assigneeName;
    public String executorName;
    public String status;
    public long taskDateUnix;
    public String completedDate;

    public String getStatusLabel() {
        if (status == null) return i18n("Incomplete");
        switch (status) {
            case "0":
                return i18n("Incomplete");      // Chưa hoàn thành (Màu xám)
            case "1":
                return i18n("Approve");         // Phê duyệt (Màu xanh dương - Sửa từ "Đã làm")
            case "2":
                return i18n("Checksheet OK");   // Checksheet OK (Màu xanh lá)
            case "3":
                return i18n("Checksheet NG");   // Checksheet NG (Màu đỏ/hồng)
            case "5":
                return i18n("Overdue");         // Quá hạn (Màu cam)
            default:
                return i18n("Incomplete");
        }
    }
}
