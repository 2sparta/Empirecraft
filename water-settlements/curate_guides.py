"""Override only native guide chapters made inaccurate by the pack's approved integration."""
from pathlib import Path
import sys,zipfile,json,hashlib,shutil,io
from inputs import read_base
R=Path(__file__).resolve().parent;REPO=R.parent;sys.path.insert(0,str(REPO/'handbook'))
from build import pages
from layout import fit,lines
from build_water import jar
CHAPTERS={
'introduction':('Польовий посібник Empirecraft','Цей світ використовує правила Empirecraft. Основний докладний довідник відкривається через /guides. Налаштування нового світу TFC, його здоров’я, спрага, харчові групи та календар не застосовуються.\n\nСезони визначає чинний календар збірки, спрагу й температуру — Legendary Survival Overhaul. Подальші змінені статті пояснюють саме нашу інтеграцію.'),
'food_and_water':('Їжа, здоров’я та вода','Здоров’я залишається звичайним. Не потрібно підтримувати окремі білки, молочні продукти чи інші харчові групи TFC. Спрага та вплив температури належать Legendary Survival Overhaul.\n\nПсування їжі TFC і ланцюжки консервації вимкнені. Борошно й тісто TFC використовуються в доступних рецептах інших кухонних модів; шукай застосування через JEI.'),
'size_and_weight':('Розміри й стаки','Обмеження ваги TFC та зменшені стаки вимкнені. Злитки складаються звичайними стаками по 64. Температура предмета TFC при цьому зберігається: гарячі предмети без щипців можуть обпікати.'),
'decay':('Зберігання їжі','Псування їжі TFC вимкнене. Стара порада сушити, солити, маринувати чи консервувати їжу для зупинки цього таймера не потрібна. Використовуй доступні кулінарні рецепти збірки.'),
'jarring':('Консервація','Система псування й способи консервації TFC вимкнені. Їжа не потребує банок для зупинки псування. Поточні рецепти банок і кухонних виробів дивись у JEI.'),
'fishing':('Рибальство Starcatcher','Система рибальства TFC та звичайна ванільна вудка не є шляхом розвитку. Використовуй спорядження й мінігру Starcatcher. Рідний довідник Starcatcher видається командою /guides.'),
'powderkegs':('Порох і артилерія','Порохові бочки TFC вимкнені. Для вогнепальної зброї й артилерії використовуються рецепти збірки зі селітрою та сіркою TFC. Старий мод Sulfur Gunpowder вилучений. Точні пропорції, патрони й деталі зброї відкривай у JEI.'),
'support_beams':('Будівництво без обвалів','Обвал печер TFC вимкнений. Підпори можна використовувати в дозволених декоративних рецептах, але вони не потрібні для запобігання обвалу цієї системи.'),
'aqueducts':('Перенесення рідин','Трубну систему TFC вимкнено. Для доступних механічних систем використовуй Create та їхні поточні рецепти. Старі схеми TFC для труб і насосів не застосовуються.'),
'pumps':('Насоси й рідини','Насоси та дубльовану механіку TFC замінено інтеграцією збірки. Доступні засоби Create відкривай у JEI та Ponder.'),
'mechanical_power':('Механічна енергія Create','Водяні колеса й дубльовані механізми TFC не є джерелом обертання у збірці. Використовуй Create. Жорно TFC інтегроване з валами Create; схема з’єднання описана в Довіднику Empirecraft.'),
'crankshaft':('Приводи Create','Кривошипи та дубльовані приводи TFC вимкнені. Побудуй кінетичну мережу Create; поточні механізми й передачі переглядай у JEI та Ponder.'),
'quern':('Жорно й Create','Жорно TFC перемелює сумісні предмети та має інтеграцію з валом Create. Підготуй вхідний предмет і місце для результату. Повний порядок з’єднання та доступні рецепти відкривай в Довіднику Empirecraft і JEI.'),
'anvils':('Ковальство Empirecraft','Для ковальських деталей використовується нагрів TFC та ковадло Overgeared. Гарячий метал не перетворюється на окрему «гарячу версію» злитка. Підготуй щипці, молот і потрібну кількість нагрітих заготовок.\n\nСкладність обладунків перенесена на виготовлення окремих частин і поступове складання на нашому столі. Старі схеми кування на ковадлі TFC не є чинним маршрутом. Точні деталі й кількості показані у JEI.'),
'primitive_anvils':('Перші металеві вироби','Почни з гончарства, випалу форм, плавлення й лиття металів TFC. Подальші вироби й деталі обробляються через ковальську систему збірки та Overgeared. Старе кування зварних заготовок на ковадлі TFC замінене.'),
'armor':('Складання обладунків','Броню TFC вилучено. Виготовляй деталі спорядження зі встановлених модів і поступово складай їх на столі складання обладунків. Підказки стола пояснюють етапи та відсутні частини.\n\nЗахисні переваги й слабкості залежать від типу обладунку. Алмазні, золоті й незеритові комплекти Chinese Weapons, його Ancient Gem та старий стіл не використовуються.'),
'armor_trims':('Оформлення й обладунки','Використовуй лише доступні рецепти та дозволені матеріали збірки. Старі обладунки TFC й шлях покращення незеритовим шаблоном не є чинною системою складання.'),
'heating':('Температура предметів','Для всіх підтриманих металів використовується нагрів TFC. Коли метал достатньо нагрітий, працюй ним на ковадлі Overgeared. Без щипців гарячі предмети можуть обпікати, зокрема в інвентарі. Кидання гарячого предмета у воду охолоджує його.\n\nЦе температура предмета, а не температура тіла гравця: тіло й навколишнє середовище контролює LSO.'),
'steel':('Сталь у цій збірці','Сталь отримують металургійним шляхом із білим чавуном та флюсом у тиглі. Проміжний етап насиченої сталі прибрано. Додавання нового чавуну в робочу суміш не має зупиняти обробку флюсом.\n\nЧервона й синя сталь, чорна та вісмутова бронза вилучені. Для чорної сталі й подальших деталей звіряйся з поточним JEI та Довідником Empirecraft.'),
'primitive_alloys':('Доступні сплави','Збережено плавлення металів і дозволені сплави TFC, з канонічними злитками інших модів. Мідь, олово й бронза не повинні створювати другі паралельні набори матеріалів.\n\nВісмутову й чорну бронзу, червону й синю сталь вимкнено. Чинні пропорції доступних сплавів показані в JEI та Довіднику Empirecraft.'),
'crucible':('Тигель та автоматизація','Тигель приймає підтримані метали й сировину для плавлення та сплавів. Предмети можна подавати воронкою. Для сталі потрібен металургійний маршрут із флюсом; звичайне ванільне виплавлення злитків не обходить цей етап.\n\nБіля тигля можна поставити наш ливарний блок, установити форму злитка та накопичувати готові злитки на виході. Для андезитового сплаву існує шлях через розплавлений андезит.'),
'blast_furnace':('Доменна піч','Обробка руди має йти поступово. Паливо підтримує збільшений запас, до 16 на слот у відповідних частинах інтеграції. Для установки потрібна фурма; її актуальний рецепт відкривай в JEI.\n\nПодальше отримання сталі відбувається через тигель і флюс. Старі етапи насиченої, червоної та синьої сталі не застосовуються.'),
'charcoal_forge':('Ковальський нагрів','Горно нагріває матеріали для подальшого ковальства. Дозволено до 16 вугілля на паливний слот. Готовність визначає температура TFC; користуйся щипцями для роботи з гарячим металом.'),
'fire_clay':('Вогнетривка кераміка','Вогнетривкі вироби доступні через змінені рецепти збірки. Графіт не потрібен як окрема обов’язкова ресурсна гілка. Для каоліну, суміші та випалу звіряйся з поточним рецептом JEI.'),
'animal_husbandry':('Тваринництво DragN’s','Основні свійські тварини належать DragN’s Livestock Overhaul. До них адаптовано поведінку, розмноження та сумісні механіки TFC; не шукай для цього ванільні моделі корів чи курей.\n\nРідний довідник DragN’s описує його види й інструменти. Довідник Empirecraft пояснює спільні правила саме збірки.'),
'climate':('Клімат і календар','Використовуй календар та сезони, налаштовані для цієї збірки. Власні календар, сезони й температура тіла TFC вимкнені. Для сільського господарства залишаються узгоджені сезонні вимоги культур. Дикі рослини доступні від весни до осені.'),
'bread':('Борошно, тісто та кухня','Борошно й тісто різних культур TFC використовуються в рецептах інших харчових модів. Дублікати тіста Farmer’s Delight і Create прибрано. За допомогою JEI перевір, які зернові заміни приймає конкретна страва.'),
}
def main():
 base=REPO/'handbook/dist/empirecraft-handbook-1.0.6.jar'
 with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 changed=[];native={}
 tfc_bytes=read_base('mods/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.3.jar','a1f82645ecbad06c5ae70b9db2b4c5e39375d63ee9485e31ae04df273e279c04')
 base_sha=hashlib.sha256(tfc_bytes).hexdigest()
 with zipfile.ZipFile(io.BytesIO(tfc_bytes)) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 locales=sorted({n.split('/')[4] for n in original if n.startswith('assets/tfc/patchouli_books/field_guide/')})
 for pattern,book in [('TerraFirmaCraft*.jar','field_guide')]:
  with zipfile.ZipFile(io.BytesIO(tfc_bytes)) as z:
   for name in z.namelist():
    if f'/patchouli_books/{book}/en_us/entries/' not in name or not name.endswith('.json'):continue
    key=Path(name).stem
    if key not in CHAPTERS:continue
    title,text=CHAPTERS[key];entry=json.loads(z.read(name));entry.update(name=title,icon='minecraft:book',pages=pages(text,260));entry.pop('extra_recipe_mappings',None);entry.pop('advancement',None);entry['read_by_default']=True
    anchors=[p['anchor'] for p in json.loads(z.read(name)).get('pages',[]) if 'anchor' in p]
    for i,anchor in enumerate(anchors):
     if i>=len(entry['pages']):entry['pages'].append({'type':'patchouli:text','text':'Чинні рецепти та докладні інструкції дивись у Довіднику Empirecraft і JEI.'})
     entry['pages'][i]['anchor']=anchor
    fit({}, {key:entry})
    for page in entry['pages']:
     if page['type']=='empirecraft_guidebook:wrapped_text':page['type']='tfc:wrapped_text'
     assert lines(page.get('text',''))<=13
    for lang in locales:
     native[name.replace('/en_us/','/'+lang+'/')]=(json.dumps(entry,ensure_ascii=False,indent=2)+'\n').encode()
     prefix=name.split('/entries/')[0].replace('/en_us','/'+lang)
     native[prefix+'/templates/wrapped_text.json']=(json.dumps({'components':[{'type':'patchouli:text','x':0,'y':0,'max_width':112,'text':'#text'}]})+'\n').encode()
    changed.append(name)
 # Forge's merged mod resource pack does not reliably override another mod's
 # namespace. Put reviewed native pages in the owning TFC jar; executable code,
 # protocol version, credits, and every unrelated resource stay byte-identical.
 target=R/'dist/empirecraft-handbook-1.0.6.jar';temporary=target.with_name(target.name+'.tmp');shutil.copy2(base,temporary);temporary.replace(target);target.with_suffix('.jar.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
 original.update(native);tfc_out=R/'dist/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar';jar(tfc_out,original)
 (R/'guides-audit.json').write_text(json.dumps({'updated_native_tfc_entries':changed,'locales':locales,'updated_resource_count':len(native),'base_tfc_sha256':base_sha,'tfc_sha256':hashlib.sha256(tfc_out.read_bytes()).hexdigest(),'handbook_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'executable_code_changed':False,'reviewed_books':['Empirecraft','TFC','DragNs Livestock','Chinese Weapons (already integrated native notes)','Starcatcher','Siegeworks (use current JEI for pack recipes)']},indent=2)+'\n')
 print('GUIDE_CURATE_PASS entries='+str(len(changed)))
if __name__=='__main__':main()
