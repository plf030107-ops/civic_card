package hk.eta.civiccard.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import hk.eta.civiccard.R;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 背屏卡片主界面。
 *
 * 设计要点：
 *  - 976x596 @450dpi，横屏
 *  - 深色底，OLED 省电
 *  - 左栏车况，右栏四个按钮
 *  - 每 1.5 秒轮询 status.json（桥由乘趣进程写入）
 *  - 点按钮写 cmd_req，乘趣侧 FileObserver 接走
 */
public class CardActivity extends Activity {

    private static final long POLL_MS = 1500L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;

    private TextView tvCarName, tvConn, tvLockIcon, tvLockState, tvTip;
    private TextView tvFLStat, tvFRStat, tvRLStat, tvRRStat;
    private TextView tvBattery, tvRange, tvTemp, tvBatteryLabel;

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

        // 背屏常亮、不锁屏
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.card_main);
        bindViews();
        bindActions();
    }

    private void bindViews() {
        tvCarName  = findViewById(R.id.tvCarName);
        tvConn     = findViewById(R.id.tvConn);
        tvLockIcon = findViewById(R.id.tvLockIcon);
        tvLockState = findViewById(R.id.tvLockState);
        tvTip      = findViewById(R.id.tvTip);
        tvFLStat   = findViewById(R.id.tvFLStat);
        tvFRStat   = findViewById(R.id.tvFRStat);
        tvRLStat   = findViewById(R.id.tvRLStat);
        tvRRStat   = findViewById(R.id.tvRRStat);
        tvBattery  = findViewById(R.id.tvBattery);
        tvRange    = findViewById(R.id.tvRange);
        tvTemp     = findViewById(R.id.tvTemp);
        tvBatteryLabel = findViewById(R.id.tvBatteryLabel);
    }

    private void bindActions() {
        findViewById(R.id.btnUnlock).setOnClickListener(v ->
                fire(CommandSender.CMD_UNLOCK, "解锁"));

        findViewById(R.id.btnLock).setOnClickListener(v ->
                fire(CommandSender.CMD_LOCK, "上锁"));

        findViewById(R.id.btnTrunk).setOnClickListener(v ->
                fire(CommandSender.CMD_OPEN_TRUNK, "开后备箱"));

        findViewById(R.id.btnFind).setOnClickListener(v ->
                fire(CommandSender.CMD_SEARCH, "寻车"));
    }

    /**
     * 点按钮 -> 写指令文件。
     * 前置校验在 CommandSender 里做，不满足会写 BLOCKED 到 last_result.txt。
     */
    private void fire(String alias, String label) {
        // 先把本地校验结果算出来（和乘趣 common_precondition 一致）
        String block = mLast != null ? mLast.preconditionBlockReason(alias) : null;
        if (block != null) {
            showTip(label + "失败：" + block);
            return;
        }
        showTip(label + "…");
        CommandSender.send(alias);
    }

    private void showTip(String s) {
        mLastTipTs = System.currentTimeMillis();
        tvTip.setText(s);
        tvTip.setTextColor(getColor(R.color.text_secondary));
    }

    // ---------------------------------------------------------------- 刷新

    private void refresh() {
        boolean ready = BridgeReader.isReady();

        tvConn.setText(ready ? "● 已连接" : "● 未连接");
        tvConn.setTextColor(getColor(ready ? R.color.ok : R.color.text_muted));

        VehicleStatus st = BridgeReader.readStatus();
        if (st == null) {
            if (ready) {
                // 桥通了但还没有车况帧
                tvLockState.setText("等待车况");
                tvLockState.setTextColor(getColor(R.color.text_muted));
                tvLockIcon.setText("⏳");
            }
            showResultIfAny();
            return;
        }

        mLast = st;
        renderLock(st);
        renderDoors(st);
        renderEnergy(st);
        showResultIfAny();
    }

    private void renderLock(VehicleStatus st) {
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

    /** 门开 > 窗开 > 全关，按危险度上色 */
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
        if (st.batteryPercentage > 0) {
            tvBattery.setText(st.batteryPercentage + "%");
        } else {
            tvBattery.setText("--%");
        }

        if (st.continuationMileage > 0) {
            tvRange.setText(st.continuationMileage + " km");
        } else if (st.remainLMileage > 0) {
            tvRange.setText(st.remainLMileage + " km");
        } else {
            tvRange.setText("-- km");
        }

        // 温度 -40 是无效标记（0 值换算而来）
        double t = st.temperatureInside;
        if (t > -39.0) {
            tvTemp.setText(String.format(java.util.Locale.US, "%.0f°C", t));
        } else {
            tvTemp.setText("--°C");
        }
    }

    /** 读最近一次指令结果，拦截原因要显示出来 */
    private void showResultIfAny() {
        String r = BridgeReader.readLastResult();
        if (r == null) return;
        // 格式: ts\tOK|FAIL|BLOCKED\t详情
        String[] p = r.split("\t");
        if (p.length < 2) return;
        long ts;
        try {
            ts = Long.parseLong(p[0]);
        } catch (NumberFormatException e) {
            return;
        }
        // 只显示 5 秒内的
        if (System.currentTimeMillis() - ts > 5000) return;
        // 别覆盖正在显示的"…"
        if (System.currentTimeMillis() - mLastTipTs < 800) return;

        String kind = p[1];
        String detail = p.length > 2 ? p[2] : "";

        if ("BLOCKED".equals(kind)) {
            tvTip.setText("无法执行：" + detail);
            tvTip.setTextColor(getColor(R.color.warn));
        } else if ("FAIL".equals(kind) || "ERROR".equals(kind)) {
            tvTip.setText("失败");
            tvTip.setTextColor(getColor(R.color.danger));
        } else if ("OK".equals(kind)) {
            tvTip.setText("已执行");
            tvTip.setTextColor(getColor(R.color.ok));
        }
    }

    // ---------------------------------------------------------------- 生命周期

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
