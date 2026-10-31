import os, sys, json
sys.path.insert(0, os.path.expanduser("~/.claude/tools/artcraft"))
from artcraft import Client
M = os.path.expanduser("~/Desktop/GlassTV-Rumah-Kulim/marketing/")
TICK = 254016000000
fc = Client("filmcraft")
d = json.load(open(M + "film_inspect.json"))
clips = d["video"][0]["items"]
for i, c in enumerate(clips):
    s, e = c["startFrame"], c["endFrame"]
    a, b = (100, 106) if i % 2 == 0 else (106, 100)   # alternate zoom in and out
    if c["name"].startswith(("00_", "11_")):
        a, b = 100, 104
    fc("effects.setParam", clip=c["clip"], effect="motion", param="scale", value=a, time=int(s * TICK / 30), keyframe=True)
    fc("effects.setParam", clip=c["clip"], effect="motion", param="scale", value=b, time=int((e - 1) * TICK / 30), keyframe=True)
    if i > 0:
        print(fc("sequence.applyVideoTransition", clip=c["clip"], effect="cross_dissolve", frames=10, edge="in"))
audio = d["audio"][0]["items"][0]["clip"]
print(fc("sequence.applyAudioTransition", clip=audio, effect="constant_power", frames=60, edge="out"))
out = M + "glass-tv-enhanced-ad.mp4"
print(fc("file.exportMedia", path=out, format="h264", crf=20, wait=True))
