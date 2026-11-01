package hk.eta.civiccard.hook;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 乘趣（com.ingeek.nokey）Hook 主入口。
 *
 * 实测环境：
 *   包名 com.ingeek.nokey  uid u0_a378(10378)  数据目录 /data/user/0/com.ingeek.nokey
 *   versionName 5.0.0  versionCode 77410  targetSdk 35
 *   4 个 dex 未加壳（dex\n037），可静态分析
 *
 * 只做三件事，绝不修改乘趣任何文件：
 *   1. hook XKeyApi.sendVehicleCommand  —— 观察 App 自己发了什么指令
 *   2. hook VehicleCommandListener.onReceiveCommandRawData —— 截获车况帧
 *   3. FileObserver 监听 /data/local/tmp/civic_card/cmd_req —— 卡片发指令
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "CivicCard";
    private static final String TARGET_PKG = "com.ingeek.nokey";

    private static final String CLS_XKEY_API = "com.ingeek.nokeeu.key.xplan.XKeyApi";
    private static final String CLS_CMD = "com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommand";

    private static volatile boolean sHooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;
        if (sHooked) return;
        sHooked = true;

        log("loaded pkg=" + lpparam.packageName
                + " proc=" + lpparam.processName
                + " uid=" + android.os.Process.myUid());

        try {
            hookApplication(lpparam);
            hookSendVehicleCommandVerbose(lpparam);
            StatusHooks.install(lpparam.classLoader);
            // StatusProbe.install(lpparam.classLoader);  // 已定位，关闭避免刷屏
            // BeanStatusReader.install(lpparam.classLoader);  // 写入的是协议定义，非真实车况，关闭
            CredDumper.install(lpparam.classLoader);
            NetDump.install(lpparam.classLoader);
            ConditionHook.install(lpparam.classLoader);
            StatusSniffer.install(lpparam.classLoader);
            // NetSniffer.install(lpparam.classLoader);  // 已定位，关闭避免刷屏

            try {
                new CommandWatcher(lpparam.classLoader).start();
            } catch (Throwable t) {
                log("watcher failed: " + t);
            }
            log("hook install OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook failed: " + t);
        }
    }

    private void hookApplication(XC_LoadPackage.LoadPackageParam lp) {
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context ctx = (Context) param.thisObject;
                try {
                    BridgeServer.get().attach(ctx);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " bridge attach failed: " + t);
                }
            }
        });
    }

    /**
     * 观察 App 自身发出的指令，用于对照验证卡片发的指令是否一致。
     * 用 dexdump 确认过两个重载：
     *   sendVehicleCommand(IngeekVehicleCommand)
     *   sendVehicleCommand(IngeekVehicleCommand, VehicleCommandSendListener)
     */
    private void hookSendVehicleCommandVerbose(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS_XKEY_API, lp.classLoader);
        if (cls == null) {
            log("XKeyApi not found");
            return;
        }
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!"sendVehicleCommand".equals(m.getName())) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object arg0 = param.args.length > 0 ? param.args[0] : null;
                    String desc = describeCommand(arg0);
                    log("app sent: " + desc);
                    BridgeServer.get().onCommandSent("APP:" + desc);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable t = param.getThrowable();
                    if (t != null) {
                        log("sendVehicleCommand threw: " + t);
                    }
                }
            });
            n++;
        }
        log("hooked sendVehicleCommand x" + n);
    }

    /**
     * IngeekVehicleCommand 字段全 private：
     *   commandCode(I) commandTag(B) commandValue(byte[]) name(String) timeout(I) vin(String)
     */
    private String describeCommand(Object cmd) {
        if (cmd == null) return "null";
        StringBuilder sb = new StringBuilder("Cmd{");
        String[] fields = {"name", "commandCode", "commandTag", "timeout", "vin"};
        boolean first = true;
        for (String f : fields) {
            try {
                Field fd = cmd.getClass().getDeclaredField(f);
                fd.setAccessible(true);
                Object v = fd.get(cmd);
                if (!first) sb.append(", ");
                sb.append(f).append('=').append(v);
                first = false;
            } catch (Throwable ignored) { }
        }
        sb.append('}');
        return sb.toString();
    }

    static void log(String msg) {
        XposedBridge.log(TAG + " " + msg);
    }
}
