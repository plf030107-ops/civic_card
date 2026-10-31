#!/system/bin/sh
# btsnoop -> GATT 帧（修正版）
# 记录头 24B 全大端: orig incl flags drops ts
# HCI ACL type=02: handle(2,LE) dlen(2,LE) | L2CAP len(2,LE) cid(2,LE) | ATT
F="$1"
MAX="${2:-80}"
echo "=== $F ==="

od -An -v -tx1 "$F" | tr -s ' ' '\n' | grep -v '^$' | awk -v max="$MAX" '
BEGIN { hex = "0123456789abcdef"; for (k = 1; k <= 16; k++) HV[substr(hex, k, 1)] = k - 1 }
{ b[++n] = HV[substr($1,1,1)] * 16 + HV[substr($1,2,1)] }
function be32(p) { return b[p]*16777216 + b[p+1]*65536 + b[p+2]*256 + b[p+3] }
function le16(p) { return b[p] + b[p+1]*256 }
END {
  i = 17; rec = 0; shown = 0; att = 0
  while (i + 23 <= n) {
    incl = be32(i + 4); flags = be32(i + 8)
    if (incl <= 0 || incl > 70000) break
    base = i + 24
    if (base + incl - 1 > n) break
    rec++
    dir = (flags % 2 == 1) ? "RX" : "TX"
    if (b[base] == 2 && incl >= 9) {
      dlen = le16(base + 3)
      cid  = le16(base + 7)
      if (cid == 4 && dlen >= 3) {
        ap = base + 9
        op = b[ap]
        att++
        hl = le16(ap + 1)
        vlen = dlen - 3
        if (op == 0x52 || op == 0x1b || op == 0x12 || op == 0x1d) {
          if (vlen > 0 && shown < max) {
            s = ""
            for (k = 0; k < vlen && k < 40; k++) {
              if (ap + 3 + k <= n) s = s sprintf("%02x", b[ap + 3 + k])
            }
            lbl = (op == 0x52) ? "WRITE" : ((op == 0x1b) ? "NOTIFY" : ((op == 0x1d) ? "INDIC" : "WREQ"))
            printf("[%4d] %s %-9s h=%04x len=%2d %s\n", rec, dir, lbl, hl, vlen, s)
            shown++
          }
        }
      }
    }
    i = base + incl
  }
  printf("records=%d att=%d shown=%d\n", rec, att, shown)
}
'
