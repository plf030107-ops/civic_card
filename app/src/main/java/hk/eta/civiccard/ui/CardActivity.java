package hk.eta.civiccard.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

import hk.eta.civiccard.R;
import hk.eta.civiccard.config.ConfigStore;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CarModel;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 背屏卡片：车模图 + 电压 / 总里程 / 剩余续航。
 *
 * 车模图来自乘趣 /v5/detail 的 vehicleModelPicture，
 * 下载后缓存，无网络也能显示。
 */
public class CardActivity extends Activity {

    private static final long POLL_MS = 2000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;

    private ImageView ivBg, ivCar;
    private TextView tvCarName, tvUpdate, tvRange, tvVoltage, tvMileage, tvTip;

    private VehicleStatus mLast;
    private long mLongPressMs;
    private boolean mCarLoaded = false;

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
        applyBg();
        loadCarModel();
        bindActions();
    }

    private void bindViews() {
        ivBg = findViewById(R.id.ivBg);
        ivCar = findViewById(R.id.ivCar);
        tvCarName = findViewById(R.id.tvCarName);
        tvUpdate = findViewById(R.id.tvUpdate);
        tvRange = findViewById(R.id.tvRange);
        tvVoltage = findViewById(R.id.tvVoltage);
        tvMileage = findViewById(R.id.tvMileage);
        tvTip = findViewById(R.id.tvTip);
    }

    /** 用户自定义背景（可选） */
    private void applyBg() {
        String p = ConfigStore.getBgPath(this);
        if (p == null) {
            ivBg.setBackgroundColor(Color.parseColor("#FF0B0F14"));
            return;
        }
        try {
            ivBg.setImageBitmap(BitmapFactory.decodeFile(p));
        } catch (Throwable ignored) { }
    }

    /** 车模图：子线程加载，避免卡界面 */
    private void loadCarModel() {
        new Thread(() -> {
            final Bitmap bmp = CarModel.load(this);
            if (bmp == null) return;
            mHandler.post(() -> {
                ivCar.setImageBitmap(bmp);
                mCarLoaded = true;
            });
        }).start();
    }

    private void bindActions() {
        bindBtn(R.id.btnUnlock, "🔓", "解锁", CommandSender.CMD_UNLOCK);
        bindBtn(R.id.btnLock,   "🔒", "锁车", CommandSender.CMD_LOCK);
        bindBtn(R.id.btnRefresh, "⟳", "刷新", null);
    }

    private void bindBtn(int id, String icon, String label, String alias) {
        android.view.View root = findViewById(id);
        if (root == null) return;
        TextView ic = root.findViewById(R.id.btnIcon);
        TextView lb = root.findViewById(R.id.btnLabel);
        if (ic != null) ic.setText(icon);
        if (lb != null) lb.setText(label);

        if (alias == null) {
            root.setOnClickListener(v -> {
                refresh();
                loadCarModel();
                showTip("已刷新");
            });
            return;
        }

        mLongPressMs = ConfigStore.getLongPressMs(this);
        Runnable act = () -> confirmThenFire(alias, label);
        if (mLongPressMs > 0) {
            root.setOnLongClickListener(v -> { act.run(); return true; });
            root.setOnClickListener(v ->
                    showTip("长按 " + (mLongPressMs / 1000f) + " 秒触发"));
        } else {
            root.setOnClickListener(v -> act.run());
        }
    }

    private void confirmThenFire(String alias, String label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage("确定要" + label + "吗？")
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    showTip(label + "中");
                    CommandSender.send(alias);
                })
                .setNegativeButton("取消", (d, w) -> {
                    d.dismiss();
                    showTip("已取消");
                })
                .show();
    }

    private void showTip(String s) {
        tvTip.setText(s);
    }

    // ---------------------------------------------------------- 刷新

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

        tvRange.setText(st.hasRemainMileage ? String.valueOf(st.continuationMileage) : "--");
        tvVoltage.setText(st.hasVoltage ? String.format(Locale.US, "%.1f", st.voltage) : "--");
        tvMileage.setText(st.hasTotalMileage ? String.valueOf((int) st.totalMileage) : "--");
        tvUpdate.setText(ageText(BridgeReader.statusAgeMs()));

        // 车模图首次没加载成功就再试
        if (!mCarLoaded) loadCarModel();
    }

    private String ageText(long ageMs) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0) return "更新 --";
        long sec = ageMs / 1000L;
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
}
