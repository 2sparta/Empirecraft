#!/usr/bin/env python3
"""Export the exact non-secret upstream mod binary for offline bytecode verification."""
import base64,hashlib
from pathlib import Path
raw=Path('mods/overgeared-forge-1.20.1-1.6.36.jar').read_bytes()
print('MOD_BINARY_BEGIN '+hashlib.sha256(raw).hexdigest())
encoded=base64.b64encode(raw).decode()
for offset in range(0,len(encoded),4096): print('MOD_BINARY_DATA '+encoded[offset:offset+4096])
print('MOD_BINARY_END')
