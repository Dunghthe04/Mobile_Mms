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
        if (status == null) return "Chưa làm";
        switch (status) {
            case "1":
                return i18n("Đã làm");
            case "2":
                return i18n("Checksheet OK");
            case "3":
                return i18n("Checksheet NG");
            case "5":
                return i18n("Overdue");
            default:
                return i18n("Pending");
        }
    }
}
