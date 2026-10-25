"""Reviewed Russian material names and texture descriptors for Chipped."""
import re

MATERIALS = dict(line.split('=', 1) for line in '''amethyst_block=Аметистовый блок
ancient_debris=Древние обломки
andesite=Андезит
basalt=Базальт
blackstone=Чернит
blue_ice=Синий лёд
bricks=Кирпичи
borderless_bricks=Кирпичи без швов
calcite=Кальцит
clay=Глина
coal_block=Угольный блок
cobblestone=Булыжник
crying_obsidian=Плачущий обсидиан
dark_prismarine=Тёмный призмарин
deepslate=Глубинный сланец
diamond_block=Алмазный блок
diorite=Диорит
dirt=Земля
dripstone_block=Блок капельника
emerald_block=Изумрудный блок
end_stone=Эндерняк
gilded_blackstone=Золочёный чернит
glowstone=Светокамень
gold_block=Золотой блок
granite=Гранит
gravel=Гравий
ice=Лёд
iron_block=Железный блок
lapis_block=Лазуритовый блок
lodestone=Магнетит
magma_block=Магма
melon=Арбуз
moss_block=Мох
mossy_cobblestone=Замшелый булыжник
mossy_stone_bricks=Замшелые каменные кирпичи
mud=Грязь
mud_bricks=Саманные кирпичи
mushroom_stem=Ножка гриба
nether_bricks=Незерские кирпичи
netherrack=Незерак
netherite_block=Незеритовый блок
nether_wart_block=Блок незерского нароста
warped_wart_block=Блок искажённого нароста
obsidian=Обсидиан
packed_ice=Плотный лёд
packed_mud=Плотная грязь
prismarine=Призмарин
purpur_block=Пурпур
quartz_block=Кварцевый блок
raw_copper_block=Блок рудной меди
raw_gold_block=Блок рудного золота
raw_iron_block=Блок рудного железа
red_mushroom_block=Красный грибной блок
brown_mushroom_block=Коричневый грибной блок
red_nether_bricks=Красные незерские кирпичи
red_sand=Красный песок
red_sandstone=Красный песчаник
redstone_block=Редстоуновый блок
sand=Песок
soul_sand=Песок душ
sandstone=Песчаник
sea_lantern=Морской фонарь
shroomlight=Грибосвет
smooth_stone=Гладкий камень
snow_block=Снег
stone=Камень
stone_bricks=Каменные кирпичи
tuff=Туф
ochre_froglight=Охристая квампа
pearlescent_froglight=Перламутровая квампа
verdant_froglight=Зелёная квампа
copper_block=Медный блок
exposed_copper=Потемневшая медь
weathered_copper=Состаренная медь
oxidized_copper=Окисленная медь
pumpkin=Тыква
jack_o_lantern=Светильник Джека
ladder=Лестница
hay_block=Сноп сена
bone_block=Костный блок
dried_kelp_block=Блок сушёной ламинарии
sponge=Губка
wet_sponge=Мокрая губка
cobweb=Паутина
vine=Лианы
iron_bars=Железные прутья
glass=Стекло
glass_pane=Стеклянная панель
leaded_glass=Витраж
leaded_glass_pane=Витражная панель
lantern=Фонарь
soul_lantern=Фонарь душ
redstone_torch=Редстоуновый факел
lily_pad=Кувшинка'''.splitlines())

STYLES = dict(line.split('=', 1) for line in '''tiled=плиточная облицовка
pillar=колонна
bricks=кирпичная кладка
polished=полировка
pillar_top=верхушка колонны
inscribed=надписи
engraved=гравировка
inlayed=инкрустация
thick_inlayed=широкая инкрустация
tiles=плитка
circular=круглый узор
scales=чешуйчатый узор
angry=сердитое лицо
bordered=рамка
brick_bordered=кирпичная рамка
carved=резьба
creeper_carving=резьба с крипером
curly_pillar=витая колонна
cut_blank=чистая обработанная поверхность
duh=удивлённое лицо
fine_pillar=тонкая колонна
glad=радостное лицо
ornate_pillar=узорчатая колонна
overlapping_tiles=перекрывающаяся плитка
sad=грустное лицо
simple_pillar=простая колонна
smoothed_double_inlayed=сглаженная двойная инкрустация
spider_carving=резьба с пауком
tiled_column=плиточная колонна
tiled_bordered=плиточная рамка
unamused=недовольное лицо
vertical_cut=вертикальные надрезы
crying=плачущее лицо
prismal_remnants=призматические обломки
reinforced=усиленная отделка
smooth_ringed=гладкие кольца
rough=грубая поверхность
blank_carving=заготовка для резьбы
cut_column=тёсаная колонна
fancy=изящная отделка
runic_carved=руническая резьба
sanded=шлифованная поверхность
tiny_brick_bordered=мелкая кирпичная рамка
tiny_layered_slabs=мелкие слоистые плиты
mini_tiles=мелкая плитка
checkered_tiles=шахматная плитка
cobbled=булыжная поверхность
cornered=угловой узор
cracked_flat_tiles=потрескавшаяся плоская плитка
flat_tiles=плоская плитка
loded=магнитный узор
smooth_column=гладкая колонна
smooth_inlayed=гладкая инкрустация
spiraled=спиральный узор
trodden=утоптанная поверхность
weathered=выветренная поверхность
woven=плетёный узор
eroded=размытая поверхность
wickered=лозовое плетение
bundled=связка
edged_bricks=кирпичи с обводкой
etched_bricks=кирпичи с травлением
massive_bricks=массивная кирпичная кладка
small_bricks=малая кирпичная кладка
stacked_bricks=штабель кирпичей
tiny_bricks=мелкая кирпичная кладка
tiny_layered_bricks=мелкая многослойная кладка
vertical_disordered_bricks=неровная вертикальная кладка
cracked_disordered_bricks=потрескавшаяся неровная кладка
pillar_bricks=колонная кладка
rounded_bricks=закруглённая кладка
cracked_bricks=потрескавшаяся кладка
offset_bricks=кладка со смещением
smooth=гладкая поверхность
layed_bricks=горизонтальная кладка
small=малый узор
soft=мягкая поверхность
stacked=штабель
cubed=кубический узор
ornate=орнамент
patched=заплаты
scaled=чешуя
arched_pillar=арочная колонна
barky=кора
blocky=блочный узор
crafted=ремесленная отделка
cross_haired=перекрестие
decorated=украшения
harsh_quilted=грубый стёганый узор
hexagonical=шестиугольный узор
knitted=вязаный узор
large_diamond=крупные ромбы
raster=растровый узор
rectangle=прямоугольный узор
ribbled=рёбра
square=квадратный узор
starry=звёздный узор
stringed=нитяной узор
tripped=переплетение
vertical_stiped=вертикальные полосы
windmill=мельничный узор
woved=тканый узор
overgrown=заросшая поверхность
railed=рейки
panel=панель
crossed=крестовой узор
mosaic=мозаика
pegged=крепление штифтами
barred=решётка
heavy=тяжёлая отделка
layered=слоистая поверхность
chiseled=резная отделка
windowed=окошко
nailed=гвозди
column=столб
cut=тёсаная поверхность
pillar_sides=боковая сторона колонны
smooth_tiles=гладкая плитка
tile=одиночная плитка
bolted=болты
creeper=узор с крипером
curled=завитки
grid=сетка
grill=решётчатая отделка
hazard=знак опасности
minimized=уменьшенный узор
paved=мощёная поверхность
rusted=ржавчина
shrunken=сжатый узор
small_tiles=малая плитка
stop=знак остановки
striped=полосы
triangular=треугольный узор
warning=предупреждающий знак
waved=волнистый узор
wired=проволока
pressed=прессованная поверхность
glass=стеклянная отделка
diamond=ромбы
leafy=лиственный узор
detailed=детальная отделка
fine=тонкая отделка
framed=обрамление
thin=тонкий узор
vertical=вертикальная отделка
shavings=стружка
pointed_dripstone=заострённый капельник
basket_woven=корзинное плетение
boxed=ящичная отделка
brick_bond=кирпичная перевязка
bricky=кирпичная текстура
checkered=шахматный узор
classic_windowed=классическое окошко
cobweb=узор с паутиной
crated=дощатый ящик
cross_laced=перекрёстное плетение
diagonal=диагональный узор
double_herringbone=двойная ёлочка
dual_paneled=двойная панель
enclosed=закрытая отделка
fine_vertical=тонкий вертикальный узор
fortified=укреплённая отделка
gated=створки
golden_barred=золотая решётка
herringbone=ёлочка
hewn=грубая обработка
iron_barred=железная решётка
laced=шнуровка
meshed=сетчатая отделка
natural=естественная поверхность
pointless=без острых углов
secret=тайник
shifted=сдвинутый узор
slanted=наклонный узор
slotted=прорези
solid=сплошная отделка
suspicious=подозрительный узор
tile_windowed=плиточное окошко
twisted=скрученная отделка
ad_astra=«К звёздам»
airy=воздушный узор
ancient=древний узор
ancient_cubed=древний кубический узор
ancient_vertical=древний вертикальный узор
carved_stripped=резьба на обтёсанной поверхности
stripped=обтёсанная поверхность'''.splitlines())


def gender(adjective, form):
    if adjective == 'Синий':
        return adjective[:-2] + {'m': 'ий', 'f': 'яя', 'n': 'ее', 'p': 'ие'}[form]
    if adjective.endswith('ий'):
        return adjective[:-2] + {'m': 'ий', 'f': 'ая', 'n': 'ое', 'p': 'ие'}[form]
    return adjective[:-2] + {'m': adjective[-2:], 'f': 'ая', 'n': 'ое', 'p': 'ые'}[form]


for wood, adjective in {
    'acacia': 'Акациевый', 'bamboo': 'Бамбуковый', 'birch': 'Берёзовый',
    'cherry': 'Вишнёвый', 'crimson': 'Багровый', 'dark_oak': 'Тёмно-дубовый',
    'jungle': 'Тропический', 'mangrove': 'Мангровый', 'oak': 'Дубовый',
    'spruce': 'Еловый', 'warped': 'Искажённый',
}.items():
    for kind, (noun, form) in {
        'planks': ('доски', 'p'), 'log': ('бревно', 'n'), 'wood': ('древесина', 'f'),
        'barrel': ('бочка', 'f'), 'crate': ('ящик', 'm'), 'trapdoor': ('люк', 'm'),
        'door': ('дверь', 'f'), 'torch': ('факел', 'm'),
        'bookshelf': ('книжная полка', 'f'), 'leaves': ('листва', 'f'),
    }.items():
        MATERIALS[wood + '_' + kind] = gender(adjective, form) + ' ' + noun

for color, adjective in {
    'white': 'Белый', 'orange': 'Оранжевый', 'magenta': 'Пурпурный', 'light_blue': 'Голубой',
    'yellow': 'Жёлтый', 'lime': 'Лаймовый', 'pink': 'Розовый', 'gray': 'Серый',
    'light_gray': 'Светло-серый', 'cyan': 'Бирюзовый', 'purple': 'Фиолетовый',
    'blue': 'Синий', 'brown': 'Коричневый', 'green': 'Зелёный', 'red': 'Красный', 'black': 'Чёрный',
}.items():
    for kind, (noun, form) in {
        'carpet': ('ковёр', 'm'), 'wool': ('шерсть', 'f'), 'concrete': ('бетон', 'm'),
        'glazed_terracotta': ('глазурованная керамика', 'f'),
        'stained_glass': ('стекло', 'n'), 'stained_glass_pane': ('стеклянная панель', 'f'),
        'terracotta': ('керамика', 'f'),
    }.items():
        MATERIALS[color + '_' + kind] = gender(adjective, form) + ' ' + noun


def reviewed_names(english, translated):
    patterns = [(re.compile(r'(^|_)' + re.escape(base) + r'(_|$)'), name) for base, name in sorted(MATERIALS.items(), key=lambda pair: len(pair[0]), reverse=True)]
    changed = 0
    for key in english:
        if not key.startswith('block.chipped.'):
            continue
        identifier = key.split('.')[-1]
        for pattern, name in patterns:
            match = pattern.search(identifier)
            if match:
                style = (identifier[:match.start()] + ('_' if match.start() else '') + identifier[match.end():]).strip('_').replace('__', '_')
                if style in STYLES or not style:
                    translated[key] = name + (' — ' + STYLES[style] if style else '')
                    changed += 1
                break
    return changed
