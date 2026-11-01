package hk.eta.civiccard.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 抓小米推送（MiPush）到达的消息 —— 车况变化的第三条通道。
 *
 * 为什么想到这条：
 *   App 在前台却没有任何 socket 连接（ss 查不到），
 *   说明它依赖系统级推送通道，即 MiPush。
 *   广播接收器：org.android.agoo.xiaomi.MiPushBroadcastReceiver
 *   动作：com.xiaomi.mipush.MESSAGE_ARRIVED
 *
 * 这里同时挂两处：
 *   1. 该 Receiver 的 onReceive
 *   2. MiPushMessage.getMessageBody / getContent
 */
final class PushSniffer {

    private static volatile boolean sInstalled = false;
    private static int sCount = 0;

    private static final String RECEIVER =
            "org.android.agoo.xiaomi.MiPushBroadcastReceiver";

    private PushSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        // 1. 广播接收器
        try {
            Class<?> rc = XposedHelpers.findClassIfExists(RECEIVER, cl);
            if (rc != null) {
                XposedBridge.hookAllMethods(rc, "onReceive", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args.length < 2) return;
                        Object intent = param.args[1];
                        if (!(intent instanceof Intent)) return;
                        Intent it = (Intent) intent;
                        dumpIntent(it);
                    }
                });
                XposedBridge.log("CivicCard PushSniffer receiver ok");
            } else {
                XposedBridge.log("CivicCard PushSniffer receiver MISS");
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard PushSniffer receiver fail: " + t);
        }

        // 2. MiPushMessage 内容
        try {
            Class<?> msg = XposedHelpers.findClassIfExists(
                    "com.xiaomi.mipush.sdk.MiPushMessage", cl);
            if (msg != null) {
                for (String m : new String[]{"getContent", "getMessageBody", "getExtra"}) {
                    try {
                        XposedBridge.hookAllMethods(msg, m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object r = param.getResult();
                                if (r != null) handle(m + "=" + r);
                            }
                        });
                    } catch (Throwable ignored) { }
                }
                XposedBridge.log("CivicCard PushSniffer MiPushMessage ok");
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard PushSniffer msg fail: " + t);
        }
    }

    private static void dumpIntent(Intent it) {
        String action = it.getAction();
        if (action == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("action=").append(action).append('\n');

        Bundle ex = it.getExtras();
        if (ex != null) {
            try {
                Set<String> keys = ex.keySet();
                for (String k : keys) {
                    Object v = ex.get(k);
                    sb.append(k).append('=').append(String.valueOf(v)).append('\n');
                }
            } catch (Throwable ignored) { }
        }
        handle(sb.toString());
    }

    private static void handle(String s) {
        if (s == null || s.length() < 8) return;
        sCount++;
        StatusStore.writeFile("push_" + (sCount % 20) + ".txt", s);
        if (sCount <= 8 || sCount % 10 == 0) {
            XposedBridge.log("CivicCard PUSH #" + sCount
                    + " len=" + s.length()
                    + " head=" + s.substring(0, Math.min(120, s.length())));
        }
    }
}
