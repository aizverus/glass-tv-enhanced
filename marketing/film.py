import os, sys, json, glob
sys.path.insert(0, os.path.expanduser("~/.claude/tools/artcraft"))
from artcraft import Client
M = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/marketing/")
TICK = 254016000000
fc = Client("filmcraft")
print(fc("file.newProject", name="glass_tv_enhanced_ad"))
scenes = [("00_title", 3.5), ("01_home", 4.5), ("02_weather", 3.5), ("03_prayer", 3.5), ("04_wisdom", 3.5),
          ("05_epl", 3.5), ("06_preview", 4.0), ("07_sepia", 2.5), ("08_noir", 2.5), ("09_language", 3.5),
          ("10_allapps", 4.0), ("11_end", 6.5)]
paths = [M + "scenes/" + n + ".png" for n, _ in scenes] + [M + "bgm.wav"]
imp = fc("file.import", paths=paths)
print(json.dumps(imp)[:600])
items = imp.get("items") or imp.get("imported") or imp
ids = [i["id"] if isinstance(i, dict) else i for i in items]
seq = fc("file.newSequence", name="ad", width=1920, height=1080, fps=30)
print(seq)
t = 0.0
for (name, dur), item in zip(scenes, ids[:-1]):
    fc("timeline.place", item=item, track="V1", seconds=t, duration=int(dur * TICK), audio=False)
    t += dur
fc("timeline.place", item=ids[-1], track="A1", seconds=0, duration=int(45.0 * TICK), video=False)
info = fc("sequence.inspect")
open(M + "film_inspect.json", "w").write(json.dumps(info, indent=1))
print(json.dumps(info)[:1500])
