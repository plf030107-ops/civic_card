package hk.eta.civiccard.hook;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 用 App 自己的 EncryptedSharedPreferences 解密凭证并落盘。
 *
 * 思路（参考跨品牌文档的做法）：
 *   宝马是 su -M <APP_UID> + app_process 调 App 自己的存储类；
 *   本机 app_process 受 dalvik-cache 权限限制跑不起来，
 *   所以改成在 Xposed 注入的 App 进程内直接调用同一套类。
 *   效果等价，且天然具有 App 的 Keystore 访问权限。
 *
 * 只读，不改动任何原始数据。
 */
final class CredDumper {

    private static volatile boolean sDone = false;

    private CredDumper() {}

    static void install(ClassLoader cl) {
        // 挂在 Application.onCreate 之后，等 App 自己初始化完存储
        XposedHelpers.findAndHookMethod(android.app.Application.class, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Context ctx = (Context) param.thisObject;
                        // 延迟几秒，确保 App 已写入凭证
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> dump(ctx), 8000);
                    }
                });
    }

    private static void dump(Context ctx) {
        if (sDone) return;
        sDone = true;
        try {
            StringBuilder out = new StringBuilder();
            out.append("=== EncryptedSharedPreferences dump ===\n");

            // 常见的几个 prefs 文件名
            String[] names = {"nokey_config.sp", "nokey_cache.sp", "INGEEK_BASE_SP_FILE"};
            for (String name : names) {
                dumpOne(ctx, name, out);
            }

            StatusStore.writeFile("creds.txt", out.toString());
            XposedBridge.log("CivicCard cred dump done len=" + out.length());
        } catch (Throwable t) {
            XposedBridge.log("CivicCard cred dump fail: " + t);
        }
    }

    private static void dumpOne(Context ctx, String name, StringBuilder out) {
        SharedPreferences sp = null;
        try {
            // 方式一：直接用 androidx 的 EncryptedSharedPreferences
            Class<?> c = Class.forName("androidx.security.crypto.EncryptedSharedPreferences");
            for (Method m : c.getDeclaredMethods()) {
                if (!"create".equals(m.getName())) continue;
                m.setAccessible(true);
                try {
                    // create(Context, String, MasterKey, ...) 需要 MasterKey，复杂
                    // 改用 App 自己的封装：大多数情况 App 已缓存实例
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        // 方式二：直接从 App 的 Application 里反射找已打开的 SP 缓存
        try {
            Object app = ctx.getApplicationContext();
            java.lang.reflect.Field f = null;
            Class<?> cls = app.getClass();
            while (cls != null && f == null) {
                for (java.lang.reflect.Field ff : cls.getDeclaredFields()) {
                    if (SharedPreferences.class.isAssignableFrom(ff.getType())) {
                        ff.setAccessible(true);
                        Object v = ff.get(app);
                        if (v instanceof SharedPreferences) {
                            dumpSp((SharedPreferences) v, name + "(fld)", out);
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            out.append("scan app fields fail: ").append(t).append('\n');
        }

        // 方式三：getSharedPreferences 直接取（若 App 存的是明文）
        try {
            sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
            if (sp != null) dumpSp(sp, name, out);
        } catch (Throwable t) {
            out.append("open ").append(name).append(" fail: ").append(t).append('\n');
        }
    }

    private static void dumpSp(SharedPreferences sp, String tag, StringBuilder out) {
        try {
            Map<String, ?> all = sp.getAll();
            if (all == null || all.isEmpty()) return;
            out.append("\n--- ").append(tag).append(" (").append(all.size()).append(") ---\n");
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                // 跳过 keystore 元数据
                if (k != null && k.contains("keyset")) continue;
                out.append(k).append(" = ").append(String.valueOf(v)).append('\n');
            }
        } catch (Throwable t) {
            out.append(tag).append(" getAll fail: ").append(t).append('\n');
        }
    }
}
