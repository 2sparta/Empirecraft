import hashlib,json,shutil,sys,zipfile,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(REPO/'siegeworks'));from build import archive,sha
import update_installation as generic

def main():
 lock=json.loads((REPO/'modpack-lock.json').read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_calendar');assert row['version']=='1.2.4'
 changes=json.loads((ROOT/'tag-files.json').read_text())
 climate=next(r for r in lock['mods'] if r['id']=='empirecraft_tfc');assert climate['version']=='1.0.4'
 release=dict(row,additional_mods=[climate],retire_ids=['chippedexttras'],files={});files={}
 for side,folder in [('client',''),('server','server/')]:
  names=['mods/'+row['file'],'mods/'+row['file']+'.sha256','mods/'+climate['file'],'mods/'+climate['file']+'.sha256','config/sereneseasons/seasons.toml','config/sereneseasons/fertility.toml']+[n[len(folder):] for n in changes if (n.startswith('server/') if side=='server' else not n.startswith('server/'))]
  if side=='server':names+=['config/admanticheat/idfile/admanticheat_id_list.json']
  payload={n:(REPO/folder/n).read_bytes() for n in names};release['files'][side]={n:hashlib.sha256(b).hexdigest() for n,b in payload.items()};files.update({side+'/'+n:b for n,b in payload.items()})
 text=(REPO/'siegeworks/update_installation.py').read_text().replace("ID='siegeworks'","ID='empirecraft_calendar'").replace('siegeworks-1','copycats-calendar-1')
 # Retain local season/weather/dimension settings; repair only timing when a file exists.
 text=text.replace('writes[root/relative]=raw', '''target=root/relative
        if relative=='config/sereneseasons/seasons.toml' and target.is_file():
            import re
            original=target.read_text();parts=re.split(r'(?m)(?=^\\s*\\[[^\\]]+\\])',original);found=False
            for i,part in enumerate(parts):
                if re.match(r'\\s*\\[time_settings\\]',part):
                    found=True
                    for key,value in [('day_duration',57600),('sub_season_duration',30)]:
                        pattern=r'(?m)^(\\s*'+key+r'\\s*=\\s*)[^#\\r\\n]*'
                        if re.search(pattern,part):part=re.sub(pattern,lambda m:m[1]+str(value)+' ',part)
                        else:part+='\\n'+key+' = '+str(value)+'\\n'
                    parts[i]=part
            if not found:parts.append('\\n[time_settings]\\nday_duration = 57600\\nsub_season_duration = 30\\n')
            raw=''.join(parts).encode()
        if relative=='config/sereneseasons/fertility.toml' and target.is_file():raw=target.read_bytes()
        writes[target]=raw''')
 files['update_installation.py']=text.encode();files['release.json']=(json.dumps(release,ensure_ascii=False,indent=2)+'\n').encode()
 files['README.uk.md']=(ROOT/'README.uk.md').read_bytes()
 for p in (ROOT/'audit').glob('*.json'):files['verification/'+p.name]=p.read_bytes()
 target=ROOT/'dist/Empirecraft-Copycats-Calendar-Climate-1-install.zip';archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n');(ROOT/'release.json').write_bytes(files['release.json']);print('TRANSITION_RELEASE',sha(target),target.stat().st_size)
if __name__=='__main__':main()
