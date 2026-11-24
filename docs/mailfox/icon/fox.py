# Generates the MailFox icon geometry as SVG (108x108 adaptive icon canvas).
import json, sys

# ---- palette
BG = "#2E4A2F"        # forest green
BG2 = "#365738"       # lighter forest (hill)
GLOW = "#3D6340"      # soft glow behind the fox
LEAF_GOLD = "#E9B03B"
LEAF_RED = "#B8452A"
LEAF_ORANGE = "#E07A2E"
FOX_L = "#EF7B2D"     # fox orange, lit side
FOX_R = "#D3611C"     # fox orange, shaded side
FOX_DARK = "#A9481A"  # forehead facet
EAR_IN = "#4A2614"
WHITE_L = "#FFF8EF"
WHITE_R = "#F0E2D2"
DARK = "#2A170E"

def poly(pts):
    return "M" + " L".join(f"{x:g},{y:g}" for x, y in pts) + " Z"

# Fox head, designed on the 108 canvas around center (54, 54)
EL, ER = (32, 25), (76, 25)
FL, FR = (44, 39), (64, 39)
T = (54, 36)
OL, OR_ = (27, 46), (81, 46)
CL, CR = (22, 61), (86, 61)
JL, JR = (39, 69), (69, 69)
N = (54, 85)
ML, MR = (44, 60), (64, 60)   # muzzle top corners
MC = (54, 66)                 # muzzle top center

fox = []
# orange halves
fox.append((FOX_L, poly([EL, FL, T, (54, 85), JL, CL, OL])))
fox.append((FOX_R, poly([ER, FR, T, (54, 85), JR, CR, OR_])))
# forehead facet (darker diamond)
fox.append((FOX_DARK, poly([T, (59, 44), (54, 56), (49, 44)])))
# inner ears
fox.append((EAR_IN, poly([(34, 30), (42, 40), (31, 45)])))
fox.append((EAR_IN, poly([(74, 30), (66, 40), (77, 45)])))
# white cheeks / muzzle (envelope-flap like V)
fox.append((WHITE_L, poly([CL, ML, MC, N, JL])))
fox.append((WHITE_R, poly([CR, MR, MC, N, JR])))
# eyes
fox.append((DARK, poly([(39, 52), (47, 54.5), (41.5, 56.5)])))
fox.append((DARK, poly([(69, 52), (61, 54.5), (66.5, 56.5)])))
# nose
fox.append((DARK, "M49.5,79 L58.5,79 Q59.5,79 59,80 L55,85 Q54,86 53,85 L49,80 Q48.5,79 49.5,79 Z"))

MAPLE_R = [(0,-10),(1.8,-5.6),(4.6,-7.2),(4,-3),(8.6,-4.6),(7.4,-1.4),(10,0.2),(5.8,2.6),(6.8,5.2),(2.4,3.8),(0.7,6.2),(0.5,10.5)]
def maple(size):
    k = size / 21.0
    right = [(x*k, y*k) for x, y in MAPLE_R]
    left = [(-x, y) for x, y in reversed(right[1:])]
    return poly(right + left)
def leaf(cx, cy, size, angle, color):
    return (color, maple(size), (cx, cy, angle))

leaves = [
    leaf(29, 80, 13, -30, LEAF_GOLD),
    leaf(41, 86, 9, 20, LEAF_RED),
    leaf(80, 79, 12, 35, LEAF_ORANGE),
    leaf(68, 87, 8, -15, LEAF_GOLD),
    leaf(83, 36, 8, 25, LEAF_GOLD),
    leaf(24, 42, 7, -35, LEAF_RED),
]

def svg(include_bg=True, include_fox=True, scale=0.79, mask=None):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">']
    if mask == "circle":
        out.append('<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath><g clip-path="url(#m)">')
    elif mask == "squircle":
        out.append('<clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="22"/></clipPath><g clip-path="url(#m)">')
    else:
        out.append('<g>')
    if include_bg:
        out.append(f'<rect width="108" height="108" fill="{BG}"/>')
        out.append(f'<path d="M0,78 C30,66 70,70 108,62 L108,108 L0,108 Z" fill="{BG2}"/>')
        out.append(f'<circle cx="54" cy="51" r="29" fill="{GLOW}"/>')
        for color, d, (cx, cy, a) in leaves:
            out.append(f'<path d="{d}" fill="{color}" transform="translate({cx},{cy}) rotate({a})"/>')
    if include_fox:
        out.append(f'<g transform="translate(54,51) scale({scale}) translate(-54,-54)">')
        for color, d in fox:
            out.append(f'<path d="{d}" fill="{color}"/>')
        out.append('</g>')
    out.append('</g>')
    out.append('<circle cx="54" cy="54" r="33" fill="none" stroke="#ff00ff" stroke-width="0.3" opacity="0.6"/>' if mask is None else '')
    out.append('</svg>')
    return "\n".join(out)

open("full.svg", "w").write(svg())
open("circle.svg", "w").write(svg(mask="circle"))
open("squircle.svg", "w").write(svg(mask="squircle"))
json.dump({"fox": fox, "leaves": leaves}, open("geometry.json", "w"))
