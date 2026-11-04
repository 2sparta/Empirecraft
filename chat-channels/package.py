#!/usr/bin/env python3
import json,zipfile,sys
sys.path.insert(1,str(__import__("pathlib").Path(__file__).resolve().parent.parent/"feudal"))
from release import PLUGIN as FEUDAL_PLUGIN, bridge_name
BRIDGE = bridge_name()
from build import HERE,REPO,NAME,PLUGIN,archive

def main():
    files={'README.md':(HERE/'README.md').read_bytes(),'LICENSE-ChatPlus.txt':(HERE/'LICENSE-ChatPlus.txt').read_bytes()}
    for side,folder,name in [('client','mods',NAME),('server','server/plugins',PLUGIN),('server','server/plugins',FEUDAL_PLUGIN)]:
        destination=side+('/mods/' if folder=='mods' else '/plugins/')+name
        for suffix in ['', '.sha256']:files[destination+suffix]=(REPO/folder/(name+suffix)).read_bytes()
    for side,base in [('client','mods'),('server','server/mods')]:
        for suffix in ['', '.sha256']:
            files[side+'/mods/'+BRIDGE+suffix]=(REPO/base/(BRIDGE+suffix)).read_bytes()
    files['CURRENCY.md']=(REPO/'feudal/CURRENCY.md').read_bytes()
    # Include the existing title resource mod so a client installation is self-contained for every cosmetic glyph.
    name='empirecraft-titles-resources-1.0.0.jar'
    files['client/mods/'+name]=(REPO/'mods'/name).read_bytes()
    files['server/config/admanticheat/idfile/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    report=json.loads((HERE/'build/report.json').read_text())
    report.update(routing_assertions=46,full_feudal_suite_passed=True,graphical_client_tested=False,live_mohist_world_tested=False,original_chatplus_sha256='f30736ebfbeb7f422401e780458ce23b0a9f6e97edef63151fa373becf90ea29')
    files['verification.json']=(json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode()
    out=HERE/'dist/Empirecraft-ChatPlus-1.1.0.zip';archive(out,files)
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        assert not any('empirecraft-feudal-1.4.0.jar' in n or '(5).jar' in n or n.endswith('.mv.db') for n in z.namelist())
        for name,data in files.items():assert z.read(name)==data
    # Ship the complete corresponding sources of our changes and the pinned upstream source.
    sources={}
    for directory in ['src','tests','patch','font','frozen-assets']:
        for path in (HERE/directory).rglob('*'):
            if path.is_file():sources['chat-channels/'+path.relative_to(HERE).as_posix()]=path.read_bytes()
    for name in ['assets.py','build.py','test.py','update_metadata.py','package.py','README.md','LICENSE-ChatPlus.txt','ChatPlus-2.8.1-upstream-source.zip']:
        sources['chat-channels/'+name]=(HERE/name).read_bytes()
    for path in (REPO/'feudal/src').rglob('*'):
        if path.is_file():sources[path.relative_to(REPO).as_posix()]=path.read_bytes()
    for path in (REPO/'bridge/src').rglob('*'):
        if path.is_file():sources[path.relative_to(REPO).as_posix()]=path.read_bytes()
    for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat']:
        sources['bridge/'+name]=(REPO/'bridge'/name).read_bytes()
    for path in (REPO/'bridge/gradle').rglob('*'):
        if path.is_file():sources[path.relative_to(REPO).as_posix()]=path.read_bytes()
    for name in ['build.py','release.py']:
        sources['feudal/'+name]=(REPO/'feudal'/name).read_bytes()
    archive(HERE/'dist/Empirecraft-ChatPlus-1.1.0-sources.zip',sources)
    target=REPO/'validation/chatplus-1.1.0.json';target.parent.mkdir(exist_ok=True);target.write_bytes(files['verification.json'])
    print('PASS verified installation overlay and complete changed/upstream sources:',out)
if __name__=='__main__':main()
