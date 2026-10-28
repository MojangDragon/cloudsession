"""Az 50 tárgy katalógusa. Ebből készül az items.yml és a resource pack is."""
import math

GOAL = 1_000_000_000

# (név, típus)  TOOL = kézi eszköz, HELPER = segéd, CAR = autó, DOZER = dózer, FINAL = a Világfaló
NAMES = [
    ("Rágcsáló Csákány", "TOOL"), ("Fogas Ásó", "TOOL"), ("Kis Segéd", "HELPER"),
    ("Falánk Kalapács", "TOOL"), ("Gödör Gokart", "CAR"), ("Kőrágó Fúró", "TOOL"),
    ("Ásó Manó", "HELPER"), ("Mini Dózer", "DOZER"), ("Vasgyomor Csákány", "TOOL"),
    ("Homokfutó", "CAR"), ("Robotszolga", "HELPER"), ("Aranyfogú Fúró", "TOOL"),
    ("Földtúró Dózer", "DOZER"), ("Terepjáró Evő", "CAR"), ("Gyémánt Harapó", "TOOL"),
    ("Drón Segéd", "HELPER"), ("Sziklarágó Dózer", "DOZER"), ("Sportautó Zabáló", "CAR"),
    ("Smaragd Pusztító", "TOOL"), ("Cyborg Segéd", "HELPER"), ("Bányász Dózer", "DOZER"),
    ("Monster Truck", "CAR"), ("Obszidián Zúzó", "TOOL"), ("Lávaszellem", "HELPER"),
    ("Gigász Dózer", "DOZER"), ("Lánctalpas Romboló", "CAR"), ("Netherit Nyelő", "TOOL"),
    ("Fekete Lyuk Segéd", "HELPER"), ("Hegyevő Dózer", "DOZER"), ("Rakétaautó", "CAR"),
    ("Végzet Fúró", "TOOL"), ("Ősi Gólem", "HELPER"), ("Titán Dózer", "DOZER"),
    ("Gépóriás", "CAR"), ("Kozmikus Csákány", "TOOL"), ("Űrlény Segéd", "HELPER"),
    ("Bolygórágó Dózer", "DOZER"), ("Plazma Autó", "CAR"), ("Antianyag Kalapács", "TOOL"),
    ("Dimenzióőrző", "HELPER"), ("Kontinens Dózer", "DOZER"), ("Időgép Autó", "CAR"),
    ("Csillagevő Fúró", "TOOL"), ("Világevő Segéd", "HELPER"), ("Apokalipszis Dózer", "DOZER"),
    ("Galaxisugró", "CAR"), ("Ősrobbanás Csákány", "TOOL"), ("Végtelen Éhség", "HELPER"),
    ("Istenek Dózere", "DOZER"), ("A Világfaló", "FINAL"),
]
assert len(NAMES) == 50

DESC = {
    "TOOL": "Jobb klikk: gömb alakban megeszi a blokkokat a célpont körül.",
    "HELPER": "Jobb klikk: előhívja / elküldi a segédet, ami követ és eszi a blokkokat.",
    "CAR": "Jobb klikk a földre: lerakja az autót. Ülj bele, és vezesd (W/A/S/D). Guggolás: leszállás.",
    "DOZER": "Jobb klikk a földre: lerakja a dózert. Ülj bele és tolj, minden eltűnik előtte. Guggolás: leszállás.",
    "FINAL": "A legfőbb fegyver. Jobb klikk: hatalmas gömb alakú pusztítás.",
}


def round_sig(x, sig=2):
    if x <= 0:
        return 0
    e = math.floor(math.log10(x))
    f = 10 ** (e - sig + 1)
    return int(round(x / f) * f)


def build():
    items = []
    for i, (name, typ) in enumerate(NAMES, start=1):
        if i == 1:
            unlock = 0
        elif i == 50:
            unlock = GOAL
        else:
            unlock = round_sig(100 * (GOAL / 100) ** ((i - 2) / 48))
        cost = round_sig(unlock * 0.2) if i > 1 else 0
        t = (i - 1) / 49
        speed = int(round(6 * 500 ** t))                 # blokk / tick
        tool_radius = int(round(1 + 119 * t ** 2.2))
        if typ in ("TOOL", "FINAL"):
            radius, move = tool_radius, 0.0
        elif typ == "HELPER":
            radius, move = max(1, round(tool_radius * 0.4)), 0.0
        elif typ == "CAR":
            radius, move = max(1, round(tool_radius * 0.3)), round(0.45 + 0.9 * t, 2)
        else:  # DOZER: lassabb, de nagyobbat eszik
            radius, move = max(1, round(tool_radius * 0.5)), round(0.22 + 0.45 * t, 2)
        items.append(dict(number=i, model=f"item_{i:02d}", name=name, type=typ,
                          unlock=unlock, cost=cost, radius=radius, speed=speed,
                          move=move, tier=min(9, (i - 1) * 10 // 50), desc=DESC[typ]))
    return items
