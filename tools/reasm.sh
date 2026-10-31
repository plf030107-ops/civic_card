#!/system/bin/sh
# btsnooz 分片重组
#
# 分片记录格式: 02 dc2e SS SS PP PP LL LL DD DD ...
#   b[1-2]=dc2e  标记为分片
#   b[3-4]=本次序号(LE)   b[5-6]=前序序号(LE)
#   b[7-8]=???            b[9-10]=???  b[11+]=数据
#
# 完整记录: 02 HH HH LL LL | L2CAP len(2,LE) cid(2,LE) | ATT
#
# 策略: 收集所有 02dc2e 分片的数据部分，按序拼接，
#       再从中定位 "52 32 00" 和 "1b 35 00" 帧。
F="$1"
od -An -v -tx1 "$F" | tr -s ' ' '\n' | grep -v '^$' | awk '
BEGIN { hex = "0123456789abcdef"; for (k = 1; k <= 16; k++) HV[substr(hex, k, 1)] = k - 1 }
{ b[++n] = HV[substr($1,1,1)] * 16 + HV[substr($1,2,1)] }
function be32(p) { return b[p]*16777216 + b[p+1]*65536 + b[p+2]*256 + b[p+3] }
function le16(p) { return b[p] + b[p+1]*256 }
function hx(p, len,   s, k) { s=""; for (k=0;k<len;k++) s = s sprintf("%02x", b[p+k]); return s }
END {
  i = 17; rec = 0
  # 全部记录的数据区拼成一个流
  stream = ""
  while (i + 23 <= n) {
    incl = be32(i + 4)
    if (incl <= 0 || incl > 70000) break
    base = i + 24
    if (base + incl - 1 > n) break
    rec++
    if (b[base] == 2 && incl >= 9 && le16(base + 7) == 4) {
      dlen = le16(base + 3)
      if (dlen >= 8) {
        printf("== ATT r%d op=%02x h=%04x dlen=%d ==\n", rec, b[base+9], le16(base+10), dlen)
        printf("   head: %s\n", hx(base+9, 6))
      }
    }
    if (b[base] == 2 && b[base+1] == 0xdc && b[base+2] == 0x2e) {
      stream = stream hx(base + 9, incl - 9)
    }
    i = base + incl
  }
  printf("\n== FRAG STREAM (%d hex chars) ==\n", length(stream))
  printf("%s\n", stream)
}
'
