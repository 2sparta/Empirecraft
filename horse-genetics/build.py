#!/usr/bin/env python3
"""Compile the DLO genetics addon with the same pinned Forge/Minecraft SDK as armor."""
import importlib.util
from pathlib import Path
ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('armor_build',ROOT.parent/'layered-armor/build.py')
build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
build.ROOT=ROOT;build.BUILD=ROOT.parent/'work/horses/build';build.NAME='empirecraft-equine-genetics-1.0.0.jar';build.MIXIN_CONFIG='empirecraft_equine.mixins.json'
if __name__=='__main__':build.main()
