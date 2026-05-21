package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;

import com.mkac.meikomms.R;

public class Checkbox_custom extends AppCompatImageView implements Checkable
{

    private boolean isChecked = false;
    private Drawable checkedDrawable, uncheckedDrawable;

    public Checkbox_custom(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        checkedDrawable = getResources().getDrawable(R.drawable.checkbox_tick);
        uncheckedDrawable = getResources().getDrawable(R.drawable.checkbox_untick);
        setImageDrawable(uncheckedDrawable);
        setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggle();
            }
        });
    }

    @Override
    public void setChecked(boolean checked)
    {
        isChecked = checked;
        setImageDrawable(isChecked ? checkedDrawable : uncheckedDrawable);
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }


}
