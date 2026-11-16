import copy
import pytest
from tools.verify_distribution import assert_same_contents


def pack():
    return {'profiles': {name: {'files': [{'path':'mods/farmersdelight.jar','size':12,'sha256':'current'}]}
                         for name in ('potato','balanced','high')}}


@pytest.mark.parametrize('change', ['old_hash', 'missing', 'extra'])
def test_refuses_a_stale_profile_even_if_others_are_current(change):
    source, published = pack(), pack()
    files = published['profiles']['potato']['files']
    if change == 'old_hash': files[0]['sha256'] = 'old'
    elif change == 'missing': files.clear()
    else: files.append({'path':'mods/old.jar','size':3,'sha256':'extra'})
    with pytest.raises(ValueError, match='Stale potato distribution'):
        assert_same_contents(source, published)


def test_content_verification_allows_different_repository_urls_and_commit_ids():
    source, public = pack(), copy.deepcopy(pack())
    source['commit'], public['commit'] = 'private', 'public'
    assert_same_contents(source, public)
