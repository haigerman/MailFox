# Converts the MailFox icon geometry (fox.py) into Android vector drawables.
import math, re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib.util
spec = importlib.util.spec_from_file_location("fox", "fox.py"); fox = importlib.util.module_from_spec(spec); spec.loader.exec_module(fox)

OUT = sys.argv[1]

def tokens(d):
    return re.findall(r"[MLQCZ]|-?\d*\.?\d+", d)

def transform_path(d, f):
    out, toks, i = [], tokens(d), 0
    while i < len(toks):
        t = toks[i]
        if t in "MLQC":
            n = {"M": 1, "L": 1, "Q": 2, "C": 3}[t]
            pts = []
            for k in range(n):
                x, y = float(toks[i + 1 + 2 * k]), float(toks[i + 2 + 2 * k])
                pts.append(f(x, y))
            out.append(t + " ".join(f"{x:.2f},{y:.2f}" for x, y in pts))
            i += 1 + 2 * n
            # implicit repeated L after M
            while i < len(toks) and toks[i] not in "MLQCZ":
                x, y = float(toks[i]), float(toks[i + 1])
                x, y = f(x, y)
                out.append(f"L{x:.2f},{y:.2f}")
                i += 2
        elif t == "Z":
            out.append("Z"); i += 1
        else:
            raise ValueError(t)
    return " ".join(out).replace(".00", "")

def fox_transform(scale, tx, ty):
    # matches SVG: translate(tx,ty) scale(s) translate(-54,-54)
    return lambda x, y: (tx + scale * (x - 54), ty + scale * (y - 54))

def leaf_transform(cx, cy, a):
    r = math.radians(a); c, s = math.cos(r), math.sin(r)
    return lambda x, y: (cx + x * c - y * s, cy + x * s + y * c)

def path_xml(d, color, indent="    "):
    return f'{indent}<path\n{indent}    android:fillColor="{color}"\n{indent}    android:pathData="{d}"\n{indent}    />\n'

def vector(body, w=108, h=108, vw=108, vh=108, comment=None, tint=None):
    head = '<?xml version="1.0" encoding="utf-8"?>\n'
    if comment:
        head += f"<!-- {comment} -->\n"
    tint_attr = f'\n    android:tint="{tint}"' if tint else ""
    return (head + f'<vector\n    xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}"{tint_attr}\n    >\n\n' + body + "</vector>\n")

FOX_SCALE, FOX_TX, FOX_TY = 0.79, 54, 51
foxf = fox_transform(FOX_SCALE, FOX_TX, FOX_TY)

def background_paths(indent="    "):
    b = path_xml("M0,0 L108,0 L108,108 L0,108 Z", fox.BG, indent)
    b += path_xml("M0,78 C30,66 70,70 108,62 L108,108 L0,108 Z", fox.BG2, indent)
    # glow circle r=29 at (54,51)
    b += path_xml("M25,51 A29,29 0 1 1 83,51 A29,29 0 1 1 25,51 Z", fox.GLOW, indent)
    for color, d, (cx, cy, a) in fox.leaves:
        b += path_xml(transform_path(d, leaf_transform(cx, cy, a)), color, indent)
    return b

def fox_paths(f, indent="    "):
    return "".join(path_xml(transform_path(d, f), color, indent) for color, d in fox.fox)

HEADER = "MailFox app icon: geometric fox head with autumn leaves. Source: docs/mailfox/icon (fox.py)."

# 1) adaptive icon layers
open(f"{OUT}/drawable/ic_launcher_background.xml", "w").write(vector(background_paths(), comment=HEADER))
open(f"{OUT}/drawable/ic_launcher_foreground.xml", "w").write(vector(fox_paths(foxf), comment=HEADER))

# 2) full round logo (legacy launcher icon before Android 8, in-app logo, shortcuts):
#    the 72dp visible part of the adaptive icon, clipped to a circle
inner = background_paths("        ") + fox_paths(foxf, "        ")
logo_body = ('    <group\n        android:translateX="-18"\n        android:translateY="-18"\n        >\n'
             '        <clip-path android:pathData="M18,54 A36,36 0 1 1 90,54 A36,36 0 1 1 18,54 Z" />\n\n'
             + inner + '    </group>\n')
open(f"{OUT}/drawable/ic_app_logo.xml", "w").write(vector(logo_body, w=72, h=72, vw=72, vh=72, comment=HEADER))

# 3) silhouette (monochrome launcher icon + notification icon)
EL, ER, FL, FR, T, OL, OR_, CL, CR, JL, JR, N = fox.EL, fox.ER, fox.FL, fox.FR, fox.T, fox.OL, fox.OR_, fox.CL, fox.CR, fox.JL, fox.JR, fox.N
outline = fox.poly([EL, FL, T, FR, ER, OR_, CR, JR, N, JL, CL, OL])
def scaled(pts, k):
    cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
    return [(cx + k * (x - cx), cy + k * (y - cy)) for x, y in pts]
holes = [
    fox.poly(scaled([(34, 30), (42, 40), (31, 45)], 0.6)),
    fox.poly(scaled([(74, 30), (66, 40), (77, 45)], 0.6)),
    fox.poly(scaled([(39, 52), (47, 54.5), (41.5, 56.5)], 1.5)),
    fox.poly(scaled([(69, 52), (61, 54.5), (66.5, 56.5)], 1.5)),
]
def silhouette(f):
    return " ".join(transform_path(d, f) for d in [outline] + holes)

def path_evenodd(d, color, indent="    "):
    return (f'{indent}<path\n{indent}    android:fillColor="{color}"\n{indent}    android:fillType="evenOdd"\n'
            f'{indent}    android:pathData="{d}"\n{indent}    />\n')

open(f"{OUT}/drawable/ic_app_logo_monochrome.xml", "w").write(
    vector(path_evenodd(silhouette(fox_transform(0.7, 54, 54)), "#000000"), comment="MailFox monochrome (themed) launcher icon"))

# notification icon: 24dp, fox head bounding box x 22..86, y 25..85 -> fit into 20x20 centered
s = 20 / 64.0
nf = lambda x, y: (12 + s * (x - 54), 12 + s * (y - 55))
open(f"{OUT}/drawable/ic_mailfox_notification.xml", "w").write(
    vector(path_evenodd(silhouette(nf), "#FFFFFFFF"), w=24, h=24, vw=24, vh=24, comment="MailFox status bar icon: fox head silhouette"))
print("done")
