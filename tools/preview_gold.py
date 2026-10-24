#!/usr/bin/env python3
"""Render the exported Minecraft JSON and its real alpha/UV atlas, not an idealized bar.

This offline raster preview does not replace a Minecraft client test.
"""
from pathlib import Path
import io, json, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from preview_verity import quads

ROOT = Path(__file__).resolve().parents[1]


def render(mesh, texture, yaw, pitch, width=640, height=420):
    yaw, pitch = np.radians([yaw, pitch])
    eye = np.array([np.sin(yaw) * np.cos(pitch), np.sin(pitch), -np.cos(yaw) * np.cos(pitch)])
    right = np.cross([0, 1, 0], eye); right /= np.linalg.norm(right)
    up = np.cross(eye, right); camera = np.array([right, up, eye])
    yy, xx = np.mgrid[:height, :width]
    glow = np.exp(-((xx - width / 2) ** 2 / (width * .38) ** 2 + (yy - height / 2) ** 2 / (height * .46) ** 2))
    output = np.clip(np.array([15, 20, 33]) + glow[:, :, None] * [19, 24, 33], 0, 255).astype(np.uint8)
    depth = np.full((height, width), -np.inf)
    for points, uv in mesh:
        if np.dot(np.cross(points[1] - points[0], points[2] - points[0]), eye) <= 0: continue
        p = points @ camera.T; scale = width / 36
        p[:, 0] = width / 2 + p[:, 0] * scale; p[:, 1] = height / 2 - p[:, 1] * scale
        for ids in ((0, 1, 2), (0, 2, 3)):
            tri, tuv = p[list(ids)], uv[list(ids)]
            xmin, ymin = np.maximum(0, np.floor(tri[:, :2].min(axis=0))).astype(int)
            xmax, ymax = np.minimum([width - 1, height - 1], np.ceil(tri[:, :2].max(axis=0))).astype(int)
            if xmin > xmax or ymin > ymax: continue
            yy, xx = np.mgrid[ymin:ymax + 1, xmin:xmax + 1].astype(float); xx += .5; yy += .5
            x0, y0 = tri[0, :2]; x1, y1 = tri[1, :2]; x2, y2 = tri[2, :2]
            den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(den) < 1e-10: continue
            w0 = ((y1 - y2) * (xx - x2) + (x2 - x1) * (yy - y2)) / den
            w1 = ((y2 - y0) * (xx - x2) + (x0 - x2) * (yy - y2)) / den; w2 = 1 - w0 - w1
            zz = w0 * tri[0, 2] + w1 * tri[1, 2] + w2 * tri[2, 2]
            uu = w0 * tuv[0, 0] + w1 * tuv[1, 0] + w2 * tuv[2, 0]
            vv = w0 * tuv[0, 1] + w1 * tuv[1, 1] + w2 * tuv[2, 1]
            tx = np.clip((uu * texture.shape[1]).astype(int), 0, texture.shape[1] - 1)
            ty = np.clip((vv * texture.shape[0]).astype(int), 0, texture.shape[0] - 1)
            mask = (w0 >= -1e-8) & (w1 >= -1e-8) & (w2 >= -1e-8) & (zz > depth[ymin:ymax + 1, xmin:xmax + 1]) & (texture[ty, tx, 3] >= 128)
            output[ymin:ymax + 1, xmin:xmax + 1][mask] = texture[ty[mask], tx[mask], :3]
            depth[ymin:ymax + 1, xmin:xmax + 1][mask] = zz[mask]
    assert np.isfinite(depth).sum() > 12000, 'Model surface is missing'
    return Image.fromarray(output)


def main():
    with zipfile.ZipFile(ROOT / 'dist/Z_Server_Pack_1.05_Gold.zip') as z:
        model = json.loads(z.read('assets/z_gold/models/item/golda.json'))
        texture = np.array(Image.open(io.BytesIO(z.read('assets/z_gold/textures/item/golda.png'))))
    mesh = list(quads(model)); assert len(mesh) == 14
    sheet = Image.new('RGB', (1280, 908), '#0f1421')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    for i, (label, yaw, pitch) in enumerate([('ANGLE / engraved G', 38, 32), ('TOP', 0, 75), ('LONG SIDE', 0, 5), ('OPPOSITE ANGLE', 220, 30)]):
        at = (i % 2 * 640, i // 2 * 448 + 24)
        sheet.paste(render(mesh, texture, yaw, pitch), at)
        draw.text((at[0] + 20, at[1] - 20), label, fill='#eed599', font=font)
    draw.text((20, 886), 'Z_Gold 1.0.0 | actual resource-pack geometry / offline preview, not an in-game screenshot', fill='#8b97aa', font=font)
    out = ROOT / 'dist/Z_Gold-preview_1.0.0.png'; sheet.save(out)
    print(out)


if __name__ == '__main__': main()
