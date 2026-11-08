#!/usr/bin/env python3
"""Package the personal ChatPlus settings update and corresponding GPL sources."""
import hashlib,importlib.util,json,zipfile
from pathlib import Path
from build import HERE,REPO,NAME,PLUGIN,archive

def main():
    files={'README.md':(HERE/'README.md').read_bytes(),'LICENSE-ChatPlus.txt':(HERE/'LICENSE-ChatPlus.txt').read_bytes()}
    for suffix in ['', '.sha256']:files['client/mods/'+NAME+suffix]=(REPO/'mods'/(NAME+suffix)).read_bytes()
    files['server/config/admanticheat/idfile/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    spec=importlib.util.spec_from_file_location('chat_pack_support',REPO/'pack-fixes/package_support.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper);helper.add_updates(files,REPO)
    report=json.loads((HERE/'build/report.json').read_text())
    assert report['chatplus_sha256']==hashlib.sha256((REPO/'mods'/NAME).read_bytes()).hexdigest(), 'Unverified client JAR'
    assert report['plugin_sha256']==hashlib.sha256((REPO/'server/plugins'/PLUGIN).read_bytes()).hexdigest(), 'Unverified server plugin'
    log=HERE/'build/test-results.txt'
    testlog=log.read_text() if log.exists() else '\n'.join(report.get('checks',[]))
    assert 'CHAT_PREFERENCES_PASS' in testlog and 'CHAT_CLIENT_JVM_VERIFY_PASS' in testlog and 'CHAT_SETTINGS_BYTECODE_PASS' in testlog
    report.update(chatplus_version='2.8.1-empirecraft.3',plugin_version='1.1.0',server_plugin_changed=False,native_width_and_hitbox_preserved=True,changed_upstream_classes=9,graphical_client_tested=False,live_mohist_world_tested=False,original_chatplus_sha256='f30736ebfbeb7f422401e780458ce23b0a9f6e97edef63151fa373becf90ea29',checks=[line for line in testlog.splitlines() if line.startswith(('PASS','CHAT_','INTERACTIVE_MIXIN_PASS'))])
    files['verification.json']=(json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode()
    files['REMOVE-OLD-CHATPLUS.txt']=b'Remove the previous chatplus-forge JAR and its checksum from client mods. Keep only the included version. Apply the paired ADM on the server. Preserve config/chatplus, geometry settings, worlds and server plugins.\n'
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    out=HERE/'dist/Empirecraft-ChatPlus-1.2.0.zip';archive(out,files)
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        for name,data in files.items():assert z.read(name)==data
    sources={}
    for directory in ['src','tests','patch','font','frozen-assets']:
        for path in (HERE/directory).rglob('*'):
            if path.is_file():sources['chat-channels/'+path.relative_to(HERE).as_posix()]=path.read_bytes()
    for name in ['assets.py','build.py','test.py','update_metadata.py','package.py','README.md','LICENSE-ChatPlus.txt','ChatPlus-2.8.1-upstream-source.zip']:
        sources['chat-channels/'+name]=(HERE/name).read_bytes()
    archive(HERE/'dist/Empirecraft-ChatPlus-1.2.0-sources.zip',sources)
    (REPO/'validation/chatplus-1.2.0.json').write_bytes(files['verification.json'])
    print('CHATPLUS_SETTINGS_PACKAGE_PASS',out,hashlib.sha256((REPO/'mods'/NAME).read_bytes()).hexdigest())
if __name__=='__main__':main()
