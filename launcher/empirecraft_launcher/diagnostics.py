"""Local metrics only. Never upload logs, player addresses, chat or credentials."""
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time
import psutil
from .security import atomic_write

def hardware(root: Path) -> dict:
    memory = psutil.virtual_memory()
    disk = psutil.disk_usage(root)
    result = {"os": platform.system(), "architecture": platform.machine(), "cpu_logical": psutil.cpu_count(), "ram_mb": memory.total // 1024**2, "available_mb": memory.available // 1024**2, "disk_free_mb": disk.free // 1024**2, "on_battery": None}
    battery = psutil.sensors_battery()
    if battery:
        result["on_battery"] = not battery.power_plugged
    if os.name == "nt":
        try:
            p = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "Get-CimInstance Win32_VideoController | Select-Object Name,DriverVersion | ConvertTo-Json -Compress"], capture_output=True, text=True, timeout=8, creationflags=subprocess.CREATE_NO_WINDOW)
            if p.returncode == 0:
                result["gpus"] = json.loads(p.stdout)
        except (OSError, subprocess.TimeoutExpired, ValueError):
            pass
    return result

def advice(data: dict) -> list[str]:
    notes = ["Невелике загальне завантаження CPU може означати, що зайняте одне ядро. Minecraft часто обмежує головний потік.", "Якщо чанки зупиняються для всіх гравців — перевір TPS/MSPT сервера. Дальність клієнта не збільшить серверну дальність."]
    if data["ram_mb"] < 12288:
        notes.append("Обери Potato, закрий браузер і почни з 4 ГБ для гри. Надмірна Java heap змусить систему використовувати диск.")
    if data.get("on_battery"):
        notes.append("Ноутбук працює від батареї. Підключи живлення й перевір режим продуктивності Windows; лаунчер не змінює системні режими.")
    notes += ["У Windows у параметрах графіки признач високопродуктивну GPU саме для javaw.exe з папки лаунчера. Поточний renderer дивись на F3.", "Регулярні довгі паузи GC видно в logs/gc.log; високий диск і swap — у локальному performance.json. Тихий вентилятор сам по собі причину не визначає."]
    return notes

def monitor(child: subprocess.Popen, output: Path):
    process = psutil.Process(child.pid)
    process.cpu_percent()
    samples = []
    try:
        while child.poll() is None:
            time.sleep(2)
            if child.poll() is not None:
                break
            with process.oneshot():
                cpu, rss = process.cpu_percent(), process.memory_info().rss
                io = process.io_counters() if hasattr(process, "io_counters") else None
            memory = psutil.virtual_memory()
            swap = psutil.swap_memory()
            samples.append({"t": int(time.time()), "java_cpu_percent": cpu, "java_rss_mb": rss // 1024**2, "system_available_mb": memory.available // 1024**2, "swap_used_mb": swap.used // 1024**2, "read_bytes": io.read_bytes if io else 0, "write_bytes": io.write_bytes if io else 0})
            samples = samples[-1800:]
            if len(samples) % 10 == 0:
                atomic_write(output, json.dumps({"interval_seconds": 2, "samples": samples}, separators=(",", ":")).encode())
    except (psutil.Error, OSError):
        pass
    atomic_write(output, json.dumps({"interval_seconds": 2, "samples": samples}, separators=(",", ":")).encode())

def gc_summary(path: Path) -> dict:
    if not path.exists():
        return {"samples": 0}
    values = [float(x) for x in re.findall(r"Pause[^\n]*? ([0-9.]+)ms", path.read_text(errors="replace")[-8_000_000:])]
    return {"samples": len(values), "max_pause_ms": max(values, default=0), "pauses_over_100ms": sum(v > 100 for v in values)}
