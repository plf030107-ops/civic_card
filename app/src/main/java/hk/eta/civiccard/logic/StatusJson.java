package hk.eta.civiccard.logic;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * 车况 JSON 序列化 / 反序列化。
 *
 * 两种输出：
 *   toJson()      全字段，用于 BLE 帧解析出的完整对象
 *   fromPState()  只输出确实拿到的字段，用于云端 pstateData（部分字段）
 *
 * 反序列化时缺失字段保持 0，因此卡片侧必须靠「字段是否存在」判断，
 * 不能把 0 当成「关闭/未锁」。
 */
public final class StatusJson {

    private StatusJson() {}

    // ------------------------------------------------------------ 全量

    public static String toJson(VehicleStatus s) {
        JSONObject o = new JSONObject();
        try {
            o.put("ts", s.timestampMs);
            o.put("frontLeftDoor", s.frontLeftDoor);
            o.put("frontRightDoor", s.frontRightDoor);
            o.put("rearLeftDoor", s.rearLeftDoor);
            o.put("rearRightDoor", s.rearRightDoor);
            o.put("frontLeftLock", s.frontLeftLock);
            o.put("frontRightLock", s.frontRightLock);
            o.put("rearLeftLock", s.rearLeftLock);
            o.put("rearRightLock", s.rearRightLock);
            o.put("frontLeftWindow", s.frontLeftWindow);
            o.put("frontRightWindow", s.frontRightWindow);
            o.put("rearLeftWindow", s.rearLeftWindow);
            o.put("rearRightWindow", s.rearRightWindow);
            o.put("trunk", s.trunk);
            o.put("hood", s.hood);
            o.put("power", s.power);
            o.put("voltage", s.voltage);
            o.put("remainLMileage", s.remainLMileage);
            o.put("totalMileage", s.totalMileage);
            o.put("continuationMileage", s.continuationMileage);
            o.put("batteryPercentage", s.batteryPercentage);
            o.put("smallBattery", s.smallBattery);
            o.put("temperatureInside", s.temperatureInside);
            o.put("temperatureOutside", s.temperatureOutside);
            o.put("acSwitch", s.acSwitch);
            o.put("acAutoSwitch", s.acAutoSwitch);
            o.put("acLeftTemp", s.acLeftTemp);
            o.put("acRightTemp", s.acRightTemp);
            o.put("maxWindLevel", s.maxWindLevel);
            o.put("currentWindLevel", s.currentWindLevel);
            o.put("positionP", s.positionP);
            o.put("vehicleMode", s.vehicleMode);
            o.put("remoteMode", s.remoteMode);
            o.put("burglarAlarm", s.burglarAlarm);
        } catch (JSONException ignored) { }
        return o.toString();
    }

    public static VehicleStatus fromJson(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            VehicleStatus s = new VehicleStatus();
            s.timestampMs = o.optLong("ts", 0);

            // 逐字段判存在，避免把缺失当 0
            s.hasDoorLock = o.has("frontLeftLock");
            s.hasDoor = o.has("frontLeftDoor");
            s.hasWindow = o.has("frontLeftWindow");
            s.hasTrunk = o.has("trunk");
            s.hasVoltage = o.has("voltage");
            s.hasTotalMileage = o.has("totalMileage");
            s.hasRemainMileage = o.has("continuationMileage");
            s.hasBattery = o.has("batteryPercentage");
            s.hasTempInside = o.has("temperatureInside");

            s.frontLeftDoor  = o.optInt("frontLeftDoor");
            s.frontRightDoor = o.optInt("frontRightDoor");
            s.rearLeftDoor   = o.optInt("rearLeftDoor");
            s.rearRightDoor  = o.optInt("rearRightDoor");
            s.frontLeftLock  = o.optInt("frontLeftLock");
            s.frontRightLock = o.optInt("frontRightLock");
            s.rearLeftLock   = o.optInt("rearLeftLock");
            s.rearRightLock  = o.optInt("rearRightLock");
            s.frontLeftWindow  = o.optInt("frontLeftWindow");
            s.frontRightWindow = o.optInt("frontRightWindow");
            s.rearLeftWindow   = o.optInt("rearLeftWindow");
            s.rearRightWindow  = o.optInt("rearRightWindow");
            s.trunk = o.optInt("trunk");
            s.hood  = o.optInt("hood");
            s.power = o.optInt("power");
            s.voltage = o.optDouble("voltage", 0);
            s.remainLMileage = o.optInt("remainLMileage");
            s.totalMileage = o.optDouble("totalMileage", 0);
            s.continuationMileage = o.optInt("continuationMileage");
            s.batteryPercentage = o.optInt("batteryPercentage");
            s.smallBattery = o.optInt("smallBattery");
            s.temperatureInside = o.optDouble("temperatureInside", 0);
            s.temperatureOutside = o.optDouble("temperatureOutside", 0);
            s.acSwitch = o.optInt("acSwitch");
            s.acAutoSwitch = o.optInt("acAutoSwitch");
            s.acLeftTemp = o.optDouble("acLeftTemp", 0);
            s.acRightTemp = o.optDouble("acRightTemp", 0);
            s.maxWindLevel = o.optInt("maxWindLevel");
            s.currentWindLevel = o.optInt("currentWindLevel");
            s.positionP = o.optInt("positionP");
            s.vehicleMode = o.optInt("vehicleMode");
            s.remoteMode = o.optInt("remoteMode");
            s.burglarAlarm = o.optInt("burglarAlarm");
            return s;
        } catch (JSONException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ 最小可信

    /**
     * 只输出确实解析到的字段，缺失的不出现。
     * 卡片据此区分「未知」和「已知为 0」。
     */
    public static String fromPState(PStateDecoder.Result r) {
        if (r == null || !r.valid) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(System.currentTimeMillis());
        if (r.voltage >= 0) {
            sb.append(",\"voltage\":").append(String.format(Locale.US, "%.1f", r.voltage));
        }
        if (r.totalMileage >= 0) {
            sb.append(",\"totalMileage\":").append(r.totalMileage);
        }
        if (r.remainMileage >= 0) {
            sb.append(",\"continuationMileage\":").append(r.remainMileage);
        }
        sb.append('}');
        return sb.toString();
    }
}
