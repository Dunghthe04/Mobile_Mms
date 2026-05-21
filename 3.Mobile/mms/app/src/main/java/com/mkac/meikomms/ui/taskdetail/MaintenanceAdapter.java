package com.mkac.meikomms.ui.taskdetail;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.recyclerview.widget.RecyclerView;

import com.mkac.meikomms.R;
import com.mkac.meikomms.data.MaintenanceStep;
import com.mkac.meikomms.ui.custom.TextViewCustom;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MaintenanceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{

    private final List<MaintenanceStep> stepList;
    private final Context context;

    public MaintenanceAdapter(Context context, List<MaintenanceStep> stepList) {
        this.context = context;
        this.stepList = stepList;
    }

    @Override
    public int getItemViewType(int position) {
        return stepList.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return stepList.size();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == MaintenanceStep.TYPE_CHECKBOX) {
            View view = inflater.inflate(R.layout.item_maintenance_checkbox, parent, false);
            return new CheckboxViewHolder(view);
        } else if (viewType == MaintenanceStep.TYPE_INPUT) {
            View view = inflater.inflate(R.layout.item_maintenance_input, parent, false);
            return new InputViewHolder(view);
        } else if (viewType == MaintenanceStep.TYPE_RADIO) {
            View view = inflater.inflate(R.layout.item_maintenance_radio, parent, false);
            return new RadioViewHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        MaintenanceStep step = stepList.get(position);
        if (holder instanceof CheckboxViewHolder) {
            ((CheckboxViewHolder) holder).bind(step);
        } else if (holder instanceof InputViewHolder) {
            ((InputViewHolder) holder).bind(step);
        }else if (holder instanceof RadioViewHolder) {
            ((RadioViewHolder) holder).bind(step);
        }
    }

    class CheckboxViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView tvContent;

        CheckboxViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox);
            tvContent = itemView.findViewById(R.id.tv_content);
        }

        void bind(MaintenanceStep step) {
            tvContent.setText(step.getContent());
            checkBox.setChecked(step.isChecked());

            // Ngăn loop khi setChecked
            checkBox.setOnCheckedChangeListener(null);

            checkBox.setChecked(step.isChecked());
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> step.setChecked(isChecked));

            // Gán click cho toàn bộ itemView
            itemView.setOnClickListener(v -> {
                boolean newState = !checkBox.isChecked();
                checkBox.setChecked(newState);
                step.setChecked(newState);
            });
        }
    }

    class RadioViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        RadioGroup radioGroup;
        RadioButton radioOk, radioNg;

        RadioViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            radioGroup = itemView.findViewById(R.id.radio_group);
            radioOk = itemView.findViewById(R.id.radio_ok);
            radioNg = itemView.findViewById(R.id.radio_ng);
        }

        void bind(MaintenanceStep step) {
            tvContent.setText(step.getContent());

            // Reset radio group
            radioGroup.setOnCheckedChangeListener(null);
            if (step.getOkSelected()) {
                radioOk.setChecked(true);
            } else {
                radioNg.setChecked(true);
            }

            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radio_ok) {
                    step.setOkSelected(true);
                } else if (checkedId == R.id.radio_ng) {
                    step.setOkSelected(false);
                }
            });
        }
    }

    class InputViewHolder extends RecyclerView.ViewHolder {
        EditText etValue;
        TextView tvContent, tvRange;

        InputViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            etValue = itemView.findViewById(R.id.et_value);
            tvRange = itemView.findViewById(R.id.tv_range);
        }

        void bind(MaintenanceStep step) {
            tvContent.setText(step.getContent());
            if (step.getValue() != null)
                etValue.setText(String.valueOf(step.getValue()));

            tvRange.setText("Min - Max: " + step.getMin() + " - " + step.getMax());
            etValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    try {
                        step.setValue(editable.toString());
                    } catch (NumberFormatException e) {
                        step.setValue("0");
                    }
                }
            });


        }
    }


    public JSONArray getUserInputAsJsonArray(String task_Id) {
        JSONArray jsonArray = new JSONArray();

        for (MaintenanceStep step : stepList) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("Task_Id", task_Id);
                obj.put("Check_Id", step.getcheckid());

                switch (step.getType()) {
                    case 1:
                        obj.put("Check_Value", step.getisChecked());
                        break;

                    case 0:
                        obj.put("Check_Value", step.getValue());
                        break;

                    case 2:
                        obj.put("Check_Value", step.getOkSelected() ? "OK" : "NG");
                        break;
                }

                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return jsonArray;
    }


}
