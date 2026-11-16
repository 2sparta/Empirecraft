#!/usr/bin/env python3
"""Compile and execute real JDK-only name/routing regressions. No Bukkit/Minecraft stubs."""
import os, subprocess
from pathlib import Path
root = Path(__file__).resolve().parent
out = root / "build/feedback-unit"
out.mkdir(parents=True, exist_ok=True)
jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
javac = str(jdk / "javac") if jdk else "javac"
java = str(jdk / "java") if jdk else "java"
sources = [root / "src/main/java/ua/townyrecruits" / (name + ".java") for name in ["TownyNames", "CommandRouting"]]
subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-d", str(out), *map(str, sources), str(root / "tests/FeedbackPolicyTest.java")], check=True)
subprocess.run([java, "-ea", "-cp", str(out), "ua.townyrecruits.test.FeedbackPolicyTest"], check=True)
