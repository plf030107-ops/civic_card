package hk.eta.civiccard.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/**
 * 向 REAREye 注册背屏卡片。
 *
 * 【实测接口】（dexdump 确认）
 *   hk.uwu.reareye.widgetapi.IRearWidgetApiService
 *     registerBusiness(String, String, String, int, int)
 *     registerBusinessWithoutFile(String, String, int, int)
 *     registerBusinessFile(String, String)
 *     unregisterBusiness(String, String)
 *     disableBusinessDisplay(String, String)
 *     disableCardDisplay(String, String, String)
 *     postNotice(String, String, Bundle, Bundle)
 *
 *   权限 hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API
 *
 * 【现实约束】REAREye 的 AIDL Service 未在 Manifest 对外声明，
 * bind 不到。所以采用「声明式卡片」路线：
 *   应用在 Manifest 内置 reareye.widget.* meta-data，
 *   REAREye 扫描到带 ACCESS_REAR_WIDGET_API 权限的应用即可列出本卡片，
 *   用户点一下挂到背屏。这条路径不需要 AIDL，最稳。
 */
public final class RearWidgetRegistrar {

    private static final String TAG = "CivicCard";

    public static final String PKG_REAREYE = "hk.uwu.reareye";
    public static final String PERM_REAR_WIDGET =
            "hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API";

    public static final String WIDGET_ID = "hk.eta.civiccard.main";
    public static final String WIDGET_NAME = "乘趣控车";
    public static final String DISPLAY_TITLE = "十代思域";

    private RearWidgetRegistrar() {}

    /** REAREye 是否已安装 */
    public static boolean isHostAvailable(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PKG_REAREYE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 本应用是否持有背屏卡片权限 */
    public static boolean hasPermission(Context ctx) {
        try {
            return ctx.checkSelfPermission(PERM_REAR_WIDGET)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 打开 REAREye 主界面，让用户在列表里挂载本卡片 */
    public static void openHostSettings(Context ctx) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(PKG_REAREYE, "hk.uwu.reareye.ui.MainActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "openHostSettings failed", t);
        }
    }

    /** 直接预览卡片 */
    public static void launchCard(Context ctx) {
        try {
            Intent i = new Intent(ctx, CardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "launchCard failed", t);
        }
    }

    /**
     * 卡片规格，对应 RearWidgetBusinessSpec：
     *   packageName / widgetName / widgetId / version / displayTitle / priority
     */
    public static Bundle buildSpec(Context ctx) {
        Bundle b = new Bundle();
        b.putString("packageName", ctx.getPackageName());
        b.putString("widgetName", WIDGET_NAME);
        b.putString("widgetId", WIDGET_ID);
        b.putInt("version", 1);
        b.putString("displayTitle", DISPLAY_TITLE);
        b.putInt("priority", 0);
        return b;
    }
}
