"""Download hash-pinned official client/mapping fixtures for bytecode tests only."""
import concurrent.futures
import hashlib
import urllib.request
import zipfile
from build import ROOT

FILES = {
    'client.jar': ('https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar', 'sha1', '0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838'),
    'client.txt': ('https://piston-data.mojang.com/v1/objects/6c48521eed01fe2e8ecdadbd5ae348415f3c47da/client.txt', 'sha1', '6c48521eed01fe2e8ecdadbd5ae348415f3c47da'),
    'mcp.zip': ('https://maven.neoforged.net/releases/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip', 'sha256', 'f46d1050d8bed9046886b90a0d2fb80b4c6b5120fc35d32e5d37498dbe0a6d2a'),
}


def prepare():
    folder = ROOT/'build/client-inputs'
    folder.mkdir(parents=True, exist_ok=True)
    def fetch(entry):
        name, (url, algorithm, expected) = entry
        path = folder/name
        if path.exists() and hashlib.new(algorithm, path.read_bytes()).hexdigest() == expected: return
        with urllib.request.urlopen(url, timeout=90) as response: data = response.read()
        assert hashlib.new(algorithm, data).hexdigest() == expected, 'Wrong client fixture: '+name
        path.write_bytes(data)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(fetch, FILES.items()))
    with zipfile.ZipFile(folder/'mcp.zip') as archive:
        (folder/'joined.tsrg').write_bytes(archive.read('config/joined.tsrg'))
    return folder
