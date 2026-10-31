package com.b2binventory.entity;

/** What a unit measures. Units convert by their base factor only within one category. */
public enum UnitCategory {
    COUNT("Quantity / count", "PCS"),
    WEIGHT("Weight", "KG"),
    LENGTH("Length", "M"),
    AREA("Area", "M²"),
    VOLUME("Volume", "L"),
    TIME("Time", "SEC"),
    OTHER("Other", null);

    private final String label;
    private final String baseUnitCode;

    UnitCategory(String label, String baseUnitCode) {
        this.label = label;
        this.baseUnitCode = baseUnitCode;
    }

    public String label() {
        return label;
    }

    /** Code of the standard unit whose base factor is 1, or null for OTHER. */
    public String baseUnitCode() {
        return baseUnitCode;
    }
}
