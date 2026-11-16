#!/usr/bin/env python3
"""Package a natively verified 2.0.1 JAR; optionally update the paired repository installation."""
import argparse, hashlib, importlib.util, json, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adm", type=Path, default=REPO / "server/config/admanticheat/idfile/admanticheat_id_list.json")
    parser.add_argument("--install", action="store_true", help="Update repository client/server JAR and only affected inventory/ADM rows")
    args = parser.parse_args()
    jar = ROOT / "dist/2.0.1/TownyRecruits.jar"
    data = jar.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, "Corrupt JAR"
        assert 'version="2.0.1"' in archive.read("META-INF/mods.toml").decode(), "Wrong mod version"
        for name in ["Recruitment", "TownyRecruitMenu", "RecruitProvisionView", "TownyCommandCompat", "CommandRouting", "TownyNames"]:
            assert "ua/townyrecruits/" + name + ".class" in archive.namelist(), "Missing " + name
        assert not any(name.startswith(("net/minecraft/", "org/bukkit/", "com/palmergames/")) for name in archive.namelist()), "Bundled API classes"
    spec = importlib.util.spec_from_file_location("adm_release", REPO / "ship-cannons/release.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    # A successful compiler invocation alone is not a release verification.
    native=ROOT/'build/native-test';markers=[];logs={}
    assert (native/'world/mods/TownyRecruits.jar').read_bytes()==data, "Native fixture used a different artifact"
    for phase in ['initial','restart']:
        log=(native/(phase+'.log')).read_text(errors='replace')
        assert 'RECRUIT_SMOKE_PASS phase='+phase in log and 'RECRUIT_SMOKE_FAIL' not in log, "Native test did not pass: "+phase
        markers.extend(line[line.index('RECRUIT_SMOKE_PASS'):].split(' \x1b')[0].strip() for line in log.splitlines() if 'RECRUIT_SMOKE_PASS phase='+phase in line)
        logs[phase]=hashlib.sha256((native/(phase+'.log')).read_bytes()).hexdigest()
    validation=dict(status='passed',version='2.0.1',sha256=digest,native_markers=markers,log_sha256=logs,
        probe_sha256=hashlib.sha256((ROOT/'tests/RecruitmentSmokeMod.java').read_bytes()).hexdigest(),
        source_sha256={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ['src/main/java','src/main/resources'] for p in sorted((ROOT/folder).rglob('*')) if p.is_file()},
        build='Changed common/server classes compiled with Java 17 against installed APIs; verified 1.5 client/tactics bytecode retained by build_unified.py.',
        production_world_opened=False,graphical_client_tested=False)
    (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/feedback-2.0.1.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2)+'\n')
    if args.install:
        assert args.adm.resolve()==(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').resolve(), "--install uses the repository ADM; use --adm only when packaging a custom list"
        from install_feedback import install
        install(helper)
    before = args.adm.read_text(encoding="utf-8")
    original, report = helper.parse_adm(before)
    assert sum(row["id"] == "townyrecruits" for row in original["modIds"]) == 1, "Expected one existing TownyRecruits ADM row"
    updated = helper.prepare_adm(original, [dict(id="townyrecruits", version="2.0.1", sha256=digest)], sort_ids=False)
    assert [row for row in original["modIds"] if row["id"] != "townyrecruits"] == [row for row in updated["modIds"] if row["id"] != "townyrecruits"], "Unrelated mod rows changed"
    assert original["resourcePackIds"] == updated["resourcePackIds"], "Resource pack rows changed"
    adm = helper.render_adm(before, original, updated, report).encode("utf-8")
    sidecar = (digest + "  TownyRecruits.jar\n").encode()
    files = {}
    for side in ["client", "server"]:
        files[side + "/mods/TownyRecruits.jar"] = data
        files[side + "/mods/TownyRecruits.jar.sha256"] = sidecar
    files["server/config/admanticheat/idfile/admanticheat_id_list.json"] = adm
    files["README.md"] = (ROOT / "FEEDBACK-2.0.1.md").read_bytes()
    files["validation.json"]=(ROOT/'audit/feedback-2.0.1.json').read_bytes()
    manifest = dict(version="2.0.1", sha256=digest, size=len(data),
        source_adm_sha256=hashlib.sha256(before.encode("utf-8")).hexdigest(),
        graphical_client_tested=False,
        native_tested=True,native_markers=markers,
        note="Native Towny/Recruits/Feudal initial and restart scenarios passed for this exact JAR; graphical client interaction was not tested.")
    files["package-info.json"] = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
    target = ROOT / "dist/2.0.1/TownyRecruits-2.0.1-install.zip"
    with zipfile.ZipFile(target, "w") as archive:
        for name, payload in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 15, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, payload)
    target.with_suffix(".zip.sha256").write_text(hashlib.sha256(target.read_bytes()).hexdigest() + "  " + target.name + "\n")
    print(target)

if __name__ == "__main__":
    main()
