package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mkac.meikomms.R;


public class LabeledEditTextCustom extends LinearLayout
{
    private TextView label, label2;
    private EditText editText;

    public LabeledEditTextCustom(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LabeledEditTextCustom(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.custom_edit_text, this, true);
        label = findViewById(R.id.label);
        label2 = findViewById(R.id.label2);
        editText = findViewById(R.id.edit_text_custom);

    }

    public void setLabel(String labelText) {
        if (label != null) {
            label.setText(labelText);
        }
    }

    public void setEditText(String text) {
        if (editText != null) {
            editText.setText(text);
        }
    }

    public void setHintEditText(String hintText) {
        if (editText != null) {
            editText.setHint(hintText);
        }
    }

    public String getEditText() {
        return editText != null ? editText.getText().toString() : "";
    }

    public void setTextSize(int size) {
        if (editText != null) {
            editText.setTextSize(size);
        }
    }

    public void setTextStyle(int style) {
        if (editText != null) {
            editText.setTypeface(editText.getTypeface(), style);
        }
    }

    public void setTextColor(int color) {
        if (editText != null) {
            editText.setTextColor(color);
        }
    }

    public void setInputType(int inputType) {
        if (editText != null) {
            editText.setInputType(inputType);
        }
    }

    public void hideLabel2() {
        if (label2 != null) {
            label2.setVisibility(View.INVISIBLE);
        }
    }

    public String getViewId() {
        return String.valueOf(getId());
    }

    public void addTextChangedListener(TextWatcher textWatcher) {
        if (editText != null) {
            editText.addTextChangedListener(textWatcher);
        }
    }

    public void setOnEditTextClickListener(OnClickListener listener) {
        if (editText != null) {
            editText.setOnClickListener(listener);
        }
    }

    public void setOnEditTextTouchListener(OnTouchListener listener) {
        if (editText != null) {
            editText.setOnTouchListener(listener);
        }
    }

    public void setOnEditTextBackground(Drawable resource) {
        if (editText != null) {
            editText.setBackground(resource);
        }
    }

    public void setInputType()
    {
        editText.setInputType(InputType.TYPE_NULL);

    }

    public void setMaxLines(int maxLines)
    {
        if (editText != null)
        {
            editText.setMaxLines(maxLines);
        }

    }




}
