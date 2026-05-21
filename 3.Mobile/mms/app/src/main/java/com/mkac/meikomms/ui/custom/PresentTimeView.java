package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatTextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PresentTimeView extends AppCompatTextView implements View.OnClickListener
{

    private OnTimeSetListener onTimeSetListener;
    protected OnTimeSetListener getOnTimeSetListener() {
        return this.onTimeSetListener;
    }

    public PresentTimeView(Context context) {
        super(context);
        init();
    }

    public PresentTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PresentTimeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        //setOnClickListener(this);
        setOnClickListener(v -> updatePresentTime(0L));
        //updatePresentTime(0L); // Initially update time to current
    }

    public void setOnTimeSetListener(OnTimeSetListener listener) {
        this.onTimeSetListener = listener;
    }

    public void updatePresentTime(Long unixTime)
    {
        if (unixTime == null)
        {
            return;
        }
        long timeToDisplay = (unixTime != 0) ? unixTime : System.currentTimeMillis()/1000;
        Date time = new Date(timeToDisplay*1000);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String formattedTime = sdf.format(time);
        setText(formattedTime);
        if (onTimeSetListener != null)
        {
            onTimeSetListener.onTimeSet(timeToDisplay);  // Pass unixTimeMillis which may be null
        }
    }



    public String getTime() {
        return getText().toString();
    }

    /*private void updatePresentTime() {
        updatePresentTime(null);
    }*/

    @Override
    public void onClick(View v) {
        updatePresentTime(0L); // On click, update to current time
    }

    public String getFormattedDate(Long unixTime) {
        if (unixTime == null) {
            return "";
        }
        if (unixTime == 0) {
            return "";
        }
        Date time = new Date(unixTime*1000);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String formattedTime = sdf.format(time);
        return formattedTime;
    }

    public interface OnTimeSetListener {
        void onTimeSet(Long unixTime);
    }
}
