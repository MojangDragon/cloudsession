// Tiszta függvények (nem használnak Minecraft API-t, ezért tesztelhetők).

export const LABEL = "Tartósság";
export const LORE_PREFIX = `§r§7${LABEL}: `;

/** Hátralévő tartósság és maximum a komponensből. A Java Edition is a hátralévőt mutatja. */
export function remainingOf(damage, maxDurability) {
  return { remaining: Math.max(0, maxDurability - damage), max: maxDurability };
}

/** Színkód a hátralévő arány szerint. */
export function colorFor(ratio) {
  if (ratio > 0.6) return "§a";
  if (ratio > 0.3) return "§e";
  if (ratio > 0.15) return "§6";
  return "§c";
}

/** "§aTartósság: 120/250" */
export function durabilityLine(remaining, max) {
  const c = colorFor(max > 0 ? remaining / max : 0);
  return `${LORE_PREFIX}${c}${remaining}§7/${max}`;
}

/** A tooltip-sor beillesztése úgy, hogy a többi lore sor megmarad, a régi tartósság-sor cserélődik. */
export function mergeLore(lore, line) {
  const out = lore.filter((l) => !l.startsWith(LORE_PREFIX));
  out.push(line);
  return out;
}

/** A tartósság-sor eltávolítása. */
export function stripLore(lore) {
  return lore.filter((l) => !l.startsWith(LORE_PREFIX));
}

export function sameLore(a, b) {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

/** Az actionbar szövege a kézben tartott tárgyhoz. */
export function hudText(remaining, max) {
  const ratio = max > 0 ? remaining / max : 0;
  const pct = Math.round(ratio * 100);
  return ` §7| ${LABEL}: ${colorFor(ratio)}${remaining}§7/${max} §8(${pct}%)`;
}
