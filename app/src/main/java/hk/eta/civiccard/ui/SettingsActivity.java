package hk.eta.civiccard.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import hk.eta.civiccard.R;
import hk.eta.civiccard.logic.BridgeReader;

/**
 * 配置/自检界面。用户从桌面点图标进来看到这个。
 *
 * 展示：
 *  - REAREye 是否安装
 *  - 权限是否授予
 *  - hook 桥是否就绪
 *  - 当前车况是否读到
 *  - 一键进入卡片预览
 */
public class SettingsActivity extends Activity {

    private TextView tvHost, tvPerm, tvBridge, tvStatus, tvHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvHost   = findViewById(R.id.tvHost);
        tvPerm   = findViewById(R.id.tvPerm);
        tvBridge = findViewById(R.id.tvBridge);
        tvStatus = findViewById(R.id.tvStatus);
        tvHint   = findViewById(R.id.tvHint);

        findViewById(R.id.btnPreview).setOnClickListener(v ->
                RearWidgetRegistrar.launchCard(this));

        findViewById(R.id.btnOpenHost).setOnClickListener(v ->
                RearWidgetRegistrar.openHostSettings(this));

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean host = RearWidgetRegistrar.isHostAvailable(this);
        boolean perm = RearWidgetRegistrar.hasPermission(this);
        boolean ready = BridgeReader.isReady();

        tvHost.setText(host ? "✓ REAREye 已安装" : "✗ 未安装 REAREye");
        tvPerm.setText(perm ? "✓ 背屏卡片权限已授予" : "✗ 缺少 ACCESS_REAR_WIDGET_API");

        if (ready) {
            tvBridge.setText("✓ 乘趣桥已就绪");
        } else {
            tvBridge.setText("✗ 乘趣桥未就绪（需在 LSPosed 启用模块并重启乘趣）");
        }

        String status = BridgeReader.readStatus() != null ? "✓ 已读到车况" : "✗ 暂无车况数据";
        tvStatus.setText(status);

        if (!host) {
            tvHint.setText("请先安装 REAREye。");
        } else if (!perm) {
            tvHint.setText("REAREye 未授予权限，请在其设置中允许本应用。");
        } else if (!ready) {
            tvHint.setText("1. LSPosed 启用本模块\n2. 作用域勾选 com.ingeek.nokey\n3. 强制停止乘趣后重新打开");
        } else {
            tvHint.setText("一切就绪。点「预览卡片」查看效果，或在 REAREye 中把本卡片加到背屏。");
        }
    }
}
