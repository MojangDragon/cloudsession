# Tartósság Kijelző (Bedrock add-on)

Megmutatja a tárgyak tartósságát számokkal (pl. `120/250`), mint a Java Editionben.

- **HUD:** a kézben tartott tárgy neve és tartóssága a képernyő alján (actionbar), színezve (zöld, sárga, narancs, piros) + százalék.
- **Tooltip:** egy `Tartósság: 120/250` sor a tárgy leírásában, a hotbaron, a táskában, a páncélon és a másik kézben lévő tárgyakon.

Egy sima textúracsomag erre nem képes (nem fér hozzá a tartóssághoz), ezért ez egy **behavior pack Script API-val**.

## Telepítés
1. Nyisd meg a `Tartossag-Kijelzo.mcpack` fájlt (dupla kattintás), a Minecraft importálja.
2. Világ beállításai → **Behavior Packs** → Tartósság Kijelző → aktiválás.
3. Szerveren (Bedrock Dedicated Server) a `behavior_packs/` mappába kell tenni a kicsomagolt mappát, és a világ `world_behavior_packs.json`-ban megadni.

Nem kell hozzá kísérleti (Beta API) kapcsoló. Az `@minecraft/server` 2.11.0 stabil modult használja.

## Kapcsolók (játékosonként)
- `/scriptevent dur:hud`: a HUD ki/be
- `/scriptevent dur:lore`: a tooltip-sor ki/be (kikapcsoláskor a sor el is tűnik a tárgyakról)

## Tudnivalók
- A tooltip-sor a tárgy leírásába íródik (lore), 1 másodpercenként frissül. Ha az add-ont kiveszed, a már beírt sor megmarad a tárgyon, amíg a `dur:lore` kapcsolóval le nem veszed, vagy újra rá nem teszed.
- A kijelzett szám a hátralévő tartósság (mint Javában): `maxDurability - damage`.
- Más add-onok lore sorait nem bántja, csak a saját sorát cseréli.

## Fejlesztés
```
node tests/logic.test.mjs      # a számoló/formázó logika tesztje
```
