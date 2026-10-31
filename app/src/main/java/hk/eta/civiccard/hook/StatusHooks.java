package hk.eta.civiccard.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 车况与指令结果的 hook。
 *
 * 【已从 dexdump 精确确认】
 *
 * com.ingeek.nokeeu.key.listener.VehicleCommandListener（接口，方法全 public）
 *   boolean isEnableCommandRawDataReturn()
 *   boolean isEnableCommandRawDataReturn(String)
 *   void onExecuteFail(IngeekVehicleCommandResponse, IngeekException)
 *   void onExecuteSuccess(IngeekVehicleCommandResponse)
 *   void onExecuting(IngeekVehicleCommandResponse)
 *   void onReceiveCommandRawData(String, byte[], IngeekException)   ← 带 vin 的原始帧
 *   void onReceiveCommandRawData(byte[], IngeekException)           ← 原始帧
 *
 * com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommandResponse
 *   字段 private: commandCode(I) commandTag(B) commandValue(byte[]) reason(B) result(B) vin(String)
 *   公开 getter: getCommandCode/getCommandTag/getCommandValue/getReason/getResult/getVin
 *
 * 车况帧就是从 onReceiveCommandRawData 的 byte[] 出来的。
 */
final class StatusHooks {

    private static final String CLS_LISTENER = "com.ingeek.nokeeu.key.listener.VehicleCommandListener";
    private static final String CLS_RESPONSE = "com.ingeek.nokeeu.key.business.bean.IngeekVehicleCommandResponse";

    private StatusHooks() {}

    static void install(ClassLoader cl) {
        hookRawData(cl);
        hookExecState(cl);
        log("status hooks installed");
    }

    /**
     * 车况帧主来源。两个重载都挂：
     *   onReceiveCommandRawData(byte[], IngeekException)
     *   onReceiveCommandRawData(String, byte[], IngeekException)
     */
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
                            if (vin != null) {
                                BridgeServer.get().setVin(vin);
                            }
                            BridgeServer.get().onRawStatusFrame(frame);
                        }
                    }).size();
            log("hooked onReceiveCommandRawData x" + n);
        } catch (Throwable t) {
            log("hookRawData failed: " + t);
        }
    }

    /** 指令执行结果，用于卡片显示"解锁中/已解锁" */
    private static void hookExecState(ClassLoader cl) {
        try {
            Class<?> listener = XposedHelpers.findClassIfExists(CLS_LISTENER, cl);
            if (listener == null) return;

            XposedBridge.hookAllMethods(listener, "onExecuteSuccess", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object resp = param.args.length > 0 ? param.args[0] : null;
                    BridgeServer.get().onCommandResult(true, describeResponse(resp));
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
                    Object resp = param.args.length > 0 ? param.args[0] : null;
                    BridgeServer.get().onCommandExecuting(describeResponse(resp));
                }
            });
            log("hooked exec state callbacks");
        } catch (Throwable t) {
            log("hookExecState failed: " + t);
        }
    }

    /** 用公开 getter 读响应内容 */
    private static String describeResponse(Object resp) {
        if (resp == null) return "null";
        StringBuilder sb = new StringBuilder();
        try {
            Method getCode = resp.getClass().getMethod("getCommandCode");
            Method getResult = resp.getClass().getMethod("getResult");
            Method getReason = resp.getClass().getMethod("getReason");
            sb.append("code=").append(getCode.invoke(resp))
              .append(" result=").append(getResult.invoke(resp))
              .append(" reason=").append(getReason.invoke(resp));
        } catch (Throwable t) {
            sb.append(resp.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private static void log(String msg) {
        XposedBridge.log("CivicCard " + msg);
    }
}
