"""Wrap entry headings through Patchouli's native text template; retain search names."""
import json,re
from pathlib import Path

METRICS=json.loads(Path(__file__).with_name('font-metrics.json').read_text())['advances']
TEXT_WIDTH=112
MAX_LINES=13

def plain(text):return re.sub(r'\$\([^)]*\)|§.', '',text)
def width(text):return sum(METRICS.get(c,8) for c in plain(text))
def lines(text):
    total=0
    for paragraph in plain(text).split('\n'):
        current=0;total+=1
        for word in paragraph.split():
            size=width(word)
            if current and current+4+size>TEXT_WIDTH:total+=1;current=0
            if size>TEXT_WIDTH:
                for char in word:
                    advance=width(char)
                    if current+advance>TEXT_WIDTH:total+=1;current=0
                    current+=advance
            else:current+=(4 if current else 0)+size
    return total

def split(text):
    result=[]
    while text:
        if lines(text)<=MAX_LINES:result.append(text);break
        ends=[m.end() for m in re.finditer(r'\S+(?:\s+|$)',text)]
        fitting=[end for end in ends if lines(text[:end].rstrip())<=MAX_LINES]
        cut=max(fitting) if fitting else ends[0]
        result.append(text[:cut].strip());text=text[cut:].strip()
    return result

def fit(categories,entries):
    changed=[]
    for key,entry in entries.items():
        original=entry['name']
        if entry['pages'][0]['type']=='patchouli:text':
            changed.append({'id':key,'full':original,'unwrapped_width':width(original),'heading_lines':lines(original)})
            entry['pages'][0]['text']=original+'\n\n'+entry['pages'][0].get('text','')
        output=[]
        for page_index,page in enumerate(entry['pages']):
            if page['type']=='patchouli:text':
                if page.get('title') and page_index:page['text']=page['title']+'\n\n'+page['text']
                for index,part in enumerate(split(page['text'])):
                    clone=dict(page);clone['text']=part
                    if index:clone.pop('anchor',None);clone.pop('title',None)
                    # Template pages use wrapped text; PageText always draws the
                    # first entry name on one unbounded line, regardless of title.
                    if page_index==0 or page.get('title'):
                        clone['type']='empirecraft_guidebook:wrapped_text';clone.pop('title',None)
                    output.append(clone)
            else:output.append(page)
        entry['pages']=output
    return {'page_width':116,'text_width':TEXT_WIDTH,'maximum_text_lines':MAX_LINES,'wrapped_headings':changed,'entry_names_preserved_for_search':True}
