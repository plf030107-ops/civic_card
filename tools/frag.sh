#!/system/bin/sh
# btsnooz 分片重组：把 02dc2e... 的块按顺序拼回完整 ACL 包
# 结构: 02 (type) + dc2e(handle) + cXX(分片序号+标志) + 后续
F="$1"
od -An -v -tx1 "$F" | tr -s ' ' '\n' | grep -v '^$' | awk '
BEGIN { hex = "0123456789abcdef"; for (k = 1; k <= 16; k++) HV[substr(hex, k, 1)] = k - 1 }
{ b[++n] = HV[substr($1,1,1)] * 16 + HV[substr($1,2,1)] }
function be32(p) { return b[p]*16777216 + b[p+1]*65536 + b[p+2]*256 + b[p+3] }
function le16(p) { return b[p] + b[p+1]*256 }
function hx(p, len,   s, k) { s=""; for (k=0;k<len;k++) s = s sprintf("%02x", b[p+k]); return s }
END {
  i = 17; rec = 0
  while (i + 23 <= n) {
    incl = be32(i + 4)
    if (incl <= 0 || incl > 70000) break
    base = i + 24
    if (base + incl - 1 > n) break
    rec++
    # 找完整 ATT 包：02 + handle + dlen(LE) 且 cid=4
    if (b[base] == 2 && incl >= 9 && le16(base + 7) == 4) {
      dlen = le16(base + 3)
      op = b[base + 9]
      if (dlen >= 4) {
        printf("[r%d] op=%02x h=%04x dlen=%d avail=%d\n", rec, op, le16(base+10), dlen, incl-9)
        if (incl - 9 >= dlen) {
          # 完整包
          printf("      FULL: %s\n", hx(base + 9, dlen))
        }
      }
    }
    # 分片记录：02 dc2e XX ...  -> 输出以便人工拼
    if (b[base] == 2 && b[base+1] == 0xdc && b[base+2] == 0x2e) {
      printf("[r%d] FRAG %s\n", rec, hx(base + 3, incl - 3))
    }
    i = base + incl
  }
}
'
