package hk.eta.civiccard.ui;

import android.app.Activity;
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
 * 背屏卡片主界面。
 *
 * 关键原则：没有车况数据时一律显示「--」，
 * 绝不显示假的「已锁车/关闭」，否则用户会误判车辆真实状态。
 */
public class CardActivity extends Activity {

    private static final long POLL_MS = 1500L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;

    private TextView tvConn, tvLockIcon, tvLockState, tvTip;
    private TextView tvFLStat, tvFRStat, tvRLStat, tvRRStat;
    private TextView tvBattery, tvRange, tvTemp;

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
        tvConn      = findViewById(R.id.tvConn);
        tvLockIcon  = findViewById(R.id.tvLockIcon);
        tvLockState = findViewById(R.id.tvLockState);
        tvTip       = findViewById(R.id.tvTip);
        tvFLStat    = findViewById(R.id.tvFLStat);
        tvFRStat    = findViewById(R.id.tvFRStat);
        tvRLStat    = findViewById(R.id.tvRLStat);
        tvRRStat    = findViewById(R.id.tvRRStat);
        tvBattery   = findViewById(R.id.tvBattery);
        tvRange     = findViewById(R.id.tvRange);
        tvTemp      = findViewById(R.id.tvTemp);

        renderUnknown();
    }

    private void bindActions() {
        bindBtn(R.id.btnUnlock, "🔓", "解锁", CommandSender.CMD_UNLOCK);
        bindBtn(R.id.btnLock, "🔒", "上锁", CommandSender.CMD_LOCK);
        bindBtn(R.id.btnTrunk, "🧳", "后备箱", CommandSender.CMD_OPEN_TRUNK);
        bindBtn(R.id.btnFind, "📍", "寻车", CommandSender.CMD_SEARCH);
    }

    /** 按钮是 include 进来的布局，图标和文字在子 View 里 */
    private void bindBtn(int id, String icon, String label, String alias) {
        android.view.View root = findViewById(id);
        if (root == null) return;
        TextView ic = root.findViewById(R.id.btnIcon);
        TextView lb = root.findViewById(R.id.btnLabel);
        if (ic != null) ic.setText(icon);
        if (lb != null) lb.setText(label);
        root.setOnClickListener(v -> fire(alias, label));
    }

    private void fire(String alias, String label) {
        if (mLast == null) {
            showTip("暂无车况");
            return;
        }
        String block = mLast.preconditionBlockReason(alias);
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
        tvTip.setTextColor(getColor(R.color.text_secondary));
    }

    private void refresh() {
        boolean ready = BridgeReader.isReady();
        tvConn.setText(ready ? "● 已连接" : "● 未连接");
        tvConn.setTextColor(getColor(ready ? R.color.ok : R.color.text_muted));

        VehicleStatus st = BridgeReader.readStatus();
        if (st == null) {
            renderUnknown();
            showResultIfAny();
            return;
        }
        mLast = st;
        renderLock(st);
        renderDoors(st);
        renderEnergy(st);
        showResultIfAny();
    }

    /** 无数据态：明确显示未知，不做任何乐观假设 */
    private void renderUnknown() {
        tvLockIcon.setText("🚗");
        tvLockState.setText("暂无车况");
        tvLockState.setTextColor(getColor(R.color.text_muted));
        tvLockState.setTextSize(26f);
        setUnknown(tvFLStat);
        setUnknown(tvFRStat);
        setUnknown(tvRLStat);
        setUnknown(tvRRStat);
        tvBattery.setText("--%");
        tvRange.setText("-- km");
        tvTemp.setText("--°C");
    }

    private void setUnknown(TextView tv) {
        tv.setText("--");
        tv.setTextColor(getColor(R.color.text_muted));
    }

    private void renderLock(VehicleStatus st) {
        tvLockState.setTextSize(26f);
        boolean locked = st.doorsLocked();
        tvLockState.setText(locked ? "已锁车" : "未锁车");
        tvLockState.setTextColor(getColor(locked ? R.color.ok : R.color.warn));
        tvLockIcon.setText(locked ? "🔒" : "🔓");
    }

    private void renderDoors(VehicleStatus st) {
        setDoor(tvFLStat, st.frontLeftDoor, st.frontLeftWindow);
        setDoor(tvFRStat, st.frontRightDoor, st.frontRightWindow);
        setDoor(tvRLStat, st.rearLeftDoor, st.rearLeftWindow);
        setDoor(tvRRStat, st.rearRightDoor, st.rearRightWindow);
    }

    private void setDoor(TextView tv, int door, int window) {
        if (door == 1) {
            tv.setText("门开");
            tv.setTextColor(getColor(R.color.danger));
        } else if (window == 1) {
            tv.setText("窗开");
            tv.setTextColor(getColor(R.color.warn));
        } else {
            tv.setText("关");
            tv.setTextColor(getColor(R.color.text_primary));
        }
    }

    private void renderEnergy(VehicleStatus st) {
        tvBattery.setText(st.batteryPercentage > 0
                ? st.batteryPercentage + "%" : "--%");

        int km = st.continuationMileage > 0 ? st.continuationMileage : st.remainLMileage;
        tvRange.setText(km > 0 ? km + " km" : "-- km");

        double t = st.temperatureInside;
        tvTemp.setText(t > -39.0 ? String.format(Locale.US, "%.0f°C", t) : "--°C");
    }

    private void showResultIfAny() {
        String r = BridgeReader.readLastResult();
        if (r == null) return;
        String[] p = r.split("\t");
        if (p.length < 2) return;
        long ts;
        try { ts = Long.parseLong(p[0]); } catch (NumberFormatException e) { return; }
        if (System.currentTimeMillis() - ts > 5000) return;
        if (System.currentTimeMillis() - mLastTipTs < 800) return;

        String kind = p[1];
        String detail = p.length > 2 ? p[2] : "";
        if ("BLOCKED".equals(kind)) {
            tvTip.setText(detail);
            tvTip.setTextColor(getColor(R.color.warn));
        } else if ("FAIL".equals(kind) || "ERROR".equals(kind)) {
            tvTip.setText("失败");
            tvTip.setTextColor(getColor(R.color.danger));
        } else if ("OK".equals(kind)) {
            tvTip.setText("已执行");
            tvTip.setTextColor(getColor(R.color.ok));
        }
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
