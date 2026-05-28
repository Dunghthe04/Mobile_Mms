package com.mkac.meikomms.ui.workorder;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentTransaction;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.Barcode;
import com.mkac.meikomms.common.LanguageAPIUtils;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class WorkOrderDataActivity extends AppCompatActivity {
    private static final String EXTRA_LANGUAGE_CODE = "LANGUAGE_CODE";
    private static final String EXTRA_MACHINE_ID = "MACHINE_ID";
    private static final int REQUEST_SCAN_MACHINE = 1201;

    private static final String TAG_WORK_ORDER = "tab_work_order";
    private static final String TAG_MAINTENANCE = "tab_maintenance";

    private TextView btnTabWorkOrder;
    private TextView btnTabMaintenance;
    private TextView tvMachineScanValue;
    private View lineTabWorkOrder;
    private View lineTabMaintenance;
    private View btnScanMachine;

    private Spinner spinnerWoStatus;
    private Spinner spinnerMaintStatus;

    private View btnFilterWoDate;
    private TextView tvFilterWoDateLabel;
    private View btnFilterMaintDate;
    private TextView tvFilterMaintDateLabel;
    private String selectedWoDate = "";
    private String selectedMaintDate = "";

    private WorkOrderDataListFragment workOrderListFragment;
    private MaintenanceTabFragment maintenanceTabFragment;
    private String currentMachineId = "";
    private ActivityResultLauncher<Intent> scanMachineLauncher;
    private String workOrderMachineId = "";
    private String maintenanceMachineId = "";
    private int currentSelectedTab = 0;// 0: Work Order, 1: Maintenance

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderDataActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyIncomingLanguageFromIntent();
        setContentView(R.layout.activity_work_order_data);

        LanguageAPIUtils.init(this);

        btnTabWorkOrder = findViewById(R.id.btn_tab_work_order);
        btnTabMaintenance = findViewById(R.id.btn_tab_maintenance);
        tvMachineScanValue = findViewById(R.id.tv_machine_scan_value);
        btnScanMachine = findViewById(R.id.btn_scan_machine);
        lineTabWorkOrder = findViewById(R.id.line_tab_work_order);
        lineTabMaintenance = findViewById(R.id.line_tab_maintenance);
        spinnerWoStatus = findViewById(R.id.spinner_filter_wo_status);
        spinnerMaintStatus = findViewById(R.id.spinner_filter_maint_status);

        btnFilterWoDate = findViewById(R.id.btn_filter_wo_date);
        tvFilterWoDateLabel = findViewById(R.id.tv_filter_wo_date_label);
        btnFilterMaintDate = findViewById(R.id.btn_filter_maint_date);
        tvFilterMaintDateLabel = findViewById(R.id.tv_filter_maint_date_label);

        // Khởi tạo biến lưu trữ mã máy đơn lập tách biệt
        String initialMachineId = safeText(getIntent() != null ? getIntent().getStringExtra(EXTRA_MACHINE_ID) : null);
        workOrderMachineId = initialMachineId;
        maintenanceMachineId = initialMachineId;

        // Đảm bảo nạp ngôn ngữ từ điển và thiết lập bộ lắng nghe sự kiện kích hoạt Spinner thành công
        applyLanguage();
        setupSpinnerSelectionListeners();
        setupDateFilterListeners();

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        scanMachineLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }

                    String machineId = safeText(result.getData().getStringExtra("barcode"));
                    if (machineId.isEmpty()) {
                        Toast.makeText(this, i18n("Unable to read machine code"), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (currentSelectedTab == 0) {
                        workOrderMachineId = machineId;
                        updateMachineScanUi(workOrderMachineId);
                        if (workOrderListFragment != null) {
                            workOrderListFragment.reloadDataForMachineId(workOrderMachineId);
                        }
                    } else {
                        maintenanceMachineId = machineId;
                        updateMachineScanUi(maintenanceMachineId);
                        if (maintenanceTabFragment != null) {
                            maintenanceTabFragment.reloadDataForMachineId(maintenanceMachineId);
                        }
                    }
                }
        );

        if (btnScanMachine != null) {
            btnScanMachine.setOnClickListener(v -> openMachineScanner());
        }

        if (btnTabWorkOrder != null) btnTabWorkOrder.setOnClickListener(v -> selectTab(0));
        if (btnTabMaintenance != null) btnTabMaintenance.setOnClickListener(v -> selectTab(1));
        findViewById(R.id.tab_work_order_container).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.tab_maintenance_container).setOnClickListener(v -> selectTab(1));

        if (savedInstanceState == null) {
            if (!workOrderMachineId.isEmpty()) {
                workOrderListFragment = WorkOrderDataListFragment.newInstance(workOrderMachineId);
            } else {
                workOrderListFragment = new WorkOrderDataListFragment();
            }

            maintenanceTabFragment = new MaintenanceTabFragment();
            if (!maintenanceMachineId.isEmpty()) {
                maintenanceTabFragment.reloadDataForMachineId(maintenanceMachineId);
            }

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, workOrderListFragment, TAG_WORK_ORDER)
                    .add(R.id.fragment_container, maintenanceTabFragment, TAG_MAINTENANCE)
                    .hide(maintenanceTabFragment)
                    .commitNow();
        } else {
            workOrderListFragment = (WorkOrderDataListFragment) getSupportFragmentManager().findFragmentByTag(TAG_WORK_ORDER);
            maintenanceTabFragment = (MaintenanceTabFragment) getSupportFragmentManager().findFragmentByTag(TAG_MAINTENANCE);
        }

        selectTab(0);
    }

    private void setupDateFilterListeners() {
        if (btnFilterWoDate != null) {
            btnFilterWoDate.setOnClickListener(v -> showDatePickerDialog(true));
        }
        if (btnFilterMaintDate != null) {
            btnFilterMaintDate.setOnClickListener(v -> showDatePickerDialog(false));
        }
    }

    private void showDatePickerDialog(boolean isWorkOrder) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            String displayDate = String.format(Locale.getDefault(), "%04d/%02d/%02d", year, month + 1, dayOfMonth);

            if (isWorkOrder) {
                selectedWoDate = dateStr;
                if (tvFilterWoDateLabel != null) {
                    tvFilterWoDateLabel.setText(i18n("Ngày tạo") + ": " + displayDate);
                }
                if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                    workOrderListFragment.filterByDate(selectedWoDate);
                }
            } else {
                selectedMaintDate = dateStr;
                if (tvFilterMaintDateLabel != null) {
                    tvFilterMaintDateLabel.setText(i18n("Dự kiến") + ": " + displayDate);
                }
                if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                    maintenanceTabFragment.filterByDate(selectedMaintDate);
                }
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.setButton(DialogInterface.BUTTON_NEUTRAL, i18n("Tất cả"), (dialog, which) -> {
            if (isWorkOrder) {
                selectedWoDate = "";
                if (tvFilterWoDateLabel != null) tvFilterWoDateLabel.setText(i18n("Ngày tạo: Tất cả"));
                if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                    workOrderListFragment.filterByDate("");
                }
            } else {
                selectedMaintDate = "";
                if (tvFilterMaintDateLabel != null) tvFilterMaintDateLabel.setText(i18n("Dự kiến: Tất cả"));
                if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                    maintenanceTabFragment.filterByDate("");
                }
            }
        });

        datePickerDialog.show();
    }

    private void applyIncomingLanguageFromIntent() {
        String passedLanguageCode = getIntent() != null ? getIntent().getStringExtra(EXTRA_LANGUAGE_CODE) : null;
        if (passedLanguageCode == null || passedLanguageCode.trim().isEmpty()) {
            return;
        }

        String normalizedCode = passedLanguageCode.trim();
        int languagePosition = 2;
        if ("ja".equalsIgnoreCase(normalizedCode)) languagePosition = 0;
        else if ("en".equalsIgnoreCase(normalizedCode)) languagePosition = 1;
        else if ("ch".equalsIgnoreCase(normalizedCode)) languagePosition = 3;

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        prefs.edit().putInt("languageSettingPosition", languagePosition).apply();
        LanguageAPIUtils.setLanguageCode(normalizedCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        applyLanguage();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WorkOrderEntryDialogHelper.onActivityResult(requestCode, resultCode, data);
    }

    private void selectTab(int index) {
        currentSelectedTab = index; // Cập nhật mốc định vị cho hệ thống
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();

        View filtersWorkOrder = findViewById(R.id.layout_filters_work_order);
        View filtersMaintenance = findViewById(R.id.layout_filters_maintenance);

        spinnerWoStatus = findViewById(R.id.spinner_filter_wo_status);
        spinnerMaintStatus = findViewById(R.id.spinner_filter_maint_status);

        if (index == 0) {
            if (workOrderListFragment != null) tx.show(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.hide(maintenanceTabFragment);
            updateTabUi(true);

            updateMachineScanUi(workOrderMachineId);

            // hiển thị bộ lọc
            if (filtersWorkOrder != null) filtersWorkOrder.setVisibility(View.VISIBLE);
            if (filtersMaintenance != null) filtersMaintenance.setVisibility(View.GONE);
        } else {
            if (workOrderListFragment != null) tx.hide(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.show(maintenanceTabFragment);
            updateTabUi(false);

            updateMachineScanUi(maintenanceMachineId);

            // hiển thị bộ lọc
            if (filtersWorkOrder != null) filtersWorkOrder.setVisibility(View.GONE);
            if (filtersMaintenance != null) filtersMaintenance.setVisibility(View.VISIBLE);
        }
        tx.commitNowAllowingStateLoss();
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

    private void applyLanguage() {
        if (btnTabWorkOrder != null) btnTabWorkOrder.setText(i18n("Work Order"));
        if (btnTabMaintenance != null) btnTabMaintenance.setText(i18n("Maintenance"));

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            LanguageAPIUtils.setLang(rootView);
        }

        populateStatusSpinnersWithI18n();


        String activeMachineId = (currentSelectedTab == 0) ? workOrderMachineId : maintenanceMachineId;
        updateMachineScanUi(activeMachineId);
    }

    private void setupSpinnerSelectionListeners() {
        if (spinnerWoStatus != null) {
            spinnerWoStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                        workOrderListFragment.filterByStatus(position);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (spinnerMaintStatus != null) {
            spinnerMaintStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                        maintenanceTabFragment.filterByStatus(position);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void populateStatusSpinnersWithI18n() {
        int currentWoSelection = spinnerWoStatus != null ? spinnerWoStatus.getSelectedItemPosition() : 0;
        int currentMaintSelection = spinnerMaintStatus != null ? spinnerMaintStatus.getSelectedItemPosition() : 0;

        if (spinnerWoStatus != null) {
            String[] woStatuses = WorkOrderDataListFragment.getLocalizedStatusLabels();

            ArrayAdapter<String> woAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, woStatuses);
            woAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerWoStatus.setAdapter(woAdapter);

            if (currentWoSelection >= 0 && currentWoSelection < woStatuses.length) {
                spinnerWoStatus.setSelection(currentWoSelection);
            }
        }

        if (spinnerMaintStatus != null) {
            String[] maintStatuses = MaintenanceTabFragment.getLocalizedStatusLabels();

            ArrayAdapter<String> maintAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, maintStatuses);
            maintAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerMaintStatus.setAdapter(maintAdapter);

            if (currentMaintSelection >= 0 && currentMaintSelection < maintStatuses.length) {
                spinnerMaintStatus.setSelection(currentMaintSelection);
            }
        }
    }


    private void refreshCurrentTab() {
        if (workOrderListFragment != null && workOrderListFragment.isVisible()) {
            workOrderListFragment.reloadData();
            return;
        }

        if (maintenanceTabFragment != null && maintenanceTabFragment.isVisible()) {
            maintenanceTabFragment.reloadData();
        }

        String activeMachineId = (currentSelectedTab == 0) ? workOrderMachineId : maintenanceMachineId;
        updateMachineScanUi(activeMachineId);
    }

    private void openMachineScanner() {
        if (scanMachineLauncher != null) {
            scanMachineLauncher.launch(new Intent(this, Barcode.class));
        }
    }

    private void updateMachineScanUi(String machineId) {
        if (tvMachineScanValue != null) {
            tvMachineScanValue.setText(machineId.isEmpty() ? i18n("Machine not scanned") : machineId);
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}