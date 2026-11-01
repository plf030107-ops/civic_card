package hk.eta.civiccard.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

import hk.eta.civiccard.R;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 背屏卡片：只显示电压、总里程、剩余续航三项。
 *
 * 规则：
 *  1. 缺失的字段显示「--」，绝不用 0 冒充真实值
 *  2. 所有控车操作二次确认，防误触
 *  3. 不提供后备箱控制
 */
public class CardActivity extends Activity {

    private static final long POLL_MS = 2000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;

    private TextView tvCarName, tvUpdate, tvRange, tvVoltage, tvMileage, tvTip;

    private VehicleStatus mLast;
    private long mLastTipTs;

    private final Runnable mPoller = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            refresh();
            mHandler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setContentView(R.layout.card_main);
        bindViews();
        bindActions();
    }

    private void bindViews() {
        tvCarName = findViewById(R.id.tvCarName);
        tvUpdate  = findViewById(R.id.tvUpdate);
        tvRange   = findViewById(R.id.tvRange);
        tvVoltage = findViewById(R.id.tvVoltage);
        tvMileage = findViewById(R.id.tvMileage);
        tvTip     = findViewById(R.id.tvTip);
    }

    private void bindActions() {
        bindBtn(R.id.btnUnlock, "🔓", "解锁", CommandSender.CMD_UNLOCK);
        bindBtn(R.id.btnLock,   "🔒", "上锁", CommandSender.CMD_LOCK);
        bindBtn(R.id.btnRefresh, "⟳", "刷新", null);   // 本地刷新，不下发指令
    }

    private void bindBtn(int id, String icon, String label, String alias) {
        android.view.View root = findViewById(id);
        if (root == null) return;
        TextView ic = root.findViewById(R.id.btnIcon);
        TextView lb = root.findViewById(R.id.btnLabel);
        if (ic != null) ic.setText(icon);
        if (lb != null) lb.setText(label);
        root.setOnClickListener(v -> {
            if (alias == null) {
                refresh();
                showTip("已刷新");
            } else {
                confirmThenFire(alias, label);
            }
        });
    }

    private void confirmThenFire(String alias, String label) {
        String block = mLast != null ? mLast.preconditionBlockReason(alias) : null;
        StringBuilder msg = new StringBuilder("确定要").append(label).append("吗？");
        if (block != null) {
            msg.append("\n\n注意：车辆当前").append(block).append("，可能无法执行。");
        }
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage(msg.toString())
                .setPositiveButton("确定", (d, w) -> { d.dismiss(); doFire(alias, label); })
                .setNegativeButton("取消", (d, w) -> { d.dismiss(); showTip("已取消"); })
                .show();
    }

    private void doFire(String alias, String label) {
        String block = mLast != null ? mLast.preconditionBlockReason(alias) : null;
        if (block != null) {
            showTip(label + "：" + block);
            return;
        }
        showTip(label + "中");
        CommandSender.send(alias);
    }

    private void showTip(String s) {
        mLastTipTs = System.currentTimeMillis();
        tvTip.setText(s);
    }

    // ------------------------------------------------------------ 刷新

    private void refresh() {
        VehicleStatus st = BridgeReader.readStatus();
        if (st == null) {
            tvRange.setText("--");
            tvVoltage.setText("--");
            tvMileage.setText("--");
            tvUpdate.setText("更新 --");
            return;
        }
        mLast = st;

        // 剩余续航
        int km = st.hasRemainMileage ? st.continuationMileage : 0;
        tvRange.setText(km > 0 ? String.valueOf(km) : "--");

        // 电压
        tvVoltage.setText(st.hasVoltage && st.voltage > 0
                ? String.format(Locale.US, "%.1f", st.voltage) : "--");

        // 总里程
        tvMileage.setText(st.hasTotalMileage && st.totalMileage > 0
                ? String.valueOf((int) st.totalMileage) : "--");

        // 更新时间
        tvUpdate.setText(ageText(st.timestampMs));
    }

    /** 显示「更新 3 分钟前」这类相对时间 */
    private String ageText(long ts) {
        if (ts <= 0) return "更新 --";
        long sec = (System.currentTimeMillis() - ts) / 1000L;
        if (sec < 60) return "刚刚更新";
        if (sec < 3600) return "更新 " + (sec / 60) + " 分钟前";
        if (sec < 86400) return "更新 " + (sec / 3600) + " 小时前";
        return "更新 " + (sec / 86400) + " 天前";
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRunning = true;
        mHandler.post(mPoller);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRunning = false;
        mHandler.removeCallbacks(mPoller);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRunning = false;
        mHandler.removeCallbacks(mPoller);
    }
}
