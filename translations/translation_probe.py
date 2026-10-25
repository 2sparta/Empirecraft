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
