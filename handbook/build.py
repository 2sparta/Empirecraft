#!/usr/bin/env python3
"""Build the resource-only Patchouli handbook from reviewed player text and current pack data."""
import csv,hashlib,io,json,re,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;NS='empirecraft_guidebook';BOOK=NS+':handbook';VERSION='1.0.7'
NAME='empirecraft-handbook-'+VERSION+'.jar'

def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encode(value))
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            i=zipfile.ZipInfo(name,(2026,11,12,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
def clean(text):
    text=re.sub(r'\[([^\]]+)\]\([^)]*\)',r'\1',text)
    text=re.sub(r'`([^`]+)`',r'\1',text).replace('**','').replace('__','')
    return text.strip()
def pages(text,limit=330):
    """Keep native book pages short enough for Minecraft's narrow, wrapped font."""
    result=[]
    for para in re.split(r'\n\s*\n',text):
        para=clean(para)
        if not para:continue
        if para.startswith('|'):
            rows=[r for r in para.splitlines() if not re.fullmatch(r'[| :\-]+',r)]
            para='\n'.join(' — '.join(c.strip() for c in r.strip('|').split('|')) for r in rows)
        para=re.sub(r'(?m)^[-*] ', '• ',para)
        while len(para)>limit:
            cut=max(para.rfind('. ',0,limit),para.rfind('\n',0,limit))
            if cut<limit//2:cut=para.rfind(' ',0,limit)
            if cut<1:cut=limit
            result.append({'type':'patchouli:text','text':para[:cut+1].strip()});para=para[cut+1:].strip()
        if para:result.append({'type':'patchouli:text','text':para})
    return result

def main():
    categories={};entries={};current=None;category=None
    for block in re.split(r'(?m)(?=^#{1,2} )',(ROOT/'guide.uk.md').read_text()):
        lines=block.strip().splitlines()
        if not lines:continue
        head=lines[0]
        if head.startswith('# '):
            key,title,icon,mods=[s.strip() for s in head[2:].split('|')]
            category=key;categories[key]={'name':title,'description':'Практичні інструкції, порядок дій та правила поточної збірки.','icon':icon,'sortnum':len(categories),'mods':mods.split(',')}
        elif head.startswith('## '):
            key,title,icon=[s.strip() for s in head[3:].split('|')]
            current=category+'/'+key
            entries[current]={'name':title,'icon':icon,'category':NS+':'+category,'sortnum':len(entries),'read_by_default':True,'pages':pages('\n'.join(lines[1:]))}
    # Reviewed, current player-facing sections. Installation, tests, migration and implementation
    # sections are deliberately not game instructions. The exact inclusion manifest is audited.
    from sources import MANUALS, append_manuals, append_data
    provenance=append_manuals(REPO,categories,entries,pages,NS)
    append_data(REPO,categories,entries,pages,NS)
    from navigation import group_categories
    navigation=group_categories(categories,NS)
    from layout import fit
    layout=fit(categories,entries)
    assert len(categories)>=18 and len(entries)>=100,'Handbook coverage unexpectedly shrank'
    files={}
    files['META-INF/mods.toml']=('modLoader="lowcodefml"\nloaderVersion="[47,)"\nlicense="CC-BY-4.0"\n[[mods]]\nmodId="'+NS+'"\nversion="'+VERSION+'"\ndisplayName="Empirecraft Handbook"\ndescription="Detailed Ukrainian guide to this modpack."\n[[dependencies.'+NS+']]\nmodId="patchouli"\nmandatory=true\nversionRange="[1.20.1-85-FORGE,)"\nordering="AFTER"\nside="BOTH"\n').encode()
    files['pack.mcmeta']=encode({'pack':{'pack_format':15,'description':'Довідник Empirecraft: механіки поточної збірки'}})
    files['data/'+NS+'/patchouli_books/handbook/book.json']=encode({'name':'Довідник Empirecraft','landing_text':'Почни з розділу «Перші кроки». Тут зібрані практичні правила саме цієї збірки: виживання, ремесла, природа, спорядження, міста, торгівля та мореплавство. Пошук і закладки допоможуть повернутися до потрібного гайда.','subtitle':'Повний посібник · 1.0','version':1,'show_progress':False,'show_toasts':False,'use_resource_pack':True,'i18n':True,'dont_generate_book':False,'creative_tab':'minecraft:tools_and_utilities'})
    files['data/'+NS+'/recipes/handbook.json']=encode({'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:paper'}],'result':{'item':'patchouli:guide_book','count':1,'nbt':{'patchouli:book':BOOK}}})
    for locale in ['uk_ua','en_us']:
        # en_us is the fallback for every unsupported client language; the handbook remains Ukrainian.
        prefix='assets/'+NS+'/patchouli_books/handbook/'+locale+'/'
        files[prefix+'templates/wrapped_text.json']=encode({'components':[{'type':'patchouli:text','x':0,'y':0,'max_width':112,'text':'#text'}]})
        for key,value in categories.items():files[prefix+'categories/'+key+'.json']=encode({k:v for k,v in value.items() if k!='mods'})
        for key,value in entries.items():files[prefix+'entries/'+key+'.json']=encode(value)
    for key,entry in entries.items():
        assert entry['pages'],key
        assert entry['category'].split(':')[1] in categories,key
        for page in entry['pages']:
            for target in re.findall(r'\$\(l:([^)]*)\)',page.get('text','')):
                target=target.split('#')[0].removeprefix(NS+':')
                assert target in entries,(key,target)
    files['LICENSE.txt']=b'The original Empirecraft handbook text is licensed under CC BY 4.0. Upstream mechanics are documented; no upstream code or textures are bundled. Source references are listed in handbook/audit/content.json.\n'
    files['empirecraft-handbook-catalog.json']=encode({'book':BOOK,'categories':[{'id':k,'icon':v['icon'],'parent':v.get('parent')} for k,v in categories.items()],
        'entries':[{'id':k,'icon':v['icon'],'category':v['category'],'recipes':[p['recipe'] for p in v['pages'] if p['type']=='patchouli:crafting']} for k,v in entries.items()]})
    target=ROOT/'dist'/NAME;archive(target,files)
    target.with_suffix('.jar.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+NAME+'\n')
    report={'version':VERSION,'book':BOOK,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'categories':len(categories),'entries':len(entries),'pages':sum(len(e['pages']) for e in entries.values()),'locales':['uk_ua','en_us (Ukrainian fallback)'],'sources':provenance,'mod_coverage':{key:value['mods'] for key,value in categories.items()},'java_classes':0}
    write(ROOT/'audit/content.json',report);write(ROOT/'build/entries.json',entries)
    write(ROOT/'audit/layout.json',layout)
    write(ROOT/'audit/navigation.json',navigation)
    print('HANDBOOK_BUILD_PASS',json.dumps({k:report[k] for k in ['categories','entries','pages','sha256']},ensure_ascii=False))

if __name__=='__main__':main()
