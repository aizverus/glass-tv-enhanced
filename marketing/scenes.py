import os, sys
sys.path.insert(0, os.path.expanduser("~/.claude/tools/artcraft"))
from artcraft import Client, save_png
D = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/docs/screenshots/enhanced/")
OUT = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/marketing/scenes/")
WR = os.path.expanduser("~/Desktop")
pc = Client("photocraft")
FONT = "Avenir Next"
def text(x, y, s, size, color="#ffffff", align="center"):
    return pc("type.create", x=x, y=y, text=s, size=size, color=color, align=align, font=FONT)

def fresh():
    try:
        pc("file.closeAll")
    except Exception:
        pass

def scene(name, shot, title, sub):
    fresh()
    pc.raw("app.open", {"path": os.path.relpath(D + shot, WR)})
    # a dark band at the bottom for the words, inside the title-safe area (y 108-972)
    pc("select.rect", x=0, y=760, width=1920, height=320, feather=0)
    pc("layer.newFillLayer.gradient", **{"from": "#000000", "to": "#000000", "angle": 90})
    pc("select.deselect")
    pc("layer.newFillLayer.gradient", **{"from": "#05070d", "to": "#05070d", "angle": 90}) if False else None
    text(960, 880, title, 64)
    text(960, 945, sub, 34, "#ffd59a")
    print(name, save_png(pc, OUT + name + ".png"))

def card(name, lines):
    fresh()
    pc("file.new", width=1920, height=1080, background="#0b1020")
    pc("layer.newFillLayer.gradient", **{"from": "#070a14", "to": "#1d2a4a", "angle": 90})
    for (y, s, size, color) in lines:
        text(960, y, s, size, color)
    print(name, save_png(pc, OUT + name + ".png"))

card("00_title", [(470, "Glass TV Enhanced", 130, "#ffffff"), (590, "Your Android TV. Reimagined.", 52, "#ffd59a")])
scene("01_home", "home.jpg", "Everything at a glance", "Clock, weather, prayer times, Wisdom and football on one calm Home")
scene("02_weather", "weather-page.jpg", "Weather for any town", "Today and the days ahead, from Open-Meteo")
scene("03_prayer", "prayer-page.jpg", "Prayer times & azan", "JAKIM e-Solat, by the week or the month")
scene("04_wisdom", "wisdom-widget.jpg", "Wisdom, every day", "Quran verses, hadith and quotes. Pick one, or let them rotate")
scene("05_epl", "epl-table.jpg", "Premier League live", "Scores, the table and one-click highlights")
scene("06_preview", "wallpaper-preview.jpg", "Try before you choose", "Preview any wallpaper on the real Home")
scene("07_sepia", "filter-sepia.jpg", "10 filters, any strength", "Sepia, Noir, Vivid, Dramatic and more")
scene("08_noir", "filter-noir.jpg", "10 filters, any strength", "Sepia, Noir, Vivid, Dramatic and more")
scene("09_language", "language.jpg", "Speaks your language", "English  ·  Bahasa Melayu  ·  中文")
scene("10_allapps", "all-apps.jpg", "Hide what you don't need", "Every widget has its own tile to bring it back")
card("11_end", [(400, "Glass TV Enhanced", 110, "#ffffff"), (500, "Free & open source  ·  No ads  ·  No bloat", 46, "#dfe6ff"),
                (620, "github.com/aizverus/glass-tv-enhanced", 56, "#ffc46b"), (720, "Based on Glass TV by Gathin (MIT)", 30, "#9aa6c8")])
