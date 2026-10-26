#!/usr/bin/env python3
"""Build a client resource-pack override for the five ChatPlus tab labels."""
import json,zipfile
from pathlib import Path

LABELS={
    'uk_ua':['Глобальний','Локальний','Системні сповіщення','Місто','Королівство'],
    'ru_ru':['Глобальный','Локальный','Системные уведомления','Город','Королевство'],
    'en_us':['Global','Local','System notifications','Town','Kingdom'],
}
KEYS=['empirecraft.chat.'+c for c in ['global','local','system','town','kingdom']]
def main():
    out=Path(__file__).resolve().parent/'dist/Empirecraft-Short-Chat-Tabs.zip'
    out.parent.mkdir(exist_ok=True)
    entries={'pack.mcmeta':json.dumps({'pack':{'pack_format':15,'description':'Empirecraft — короткі назви вкладок чату'}},ensure_ascii=False)}
    for locale,names in LABELS.items():entries['assets/chatplus/lang/'+locale+'.json']=json.dumps(dict(zip(KEYS,names)),ensure_ascii=False,indent=2)
    with zipfile.ZipFile(out,'w') as z:
        for name,value in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,26,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,value.encode())
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        for locale,names in LABELS.items():assert json.loads(z.read('assets/chatplus/lang/'+locale+'.json'))==dict(zip(KEYS,names))
    print(out)
if __name__=='__main__':main()
