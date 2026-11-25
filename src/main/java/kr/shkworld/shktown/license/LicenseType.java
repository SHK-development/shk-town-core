package kr.shkworld.shktown.license;

public enum LicenseType {
    WOODCUTTING("woodcutting", LicenseCategory.LIFE),
    MINING("mining", LicenseCategory.LIFE),
    FISHING("fishing", LicenseCategory.LIFE),
    FARMING("farming", LicenseCategory.LIFE),
    GATHERING("gathering", LicenseCategory.LIFE),
    SKINNING("skinning", LicenseCategory.LIFE);

    private final String id;
    private final LicenseCategory category;

    LicenseType(String id, LicenseCategory category) {
        this.id = id;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public LicenseCategory getCategory() {
        return category;
    }
}
