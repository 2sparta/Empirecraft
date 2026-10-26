"""Deterministic bitmap-font assets for every already owned/selectable cosmetic title."""
import io, json, re, hashlib
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import yaml, zipfile

HERE = Path(__file__).resolve().parent
FONT_SHA = '5c1247acef7f2b8522a31742c76d6adcb5569bacc0be7ceaa4dc39dd252ce895'

def title_image(suffix):
    font = ImageFont.truetype(str(HERE/'font/DejaVuSans-Bold.ttf'), 24)
    chars, colors, current = [], [], '#ffffff'
    i = 0
    palette = dict(zip('0123456789abcdef', ['000000','0000aa','00aa00','00aaaa','aa0000','aa00aa','ffaa00','aaaaaa','555555','5555ff','55ff55','55ffff','ff5555','ff55ff','ffff55','ffffff']))
    suffix = suffix.strip()
    while i < len(suffix):
        if suffix[i:i+2].lower() == '&x' and re.match(r'(?:&[0-9a-fA-F]){6}', suffix[i+2:i+14]):
            current = '#' + suffix[i+3:i+14:2]; i += 14; continue
        if suffix[i] == '&' and i+1 < len(suffix):
            code = suffix[i+1].lower()
            if code in palette: current = '#'+palette[code]
            if code == 'r': current = '#ffffff'
            i += 2; continue
        chars.append(suffix[i]); colors.append(current); i += 1
    text = ''.join(chars).strip()
    bounds = font.getbbox(''.join(chars))
    image = Image.new('RGBA', (int(font.getlength(''.join(chars)))+4, bounds[3]-bounds[1]+4))
    draw = ImageDraw.Draw(image)
    x = 2.0
    for char, color in zip(chars, colors):
        draw.text((x, 2-bounds[1]), char, font=font, fill=color, stroke_width=1, stroke_fill='#15120f')
        x += font.getlength(char)
    out = io.BytesIO(); image.save(out, format='PNG', optimize=False)
    return out.getvalue(), text

def render_assets():
    assert hashlib.sha256((HERE/'font/DejaVuSans-Bold.ttf').read_bytes()).hexdigest()==FONT_SHA
    catalog = json.loads((HERE.parent/'titles/catalog.json').read_text())
    resources = zipfile.ZipFile(HERE.parent/'mods/empirecraft-titles-resources-1.0.0.jar')
    old = json.loads(resources.read('assets/minecraft/font/default.json'))['providers']
    original = {p['chars'][0]:p['file'] for p in old}
    providers, entries, icons, report = [], {}, {}, []
    for index, (key, title) in enumerate(catalog.items()):
        glyph = chr(0xe900+index)
        source = next((c for c in title['suffix'] if c in original), None)
        if source:
            png = resources.read('assets/'+original[source].replace(':','/textures/'))
            origin, label = 'supplied-artwork', title['label']
        else:
            png, label = title_image(title['suffix']); origin = 'rendered-original-colored-text'
        path = 'assets/empirecraft_chat/textures/titles/'+key+'.png'
        entries[path] = png
        providers.append(dict(type='bitmap',file='empirecraft_chat:titles/'+key+'.png',height=8,ascent=8,chars=[glyph]))
        icons[key] = dict(glyph=glyph)
        report.append(dict(key=key, glyph=glyph, origin=origin, label=label, sha256=hashlib.sha256(png).hexdigest()))
    entries['assets/empirecraft_chat/font/titles.json'] = json.dumps(dict(providers=providers),ensure_ascii=False,indent=2).encode()
    return entries, yaml.safe_dump(icons,allow_unicode=True,sort_keys=False).encode(), report

def generate():
    frozen=HERE/'frozen-assets'
    report=json.loads((frozen/'index.json').read_text())
    entries={p.relative_to(frozen).as_posix():p.read_bytes() for p in (frozen/'assets').rglob('*') if p.is_file()}
    for title in report:
        key='assets/empirecraft_chat/textures/titles/'+title['key']+'.png'
        assert hashlib.sha256(entries[key]).hexdigest()==title['sha256']
    return entries,(frozen/'cosmetics.yml').read_bytes(),report

if __name__=='__main__':
    entries,icons,report=render_assets()
    for name,data in entries.items():
        p=HERE/'frozen-assets'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    (HERE/'frozen-assets/cosmetics.yml').write_bytes(icons)
    (HERE/'frozen-assets/index.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
