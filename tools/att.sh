#!/system/bin/sh
# 输出所有 L2CAP cid=4 (ATT) 的记录
F="$1"
od -An -v -tx1 "$F" | tr -s ' ' '\n' | grep -v '^$' | awk '
BEGIN { hex = "0123456789abcdef"; for (k = 1; k <= 16; k++) HV[substr(hex, k, 1)] = k - 1 }
{ b[++n] = HV[substr($1,1,1)] * 16 + HV[substr($1,2,1)] }
function be32(p) { return b[p]*16777216 + b[p+1]*65536 + b[p+2]*256 + b[p+3] }
function le16(p) { return b[p] + b[p+1]*256 }
END {
  i = 17; c = 0
  while (i + 23 <= n) {
    incl = be32(i + 4)
    if (incl <= 0 || incl > 70000) break
    base = i + 24
    if (base + incl - 1 > n) break
    if (b[base] == 2 && incl >= 9 && le16(base + 7) == 4) {
      c++
      dlen = le16(base + 3)
      s = ""
      lim = dlen + 9
      if (lim > incl) lim = incl
      for (k = 0; k < lim && k < 50; k++) s = s sprintf("%02x", b[base + k])
      printf("cid4 #%d incl=%d dlen=%d %s\n", c, incl, dlen, s)
    }
    i = base + incl
  }
  printf("total cid4 = %d\n", c)
}
'
