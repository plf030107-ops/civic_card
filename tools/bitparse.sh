#!/system/bin/sh
# 复现 StatusCodec 的位域解析，验证偏移表正确性
# 用法: sh bitparse.sh <hexstring>
# hexstring 可以是 76 字节整帧，或 >=56 字节的头部+载荷

H="$1"
[ -z "$H" ] && { echo "usage: bitparse.sh <hexframe>"; exit 1; }

echo "$H" | sed 's/../& /g' | tr ' ' '\n' | grep -v '^$' | awk '
BEGIN { hex="0123456789abcdef"; for(k=1;k<=16;k++){c=substr(hex,k,1); HV[c]=k-1; HV[toupper(c)]=k-1} }
{ b[++n] = HV[substr($1,1,1)]*16 + HV[substr($1,2,1)] }
function bits(off, len,   v, i, bit, bi, bb, sh) {
  v = 0
  for (i = 0; i < len; i++) {
    bit = off + i
    bi = int(bit / 8)
    bb = 7 - (bit % 8)
    sh = b[57 + bi]          # 1-based，载荷从第57字节开始
    v = v * 2 + (int(sh / (2 ^ bb)) % 2)
  }
  return v
}
END {
  printf "总字节=%d\n\n", n
  if (n < 76) { printf "警告: 不足76字节, 按载荷起点57读取\n" }

  printf "== 门/锁 (字节0) ==\n"
  printf "左前门=%d 右前门=%d 左后门=%d 右后门=%d\n", bits(0,1),bits(1,1),bits(2,1),bits(3,1)
  printf "左前锁=%d 右前锁=%d 左后锁=%d 右后锁=%d\n", bits(4,1),bits(5,1),bits(6,1),bits(7,1)

  printf "\n== 电源/窗 (字节1) ==\n"
  printf "power=%d\n", bits(8,2)
  printf "左前窗=%d 右前窗=%d 左后窗=%d 右后窗=%d trunk=%d hood=%d\n", \
         bits(10,1),bits(11,1),bits(12,1),bits(13,1),bits(14,1),bits(15,1)

  printf "\n== 温度/电压 ==\n"
  printf "车内=%0.1fC 车外=%0.1fC 电压=%0.1fV\n", \
         bits(16,8)*0.5-40, bits(24,8)*0.5-40, bits(32,8)*0.1

  printf "\n== 里程/电量 ==\n"
  printf "remainL=%dkm total=%0.1fkm 续航=%d 电量=%d%% 小电瓶=%d\n", \
         bits(40,8)*12, bits(48,32)*0.1, bits(120,16), bits(136,16), bits(152,8)

  printf "\n== 空调 ==\n"
  printf "AC=%d 自动=%d 左温=%0.1f 右温=%0.1f 风速=%d/%d\n", \
         bits(104,1), bits(105,1), bits(96,8)*0.5, bits(88,8)*0.5, bits(84,4), bits(80,4)

  printf "\n== 状态 ==\n"
  printf "P挡=%d 车辆模式=%d 远程模式=%d 防盗=%d\n", \
         bits(112,1), bits(113,1), bits(114,2), bits(116,4)
}
'
