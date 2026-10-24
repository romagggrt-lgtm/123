#!/usr/bin/env python3
"""Add one original tapered gold bar to the unmodified 1.05 base, without SmpLore.

Minecraft 1.21.11 supports Euler-rotated item model elements. Fourteen planar
faces form one beveled frustum. Trapezoids are clipped in their texture atlas,
so there is no stack of cuboids and no staircase along the sloping sides.
"""
from pathlib import Path
import hashlib
import io
import json
import math
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from merge_smplore_pack import read_pack, check_references

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'dist/Z_Server_Pack_1.05.zip'
OUT = ROOT / 'dist/Z_Server_Pack_1.05_Gold.zip'
TILE, SIZE = 256, 1024


def basis(pitch, yaw):
    x, y = np.radians([pitch, yaw])
    return np.array([[math.cos(y), math.sin(y) * math.sin(x), math.sin(y) * math.cos(x)],
                     [0, math.cos(x), -math.sin(x)],
                     [-math.sin(y), math.cos(y) * math.sin(x), math.cos(y) * math.cos(x)]])


def build_bar():
    # Y, half length, half width. Small bottom/top bevels surround the sloped body.
    rings = [(4, 13.6, 6.6), (4.25, 14, 7), (11.65, 12.1, 5.1), (12, 11.65, 4.65)]
    faces = []
    for y, x, z in (rings[0], rings[-1]):
        faces.append(np.array([[-x, y, -z], [x, y, -z], [x, y, z], [-x, y, z]], dtype=float))
    for lo, hi in zip(rings, rings[1:]):
        y0, x0, z0 = lo; y1, x1, z1 = hi
        faces.extend([
            np.array([[-x0, y0, -z0], [x0, y0, -z0], [x1, y1, -z1], [-x1, y1, -z1]]),
            np.array([[-x0, y0, z0], [x0, y0, z0], [x1, y1, z1], [-x1, y1, z1]]),
            np.array([[-x0, y0, -z0], [-x0, y0, z0], [-x1, y1, z1], [-x1, y1, -z1]]),
            np.array([[x0, y0, -z0], [x0, y0, z0], [x1, y1, z1], [x1, y1, -z1]])])
    atlas = Image.new('RGBA', (SIZE, SIZE))
    elements = []
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 88)
    for i, vertices in enumerate(faces):
        center = vertices.mean(axis=0)
        normal = np.cross(vertices[1] - vertices[0], vertices[3] - vertices[0])
        normal = normal / np.linalg.norm(normal)
        if np.dot(normal, center - [0, 8, 0]) < 0: normal *= -1
        pitch = math.degrees(-math.asin(np.clip(normal[1], -1, 1)))
        yaw = math.degrees(math.atan2(normal[0], normal[2]))
        rotation = basis(pitch, yaw)
        local = (vertices - center) @ rotation
        assert abs(local[:, 2]).max() < 1e-8
        low, high = local[:, :2].min(axis=0), local[:, :2].max(axis=0)
        # Leave a pixel gutter on each atlas tile to prevent mipmap seams.
        uv = (local[:, :2] - low) / (high - low)
        uv[:, 1] = 1 - uv[:, 1]
        polygon = [tuple(p) for p in 1 + uv * (TILE - 2)]
        mask = Image.new('L', (TILE, TILE)); ImageDraw.Draw(mask).polygon(polygon, fill=255)
        yy, xx = np.mgrid[:TILE, :TILE].astype(float) / (TILE - 1)
        rng = np.random.default_rng(4100 + i)
        grain = rng.normal(0, 1.35, (TILE, TILE))
        lit = float(np.dot(normal, np.array([-.35, .85, -.4])))
        base = np.array([235, 158, 32]) + np.array([17, 30, 20]) * lit
        broad = 26 * np.exp(-((xx - .23 - .15 * yy) / .2) ** 2)
        streak = 24 * np.exp(-((xx - .68 + .13 * yy) / .025) ** 2)
        falloff = 16 * (1 - yy) - 7 * xx
        if i == 1: base = np.array([255, 215, 95]); broad *= .6; streak *= .5
        if i >= 2 and (i < 6 or i >= 10): base = np.array([255, 224, 119]); broad *= .4
        colors = np.clip(base + (broad + streak + falloff + grain)[:, :, None] * [1, .9, .6], 0, 255).astype(np.uint8)
        tile = Image.fromarray(np.dstack([colors, np.array(mask)]), 'RGBA')
        # Edge reflections follow the clipped trapezoid, not its rectangular UV box.
        edge = Image.new('RGBA', (TILE, TILE)); d = ImageDraw.Draw(edge)
        d.line(polygon + [polygon[0]], fill=(255, 244, 179, 235), width=2)
        tile = Image.alpha_composite(tile, edge)
        tile.putalpha(mask)
        if i == 1:
            # Original engraved G with a dark recess and a narrow reflected lower edge.
            draw = ImageDraw.Draw(tile)
            bounds = draw.textbbox((0, 0), 'G', font=font)
            pos = ((TILE - (bounds[2] - bounds[0])) / 2, (TILE - (bounds[3] - bounds[1])) / 2 - bounds[1])
            draw.text((pos[0] + 1, pos[1] + 2), 'G', font=font, fill=(255, 244, 170, 255))
            draw.text((pos[0] - 1, pos[1] - 1), 'G', font=font, fill=(121, 79, 16, 255))
            draw.text(pos, 'G', font=font, fill=(166, 113, 22, 255))
        tx, ty = i % 4 * TILE, i // 4 * TILE
        atlas.paste(tile, (tx, ty))
        origin = center + [8, 0, 8]
        elements.append({
            'from': [origin[0] + low[0], origin[1] + low[1], origin[2]],
            'to': [origin[0] + high[0], origin[1] + high[1], origin[2]],
            'rotation': {'origin': origin.tolist(), 'x': pitch, 'y': yaw, 'z': 0},
            'shade': False,
            'faces': {'south': {'texture': '#gold', 'uv': [(tx + 1) * 16 / SIZE, (ty + 1) * 16 / SIZE,
                                                        (tx + TILE - 1) * 16 / SIZE, (ty + TILE - 1) * 16 / SIZE]}}})
    # The mathematical surface is closed: every edge belongs to exactly two faces.
    edges = {}
    for vertices in faces:
        for a, b in zip(vertices, np.roll(vertices, -1, axis=0)):
            key = tuple(sorted((tuple(a), tuple(b)))); edges[key] = edges.get(key, 0) + 1
    assert set(edges.values()) == {2} and len(faces) == 14
    model = {'credit': 'Original single beveled gold ingot by Z_Server, CC0-1.0. Minecraft Java 1.21.11.',
             'ambientocclusion': False, 'textures': {'gold': 'z_gold:item/golda', 'particle': 'z_gold:item/gold_particle'},
             'elements': elements,
             'display': {'gui': {'rotation': [30, 35, 0], 'scale': [.58, .58, .58]},
                         'ground': {'scale': [.35, .35, .35]},
                         'firstperson_righthand': {'rotation': [0, 15, -12], 'scale': [.45, .45, .45]},
                         'firstperson_lefthand': {'rotation': [0, -15, 12], 'scale': [.45, .45, .45]},
                         'thirdperson_righthand': {'rotation': [0, 0, 0], 'scale': [.4, .4, .4]},
                         'thirdperson_lefthand': {'rotation': [0, 0, 0], 'scale': [.4, .4, .4]}}}
    return model, atlas


def encoded(image):
    stream = io.BytesIO(); image.save(stream, 'PNG', optimize=True); return stream.getvalue()


def main():
    base = read_pack(BASE)
    assert not any(name.startswith('assets/smplore/') for name in base)
    files = dict(base)
    def write(name, value): files[name] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    model, atlas = build_bar()
    write('assets/z_gold/models/item/golda.json', model)
    files['assets/z_gold/textures/item/golda.png'] = encoded(atlas)
    files['assets/z_gold/textures/item/gold_particle.png'] = encoded(Image.new('RGBA', (16, 16), '#f4b62d'))
    # A distinct golden egg icon; the base Bukkit item is EGG, never a living mob's spawn egg.
    egg = Image.new('RGBA', (64, 64)); d = ImageDraw.Draw(egg)
    d.ellipse((11, 5, 53, 60), fill='#8b5414')
    d.ellipse((13, 5, 51, 58), fill='#ffcc36')
    d.ellipse((16, 9, 28, 32), fill='#fff0a1')
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 25)
    d.text((23, 26), 'G', font=font, fill='#a66d16', stroke_width=0)
    files['assets/z_gold/textures/item/koch_golda.png'] = encoded(egg)
    write('assets/z_gold/models/item/koch_golda.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'z_gold:item/koch_golda'}})
    for item in ('golda', 'koch_golda'):
        write(f'assets/z_gold/items/{item}.json', {'model': {'type': 'minecraft:model', 'model': 'z_gold:item/' + item}})
    write('pack.mcmeta', {'pack': {'min_format': [75, 0], 'max_format': [75, 0],
                                  'description': 'Z_Server_Pack 1.05 + Gold | Shahed + Verity + Z_Gold | 1.21.11'}})
    files['assets/z_gold/LICENSE.txt'] = b'Original Golda model, gold atlas and egg icon: CC0-1.0. Reference images were not embedded.\n'
    for name, data in base.items():
        if name != 'pack.mcmeta': assert files[name] == data, 'Base resource was changed: ' + name
    assert not any(name.startswith('assets/smplore/') for name in files)
    counts = check_references(files)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    assert read_pack(OUT) == files
    sha1 = hashlib.sha1(OUT.read_bytes()).hexdigest()
    OUT.with_suffix('.zip.sha1').write_text(sha1 + '\n')
    OUT.with_suffix('.zip.sha256').write_text(hashlib.sha256(OUT.read_bytes()).hexdigest() + '\n')
    (ROOT / 'gold/src/main/resources/pack.sha1').write_text(sha1 + '\n')
    report = {'pack': OUT.name, 'base': BASE.name, 'base_sha256': hashlib.sha256(BASE.read_bytes()).hexdigest(),
              'base_files_preserved': len(base) - 1, 'shahed_verity_assets_unchanged': True, 'smplore_assets': 0,
              'gold_bar_count': 1, 'gold_bar_faces': 14, 'closed_surface_edges': True,
              'validated': counts, 'sha1': sha1, 'size': OUT.stat().st_size, 'minecraft_client_tested': False}
    OUT.with_suffix('.report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
