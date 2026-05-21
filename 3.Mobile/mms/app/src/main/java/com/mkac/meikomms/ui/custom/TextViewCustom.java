package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mkac.meikomms.R;

public class TextViewCustom extends LinearLayout
{
    private ImageView img_check;
    private TextView txt_lable;


    public TextViewCustom(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TextViewCustom(Context context)
    {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs)
    {
        LayoutInflater.from(context).inflate(R.layout.view_custom_text_view, this, true);
        img_check = findViewById(R.id.img_check);
        txt_lable = findViewById(R.id.txt_lable);

    }

    public void setLabel(String labelText) {
        if (txt_lable != null) {
            txt_lable.setText(labelText);
        }
    }


    public String getEditText()
    {
        return txt_lable != null ? txt_lable.getText().toString() : "";
    }

    public void setTextSize(int size) {
        if (txt_lable != null) {
            txt_lable.setTextSize(size);
        }
    }

    public void setTextStyle(int style) {
        if (txt_lable != null) {
            txt_lable.setTypeface(txt_lable.getTypeface(), style);
        }
    }

    public void setTextColor(int color) {
        if (txt_lable != null) {
            txt_lable.setTextColor(color);
        }
    }



    public void hideLabel()
    {
        if (txt_lable != null) {
            txt_lable.setVisibility(View.INVISIBLE);
        }
    }

    public void showLabel()
    {
        if (txt_lable != null) {
            txt_lable.setVisibility(View.VISIBLE);
        }
    }


    public void hideIcon()
    {
        if (img_check != null) {
            img_check.setVisibility(View.INVISIBLE);
        }
    }


    public String getViewId() {
        return String.valueOf(getId());
    }





    public void setOnEditTextBackground(Drawable resource) {
        if (txt_lable != null) {
            txt_lable.setBackground(resource);
        }
    }




}
