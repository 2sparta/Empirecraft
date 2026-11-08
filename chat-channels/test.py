#!/usr/bin/env python3
import hashlib, json, os, subprocess, zipfile
from pathlib import Path
from build import HERE,REPO,NAME,PLUGIN,compile_sources
from PIL import Image, ImageDraw, ImageFont
import io

RESULTS=[]
def run_test(args,**kwargs):
    result=subprocess.run(args,capture_output=True,text=True,**kwargs)
    print(result.stdout,end='');print(result.stderr,end='')
    result.check_returncode();RESULTS.append(result.stdout+result.stderr)

def main():
    log=HERE/'build/test-results.txt';log.unlink(missing_ok=True)
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    compile_sources('server-tests',[HERE/'src/server/java/ua/empirecraft/chat/ChatFormat.java'],[HERE/'build/client',*libs])
    cp=[HERE/'build/client',HERE/'build/server-tests',*libs]
    tests=compile_sources('tests',sorted((HERE/'tests').rglob('*.java')),cp)
    run_test(['java','-cp',os.pathsep.join(map(str,[tests,*cp])),'ua.empirecraft.chat.ChannelTests'],check=True)
    run_test(['java','-cp',os.pathsep.join(map(str,[tests,*cp])),'ua.empirecraft.chat.PreferencesTests'],check=True)
    original=HERE/'upstream'/'chatplus-forge-2.8.1 (5).jar'
    if not original.exists():original=REPO/'mods'/'chatplus-forge-2.8.1 (5).jar'
    run_test(['java','-cp',os.pathsep.join(map(str,[tests,*cp])),'ChatWidthTests',str(original),str(REPO/'mods'/NAME)],check=True)
    run_test(['java','-cp',os.pathsep.join(map(str,[tests,*cp])),'PreferencesBytecodeTests',str(original),str(REPO/'mods'/NAME)],check=True)
    verification=[tests,REPO/'mods'/NAME,HERE/'build/client-api-srg.jar',*sorted((REPO/'mods').glob('*.jar')),*sorted((REPO/'sails/build/nested').glob('*.jar')),*libs]
    run_test(['java','-Xverify:all','-cp',os.pathsep.join(map(str,verification)),'VerifyClientClasses'],check=True)
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(REPO/'mods'/NAME) as new:
        modified={n for n in old.namelist() if n.endswith('.class') and old.read(n)!=new.read(n)}
        expected={f'com/ebicep/chatplus/{n}.class' for n in ['config/Config','features/chattabs/ChatTab','features/chattabs/ChatTabs','config/ConfigScreen','config/forge/ConfigScreenImpl','features/HideChat','features/TranslateMessage','features/speechtotext/MicrophoneThread','features/PeekChat']}
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
            assert all('empirecraft.chat.settings.'+key in lang for key in ['title','enabled','merge','enabled.tooltip','merge.tooltip','save_failed'])
            assert 'empirecraft.chat.tab.global_merged' in lang
            if locale=='uk_ua':assert lang['empirecraft.chat.tab.global_merged']=='Глобальне'
            labels={'uk_ua':['Глобальний','Локальний','Системні сповіщення','Місто','Королівство'],
                    'ru_ru':['Глобальный','Локальный','Системные уведомления','Город','Королевство']}
            if locale in labels:
                lang.update({'empirecraft.chat.'+c:'Legacy long label' for c in ['global','local','system','town','kingdom']})
                assert [lang['empirecraft.chat.tab.'+c] for c in ['global','local','system','town','kingdom']]==labels[locale], 'Short labels must be embedded in the mod JAR itself'
    with zipfile.ZipFile(REPO/'server/plugins'/PLUGIN) as z:
        assert not any(n.startswith('net/minecraft') or n.startswith('com/palmergames') for n in z.namelist())
    message='PASS exact upstream class allowlist, unchanged direct chat/window UI + Mixins, 39 bitmap assets, three localizations, no bundled server APIs'
    print(message);RESULTS.append(message+'\n');log.write_text(''.join(RESULTS))
if __name__=='__main__':main()
