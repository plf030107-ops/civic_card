package hk.eta.civiccard.hook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 车况来源钩子。
 *
 * 两个来源都要挂：
 *  1. BLE：VehicleCommandListener.onReceiveCommandRawData(byte[]) —— 靠近车时
 *  2. 云端：VehicleDetailBean.setVehicleStatusData(String) —— 远程查询，不需要靠近车
 *
 * 第 2 条是关键：App 的远程车况最终写进数据库字段
 * com.ingeek.jsbridge.bean.db.VehicleDetailBean.setVehicleStatusData(String)，
 * 这里直接截获它，就不必依赖蓝牙连接。
 */
final class StatusHooks {

    private static final String CLS_LISTENER = "com.ingeek.nokeeu.key.listener.VehicleCommandListener";
    private static final String CLS_DETAIL_BEAN = "com.ingeek.jsbridge.bean.db.VehicleDetailBean";

    private StatusHooks() {}

    static void install(ClassLoader cl) {
        hookRawData(cl);
        hookCloudStatus(cl);
        hookExecState(cl);
        log("status hooks installed");
    }

    /** 车况帧（BLE 原始数据） */
    private static void hookRawData(ClassLoader cl) {
        try {
            Class<?> listener = XposedHelpers.findClassIfExists(CLS_LISTENER, cl);
            if (listener == null) {
                log("listener class not found");
                return;
            }
            int n = XposedBridge.hookAllMethods(listener, "onReceiveCommandRawData",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            byte[] frame = null;
                            String vin = null;
                            for (Object a : param.args) {
                                if (a instanceof byte[]) frame = (byte[]) a;
                                else if (a instanceof String) vin = (String) a;
                            }
                            if (frame == null || frame.length < 76) return;
                            if (vin != null) BridgeServer.get().setVin(vin);
                            BridgeServer.get().onRawStatusFrame(frame);
                        }
                    }).size();
            log("hooked onReceiveCommandRawData x" + n);
        } catch (Throwable t) {
            log("hookRawData failed: " + t);
        }
    }

    /**
     * 云端车况：setVehicleStatusData(String)
     *
     * 这是远程通道的落点。App 从云端拉到车况后写入该字段，
     * 截获它就能在不连蓝牙的情况下拿到车况。
     */
    private static void hookCloudStatus(ClassLoader cl) {
        try {
            Class<?> bean = XposedHelpers.findClassIfExists(CLS_DETAIL_BEAN, cl);
            if (bean == null) {
                log("VehicleDetailBean not found");
                return;
            }
            int n = XposedBridge.hookAllMethods(bean, "setVehicleStatusData",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args.length == 0) return;
                            Object v = param.args[0];
                            if (!(v instanceof String)) return;
                            String json = (String) v;
                            if (json.length() < 10) return;
                            log("cloud status len=" + json.length());
                            BridgeServer.get().onCloudStatus(json);
                        }
                    }).size();
            log("hooked setVehicleStatusData x" + n);
        } catch (Throwable t) {
            log("hookCloudStatus failed: " + t);
        }
    }

    /** 指令执行状态 */
    private static void hookExecState(ClassLoader cl) {
        try {
            Class<?> listener = XposedHelpers.findClassIfExists(CLS_LISTENER, cl);
            if (listener == null) return;

            XposedBridge.hookAllMethods(listener, "onExecuteSuccess", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    BridgeServer.get().onCommandResult(true, describeResponse(
                            param.args.length > 0 ? param.args[0] : null));
                }
            });

            XposedBridge.hookAllMethods(listener, "onExecuteFail", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object resp = param.args.length > 0 ? param.args[0] : null;
                    Object ex = param.args.length > 1 ? param.args[1] : null;
                    BridgeServer.get().onCommandResult(false,
                            describeResponse(resp) + " ex=" + ex);
                }
            });

            XposedBridge.hookAllMethods(listener, "onExecuting", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    BridgeServer.get().onCommandExecuting(describeResponse(
                            param.args.length > 0 ? param.args[0] : null));
                }
            });
            log("hooked exec state callbacks");
        } catch (Throwable t) {
            log("hookExecState failed: " + t);
        }
    }

    private static String describeResponse(Object resp) {
        if (resp == null) return "null";
        try {
            Method getCode = resp.getClass().getMethod("getCommandCode");
            Method getResult = resp.getClass().getMethod("getResult");
            Method getReason = resp.getClass().getMethod("getReason");
            return "code=" + getCode.invoke(resp)
                    + " result=" + getResult.invoke(resp)
                    + " reason=" + getReason.invoke(resp);
        } catch (Throwable t) {
            return resp.getClass().getSimpleName();
        }
    }

    private static void log(String msg) {
        XposedBridge.log("CivicCard " + msg);
    }
}
