#!/usr/bin/env python3
"""Generates the 3D product models for the PoC (no third-party assets).

PoC simplification: placeholder geometry. A product line uses its own GLB/USDZ models
with the same `print_area` material and UV rule.

Writes for each product:
  products/<id>/model.glb   glTF 2.0 binary (Android, SceneView/Filament)
  products/<id>/model.obj   Wavefront OBJ + model.mtl (iOS, SceneKit via ModelIO)

Every model has a material named "print_area". Its UV map covers [0,1] x [0,1]
and maps 1:1 onto the CE.SDK design page: u = left to right, v = top to bottom
(glTF convention). The OBJ stores v flipped (OBJ convention, origin bottom left).

Units: meters. Pure Python, no dependencies.
"""

import json
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "products")


# ---------------------------------------------------------------------------
# Mesh helpers
# ---------------------------------------------------------------------------

class Primitive:
    def __init__(self, material):
        self.material = material
        self.pos = []
        self.nrm = []
        self.uv = []
        self.idx = []

    def add_grid(self, fn, ns, nt):
        """fn(s, t) -> (position, normal, uv) for s, t in [0, 1].

        The winding of each triangle is set from the given normals, so the
        caller does not have to care about the orientation of (s, t).
        """
        base = len(self.pos)
        for j in range(nt + 1):
            for i in range(ns + 1):
                p, n, uv = fn(i / ns, j / nt)
                self.pos.append(p)
                self.nrm.append(normalize(n))
                self.uv.append(uv)
        row = ns + 1
        for j in range(nt):
            for i in range(ns):
                a = base + j * row + i
                b = a + 1
                c = a + row + 1
                d = a + row
                self._tri(a, b, c)
                self._tri(a, c, d)

    def _tri(self, a, b, c):
        pa, pb, pc = self.pos[a], self.pos[b], self.pos[c]
        cr = cross(sub(pb, pa), sub(pc, pa))
        if length(cr) < 1e-12:
            return
        n = add(add(self.nrm[a], self.nrm[b]), self.nrm[c])
        if dot(cr, n) < 0:
            self.idx += [a, c, b]
        else:
            self.idx += [a, b, c]


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def scale(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def normalize(a):
    l = length(a)
    return (0.0, 1.0, 0.0) if l < 1e-12 else scale(a, 1.0 / l)


def lerp(a, b, t):
    return a + (b - a) * t


# ---------------------------------------------------------------------------
# Mug: 11 oz, 82 mm diameter, 95 mm high, print area 8.5 x 3.5 in (216 x 89 mm)
# ---------------------------------------------------------------------------

def build_mug():
    R = 0.041          # outer radius
    T = 0.004          # wall thickness
    Ri = R - T
    H = 0.095          # height
    B = 0.007          # bottom thickness
    print_w = 0.2159   # 8.5 in
    print_h = 0.0889   # 3.5 in
    circ = 2 * math.pi * R
    span = 2 * math.pi * print_w / circ
    gap = 2 * math.pi - span
    handle_phi = math.pi / 2               # handle points to +X
    phi0 = handle_phi + gap / 2            # print starts right after the handle
    phi1 = phi0 + span
    yb0 = (H - print_h) / 2
    yb1 = yb0 + print_h

    def cyl(radius, phi, y, inward=False):
        n = (math.sin(phi), 0.0, math.cos(phi))
        p = (radius * math.sin(phi), y, radius * math.cos(phi))
        return p, (scale(n, -1) if inward else n)

    printp = Primitive("print_area")
    ceramic = Primitive("ceramic")

    # Print band (outer wall). phi grows to the viewer's right, so u grows with phi.
    def band(s, t):
        phi = lerp(phi0, phi1, s)
        y = lerp(yb1, yb0, t)             # t = 0 at top edge of the page
        p, n = cyl(R, phi, y)
        return p, n, (s, t)
    printp.add_grid(band, 160, 2)

    def outer(pa, pb, ya, yb, ns):
        def f(s, t):
            p, n = cyl(R, lerp(pa, pb, s), lerp(ya, yb, t))
            return p, n, (0.0, 0.0)
        ceramic.add_grid(f, ns, 1)

    outer(phi0, phi1, 0.0, yb0, 160)                 # below the band
    outer(phi0, phi1, yb1, H, 160)                   # above the band
    outer(phi1, phi0 + 2 * math.pi, 0.0, H, 32)      # behind the handle

    # Top rim, rounded a little: half torus between Ri and R.
    rr = T / 2
    rc = Ri + rr

    def rim(s, t):
        phi = 2 * math.pi * s
        a = math.pi * t                    # 0 = outer side, pi = inner side
        radial = rc + rr * math.cos(a)
        y = H + rr * math.sin(a)
        p = (radial * math.sin(phi), y, radial * math.cos(phi))
        n = (math.cos(a) * math.sin(phi), math.sin(a), math.cos(a) * math.cos(phi))
        return p, n, (0.0, 0.0)
    ceramic.add_grid(rim, 192, 8)

    # Inner wall and inner bottom.
    def inner(s, t):
        p, n = cyl(Ri, 2 * math.pi * s, lerp(B, H, t), inward=True)
        return p, n, (0.0, 0.0)
    ceramic.add_grid(inner, 192, 2)

    def disk(radius, y, up):
        def f(s, t):
            phi = 2 * math.pi * s
            r = radius * t
            return (r * math.sin(phi), y, r * math.cos(phi)), (0.0, 1.0 if up else -1.0, 0.0), (0.0, 0.0)
        ceramic.add_grid(f, 96, 1)
    disk(Ri, B, True)
    disk(R, 0.0, False)

    # Handle: tube along an elliptic arc in the XY plane, ends inside the wall.
    cx, cy = R - 0.002, H / 2 + 0.002
    rx, ry = 0.027, 0.032
    ta, tb = 0.0055, 0.0075                # tube half sizes (in plane, depth)
    th_max = math.radians(95)

    def handle(s, t):
        th = lerp(-th_max, th_max, s)
        c = (cx + rx * math.cos(th), cy + ry * math.sin(th), 0.0)
        tan = normalize((-rx * math.sin(th), ry * math.cos(th), 0.0))
        n1 = (tan[1], -tan[0], 0.0)        # in-plane, away from the arc center
        n2 = (0.0, 0.0, 1.0)
        psi = 2 * math.pi * t
        p = add(c, add(scale(n1, ta * math.cos(psi)), scale(n2, tb * math.sin(psi))))
        n = add(scale(n1, math.cos(psi) / ta), scale(n2, math.sin(psi) / tb))
        return p, n, (0.0, 0.0)
    ceramic.add_grid(handle, 64, 32)

    return [printp, ceramic], {
        "print_area": {"color": (1.0, 1.0, 1.0), "roughness": 0.25, "textured": True},
        "ceramic": {"color": (0.96, 0.96, 0.95), "roughness": 0.25, "textured": False},
    }


# ---------------------------------------------------------------------------
# Framed photo: 8 x 10 in photo (portrait), 30 mm wooden frame, 20 mm deep
# ---------------------------------------------------------------------------

def build_frame():
    pw, ph = 0.2032, 0.254   # 8 x 10 in
    fw = 0.03                # frame width
    fd = 0.02                # frame depth
    recess = 0.006           # photo sits behind the frame front
    photo = Primitive("print_area")
    frame = Primitive("frame")

    def quad(prim, p0, p1, p3, n, uv=False):
        # p0 = top-left, p1 = top-right, p3 = bottom-left
        def f(s, t):
            p = add(p0, add(scale(sub(p1, p0), s), scale(sub(p3, p0), t)))
            return p, n, ((s, t) if uv else (0.0, 0.0))
        prim.add_grid(f, 1, 1)

    hx, hy = pw / 2, ph / 2
    zf = fd / 2              # front face of the frame
    zp = zf - recess         # photo plane
    quad(photo, (-hx, hy, zp), (hx, hy, zp), (-hx, -hy, zp), (0, 0, 1), uv=True)

    ox, oy = hx + fw, hy + fw
    zb = -fd / 2
    # Front faces of the four frame bars.
    quad(frame, (-ox, oy, zf), (ox, oy, zf), (-ox, hy, zf), (0, 0, 1))
    quad(frame, (-ox, -hy, zf), (ox, -hy, zf), (-ox, -oy, zf), (0, 0, 1))
    quad(frame, (-ox, hy, zf), (-hx, hy, zf), (-ox, -hy, zf), (0, 0, 1))
    quad(frame, (hx, hy, zf), (ox, hy, zf), (hx, -hy, zf), (0, 0, 1))
    # Outer sides.
    quad(frame, (-ox, oy, zb), (ox, oy, zb), (-ox, oy, zf), (0, 1, 0))
    quad(frame, (-ox, -oy, zf), (ox, -oy, zf), (-ox, -oy, zb), (0, -1, 0))
    quad(frame, (-ox, oy, zb), (-ox, oy, zf), (-ox, -oy, zb), (-1, 0, 0))
    quad(frame, (ox, oy, zf), (ox, oy, zb), (ox, -oy, zf), (1, 0, 0))
    # Inner sides (the recess around the photo).
    quad(frame, (-hx, hy, zf), (hx, hy, zf), (-hx, hy, zp), (0, -1, 0))
    quad(frame, (-hx, -hy, zp), (hx, -hy, zp), (-hx, -hy, zf), (0, 1, 0))
    quad(frame, (-hx, hy, zp), (-hx, hy, zf), (-hx, -hy, zp), (1, 0, 0))
    quad(frame, (hx, hy, zf), (hx, hy, zp), (hx, -hy, zf), (-1, 0, 0))
    # Back.
    quad(frame, (ox, oy, zb), (-ox, oy, zb), (ox, -oy, zb), (0, 0, -1))

    return [photo, frame], {
        "print_area": {"color": (1.0, 1.0, 1.0), "roughness": 0.5, "textured": True},
        "frame": {"color": (0.12, 0.09, 0.07), "roughness": 0.55, "textured": False},
    }


# ---------------------------------------------------------------------------
# Writers
# ---------------------------------------------------------------------------

def white_png(size=4):
    raw = b"".join(b"\x00" + b"\xff\xff\xff" * size for _ in range(size))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def write_glb(path, name, prims, mats):
    bin_data = bytearray()
    views, accessors = [], []

    def push(data, target=None):
        while len(bin_data) % 4:
            bin_data.append(0)
        off = len(bin_data)
        bin_data.extend(data)
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target:
            v["target"] = target
        views.append(v)
        return len(views) - 1

    mat_names = list(mats.keys())
    gl_mats, images, textures = [], [], []
    for mname in mat_names:
        m = mats[mname]
        pbr = {"baseColorFactor": [*m["color"], 1.0], "metallicFactor": 0.0, "roughnessFactor": m["roughness"]}
        if m["textured"]:
            images.append({"bufferView": push(white_png()), "mimeType": "image/png"})
            textures.append({"source": len(images) - 1, "sampler": 0})
            pbr["baseColorTexture"] = {"index": len(textures) - 1}
        gl_mats.append({"name": mname, "pbrMetallicRoughness": pbr})

    gl_prims = []
    for p in prims:
        vp = push(b"".join(struct.pack("<3f", *v) for v in p.pos), 34962)
        mins = [min(v[i] for v in p.pos) for i in range(3)]
        maxs = [max(v[i] for v in p.pos) for i in range(3)]
        accessors.append({"bufferView": vp, "componentType": 5126, "count": len(p.pos), "type": "VEC3", "min": mins, "max": maxs})
        a_pos = len(accessors) - 1
        vn = push(b"".join(struct.pack("<3f", *v) for v in p.nrm), 34962)
        accessors.append({"bufferView": vn, "componentType": 5126, "count": len(p.nrm), "type": "VEC3"})
        a_nrm = len(accessors) - 1
        vt = push(b"".join(struct.pack("<2f", *v) for v in p.uv), 34962)
        accessors.append({"bufferView": vt, "componentType": 5126, "count": len(p.uv), "type": "VEC2"})
        a_uv = len(accessors) - 1
        vi = push(b"".join(struct.pack("<I", i) for i in p.idx), 34963)
        accessors.append({"bufferView": vi, "componentType": 5125, "count": len(p.idx), "type": "SCALAR"})
        a_idx = len(accessors) - 1
        gl_prims.append({"attributes": {"POSITION": a_pos, "NORMAL": a_nrm, "TEXCOORD_0": a_uv},
                         "indices": a_idx, "material": mat_names.index(p.material)})

    gltf = {
        "asset": {"version": "2.0", "generator": "gen_models.py (3d-product-preview PoC)"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": name, "mesh": 0}],
        "meshes": [{"name": name, "primitives": gl_prims}],
        "materials": gl_mats,
        "samplers": [{"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071}],
        "buffers": [{"byteLength": len(bin_data)}],
        "bufferViews": views,
        "accessors": accessors,
    }
    if images:
        gltf["images"] = images
        gltf["textures"] = textures
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    while len(bin_data) % 4:
        bin_data.append(0)
    total = 12 + 8 + len(js) + 8 + len(bin_data)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        f.write(struct.pack("<II", len(bin_data), 0x004E4942) + bytes(bin_data))


def write_obj(path, prims, mats):
    mtl_name = os.path.splitext(os.path.basename(path))[0] + ".mtl"
    lines = ["# generated by gen_models.py", f"mtllib {mtl_name}"]
    off = 1
    for p in prims:
        lines.append(f"o {p.material}")
        lines.append(f"usemtl {p.material}")
        lines += [f"v {x:.6f} {y:.6f} {z:.6f}" for x, y, z in p.pos]
        lines += [f"vn {x:.6f} {y:.6f} {z:.6f}" for x, y, z in p.nrm]
        lines += [f"vt {u:.6f} {1.0 - v:.6f}" for u, v in p.uv]
        for k in range(0, len(p.idx), 3):
            a, b, c = (i + off for i in p.idx[k:k + 3])
            lines.append(f"f {a}/{a}/{a} {b}/{b}/{b} {c}/{c}/{c}")
        off += len(p.pos)
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    mtl = []
    for mname, m in mats.items():
        r, g, b = m["color"]
        mtl += [f"newmtl {mname}", f"Kd {r:.3f} {g:.3f} {b:.3f}", "Ks 0.200 0.200 0.200", "Ns 60", "d 1.0", ""]
    with open(os.path.join(os.path.dirname(path), mtl_name), "w") as f:
        f.write("\n".join(mtl))


def main():
    for pid, builder in (("mug", build_mug), ("frame", build_frame)):
        prims, mats = builder()
        out = os.path.join(ROOT, pid)
        os.makedirs(out, exist_ok=True)
        write_glb(os.path.join(out, "model.glb"), pid, prims, mats)
        write_obj(os.path.join(out, "model.obj"), prims, mats)
        tris = sum(len(p.idx) // 3 for p in prims)
        print(f"{pid}: {tris} triangles -> {out}")


if __name__ == "__main__":
    main()
