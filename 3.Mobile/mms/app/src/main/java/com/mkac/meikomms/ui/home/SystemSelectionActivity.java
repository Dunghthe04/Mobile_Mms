package com.mkac.meikomms.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.ui.workorder.WorkOrderHubActivity;
import com.mkac.meikomms.ui.workorder.WmsRequestListActivity;

import org.json.JSONObject;

public class SystemSelectionActivity extends AppCompatActivity {

    private TextView tvUsername;
    private ImageView imgUserProfile;
    private MaterialCardView cardMms;
    private MaterialCardView cardWms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_selection);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        LanguageAPIUtils.init(this);

        tvUsername = findViewById(R.id.tv_username_display);
        imgUserProfile = findViewById(R.id.img_user_profile);
        cardMms = findViewById(R.id.card_mms);
        cardWms = findViewById(R.id.card_wms);

        displayUserInfo();

        cardMms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SystemSelectionActivity.this, WorkOrderHubActivity.class);
                startActivity(intent);
            }
        });

        cardWms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SystemSelectionActivity.this, WmsRequestListActivity.class);
                startActivity(intent);
            }
        });

        if (imgUserProfile != null) {
            imgUserProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLogoutConfirmationDialog();
                }
            });
        }

        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        displayUserInfo();
        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    private void displayUserInfo() {
        if (tvUsername == null) return;

        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");

        String accountName = "";

        if (userObj != null) {
            if (userObj.has("User_Name")) {
                accountName = userObj.optString("User_Name", "").trim();
            } else if (userObj.has("username")) {
                accountName = userObj.optString("username", "").trim();
            } else if (userObj.has("Account")) {
                accountName = userObj.optString("Account", "").trim();
            }

            if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
                accountName = userObj.optString("Full_Name", "").trim();
            }
        }

        if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
            accountName = handler.getString("Userlogin").trim();
        }

        if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
            accountName = "MMS_Account";
        }

        tvUsername.setText(accountName);
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(LanguageAPIUtils.i18n("Logout"))
                .setMessage(LanguageAPIUtils.i18n("Do you want to log out?"))
                .setCancelable(true)
                .setPositiveButton(LanguageAPIUtils.i18n("Logout"), (dialog, which) -> executeLogoutAction())
                .setNegativeButton(LanguageAPIUtils.i18n("Cancel"), (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void executeLogoutAction() {
        try {
            PreferenceHandler handler = new PreferenceHandler(this);
            handler.setBoolean("isLoggedIn", false);
            handler.remove("user");
            handler.remove("api_key");

            Toast.makeText(this, LanguageAPIUtils.i18n("Logout") + " " + LanguageAPIUtils.i18n("Success"), Toast.LENGTH_SHORT).show();

            Class<?> loginActivityClass = Class.forName("com.mkac.meikomms.ui.login.LoginActivity");
            Intent intent = new Intent(this, loginActivityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
