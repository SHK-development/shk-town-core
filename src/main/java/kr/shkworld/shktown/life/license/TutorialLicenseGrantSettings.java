package kr.shkworld.shktown.life.license;

import kr.shkworld.shktown.license.LicenseCategory;
import kr.shkworld.shktown.license.LicenseType;

import java.util.*;

public record TutorialLicenseGrantSettings(
        List<LicenseType> types,
        int initialGrade,
        int initialExperience
) {
    public TutorialLicenseGrantSettings {
        types = List.copyOf(types);

        if (new HashSet<>(types).size() != types.size()) {
            throw new IllegalArgumentException("config.yml의 life.license.tutorial-grant.types에 중복된 라이센스가 있습니다.");
        }
        for (var type : types) {
            if (type.getCategory() != LicenseCategory.LIFE) {
                throw new IllegalArgumentException("생활 라이센스 최초 지급 목록에 전문 자격을 넣을 수 없습니다: " + type.getId());
            }
        }
        if (initialGrade < 1 || initialGrade > 9) {
            throw new IllegalArgumentException("config.yml의 life.license.tutorial-grant.initial-grade는 1~9 사이어야 합니다.");
        }
        if (initialExperience < 0) {
            throw new IllegalArgumentException("config.yml의 life.license.tutorial-grant.initial-experience는 0 이상이어야 합니다.");
        }
    }

    public static TutorialLicenseGrantSettings from(Map<String, Object> values) {
        if (!(values.get("types") instanceof List<?> ids)) {
            throw new IllegalArgumentException("config.yml의 life.license.tutorial-grant.types에는 라이선스 ID 목록이 필요합니다.");
        }

        var types = new ArrayList<LicenseType>();
        for (var id : ids) {
            var type = Arrays.stream(LicenseType.values())
                    .filter(candidate -> candidate.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("config.yml의 life.license.tutorial-grant.types에 알 수 없는 라이선스 ID가 있습니다:" + id));
            types.add(type);
        }

        return new TutorialLicenseGrantSettings(types, requireInt(values, "initial-grade"), requireInt(values, "initial-experience"));
    }

    private static int requireInt(Map<String, Object> values, String key) {
        if (values.get(key) instanceof Integer value) {
            return value;
        }
        throw new IllegalArgumentException("config.yml의 life.license.tutorial-grant." + key + "에는 정수가 필요합니다.");
    }
}
