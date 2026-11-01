package hk.eta.civiccard.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import hk.eta.civiccard.R;
import hk.eta.civiccard.config.CardConfig;
import hk.eta.civiccard.config.ConfigStore;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 背屏卡片 —— 按配置动态渲染。
 *
 * 状态区：用户选择的状态项，最多 5 项
 * 功能区：2×2 槽位，长按或点击触发（可配）
 */
public class CardActivity extends Activity {

    private static final long POLL_MS = 2000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;

    private ImageView ivBg;
    private TextView tvCarName, tvUpdate, tvTip;
    private LinearLayout llStatus, llFuncRow1, llFuncRow2;

    private VehicleStatus mLast;
    private long mLongPressMs;
    private CardConfig.FuncItem[] mFuncs;
    private List<String> mStatusKeys;

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
        rebuildFuncs();
        rebuildStatus();
    }

    private void bindViews() {
        ivBg = findViewById(R.id.ivBg);
        tvCarName = findViewById(R.id.tvCarName);
        tvUpdate = findViewById(R.id.tvUpdate);
        tvTip = findViewById(R.id.tvTip);
        llStatus = findViewById(R.id.llStatus);
        llFuncRow1 = findViewById(R.id.llFuncRow1);
        llFuncRow2 = findViewById(R.id.llFuncRow2);
    }

    // ---------------------------------------------------------- 背景

    private void applyBg() {
        String p = ConfigStore.getBgPath(this);
        if (p == null) return;
        try {
            ivBg.setImageBitmap(BitmapFactory.decodeFile(p));
        } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------- 状态区

    private void rebuildStatus() {
        mStatusKeys = ConfigStore.getStatusKeys(this);
        llStatus.removeAllViews();

        // 主项（第一项）用大字号，其余用小字
        for (int i = 0; i < mStatusKeys.size(); i++) {
            llStatus.addView(makeStatusView(mStatusKeys.get(i), i == 0));
        }
    }

    private View makeStatusView(String key, boolean hero) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = hero ? 0 : dp(10);
        box.setLayoutParams(lp);

        TextView value = new TextView(this);
        value.setTag("v:" + key);
        value.setText("--");
        value.setTextColor(Color.parseColor("#FFF2F5F8"));
        value.setTextSize(hero ? 46f : 20f);
        value.setGravity(Gravity.CENTER);
        if (hero) value.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView label = new TextView(this);
        label.setTag("l:" + key);
        CardConfig.StatusItem it = CardConfig.findStatus(key);
        label.setText((it == null ? key : it.label) + (hero ? "" : ""));
        label.setTextColor(Color.parseColor("#FF97A3B0"));
        label.setTextSize(hero ? 12f : 10f);
        label.setGravity(Gravity.CENTER);

        box.addView(value);
        box.addView(label);
        return box;
    }

    // ---------------------------------------------------------- 功能区

    private void rebuildFuncs() {
        mFuncs = ConfigStore.getFuncs(this);
        mLongPressMs = ConfigStore.getLongPressMs(this);

        llFuncRow1.removeAllViews();
        llFuncRow2.removeAllViews();

        for (int i = 0; i < mFuncs.length; i++) {
            View b = makeFuncButton(mFuncs[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) lp.leftMargin = dp(8);
            b.setLayoutParams(lp);
            if (i < 2) llFuncRow1.addView(b);
            else llFuncRow2.addView(b);
        }
    }

    private View makeFuncButton(CardConfig.FuncItem f) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(R.drawable.bg_btn);
        box.setPadding(0, dp(10), 0, dp(10));

        TextView icon = new TextView(this);
        icon.setText(f.icon.isEmpty() ? "·" : f.icon);
        icon.setTextSize(22f);
        icon.setGravity(Gravity.CENTER);

        TextView label = new TextView(this);
        label.setText(f.label);
        label.setTextColor(Color.parseColor("#FF97A3B0"));
        label.setTextSize(10f);
        label.setGravity(Gravity.CENTER);

        box.addView(icon);
        box.addView(label);

        if (f == CardConfig.FUNC_NONE || f.label.equals("（空）")) {
            box.setAlpha(0.25f);
            return box;
        }

        // 长按防误触：绑定了长按时长就要求长按，否则点击
        if (mLongPressMs > 0) {
            box.setOnLongClickListener(v -> {
                fire(f);
                return true;
            });
            box.setOnClickListener(v ->
                    showTip("长按 " + (mLongPressMs / 1000f) + " 秒触发"));
        } else {
            box.setOnClickListener(v -> fire(f));
        }
        return box;
    }

    private void fire(CardConfig.FuncItem f) {
        if (f.alias == null) return;

        if ("__refresh__".equals(f.alias)) {
            refresh();
            showTip("已刷新");
            return;
        }

        String block = mLast != null ? mLast.preconditionBlockReason(f.alias) : null;
        StringBuilder msg = new StringBuilder("确定要").append(f.label).append("吗？");
        if (block != null) {
            msg.append("\n\n注意：车辆当前").append(block).append("，可能无法执行。");
        }
        new AlertDialog.Builder(this)
                .setTitle(f.label)
                .setMessage(msg.toString())
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    showTip(f.label + "中");
                    CommandSender.send(f.alias);
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
            for (String k : mStatusKeys) setStatusValue(k, "--");
            tvUpdate.setText("更新 --");
            return;
        }
        mLast = st;
        for (String k : mStatusKeys) {
            setStatusValue(k, formatValue(st, k));
        }
        tvUpdate.setText(ageText(BridgeReader.statusAgeMs()));
    }

    private void setStatusValue(String key, String v) {
        View root = llStatus;
        for (int i = 0; i < llStatus.getChildCount(); i++) {
            View child = llStatus.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout box = (LinearLayout) child;
            for (int j = 0; j < box.getChildCount(); j++) {
                View t = box.getChildAt(j);
                if (t instanceof TextView) {
                    Object tag = t.getTag();
                    if (("v:" + key).equals(tag)) ((TextView) t).setText(v);
                }
            }
        }
    }

    /** 把字段值格式化成显示文本 */
    private String formatValue(VehicleStatus st, String key) {
        switch (key) {
            case "voltage":
                return st.hasVoltage ? String.format(Locale.US, "%.1f", st.voltage) : "--";
            case "totalMileage":
                return st.hasTotalMileage ? String.valueOf((int) st.totalMileage) : "--";
            case "continuationMileage":
                return st.hasRemainMileage ? String.valueOf(st.continuationMileage) : "--";
            case "remainLMileage":
                return st.hasRemainMileage ? String.valueOf(st.remainLMileage) : "--";
            case "batteryPercentage":
                return st.hasBattery ? st.batteryPercentage + "%" : "--";
            case "temperatureInside":
                return st.hasTempInside ? String.format(Locale.US, "%.0f°", st.temperatureInside) : "--";
            case "temperatureOutside":
                return String.format(Locale.US, "%.0f°", st.temperatureOutside);
            case "trunk":
                return st.hasTrunk ? (st.trunk == 1 ? "开" : "关") : "--";
            case "hood":
                return st.hood == 1 ? "开" : "关";
            case "frontLeftLock":
            case "frontRightLock":
            case "rearLeftLock":
            case "rearRightLock":
                return st.hasDoorLock ? (st.doorsLocked() ? "已锁" : "未锁") : "--";
            case "frontLeftDoor":
            case "frontRightDoor":
            case "rearLeftDoor":
            case "rearRightDoor":
                return st.hasDoor ? (st.doorsClosed() ? "关" : "开") : "--";
            case "power":
                return String.valueOf(st.power);
            case "positionP":
                return st.positionP == 1 ? "非P" : "P";
            default:
                return "--";
        }
    }

    private String ageText(long ageMs) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0) return "更新 --";
        long sec = ageMs / 1000L;
        if (sec < 60) return "刚刚更新";
        if (sec < 3600) return "更新 " + (sec / 60) + " 分钟前";
        if (sec < 86400) return "更新 " + (sec / 3600) + " 小时前";
        return "更新 " + (sec / 86400) + " 天前";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 配置可能变更，回来重建
        applyBg();
        rebuildFuncs();
        rebuildStatus();
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
