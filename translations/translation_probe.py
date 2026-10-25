import json
import urllib.request
import urllib.parse

params = urllib.parse.urlencode({'client': 'gtx', 'sl': 'en', 'tl': 'uk', 'dt': 't', 'q': 'Acacia Rabbit Hutch\nRight click to open the catalogue.'})
url = 'https://translate.googleapis.com/translate_a/single?' + params
try:
    response = urllib.request.urlopen(url, timeout=30)
    print('TRANSLATION_PROBE', response.status, response.read().decode())
except Exception as error:
    print('TRANSLATION_PROBE_ERROR', str(error))

from bootstrap import translate_batch
import time
started = time.monotonic()
try:
    print('BATCH_PROBE', json.dumps(translate_batch(['Welcome %1$s! You have %2$d items.', '$(bold)Animal guide$()$(br2)Right click with minecraft:wheat to feed this animal.', '§cWooden block\nSecond line', 'Ancient Debris Bricks Slab'], 'uk_ua'), ensure_ascii=False), flush=True)
except Exception as error:
    print('BATCH_PROBE_ERROR', repr(error), flush=True)
print('BATCH_SECONDS', time.monotonic() - started, flush=True)
