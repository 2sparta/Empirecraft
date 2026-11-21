#!/usr/bin/env python3
"""Start an isolated Empirecraft test server with a locally installed clean Forge runtime."""

import argparse
import os
from pathlib import Path
import subprocess
import zipfile

from migrate import FORGE_LIB, check_forge, java, properties


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", type=Path)
    args = parser.parse_args()
    stage = args.stage.resolve()
    try:
        config = properties(stage / "server.properties")
        if not (stage / "migration-report.json").is_file() or (stage / "plugins").exists():
            raise ValueError("Expected a Forge staging directory produced by migrate.py")
        if config.get("server-ip") != "127.0.0.1" or config.get("white-list") != "true":
            raise ValueError("The staging server is limited to loopback with whitelist enabled")
        check_forge(stage)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot start Forge staging: {error}\n")
    args_name = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    raise SystemExit(subprocess.call([
        java(), "@user_jvm_args.txt", "@" + (FORGE_LIB / args_name).as_posix(), "nogui",
    ], cwd=stage))


if __name__ == "__main__":
    main()
