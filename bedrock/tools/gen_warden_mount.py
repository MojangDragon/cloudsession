"""Lovagolható Warden add-on generálása.

A kliens-oldali kinézetet a hivatalos Mojang/bedrock-samples warden fájljából származtatja
(ugyanaz a modell, textúra, animáció), a viselkedést (BP) viszont egy szelíd, lovagolható változatként
írja meg a ló összetevőivel (rideable, input_ground_controlled, player_ride_tamed).

Használat: python3 gen_warden_mount.py [--test-family zombie]
  --test-family: tesztcélra egy mobfajtát is ráültethetővé tesz (a parancssoros teszthez).
"""
import json, os, sys, urllib.request, zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "WardenMount")
RAW = "https://raw.githubusercontent.com/Mojang/bedrock-samples/main/resource_pack/"
ID = "ridewarden:warden"
SEAT_Y = 2.6          # a lovas magassága a warden aljától (blokk)

test_family = None
if "--test-family" in sys.argv:
    test_family = sys.argv[sys.argv.index("--test-family") + 1]
    OUT = OUT + "_TEST"


def fetch(path):
    with urllib.request.urlopen(RAW + path, timeout=60) as r:
        return r.read().decode("utf-8")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(obj if isinstance(obj, str) else json.dumps(obj, ensure_ascii=False, indent=2))


BP_UUID, BP_MOD = "c2a41e57-8d3b-4f06-b1a9-5e7d0c9f2a10", "3f9d6b82-47ea-4c15-a0b3-91d8e52c7f44"
RP_UUID, RP_MOD = "a8e07b3d-52c9-41fa-9d64-0b1e3f7c8a25", "e15c9a40-6b2d-4d83-8f07-c4a3b9d1e682"

# ---------------- Behavior pack ----------------
bp = os.path.join(OUT, "WardenMount_BP")
write(os.path.join(bp, "manifest.json"), {
    "format_version": 2,
    "header": {"name": "Lovagolható Warden (BP)", "description": "Egy szelíd, lovagolható warden.",
               "uuid": BP_UUID, "version": [1, 0, 0], "min_engine_version": [1, 21, 0]},
    "modules": [{"type": "data", "uuid": BP_MOD, "version": [1, 0, 0]}],
    "dependencies": [{"uuid": RP_UUID, "version": [1, 0, 0]}],
})

families = ["player"] + ([test_family] if test_family else [])
write(os.path.join(bp, "entities", "warden_mount.json"), {
    "format_version": "1.21.0",
    "minecraft:entity": {
        "description": {"identifier": ID, "is_spawnable": True, "is_summonable": True, "is_experimental": False},
        "components": {
            "minecraft:type_family": {"family": ["ridewarden_mount", "mob"]},
            "minecraft:health": {"value": 500, "max": 500},
            "minecraft:collision_box": {"width": 0.9, "height": 2.9},
            "minecraft:knockback_resistance": {"value": 1.0},
            "minecraft:fire_immune": {},
            "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0},
            "minecraft:nameable": {},
            "minecraft:physics": {},
            "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
            "minecraft:can_climb": {},
            "minecraft:movement": {"value": 0.32},
            "minecraft:movement.basic": {},
            "minecraft:jump.static": {},
            "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_damage_blocks": True,
                                            "can_walk": True},
            "minecraft:movement_sound_distance_offset": {"value": 0.55},
            "minecraft:ambient_sound_interval": {"event_name": "ambient", "range": 6.0, "value": 6.0},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.random_look_around": {"priority": 11},
            # ---- lovagolhatóság (a ló mintájára) ----
            "minecraft:is_tamed": {},
            "minecraft:is_saddled": {},
            "minecraft:rideable": {
                "seat_count": 1,
                "crouching_skip_interact": True,
                "family_types": families,
                "interact_text": "action.interact.ride.horse",
                "seats": [{"position": [0.0, SEAT_Y, 0.0]}],
            },
            "minecraft:input_ground_controlled": {},
            "minecraft:behavior.player_ride_tamed": {},
            "minecraft:can_power_jump": {},
            "minecraft:horse.jump_strength": {"value": {"range_min": 0.7, "range_max": 0.7}},
        },
    },
})

# ---------------- Resource pack ----------------
rp = os.path.join(OUT, "WardenMount_RP")
write(os.path.join(rp, "manifest.json"), {
    "format_version": 2,
    "header": {"name": "Lovagolható Warden (RP)", "description": "A lovagolható warden kinézete és hangjai.",
               "uuid": RP_UUID, "version": [1, 0, 0], "min_engine_version": [1, 21, 0]},
    "modules": [{"type": "resources", "uuid": RP_MOD, "version": [1, 0, 0]}],
    "dependencies": [{"uuid": BP_UUID, "version": [1, 0, 0]}],
})

# kliens entitás: a vanilla warden, új azonosítóval
client = json.loads(fetch("entity/warden.entity.json"))
client["minecraft:client_entity"]["description"]["identifier"] = ID
write(os.path.join(rp, "entity", "warden_mount.entity.json"), client)

# hangok: a vanilla warden hangjaiból (ambient, hurt, death, step, heartbeat)
sounds = json.loads(fetch("sounds.json"))
w = sounds["entity_sounds"]["entities"]["warden"]
keep = {k: v for k, v in w["events"].items() if k in ("ambient", "hurt", "death", "step", "heartbeat")}
write(os.path.join(rp, "sounds.json"), {"entity_sounds": {"entities": {ID: {
    "events": keep, "pitch": w.get("pitch", [0.8, 1.2]), "volume": w.get("volume", 1.0)}}}})

write(os.path.join(rp, "texts", "languages.json"), ["en_US", "hu_HU"])
write(os.path.join(rp, "texts", "en_US.lang"),
      f"entity.{ID}.name=Rideable Warden\nitem.spawn_egg.entity.{ID}.name=Rideable Warden Spawn Egg\n")
write(os.path.join(rp, "texts", "hu_HU.lang"),
      f"entity.{ID}.name=Lovagolható Warden\nitem.spawn_egg.entity.{ID}.name=Lovagolható Warden idézőtojás\n")

# ikonok
from PIL import Image, ImageDraw
for d in (bp, rp):
    im = Image.new("RGBA", (128, 128), (10, 40, 48, 255))
    dr = ImageDraw.Draw(im)
    dr.rectangle([40, 24, 88, 104], fill=(24, 90, 100, 255), outline=(60, 220, 230, 255), width=3)
    dr.rectangle([52, 36, 62, 46], fill=(60, 220, 230, 255)); dr.rectangle([66, 36, 76, 46], fill=(60, 220, 230, 255))
    dr.rectangle([56, 62, 72, 70], fill=(60, 220, 230, 255))
    im.save(os.path.join(d, "pack_icon.png"))

# ---------------- .mcaddon ----------------
name = "Lovagolhato-Warden" + ("_TEST" if test_family else "") + ".mcaddon"
dest = os.path.join(ROOT, name)
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
    for base in (bp, rp):
        for b, _, files in os.walk(base):
            for fn in files:
                full = os.path.join(b, fn)
                z.write(full, os.path.relpath(full, OUT).replace(os.sep, "/"))
print("kész:", dest)
