package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.SkillNode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SkillManager class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillManager Tests")
class SkillManagerTest {

    @Mock
    private WpetsPlugin plugin;

    @Mock
    private FileConfiguration skillsConfig;

    @Mock
    private ConfigurationSection skillTreeSection;

    @Mock
    private ConfigurationSection skillSection;

    @Mock
    private ConfigurationSection statBonusSection;

    @Mock
    private Logger logger;

    private SkillManager skillManager;

    @BeforeEach
    void setUp() {
        when(plugin.getSkillsConfig()).thenReturn(skillsConfig);
        when(plugin.getLogger()).thenReturn(logger);
        skillManager = new SkillManager(plugin);
    }

    @Test
    @DisplayName("Should handle missing skill-tree section gracefully")
    void testLoadMissingSkillTree() {
        when(skillsConfig.getConfigurationSection("skill-tree")).thenReturn(null);

        skillManager.load();

        verify(logger).warning(contains("skill-tree"));
        assertThat(skillManager.getAllNodes()).isEmpty();
    }

    @Test
    @DisplayName("Should load empty skill tree")
    void testLoadEmptySkillTree() {
        when(skillsConfig.getConfigurationSection("skill-tree")).thenReturn(skillTreeSection);
        when(skillTreeSection.getKeys(false)).thenReturn(Collections.emptySet());

        skillManager.load();

        assertThat(skillManager.getAllNodes()).isEmpty();
    }

    @Test
    @DisplayName("Should return null for non-existent node")
    void testGetNonExistentNode() {
        when(skillsConfig.getConfigurationSection("skill-tree")).thenReturn(skillTreeSection);
        when(skillTreeSection.getKeys(false)).thenReturn(Collections.emptySet());

        skillManager.load();

        assertThat(skillManager.getNode("non_existent")).isNull();
    }

    @Test
    @DisplayName("Should get all nodes")
    void testGetAllNodes() {
        setupBasicSkill();
        skillManager.load();

        assertThat(skillManager.getAllNodes()).hasSize(1);
        assertThat(skillManager.getAllNodes()).extracting("id").contains("basic_skill");
    }

    @Test
    @DisplayName("Should get nodes by branch")
    void testGetNodesByBranch() {
        setupMultipleBranchSkills();
        skillManager.load();

        List<SkillNode> tankNodes = skillManager.getNodesByBranch("TANK");
        List<SkillNode> damageNodes = skillManager.getNodesByBranch("DAMAGE");
        List<SkillNode> utilityNodes = skillManager.getNodesByBranch("UTILITY");

        assertThat(tankNodes).hasSize(1);
        assertThat(damageNodes).hasSize(1);
        assertThat(utilityNodes).hasSize(1);
    }

    @Test
    @DisplayName("Should get nodes by branch case insensitive")
    void testGetNodesByBranchCaseInsensitive() {
        setupBasicSkill();
        skillManager.load();

        List<SkillNode> lower = skillManager.getNodesByBranch("damage");
        List<SkillNode> upper = skillManager.getNodesByBranch("DAMAGE");
        List<SkillNode> mixed = skillManager.getNodesByBranch("DaMaGe");

        assertThat(lower).hasSize(1);
        assertThat(upper).hasSize(1);
        assertThat(mixed).hasSize(1);
    }

    @Test
    @DisplayName("Should return empty list for non-existent branch")
    void testGetNodesByNonExistentBranch() {
        setupBasicSkill();
        skillManager.load();

        List<SkillNode> nodes = skillManager.getNodesByBranch("INVALID");
        assertThat(nodes).isEmpty();
    }

    @Test
    @DisplayName("Should validate canUnlock with no prerequisites")
    void testCanUnlockNoPrerequisites() {
        SkillNode node = new SkillNode(
            "basic", "Basic", "DAMAGE", "Desc", 1,
            Collections.emptyList(), null, false, 1,
            0, 0, 0, 0, 0
        );

        boolean canUnlock = skillManager.canUnlock(node, Collections.emptyList(), 1);
        assertThat(canUnlock).isTrue();
    }

    @Test
    @DisplayName("Should validate canUnlock with met prerequisites")
    void testCanUnlockPrerequisitesMet() {
        SkillNode node = new SkillNode(
            "advanced", "Advanced", "DAMAGE", "Desc", 5,
            Arrays.asList("basic1", "basic2"), null, false, 1,
            0, 0, 0, 0, 0
        );

        List<String> unlocked = Arrays.asList("basic1", "basic2", "other");
        boolean canUnlock = skillManager.canUnlock(node, unlocked, 10);
        assertThat(canUnlock).isTrue();
    }

    @Test
    @DisplayName("Should validate canUnlock with unmet prerequisites")
    void testCanUnlockPrerequisitesNotMet() {
        SkillNode node = new SkillNode(
            "advanced", "Advanced", "DAMAGE", "Desc", 5,
            Arrays.asList("basic1", "basic2"), null, false, 1,
            0, 0, 0, 0, 0
        );

        List<String> unlocked = Collections.singletonList("basic1");
        boolean canUnlock = skillManager.canUnlock(node, unlocked, 10);
        assertThat(canUnlock).isFalse();
    }

    @Test
    @DisplayName("Should validate canUnlock with partially met prerequisites")
    void testCanUnlockPartialPrerequisites() {
        SkillNode node = new SkillNode(
            "advanced", "Advanced", "DAMAGE", "Desc", 5,
            Arrays.asList("req1", "req2", "req3"), null, false, 1,
            0, 0, 0, 0, 0
        );

        List<String> unlocked = Arrays.asList("req1", "req3");
        boolean canUnlock = skillManager.canUnlock(node, unlocked, 10);
        assertThat(canUnlock).isFalse();
    }

    @Test
    @DisplayName("Should validate canUnlock with level requirement met")
    void testCanUnlockLevelRequirementMet() {
        SkillNode node = new SkillNode(
            "ultimate", "Ultimate", "DAMAGE", "Desc", 10,
            Collections.emptyList(), null, false, 50,
            0, 0, 0, 0, 0
        );

        boolean canUnlock = skillManager.canUnlock(node, Collections.emptyList(), 50);
        assertThat(canUnlock).isTrue();

        canUnlock = skillManager.canUnlock(node, Collections.emptyList(), 100);
        assertThat(canUnlock).isTrue();
    }

    @Test
    @DisplayName("Should validate canUnlock with level requirement not met")
    void testCanUnlockLevelRequirementNotMet() {
        SkillNode node = new SkillNode(
            "ultimate", "Ultimate", "DAMAGE", "Desc", 10,
            Collections.emptyList(), null, false, 50,
            0, 0, 0, 0, 0
        );

        boolean canUnlock = skillManager.canUnlock(node, Collections.emptyList(), 49);
        assertThat(canUnlock).isFalse();

        canUnlock = skillManager.canUnlock(node, Collections.emptyList(), 1);
        assertThat(canUnlock).isFalse();
    }

    @Test
    @DisplayName("Should validate canUnlock with both level and prerequisites")
    void testCanUnlockLevelAndPrerequisites() {
        SkillNode node = new SkillNode(
            "master", "Master", "DAMAGE", "Desc", 15,
            Arrays.asList("skill1", "skill2"), null, false, 30,
            0, 0, 0, 0, 0
        );

        // Both met
        assertThat(skillManager.canUnlock(node, Arrays.asList("skill1", "skill2"), 30)).isTrue();

        // Prerequisites met, level not
        assertThat(skillManager.canUnlock(node, Arrays.asList("skill1", "skill2"), 29)).isFalse();

        // Level met, prerequisites not
        assertThat(skillManager.canUnlock(node, Collections.singletonList("skill1"), 30)).isFalse();

        // Neither met
        assertThat(skillManager.canUnlock(node, Collections.emptyList(), 20)).isFalse();
    }

    @Test
    @DisplayName("Should validate canUnlock with zero level requirement")
    void testCanUnlockZeroLevelRequirement() {
        SkillNode node = new SkillNode(
            "basic", "Basic", "DAMAGE", "Desc", 1,
            Collections.emptyList(), null, false, 0,
            0, 0, 0, 0, 0
        );

        // Level requirement 0 or negative means no requirement
        assertThat(skillManager.canUnlock(node, Collections.emptyList(), 1)).isTrue();
        assertThat(skillManager.canUnlock(node, Collections.emptyList(), 0)).isTrue();
    }

    @Test
    @DisplayName("Should validate canUnlock returns immutable collection")
    void testGetAllNodesImmutable() {
        setupBasicSkill();
        skillManager.load();

        var nodes = skillManager.getAllNodes();
        assertThatThrownBy(() -> {
            if (nodes instanceof List) {
                ((List<SkillNode>) nodes).clear();
            }
        }).isInstanceOf(UnsupportedOperationException.class);
    }

    // Helper methods to setup mocked skills

    private void setupBasicSkill() {
        when(skillsConfig.getConfigurationSection("skill-tree")).thenReturn(skillTreeSection);
        when(skillTreeSection.getKeys(false)).thenReturn(Collections.singleton("basic_skill"));
        when(skillTreeSection.getConfigurationSection("basic_skill")).thenReturn(skillSection);

        when(skillSection.getString("display-name", "basic_skill")).thenReturn("Basic Skill");
        when(skillSection.getString("branch", "UTILITY")).thenReturn("DAMAGE");
        when(skillSection.getString("description", "")).thenReturn("A basic skill");
        when(skillSection.getInt("cost", 1)).thenReturn(5);
        when(skillSection.getStringList("required-nodes")).thenReturn(Collections.emptyList());
        when(skillSection.getString("mythic-skill", "")).thenReturn("");
        when(skillSection.getBoolean("passive", true)).thenReturn(false);
        when(skillSection.getInt("level-required", 0)).thenReturn(1);
        when(skillSection.getInt("inventory-slots", 0)).thenReturn(0);
        when(skillSection.getConfigurationSection("stat-bonus")).thenReturn(null);
    }

    private void setupMultipleBranchSkills() {
        when(skillsConfig.getConfigurationSection("skill-tree")).thenReturn(skillTreeSection);
        when(skillTreeSection.getKeys(false)).thenReturn(
            new java.util.HashSet<>(Arrays.asList("tank_skill", "damage_skill", "utility_skill"))
        );

        // Tank skill
        ConfigurationSection tankSection = mock(ConfigurationSection.class);
        when(skillTreeSection.getConfigurationSection("tank_skill")).thenReturn(tankSection);
        when(tankSection.getString("display-name", "tank_skill")).thenReturn("Tank");
        when(tankSection.getString("branch", "UTILITY")).thenReturn("TANK");
        when(tankSection.getString("description", "")).thenReturn("Tank");
        when(tankSection.getInt("cost", 1)).thenReturn(1);
        when(tankSection.getStringList("required-nodes")).thenReturn(Collections.emptyList());
        when(tankSection.getString("mythic-skill", "")).thenReturn("");
        when(tankSection.getBoolean("passive", true)).thenReturn(true);
        when(tankSection.getInt("level-required", 0)).thenReturn(0);
        when(tankSection.getInt("inventory-slots", 0)).thenReturn(0);
        when(tankSection.getConfigurationSection("stat-bonus")).thenReturn(null);

        // Damage skill
        ConfigurationSection damageSection = mock(ConfigurationSection.class);
        when(skillTreeSection.getConfigurationSection("damage_skill")).thenReturn(damageSection);
        when(damageSection.getString("display-name", "damage_skill")).thenReturn("Damage");
        when(damageSection.getString("branch", "UTILITY")).thenReturn("DAMAGE");
        when(damageSection.getString("description", "")).thenReturn("Damage");
        when(damageSection.getInt("cost", 1)).thenReturn(1);
        when(damageSection.getStringList("required-nodes")).thenReturn(Collections.emptyList());
        when(damageSection.getString("mythic-skill", "")).thenReturn("");
        when(damageSection.getBoolean("passive", true)).thenReturn(true);
        when(damageSection.getInt("level-required", 0)).thenReturn(0);
        when(damageSection.getInt("inventory-slots", 0)).thenReturn(0);
        when(damageSection.getConfigurationSection("stat-bonus")).thenReturn(null);

        // Utility skill
        ConfigurationSection utilitySection = mock(ConfigurationSection.class);
        when(skillTreeSection.getConfigurationSection("utility_skill")).thenReturn(utilitySection);
        when(utilitySection.getString("display-name", "utility_skill")).thenReturn("Utility");
        when(utilitySection.getString("branch", "UTILITY")).thenReturn("UTILITY");
        when(utilitySection.getString("description", "")).thenReturn("Utility");
        when(utilitySection.getInt("cost", 1)).thenReturn(1);
        when(utilitySection.getStringList("required-nodes")).thenReturn(Collections.emptyList());
        when(utilitySection.getString("mythic-skill", "")).thenReturn("");
        when(utilitySection.getBoolean("passive", true)).thenReturn(true);
        when(utilitySection.getInt("level-required", 0)).thenReturn(0);
        when(utilitySection.getInt("inventory-slots", 0)).thenReturn(0);
        when(utilitySection.getConfigurationSection("stat-bonus")).thenReturn(null);
    }
}
