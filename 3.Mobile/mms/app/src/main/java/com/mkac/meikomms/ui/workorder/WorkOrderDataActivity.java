package com.mkac.meikomms.ui.workorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.mkac.meikomms.R;

public class WorkOrderDataActivity extends AppCompatActivity {

    private static final String TAG_WORK_ORDER = "tab_work_order";
    private static final String TAG_MAINTENANCE = "tab_maintenance";

    private TextView btnTabWorkOrder;
    private TextView btnTabMaintenance;
    private View lineTabWorkOrder;
    private View lineTabMaintenance;

    private WorkOrderDataListFragment workOrderListFragment;
    private MaintenanceTabFragment maintenanceTabFragment;

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderDataActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_order_data);

        btnTabWorkOrder = findViewById(R.id.btn_tab_work_order);
        btnTabMaintenance = findViewById(R.id.btn_tab_maintenance);
        lineTabWorkOrder = findViewById(R.id.line_tab_work_order);
        lineTabMaintenance = findViewById(R.id.line_tab_maintenance);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        ImageView updateIcon = findViewById(R.id.update_icon);
        if (updateIcon != null) {
            updateIcon.setOnClickListener(v -> refreshCurrentTab());
        }

        findViewById(R.id.tab_work_order_container).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.tab_maintenance_container).setOnClickListener(v -> selectTab(1));
        if (btnTabWorkOrder != null) btnTabWorkOrder.setOnClickListener(v -> selectTab(0));
        if (btnTabMaintenance != null) btnTabMaintenance.setOnClickListener(v -> selectTab(1));

        if (savedInstanceState == null) {
            workOrderListFragment = new WorkOrderDataListFragment();
            maintenanceTabFragment = new MaintenanceTabFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, workOrderListFragment, TAG_WORK_ORDER)
                    .add(R.id.fragment_container, maintenanceTabFragment, TAG_MAINTENANCE)
                    .hide(maintenanceTabFragment)
                    .commit();
        } else {
            workOrderListFragment = (WorkOrderDataListFragment) getSupportFragmentManager().findFragmentByTag(TAG_WORK_ORDER);
            maintenanceTabFragment = (MaintenanceTabFragment) getSupportFragmentManager().findFragmentByTag(TAG_MAINTENANCE);
        }

        selectTab(0);
    }

    private void selectTab(int index) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (index == 0) {
            if (workOrderListFragment != null) tx.show(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.hide(maintenanceTabFragment);
            updateTabUi(true);
        } else {
            if (workOrderListFragment != null) tx.hide(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.show(maintenanceTabFragment);
            updateTabUi(false);
        }
        tx.commit();
    }

    private void updateTabUi(boolean workOrderSelected) {
        int activeColor = Color.parseColor("#00A680");
        int inactiveColor = Color.parseColor("#5C5C5C");
        int activeLine = activeColor;
        int inactiveLine = Color.parseColor("#F4F2F2");

        if (btnTabWorkOrder != null) {
            btnTabWorkOrder.setTextColor(workOrderSelected ? activeColor : inactiveColor);
            btnTabWorkOrder.setTypeface(null, workOrderSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (btnTabMaintenance != null) {
            btnTabMaintenance.setTextColor(workOrderSelected ? inactiveColor : activeColor);
            btnTabMaintenance.setTypeface(null, workOrderSelected ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        }
        if (lineTabWorkOrder != null) lineTabWorkOrder.setBackgroundColor(workOrderSelected ? activeLine : inactiveLine);
        if (lineTabMaintenance != null) lineTabMaintenance.setBackgroundColor(workOrderSelected ? inactiveLine : activeLine);
    }

    private void refreshCurrentTab() {
        if (workOrderListFragment != null && workOrderListFragment.isVisible()) {
            workOrderListFragment.reloadData();
        }
    }
}
