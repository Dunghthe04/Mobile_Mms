package com.mkac.meikomms;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.ui.login.LoginActivity;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private RelativeLayout menu_home, menu_checksheet, menu_setting, btn_menu_user;
    private ImageView icon_menu_home, icon_menu_checksheet, icon_menu_setting, btn_menu, user_icon;
    private String user_login = "";
    private TextView user_id_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        // Ánh xạ View
        btn_menu_user = findViewById(R.id.btn_menu_user);
        menu_home = findViewById(R.id.menu_home);
        menu_checksheet = findViewById(R.id.menu_checksheet);
        menu_setting = findViewById(R.id.menu_setting);
        
        icon_menu_home = findViewById(R.id.icon_menu_home);
        icon_menu_checksheet = findViewById(R.id.icon_menu_checksheet);
        icon_menu_setting = findViewById(R.id.icon_menu_setting);
        
        user_id_login = findViewById(R.id.user_id_login);
        user_icon = findViewById(R.id.user_icon);
        btn_menu = findViewById(R.id.btn_menu);
        viewPager = findViewById(R.id.view_pager2);

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setUserInputEnabled(false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateUIForPosition(position);
            }
        });

        // Click Events
        menu_home.setOnClickListener(v -> viewPager.setCurrentItem(0));
        menu_checksheet.setOnClickListener(v -> viewPager.setCurrentItem(1));
        menu_setting.setOnClickListener(v -> viewPager.setCurrentItem(2));

        btn_menu.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() != 0) {
                viewPager.setCurrentItem(0);
            }
        });

        btn_menu_user.setOnClickListener(v -> LogoutDialog(Gravity.CENTER, "LOGOUT", "Do you want to log out?"));

        // Load User Info
        PreferenceHandler handler = new PreferenceHandler(this);
        if (!handler.getString("Userlogin").isEmpty()) {
            user_login = handler.getString("Userlogin");
            user_id_login.setText(user_login);
        }

        ConfigManager configManager = new ConfigManager(this);
        if (configManager.getProperty("vertical_lock").equals("true")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void updateUIForPosition(int position) {
        // Reset Icons về trạng thái Unselected và loại bỏ background
        icon_menu_home.setImageResource(R.drawable.icon_home_unselect);
        icon_menu_home.setBackground(null);
        
        icon_menu_checksheet.setImageResource(R.drawable.ic_checksheet_unselected);
        icon_menu_checksheet.setBackground(null);
        icon_menu_checksheet.setPadding(0, 0, 0, 0);

        icon_menu_setting.setImageResource(R.drawable.ic_settings_unselected);
        icon_menu_setting.setBackground(null);
        
        btn_menu.setImageResource(R.drawable.menu_24);

//        switch (position) {
//            case 0: // Home
////                icon_menu_home.setImageResource(R.drawable.icon_home_select);
//                icon_menu_checksheet.setImageResource(R.drawable.ic_checksheet_selected);
//                btn_menu.setImageResource(R.drawable.back_24);
//                break;
//            case 1: // Checksheet
//                icon_menu_home.setImageResource(R.drawable.icon_home_select);
////                icon_menu_checksheet.setImageResource(R.drawable.ic_checksheet_selected);
////                btn_menu.setImageResource(R.drawable.back_24);
//                break;
//            case 2: // Setting
//                icon_menu_setting.setImageResource(R.drawable.ic_settings_selected);
//                btn_menu.setImageResource(R.drawable.back_24);
//                break;
//        }

        icon_menu_checksheet.setImageResource(R.drawable.ic_checksheet_selected);
        btn_menu.setImageResource(R.drawable.back_24);
    }

    private void LogoutDialog(int gravity, String tieude, String noidung) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_yesno);

        TextView btncancel = dialog.findViewById(R.id.btnclose);
        TextView btnOK = dialog.findViewById(R.id.btnlogout);
        TextView txtnoidung = dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = dialog.findViewById(R.id.btn_close);

        txtnoidung.setText(i18n(noidung));
        tvTitle.setText(i18n(tieude));
        btnOK.setText(i18n("YES"));
        btncancel.setText(i18n("NO"));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
        }

        btncancel.setOnClickListener(v -> dialog.dismiss());
        btn_close.setOnClickListener(v -> dialog.dismiss());
        btnOK.setOnClickListener(v -> {
            PreferenceHandler handler = new PreferenceHandler(getBaseContext());
            handler.setBoolean("isLoggedIn", false);
            dialog.dismiss();
            finish();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            activityResultLaunch.launch(intent);
        });
        dialog.show();
    }

    ActivityResultLauncher<Intent> activityResultLaunch = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> { });

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    hideKeyboard(v);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        TextView header_home_title = findViewById(R.id.lable_screen_header);
        header_home_title.setText(i18n("MAINTENANCE MANAGEMENT SYSTEM"));
    }
}
