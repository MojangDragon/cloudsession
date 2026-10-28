# Lovagolható Warden (Bedrock add-on)

Egy szelíd warden, amin lovagolhatsz: ugyanaz a kinézet, ugyanazok az animációk és hangok, mint a vanilla wardené,
de nem támad rád, és úgy irányítod, mint egy lovat.

## Telepítés
1. Nyisd meg a `Lovagolhato-Warden.mcaddon` fájlt, a Minecraft mindkét csomagot (BP + RP) importálja.
2. Világ beállításai → **Behavior Packs** és **Resource Packs** → *Lovagolható Warden* → aktiválás mindkettőnél.
3. A világban kell a cheat (parancsok), ha parancsból idézed.

## Használat
- Idézés: `/summon ridewarden:warden` (vagy a kreatív menüben az idézőtojás).
- Felszállás: jobb klikk a wardenre. Irányítás: W/A/S/D, ugrás: szóköz (nyomva tartva erősebb).
- Leszállás: guggolás.

## Hangolás
A `tools/gen_warden_mount.py` elején:
- `SEAT_Y`: milyen magasan ülsz a warden hátán/fején (blokk). Ha beleesel a fejébe, vagy fölötte lebegsz, ezt állítsd.
Az érték módosítása után futtasd újra: `python3 tools/gen_warden_mount.py`.
A sebesség és az ugrás a `WardenMount/WardenMount_BP/entities/warden_mount.json`-ban van (`minecraft:movement`, `horse.jump_strength`).

## Tesztelés
Az add-on fájljait statikusan ellenőriztem (érvényes JSON, azonosítók, a két csomag egymásra hivatkozik, nincs agresszív
komponens), de **játékban nem próbáltam ki**. Nem tudtam Bedrock szervert/klienst futtatni.
Amit ezért először nézz meg: ülés magassága (`SEAT_Y`), a W/A/S/D irányítás, a hangok.
