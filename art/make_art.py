"""Erzeugt App-Icon und TV-Banner für Antenne.

Aufruf: python3 art/make_art.py <pfad-zu-inter-bold>
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
BG_TOP = (28, 30, 44, 255)
BG_BOTTOM = (8, 9, 14, 255)
C1 = (90, 200, 250)   # Himmelblau
C2 = (175, 82, 222)   # Violett


def lerp(a, b, t):
    return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))


def gradient(w, h, a, b, diagonal=True):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / max(1, w - 1)) + (y / max(1, h - 1))) / 2 if diagonal else y / max(1, h - 1)
            px[x, y] = lerp(a, b, t) + (255,)
    return img


def mark(size):
    """Das Antenne-Zeichen: ein Punkt mit zwei Funkbögen, wie ein Signal."""
    s = size * 4
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    cx, cy = s * 0.5, s * 0.5
    r = s * 0.085
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    w = int(s * 0.07)
    for rad in (s * 0.2, s * 0.34):
      for st, en in ((140, 220), (-40, 40)):
        d.arc([cx - rad, cy - rad, cx + rad, cy + rad], start=st, end=en, fill=255, width=w)
        # abgerundete Enden
        for ang in (st, en):
            a = math.radians(ang)
            ex = cx + (rad - w / 2) * math.cos(a)
            ey = cy + (rad - w / 2) * math.sin(a)
            d.ellipse([ex - w / 2, ey - w / 2, ex + w / 2, ey + w / 2], fill=255)
    grad = gradient(s, s, C1, C2)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def icon(path, size):
    big = size * 4
    bg = gradient(big, big, BG_TOP, BG_BOTTOM, diagonal=False)
    m = Image.new("L", (big, big), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.22, fill=255)
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    img.paste(bg, (0, 0), m)
    img = img.resize((size, size), Image.LANCZOS)
    mk = mark(int(size * 0.78))
    off = (size - mk.width) // 2
    img.alpha_composite(mk, (off, off - int(size * 0.04)))
    img.save(path)


def banner(path, font_path):
    w, h = 320, 180
    img = gradient(w, h, BG_TOP, BG_BOTTOM, diagonal=False)
    mk = mark(84)
    font = ImageFont.truetype(font_path, 44)
    text = "Antenne"
    tw = ImageDraw.Draw(img).textlength(text, font=font)
    total = 84 + 6 + tw
    x = int((w - total) / 2)
    img.alpha_composite(mk, (x, (h - 84) // 2 - 6))
    ImageDraw.Draw(img).text((x + 90, h / 2), text, font=font, fill=(255, 255, 255), anchor="lm")
    img.save(path)


if __name__ == "__main__":
    font = sys.argv[1]
    os.makedirs(os.path.join(ROOT, "mipmap-xhdpi"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "drawable-xhdpi"), exist_ok=True)
    icon(os.path.join(ROOT, "mipmap-xhdpi", "ic_launcher.png"), 96)
    banner(os.path.join(ROOT, "drawable-xhdpi", "banner.png"), font)
    print("Bilder erstellt")
