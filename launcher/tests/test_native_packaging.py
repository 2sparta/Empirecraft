import json
from pathlib import Path
import zipfile

JAR=Path(__file__).resolve().parents[1]/'native/dist/empirecraft-launcher-client-0.1.1.jar'

def test_runtime_classes_are_outside_the_reserved_mixin_namespace():
    with zipfile.ZipFile(JAR) as z:
        config=json.loads(z.read('empirecraft_launcher.mixins.json'))
        prefix=config['package'].replace('.','/')+'/'
        for name in ['LauncherClient.class','LauncherClient$Events.class','EmpireHome.class']:
            runtime='ua/empirecraft/launcherclient/'+name
            assert runtime in z.namelist()
            assert not runtime.startswith(prefix)
        scoped={n for n in z.namelist() if n.startswith(prefix) and n.endswith('.class')}
        declared={prefix+n+'.class' for n in config['client']}
        assert scoped==declared
        assert config['required'] and config['injectors']['defaultRequire']==1

def test_production_artifact_matches_native_build():
    production=JAR.parents[3]/'mods'/JAR.name
    if production.exists():
        assert production.read_bytes()==JAR.read_bytes()


def test_native_pack_metadata():
    import json
    import zipfile
    from pathlib import Path
    jar = Path(__file__).resolve().parents[1] / "native/dist/empirecraft-launcher-client-0.1.1.jar"
    with zipfile.ZipFile(jar) as archive:
        assert json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"] == 15
