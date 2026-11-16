import io
import zipfile
import pytest
from tools.pack_parity import index_mods, compare_shared


def jar(mod, version, patch=b''):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as z:
        z.writestr('META-INF/mods.toml', f'[[mods]]\nmodId="{mod}"\nversion="{version}"\n')
        z.writestr('implementation.class', patch)
    return output.getvalue()


def test_rejects_farmer_channel_version_skew_even_when_filenames_differ():
    client = index_mods([('FarmersDelight-1.2.11a.jar', jar('farmersdelight', '1.2.11a'))])
    server = index_mods([('FarmersDelight-1.3.4.jar', jar('farmersdelight', '1.3.4'))])
    with pytest.raises(ValueError, match='farmersdelight'):
        compare_shared(client, server)


def test_rejects_different_patches_with_identical_version_labels():
    client = index_mods([('client.jar', jar('shared', '1.0', b'old'))])
    server = index_mods([('server.jar', jar('shared', '1.0', b'new'))])
    with pytest.raises(ValueError, match='shared'):
        compare_shared(client, server)


def test_allows_side_specific_mods_and_requires_identical_shared_jar():
    body = jar('shared', '1.0')
    client = index_mods([('shared.jar', body), ('render.jar', jar('render', '2'))])
    server = index_mods([('shared.jar', body), ('admin.jar', jar('admin', '3'))])
    assert list(compare_shared(client, server)) == ['shared']


def test_rejects_duplicate_mod_id_before_export():
    body = jar('farmersdelight', '1.3.4')
    with pytest.raises(ValueError, match='Duplicate Forge mod ID'):
        index_mods([('a.jar', body), ('b.jar', body)])
