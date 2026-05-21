package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mkac.meikomms.R;

public class UploadButton extends RelativeLayout
{
    private TextView txt_lable,txt_lable_2;
    private ImageView img_upload;


    public UploadButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public UploadButton(Context context)
    {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs)
    {
        LayoutInflater.from(context).inflate(R.layout.view_custom_upload_button, this, true);
        txt_lable = findViewById(R.id.txt_lable);
        txt_lable_2 = findViewById(R.id.txt_lable_2);
        img_upload = findViewById(R.id.img_upload);

    }

    public void setLabel(String label)
    {
        this.txt_lable.setText(label);
    }

    public void setLabel2(String label)
    {
        this.txt_lable_2.setText(label);
    }
}
