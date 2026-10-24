#!/usr/bin/env python3
"""Add the user-provided SmpLore resource pack, preserving existing resources.

No plugin JAR is read, built or modified. Run after tools/build_pack.py when
regenerating the underlying Shahed + Verity resource pack.
"""
from pathlib import Path, PurePosixPath
import hashlib
import io
import json
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'dist/Z_Server_Pack_1.05.zip'
ADDON = ROOT / 'assets-src/SmpLore-Paper-1.4.0-beta.3.3-resources.zip'
OUTPUT = ROOT / 'dist/Z_Server_Pack_1.06.zip'


def read_pack(path):
    result = {}
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f'Corrupt archive: {path}'
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            assert not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts
            assert '\\' not in name, f'Nonstandard resource path: {name}'
            assert name not in result, f'Duplicate ZIP entry: {name}'
            result[name] = archive.read(entry)
    return result


def resource_file(identifier, directory, suffix):
    ns, path = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
    return None if ns == 'minecraft' else f'assets/{ns}/{directory}/{path}{suffix}'


def check_references(files):
    json_files = {name: json.loads(data) for name, data in files.items()
                  if name.endswith('.json') or name == 'pack.mcmeta'}
    counts = {'json': len(json_files), 'models': 0, 'textures': 0, 'sounds': 0, 'items': 0}

    def require(identifier, directory, suffix):
        name = resource_file(identifier, directory, suffix)
        assert name is None or name in files, f'Missing reference: {identifier} -> {name}'

    def model_refs(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'model' and isinstance(child, str):
                    require(child, 'models', '.json')
                model_refs(child)
        elif isinstance(value, list):
            for child in value:
                model_refs(child)

    for name, value in json_files.items():
        if '/models/' in name:
            counts['models'] += 1
            if value.get('parent'):
                require(value['parent'], 'models', '.json')
            for texture in value.get('textures', {}).values():
                if not texture.startswith('#'):
                    require(texture, 'textures', '.png')
        if '/items/' in name or '/blockstates/' in name:
            model_refs(value)
            if '/items/' in name:
                counts['items'] += 1
        if name.endswith('/sounds.json'):
            namespace = name.split('/')[1]
            for event in value.values():
                for sound in event.get('sounds', []):
                    sound_name = sound if isinstance(sound, str) else sound['name']
                    kind = 'file' if isinstance(sound, str) else sound.get('type', 'file')
                    if kind == 'file':
                        require(sound_name if ':' in sound_name else namespace + ':' + sound_name, 'sounds', '.ogg')
    for name, data in files.items():
        if name.endswith('.png'):
            with Image.open(io.BytesIO(data)) as png:
                png.verify()
            counts['textures'] += 1
        elif name.endswith('.ogg'):
            assert data.startswith(b'OggS'), name
            counts['sounds'] += 1
    return counts


def main():
    base, addon = read_pack(BASE), read_pack(ADDON)
    files = dict(base)
    for name, data in addon.items():
        if name == 'pack.mcmeta':
            continue
        if name in files and files[name] != data:
            raise ValueError(f'Resource collision; refusing to overwrite {name}')
        files[name] = data
    files['pack.mcmeta'] = (json.dumps({'pack': {
        'min_format': [75, 0], 'max_format': [75, 0],
        'description': 'Z_Server_Pack 1.06 | Shahed + Verity + SmpLore | 1.21.11'
    }}, ensure_ascii=False, indent=2) + '\n').encode()
    files['MERGED_PACKS.txt'] = (
        'Z_Server_Pack 1.06 for Minecraft Java 1.21.11\n'
        'Base: Z_Server_Pack_1.05.zip (Shahed + Z_Verity).\n'
        'Addition: user-provided SmpLore-Paper-1.4.0-beta.3.3-resources.zip.\n'
        'All original asset bytes and supplied license notices are preserved.\n'
        'SmpLore assets retain their original ownership; see assets/smplore/licenses.\n'
        'No plugin JARs or executable libraries are included.\n'
    ).encode()
    for name, data in base.items():
        if name != 'pack.mcmeta':
            assert files[name] == data, f'Existing resource changed: {name}'
    for name, data in addon.items():
        if name != 'pack.mcmeta':
            assert files[name] == data, f'SmpLore resource changed: {name}'
    counts = check_references(files)
    with zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    assert read_pack(OUTPUT) == files
    sha1 = hashlib.sha1(OUTPUT.read_bytes()).hexdigest()
    sha256 = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    OUTPUT.with_suffix('.zip.sha1').write_text(sha1 + '\n')
    OUTPUT.with_suffix('.zip.sha256').write_text(sha256 + '\n')
    report = {
        'pack': OUTPUT.name,
        'minecraft_version': '1.21.11',
        'sources': {BASE.name: hashlib.sha256(BASE.read_bytes()).hexdigest(),
                    ADDON.name: hashlib.sha256(ADDON.read_bytes()).hexdigest()},
        'files': len(files),
        'unchanged_existing_files': len(base) - 1,
        'copied_smplore_files': len(addon) - 1,
        'conflicting_asset_paths': [],
        'validated': counts,
        'sha1': sha1, 'sha256': sha256,
        'minecraft_client_tested': False,
        'plugin_jars_modified': False,
    }
    OUTPUT.with_suffix('.report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
