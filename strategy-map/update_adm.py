#!/usr/bin/env python3
"""Update the three map-related ADM entries after installing the ZIP; preserve all other entries."""
import argparse,copy,hashlib,importlib.util,json,os,stat,tempfile,time
from pathlib import Path

HERE=Path(__file__).resolve().parent

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,required=True);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('adm_helper',HERE/'adm_helper.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    release=json.loads((HERE/'release.json').read_text());file=args.root/'config/admanticheat/idfile/admanticheat_id_list.json'
    text=file.read_text(encoding='utf-8-sig');original,report=helper.parse_adm(text);assert helper.integrity(original)==original['integrityHash']
    updated=copy.deepcopy(original)
    for item in release['mods']:
        installed=args.root/'mods'/item['file'];assert hashlib.sha256(installed.read_bytes()).hexdigest()==item['sha256'],str(installed)
        matches=[row for row in updated['modIds'] if row['id']==item['id']];assert len(matches)<=1,'Duplicate ADM identity: '+item['id']
        row=dict(id=item['id'],version=item['version'],sha256=item['sha256'],filePresent=True)
        if matches:matches[0].update(row)
        else:updated['modIds'].append(row)
    if args.check:assert updated==original,'ADM has not been updated';print('STRATEGY_ADM_CHECK_PASS');return
    if updated==original:print('ADM already matches the installed map update');return
    updated['lastUpdated']=max(int(time.time()*1000),int(original['lastUpdated'])+1);updated['integrityHash']=helper.integrity(updated)
    rendered=helper.render_adm(text,original,updated,report);assert helper.parse_adm(rendered)[0]==updated
    backup=file.with_name(file.name+'.before-strategy-map-'+str(int(time.time()*1000))+'.bak');backup.write_bytes(file.read_bytes())
    descriptor,temporary=tempfile.mkstemp(prefix='.strategy-adm-',dir=file.parent)
    try:
        os.chmod(temporary,stat.S_IMODE(file.stat().st_mode))
        with os.fdopen(descriptor,'w',encoding='utf-8') as stream:stream.write(rendered);stream.flush();os.fsync(stream.fileno())
        os.replace(temporary,file)
    finally:Path(temporary).unlink(missing_ok=True)
    print('STRATEGY_ADM_UPDATE_PASS: only three mod identities updated; backup saved')

if __name__=='__main__':main()
