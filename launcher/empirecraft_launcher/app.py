from __future__ import annotations
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox
from . import __version__
from .profiles import PROFILES, recommended_ram, apply_options
from .updater import data_root, instance_lock, load_manifest, install_pack, recover
from .security import atomic_write
from .game import install_runtime, make_command, run_game, SERVER
from .diagnostics import hardware, advice, gc_summary

FONT = "Segoe UI" if sys.platform == "win32" else "DejaVu Sans"

BG, PANEL, CARD, LINE = "#0b151b", "#111f28", "#162832", "#29414c"
TEXT, MUTED, GOLD, TEAL = "#e7eeed", "#9aafb4", "#e4bc79", "#65baa9"

class Launcher(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Empirecraft · Launcher")
        self.geometry("1120x760")
        self.minsize(980, 720)
        self.configure(bg=BG)
        self.root_path = data_root()
        self.root_path.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.events = queue.Queue()
        self.busy = False
        try:
            self.settings = json.loads((self.root_path / "settings.json").read_text())
        except (OSError, ValueError):
            self.settings = {}
        self.selected = tk.StringVar(value=self.settings.get("profile", "potato"))
        if self.selected.get() not in PROFILES:
            self.selected.set("potato")
        self.name = tk.StringVar(value=self.settings.get("nickname", ""))
        self.ram = tk.IntVar(value=self.settings.get("ram", recommended_ram(self.selected.get())))
        self.distance = tk.IntVar()
        self.fps = tk.IntVar()
        self.reduced = tk.BooleanVar()
        self.death_effects = tk.BooleanVar()
        self.status = tk.StringVar(value="Обери профіль і готуйся до подорожі.")
        self.style()
        self.layout()
        self.choose(self.selected.get(), initial=True)
        self.protocol("WM_DELETE_WINDOW", self.close)
        self.after(100, self.poll)

    def style(self):
        s = ttk.Style(self)
        s.theme_use("clam")
        s.configure("TFrame", background=BG)
        s.configure("TLabel", background=BG, foreground=TEXT, font=(FONT, 10))
        s.configure("Muted.TLabel", foreground=MUTED)
        s.configure("TCheckbutton", background=BG, foreground=TEXT, font=(FONT, 10))
        s.map("TCheckbutton", background=[("active", BG)], foreground=[("disabled", MUTED)])
        s.configure("TEntry", fieldbackground=CARD, foreground=TEXT, insertcolor=TEXT, bordercolor=LINE, padding=9)
        s.configure("TSpinbox", fieldbackground=CARD, foreground=TEXT, arrowcolor=GOLD, padding=7)
        s.configure("TButton", background=CARD, foreground=TEXT, bordercolor=LINE, padding=(15, 10), font=(FONT, 10), focusthickness=1, focuscolor=GOLD)
        s.map("TButton", background=[("active", "#243d49")], foreground=[("disabled", MUTED)])
        s.configure("Play.TButton", background=GOLD, foreground=BG, padding=(28, 14), font=(FONT, 12, "bold"))
        s.map("Play.TButton", background=[("active", "#f1ce94"), ("disabled", "#5c5547")])
        s.configure("Horizontal.TProgressbar", troughcolor=CARD, background=TEAL, bordercolor=BG, lightcolor=TEAL, darkcolor=TEAL)
        s.configure("Horizontal.TScale", background=BG, troughcolor=CARD)

    def label(self, parent, text, color=TEXT, size=10, bold=False, **kwargs):
        return tk.Label(parent, text=text, bg=parent.cget("bg"), fg=color, font=(FONT, size, "bold" if bold else "normal"), **kwargs)

    def layout(self):
        side = tk.Frame(self, bg=PANEL, width=218)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)
        emblem = tk.Canvas(side, bg=PANEL, height=100, highlightthickness=0)
        emblem.pack(fill="x", padx=24, pady=(24, 0))
        emblem.create_oval(52, 5, 112, 65, outline=GOLD, width=1)
        emblem.create_line(82, 15, 82, 54, fill=GOLD, width=2)
        emblem.create_polygon(78, 19, 60, 47, 78, 47, fill=GOLD)
        emblem.create_polygon(87, 24, 102, 47, 87, 47, fill=TEAL)
        emblem.create_line(60, 54, 103, 54, fill=GOLD, width=2)
        self.label(side, "EMPIRECRAFT", GOLD, 17, True).pack(anchor="w", padx=24)
        self.label(side, "Твоя історія починається тут", MUTED, 9).pack(anchor="w", padx=24, pady=(6, 35))
        self.label(side, "●   Грати", TEAL, 12, True).pack(anchor="w", padx=26, pady=12)
        ttk.Button(side, text="Діагностика", command=self.diagnostics).pack(fill="x", padx=20, pady=5)
        ttk.Button(side, text="Файли та логи", command=self.open_folder).pack(fill="x", padx=20, pady=5)
        bottom = tk.Frame(side, bg=PANEL)
        bottom.pack(side="bottom", fill="x", padx=24, pady=25)
        self.label(bottom, "ЄДИНИЙ СЕРВЕР", MUTED, 8, True).pack(anchor="w")
        self.label(bottom, SERVER, TEXT, 10).pack(anchor="w", pady=(7, 18))
        self.label(bottom, f"Launcher {__version__}\nMinecraft 1.20.1 · Forge", MUTED, 9, justify="left").pack(anchor="w")

        body = tk.Frame(self, bg=BG)
        body.pack(side="left", fill="both", expand=True, padx=26, pady=20)
        self.label(body, "ВІТРИЛА ВГОРУ", TEAL, 9, True).pack(anchor="w")
        self.label(body, "Повернись до свого світу", TEXT, 22, True).pack(anchor="w", pady=(4, 3))
        self.label(body, "Міста, ремесла й далекі береги. Одна збірка — твій темп гри.", MUTED, 10).pack(anchor="w")
        sea = tk.Canvas(body, height=72, bg=BG, highlightthickness=0)
        sea.pack(fill="x", pady=(10, 12))
        def seascape(event=None):
            sea.delete("all")
            w = sea.winfo_width()
            sea.create_rectangle(0, 0, w, 72, fill=PANEL, outline=LINE)
            for y in (52, 61, 68):
                sea.create_line(16, y, w - 16, y, fill="#213943")
            sea.create_text(22, 22, anchor="w", text="EMPIRECRAFT  /  МІСЬКІ ХРОНІКИ", fill=GOLD, font=(FONT, 10, "bold"))
            sea.create_text(22, 45, anchor="w", text="Перевірена збірка · окремі профілі · автоматичні оновлення", fill=MUTED, font=(FONT, 9))
            x = w - 75
            sea.create_line(x, 13, x, 64, fill=GOLD, width=2)
            sea.create_polygon(x-5, 17, x-29, 52, x-5, 52, fill="#3b615d")
            sea.create_polygon(x+5, 23, x+25, 52, x+5, 52, fill="#987b4f")
            sea.create_polygon(x-34, 59, x+32, 59, x+19, 70, x-21, 70, fill=GOLD)
        sea.bind("<Configure>", seascape)
        self.label(body, "01   Обери продуктивність", TEXT, 12, True).pack(anchor="w", pady=(0, 8))
        cards = tk.Frame(body, bg=BG)
        cards.pack(fill="x")
        self.cards = {}
        specs = {"potato": ("05", "Мінімум частинок та ефектів\nБез звукової фізики й шейдерів"), "balanced": ("08", "Помірна дальність і деталі\nДля щоденної гри"), "high": ("12", "Більше деталей та Oculus\nШейдери вмикаєш самостійно")}
        for i, (key, p) in enumerate(PROFILES.items()):
            frame = tk.Frame(cards, bg=CARD, highlightthickness=1, highlightbackground=LINE, cursor="hand2", padx=12, pady=9)
            frame.grid(row=0, column=i, sticky="nsew", padx=(0, 10 if i < 2 else 0))
            cards.columnconfigure(i, weight=1, uniform="card")
            title = self.label(frame, p["title"], TEXT, 15, True)
            title.pack(anchor="w")
            self.label(frame, specs[key][1], MUTED, 9, justify="left").pack(anchor="w", pady=(5, 7))
            self.label(frame, f"{specs[key][0]} чанків   /   {p['fps']} FPS ліміт", GOLD, 9).pack(anchor="w")
            button = tk.Button(frame, text="Обрати", bg=CARD, fg=TEXT, activebackground=LINE, activeforeground=TEXT, relief="flat", bd=0, font=(FONT, 9), command=lambda k=key: self.choose(k))
            button.pack(anchor="w", pady=(6, 0))
            for child in [frame, *frame.winfo_children()]:
                child.bind("<Button-1>", lambda event, k=key: self.choose(k))
            self.cards[key] = (frame, button)
        tuning = tk.Frame(body, bg=BG)
        tuning.pack(fill="x", pady=(12, 6))
        for index in range(3):
            tuning.columnconfigure(index, weight=1)
        self.label(tuning, "Оперативна пам’ять, МБ", MUTED, 9).grid(row=0, column=0, sticky="w")
        self.ram_box = ttk.Spinbox(tuning, from_=2048, to=12288, increment=256, textvariable=self.ram, width=10)
        self.ram_box.grid(row=1, column=0, sticky="w", pady=5)
        self.distance_text = self.label(tuning, "", MUTED, 9)
        self.distance_text.grid(row=0, column=1, sticky="w")
        ttk.Scale(tuning, from_=3, to=20, variable=self.distance, command=lambda v: self.distance_text.configure(text=f"Дальність: {int(float(v))} чанків")).grid(row=1, column=1, sticky="ew", padx=(0, 18))
        self.fps_text = self.label(tuning, "", MUTED, 9)
        self.fps_text.grid(row=0, column=2, sticky="w")
        ttk.Scale(tuning, from_=30, to=240, variable=self.fps, command=lambda v: self.fps_text.configure(text=f"Ліміт: {int(float(v))} FPS")).grid(row=1, column=2, sticky="ew")
        ttk.Checkbutton(body, text="Зменшити візуальні ефекти LSO, зберегти індикатори виживання", variable=self.reduced).pack(anchor="w")
        ttk.Checkbutton(body, text="Ефекти перед смертю: серцебиття й затемнення", variable=self.death_effects).pack(anchor="w")
        self.label(body, "02   Увійди на сервер", TEXT, 12, True).pack(anchor="w", pady=(14, 7))
        login = tk.Frame(body, bg=BG)
        login.pack(fill="x")
        self.name_entry = ttk.Entry(login, textvariable=self.name, width=22)
        self.name_entry.pack(side="left")
        self.label(login, "Нік Minecraft · пароль вводиться лише у грі", MUTED, 9).pack(side="left", padx=15)
        footer = tk.Frame(body, bg=BG)
        footer.pack(side="bottom", fill="x", pady=(12, 0))
        self.progress = ttk.Progressbar(footer, mode="indeterminate")
        self.progress.pack(fill="x", pady=(0, 10))
        tk.Label(footer, textvariable=self.status, bg=BG, fg=MUTED, font=(FONT, 9), anchor="w", wraplength=700, justify="left").pack(fill="x", pady=(0, 10))
        self.play = ttk.Button(footer, text="Вирушити на сервер   →", style="Play.TButton", command=self.launch)
        self.play.pack(side="right")
        self.repair = ttk.Button(footer, text="Перевірити збірку", command=lambda: self.launch(repair=True))
        self.repair.pack(side="left")

    def choose(self, profile, initial=False):
        if self.busy:
            return
        self.selected.set(profile)
        p = PROFILES[profile]
        for key, (frame, button) in self.cards.items():
            frame.configure(highlightbackground=GOLD if key == profile else LINE, highlightthickness=2 if key == profile else 1)
            button.configure(text="● Обрано" if key == profile else "Обрати", fg=GOLD if key == profile else MUTED)
        custom = self.settings.get("tuning", {}).get(profile, {})
        self.distance.set(custom.get("distance", p["distance"]))
        self.fps.set(custom.get("fps", p["fps"]))
        self.reduced.set(custom.get("reduced", p["reduced"]))
        self.death_effects.set(custom.get("death_effects", True))
        if not initial:
            self.ram.set(recommended_ram(profile))
        self.distance_text.configure(text=f"Дальність: {self.distance.get()} чанків")
        self.fps_text.configure(text=f"Ліміт: {self.fps.get()} FPS")

    def launch(self, repair=False):
        if self.busy:
            return
        import re
        name = self.name.get().strip()
        if not repair and not re.fullmatch(r"[A-Za-z0-9_]{3,16}", name):
            messagebox.showerror("Нік Minecraft", "Вкажи 3–16 латинських літер, цифр або _", parent=self)
            return
        profile = self.selected.get()
        try:
            tuning = {"distance": int(self.distance.get()), "fps": int(self.fps.get()), "reduced": self.reduced.get(), "death_effects": self.death_effects.get()}
            ram = int(self.ram.get())
        except (ValueError, tk.TclError):
            messagebox.showerror("Налаштування", "Перевір значення пам’яті й дальності.", parent=self)
            return
        self.settings.update(profile=profile, nickname=name, ram=ram)
        self.settings.setdefault("tuning", {})[profile] = tuning
        atomic_write(self.root_path / "settings.json", json.dumps(self.settings).encode())
        self.busy = True
        self.play.state(["disabled"])
        self.repair.state(["disabled"])
        self.progress.start(16)
        def status(text):
            self.events.put(("status", text))
        def work():
            try:
                with instance_lock(self.root_path):
                    recover(self.root_path)
                    status("Перевіряю підпис каналу оновлень…")
                    manifest = load_manifest(self.root_path, status)
                    required = sum(i["size"] for i in manifest["profiles"][profile]["files"]) * 3 + 2 * 1024**3
                    if shutil.disk_usage(self.root_path).free < required:
                        raise RuntimeError(f"Звільни щонайменше {required / 1024**3:.1f} ГБ на диску для безпечного встановлення.")
                    game = install_pack(self.root_path, manifest, profile, status)
                    apply_options(game, profile, tuning)
                    java = install_runtime(self.root_path, manifest, status, repair=repair)
                    if repair:
                        self.events.put(("done", "Збірку перевірено. Можна запускати гру."))
                        return
                    cmd = make_command(self.root_path, game, java, name, profile, ram)
                    self.events.put(("minimize", None))
                    code = run_game(cmd, game, status)
                    self.events.put(("done", "Гру закрито." if code == 0 else f"Гра завершилася з кодом {code}. Відкрий «Файли та логи» → instances/{profile}/logs."))
            except Exception as exc:
                self.events.put(("error", str(exc)))
        threading.Thread(target=work, name="empirecraft-install", daemon=False).start()

    def poll(self):
        while True:
            try:
                kind, value = self.events.get_nowait()
            except queue.Empty:
                break
            if kind == "status":
                self.status.set(value)
            elif kind == "minimize":
                self.progress.stop()
                self.iconify()
            elif kind in ("done", "error"):
                self.busy = False
                self.progress.stop()
                self.play.state(["!disabled"])
                self.repair.state(["!disabled"])
                self.deiconify()
                self.status.set(value)
                if kind == "error":
                    messagebox.showerror("Не вдалося завершити", value, parent=self)
        self.after(100, self.poll)

    def diagnostics(self):
        profile = self.selected.get()
        popup = tk.Toplevel(self)
        popup.title("Локальна діагностика · Empirecraft")
        popup.geometry("760x570")
        popup.configure(bg=BG)
        box = tk.Text(popup, bg=PANEL, fg=TEXT, font=(FONT, 11), wrap="word", relief="flat", padx=22, pady=20)
        box.pack(fill="both", expand=True, padx=15, pady=15)
        box.insert("end", "Збираю локальні показники…")
        def work():
            data = hardware(self.root_path)
            data["gc"] = gc_summary(self.root_path / "instances" / profile / "logs/gc.log")
            atomic_write(self.root_path / "diagnostics.json", json.dumps(data, indent=2).encode())
            text = f"RAM: {data['ram_mb']} МБ · доступно: {data['available_mb']} МБ\nCPU потоків: {data['cpu_logical']} · вільний диск: {data['disk_free_mb'] // 1024} ГБ\nGC: {data['gc']}\n\n" + "\n\n".join(advice(data)) + "\n\nЗвіт збережено у diagnostics.json. Нічого не надсилається в мережу."
            def show():
                if box.winfo_exists():
                    box.delete("1.0", "end")
                    box.insert("end", text)
                    box.configure(state="disabled")
            self.after(0, show)
        threading.Thread(target=work, daemon=True).start()

    def open_folder(self):
        if sys.platform == "win32":
            os.startfile(self.root_path)
        else:
            subprocess.Popen(["xdg-open", str(self.root_path)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def close(self):
        if self.busy:
            self.iconify()
        else:
            self.destroy()

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Empirecraft Launcher")
    parser.add_argument("--diagnostics", action="store_true")
    args = parser.parse_args()
    if args.diagnostics:
        root = data_root()
        root.mkdir(parents=True, exist_ok=True)
        report = hardware(root)
        print(json.dumps({"hardware": report, "advice": advice(report)}, ensure_ascii=False, indent=2))
        return
    Launcher().mainloop()
