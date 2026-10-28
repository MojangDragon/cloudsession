# BlockEater

Spigot/Paper plugin **Minecraft 26.3**-ra. Minél több blokkot eszel meg, annál erősebb tárgyakat oldasz fel,
egészen az 1 milliárd blokkos végső fegyverig: **A Világfaló**.

> Datapackben ez nem megoldható (bolt-menü, blokkszámláló, vezethető járművek), ezért plugin + resource pack.

## Telepítés

1. `dist/BlockEater-1.0.0.jar` → a szerver `plugins/` mappájába (Spigot vagy Paper, 26.3, Java 25).
2. A resource pack (`dist/BlockEater-ResourcePack.zip`) adja az egyedi textúrákat és a 3D modelleket.
   Két módon kapják meg a játékosok:
   - **Saját hosting:** töltsd fel a zipet egy közvetlen letöltési linkre, majd a `plugins/BlockEater/config.yml`-ben
     add meg a `resource-pack.url` és `resource-pack.sha1` értékét.
   - **Beépített szerver:** `resource-pack.embedded-server.enabled: true`, `port` és `public-host` megadása
     (a portot el kell érni kívülről). A plugin ilyenkor magától kiszolgálja a packet és beállítja az sha1-et.
3. Pack nélkül is működik minden, csak a tárgyak papírként néznek ki.

## Játékmenet

- Új játékos kap egy **Portál Kristályt** és az 1. tárgyat (Rágcsáló Csákány).
- **Jobb klikk a kristállyal** (vagy `/shop`): bolt-portál jelenik meg előtted, bárhol. Jobb klikk a portálra = bolt.
- Minden megevett blokk 1 egység: ez a **megevett** számláló (feloldja a tárgyakat) és az **egyenleg** (ebből fizetsz).
- **50 tárgy**, a megevett blokkok szerint feloldva, az utolsó (A Világfaló) 1 000 000 000 megevett blokknál.
- A jobb oldali alap Minecraft kijelző (sidebar) mutatja: megevett blokk, egyenleg, következő tárgy, hátralévő blokk az 1 milliárdig. (`/be hud` kapcsolja.)

| Típus | Tárgyak | Használat |
|---|---|---|
| Eszköz (csákány, kalapács, fúró) | 15 | jobb klikk: gömb alakban eszi a célpont körül |
| Segéd | 12 | jobb klikk: előhívja, követ és eszi a blokkokat; újra jobb klikk: elküldi |
| Autó | 11 | jobb klikk a földre: lerakja, beleülsz, W/A/S/D, guggolás = leszállás |
| Dózer | 11 | mint az autó, lassabb, de nagyobb folyosót nyit |
| **A Világfaló** | 1 | 120 blokk sugarú gömb, 3000 blokk/tick |

A tárgyak sugara és sebessége (blokk/tick) a szinttel nő. A pusztítást a plugin tickenként, időkerettel dolgozza fel,
így a hatalmas sugarak sem fagyasztják le a szervert (tesztelve: ~5 ms/tick a Világfaló evés közben).

## Parancsok

| Parancs | |
|---|---|
| `/shop` | bolt-portál előhívása |
| `/be stats` | állásod |
| `/be hud` | jobb oldali kijelző ki/be |
| `/be pack` | resource pack újraküldése |
| `/be give <játékos> <1-50>` | tárgy adása (admin) |
| `/be add <játékos> <összeg>` | megevett blokkok jóváírása (admin) |
| `/be set <játékos> <összeg>` | állás beállítása (admin) |
| `/be reset <játékos>` | nullázás (admin) |
| `/be stats <játékos>`, `/be reload` | admin / konzol |

## Beállítás

- `config.yml`: tiltott világok, tiltott blokkok, ládák védelme, padlóvédelem, időkeret (`max-ms-per-tick`), portál élettartama.
- `items.yml`: a 50 tárgy (név, ár, feloldás, sugár, sebesség). Átírható, `/be reload` betölti.

## Fordítás

```
mvn package                      # target/BlockEater.jar
```

A Spigot API-t a `hub.spigotmc.org` Maven tárolóból tölti (`26.3-R0.1-SNAPSHOT`).

A tárgykatalógus és a resource pack generálható (Python 3, Pillow, PyYAML):

```
python3 tools/gen_items.py       # src/main/resources/items.yml
python3 tools/gen_pack.py        # resourcepack/, dist/*.zip, models.yml, docs/preview.png
python3 tools/validate_pack.py   # modellek ellenőrzése
```

Előnézet a 3D modellekről: `docs/preview.png`.

## Korlátok

- Nincs régióvédelmi plugin-integráció (WorldGuard stb.): használd a `disabled-worlds` beállítást és a `blockeater.use` jogot.
- A be nem töltött chunkokat kihagyja.
- A járművek láthatatlan páncélállványra épülnek, a megjelenítésük külön entitás.
