#!/usr/bin/env python3
"""Check actual packaged navigation against the pinned Patchouli 85 icon geometry."""
import hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import build,layout

def main():
    book=ROOT/'dist'/build.NAME;rectangles=[]
    # GuiBookLanding.addCategoryButton and GuiBookCategory.addSubcategoryButtons:
    # four columns, x=151+24*column, y=43+24*row; category buttons are 20x20.
    def bounds(screen,count):
        for index in range(count):
            x,y=151+(index%4)*24,43+(index//4)*24
            assert 141<=x and x+20<=263 and 13<=y and y+20<=169,(screen,index,x,y)
            rectangles.append({'screen':screen,'x':x,'y':y,'width':20,'height':20})
    with zipfile.ZipFile(book) as z:
        for locale in ['uk_ua','en_us']:
            prefix='assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/'
            categories={Path(n).stem:json.loads(z.read(n)) for n in z.namelist() if n.startswith(prefix+'categories/')}
            roots={k:v for k,v in categories.items() if 'parent' not in v};assert len(roots)==5 and len(categories)==30
            bounds(locale+'/landing',len(roots)+1) # includes native "all entries" button
            for key,root in roots.items():
                assert layout.width(root['name'])<=116
                children=[k for k,v in categories.items() if v.get('parent')==build.NS+':'+key];assert 1<=len(children)<=6
                bounds(locale+'/'+key,len(children))
            for n in z.namelist():
                if n.startswith(prefix+'entries/'):
                    entry=json.loads(z.read(n));chapter=entry['category'].removeprefix(build.NS+':')
                    assert chapter in categories and chapter not in roots,(n,chapter)
    # The previous 25-root layout overflows; this check reproduces that report.
    assert 43+(25//4)*24+20>180
    report={'status':'passed','sha256':hashlib.sha256(book.read_bytes()).hexdigest(),'roots':5,'chapters':25,'bounded_icon_rectangles':len(rectangles),
            'maximum_icon_bottom':max(r['y']+r['height'] for r in rectangles),'previous_overflow_reproduced':True,'graphical_client_tested':False}
    (ROOT/'audit/navigation-test.json').write_bytes(build.encode(report));print('HANDBOOK_NAVIGATION_PASS',len(rectangles))
if __name__=='__main__':main()
