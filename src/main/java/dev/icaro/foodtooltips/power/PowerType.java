package dev.icaro.foodtooltips.power;

/** The two Power tiers shown in the reference table - no unlock gate between them for now, both always selectable (see {@link PowersMenuService}'s own doc on why). */
public enum PowerType {
    STARTER("Starter Power"),
    INTERMEDIATE("Intermediate Power");

    private final String label;

    PowerType(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
