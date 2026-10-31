#!/system/bin/sh
# btsnoop -> ATT 通知帧解析
# 关键: btsnoop 记录头/FD 用大端; L2CAP 长度与 CID 用小端
F="$1"
MAX="${2:-30}"
echo "file=$F size=$(wc -c < "$F")"

od -An -v -tx1 "$F" | tr -s ' ' '\n' | grep -v '^$' | awk -v max="$MAX" '
BEGIN { hex = "0123456789abcdef"; for (k = 1; k <= 16; k++) HV[substr(hex, k, 1)] = k - 1 }
{ b[++n] = HV[substr($1,1,1)] * 16 + HV[substr($1,2,1)] }
function be16(p) { return b[p] * 256 + b[p+1] }
function be32(p) { return b[p] * 16777216 + b[p+1] * 65536 + b[p+2] * 256 + b[p+3] }
function le16(p) { return b[p] + b[p+1] * 256 }
END {
  printf("total bytes=%d\n", n)
  i = 17; rec = 0; att = 0; hits = 0; printed = 0
  while (i + 23 <= n) {
    incl = be32(i + 4); flags = be32(i + 8)
    if (incl <= 0 || incl > 70000) break
    base = i + 24
    if (base + incl - 1 > n) break
    rec++
    dir = (flags % 2 == 1) ? "RX" : "TX"
    if (b[base] == 2 && base + 7 <= n) {
      # ACL: handle(2) dlen(2) | L2CAP: len(2,LE) cid(2,LE)
      cid = le16(base + 7)
      if (cid == 4) {
        ap = base + 9
        op = b[ap]
        att++
        if (op == 27) {
          hits++
          if (printed < max) {
            hl = le16(ap + 1)
            vlen = le16(base + 5) - 3
            if (vlen < 0) vlen = 0
            s = ""
            for (k = 0; k < vlen && k < 64; k++) s = s sprintf("%02x", b[ap + 3 + k])
            printf("[%3d] %s h=%04x len=%d %s\n", printed, dir, hl, vlen, s)
            printed++
          }
        }
      }
    }
    i = base + incl
  }
  printf("records=%d att=%d notif=%d\n", rec, att, hits)
}
'
