#!/usr/bin/env python3
"""Check all shipped page bounds and full heading retention, including both reports."""
import hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import build,layout

def main():
    book=ROOT/'dist'/build.NAME;checks=0;count=0;wrapped=0
    with zipfile.ZipFile(book) as z:
        for locale in ['uk_ua','en_us']:
            base='assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/'
            template=json.loads(z.read(base+'templates/wrapped_text.json'))
            assert template['components']==[{'type':'patchouli:text','x':0,'y':0,'max_width':112,'text':'#text'}]
            for name in z.namelist():
                if not name.startswith(base+'entries/'):continue
                entry=json.loads(z.read(name));count+=1
                first=entry['pages'][0]
                if first['type']=='empirecraft_guidebook:wrapped_text':
                    assert first['text'].startswith(entry['name']+'\n\n'),name
                    wrapped+=1
                assert first['type']!='patchouli:text','Unbounded first-page header: '+name
                for page in entry['pages']:
                    if page['type'] in ['patchouli:text','empirecraft_guidebook:wrapped_text']:
                        assert layout.lines(page['text'])<=layout.MAX_LINES,(name,page)
                        checks+=1
        for key,title in [('start/handbook','Як користуватися книгою'),('start/world_rules','Доступні матеріали та маршрути розвитку')]:
            e=json.loads(z.read('assets/empirecraft_guidebook/patchouli_books/handbook/uk_ua/entries/'+key+'.json'))
            assert e['name']==title and e['pages'][0]['type']=='empirecraft_guidebook:wrapped_text'
            assert layout.width(title)>116 and layout.lines(title)>=2
    report={'status':'passed','sha256':hashlib.sha256(book.read_bytes()).hexdigest(),'entries_both_locales':count,'wrapped_first_pages':wrapped,'bounded_text_pages':checks,'full_names_preserved':True,'reported_headings_reproduced':2,'font_metrics_source':'Minecraft 1.20.1 bitmap glyph advances','graphical_client_tested':False}
    (ROOT/'audit/layout-test.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('HANDBOOK_LAYOUT_PASS',checks)
if __name__=='__main__':main()
