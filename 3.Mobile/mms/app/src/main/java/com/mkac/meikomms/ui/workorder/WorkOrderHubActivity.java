package com.mkac.meikomms.ui.workorder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONObject;

public class WorkOrderHubActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderHubActivity.class));
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_order_hub);

        // 1. Ánh xạ các CardView từ giao diện XML
        LinearLayout card_create_work_order = findViewById(R.id.btn_create_work_order);
        LinearLayout btn_enter_work_order_data = findViewById(R.id.btn_enter_work_order_data);


        // 2. Xử lý sự kiện click cho Module WorkOrder
        card_create_work_order.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WorkOrderHubActivity.this, ListWorkOrderActivity.class);
                startActivity(intent);
            }
        });

        // 3. Xử lý sự kiện click cho Module A
        btn_enter_work_order_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WorkOrderHubActivity.this, WorkOrderDataActivity.class);
                startActivity(intent);
            }
        });
    }




    private void displayUserInfo() {
        TextView tvUsername = findViewById(R.id.tv_username_display);
        if (tvUsername == null) return;
        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");
        if (userObj != null) {
            String name = userObj.optString("Full_Name", userObj.optString("User_Name", "Guest"));
            tvUsername.setText(name);
        }
    }
}
