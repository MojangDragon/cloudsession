import assert from "node:assert/strict";
import { remainingOf, colorFor, durabilityLine, mergeLore, stripLore, sameLore, hudText, LORE_PREFIX } from "../DurabilityDisplay_BP/scripts/logic.js";

// A Java Edition a hátralévőt mutatja: maxDurability - damage
assert.deepEqual(remainingOf(130, 250), { remaining: 120, max: 250 });
assert.deepEqual(remainingOf(300, 250), { remaining: 0, max: 250 });   // soha nem negatív

assert.equal(colorFor(1), "§a");
assert.equal(colorFor(0.5), "§e");
assert.equal(colorFor(0.2), "§6");
assert.equal(colorFor(0.05), "§c");

assert.equal(durabilityLine(120, 250), `${LORE_PREFIX}§e120§7/250`);

// meglévő lore megmarad, a régi tartósság-sor cserélődik (nem duplázódik)
const base = ["§bEgyedi sor"];
const once = mergeLore(base, durabilityLine(120, 250));
assert.equal(once.length, 2);
const twice = mergeLore(once, durabilityLine(119, 250));
assert.equal(twice.length, 2);
assert.ok(twice[1].includes("119"));
assert.deepEqual(stripLore(twice), base);
assert.ok(sameLore(twice, twice.slice()));
assert.ok(!sameLore(once, twice));

assert.equal(hudText(120, 250), " §7| Tartósság: §e120§7/250 §8(48%)");
assert.equal(hudText(0, 0).includes("0%"), true);   // nulla maximum nem dob hibát
console.log("logic tesztek: rendben");
