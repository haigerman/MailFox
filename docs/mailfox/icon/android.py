# Generates the MailFox icons (variant C: fox head with envelope badge on a dark green gradient)
# as Android vector drawables. Fox geometry: fox.py. Usage: python3 android.py <app-k9mail/src/main/res> <bolt logo file>
import importlib.util, math, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("fox", os.path.join(HERE, "fox.py"))
fox = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fox)

RES, BOLT_LOGO = sys.argv[1], sys.argv[2]

# ---- design (108 x 108 adaptive icon canvas)
BG_INNER, BG_OUTER, BG_FLAT = "#2B5637", "#0D2015", "#1B3A26"
BG_CENTER, BG_RADIUS = (54, 41), 70
FOX_SCALE, FOX_CX, FOX_CY = 0.72, 51, 49
BADGE_CX, BADGE_CY, BADGE_R, BADGE_RING = 71, 70, 11.5, 1.5
BADGE_COLOR, RING_COLOR = "#E07A2E", "#0E2216"
ENV_W, ENV_H = 15, 10
ENV_BODY, ENV_FLAP, ENV_LINE = "#FFFFFF", "#FFF4E6", "#B05A1E"


def tokens(d):
    return re.findall(r"[MLQCAZ]|-?\d*\.?\d+", d)


def transform_path(d, f):
    out, t, i = [], tokens(d), 0
    while i < len(t):
        c = t[i]
        if c in "MLQC":
            n = {"M": 1, "L": 1, "Q": 2, "C": 3}[c]
            pts = [f(float(t[i + 1 + 2 * k]), float(t[i + 2 + 2 * k])) for k in range(n)]
            out.append(c + " ".join(f"{x:.2f},{y:.2f}" for x, y in pts))
            i += 1 + 2 * n
            while i < len(t) and t[i] not in "MLQCAZ":
                x, y = f(float(t[i]), float(t[i + 1]))
                out.append(f"L{x:.2f},{y:.2f}")
                i += 2
        elif c == "Z":
            out.append("Z")
            i += 1
        else:
            raise ValueError(c)
    return " ".join(out)


def fox_transform(s, cx, cy):
    return lambda x, y: (cx + s * (x - 54), cy + s * (y - 55))


def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1 1 {cx + r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1 1 {cx - r:.2f},{cy:.2f} Z"


def rect(x, y, w, h):
    return f"M{x:.2f},{y:.2f} L{x + w:.2f},{y:.2f} L{x + w:.2f},{y + h:.2f} L{x:.2f},{y + h:.2f} Z"


def path(d, fill=None, stroke=None, width=None, evenodd=False, alpha=None, indent="    "):
    a = []
    if fill:
        a.append(f'android:fillColor="{fill}"')
    if evenodd:
        a.append('android:fillType="evenOdd"')
    if stroke:
        a += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{width}"', 'android:strokeLineJoin="round"']
    if alpha is not None:
        a.append(f'android:strokeAlpha="{alpha}"')
    a.append(f'android:pathData="{d}"')
    body = "".join(f"\n{indent}    {x}" for x in a)
    return f"{indent}<path{body}\n{indent}    />\n"


def gradient_bg(indent="    "):
    i = indent
    return (f'{i}<path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">\n'
            f'{i}    <aapt:attr name="android:fillColor">\n'
            f'{i}        <gradient\n{i}            android:type="radial"\n'
            f'{i}            android:centerX="{BG_CENTER[0]}"\n{i}            android:centerY="{BG_CENTER[1]}"\n'
            f'{i}            android:gradientRadius="{BG_RADIUS}"\n'
            f'{i}            android:startColor="#FF{BG_INNER[1:]}"\n{i}            android:endColor="#FF{BG_OUTER[1:]}"\n'
            f'{i}            />\n{i}    </aapt:attr>\n{i}</path>\n')


def flat_bg(indent="    "):
    return (path("M0,0 L108,0 L108,108 L0,108 Z", fill=BG_FLAT, indent=indent)
            + path(circle(BG_CENTER[0], BG_CENTER[1], 30), fill="#24492F", indent=indent))


def fox_paths(indent="    "):
    f = fox_transform(FOX_SCALE, FOX_CX, FOX_CY)
    return "".join(path(transform_path(d, f), fill=c, indent=indent) for c, d in fox.fox)


def badge_paths(indent="    "):
    x, y = BADGE_CX - ENV_W / 2, BADGE_CY - ENV_H / 2
    cx = BADGE_CX
    s = path(circle(BADGE_CX, BADGE_CY, BADGE_R + BADGE_RING), fill=RING_COLOR, indent=indent)
    s += path(circle(BADGE_CX, BADGE_CY, BADGE_R), fill=BADGE_COLOR, indent=indent)
    s += path(rect(x, y, ENV_W, ENV_H), fill=ENV_BODY, indent=indent)
    s += path(f"M{x:.2f},{y + ENV_H:.2f} L{cx:.2f},{y + ENV_H * 0.48:.2f} L{x + ENV_W:.2f},{y + ENV_H:.2f}",
              stroke=ENV_LINE, width=0.7, alpha=0.55, indent=indent)
    s += path(f"M{x + 0.5:.2f},{y + 0.5:.2f} L{cx:.2f},{y + ENV_H * 0.58:.2f} L{x + ENV_W - 0.5:.2f},{y + 0.5:.2f} Z",
              fill=ENV_FLAP, stroke=ENV_LINE, width=0.9, indent=indent)
    return s


def vector(body, size=108, viewport=108, comment=None, aapt=False, tint=None):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    head = '<?xml version="1.0" encoding="utf-8"?>\n' + (f"<!-- {comment} -->\n" if comment else "")
    return (head + f'<vector\n    xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}"\n    >\n\n' + body + "</vector>\n")


def write(rel, text):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w").write(text)


NOTE = "MailFox app icon (fox head with envelope badge). Generated by docs/mailfox/icon/android.py"

# adaptive icon layers (adaptive icons need Android 8+, so the gradient is fine there)
write("drawable-v26/ic_launcher_background.xml", vector(gradient_bg(), comment=NOTE, aapt=True))
write("drawable/ic_launcher_foreground.xml", vector(fox_paths() + badge_paths(), comment=NOTE))


def logo(bg):
    clip = "        <clip-path android:pathData=\"" + circle(54, 54, 36) + "\" />\n\n"
    return ('    <group\n        android:translateX="-18"\n        android:translateY="-18"\n        >\n' + clip
            + bg("        ") + fox_paths("        ") + badge_paths("        ") + "    </group>\n")


# round logo: launcher icon before Android 8, in-app logo, shortcuts
write("drawable/ic_app_logo.xml", vector(logo(flat_bg), 72, 72, comment=NOTE))
write("drawable-v24/ic_app_logo.xml", vector(logo(gradient_bg), 72, 72, comment=NOTE, aapt=True))
open(BOLT_LOGO, "w").write("<!-- MailFox: copy of the fox logo (app-k9mail/src/main/res/drawable/ic_app_logo.xml) -->\n"
                           + vector(logo(flat_bg), 72, 72).split("\n", 1)[1])

# silhouette: fox (eye and inner ear cut-outs) + badge with envelope cut-out
EL, ER, FL, FR, T, OL, OR_, CL, CR, JL, JR, N = (fox.EL, fox.ER, fox.FL, fox.FR, fox.T, fox.OL, fox.OR_, fox.CL,
                                                 fox.CR, fox.JL, fox.JR, fox.N)


def scaled(pts, k):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    return [(cx + k * (x - cx), cy + k * (y - cy)) for x, y in pts]


FOX_OUTLINE = fox.poly([EL, FL, T, FR, ER, OR_, CR, JR, N, JL, CL, OL])
FOX_HOLES = [
    fox.poly(scaled([(34, 30), (42, 40), (31, 45)], 0.6)),
    fox.poly(scaled([(74, 30), (66, 40), (77, 45)], 0.6)),
    fox.poly(scaled([(39, 52), (47, 54.5), (41.5, 56.5)], 1.5)),
    fox.poly(scaled([(69, 52), (61, 54.5), (66.5, 56.5)], 1.5)),
]


def silhouette(g):
    """g maps 108-canvas coordinates to the target viewport."""
    f = fox_transform(FOX_SCALE, FOX_CX, FOX_CY)
    fox_d = " ".join(transform_path(d, lambda x, y: g(*f(x, y))) for d in [FOX_OUTLINE] + FOX_HOLES)
    r = BADGE_R + 0.6
    ew, eh = ENV_W * 1.05, ENV_H * 1.1
    x, y = BADGE_CX - ew / 2, BADGE_CY - eh / 2
    t = 1.6  # width of the flap line
    flap = [(x + 1.2, y), (BADGE_CX, y + eh * 0.55), (x + ew - 1.2, y), (x + ew - 1.2 + t, y),
            (BADGE_CX, y + eh * 0.55 + t * 1.25), (x + 1.2 - t, y)]
    badge = [circle(BADGE_CX, BADGE_CY, r), rect(x, y, ew, eh), fox.poly(flap)]
    badge_d = " ".join(transform_path(d, g) if "A" not in d else
                       _circle_t(g, BADGE_CX, BADGE_CY, r) for d in badge)
    return fox_d, badge_d


def _circle_t(g, cx, cy, r):
    (gx, gy), (gx2, _) = g(cx, cy), g(cx + r, cy)
    return circle(gx, gy, gx2 - gx)


def silhouette_vector(g, size, viewport, color, comment):
    fox_d, badge_d = silhouette(g)
    body = path(fox_d, fill=color, evenodd=True) + path(badge_d, fill=color, evenodd=True)
    return vector(body, size, viewport, comment=comment)


# monochrome (themed) launcher icon
write("drawable/ic_app_logo_monochrome.xml",
      silhouette_vector(lambda x, y: (54 + 0.9 * (x - 55), 54 + 0.9 * (y - 54.5)), 108, 108, "#000000",
                        "MailFox monochrome (themed) launcher icon"))
# status bar icon: content box x 28..83, y 27..82 -> 22dp
k = 22 / 55.0
write("drawable/ic_mailfox_notification.xml",
      silhouette_vector(lambda x, y: (12 + k * (x - 55.5), 12 + k * (y - 54.5)), 24, 24, "#FFFFFFFF",
                        "MailFox status bar icon: fox head with envelope badge"))
print("done")
