"""Keep vanilla tag overrides usable when optional mod registrations are absent."""


def optional_mod_reference(value):
    identity = value if isinstance(value, str) else value['id']
    location = identity.removeprefix('#')
    namespace = location.split(':', 1)[0] if ':' in location else 'minecraft'
    if namespace == 'minecraft':
        return value
    return dict(value, required=False) if isinstance(value, dict) else {'id': identity, 'required': False}
