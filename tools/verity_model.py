"""Round Verity surface for the vanilla 1.21.11 multi-axis element format.

The six faces of a subdivided cube are projected onto a sphere. Small tangent
quads cover those cells with sub-pixel overlaps, instead of stacking cuboids.
The atlas is sampled in world coordinates, so there is exactly one face.
"""
import math

RADIUS = 7.0
SUBDIVISIONS = 24
TILE = 16
ATLAS_SIZE = 1024


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def unit(v):
    length = math.sqrt(dot(v, v))
    return tuple(x / length for x in v)


def basis(x_degrees, y_degrees):
    """Columns of Minecraft's Euler XYZ element rotation (Rz * Ry * Rx)."""
    x, y = math.radians(x_degrees), math.radians(y_degrees)
    cx, sx, cy, sy = math.cos(x), math.sin(x), math.cos(y), math.sin(y)
    return ((cy, 0, -sy), (sy * sx, cx, cy * sx), (sy * cx, -sx, cy * cx))


def skin(p):
    n = unit(p)
    # One simple, friendly face on the front (-Z) hemisphere.
    px, py, pz = (RADIUS * v for v in n)
    eye = ((px - 1.55) / .4) ** 2 + ((py - 1.9) / 1.0) ** 2 < 1
    eye |= ((px + 1.55) / .4) ** 2 + ((py - 1.9) / 1.0) ** 2 < 1
    smile = abs(py - (-2.7 + .055 * px * px)) < .105 and abs(px) < 4.3
    smile |= abs(abs(px) - 4.3) < .075 and abs(py + 1.68) < .22
    if pz < -3 and (eye or smile):
        return (25, 18, 5, 255)
    # Bake continuous lighting, avoiding axis-based flat-cube face shading.
    brightness = .54 + .46 * (n[1] + 1) / 2
    return (round(255 * brightness), round(235 * brightness), 5, 255)


def build_sphere():
    elements = []
    pixels = bytearray(ATLAS_SIZE * ATLAS_SIZE * 4)
    columns = ATLAS_SIZE // TILE
    for axis in range(3):
        others = [i for i in range(3) if i != axis]
        for sign in (-1, 1):
            for row in range(SUBDIVISIONS):
                for col in range(SUBDIVISIONS):
                    lo = [-1 + 2 * col / SUBDIVISIONS, -1 + 2 * row / SUBDIVISIONS]
                    hi = [v + 2 / SUBDIVISIONS for v in lo]

                    def project(u, v):
                        p = [0., 0., 0.]
                        p[axis] = sign
                        p[others[0]], p[others[1]] = u, v
                        return unit(p)

                    normal = project((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2)
                    # Rz*Ry*Rx: rotate local +Z onto the radial normal.
                    pitch = math.degrees(-math.asin(normal[1]))
                    yaw = math.degrees(math.atan2(normal[0], normal[2]))
                    tangent_x, tangent_y, _ = basis(pitch, yaw)
                    corners = [project(u, v) for u in (lo[0], hi[0]) for v in (lo[1], hi[1])]
                    xs = [RADIUS * dot(p, tangent_x) / dot(p, normal) for p in corners]
                    ys = [RADIUS * dot(p, tangent_y) / dot(p, normal) for p in corners]
                    x0, x1 = min(xs) - .01, max(xs) + .01
                    y0, y1 = min(ys) - .01, max(ys) + .01
                    center = [8 + RADIUS * v for v in normal]
                    tile_x, tile_y = len(elements) % columns * TILE, len(elements) // columns * TILE
                    # SOUTH UV increases local X and decreases local Y.
                    for iy in range(TILE):
                        v = (iy - 1) / (TILE - 2)
                        py = y1 + (y0 - y1) * v
                        for ix in range(TILE):
                            u = (ix - 1) / (TILE - 2)
                            px = x0 + (x1 - x0) * u
                            p = [RADIUS * normal[k] + px * tangent_x[k] + py * tangent_y[k] for k in range(3)]
                            offset = ((tile_y + iy) * ATLAS_SIZE + tile_x + ix) * 4
                            pixels[offset:offset + 4] = bytes(skin(p))
                    uv_scale = 16 / ATLAS_SIZE
                    elements.append({
                        'from': [center[0] + x0, center[1] + y0, center[2]],
                        'to': [center[0] + x1, center[1] + y1, center[2]],
                        'rotation': {'origin': center, 'x': pitch, 'y': yaw, 'z': 0},
                        'shade': False,
                        'faces': {'south': {'texture': '#skin', 'uv': [
                            (tile_x + 1) * uv_scale, (tile_y + 1) * uv_scale,
                            (tile_x + TILE - 1) * uv_scale, (tile_y + TILE - 1) * uv_scale]}}
                    })
    model = {
        'credit': 'Original round Verity surface, Euler XYZ elements for Minecraft 1.21.11.',
        'ambientocclusion': False,
        'textures': {'skin': 'z_verity:item/verity_skin', 'particle': 'z_verity:item/verity_yellow'},
        'elements': elements,
        'display': {
            'gui': {'rotation': [0, 180, 0], 'scale': [.8, .8, .8]},
            'ground': {'scale': [.6, .6, .6]},
            'firstperson_righthand': {'rotation': [0, 180, 0], 'scale': [.45, .45, .45]},
            'firstperson_lefthand': {'rotation': [0, 180, 0], 'scale': [.45, .45, .45]},
            'thirdperson_righthand': {'rotation': [0, 180, 0], 'scale': [.5, .5, .5]},
            'thirdperson_lefthand': {'rotation': [0, 180, 0], 'scale': [.5, .5, .5]},
        },
    }
    return model, pixels
