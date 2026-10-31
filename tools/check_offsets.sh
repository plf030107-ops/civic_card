#!/system/bin/sh
# 校验 StatusCodec 偏移表：用 vehicleInfoRules.json 原文重新推导，
# 和 Java 里硬编码的常量逐个比对。
#
# 用法: sh check_offsets.sh <vehicleInfoRules.json>

J="$1"
[ -z "$J" ] && { echo "usage: check_offsets.sh <json>"; exit 1; }

echo "=== 从 $J 重新推导 ==="
echo "字段                   bit偏移  bit宽  字节偏移(载荷内)"
echo "----------------------------------------------------"

sed -E "s/^ +//" "$J" | sed "s/,$//" | sed 's/^"//;s/"$//' | sed 's/": / /' | awk '
$1 == "key" { k = $2 }
$1 == "length" {
  if (k != "") {
    printf "%-22s %5d %5d %6d\n", k, bitoff, $2, int(bitoff/8)
    bitoff += $2
    k = ""
  }
}
END {
  printf "----------------------------------------------------\n"
  printf "bit 合计 = %d  (= %d 字节)\n", bitoff, bitoff/8
}
'
