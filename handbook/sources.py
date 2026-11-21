"""Reviewed inclusion list and exact data tables for the player handbook."""
import csv,hashlib,json,re,zipfile
from pathlib import Path

MANUALS=[
 ('survival-overhaul/README.md','survival',['Підшивання','Вода й напої']),
 ('layered-armor/INLINE-UA.md','combat',['Інвентар і ПКМ']),
 ('seasonal-calendar/README.md','time',['Місяці','Добовий цикл']),
 ('steel-lockpicking/README.md','building',['Як грати']),
 ('furniture-replacement/README.md','building',['Застосування']),
 ('create-posters/README-UK.md','building',['Створення плаката','Ракурси та масштаб']),
 ('sails/SMOOTH-SAILING.md','ships',['Вітер і розгін','Перекриття вітрил','Copycats']),
 ('sails/WATER-SEALING.md','ships',None),
 ('sails/WIND-STABILITY.md','ships',None),
 ('sails/SEA-MOTION.md','ships',None),
 ('sails/TOWNY-SAFETY.md','ships',None),
 ('naval/README.md','ships',['Створення та управління','Продаж, обмін і аукціон','Прямий обмін','Ринок торгової республіки','Мапа та останнє спостереження']),
 ('naval/FLEET-FIXES.md','ships',['Мирні кораблі','Мітки JourneyMap','Вибір корабля та гравця']),
 ('feudal/PROVISIONS.md','towns',['Витрати й обмеження території','Голод']),
 ('feudal/POLITICS.md','diplomacy',['Модель','Гілки розвитку','Основний порядок','Замок','Вотчини та пряма адміністрація','Вибори й бюджети','Союзи','Повне політичне об’єднання','Поетапне заснування конфедерації або федерації']),
 ('feudal/CITY-MARKETS.md','economy',['Відкриття ринку','Товари, отримання та тарифи','Валютна біржа й відомості','Роботи та постачання','Заставні кредити','Офіційний обмін','Книги та двосторонні торгові договори','Постійні міські закупівлі','Аукціони','Ярмарки, Contact і чат']),
 ('feudal/CURRENCY.md','economy',['Друк та повноваження','Погашення й золото до видачі','Казни']),
 ('feudal/CIVIC-EVENTS.md','leisure',['Дошка на базарі','Турнір і вступний внесок','Нагороди, залік і видача','Запрошення Contact']),
 ('water-settlements/README.uk.md','army',['Найм і людський ресурс','Ополчення']),
 ('towny-recruits/ARMY.md','army',['Керування через Towny','Бойова мораль','Відсталі й застряглі']),
 ('towny-recruits/TACTICS.md','army',['Керування','Марш','Шикування','Фронт, стрільба та дистанція','Відхід, маршрут і швидкість','Кіннота','Бомби Supplementaries','Рельєф і межі']),
 ('towny-recruits/CONTRACTS.md','army',['Постійний продаж полку','Найманці','Огляд і відновлення']),
 ('chat-channels/README.md','map',['Канали та приватність']),
]
TECH=re.compile(r'(?i)(?:\b(?:JAR|SHA[- ]?256|SDK|Mixin|ASM|javac|classpath|UUID|H2|API|NBTIo|Rhino|Forge|Mohist)\b|\.java\b|\.py\b|\.properties\b|\.cfg\b|\bconfig/|source_commit|server/mods|client/mods|production.world|графічн\w* клієнт|регресійн|вихідн\w* код|байткод|компіляц|компілю|тестов|SQL|запит\w* до баз|empirecraft\.[0-9])')
EXCLUDE=re.compile(r'(?i)(встанов|установ|збірк|збиран|збірц|перевірк|тест|вихідн|інсталяц|міграц|адміністр|відтвор|навантаження|історич|попередн.*випуск|previous|historical|install|test|source|build|verification|незавершен|unfinished|included optimization)')

def append_manuals(repo,categories,entries,pages,ns):
    provenance=[]
    for relative,category,wanted in MANUALS:
        path=repo/relative;source=path.read_text();selected=[]
        for section in re.split(r'(?m)(?=^#{1,3} )',source):
            lines=section.strip().splitlines()
            if not lines:continue
            title=re.sub(r'^#+\s*','',lines[0]);body='\n'.join(lines[1:])
            if wanted is not None and title not in wanted:continue
            if wanted is None and EXCLUDE.search(title):continue
            if wanted is None and re.search('[а-яА-ЯіїєґІЇЄҐ]',title) is None:continue
            if wanted is None and len(body)<100:continue
            # Keep game commands, examples, units and player-facing tables. Strip code fences and
            # technical paragraphs rather than teaching installation or internal data formats.
            body=re.sub(r'```[^\n]*\n', '',body).replace('```','')
            paragraphs=[p for p in re.split(r'\n\s*\n',body) if not TECH.search(p)]
            body='\n\n'.join(paragraphs)
            body=body.replace('NBT','властивості предмета').replace('TPS','швидкість роботи сервера').replace('ПКМ','ПКМ')
            output=pages(body)
            if not output:continue
            ident=category+'/manual_'+hashlib.sha256((relative+'#'+title).encode()).hexdigest()[:12]
            entries[ident]={'name':title,'icon':categories[category]['icon'],'category':ns+':'+category,'sortnum':len(entries),'read_by_default':True,'pages':output}
            selected.append(title)
        if wanted is not None:assert set(wanted)==set(selected),(relative,'Missing reviewed sections',set(wanted)-set(selected))
        provenance.append({'path':relative,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'sections':selected})
    return provenance

def append_data(repo,categories,entries,pages,ns):
    lang={}
    for jar in sorted((repo/'server/mods').glob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if re.fullmatch('assets/[^/]+/lang/uk_ua.json',name):
                    try:lang.update(json.loads(z.read(name)))
                    except (ValueError,UnicodeDecodeError):pass
    for path in (repo/'translations/pack/assets').glob('*/lang/uk_ua.json'):lang.update(json.loads(path.read_text()))
    def label(identifier):
        namespace,path=identifier.split(':',1);key=namespace+'.'+path.replace('/','.')
        return lang.get('item.'+key,lang.get('block.'+key,identifier))
    def add(category,key,title,icon,text):
        entries[category+'/'+key]={'name':title,'icon':icon,'category':ns+':'+category,'sortnum':len(entries),'read_by_default':True,'pages':pages(text)}
    climate={'any':'будь-який придатний природний клімат','temperate':'помірний клімат','warm':'теплий клімат','hot':'спекотний клімат','cold':'холодний клімат','wet':'вологе місце біля води'}
    plants=json.loads((repo/'seasonal-leaves/src/main/resources/vegetation-profiles.json').read_text())['renewal_plants']
    for row in plants:
        identifier=row['id'];title=label(identifier)
        detail='Збирай посадковий матеріал і зберігай частину для власних посівів. Сезон появи дикої рослини та сезон росту вирощуваної культури перевіряй окремо.'
        if 'jungle_grape_bush' in identifier:detail='Це ліана. Підготуй придатну бічну опору; відновлення прикріплює її до опори, а не ставить як окремий кущ.'
        if 'pineapple' in identifier:detail='Придатна основа: пісок, червоний пісок або груба земля. На звичайній траві природна рослина не приживається.'
        if 'mushroom_colony' in identifier:detail='Потрібний відповідний грибний ґрунт або придатний затінок. Для власної грибної ділянки підготуй умови до перенесення колонії.'
        if 'wild_rice' in identifier:detail='Потрібна мілка вода й вільне місце для верхньої частини. Перевір обидві частини рослини перед збиранням посадкового матеріалу.'
        if identifier in ['nirvana:wild_hemp','farmersrespite:wild_coffee_bush','rusticdelight:wild_coffee']:detail+=' Природне оселище рідкісне: приблизно один придатний чанк зі 128; повторна поява обмежена щонайменше сімома ігровими днями.'
        add('crops','plant_'+identifier.replace(':','_'),title,'minecraft:wheat_seeds',f'{title}. Природні умови: {climate[row["climate"]]}. Дика поява доступна навесні, влітку й восени.\n\n{detail}')
    rows=json.loads((repo/'fauna/src/main/resources/nearby-fauna.json').read_text())['entries']
    times={'day':'день','night':'ніч','dusk':'сутінки','any':'будь-який час доби'}
    for index,row in enumerate(rows):
        rates='; '.join(name+': '+str(value)+'%' for name,value in zip(['весна','літо','осінь','зима'],row['chances']))
        add('wildlife','species_'+str(index),row['name'],'minecraft:egg',f'{row["name"]}: {row["habitat"]}. Час: {times.get(row["time"],row["time"])}. Звичайна група додаткової появи: {row["min"]}–{row["max"]}.\n\nТабличний шанс сезонної додаткової спроби: {rates}. Для появи також потрібні відповідний біом, природні умови й вільний ліміт популяції; відсоток не гарантує появу при кожному огляді місцевості.')
    with (repo/'tfc-integration/minerals.csv').open(encoding='utf-8-sig') as f:
        for index,row in enumerate(list(csv.reader(f,delimiter=';'))[1:]):
            if not row:continue
            title,block,product,use=row
            add('minerals','mineral_'+str(index),title,product,f'{title}. Застосування: {use}.\n\nШукай відповідне родовище, добувай сировину та перевір потрібну форму матеріалу перед роботою. Результат або робочий матеріал: {label(product)}. Для порошку, сплаву чи обробленого самоцвіту вибери відповідний поточний рецепт.')
    decisions=json.loads((repo/'tfc-integration/decisions.json').read_text());canonical=decisions['canonical_metals']
    metal_names={'copper':'мідь','tin':'олово','zinc':'цинк','brass':'латунь','bronze':'бронза','bismuth':'вісмут','nickel':'нікель','silver':'срібло','gold':'золото','cast_iron':'чавун','wrought_iron':'залізо','steel':'сталь','bismuth_bronze':'вісмутова бронза','black_bronze':'чорна бронза','black_steel':'чорна сталь','weak_steel':'суміш для сталі','weak_red_steel':'суміш для червоної сталі','weak_blue_steel':'суміш для синьої сталі','red_steel':'червона сталь','blue_steel':'синя сталь','rose_gold':'рожеве золото','sterling_silver':'стерлінгове срібло'}
    tfc=next((repo/'server/mods').glob('TerraFirmaCraft*.jar'))
    with zipfile.ZipFile(tfc) as z:
        for name in sorted(z.namelist()):
            if not name.startswith('data/tfc/recipes/alloy/') or not name.endswith('.json'):continue
            spec=json.loads(z.read(name));metal=spec['result'].split(':')[1];
            if metal in {'bismuth_bronze','black_bronze','weak_steel','weak_red_steel','weak_blue_steel','red_steel','blue_steel'}:continue
            title=metal_names.get(metal,metal);rows=[]
            for value in spec['contents']:
                component=value['metal'].split(':')[1];rows.append(f'{metal_names.get(component,component)}: {value["min"]*100:g}–{value["max"]*100:g}%')
            output=canonical.get(metal,'tfc:metal/ingot/'+metal)
            if 'assets/tfc/models/item/metal/ingot/'+metal+'.json' not in z.namelist() and metal not in canonical:output='minecraft:iron_ingot'
            add('metals','alloy_'+metal,title.capitalize(),output,'Допустимі частки суміші:\n'+'\n'.join(rows)+'\n\nВимірюй усі компоненти в одиницях металу. Після додавання перевір сумарний об’єм, частки й назву результату в тиглі. На кожен звичайний злиток відкладай 100 одиниць готового металу. Для проміжного сталевого сплаву продовжуй відповідний тепловий маршрут.')
    # The integration's own shaped/shapeless recipes are guarded and tested after KubeJS reload.
    # Generic upstream firearm recipes are intentionally never imported into this handbook.
    integration=repo/'tfc-integration/build/libs/empirecraft-tfc-integration-1.0.3.jar'
    if not integration.exists():integration=next((repo/'server/mods').glob('empirecraft-tfc-integration-*.jar'))
    with zipfile.ZipFile(integration) as z:
        matrix=json.loads(z.read('empirecraft-tfc-casting-parity.json'))
        tools={'axe':'сокира','pickaxe':'кайло','hoe':'мотика','shovel':'лопата','hammer':'молот','sword':'лезо меча'}
        for row in matrix:
            title=label(row['output'])
            extra=f"Після застигання додай {row['extra_ingots']} відповідних злитків і молот." if row['tool']!='sword' else 'Після застигання дістань лезо Overgeared із форми.'
            add('pottery','cast_'+row['metal']+'_'+row['tool'],title,row['mold'],
                f"Деталь: {title}. Форма TFC: {tools[row['tool']]}. Налий {row['cast_units']} одиниць відповідного розплаву в готову випалену форму.\n\n{extra} Відполіруй деталь перед складанням спорядження. Повна витрата металу: {row['total_units']} одиниць; один звичайний злиток дорівнює 100 одиницям.")
        for name in sorted(z.namelist()):
            if not re.fullmatch(r'data/(empirecraft_tfc|tfc_muskets|musketmod|medieval_boomsticks)/recipes/.+\.json',name):continue
            spec=json.loads(z.read(name));kind=spec.get('type','');result=spec.get('result',{})
            item=result.get('item') if isinstance(result,dict) else None
            if not item:continue
            if any(metal in item for metal in ['bismuth_bronze','black_bronze','weak_steel','weak_red_steel','weak_blue_steel','red_steel','blue_steel']):continue
            identifier=name.split('/')[1]+':'+name.split('/recipes/')[1][:-5]
            category='guns' if kind=='overgeared:forging' or name.split('/')[1] in ['tfc_muskets','musketmod','medieval_boomsticks'] else 'machines'
            if kind in ['minecraft:crafting_shaped','minecraft:crafting_shapeless']:
                entry={'name':label(item),'icon':item,'category':ns+':'+category,'read_by_default':True,'sortnum':len(entries),'pages':[{'type':'patchouli:crafting','recipe':identifier},{'type':'patchouli:text','text':'Рецепт використовує матеріали саме поточної інтеграції. Підготуй точний тип заготовки й потрібну кількість. Використання результату переглядай у JEI.'}]}
                entries[category+'/recipe_'+identifier.split(':')[1]]=entry
            elif kind=='overgeared:forging':
                parts=[]
                for symbol,value in spec.get('key',{}).items():
                    ing=value.get('item',value.get('tag',''));parts.append(symbol+' — '+(label(ing) if ':' in ing and 'item' in value else ing))
                text='Ковадло Overgeared; креслення «Вогнепальна зброя». Підготуй гарячу заготовку, молот і матеріали. Потрібна обробка: '+str(spec.get('hammering',5))+' ударів.\n\nСхема:\n'+'\n'.join(spec.get('pattern',[]))+'\n\n'+'\n'.join(parts)+'\n\nПісля ковальської обробки перевір якість і забери готовий компонент. Його подальшу збірку відкрий у JEI.'
                add(category,'forge_'+identifier.split(':')[1],label(item),item,text)
