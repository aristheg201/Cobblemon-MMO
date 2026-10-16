#!/usr/bin/env python3
"""Author original gun/axe foley; no movie recordings or third-party samples."""
import argparse, math, random, struct, subprocess, tempfile, wave
from pathlib import Path

RATE = 48000

def synth(kind, seed):
    rng = random.Random(seed)
    duration = .38 if kind == 'gun_fire' else .43
    samples, noise, phase = [], 0., 0.
    for i in range(int(RATE * duration)):
        t = i / RATE
        white = rng.uniform(-1, 1)
        noise = .82 * noise + .18 * white
        if kind == 'gun_fire':
            # Receiver clack, a short downward electronic discharge and a metal tail.
            phase += 2 * math.pi * (190 + 1900 * math.exp(-t * 60)) / RATE
            value = (.56 * math.sin(phase) * math.exp(-t * 28)
                     + .42 * white * math.exp(-t * 95)
                     + .18 * noise * math.exp(-t * 12))
            for f in (1160, 1780, 2630):
                value += .035 * math.sin(2 * math.pi * f * t) * math.exp(-t * 17)
            if .038 < t < .065:
                value += .18 * white * math.exp(-(t - .038) * 100)
        else:
            # Air sweep followed by a brief, high metal edge resonance.
            sweep = math.sin(math.pi * min(t / .26, 1)) ** 2 if t < .26 else 0
            phase += 2 * math.pi * (400 + 2700 * t / duration) / RATE
            value = .62 * noise * sweep + .14 * white * sweep + .055 * math.sin(phase) * sweep
            if t > .13:
                for f in (2050, 3140, 4370):
                    value += .055 * math.sin(2 * math.pi * f * (t - .13)) * math.exp(-(t - .13) * 18)
        fade = min(1, t / .0015, (duration - t) / .015)
        samples.append(value * max(0, fade))
    peak = max(abs(v) for v in samples)
    return [round(v / max(1., peak / .86) * 32767) for v in samples]

def build(root):
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for cue in ('gun_fire', 'kunai_slash'):
            for variant in range(1, 4):
                pcm = synth(cue, 100 + variant)
                wav = Path(tmp) / 'source.wav'
                with wave.open(str(wav), 'wb') as f:
                    f.setnchannels(1); f.setsampwidth(2); f.setframerate(RATE)
                    f.writeframes(struct.pack('<' + 'h' * len(pcm), *pcm))
                subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-i', str(wav),
                                '-c:a', 'libvorbis', '-q:a', '5', str(root / f'{cue}_{variant}.ogg')], check=True)

if __name__ == '__main__':
    p = argparse.ArgumentParser();p.add_argument('--output', type=Path,
        default=Path(__file__).resolve().parents[1] / 'client/src/main/resources/assets/fantasyhub/sounds/kabuto')
    build(p.parse_args().output)
