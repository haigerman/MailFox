# Extracts the fox line art from the design image and traces it to a vector path.
import sys
from collections import deque
import numpy as np
from PIL import Image, ImageFilter
import potrace

src, out_dir = sys.argv[1], sys.argv[2]
S = 4
im = Image.open(src).convert("RGB").crop((820, 230, 1130, 530))
big = im.resize((310 * S, 300 * S), Image.LANCZOS)
a = np.asarray(big).astype(float)
g = a.mean(axis=2)
bl = np.asarray(Image.fromarray(g.astype(np.uint8)).filter(ImageFilter.GaussianBlur(28))).astype(float)
sat = a.max(axis=2) - a.min(axis=2)
m = ((g - bl) > 22) & (sat < 45)
H, W = m.shape
yy, xx = np.mgrid[0:H, 0:W]
m &= np.hypot(xx - 155 * S, yy - 150 * S) < 118 * S
m[(xx > 145 * S) & (xx < 165 * S) & (yy > 120 * S) & (yy < 172 * S)] = False
b = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MedianFilter(9))) > 127

lab = np.zeros(b.shape, int); n = 0; sizes = {}
for y in range(H):
    for x in np.nonzero(b[y] & (lab[y] == 0))[0]:
        if lab[y, x]:
            continue
        n += 1; q = deque([(y, x)]); lab[y, x] = n; c = 0
        while q:
            py, px = q.popleft(); c += 1
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = py + dy, px + dx
                if 0 <= ny < H and 0 <= nx < W and b[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n; q.append((ny, nx))
        sizes[n] = c
keep = [k for k, v in sizes.items() if v > 700 * S]
clean = np.isin(lab, keep)


def trace(mask, name, grow):
    img = Image.fromarray((mask * 255).astype(np.uint8))
    if grow:
        img = img.filter(ImageFilter.MaxFilter(grow))
    img = img.filter(ImageFilter.GaussianBlur(3)).point(lambda v: 255 if v > 127 else 0)
    img.save(f"{out_dir}/{name}.png")
    bm = potrace.Bitmap(np.asarray(img) <= 127)
    plist = bm.trace(turdsize=50, alphamax=1.0, opttolerance=0.4)
    parts = []
    for curve in plist:
        s = curve.start_point
        d = [f"M{s.x:.1f},{s.y:.1f}"]
        for seg in curve.segments:
            if seg.is_corner:
                d.append(f"L{seg.c.x:.1f},{seg.c.y:.1f}L{seg.end_point.x:.1f},{seg.end_point.y:.1f}")
            else:
                d.append(f"C{seg.c1.x:.1f},{seg.c1.y:.1f} {seg.c2.x:.1f},{seg.c2.y:.1f} {seg.end_point.x:.1f},{seg.end_point.y:.1f}")
        d.append("Z")
        parts.append("".join(d))
    ys, xs = np.nonzero(np.asarray(img) > 127)
    bbox = (xs.min(), ys.min(), xs.max(), ys.max())
    open(f"{out_dir}/{name}.path", "w").write(" ".join(parts))
    open(f"{out_dir}/{name}.bbox", "w").write(" ".join(map(str, bbox)))
    print(name, "paths", len(plist), "bbox", bbox)


trace(clean, "lineart_thin", 0)
trace(clean, "lineart_bold", 9)
