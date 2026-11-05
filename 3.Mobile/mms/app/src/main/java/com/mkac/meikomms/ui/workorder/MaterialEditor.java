package com.mkac.meikomms.ui.workorder;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.HttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bảng khai báo vật tư của FE ở màn nhập kết quả Work Order (giống web: #matTbody trong
 * openWorkOrderRecordByCode). Mỗi dòng: vật tư | tồn kho (chỉ đọc) | số lượng | xoá.
 * Dữ liệu lưu dạng [{materialId, quantity}] vào MT_WORK_ORDER.MATERIAL_INFO.
 */
final class MaterialEditor {

    private final Context context;
    private final LinearLayout host;
    private final LinearLayout rowsContainer;

    private final List<String> labels = new ArrayList<>();                 // "Id - Name"
    private final Map<String, String> labelToId = new HashMap<>();         // label -> Item_Id
    private final Map<String, String> idToLabel = new HashMap<>();         // Item_Id -> label
    private final Map<String, Double> labelToStock = new HashMap<>();      // label -> tồn kho
    private final List<Row> rows = new ArrayList<>();
    private View btnAdd;
    private boolean locked = false;

    private static class Row {
        AutoCompleteTextView material;
        EditText stock;
        EditText qty;
        View removeBtn;
        View view;
    }

    MaterialEditor(Context context, LinearLayout host) {
        this.context = context;
        this.host = host;
        host.removeAllViews();
        host.addView(buildHeader());
        rowsContainer = new LinearLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        host.addView(rowsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        host.addView(buildAddButton());
        // Chừa chỗ để danh sách xổ xuống của ô chọn vật tư không bị cắt (giống web).
        host.setMinimumHeight(dp(280));
    }

    boolean isLocked() {
        return locked;
    }

    /** Đổ dữ liệu đã lưu (MATERIAL_INFO) vào bảng; luôn còn ít nhất 1 dòng. */
    void fill(JSONArray materialInfo) {
        if (materialInfo != null) {
            for (int i = 0; i < materialInfo.length(); i++) {
                JSONObject o = materialInfo.optJSONObject(i);
                if (o == null) continue;
                String id = firstNonEmpty(o.optString("materialId"), o.optString("Material_Id"),
                        o.optString("material_id"));
                double q = o.optDouble("quantity", o.optDouble("Quantity", 0));
                if (id.isEmpty() && q <= 0) continue;
                addRow(id, formatNum(q));
            }
        }
        if (rows.isEmpty()) addRow("", "");
    }

    /** Tải danh sách vật tư + tồn kho (giống web getMaterialDataList). */
    void loadMaterialList(String serverDynamic, String schemaWms) {
        new Thread(() -> {
            final List<String> newLabels = new ArrayList<>();
            final Map<String, String> l2i = new HashMap<>();
            final Map<String, String> i2l = new HashMap<>();
            final Map<String, Double> l2s = new HashMap<>();
            try {
                JSONObject cond = new JSONObject();
                cond.put("Schema_WMS", schemaWms);
                cond.put("where", "1=1");
                HttpClient.APIReturn rs = HttpClient.callDynamics(
                        context, serverDynamic, "mes_mms", "MMS_GET_LIST_MATERIALS_225", cond);
                if (rs != null && rs.code == 200 && rs.data != null) {
                    Set<String> seen = new HashSet<>();
                    for (JSONObject m : rs.data) {
                        if (m == null) continue;
                        String id = m.optString("Item_Id").trim();
                        String name = m.optString("Item_Name").trim();
                        if (id.isEmpty() || seen.contains(id) || m.optInt("Deleted", 0) != 0) continue;
                        seen.add(id);
                        String label = id + " - " + name;
                        newLabels.add(label);
                        l2i.put(label, id);
                        i2l.put(id, label);
                        l2s.put(label, m.optDouble("Item_Total_Quantity", 0));
                    }
                }
            } catch (Exception ignored) {
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                labels.clear(); labels.addAll(newLabels);
                labelToId.clear(); labelToId.putAll(l2i);
                idToLabel.clear(); idToLabel.putAll(i2l);
                labelToStock.clear(); labelToStock.putAll(l2s);
                for (Row r : rows) {
                    setupDropdown(r.material);
                    // Dòng đã lưu chỉ có mã -> đổi sang "Mã - Tên" và điền tồn kho.
                    String id = idOf(r.material.getText().toString().trim());
                    String label = idToLabel.get(id);
                    if (label != null) {
                        r.material.setText(label, false);
                        r.stock.setText(formatNum(stockOf(label)));
                    }
                }
            });
        }).start();
    }

    /**
     * Kiểm tra giống web woRpGetValidatedMaterials: chọn mã + SL>0 + SL<=tồn kho.
     * Trả về danh sách [{materialId, quantity}] (bỏ dòng trống), hoặc null nếu lỗi (đã toast).
     */
    JSONArray collectValidated() {
        JSONArray out = new JSONArray();
        for (Row r : rows) {
            String label = r.material.getText().toString().trim();
            double q = qtyOf(r);
            if (label.isEmpty() && q <= 0) continue; // dòng trống

            if (label.isEmpty()) {
                toast(i18n("Please select a material after entering quantity"));
                return null;
            }
            String id = idOf(label);
            if (q <= 0) {
                toast(i18n("Material") + " [" + id + "] " + i18n("must have quantity > 0"));
                return null;
            }
            double stock = stockOf(label);
            if (q > stock) {
                toast(i18n("Requested quantity") + " (" + formatNum(q) + ") "
                        + i18n("exceeds stock") + " (" + formatNum(stock) + ")");
                return null;
            }
            try {
                JSONObject o = new JSONObject();
                o.put("materialId", id);
                o.put("quantity", q);
                out.put(o);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Đã tạo yêu cầu xuất kho -> khoá bảng, nút Lưu không ghi vật tư nữa (giống woRpLockMaterialEditor). */
    void lock() {
        locked = true;
        if (btnAdd != null) btnAdd.setVisibility(View.GONE);
        for (Row r : rows) {
            r.material.setEnabled(false);
            r.qty.setEnabled(false);
            r.removeBtn.setVisibility(View.INVISIBLE);
            r.view.setAlpha(0.7f);
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.parseColor("#F4F4F4"));
        header.setPadding(dp(4), dp(8), dp(4), dp(8));
        header.addView(headerCell(i18n("Materials"), 2.4f, Gravity.CENTER_VERTICAL | Gravity.START, dp(6)));
        header.addView(headerCell(i18n("Stock"), 1f, Gravity.CENTER, dp(6)));
        header.addView(headerCell(i18n("Quantity"), 1f, Gravity.CENTER, dp(6)));
        View spacer = new View(context);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(40), 1));
        return header;
    }

    private TextView headerCell(String text, float weight, int gravity, int marginEnd) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMarginEnd(marginEnd);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(13);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(gravity);
        return tv;
    }

    private View buildAddButton() {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(40));
        lp.topMargin = dp(8);
        lp.gravity = Gravity.END;
        tv.setLayoutParams(lp);
        tv.setText("+");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_blue_button);
        tv.setOnClickListener(v -> addRow("", ""));
        btnAdd = tv;
        return tv;
    }

    private void addRow(String preId, String preQty) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowLp);

        // Ô vật tư: RelativeLayout (nền + mũi tên) bọc AutoCompleteTextView.
        RelativeLayout matWrap = new RelativeLayout(context);
        LinearLayout.LayoutParams matLp = new LinearLayout.LayoutParams(0, dp(44), 2.4f);
        matLp.setMarginEnd(dp(6));
        matWrap.setLayoutParams(matLp);
        matWrap.setBackgroundResource(R.drawable.bg_input_field);

        AutoCompleteTextView mat = new AutoCompleteTextView(context);
        mat.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mat.setBackgroundColor(Color.TRANSPARENT);
        mat.setPadding(dp(10), 0, dp(28), 0);
        mat.setHint(i18n("Select an item"));
        mat.setTextSize(13);
        mat.setSingleLine(true);
        setupDropdown(mat); // list có thể đang tải, sẽ gắn lại khi tải xong
        String preLabel = idToLabel.get(preId);
        mat.setText(preLabel != null ? preLabel : preId, false);

        ImageView arrow = new ImageView(context);
        RelativeLayout.LayoutParams arrowLp = new RelativeLayout.LayoutParams(dp(18), dp(18));
        arrowLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        arrowLp.addRule(RelativeLayout.CENTER_VERTICAL);
        arrowLp.setMarginEnd(dp(8));
        arrow.setLayoutParams(arrowLp);
        arrow.setImageResource(android.R.drawable.arrow_down_float);
        arrow.setOnClickListener(v -> { if (mat.isEnabled()) mat.showDropDown(); });
        matWrap.addView(mat);
        matWrap.addView(arrow);

        // Tồn kho (chỉ đọc) — tự điền theo vật tư đã chọn.
        EditText stock = new EditText(context);
        LinearLayout.LayoutParams stockLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        stockLp.setMarginEnd(dp(6));
        stock.setLayoutParams(stockLp);
        stock.setBackgroundResource(R.drawable.bg_input_field_disabled);
        stock.setPadding(dp(10), 0, dp(10), 0);
        stock.setTextSize(13);
        stock.setEnabled(false);
        stock.setFocusable(false);
        stock.setText(preLabel != null ? formatNum(stockOf(preLabel)) : "0");

        EditText qty = new EditText(context);
        LinearLayout.LayoutParams qtyLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        qtyLp.setMarginEnd(dp(6));
        qty.setLayoutParams(qtyLp);
        qty.setBackgroundResource(R.drawable.bg_input_field);
        qty.setPadding(dp(10), 0, dp(10), 0);
        qty.setHint("0");
        qty.setTextSize(13);
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (preQty != null && !preQty.isEmpty()) qty.setText(preQty);

        mat.setOnItemClickListener((parent, v, pos, id) -> {
            String lbl = mat.getText().toString().trim();
            stock.setText(formatNum(stockOf(lbl)));
        });

        ImageView btnRemove = new ImageView(context);
        btnRemove.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(28)));
        btnRemove.setImageResource(R.drawable.close_24);
        btnRemove.setScaleType(ImageView.ScaleType.CENTER);

        final Row holder = new Row();
        holder.material = mat; holder.stock = stock; holder.qty = qty;
        holder.removeBtn = btnRemove; holder.view = row;
        btnRemove.setOnClickListener(v -> {
            if (locked) return;
            rowsContainer.removeView(row);
            rows.remove(holder);
            if (rows.isEmpty()) addRow("", ""); // luôn còn ít nhất 1 dòng
        });

        row.addView(matWrap);
        row.addView(stock);
        row.addView(qty);
        row.addView(btnRemove);
        rowsContainer.addView(row);
        rows.add(holder);
    }

    private void setupDropdown(AutoCompleteTextView view) {
        view.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(labels)));
        view.setThreshold(0);
        view.setDropDownWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.6));
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) view.showDropDown();
            return false;
        });
        view.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) view.showDropDown(); });
    }

    // --------------------------------------------------------------- helpers

    private String idOf(String label) {
        String id = labelToId.get(label);
        if (id != null && !id.isEmpty()) return id;
        return label.contains(" - ") ? label.split(" - ")[0].trim() : label;
    }

    private double stockOf(String label) {
        Double s = labelToStock.get(label);
        return s == null ? 0 : s;
    }

    private double qtyOf(Row r) {
        String s = r.qty.getText().toString().trim();
        try { return s.isEmpty() ? 0 : Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    /** Bỏ .0 thừa khi hiển thị số lượng/tồn kho. */
    private static String formatNum(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
