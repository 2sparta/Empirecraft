#!/usr/bin/env python3
"""Historical build entry point; Sails 16 also needs pinned client rendering APIs.

Delegate to the complete build, which verifies and caches every downloaded input.
The old server-only overlay cannot compile the gravity-aligned water renderer.
"""
import subprocess
import sys
from pathlib import Path

if __name__ == '__main__':
    subprocess.run([sys.executable, Path(__file__).with_name('build.py'), '--sails-only', *sys.argv[1:]], check=True)
