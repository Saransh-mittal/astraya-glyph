"""Draws the toy's slides as a PNG, reading the art from Slides.kt.

    python3 -I tools/preview.py            -> preview/slides.png

Mirrors Slides.kt's drawing rules (centring, degree mark, brightness levels);
change both together. No dependencies.
"""
import re
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = (ROOT / "app/src/main/java/com/astraya/glyph/Slides.kt").read_text()
N = 13
LEVEL = {"d": 60, "B": 160, "S": 255}


def art(name):
    m = re.search(r"val %s = \"\"\"(.*?)\"\"\"" % name, SRC, re.S)
    return [ln.strip() for ln in m.group(1).strip().splitlines()]


FONT = {
    ch: [int(b, 2) for b in re.findall(r"0b([01]{3})", body)]
    for ch, body in re.findall(r"'(.)' to intArrayOf\(([^)]*)\)", SRC)
}


def blank():
    return [[0] * N for _ in range(N)]


def put(f, x, y, v):
    if 0 <= x < N and 0 <= y < N:
        f[y][x] = v


def stamp(f, rows, x, y):
    for r, line in enumerate(rows):
        for c, ch in enumerate(line):
            if ch in LEVEL:
                put(f, x + c, y + r, LEVEL[ch])


def text(f, s, y, reserve=0):
    left = (N - (len(s) * 4 - 1 + reserve)) // 2
    for i, ch in enumerate(s):
        for r in range(5):
            for c in range(3):
                if FONT[ch][r] >> (2 - c) & 1:
                    put(f, left + i * 4 + c, y + r, 255)
    return left


def emblem():
    f = blank()
    stamp(f, art("EMBLEM"), 0, 0)
    return f


def time(h, m):
    f = blank()
    text(f, str(h), 1)
    text(f, "%02d" % m, 7)
    return f


def weather(t, icon):
    f = blank()
    stamp(f, art(icon), 3, 1)
    s = str(t)
    left = text(f, s, 7, reserve=2)
    put(f, left + len(s) * 4, 7, 160)
    return f


SLIDES = [
    ("emblem", emblem()),
    ("time 9:41", time(9, 41)),
    ("weather 28 sun", weather(28, "SUN")),
    ("18 rain", weather(18, "RAIN")),
    ("22 night", weather(22, "MOON")),
    ("31 cloud", weather(31, "CLOUD")),
    ("-4 snow", weather(-4, "SNOW")),
    ("26 storm", weather(26, "STORM")),
]


def png(path, w, h, pix):
    raw = b"".join(b"\x00" + bytes(pix[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    cell, gap = 22, 40
    tile = N * cell
    cols = 4
    rows = (len(SLIDES) + cols - 1) // cols
    W, H = cols * tile + (cols + 1) * gap, rows * tile + (rows + 1) * gap
    pix = bytearray([18, 18, 20] * W * H)
    for i, (_, f) in enumerate(SLIDES):
        ox = gap + (i % cols) * (tile + gap)
        oy = gap + (i // cols) * (tile + gap)
        for r in range(N):
            for c in range(N):
                inside = (r - 6) ** 2 + (c - 6) ** 2 <= 43.6
                v = f[r][c]
                col = (28, 28, 30) if v == 0 else (v, v, v)
                if not inside:
                    col = (18, 18, 20) if v == 0 else (255, 0, 0)  # red = outside the safe circle
                cx, cy = ox + c * cell + cell // 2, oy + r * cell + cell // 2
                rad = cell * 0.38
                for y in range(int(cy - rad), int(cy + rad) + 1):
                    for x in range(int(cx - rad), int(cx + rad) + 1):
                        if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad:
                            k = (y * W + x) * 3
                            pix[k:k + 3] = bytes(col)
    out = ROOT / "preview"
    out.mkdir(exist_ok=True)
    png(out / "slides.png", W, H, pix)
    for name, f in SLIDES[:3]:
        print(name)
        for r in range(N):
            print(" ".join("·" if v == 0 else ("◆" if v == 255 else "█") for v in f[r]))
    print(out / "slides.png")


if __name__ == "__main__":
    sys.exit(main())
