package hk.eta.civiccard.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * 向 REAREye 注册背屏卡片。
 *
 * 【REAREye AIDL 接口】（dexdump 确认）
 *   hk.uwu.reareye.widgetapi.IRearWidgetApiService
 *     registerBusiness(String, String, String, int, int)
 *     registerBusinessWithoutFile(String, String, int, int)
 *     registerBusinessFile(String, String)
 *     unregisterBusiness(String, String)
 *     disableCardDisplay(String, String, String)
 *
 *   权限 hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API
 *
 * 【现实约束】
 *   REAREye 的 AIDL Service 未在 Manifest 对外声明 descriptor，无法 bind。
 *   因此采用「声明式卡片」路线：
 *   应用在 Manifest 内置 reareye.widget.* meta-data，
 *   REAREye 通过 PackageManager 扫描带 ACCESS_REAR_WIDGET_API 权限的应用，
 *   列出本卡片供用户挂载。这条路径不需要 AIDL，最稳。
 */
public final class RearWidgetRegistrar {

    private static final String TAG = "CivicCard";

    public static final String PKG_REAREYE = "hk.uwu.reareye";
    public static final String PERM_REAR_WIDGET =
            "hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API";

    /** 卡片标识（需与 Manifest 里 reareye.widget.id 一致） */
    public static final String WIDGET_ID = "hk.eta.civiccard.main";
    public static final String WIDGET_NAME = "乘趣控车";
    public static final String DISPLAY_TITLE = "我的思域";

    private RearWidgetRegistrar() {}

    /** REAREye 是否已安装 */
    public static boolean isHostAvailable(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PKG_REAREYE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 本应用是否持有背屏卡片权限 */
    public static boolean hasPermission(Context ctx) {
        try {
            return ctx.checkSelfPermission(PERM_REAR_WIDGET)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
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
     * 兜底尝试：通过隐藏 AIDL 直接注册。
     *
     * 大部分情况下 bind 会失败（Service 未导出），失败就退回声明式路线。
     * 这里用反射构造 Binder 代理，避免依赖 AIDL 生成的 Stub。
     */
    public static boolean tryBindRegister(Context ctx) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(PKG_REAREYE,
                    "hk.uwu.reareye.widgetapi.RearWidgetApiService"));
            boolean ok = ctx.bindService(i, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        transactRegister(service, ctx);
                    } catch (Throwable t) {
                        Log.w(TAG, "transactRegister failed", t);
                    }
                    try { ctx.unbindService(this); } catch (Throwable ignored) { }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) { }
            }, Context.BIND_AUTO_CREATE);
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 通过 Parcel 直调 AIDL，不依赖生成的 Stub */
    private static void transactRegister(IBinder binder, Context ctx) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("hk.uwu.reareye.widgetapi.IRearWidgetApiService");
            // registerBusinessWithoutFile(String, String, int, int)
            data.writeString(ctx.getPackageName());
            data.writeString(WIDGET_ID);
            data.writeInt(1);   // version
            data.writeInt(0);   // priority
            binder.transact(2, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * 卡片规格，用于日志/调试。
     * 字段对应 RearWidgetBusinessSpec：
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
