package hk.eta.civiccard.logic;

import android.util.Base64;

import org.json.JSONObject;

/**
 * 解析乘趣云端 getVehicleState 返回的 pstateData。
 *
 * 【实测确定的字节布局】（用真实车辆数据反推验证）
 *
 *  偏移  长度  含义            验证值
 *  ------------------------------------------------
 *   0    1   帧头 0xFF
 *   1    1   0x00
 *   2    1   载荷长度（0x19 = 25）
 *   3    1   0x00
 *   4    1   版本 0xF3
 *   5    1   版本 0x02
 *   6    1   序号低字节（每帧递增）
 *   7    1   序号高字节
 *   8    1   状态位（门锁/门窗位域，待验证）
 *   9    1   电压 ×0.1        0x82=130 → 13.0V
 *  10    5   总里程(大端)      0x00020F93 = 135059 km
 *  15    2   剩余里程(大端)    0x01F7 = 503 km
 *  17    2   保留
 *  19    1   0xFF 分隔
 *  20    8   填充 0x00
 *
 * 整段 base64 后共 28 字节。
 *
 * 状态位（偏移8）待进一步验证：当前样本全为 0（车辆锁止且门窗全关）。
 * 在拿到「已解锁」样本前，位定义保持保守：只做全 0 判定。
 */
public final class PStateDecoder {

    /** 解析结果 */
    public static final class Result {
        public int voltageRaw = -1;
        public double voltage = -1;
        public int totalMileage = -1;
        public int remainMileage = -1;
        public int statusByte = -1;
        public long seq = -1;
        public boolean valid;
    }

    private PStateDecoder() {}

    /** 从 getVehicleState 的完整 JSON 里取 pstateData 并解码 */
    public static Result fromResponseJson(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject data = o.optJSONObject("data");
            if (data == null) return null;
            String b64 = data.optString("pstateData", "");
            if (b64.isEmpty() || "null".equals(b64)) return null;
            return decode(b64);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 解码 pstateData（base64 → 字节 → 字段） */
    public static Result decode(String b64) {
        try {
            byte[] b = Base64.decode(b64, Base64.DEFAULT);
            if (b == null || b.length < 17) return null;

            Result r = new Result();
            r.statusByte = b[8] & 0xFF;
            r.voltageRaw = b[9] & 0xFF;
            r.voltage = r.voltageRaw * 0.1;

            // 总里程：偏移10起 5 字节大端
            long total = 0;
            for (int i = 10; i < 15; i++) {
                total = (total << 8) | (b[i] & 0xFF);
            }
            r.totalMileage = (int) total;

            // 剩余里程：偏移15起 2 字节大端
            r.remainMileage = ((b[15] & 0xFF) << 8) | (b[16] & 0xFF);

            // 序号：偏移6-7
            r.seq = ((long) (b[6] & 0xFF) << 8) | (b[7] & 0xFF);

            r.valid = true;
            return r;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把解析结果转成卡片用的 status.json。
     *
     * 注意：状态位尚未完全解出，门锁/门窗字段暂不写入，
     * 避免显示错误状态。只写入已验证的数值字段。
     */
    public static String toStatusJson(Result r) {
        if (r == null || !r.valid) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"voltage\":").append(r.voltage);
        sb.append(",\"totalMileage\":").append(r.totalMileage);
        sb.append(",\"continuationMileage\":").append(r.remainMileage);
        sb.append('}');
        return sb.toString();
    }
}
