"""Refuse a client export when shared Forge mods differ from its server snapshot."""
import hashlib
import io
from pathlib import PurePosixPath
import tomllib
from urllib.parse import unquote
import zipfile
from tools.channel import git


def index_mods(jars):
    result = {}
    for path, body in jars:
        with zipfile.ZipFile(io.BytesIO(body)) as archive:
            if 'META-INF/mods.toml' not in archive.namelist():
                continue
            metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode('utf-8-sig'))
        digest = hashlib.sha256(body).hexdigest()
        for mod in metadata.get('mods', []):
            mod_id = mod['modId']
            if mod_id in result:
                raise ValueError('Duplicate Forge mod ID: '+mod_id)
            result[mod_id] = {'file': PurePosixPath(path).name, 'sha256': digest,
                              'version': mod.get('version', '')}
    return result


def compare_shared(client, server):
    shared = sorted(client.keys() & server.keys())
    mismatches = [mod for mod in shared if client[mod]['sha256'] != server[mod]['sha256']]
    if mismatches:
        details = ', '.join(mod+' ('+client[mod]['file']+' vs '+server[mod]['file']+')' for mod in mismatches)
        raise ValueError('Client/server JAR mismatch: '+details)
    return {mod: client[mod] for mod in shared}


def verify_server_parity(manifest, repository, ref):
    server_tree = git('ls-tree', '-r', '-z', ref, 'server/mods', repo=repository)
    def server_jars():
        for entry in server_tree.split(b'\0'):
            if not entry: continue
            meta, path = entry.split(b'\t', 1)
            name = path.decode()
            if name.endswith('.jar'):
                mode, kind, blob = meta.decode().split()
                if mode != '100644' or kind != 'blob':
                    raise ValueError('Unexpected server JAR input: '+name)
                yield name, git('cat-file', 'blob', blob, repo=repository)
    server = index_mods(server_jars())
    def client_jars():
        for item in manifest['profiles']['balanced']['files']:
            if item['path'].endswith('.jar'):
                source = unquote(item['url'].split(manifest['commit']+'/', 1)[1])
                yield item['path'], git('show', manifest['commit']+':'+source, repo=repository)
    shared = compare_shared(index_mods(client_jars()), server)
    return {'server_snapshot_present': bool(server), 'shared_mod_count': len(shared), 'shared_mods': shared}
