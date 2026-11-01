package hk.eta.civiccard.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import hk.eta.civiccard.R;
import hk.eta.civiccard.config.CardConfig;
import hk.eta.civiccard.config.ConfigStore;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 配置主界面：卡片状态、功能槽位、防误触、背景、测试、生成。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK_BG = 1001;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TextView tvRuntime, tvStatusSummary, tvBgPath;
    private CheckBox cbLongPress;
    private Spinner spLong0, spLong1, spLong2, spLong3, spLongPress, spTest;
    private Spinner[] mFuncSpinners;

    private final Runnable mRuntimeTick = new Runnable() {
        @Override
        public void run() {
            refreshRuntime();
            mHandler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        loadConfig();
        setUpListeners();
    }

    private void bindViews() {
        tvRuntime = findViewById(R.id.tvRuntime);
        tvStatusSummary = findViewById(R.id.tvStatusSummary);
        tvBgPath = findViewById(R.id.tvBgPath);
        cbLongPress = findViewById(R.id.cbLongPress);

        spLong0 = findViewById(R.id.spFunc0);
        spLong1 = findViewById(R.id.spFunc1);
        spLong2 = findViewById(R.id.spFunc2);
        spLong3 = findViewById(R.id.spFunc3);
        mFuncSpinners = new Spinner[]{spLong0, spLong1, spLong2, spLong3};

        spLongPress = findViewById(R.id.spLongPress);
        spTest = findViewById(R.id.spTest);
    }

    private void loadConfig() {
        // 功能下拉
        List<String> labels = new ArrayList<>();
        for (CardConfig.FuncItem f : CardConfig.ALL_FUNCS) labels.add(f.label);
        ArrayAdapter<String> fa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);

        CardConfig.FuncItem[] cur = ConfigStore.getFuncs(this);
        for (int i = 0; i < mFuncSpinners.length; i++) {
            mFuncSpinners[i].setAdapter(fa);
            mFuncSpinners[i].setSelection(indexOfFunc(cur[i]));
        }

        // 长按时长
        List<String> lp = new ArrayList<>();
        for (String s : CardConfig.LONG_PRESS_LABELS) lp.add(s);
        ArrayAdapter<String> la = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, lp);
        spLongPress.setAdapter(la);

        long ms = ConfigStore.getLongPressMs(this);
        cbLongPress.setChecked(ms > 0);
        spLongPress.setSelection(indexOfLongPress(ms));

        // 测试项
        List<String> testable = new ArrayList<>();
        for (CardConfig.FuncItem f : CardConfig.ALL_FUNCS) {
            if (f.alias != null) testable.add(f.label);
        }
        ArrayAdapter<String> ta = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, testable);
        spTest.setAdapter(ta);

        refreshSummary();
        refreshBgText();
    }

    private int indexOfFunc(CardConfig.FuncItem f) {
        for (int i = 0; i < CardConfig.ALL_FUNCS.length; i++) {
            if (CardConfig.ALL_FUNCS[i].label.equals(f.label)) return i;
        }
        return 0;
    }

    private int indexOfLongPress(long ms) {
        for (int i = 0; i < CardConfig.LONG_PRESS_OPTIONS.length; i++) {
            if (CardConfig.LONG_PRESS_OPTIONS[i] == ms) return i;
        }
        return 0;
    }

    private void setUpListeners() {
        findViewById(R.id.btnPickStatus).setOnClickListener(v ->
                startActivity(new Intent(this, StatusPickActivity.class)));

        // 功能变更即保存
        for (int i = 0; i < mFuncSpinners.length; i++) {
            final int slot = i;
            mFuncSpinners[i].setOnItemSelectedListener(
                    new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> p,
                                                   View view, int pos, long id) {
                            ConfigStore.setFunc(SettingsActivity.this,
                                    slot, CardConfig.ALL_FUNCS[pos]);
                        }

                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> p) { }
                    });
        }

        cbLongPress.setOnCheckedChangeListener((b, checked) -> {
            long ms = checked
                    ? CardConfig.LONG_PRESS_OPTIONS[spLongPress.getSelectedItemPosition()]
                    : 0L;
            ConfigStore.setLongPressMs(this, ms);
        });

        spLongPress.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> p,
                                               View view, int pos, long id) {
                        if (cbLongPress.isChecked()) {
                            ConfigStore.setLongPressMs(SettingsActivity.this,
                                    CardConfig.LONG_PRESS_OPTIONS[pos]);
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> p) { }
                });

        findViewById(R.id.btnPickBg).setOnClickListener(v -> pickBg());
        findViewById(R.id.btnResetBg).setOnClickListener(v -> {
            ConfigStore.resetBg(this);
            refreshBgText();
            Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnRunTest).setOnClickListener(v -> runTest());

        findViewById(R.id.btnGenerate).setOnClickListener(v -> generate());

        findViewById(R.id.btnPreview).setOnClickListener(v ->
                startActivity(new Intent(this, CardActivity.class)));

        // 挂载到背屏
        findViewById(R.id.btnMount).setOnClickListener(v -> mountToRear());
        refreshRearStatus();
    }

    // ---------------------------------------------------------- 背屏挂载

    private void refreshRearStatus() {
        TextView tv = findViewById(R.id.tvRearStatus);
        boolean host = RearWidgetRegistrar.isHostAvailable(this);
        boolean perm = RearWidgetRegistrar.hasPermission(this);
        StringBuilder sb = new StringBuilder();
        sb.append(host ? "✓ REAREye 已安装" : "✗ 未安装 REAREye");
        sb.append(host && perm ? "　✓ 卡片权限已授予" : "　✗ 缺少卡片权限");
        tv.setText(sb.toString());
    }

    /**
     * 挂载到背屏。
     *
     * REAREye 通过扫描应用 meta-data 发现卡片，
     * 所以这里直接打开它的卡片列表页，用户点一下即可添加。
     */
    private void mountToRear() {
        if (!RearWidgetRegistrar.isHostAvailable(this)) {
            Toast.makeText(this, "未安装 REAREye", Toast.LENGTH_LONG).show();
            return;
        }
        if (!RearWidgetRegistrar.hasPermission(this)) {
            Toast.makeText(this, "缺少 ACCESS_REAR_WIDGET_API 权限", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("挂载到背屏")
                .setMessage("将打开 REAREye 卡片列表。\n\n"
                        + "在列表中找到「" + RearWidgetRegistrar.WIDGET_NAME + "」，\n"
                        + "点一下即可添加到背屏。\n\n"
                        + "之后可在 REAREye 里随时移除，恢复原生背屏。")
                .setPositiveButton("打开 REAREye", (d, w) -> {
                    d.dismiss();
                    RearWidgetRegistrar.openHostSettings(this);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------------------------------------------------- 背景

    private void pickBg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_PICK_BG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BG || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            File out = new File(getFilesDir(), "card_bg.img");
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
            ConfigStore.setBgPath(this, out.getAbsolutePath());
            refreshBgText();
            Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "设置失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshBgText() {
        String p = ConfigStore.getBgPath(this);
        tvBgPath.setText(p == null ? "默认背景" : "已自定义");
    }

    // ---------------------------------------------------------- 测试

    private void runTest() {
        String label = (String) spTest.getSelectedItem();
        CardConfig.FuncItem f = CardConfig.findFuncByLabel(label);
        if (f == null || f.alias == null) {
            Toast.makeText(this, "该功能无法测试", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("__refresh__".equals(f.alias)) {
            Toast.makeText(this, "刷新无指令，卡片内可见", Toast.LENGTH_SHORT).show();
            return;
        }
        VehicleStatus st = BridgeReader.readStatus();
        String block = st != null ? st.preconditionBlockReason(f.alias) : null;
        if (block != null) {
            new AlertDialog.Builder(this)
                    .setTitle("无法执行")
                    .setMessage("车辆当前" + block + "，该操作被前置条件拦截。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("执行测试")
                .setMessage("即将下发：" + f.label + "\n\n确认执行？")
                .setPositiveButton("执行", (d, w) -> {
                    CommandSender.send(f.alias);
                    Toast.makeText(this, "已下发 " + f.label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------------------------------------------------- 生成

    private void generate() {
        refreshSummary();
        Toast.makeText(this, "卡片已生成，可点「预览卡片」查看", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, CardActivity.class));
    }

    // ---------------------------------------------------------- 刷新

    private void refreshSummary() {
        List<String> keys = ConfigStore.getStatusKeys(this);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            CardConfig.StatusItem it = CardConfig.findStatus(k);
            if (sb.length() > 0) sb.append("、");
            sb.append(it == null ? k : it.label);
        }
        tvStatusSummary.setText(sb.length() == 0 ? "未选择" : sb.toString());
    }

    private void refreshRuntime() {
        boolean ready = BridgeReader.isReady();
        VehicleStatus st = BridgeReader.readStatus();
        StringBuilder sb = new StringBuilder();
        sb.append(ready ? "✓ 乘趣桥已连接" : "✗ 乘趣桥未连接");
        if (st != null) {
            sb.append("\n✓ 车况已读取");
            if (st.hasVoltage) sb.append("　电压 ").append(String.format("%.1f V", st.voltage));
            if (st.hasRemainMileage) sb.append("　续航 ").append(st.continuationMileage).append(" km");
        } else {
            sb.append("\n✗ 暂无车况（打开乘趣一次即可）");
        }
        tvRuntime.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
        mHandler.post(mRuntimeTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRuntimeTick);
    }
}
