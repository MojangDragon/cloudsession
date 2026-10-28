"""items.yml generálása a katalógusból."""
import os, sys, yaml
sys.path.insert(0, os.path.dirname(__file__))
import catalog

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "items.yml")
data = {"goal": catalog.GOAL, "items": catalog.build()}
with open(out, "w", encoding="utf-8") as f:
    f.write("# A tárgyak listája. Átírhatod (ár, sugár, sebesség), a /be reload betölti.\n")
    f.write("# unlock = ennyi összes megevett blokk kell a feloldáshoz, cost = ennyi blokk az ára.\n")
    f.write("# speed = blokk/tick, radius = sugár, move = jármű sebessége.\n")
    yaml.safe_dump(data, f, allow_unicode=True, sort_keys=False, width=200)
print("ok", len(data["items"]))
