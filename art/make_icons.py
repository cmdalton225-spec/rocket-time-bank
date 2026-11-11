import sys, os
from PIL import Image, ImageDraw
src, res, art = sys.argv[1], sys.argv[2], sys.argv[3]
NAVY = (0, 50, 120)
im = Image.open(src).convert("RGBA")
W, H = im.size
# 1) full icon with the white outside-corners made transparent (flood fill from each corner)
full = im.copy()
for c in [(0, 0), (W-1, 0), (0, H-1), (W-1, H-1)]:
    ImageDraw.floodfill(full, c, (255, 255, 255, 0), thresh=40)
os.makedirs(art, exist_ok=True)
full.save(os.path.join(art, "icon-1600.png"))
# 2) navy keyed out -> transparent, with simple alpha matting on the antialiased edges
px = im.load()
keyed = Image.new("RGBA", (W, H))
kp = keyed.load()
T = 70.0
for y in range(H):
    for x in range(W):
        r, g, b, a = px[x, y]
        d = ((r-NAVY[0])**2 + (g-NAVY[1])**2 + (b-NAVY[2])**2) ** 0.5
        al = min(1.0, d / T)
        if al <= 0.15:
            kp[x, y] = (0, 0, 0, 0); continue
        fr = [max(0, min(255, int(round((c - (1-al)*n) / al)))) for c, n in zip((r, g, b), NAVY)]
        kp[x, y] = (fr[0], fr[1], fr[2], int(round(al*255)))
from PIL import ImageFilter
fullmask = full.split()[3].point(lambda v: 255 if v > 250 else 0).filter(ImageFilter.MinFilter(15))
keyed.putalpha(Image.composite(keyed.split()[3], Image.new("L", (W, H), 0), fullmask))
keyed.save(os.path.join(art, "keyed-1600.png"))
# rocket only (crop away the stars): bbox of keyed alpha inside the rocket's columns
rocket = keyed.crop((330, 100, 1270, 1520))
bb = rocket.getbbox(); rocket = rocket.crop(bb)
rocket.save(os.path.join(art, "rocket-only.png"))

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True); img.save(path, optimize=True)

dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# adaptive foreground: original artwork fills the 72dp visible area of the 108dp layer
canvas = Image.new("RGBA", (2400, 2400), (0, 0, 0, 0)); canvas.paste(keyed, (400, 400), keyed)
mono = Image.new("RGBA", (2400, 2400), (0, 0, 0, 0))
mono.putalpha(canvas.split()[3]); mono = Image.merge("RGBA", (*[Image.new("L", (2400, 2400), 255)]*3, canvas.split()[3]))
# round legacy icon: navy disc + artwork scaled into it
disc = Image.new("RGBA", (1600, 1600), (0, 0, 0, 0)); ImageDraw.Draw(disc).ellipse((0, 0, 1599, 1599), fill=NAVY + (255,))
k2 = keyed.resize((1360, 1360), Image.LANCZOS); disc.alpha_composite(k2, (120, 120))
cm = Image.new("L", (1600, 1600), 0); ImageDraw.Draw(cm).ellipse((0, 0, 1599, 1599), fill=255)
disc.putalpha(Image.composite(disc.split()[3], Image.new("L", (1600, 1600), 0), cm))
for d, s in dens.items():
    save(full.resize((int(48*s),)*2, Image.LANCZOS), f"{res}/mipmap-{d}/ic_launcher.png")
    save(disc.resize((int(48*s),)*2, Image.LANCZOS), f"{res}/mipmap-{d}/ic_launcher_round.png")
    save(canvas.resize((int(108*s),)*2, Image.LANCZOS), f"{res}/mipmap-{d}/ic_launcher_foreground.png")
    save(mono.resize((int(108*s),)*2, Image.LANCZOS), f"{res}/mipmap-{d}/ic_launcher_monochrome.png")
# in-app / widget rocket picture
rw, rh = rocket.size; sc = 600 / rh
save(rocket.resize((int(rw*sc), 600), Image.LANCZOS), f"{res}/drawable-nodpi/rocket.png")
save(full.resize((512, 512), Image.LANCZOS), f"{art}/ic_launcher-512.png")
print("ok", rocket.size)
