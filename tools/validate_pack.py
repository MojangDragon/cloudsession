"""A resource pack ellenőrzése: hivatkozások, UV-k, koordináták, forgatások."""
import json, os, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
A = os.path.join(ROOT, "assets", "blockeater")
errors, models = [], 0
VALID_ANGLES = {-45, -22.5, 0, 22.5, 45}

for fn in sorted(os.listdir(os.path.join(A, "models", "item"))):
    models += 1
    j = json.load(open(os.path.join(A, "models", "item", fn), encoding="utf-8"))
    for key, ref in j["textures"].items():
        path = os.path.join(A, "textures", ref.split(":")[1] + ".png")
        if not os.path.exists(path):
            errors.append(f"{fn}: hiányzó textúra {ref}")
    for i, e in enumerate(j["elements"]):
        for c in e["from"] + e["to"]:
            if not -16 <= c <= 32:
                errors.append(f"{fn}[{i}]: koordináta a határon kívül {c}")
        if any(e["from"][k] > e["to"][k] for k in range(3)):
            errors.append(f"{fn}[{i}]: from > to")
        r = e.get("rotation")
        if r and (r["angle"] not in VALID_ANGLES or r["axis"] not in "xyz"):
            errors.append(f"{fn}[{i}]: érvénytelen forgatás {r}")
        for fname, f in e["faces"].items():
            if any(not 0 <= u <= 16 for u in f["uv"]):
                errors.append(f"{fn}[{i}]: UV tartományon kívül")
            if f["texture"][1:] not in j["textures"]:
                errors.append(f"{fn}[{i}]: ismeretlen textúra {f['texture']}")

for fn in sorted(os.listdir(os.path.join(A, "items"))):
    j = json.load(open(os.path.join(A, "items", fn), encoding="utf-8"))
    ref = j["model"]["model"].split(":")[1]
    if not os.path.exists(os.path.join(A, "models", ref + ".json")):
        errors.append(f"items/{fn}: hiányzó modell {ref}")

for fn in os.listdir(os.path.join(A, "textures", "item")):
    if fn.endswith(".png"):
        w, h = Image.open(os.path.join(A, "textures", "item", fn)).size
        if fn != "shop_portal_swirl.png" and (w, h) != (32, 32):
            errors.append(f"{fn}: váratlan méret {w}x{h}")

mc = json.load(open(os.path.join(ROOT, "pack.mcmeta"), encoding="utf-8"))
print("modellek:", models, "| pack.mcmeta:", mc["pack"])
if errors:
    print("HIBÁK:"); [print(" -", e) for e in errors[:30]]; sys.exit(1)
print("rendben: minden hivatkozás, UV, koordináta és forgatás érvényes")
