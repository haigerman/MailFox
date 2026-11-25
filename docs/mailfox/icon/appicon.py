# Builds the MailFox app icon from the design image: the metal plate with the fox emblem is placed on a matching,
# slightly larger brushed-metal background so that launcher masks don't clip the emblem.
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

src, out_dir = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")

# Square part of the plate around the emblem (source pixels)
CROP = (270, 190, 632, 552)
crop = im.crop(CROP)
side = CROP[2] - CROP[0]

CANVAS = 545
offset = (CANVAS - side) // 2

# Background: continue the plate's edge colors outwards (smoothly), plus a brushed-metal texture
crop_array = np.asarray(crop).astype(float)
padded = np.pad(crop_array, ((offset, CANVAS - side - offset), (offset, CANVAS - side - offset), (0, 0)), mode="edge")
smooth = np.asarray(Image.fromarray(padded.astype(np.uint8)).filter(ImageFilter.GaussianBlur(24))).astype(float)

rng = np.random.default_rng(7)
noise = Image.fromarray((rng.normal(128, 40, (CANVAS, CANVAS))).clip(0, 255).astype(np.uint8))
noise = noise.resize((CANVAS // 8, CANVAS), Image.BILINEAR).resize((CANVAS, CANVAS), Image.BICUBIC)
streaks = (np.asarray(noise).astype(float) - 128) / 128 * 3
background = np.clip(smooth + streaks[..., None], 0, 255).astype(np.uint8)
canvas = Image.fromarray(background, "RGB")

# Paste the plate with feathered edges
FEATHER = 60
mask = Image.new("L", (side, side), 0)
ImageDraw.Draw(mask).rectangle((FEATHER // 2, FEATHER // 2, side - FEATHER // 2, side - FEATHER // 2), fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(FEATHER / 3))
canvas.paste(crop, (offset, offset), mask)
canvas.save(f"{out_dir}/appicon_canvas.png")

# Adaptive icon background (108dp) per density
for density, size in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    canvas.resize((size, size), Image.LANCZOS).save(f"{out_dir}/ic_launcher_mailfox_background-{density}.png", optimize=True)

# Logo (72dp): visible center of the adaptive icon with rounded corners, like the design's tile
visible = canvas.crop((CANVAS // 6, CANVAS // 6, CANVAS - CANVAS // 6, CANVAS - CANVAS // 6))
for density, size in {"mdpi": 72, "hdpi": 108, "xhdpi": 144, "xxhdpi": 216, "xxxhdpi": 288}.items():
    scale = 4
    logo = visible.resize((size, size), Image.LANCZOS).convert("RGBA")
    rounded = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, size * scale - 1, size * scale - 1), radius=int(size * scale * 0.2), fill=255)
    logo.putalpha(rounded.resize((size, size), Image.LANCZOS))
    logo.save(f"{out_dir}/ic_mailfox_logo-{density}.png", optimize=True)
print("done")
