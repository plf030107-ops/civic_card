package hk.eta.civiccard.hook;

import android.os.FileObserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 监听卡片写入的指令请求，转成 IngeekVehicleCommand 调 XKeyApi。
 *
 * 【已从 dexdump 精确确认的 API】
 *
 * com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommand
 *   字段: commandCode(I) commandTag(B) commandValue(byte[]) name(String) timeout(I) vin(String)
 *   构造: 全 private / synthetic → 只能经 Builder 创建
 *   方法: s(Object, Object)  native  —— 序列化/发送底层
 *
 * com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommand$Builder
 *   public Builder()
 *   public IngeekVehicleCommand create()                 ← 注意是 create() 不是 build()
 *   public Builder setName(String)                       ← 不是 commandName()
 *   public Builder setTimeout(int)
 *   public Builder setVin(String)
 *   public Builder setData(byte[])
 *   public Builder initWithCode(int)
 *   public Builder initWithData(byte[])
 *   public Builder initWithTag(byte)
 */
final class CommandWatcher {

    private static final String DIR = "/data/local/tmp/civic_card";
    private static final String F_REQ = DIR + "/cmd_req";

    private static final String CLS_CMD = "com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommand";
    private static final String CLS_BUILDER = CLS_CMD + "$Builder";
    private static final String CLS_XKEY_API = "com.ingeek.nokeeu.key.xplan.XKeyApi";

    private FileObserver mObserver;
    private final ClassLoader mCl;
    private final Class<?> mXKeyApi;
    private long mLastFire = 0;

    CommandWatcher(ClassLoader cl) {
        mCl = cl;
        Class<?> api = null;
        try {
            api = Class.forName(CLS_XKEY_API, true, cl);
        } catch (Throwable t) {
            HookEntry.log("XKeyApi not found: " + t);
        }
        mXKeyApi = api;
    }

    void start() {
        File dir = new File(DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        // 目录必须可写，卡片进程用 root 写，这里放宽权限
        //noinspection ResultOfMethodCallIgnored
        dir.setReadable(true, false);
        //noinspection ResultOfMethodCallIgnored
        dir.setWritable(true, false);
        //noinspection ResultOfMethodCallIgnored
        dir.setExecutable(true, false);

        mObserver = new FileObserver(DIR, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, String path) {
                if (path == null || !path.startsWith("cmd_req")) return;
                long now = System.currentTimeMillis();
                if (now - mLastFire < 150) return;   // rename + close_write 双触发去重
                mLastFire = now;
                handleRequest();
            }
        };
        try {
            mObserver.startWatching();
            HookEntry.log("command watcher started on " + DIR);
        } catch (Throwable t) {
            HookEntry.log("watcher start failed: " + t);
        }
    }

    private void handleRequest() {
        String payload = read(F_REQ);
        if (payload == null || payload.isEmpty()) return;
        //noinspection ResultOfMethodCallIgnored
        new File(F_REQ).delete();

        String[] parts = payload.split("\t");
        String alias = parts[0].trim();
        HookEntry.log("card request: " + alias);
        try {
            dispatch(alias);
        } catch (Throwable t) {
            HookEntry.log("dispatch failed: " + t);
            BridgeServer.get().onCommandResult(false, String.valueOf(t));
        }
    }

    /**
     * 反射构造指令并下发。
     * 走 Builder 的 create()，这是 SDK 唯一公开的构造路径。
     */
    private void dispatch(String alias) throws Exception {
        if (mXKeyApi == null) throw new IllegalStateException("XKeyApi unavailable");

        Class<?> builderCls = Class.forName(CLS_BUILDER, true, mCl);
        Class<?> cmdCls = Class.forName(CLS_CMD, true, mCl);

        Constructor<?> bc = builderCls.getDeclaredConstructor();
        bc.setAccessible(true);
        Object builder = bc.newInstance();

        // Builder.setName(String) —— 指令名就是 alias
        Method setName = builderCls.getDeclaredMethod("setName", String.class);
        setName.setAccessible(true);
        setName.invoke(builder, alias);

        // 超时按 command_timeout_config.json 的 BLE 3s / 远程 4s，取 4s 兜底
        try {
            Method setTimeout = builderCls.getDeclaredMethod("setTimeout", int.class);
            setTimeout.setAccessible(true);
            setTimeout.invoke(builder, 4000);
        } catch (Throwable ignored) { }

        // Builder.create() —— 不是 build()
        Method create = builderCls.getDeclaredMethod("create");
        create.setAccessible(true);
        Object command = create.invoke(builder);
        if (command == null) throw new IllegalStateException("create() returned null");

        // XKeyApi.sendVehicleCommand(IngeekVehicleCommand[, listener])
        Method target = null;
        Method targetWithListener = null;
        for (Method m : mXKeyApi.getDeclaredMethods()) {
            if (!"sendVehicleCommand".equals(m.getName())) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 1 && pt[0].equals(cmdCls)) target = m;
            else if (pt.length == 2 && pt[0].equals(cmdCls)) targetWithListener = m;
        }

        if (target == null && targetWithListener == null) {
            throw new IllegalStateException("sendVehicleCommand not found");
        }
        Method use = target != null ? target : targetWithListener;
        use.setAccessible(true);
        if (use.getParameterTypes().length == 1) {
            use.invoke(null, command);
        } else {
            use.invoke(null, command, null);   // listener 可空
        }

        BridgeServer.get().onCommandSent(alias);
        HookEntry.log("dispatched " + alias);
    }

    private static String read(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) { }
        }
    }
}
