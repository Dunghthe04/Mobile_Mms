package com.mkac.meikomms.data;

public class MaintenanceStep {
    public static final int TYPE_CHECKBOX = 1;
    public static final int TYPE_INPUT = 0;
    public static final int TYPE_RADIO = 2;
    private int type; // TYPE_CHECKBOX or TYPE_INPUT
    private String content;
    private boolean checked; // for checkbox
    private String value;
    private String checkid;// for input
    private String min;
    private String max;

    public MaintenanceStep(String check_id ,int type, String content, boolean checked, String value, String min, String max)
    {
        this.checkid = check_id;
        this.type = type;
        this.content = content;
        this.checked = checked;
        this.value = value;
        this.min = min;
        this.max = max;
    }



    public int getType() {
        return type;

    }
    public String getcheckid() {
        return checkid;

    }

    public String getContent() {
        return content;
    }

    public boolean isChecked() {
        return  checked;
    }

    public String getisChecked() {
        return  String.valueOf(checked);
    }

    public String getValue() {
        return value;
    }

    public String getMax() {
        return max;
    }
    public String getMin() {
        return min;
    }

    public void setChecked(boolean isChecked) {
        this.checked = isChecked;
    }

    public void setValue(String v) {
        this.value = v;
    }

    public boolean getOkSelected()
    {
        if(value.equals("OK"))
        {
            return true;
        }
        else if(value.equals("NG"))
        {
            return false;
        }
        return false;
    }

    public void setOkSelected(boolean b)
    {
        if (b)
            value = "OK";
        else
            value = "NG";
    }
}
