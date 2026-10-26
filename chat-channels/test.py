#!/usr/bin/env python3
import hashlib, json, os, subprocess, zipfile
from pathlib import Path
from build import HERE,REPO,NAME,PLUGIN,compile_sources
from PIL import Image, ImageDraw, ImageFont
import io

def main():
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    cp=[HERE/'build/client',HERE/'build/server',HERE/'lib/spigot-api.jar',*libs]
    tests=compile_sources('tests',sorted((HERE/'tests').rglob('*.java')),cp)
    subprocess.run(['java','-cp',os.pathsep.join(map(str,[tests,*cp])),'ua.empirecraft.chat.ChannelTests'],check=True)
    original=HERE/'upstream'/'chatplus-forge-2.8.1 (5).jar'
    if not original.exists():original=REPO/'mods'/'chatplus-forge-2.8.1 (5).jar'
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(REPO/'mods'/NAME) as new:
        modified={n for n in old.namelist() if n.endswith('.class') and old.read(n)!=new.read(n)}
        expected={f'com/ebicep/chatplus/{n}.class' for n in ['config/Config','features/chattabs/ChatTab','features/chattabs/ChatTabs','config/ConfigScreen','config/forge/ConfigScreenImpl','forge/ChatPlusPlatformInitImpl']}
        assert modified==expected,modified
        # Every input/window handler, renderer, tab history/notification renderer and all upstream Mixins stay byte-identical.
        for name in old.namelist():
            if name.endswith(('.mixins.json','refmap.json')):assert old.read(name)==new.read(name)
        assert b'empirecraft.chat.tab.' in new.read('ua/empirecraft/chat/ClientChannels.class')
        font=json.loads(new.read('assets/empirecraft_chat/font/titles.json'))
        assert len(font['providers'])==39
        assert len({p['chars'][0] for p in font['providers']})==39
        sheet=Image.new('RGB',(850,39*42),(32,30,27));draw=ImageDraw.Draw(sheet)
        label_font=ImageFont.truetype(str(HERE/'font/DejaVuSans-Bold.ttf'),14)
        for i,p in enumerate(font['providers']):
            path='assets/'+p['file'].replace(':','/textures/')
            icon=Image.open(io.BytesIO(new.read(path))).convert('RGBA');assert icon.getbbox()
            assert p['height']==8 and p['ascent']==8
            icon.thumbnail((620,32))
            draw.text((10,i*42+8),Path(path).stem,font=label_font,fill='white')
            sheet.paste(icon,(190,i*42+4),icon)
        sheet.save(HERE/'build/cosmetic-preview.png')
        for locale in ['en_us','uk_ua','ru_ru']:
            lang=json.loads(new.read('assets/chatplus/lang/'+locale+'.json'))
            assert all('empirecraft.chat.'+c in lang for c in ['global','local','system','town','kingdom','system_read_only','too_long'])
            labels={'uk_ua':['Глобальний','Локальний','Системні сповіщення','Місто','Королівство'],
                    'ru_ru':['Глобальный','Локальный','Системные уведомления','Город','Королевство']}
            if locale in labels:
                lang.update({'empirecraft.chat.'+c:'Legacy long label' for c in ['global','local','system','town','kingdom']})
                assert [lang['empirecraft.chat.tab.'+c] for c in ['global','local','system','town','kingdom']]==labels[locale], 'Short labels must be embedded in the mod JAR itself'
    with zipfile.ZipFile(REPO/'server/plugins'/PLUGIN) as z:
        assert not any(n.startswith('net/minecraft') or n.startswith('com/palmergames') for n in z.namelist())
    print('PASS exact upstream class allowlist, unchanged direct chat/window UI + Mixins, 39 bitmap assets, three localizations, no bundled server APIs')
if __name__=='__main__':main()
