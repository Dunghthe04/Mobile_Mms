package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mkac.meikomms.R;


public class LabeledEditText extends LinearLayout {

    private TextView label,label2;
    private TextView editText;


    public LabeledEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LabeledEditText(Context context)
    {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs)
    {
        LayoutInflater.from(context).inflate(R.layout.view_custom_edit_text, this, true);
        label = findViewById(R.id.label);
        editText = findViewById(R.id.edit_text);
        label2 = findViewById(R.id.label2);

    }

    public void setLabel(String label)
        {
        this.label.setText(label);
    }

    public void setEditText(String text)
    {
        this.editText.setText(text);
    }

    public void setHintEditText(String text)
    {
        this.editText.setHint(text);
    }

    public String getEditText()
    {
       return editText.getText().toString();
    }

    public void setTextsize(int size)
    {
        editText.setTextSize(size);

    }

    public void setTexStyle(int style)
    {
         editText.setTypeface(editText.getTypeface(), style);

    }

    public void setTextColor(int color)
    {
        editText.setTextColor(color);

    }

    public void setHidered()
    {
        label2.setVisibility(View.INVISIBLE);

    }

    public void setInputType()
    {
        editText.setInputType(InputType.TYPE_NULL);

    }

    public void setOnEditTextBackground(Drawable resource)
    {
        if (editText != null)
        {
            editText.setBackground(resource);
        }
    }





}
