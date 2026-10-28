"""Resource pack generálása: egyedi textúrák + 3D modellek (kockákból) mind az 50 tárgyhoz.

Kimenet:
  resourcepack/                      a kicsomagolt pack
  dist/BlockEater-ResourcePack.zip   a kész pack
  src/main/resources/resourcepack.zip  a pluginba csomagolt másolat (beágyazott szerverhez)
  src/main/resources/models.yml      modellméretek (a pluginnak)
  docs/preview.png                   előnézet (3D renderelt ikonok)
"""
import json, math, os, random, shutil, sys, zipfile
import yaml
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
sys.path.insert(0, HERE)
import catalog

NS = "blockeater"
PACK_MAJOR, PACK_MINOR = 97, 1          # Minecraft 26.3 resource pack formátum

# ---------------------------------------------------------------- színek ----
TIERS = [  # (fő szín, ragyogó szín)
    ((150, 110, 62), (214, 170, 96)),    # 0 fa
    ((124, 124, 128), (176, 176, 182)),  # 1 kő
    ((200, 202, 208), (244, 244, 250)),  # 2 vas
    ((236, 190, 50), (255, 240, 140)),   # 3 arany
    ((60, 200, 214), (170, 255, 255)),   # 4 gyémánt
    ((40, 190, 90), (150, 255, 170)),    # 5 smaragd
    ((70, 36, 110), (190, 100, 255)),    # 6 obszidián
    ((96, 44, 44), (255, 100, 40)),      # 7 netherit
    ((42, 52, 150), (255, 120, 230)),    # 8 kozmikus
    ((20, 14, 30), (255, 50, 90)),       # 9 űr
]
ROLES = ["main", "dark", "light", "accent", "wood", "wood2", "black", "glass", "white", "red", "yellow"]


def mul(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def palette(tier):
    main, acc = TIERS[tier]
    return {
        "main": main, "dark": mul(main, 0.62), "light": mul(main, 1.28), "accent": acc,
        "wood": (112, 76, 42), "wood2": (142, 102, 58), "black": (34, 34, 40),
        "glass": (130, 196, 236), "white": (236, 236, 240), "red": (210, 40, 40),
        "yellow": (255, 232, 120),
    }


def make_texture(tier, seed):
    """32x32 textúra: 4x4 pixeles cellák, minden szerepnek egy cella, zajjal."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (32, 32))
    pal = palette(tier)
    px = img.load()
    for idx, role in enumerate(ROLES):
        cx, cy = idx % 8, idx // 8
        base = pal[role]
        for y in range(4):
            for x in range(4):
                f = 1.0 + rnd.uniform(-0.09, 0.09)
                if role == "accent":
                    f = 1.0 + 0.1 * math.sin((x + y) * 1.3) + rnd.uniform(-0.04, 0.04)
                px[cx * 4 + x, cy * 4 + y] = mul(base, f) + (255,)
    return img


def cell_uv(role):
    i = ROLES.index(role)
    cx, cy = i % 8, i // 8
    return [cx * 2, cy * 2, cx * 2 + 2, cy * 2 + 2]


def cell_rgb(tier, role):
    return palette(tier)[role]


# --------------------------------------------------------------- modellek ---
class Model:
    def __init__(self):
        self.els = []

    def box(self, x0, y0, z0, x1, y1, z1, role, top=None, rot=None, bottom=None):
        faces = {}
        for side in ("north", "east", "south", "west", "down"):
            faces[side] = {"uv": cell_uv(role if side != "down" else (bottom or role)), "texture": "#0"}
        faces["up"] = {"uv": cell_uv(top or role), "texture": "#0"}
        el = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces,
              "_role": role, "_top": top or role}
        if rot:
            el["rotation"] = {"angle": rot[0], "axis": rot[1], "origin": rot[2] if len(rot) > 2 else None}
        self.els.append(el)
        return el

    def bbox(self):
        xs, ys, zs = [], [], []
        for e in self.els:
            for c in corners(e):
                xs.append(c[0]); ys.append(c[1]); zs.append(c[2])
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def normalise(self, limit=15.0):
        """A modell középre (8,8,8) és belefér a 16-os kockába."""
        lo, hi = self.bbox()
        size = max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
        s = min(1.0, limit / size) if size > 0 else 1.0
        cx, cy, cz = [(lo[i] + hi[i]) / 2 for i in range(3)]

        def tf(p):
            return [8 + (p[0] - cx) * s, 8 + (p[1] - cy) * s, 8 + (p[2] - cz) * s]
        for e in self.els:
            a, b = tf(e["from"]), tf(e["to"])
            e["from"], e["to"] = [round(v, 3) for v in a], [round(v, 3) for v in b]
            if "rotation" in e:
                o = e["rotation"]["origin"] or [(e["from"][i] + e["to"][i]) / 2 for i in range(3)]
                if e["rotation"]["origin"] is not None:
                    o = tf(o)
                e["rotation"]["origin"] = [round(v, 3) for v in o]
        lo, hi = self.bbox()
        return {"half": round((hi[1] - lo[1]) / 2 / 16, 4), "width": round(max(hi[0] - lo[0], hi[2] - lo[2]) / 16, 4)}

    def to_json(self, textures):
        els = []
        for e in self.els:
            j = {k: v for k, v in e.items() if not k.startswith("_")}
            if "rotation" in j and j["rotation"].get("origin") is None:
                j["rotation"]["origin"] = [(j["from"][i] + j["to"][i]) / 2 for i in range(3)]
            els.append(j)
        return {
            "textures": textures,
            "elements": els,
            "display": DISPLAY,
        }


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 3, 1], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 3, 1], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [1, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [1, 2, 0], "scale": [0.45, 0.45, 0.45]},
}


def corners(e):
    f, t = e["from"], e["to"]
    pts = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    r = e.get("rotation")
    if r:
        o = r["origin"] or [(f[i] + t[i]) / 2 for i in range(3)]
        a = math.radians(r["angle"])
        out = []
        for p in pts:
            q = [p[i] - o[i] for i in range(3)]
            if r["axis"] == "x":
                q = [q[0], q[1] * math.cos(a) - q[2] * math.sin(a), q[1] * math.sin(a) + q[2] * math.cos(a)]
            elif r["axis"] == "y":
                q = [q[0] * math.cos(a) + q[2] * math.sin(a), q[1], -q[0] * math.sin(a) + q[2] * math.cos(a)]
            else:
                q = [q[0] * math.cos(a) - q[1] * math.sin(a), q[0] * math.sin(a) + q[1] * math.cos(a), q[2]]
            out.append([q[i] + o[i] for i in range(3)])
        return out
    return pts


# ---- tárgytípusok ----
def build_tool(num, tier):
    m = Model()
    var = num % 3
    m.box(7, 0, 7, 9, 11, 9, "wood", top="wood2")
    m.box(6.5, 0, 6.5, 9.5, 1.2, 9.5, "dark")
    m.box(6.5, 4, 6.5, 9.5, 5, 9.5, "dark")
    if var == 0:      # csákány
        m.box(2, 11, 6, 14, 13.5, 10, "main", top="light")
        m.box(0, 9.5, 6.5, 2.5, 12, 9.5, "dark")
        m.box(13.5, 9.5, 6.5, 16, 12, 9.5, "dark")
        m.box(6, 13.5, 6, 10, 15, 10, "light")
    elif var == 1:    # kalapács
        m.box(3, 10.5, 4, 13, 15.5, 12, "main", top="light")
        m.box(2, 11.5, 5, 3, 14.5, 11, "dark")
        m.box(13, 11.5, 5, 14, 14.5, 11, "dark")
        m.box(5, 12, 3.4, 11, 14, 4, "accent")
    else:             # fúró
        y = 11.0
        for w in (7, 6, 5, 4, 3, 2, 1):
            h = 1.8
            m.box(8 - w / 2, y, 8 - w / 2, 8 + w / 2, y + h, 8 + w / 2, "main" if int(y) % 2 else "light")
            y += h
    gems = tier // 2 + 1
    for k in range(gems):
        m.box(6.6, 1.5 + k * 1.8, 6.6, 9.4, 2.7 + k * 1.8, 9.4, "accent")
    if tier >= 4:
        for sx in (-1, 1):
            m.box(8 + sx * 6.2 - 0.8, 13.6, 7.2, 8 + sx * 6.2 + 0.8, 16, 8.8, "accent")
    if tier >= 7:
        for (ox, oz) in ((-3.2, 0), (3.2, 0), (0, -3.2), (0, 3.2)):
            m.box(7.2 + ox, 16.5, 7.2 + oz, 8.8 + ox, 18.5, 8.8 + oz, "accent", rot=(45, "y"))
    if tier >= 9:
        m.box(4, 17.5, 4, 12, 18.3, 12, "accent")
    return m


def build_helper(num, tier):
    m = Model()
    m.box(4, 0, 4, 12, 1.5, 12, "dark")
    m.box(5.5, -1.2, 5.5, 10.5, 0, 10.5, "accent")
    m.box(4, 1.5, 5, 12, 9, 11, "main", top="light")
    m.box(6, 4, 4.5, 10, 7.5, 5, "accent")
    m.box(2, 3, 6, 4, 8.5, 10, "dark")
    m.box(12, 3, 6, 14, 8.5, 10, "dark")
    m.box(1.5, 2, 6.5, 4, 3, 9.5, "light")
    m.box(12, 2, 6.5, 14.5, 3, 9.5, "light")
    m.box(5, 9, 5, 11, 14, 11, "light", top="main")
    m.box(6, 11, 4.4, 7.6, 12.6, 5, "accent")
    m.box(8.4, 11, 4.4, 10, 12.6, 5, "accent")
    m.box(6.5, 9.6, 4.6, 9.5, 10.3, 5, "black")
    m.box(7.5, 14, 7.5, 8.5, 16, 8.5, "dark")
    m.box(7, 16, 7, 9, 17.5, 9, "accent")
    if tier >= 2:
        m.box(3, 8.5, 5.5, 5, 10, 10.5, "main")
        m.box(11, 8.5, 5.5, 13, 10, 10.5, "main")
    if tier >= 3:
        m.box(7, 6, 11, 9, 11, 12.5, "dark")
        m.box(2, 11.5, 10.8, 14, 12.5, 11.8, "light")
    if tier >= 5:
        for sx in (-1, 1):
            m.box(8 + sx * 5 - 0.6, 7, 11, 8 + sx * 5 + 0.6, 15, 12, "accent", rot=(22.5 * sx * -1, "z"))
    if tier >= 7:
        m.box(3, 17, 3, 13, 17.8, 13, "accent")
    if tier >= 8:
        m.box(4.5, 18.4, 4.5, 11.5, 19.2, 11.5, "yellow")
    return m


def build_car(num, tier):
    m = Model()
    var = num % 3
    lift = 3.5 if var == 2 else 0
    wr = 4 if var != 2 else 7                      # kerékméret
    # kerekek
    for x0, x1 in ((0.5, 3), (13, 15.5)):
        for z0 in (1.5, 11.5):
            m.box(x0, 0, z0, x1, wr, z0 + wr, "black")
            hub = (x0 - 0.3, x1 + 0.3)[x0 > 5]
            m.box(min(hub, x0 + 1.2), wr / 2 - 1, z0 + wr / 2 - 1, max(hub, x1 - 1.2), wr / 2 + 1, z0 + wr / 2 + 1, "accent")
    # karosszéria
    m.box(3, 2 + lift, 0, 13, 5 + lift, 16, "main", top="light")
    m.box(3, 5 + lift, 0, 13, 6 + lift, 6, "main", top="light")
    if var != 1:      # zárt fülke
        m.box(4, 6 + lift, 6, 12, 10 + lift, 13, "glass")
        m.box(3.6, 10 + lift, 5.6, 12.4, 11 + lift, 13.4, "main", top="light")
    else:             # buggy: gördülő védőkeret
        for x in (4, 11.4):
            for z in (6.5, 12.5):
                m.box(x, 6 + lift, z, x + 0.6, 11 + lift, z + 0.6, "dark")
        m.box(4, 11 + lift, 6.5, 12, 11.6 + lift, 7.1, "dark")
        m.box(4, 11 + lift, 12.5, 12, 11.6 + lift, 13.1, "dark")
        m.box(6, 6 + lift, 9, 10, 8 + lift, 11, "wood")
    # lámpák
    m.box(3.5, 3 + lift, -0.5, 5.7, 4.6 + lift, 0, "yellow")
    m.box(10.3, 3 + lift, -0.5, 12.5, 4.6 + lift, 0, "yellow")
    m.box(3.5, 3.4 + lift, 16, 5.7, 4.6 + lift, 16.5, "red")
    m.box(10.3, 3.4 + lift, 16, 12.5, 4.6 + lift, 16.5, "red")
    m.box(6.5, 2.6 + lift, -0.4, 9.5, 3.8 + lift, 0, "accent")
    if tier >= 2:
        m.box(3, 6.6 + lift, 14.6, 13, 7.6 + lift, 16, "dark")
        m.box(4, 5 + lift, 15, 4.8, 6.6 + lift, 15.8, "dark")
        m.box(11.2, 5 + lift, 15, 12, 6.6 + lift, 15.8, "dark")
    if tier >= 4:
        m.box(5, 5.1 + lift, 1, 11, 5.5 + lift, 5, "accent")
    if tier >= 6:
        for x in (4.5, 10):
            m.box(x, 3 + lift, 16, x + 1.5, 4.6 + lift, 18.5, "dark")
            m.box(x + 0.3, 3.3 + lift, 18.5, x + 1.2, 4.3 + lift, 19.5, "accent")
    if tier >= 8:
        m.box(3, 11.6 + lift, 6, 13, 12.2 + lift, 13, "accent")
    return m


def build_dozer(num, tier):
    m = Model()
    # lánctalpak
    for x0 in (0.5, 11.5):
        m.box(x0, 0, 2, x0 + 4, 5, 16, "black")
        m.box(x0 + 0.4, 5, 2.4, x0 + 3.6, 5.6, 15.6, "dark")
        for z in (3, 7, 11, 14.5):
            m.box(x0 - 0.2, 1.2, z, x0 + 4.2, 3.8, z + 1, "dark")
    m.box(4.5, 1.5, 3, 11.5, 6.5, 16, "main", top="light")
    m.box(4.5, 6.5, 3, 11.5, 10, 8.5, "main", top="light")                 # motorháztető
    m.box(4, 6.5, 8.5, 12, 14, 16, "glass")                                  # fülke
    m.box(3.4, 14, 8, 12.6, 15, 16.5, "dark")
    for x in (3.6, 11.6):
        for z in (8.2, 15.4):
            m.box(x, 6.5, z, x + 0.8, 14, z + 0.8, "dark")
    m.box(10, 10, 4, 11.4, 16, 5.4, "dark")                                  # kipufogó
    m.box(10.2, 16, 4.2, 11.2, 17, 5.2, "accent")
    # lapát
    m.box(2, 2, 0, 4.5, 4.5, 3.5, "dark")
    m.box(11.5, 2, 0, 14, 4.5, 3.5, "dark")
    width = 16 + min(6, tier) * 1.0
    x0, x1 = 8 - width / 2, 8 + width / 2
    m.box(x0, 1, -2, x1, 8 + tier * 0.3, 0, "main", top="light")
    m.box(x0, 0, -2.6, x1, 1.6, -0.4, "light")
    if tier >= 2:
        n = 6 + tier
        step = (x1 - x0) / n
        for k in range(n):
            m.box(x0 + k * step + 0.2, -0.8, -3.4, x0 + (k + 1) * step - 0.2, 1, -2.2, "accent")
    if tier >= 6:
        m.box(x0 - 1, 1, -2, x0, 9 + tier * 0.3, 1.5, "dark")
        m.box(x1, 1, -2, x1 + 1, 9 + tier * 0.3, 1.5, "dark")
    if tier >= 8:
        m.box(4, 15, 8, 12, 15.8, 16, "accent")
    return m


def build_final(num, tier):
    m = Model()
    c = (8, 8, 8)
    m.box(4.5, 4.5, 4.5, 11.5, 11.5, 11.5, "accent")
    m.box(2.5, 2.5, 2.5, 13.5, 13.5, 13.5, "main", rot=(45, "y", c))
    m.box(2.5, 2.5, 2.5, 13.5, 13.5, 13.5, "dark", rot=(45, "x", c))
    m.box(2.5, 2.5, 2.5, 13.5, 13.5, 13.5, "light", rot=(45, "z", c))
    # tüskék minden irányba
    for (a, b, cc) in ((7, 13.5, 7), (7, -1.5, 7)):
        m.box(a, b, cc, a + 2, b + 4, cc + 2, "accent")
    m.box(13.5, 7, 7, 17.5, 9, 9, "accent")
    m.box(-1.5, 7, 7, 2.5, 9, 9, "accent")
    m.box(7, 7, 13.5, 9, 9, 17.5, "accent")
    # száj elöl (észak)
    m.box(3, 5, -2, 13, 11, 2, "black")
    for k in range(6):
        m.box(3.4 + k * 1.65, 9.2, -2.6, 4.6 + k * 1.65, 11, -1.6, "white")
        m.box(3.4 + k * 1.65, 5, -2.6, 4.6 + k * 1.65, 6.8, -1.6, "white")
    # keringő szilánkok
    for (x, y, z) in ((0, 12, 0), (14, 1, 14), (14, 14, 0), (0, 0, 14)):
        m.box(x, y, z, x + 2, y + 2, z + 2, "light", rot=(45, "y"))
    m.box(1, 15, 6, 15, 15.8, 10, "accent")
    return m


def build_crystal():
    m = Model()
    c = (8, 8, 8)
    m.box(5, 3, 5, 11, 13, 11, "accent", rot=(45, "y", c))
    m.box(5, 3, 5, 11, 13, 11, "light", rot=(45, "x", c))
    m.box(6.5, 1, 6.5, 9.5, 3, 9.5, "dark")
    m.box(6.5, 13, 6.5, 9.5, 15, 9.5, "dark")
    return m


def build_portal():
    """Bolt-portál: keret + animált örvény (#1)."""
    m = Model()
    for x in (0, 14):
        m.box(x, 0, 6, x + 2, 24, 10, "dark", top="light")
        m.box(x - 1, 0, 5, x + 3, 2, 11, "main")
    m.box(-1, 22, 5, 17, 26, 11, "main", top="light")
    m.box(2, 24, 6.4, 14, 25, 9.6, "accent")
    for x in (-1, 15):
        m.box(x, 11, 4.6, x + 2, 15, 5.2, "accent")
    el = {"from": [2, 2, 7.6], "to": [14, 22, 8.4],
          "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#1"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#1"}}}
    return m, el


def swirl_texture(frames=16):
    img = Image.new("RGBA", (16, 16 * frames))
    px = img.load()
    for f in range(frames):
        ph = f / frames * 2 * math.pi
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = math.sin(r * 0.9 - ph + a * 2) * 0.5 + 0.5
                col = (int(70 + 40 * v), int(20 + 200 * v * v), int(150 + 105 * v))
                px[x, f * 16 + y] = col + (225,)
    return img


BUILDERS = {"TOOL": build_tool, "HELPER": build_helper, "CAR": build_car, "DOZER": build_dozer, "FINAL": build_final}


# --------------------------------------------------------------- renderer ---
def render(m, tier, size=160, ss=3):
    f = (1, -1, 1)
    n = math.sqrt(3)
    f = tuple(v / n for v in f)
    up = (0, 1, 0)
    r = (f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0])
    rl = math.sqrt(sum(v * v for v in r))
    r = tuple(v / rl for v in r)
    u = (r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    polys = []
    scale = S / 24.0
    for e in m.els:
        pts = corners(e)
        # pts sorrend: (x,y,z) bitek: index = ix*4 + iy*2 + iz
        def P(ix, iy, iz):
            return pts[ix * 4 + iy * 2 + iz]
        faces = {
            "up": ([P(0, 1, 0), P(1, 1, 0), P(1, 1, 1), P(0, 1, 1)], (0, 1, 0), 1.0),
            "north": ([P(0, 0, 0), P(1, 0, 0), P(1, 1, 0), P(0, 1, 0)], (0, 0, -1), 0.82),
            "south": ([P(0, 0, 1), P(1, 0, 1), P(1, 1, 1), P(0, 1, 1)], (0, 0, 1), 0.82),
            "west": ([P(0, 0, 0), P(0, 0, 1), P(0, 1, 1), P(0, 1, 0)], (-1, 0, 0), 0.62),
            "east": ([P(1, 0, 0), P(1, 0, 1), P(1, 1, 1), P(1, 1, 0)], (1, 0, 0), 0.62),
        }
        for name, (verts, nrm, shade) in faces.items():
            if "rotation" in e:
                # a normált is forgatjuk (közelítés a középpontból)
                cen = [sum(v[i] for v in verts) / 4 for i in range(3)]
                ecen = [sum(p[i] for p in pts) / 8 for i in range(3)]
                nrm = tuple(cen[i] - ecen[i] for i in range(3))
                ln = math.sqrt(sum(v * v for v in nrm)) or 1
                nrm = tuple(v / ln for v in nrm)
            if sum(nrm[i] * f[i] for i in range(3)) >= -1e-6:
                continue
            role = e["_top"] if name == "up" else e["_role"]
            col = mul(cell_rgb(tier, role), shade)
            depth = sum(sum(v[i] * f[i] for i in range(3)) for v in verts) / 4
            sp = [(S / 2 + sum((v[i] - 8) * r[i] for i in range(3)) * scale,
                   S / 2 - sum((v[i] - 8) * u[i] for i in range(3)) * scale) for v in verts]
            polys.append((depth, sp, col))
    for depth, sp, col in sorted(polys, key=lambda t: -t[0]):
        d.polygon(sp, fill=col + (255,), outline=mul(col, 0.7) + (255,))
    return img.resize((size, size), Image.LANCZOS)


# ------------------------------------------------------------------- main ---
def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, (dict, list)):
        data = json.dumps(data, ensure_ascii=False, indent=1)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(data)


def main():
    out = os.path.join(ROOT, "resourcepack")
    if os.path.isdir(out):
        shutil.rmtree(out)
    A = os.path.join(out, "assets", NS)
    items = catalog.build()
    models_meta = {}
    previews = []

    write(os.path.join(out, "pack.mcmeta"), {
        "pack": {"description": "§6BlockEater §7- egyedi 3D tárgyak",
                 "min_format": [PACK_MAJOR, 0], "max_format": [PACK_MAJOR, PACK_MINOR]}})
    icon = Image.new("RGBA", (64, 64), (24, 18, 36, 255))
    dd = ImageDraw.Draw(icon)
    dd.rectangle([8, 8, 55, 55], fill=(190, 100, 255, 255)); dd.rectangle([20, 20, 43, 43], fill=(20, 14, 30, 255))
    icon.save(os.path.join(out, "pack.png"))

    for it in items:
        key = it["model"]
        tier = it["tier"]
        m = BUILDERS[it["type"]](it["number"], tier)
        meta = m.normalise()
        models_meta[key] = meta
        tex = make_texture(tier, it["number"] * 7919)
        os.makedirs(os.path.join(A, "textures", "item"), exist_ok=True)
        tex.save(os.path.join(A, "textures", "item", key + ".png"))
        write(os.path.join(A, "models", "item", key + ".json"),
              m.to_json({"0": f"{NS}:item/{key}", "particle": f"{NS}:item/{key}"}))
        write(os.path.join(A, "items", key + ".json"),
              {"model": {"type": "minecraft:model", "model": f"{NS}:item/{key}"}})
        previews.append((it, render(m, tier)))

    # teleportáló kristály
    cm = build_crystal()
    models_meta["portal_crystal"] = cm.normalise()
    make_texture(6, 4242).save(os.path.join(A, "textures", "item", "portal_crystal.png"))
    write(os.path.join(A, "models", "item", "portal_crystal.json"),
          cm.to_json({"0": f"{NS}:item/portal_crystal", "particle": f"{NS}:item/portal_crystal"}))
    write(os.path.join(A, "items", "portal_crystal.json"),
          {"model": {"type": "minecraft:model", "model": f"{NS}:item/portal_crystal"}})
    crystal_prev = render(cm, 6)

    # bolt portál (világban)
    pm, plane = build_portal()
    models_meta["shop_portal"] = pm.normalise(limit=40)
    # a normalizálás ugyanúgy transzformálja a síkot
    lo, hi = None, None
    # a sík koordinátái: ugyanazzal a transzformmal (scale=1, eltolás) mint a keret → újraszámoljuk
    # (a normalise-nál a keret bbox-a (-1..17, 0..26, 4.6..11); középpont (8,13,7.8)
    shift = [8 - 8, 8 - 13, 8 - 7.8]
    plane["from"] = [round(plane["from"][i] + shift[i], 3) for i in range(3)]
    plane["to"] = [round(plane["to"][i] + shift[i], 3) for i in range(3)]
    pj = pm.to_json({"0": f"{NS}:item/shop_portal_frame", "1": f"{NS}:item/shop_portal_swirl",
                     "particle": f"{NS}:item/shop_portal_frame"})
    pj["elements"].append(plane)
    make_texture(6, 99).save(os.path.join(A, "textures", "item", "shop_portal_frame.png"))
    swirl_texture().save(os.path.join(A, "textures", "item", "shop_portal_swirl.png"))
    write(os.path.join(A, "textures", "item", "shop_portal_swirl.png.mcmeta"),
          {"animation": {"frametime": 2, "interpolate": True}})
    write(os.path.join(A, "models", "item", "shop_portal.json"), pj)
    write(os.path.join(A, "items", "shop_portal.json"),
          {"model": {"type": "minecraft:model", "model": f"{NS}:item/shop_portal"}})

    # modellméretek a pluginnak
    with open(os.path.join(ROOT, "src", "main", "resources", "models.yml"), "w", encoding="utf-8") as fh:
        fh.write("# Automatikusan generált (tools/gen_pack.py): modellméretek a világba tett megjelenítéshez.\n")
        yaml.safe_dump(models_meta, fh, sort_keys=False)

    # zip (determinisztikus)
    os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
    zips = [os.path.join(ROOT, "dist", "BlockEater-ResourcePack.zip"),
            os.path.join(ROOT, "src", "main", "resources", "resourcepack.zip")]
    for zp in zips:
        with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
            for base, _, files in sorted(os.walk(out)):
                for fn in sorted(files):
                    full = os.path.join(base, fn)
                    zi = zipfile.ZipInfo(os.path.relpath(full, out).replace(os.sep, "/"), (2026, 1, 1, 0, 0, 0))
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    with open(full, "rb") as fh:
                        z.writestr(zi, fh.read())

    # előnézet
    cols, cell = 10, 160
    rows = (len(previews) + 1 + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 18)), (36, 34, 44, 255))
    sd = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    except OSError:
        font = ImageFont.load_default()
    allp = [(f"{it['number']}. {it['name']}", im) for it, im in previews] + [("Portál kristály", crystal_prev)]
    for i, (label, im) in enumerate(allp):
        x, y = (i % cols) * cell, (i // cols) * (cell + 18)
        sheet.alpha_composite(im, (x, y))
        sd.text((x + 4, y + cell + 2), label[:26], fill=(230, 230, 235, 255), font=font)
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    sheet.convert("RGB").save(os.path.join(ROOT, "docs", "preview.png"))
    print("kész:", len(items), "tárgy; zip:", [os.path.getsize(z) for z in zips])


if __name__ == "__main__":
    main()
