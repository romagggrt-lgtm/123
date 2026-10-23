#!/usr/bin/env python3
"""Render the actual exported JSON/UV/PNG, with a z-buffer (not an ideal sphere).

This is an offline geometry preview, not a screenshot from a Minecraft client.
Requires numpy and Pillow only for QA; neither is required by the plugin.
"""
import io
import json
import math
import zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'dist/Z_Server_Pack_1.05.zip'


def quads(model):
    for element in model['elements']:
        a, b = element['from'], element['to']
        points = np.array([[a[0], b[1], b[2]], [a[0], a[1], b[2]],
                           [b[0], a[1], b[2]], [b[0], b[1], b[2]]], dtype=float)
        rotation = element['rotation']
        x, y, z = np.radians([rotation['x'], rotation['y'], rotation['z']])
        rx = np.array([[1, 0, 0], [0, np.cos(x), -np.sin(x)], [0, np.sin(x), np.cos(x)]])
        ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
        rz = np.array([[np.cos(z), -np.sin(z), 0], [np.sin(z), np.cos(z), 0], [0, 0, 1]])
        origin = np.array(rotation['origin'])
        points = (points - origin) @ (rz @ ry @ rx).T + origin - 8
        u0, v0, u1, v1 = np.array(element['faces']['south']['uv']) / 16
        yield points, np.array([[u0, v0], [u0, v1], [u1, v1], [u1, v0]])


def render(mesh, texture, yaw, pitch=0, size=400):
    yaw, pitch = np.radians([yaw, pitch])
    eye = np.array([np.sin(yaw) * np.cos(pitch), np.sin(pitch), -np.cos(yaw) * np.cos(pitch)])
    right = np.cross(eye, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, eye)
    camera = np.array([right, up, eye])
    scale = size / 17.5
    output = np.full((size, size, 3), (148, 183, 231), dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    for points, uv in mesh:
        normal = np.cross(points[1] - points[0], points[2] - points[0])
        if np.dot(normal, eye) <= 0:
            continue
        p = points @ camera.T
        p[:, 0] = size / 2 + p[:, 0] * scale
        p[:, 1] = size / 2 - p[:, 1] * scale
        for indices in ((0, 1, 2), (0, 2, 3)):
            tri, tuv = p[list(indices)], uv[list(indices)]
            xmin, ymin = np.maximum(0, np.floor(tri[:, :2].min(axis=0))).astype(int)
            xmax, ymax = np.minimum(size - 1, np.ceil(tri[:, :2].max(axis=0))).astype(int)
            if xmin > xmax or ymin > ymax:
                continue
            yy, xx = np.mgrid[ymin:ymax + 1, xmin:xmax + 1].astype(float)
            xx += .5; yy += .5
            x0, y0 = tri[0, :2]; x1, y1 = tri[1, :2]; x2, y2 = tri[2, :2]
            den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(den) < 1e-10:
                continue
            w0 = ((y1 - y2) * (xx - x2) + (x2 - x1) * (yy - y2)) / den
            w1 = ((y2 - y0) * (xx - x2) + (x0 - x2) * (yy - y2)) / den
            w2 = 1 - w0 - w1
            zz = w0 * tri[0, 2] + w1 * tri[1, 2] + w2 * tri[2, 2]
            mask = (w0 >= -1e-8) & (w1 >= -1e-8) & (w2 >= -1e-8) & (zz > depth[ymin:ymax + 1, xmin:xmax + 1])
            uu = w0 * tuv[0, 0] + w1 * tuv[1, 0] + w2 * tuv[2, 0]
            vv = w0 * tuv[0, 1] + w1 * tuv[1, 1] + w2 * tuv[2, 1]
            tx = np.clip((uu * texture.shape[1]).astype(int), 0, texture.shape[1] - 1)
            ty = np.clip((vv * texture.shape[0]).astype(int), 0, texture.shape[0] - 1)
            output[ymin:ymax + 1, xmin:xmax + 1][mask] = texture[ty[mask], tx[mask], :3]
            depth[ymin:ymax + 1, xmin:xmax + 1][mask] = zz[mask]
    return Image.fromarray(output), depth


def main():
    with zipfile.ZipFile(PACK) as z:
        model = json.loads(z.read('assets/z_verity/models/item/verity_sphere.json'))
        texture = np.array(Image.open(io.BytesIO(z.read('assets/z_verity/textures/item/verity_skin.png'))))
    mesh = list(quads(model))
    radii = np.concatenate([np.linalg.norm(q, axis=1) for q, _ in mesh])
    assert radii.min() >= 7 - 1e-4 and radii.max() < 7.05, (radii.min(), radii.max())
    sheet = Image.new('RGB', (800, 852), '#e8edf4')
    for i, (label, yaw, pitch) in enumerate([('FRONT', 0, 0), ('SIDE', 90, 0), ('BACK', 180, 0), ('ANGLE', 35, 20)]):
        rendered, depth = render(mesh, texture, yaw, pitch)
        yy, xx = np.mgrid[:400, :400]
        circle = (xx + .5 - 200) ** 2 + (yy + .5 - 200) ** 2 < (7 * 400 / 17.5 - 1) ** 2
        assert np.all(np.isfinite(depth[circle])), f'hole in {label} surface'
        sheet.paste(rendered, ((i % 2) * 400, (i // 2) * 426 + 26))
        ImageDraw.Draw(sheet).text(((i % 2) * 400 + 12, (i // 2) * 426 + 7), label, fill='#202630')
    out = ROOT / 'dist/Verity-preview_1.0.2.png'
    sheet.save(out)
    print(f'Geometry radius: {radii.min():.5f}..{radii.max():.5f}, {len(mesh)} quads; four views have no holes.')
    print(out)


if __name__ == '__main__':
    main()
