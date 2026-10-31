"""Recipe: compose a 100 BPM lo-fi BGM in SoundCraft (Am7-Fmaj7-C-G x2, 8 bars = 19.2s + tail).

Run: python3 ~/.claude/tools/artcraft/recipes/soundcraft_lofi_bgm.py "<out.wav>"
Made the BGM of the ArtCraft-only test short (2026-10-20). MIDI = 960 ticks per beat; synth times in ms,
cutoff in Hz; osc waves 0..3. Master maximizer threshold -22 / ceiling -2 lands about -14.4 LUFS, -1.2 dBTP.
"""
import json
import os
import sys

sys.path.insert(0, os.path.expanduser("~/.claude/tools/artcraft"))
from artcraft import Client  # noqa: E402

sc = Client("soundcraft")
T = 960
BAR = 4 * T
BARS = 21
BAR_SECONDS = 4 * 60 / 112  # 4 beats at 112 BPM


def track(name, inst, params, db):
    t = sc("track.new", kind="instrument", name=name, instrument=inst)["tracks"][0]
    for k, v in params.items():
        sc("mix.instrument_param", track=t, param=k, value=v)
    sc("mix.volume", tracks=[t], db=db)
    return t


def clip(t):
    return sc("midi.new_clip", track=t, start={"seconds": 0}, end={"seconds": BARS * BAR_SECONDS})["clip"]


def note(c, pitch, start, length, vel=100):
    sc("midi.note_add", clip=c, pitch=pitch, start_ticks=start, length_ticks=length, velocity=vel)


def main(out):
    sc("session.new", name="glass_tv_ad")
    sc("event.tempo", bpm=112)
    pad = track("Pad", "subtractive_synth", dict(osc1_wave=1, osc2_wave=1, osc2_detune=0.15, osc_mix=0.5,
                cutoff=1400, resonance=0.1, filter_env_amount=0.2, amp_attack=600, amp_decay=900,
                amp_sustain=0.75, amp_release=1500), -14)
    bass = track("Bass", "subtractive_synth", dict(osc1_wave=2, osc2_wave=0, osc2_semitones=-12, osc_mix=0.4,
                 cutoff=500, resonance=0.15, amp_attack=5, amp_decay=400, amp_sustain=0.6, amp_release=150), -11)
    arp = track("Arp", "subtractive_synth", dict(osc1_wave=3, osc2_wave=1, osc2_detune=0.05, osc_mix=0.3,
                cutoff=2600, resonance=0.2, filter_env_amount=0.5, filter_decay=180, amp_attack=2,
                amp_decay=220, amp_sustain=0.0, amp_release=250), -17)
    drums = track("Drums", "drum_synth", dict(kick_decay=380, kick_tune=0.45, snare_decay=180, snare_snappy=0.6,
                  hat_decay=45), -9)

    sc("mix.insert", track=pad, slot=0, plugin="plate_reverb")
    sc("mix.insert", track=arp, slot=0, plugin="mod_delay")
    sc("mix.insert", track=arp, slot=1, plugin="room_reverb")
    sc("mix.insert", track=drums, slot=0, plugin="lofi")
    sc("mix.insert", track=drums, slot=1, plugin="compressor")

    chords = [[60, 64, 67, 71],   # Cmaj7
              [55, 59, 62, 67],   # G
              [57, 60, 64, 67],   # Am7
              [53, 57, 60, 64]]   # Fmaj7
    roots = [48, 43, 45, 41]      # C3 G2 A2 F2
    cp, cb, ca, cd = clip(pad), clip(bass), clip(arp), clip(drums)
    for b in range(BARS):
        ch, r, s = chords[b % 4], roots[b % 4], b * BAR
        for p in ch:
            note(cp, p, s, BAR - 20, 70)
        if 2 <= b < BARS - 1:  # bass enters with the drums, rests on the last bar
            for off, p, ln in [(0, r, T), (int(1.5 * T), r, T // 2), (2 * T, r + 7, T),
                               (int(3.5 * T), r + 12, T // 2)]:
                note(cb, p, s + off, ln, 95)
        if 3 <= b < BARS - 1:  # 8th-note arp an octave up
            seq = [ch[0], ch[1], ch[2], ch[3], ch[2], ch[1], ch[2], ch[3]]
            for i, p in enumerate(seq):
                note(ca, p + 12, s + i * T // 2, T // 2, 75 if i % 2 else 92)
        # drums (GM): 36 kick, 38 snare, 42 closed hat; hats only in bars 1-2
        if b >= BARS - 1:
            continue
        for i in range(8):
            note(cd, 42, s + i * T // 2, T // 4, 70 if i % 2 else 90)
        if b >= 2:
            for off in (0, int(2.5 * T)):
                note(cd, 36, s + off, T // 2, 110)
            for off in (T, 3 * T):
                note(cd, 38, s + off, T // 2, 100)

    master = sc("track.new", kind="master", name="Master")["tracks"][0]
    sc("mix.insert", track=master, slot=0, plugin="maximizer",
       params={"threshold": -22, "ceiling": -2, "release": 80})
    return sc("file.bounce_mix", path=out, format="wav", bit_depth=24, start={"seconds": 0},
              end={"seconds": 45.5}, normalize=False)


if __name__ == "__main__":
    print(json.dumps(main(os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "bgm.wav"))))
