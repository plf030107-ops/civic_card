package hk.eta.civiccard.logic;

import org.json.JSONException;
import org.json.JSONObject;

/** VehicleStatus <-> JSON，卡片侧直接读。 */
public final class StatusJson {

    private StatusJson() {}

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
            o.put("doorsClosed", s.doorsClosed());
            o.put("doorsLocked", s.doorsLocked());
            o.put("trunkClosed", s.trunkClosed());
            o.put("windowsClosed", s.windowsClosed());
        } catch (JSONException ignored) { }
        return o.toString();
    }

    public static VehicleStatus fromJson(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            VehicleStatus s = new VehicleStatus();
            s.timestampMs = o.optLong("ts", 0);
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
}
