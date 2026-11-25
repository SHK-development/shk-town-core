package kr.shkworld.shktown;

import kr.shkworld.shktown.life.license.TutorialLicenseGrantSettings;
import org.bukkit.plugin.java.JavaPlugin;

public class SHKTown extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();

        var section = getConfig().getConfigurationSection("life.licenses.tutotrial-grant");
        if (section == null) {
            getLogger().severe("config.yml에 life.licenses.tutorial-grant 섹션이 필요합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final TutorialLicenseGrantSettings tutorialLicenseGrantSettings;
        try {
            tutorialLicenseGrantSettings = TutorialLicenseGrantSettings.from(section.getValues(false));
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml의 life.license.tutorial-grant 설정 오류: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {

    }
}
