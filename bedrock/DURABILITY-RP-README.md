# Tartósság Számokkal (csak resource pack)

A tárgyak **hátralévő tartósságát** számmal írja ki a tárgy fölé: hotbar, táska, ládák, és a kurzoron fogott tárgy.
Nincs hozzá behavior pack, nincs script. Csak a vanilla JSON UI tartósság-bindingjeit használja
(`#item_durability_current_amount`, `#selected_item_durability_current_amount`).

## Telepítés
1. Nyisd meg a `Tartossag-Szamokkal.mcpack` fájlt, a Minecraft importálja.
2. Beállítások → Globális források (Global Resources) → Saját csomagok → Tartósság Számokkal → aktiválás.
   (Világonként is bekapcsolható a világ Resource Packs menüjében.)
3. Ha a menük/inventory rosszul néznek ki: kapcsold ki a csomagot, minden visszaáll.

## Hangolás
A `DurabilityDisplay_RP/ui/ui_common.json`-ban az elem elején:
- `$durability_text_offset`: a szám helye `[x, y]` (most `[0, -10]` = a sáv fölött, a tárgy tetején)
- `$durability_text_scale`: a szám mérete (most `0.6`)

## Tudnivalók
- A számot a tartósság-sáv mellé teszi, ugyanott, ahol a vanilla sáv megjelenik (azaz csak a sérült tárgyaknál látszik, mint a vanilla sáv).
- Csak a hátralévő értéket mutatja (pl. `120`), a maximumot nem, mert egy 16 pixeles slotban nem fér el a `120/250`.
- Nem fér hozzá a tárgy leírásához (tooltip), arra a `Tartossag-Kijelzo.mcpack` (behavior/script verzió) való.

## Tesztelés
Ezt nem tudtam kipróbálni: nincs kliensem a UI megtekintéséhez. A fájl érvényes JSON, a szerkezet a vanilla
`ui_common.json` `durability_bar` elemére épül. A szám pontos helye és mérete valószínűleg hangolást kér.
