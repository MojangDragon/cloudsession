import { world, system, Player, EquipmentSlot, EntityComponentTypes } from "@minecraft/server";
import { remainingOf, durabilityLine, mergeLore, stripLore, sameLore, hudText } from "./logic.js";

// Beállítások: játékosonként kapcsolható a játékban is:
//   /scriptevent dur:hud    (actionbar ki/be)
//   /scriptevent dur:lore   (tooltip-sor ki/be)
const HUD_INTERVAL_TICKS = 5;
const LORE_INTERVAL_TICKS = 20;
const TAG_HUD_OFF = "dur_hud_off";
const TAG_LORE_OFF = "dur_lore_off";

const ARMOR_AND_HANDS = [
  EquipmentSlot.Mainhand,
  EquipmentSlot.Offhand,
  EquipmentSlot.Head,
  EquipmentSlot.Chest,
  EquipmentSlot.Legs,
  EquipmentSlot.Feet,
];

function durabilityOf(item) {
  const d = item?.getComponent("minecraft:durability");
  if (!d) return undefined;
  return remainingOf(d.damage, d.maxDurability);
}

function mainhandItem(player) {
  return player.getComponent(EntityComponentTypes.Equippable)?.getEquipment(EquipmentSlot.Mainhand);
}

// ---- HUD (actionbar): a kézben tartott tárgy tartóssága ----
function showHud(player) {
  if (player.hasTag(TAG_HUD_OFF)) return;
  const item = mainhandItem(player);
  const dur = durabilityOf(item);
  if (!dur) return;
  player.onScreenDisplay.setActionBar({
    rawtext: [{ translate: item.localizationKey }, { text: hudText(dur.remaining, dur.max) }],
  });
}

// ---- Tooltip: a tartósság sor a tárgy leírásában ----
function updateSlot(slot, loreOn) {
  if (!slot.hasItem()) return;
  const item = slot.getItem();
  const dur = durabilityOf(item);
  if (!dur) return;
  const current = slot.getLore();
  const wanted = loreOn ? mergeLore(current, durabilityLine(dur.remaining, dur.max)) : stripLore(current);
  if (!sameLore(current, wanted)) slot.setLore(wanted);
}

function updateLore(player) {
  const loreOn = !player.hasTag(TAG_LORE_OFF);
  const inv = player.getComponent(EntityComponentTypes.Inventory)?.container;
  if (inv) {
    for (let i = 0; i < inv.size; i++) updateSlot(inv.getSlot(i), loreOn);
  }
  const eq = player.getComponent(EntityComponentTypes.Equippable);
  if (eq) {
    for (const s of ARMOR_AND_HANDS) updateSlot(eq.getEquipmentSlot(s), loreOn);
  }
}

function safely(fn, player) {
  try {
    fn(player);
  } catch (e) {
    // a játékos közben kiléphetett / meghalhatott: nem hiba
  }
}

system.runInterval(() => {
  for (const p of world.getAllPlayers()) safely(showHud, p);
}, HUD_INTERVAL_TICKS);

system.runInterval(() => {
  for (const p of world.getAllPlayers()) safely(updateLore, p);
}, LORE_INTERVAL_TICKS);

// ---- Kapcsolók ----
system.afterEvents.scriptEventReceive.subscribe((e) => {
  const p = e.sourceEntity;
  if (!(p instanceof Player)) return;
  if (e.id === "dur:hud" || e.id === "dur:lore") {
    const tag = e.id === "dur:hud" ? TAG_HUD_OFF : TAG_LORE_OFF;
    const what = e.id === "dur:hud" ? "HUD kijelző" : "Tooltip sor";
    if (p.hasTag(tag)) {
      p.removeTag(tag);
      p.sendMessage(`§a${what}: be`);
    } else {
      p.addTag(tag);
      p.sendMessage(`§c${what}: ki`);
    }
  }
}, { namespaces: ["dur"] });
