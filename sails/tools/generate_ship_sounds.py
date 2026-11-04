#!/usr/bin/env python3
"""Generate original mono hull/water foley (no sampled or third-party audio). Requires ffmpeg."""
from array import array
import math
from pathlib import Path
import random
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE = 22050


def render(kind, seconds):
    rng = random.Random(1401 if kind == 'hull_creak' else 1402)
    samples = array('h')
    low = slow = phase = 0.0
    for i in range(int(seconds * RATE)):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        low += .09 * (noise - low)
        slow += .007 * (noise - slow)
        envelope = math.sin(math.pi * t / seconds) ** 1.5
        if kind == 'hull_creak':
            phase += 2 * math.pi * (117 + 31 * math.sin(t * 3.8) + 6 * low) / RATE
            friction = .35 + .65 * max(0, math.sin(t * 43 + 8 * math.sin(t * 5)))
            sample = envelope * friction * (.23 * math.sin(phase) + .1 * math.sin(phase * 2.97) + .65 * low)
        else:
            laps = .35 + .65 * math.sin(t * 6.2) ** 2
            sample = envelope * laps * (1.4 * low + 2 * slow + .04 * noise)
        samples.append(int(max(-.95, min(.95, sample)) * 32767))
    return samples


def main():
    output = ROOT / 'src/main/resources/assets/vs_sails/sounds/ship'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT.parent / 'work', prefix='ship-audio-') as temp:
        for kind, seconds in [('hull_creak', 1.8), ('hull_water', 1.6)]:
            raw = Path(temp) / (kind + '.wav')
            samples = render(kind, seconds)
            with wave.open(str(raw), 'wb') as wav:
                wav.setparams((1, 2, RATE, len(samples), 'NONE', 'not compressed'))
                wav.writeframes(samples.tobytes())
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(raw), '-map_metadata', '-1',
                            '-c:a', 'libvorbis', '-q:a', '4', str(output / (kind + '.ogg'))], check=True)


if __name__ == '__main__':
    main()
