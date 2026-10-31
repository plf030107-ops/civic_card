package hk.eta.civiccard.hook;

import android.content.SharedPreferences;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 抓取 App 解密后的 SharedPreferences 内容。
 *
 * 上一版直接 getAll() 拿到的是仍加密的密文（EncryptedSharedPreferences
 * 只在 App 自己通过它读写时才解密）。
 *
 * 所以改成：hook EncryptedSharedPreferences 的 getString 等方法，
 * App 每读一个值，我们就拿到明文。同时 hook putString 捕获写入。
 */
final class CredDumper {

    private static final StringBuilder sBuf = new StringBuilder();
    private static long sLastFlush = 0;

    private CredDumper() {}

    static void install(ClassLoader cl) {
        try {
            Class<?> esp = XposedHelpers.findClassIfExists(
                    "androidx.security.crypto.EncryptedSharedPreferences", cl);
            if (esp == null) {
                XposedBridge.log("CivicCard esp not found");
                return;
            }

            // 捕获读取：App 拿到的一定是明文
            int n = 0;
            for (Method m : esp.getDeclaredMethods()) {
                String name = m.getName();
                if (!name.startsWith("get")) continue;
                if (m.getReturnType() == void.class) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args.length == 0) return;
                            Object k = param.args[0];
                            Object v = param.getResult();
                            if (!(k instanceof String) || v == null) return;
                            record((String) k, String.valueOf(v));
                        }
                    });
                    n++;
                } catch (Throwable ignored) { }
            }

            // 捕获写入
            int p = 0;
            for (Method m : esp.getDeclaredMethods()) {
                String name = m.getName();
                if (!name.startsWith("put")) continue;
                if (m.getParameterTypes().length != 2) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object k = param.args[0];
                            Object v = param.args[1];
                            if (k instanceof String) {
                                record((String) k, String.valueOf(v));
                            }
                        }
                    });
                    p++;
                } catch (Throwable ignored) { }
            }

            XposedBridge.log("CivicCard CredDumper installed get=" + n + " put=" + p);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard CredDumper fail: " + t);
        }
    }

    private static void record(String key, String value) {
        if (key == null || value == null) return;
        if (value.length() < 8) return;

        synchronized (sBuf) {
            sBuf.append(key).append(" = ").append(value).append('\n');
            long now = System.currentTimeMillis();
            // 节流写盘
            if (now - sLastFlush > 2000) {
                sLastFlush = now;
                StatusStore.writeFile("creds.txt", sBuf.toString());
            }
        }
    }
}
