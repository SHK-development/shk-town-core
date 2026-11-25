package kr.shkworld.shktown.license.professional;

public record ProfessionalSlotSettings(int base, int maximum) {
    public ProfessionalSlotSettings {
        if (base < 0 || maximum < base) {
            throw new IllegalArgumentException("config.yml의 license.professional.slots 설정은 0 <= base <= maximum이어야 합니다.");
        }
    }

    public int capacity(int extraSlots) {
        if (extraSlots < 0) {
            throw new IllegalArgumentException("추가 슬롯 수는 음수일 수 없습니다.");
        }

        return (int) Math.min(maximum, (long) base + extraSlots);
    }
}
