import os, sys, json
sys.path.insert(0, os.path.expanduser("~/.claude/tools/artcraft"))
from artcraft import Client, save_png
D = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/docs/screenshots/enhanced/")
OUT = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/marketing/poster.png")
pc = Client("photocraft")
try:
    pc("file.closeAll")
except Exception as e:
    print("closeAll", e)
WR = os.path.expanduser("~/Desktop")

def place(path, scale, center):
    """Opens a picture as its own document, copies it into the poster and sizes it there."""
    pc.raw("app.open", {"path": os.path.relpath(path, WR)})
    pc("select.all"); pc("edit.copy"); pc("file.close")
    pc("document.activate", document=0)
    pc("edit.paste", center=center)
    r = pc("edit.transform", scaleX=scale, scaleY=scale)
    return r
def c(cmd, **p):
    r = pc(cmd, **p)
    return r
W, H = 1080, 1350
print(c("file.new", width=W, height=H, background="#0b1020"))
c("layer.newFillLayer.gradient", **{"from": "#070a14", "to": "#1d2a4a", "angle": 90})
# soft warm glow behind the TV
c("layer.newFillLayer.gradient", **{"from": "#ffc46b", "to": "#0b1020", "angle": 90, "style": "radial"})
print(c("layer.properties", opacity=0.18) if False else "")
FONT = "Avenir Next"
def text(x, y, s, size, color="#ffffff", align="center", weight=None):
    p = {"x": x, "y": y, "text": s, "size": size, "color": color, "align": align, "font": FONT}
    r = c("type.create", **p)
    return r
print(text(540, 150, "Glass TV Enhanced", 76))
print(text(540, 215, "Your Android TV. Fast, ad-free and truly yours.", 30, "#ffd59a"))
# hero screenshot
r = place(D + "home.jpg", 50, [540, 520])
print(r)
c("layer.layerStyle.stroke", size=3, position="outside", color="#ffffff", opacity=55)
c("layer.layerStyle.dropShadow", color="#000000", opacity=70, distance=18, size=40, angle=90)
# three thumbnails
for i, (f, cap) in enumerate([("filter-sepia.jpg", "Wallpaper studio"), ("weather-page.jpg", "Weather"), ("wisdom-page.jpg", "Wisdom")]):
    cx = 210 + i * 330
    place(D + f, 15.5, [cx, 905])
    c("layer.layerStyle.stroke", size=2, position="outside", color="#ffffff", opacity=45)
    c("layer.layerStyle.dropShadow", color="#000000", opacity=60, distance=8, size=18, angle=90)
    text(cx, 1036, cap, 24, "#ffffff")
lines = ["Weather  ·  Prayer times  ·  Wisdom  ·  Premier League widgets",
         "Hide any widget  ·  Live wallpaper preview with 10 filters",
         "English  ·  Bahasa Melayu  ·  中文  ·  Daily cache clean, no ads"]
for i, s in enumerate(lines):
    text(540, 1108 + i * 42, s, 25, "#dfe6ff")
text(540, 1256, "Free & open source  ·  github.com/aizverus/glass-tv-enhanced", 26, "#ffc46b")
text(540, 1292, "Based on Glass TV by Gathin (MIT)", 18, "#9aa6c8")
print(save_png(pc, OUT))
