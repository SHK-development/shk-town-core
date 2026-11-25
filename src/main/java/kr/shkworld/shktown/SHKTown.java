package kr.shkworld.shktown;

import kr.shkworld.shktown.license.life.TutorialLicenseGrantSettings;
import kr.shkworld.shktown.license.professional.ProfessionalSlotSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class SHKTown extends JavaPlugin {
    private static final String TUTORIAL_GRANT_PATH = "license.life.tutorial-grant";
    private static final String PROFESSIONAL_SLOTS_PATH = "license.professional.slots";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            var tutorialSection = requireSection(TUTORIAL_GRANT_PATH);
            var tutorialSettings =
                    TutorialLicenseGrantSettings.from(tutorialSection.getValues(false));

            var slotSection = requireSection(PROFESSIONAL_SLOTS_PATH);
            var slotSettings = new ProfessionalSlotSettings(
                    requireInt(slotSection, "base"),
                    requireInt(slotSection, "maximum")
            );

            // 운영 DataSource를 연결할 때 여기서 두 설정을 각 서비스에 전달한다.
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml 설정 오류: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    private ConfigurationSection requireSection(String path) {
        var section = getConfig().getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException(path + " 섹션이 필요합니다.");
        }
        return section;
    }

    private int requireInt(ConfigurationSection section, String key) {
        if (!section.isInt(key)) {
            throw new IllegalArgumentException(
                    section.getCurrentPath() + "." + key + "에는 정수가 필요합니다."
            );
        }
        return section.getInt(key);
    }
}