"""Neutral flanner icon: mid-tone slate-indigo, ivory f, red 'now' bar. Works on light and dark launchers."""
import math
from PIL import Image, ImageDraw, ImageFilter

BG_TOP, BG_BOT = (104, 118, 164), (62, 76, 124)
IVORY, RED = (248, 246, 240), (232, 66, 58)
DX, DY = -16, 26
STEM = [('line', 470 + DX, 790 + DY, 470 + DX, 340 + DY), ('arc', 620 + DX, 340 + DY, 150, 180, 270), ('line', 620 + DX, 190 + DY, 722 + DX, 190 + DY)]
BAR = [('line', 340 + DX, 490 + DY, 660 + DX, 490 + DY)]


def _mask(parts, w, S, sc):
    k = S / 1024
    T = lambda v: 512 + (v - 512) * sc
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    W = w * sc * k

    def cap(x, y):
        d.ellipse((T(x) * k - W / 2, T(y) * k - W / 2, T(x) * k + W / 2, T(y) * k + W / 2), fill=255)

    for p in parts:
        if p[0] == 'line':
            _, x0, y0, x1, y1 = p
            d.line((T(x0) * k, T(y0) * k, T(x1) * k, T(y1) * k), fill=255, width=int(W))
            cap(x0, y0); cap(x1, y1)
        else:
            _, cx, cy, R, a0, a1 = p
            for i in range(91):
                a = math.radians(a0 + (a1 - a0) * i / 90)
                cap(cx + R * math.cos(a), cy + R * math.sin(a))
    return m


def render(size, layer='full'):
    """layer: full (bg + glyph), fg (adaptive foreground, transparent), mono (white silhouette, transparent)."""
    S = size * 2
    sc = 1.0 if layer == 'full' else 0.74
    ms, mb = _mask(STEM, 70, S, sc), _mask(BAR, 70, S, sc)
    if layer == 'full':
        bg = Image.new('RGB', (S, S))
        px = bg.load()
        for y in range(S):
            t = y / (S - 1)
            c = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3))
            for x in range(S):
                px[x, y] = c
        im = bg.convert('RGBA')
    else:
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    a_col, b_col = ((IVORY, RED) if layer != 'mono' else ((255, 255, 255), (255, 255, 255)))
    A = Image.new('RGBA', (S, S), a_col + (255,)); A.putalpha(ms)
    B = Image.new('RGBA', (S, S), b_col + (255,)); B.putalpha(mb)
    im = Image.alpha_composite(Image.alpha_composite(im, A), B)
    return im.resize((size, size), Image.LANCZOS)
