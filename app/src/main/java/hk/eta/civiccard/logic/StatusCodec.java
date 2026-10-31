package hk.eta.civiccard.logic;

/**
 * 车况帧解析器。
 *
 * 【重要：字段是位域，不是字节对齐】
 * 从 assets/vehicleInfoRules.json 逐位推导（tools/check_offsets.sh 可复现）：
 *
 *   位偏移 宽  字段               字节
 *   -----------------------------------------
 *     0    1  frontLeftDoor       0  bit0
 *     1    1  frontRightDoor      0  bit1
 *     2    1  rearLeftDoor        0  bit2
 *     3    1  rearRightDoor       0  bit3
 *     4    1  frontLeftLock       0  bit4
 *     5    1  frontRightLock      0  bit5
 *     6    1  rearLeftLock        0  bit6
 *     7    1  rearRightLock       0  bit7
 *     8    2  power               1
 *    10    1  frontLeftWindow     1
 *    11    1  frontRightWindow    1
 *    12    1  rearLeftWindow      1
 *    13    1  rearRightWindow     1
 *    14    1  trunk               1
 *    15    1  hood                1
 *    16    8  temperatureInside   2     x*0.5-40
 *    24    8  temperatureOutside  3     x*0.5-40
 *    32    8  voltage             4     x*0.1
 *    40    8  remainLMileage      5     x*12
 *    48   32  totalMileage        6     x*0.1
 *    80    4  maxWindLevel        10
 *    84    4  currentWindLevel    10
 *    88    8  acRightTemp         11    x*0.5
 *    96    8  acLeftTemp          12    x*0.5
 *   104    1  acSwitch            13
 *   105    1  acAutoSwitch        13
 *   106    3  acRsv               13
 *   109    1  acAutoCycle         13
 *   110    1  acInternalCycle     13
 *   111    1  acExternalCycle     13
 *   112    1  positionP           14
 *   113    1  vehicleMode         14
 *   114    2  remoteMode          14
 *   116    4  burglarAlarm        14
 *   120   16  continuationMileage 15
 *   136   16  batteryPercentage   17
 *   152    8  smallBattery        19
 *   -----------------------------------------
 *   合计 160 bit = 20 字节
 *
 * 帧结构：56 字节 header + 20 字节载荷，littleEndian=0 → 位序 MSB first。
 *
 * 注意：本类只是兜底。实际 hook 拿到的是 SDK 已解析好的对象，
 * 优先用反射读 VehicleItemBean 的 key/value。
 */
public final class StatusCodec {

    public static final int HEADER_LEN = 56;
    public static final int PAYLOAD_LEN = 20;
    public static final int FRAME_MIN = HEADER_LEN + PAYLOAD_LEN;

    private StatusCodec() {}

    /** 按位域取无符号整数（大端位序，MSB first） */
    private static int bits(byte[] f, int bitOffset, int bitLen) {
        int v = 0;
        for (int i = 0; i < bitLen; i++) {
            int bit = bitOffset + i;
            int byteIdx = HEADER_LEN + (bit >>> 3);
            int bitInByte = 7 - (bit & 7);
            int b = (f[byteIdx] >>> bitInByte) & 1;
            v = (v << 1) | b;
        }
        return v;
    }

    public static VehicleStatus decode(byte[] frame) {
        if (frame == null || frame.length < FRAME_MIN) return null;

        VehicleStatus s = new VehicleStatus();

        // 字节 0：4 门 + 4 锁
        s.frontLeftDoor  = bits(frame, 0, 1);
        s.frontRightDoor = bits(frame, 1, 1);
        s.rearLeftDoor   = bits(frame, 2, 1);
        s.rearRightDoor  = bits(frame, 3, 1);
        s.frontLeftLock  = bits(frame, 4, 1);
        s.frontRightLock = bits(frame, 5, 1);
        s.rearLeftLock   = bits(frame, 6, 1);
        s.rearRightLock  = bits(frame, 7, 1);

        s.power = bits(frame, 8, 2);

        s.frontLeftWindow  = bits(frame, 10, 1);
        s.frontRightWindow = bits(frame, 11, 1);
        s.rearLeftWindow   = bits(frame, 12, 1);
        s.rearRightWindow  = bits(frame, 13, 1);
        s.trunk = bits(frame, 14, 1);
        s.hood  = bits(frame, 15, 1);

        s.temperatureInside  = bits(frame, 16, 8) * 0.5 - 40.0;
        s.temperatureOutside = bits(frame, 24, 8) * 0.5 - 40.0;
        s.voltage            = bits(frame, 32, 8) * 0.1;
        s.remainLMileage     = bits(frame, 40, 8) * 12;
        s.totalMileage       = bits(frame, 48, 32) * 0.1;

        s.maxWindLevel     = bits(frame, 80, 4);
        s.currentWindLevel = bits(frame, 84, 4);
        s.acRightTemp      = bits(frame, 88, 8) * 0.5;
        s.acLeftTemp       = bits(frame, 96, 8) * 0.5;

        s.acSwitch         = bits(frame, 104, 1);
        s.acAutoSwitch     = bits(frame, 105, 1);
        // acRsv / acAutoCycle / acInternalCycle / acExternalCycle 暂不映射

        s.positionP    = bits(frame, 112, 1);
        s.vehicleMode  = bits(frame, 113, 1);
        s.remoteMode   = bits(frame, 114, 2);
        s.burglarAlarm = bits(frame, 116, 4);

        s.continuationMileage = bits(frame, 120, 16);
        s.batteryPercentage   = bits(frame, 136, 16);
        s.smallBattery        = bits(frame, 152, 8);

        s.timestampMs = System.currentTimeMillis();
        return s;
    }
}
