package com.mkac.meikomms.ui;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class FragmentProfile  implements Serializable {
    public String screenTitle; // Tiêu đề màn hình (Dùng để check Mode)
    public String schemaMms; // Schema quản lý bảo trì
    public String schemaData; // Schema dữ liệu động
    public String schemaCore; // Schema hệ thống core

    //TODO: định nghĩa giá trị DB
    public Map<String, StatusInfo> statusMapping;

    //TODO: danh sách json trong table
    public List<String> columnKeys;

    // TODO: Danh sách tiêu đề hiển thị tương ứng (VD: "Mã WO", "Tên máy")
    public List<String> columnLabels;

    public static class StatusInfo implements Serializable {
        public String label;
        public String colorHex;
        public StatusInfo(String label, String colorHex){
            this.label = label;
            this.colorHex = colorHex;
        }
    }
}
