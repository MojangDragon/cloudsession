package hu.blockeater;

/** Egy tárgy leírása az items.yml-ből. */
public record ItemDef(int number, String model, String name, Type type, long unlock, long cost,
                      int radius, int speed, double move, int tier, String desc) {

    public enum Type { TOOL, HELPER, CAR, DOZER, FINAL;

        public boolean isVehicle() {
            return this == CAR || this == DOZER;
        }

        public String label() {
            return switch (this) {
                case TOOL -> "Eszköz";
                case HELPER -> "Segéd";
                case CAR -> "Autó";
                case DOZER -> "Dózer";
                case FINAL -> "Végső fegyver";
            };
        }
    }
}
