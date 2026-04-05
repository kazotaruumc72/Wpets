package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExperienceManager class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExperienceManager Tests")
class ExperienceManagerTest {

    @Mock
    private WpetsPlugin plugin;

    @Mock
    private FileConfiguration config;

    @Mock
    private FileConfiguration petsConfig;

    @Mock
    private FileConfiguration messagesConfig;

    @Mock
    private Player player;

    @Mock
    private Server server;

    @Mock
    private PetManager petManager;

    @Mock
    private MilestoneManager milestoneManager;

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private Entity petEntity;

    private ExperienceManager experienceManager;
    private UUID playerUuid;
    private PetData petData;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        petData = new PetData(playerUuid, "dragon");

        // Setup plugin mocks
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getPetsConfig()).thenReturn(petsConfig);
        when(plugin.getMessagesConfig()).thenReturn(messagesConfig);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getPetManager()).thenReturn(petManager);
        when(plugin.getMilestoneManager()).thenReturn(milestoneManager);
        when(plugin.getDatabaseManager()).thenReturn(databaseManager);
        when(plugin.getCachedPetData(playerUuid, "dragon")).thenReturn(petData);

        // Setup player mocks
        when(player.getUniqueId()).thenReturn(playerUuid);

        // Setup default config values
        when(config.getInt("max-level", 100)).thenReturn(100);
        when(config.getDouble("xp-formula-multiplier", 100)).thenReturn(100.0);
        when(config.getInt("skill-points-per-level", 1)).thenReturn(1);

        // Setup messages config
        when(messagesConfig.getString("prefix", "[Wpets] ")).thenReturn("[Wpets] ");
        when(messagesConfig.getString(anyString(), anyString())).thenReturn("Test message");

        experienceManager = new ExperienceManager(plugin);
    }

    @Test
    @DisplayName("Should calculate XP requirement using quadratic formula")
    void testGetRequiredXP() {
        // Formula: level² × 100
        assertThat(experienceManager.getRequiredXP(1)).isEqualTo(100);
        assertThat(experienceManager.getRequiredXP(2)).isEqualTo(400);
        assertThat(experienceManager.getRequiredXP(3)).isEqualTo(900);
        assertThat(experienceManager.getRequiredXP(10)).isEqualTo(10000);
        assertThat(experienceManager.getRequiredXP(50)).isEqualTo(250000);
    }

    @Test
    @DisplayName("Should calculate XP requirement with custom multiplier")
    void testGetRequiredXPCustomMultiplier() {
        when(config.getDouble("xp-formula-multiplier", 100)).thenReturn(50.0);

        assertThat(experienceManager.getRequiredXP(1)).isEqualTo(50);
        assertThat(experienceManager.getRequiredXP(2)).isEqualTo(200);
        assertThat(experienceManager.getRequiredXP(10)).isEqualTo(5000);
    }

    @Test
    @DisplayName("Should handle level 0 XP requirement")
    void testGetRequiredXPLevelZero() {
        assertThat(experienceManager.getRequiredXP(0)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should grant XP and not level up if insufficient")
    void testGrantXPDirectNoLevelUp() {
        petData.setLevel(1);
        petData.setExperience(0);

        experienceManager.grantXPDirect(player, petData, 50);

        assertThat(petData.getLevel()).isEqualTo(1);
        assertThat(petData.getExperience()).isEqualTo(50);
        verify(player, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should grant XP and level up once")
    void testGrantXPDirectSingleLevelUp() {
        petData.setLevel(1);
        petData.setExperience(0);
        petData.setSkillPoints(0);

        // Required XP for level 1→2 is 100
        experienceManager.grantXPDirect(player, petData, 150);

        assertThat(petData.getLevel()).isEqualTo(2);
        assertThat(petData.getExperience()).isEqualTo(50); // 150 - 100 = 50 remaining
        assertThat(petData.getSkillPoints()).isEqualTo(1);
        verify(milestoneManager).checkMilestone(player, petData, 2);
        verify(databaseManager).savePetData(petData);
    }

    @Test
    @DisplayName("Should handle multiple level-ups from single XP grant")
    void testGrantXPDirectMultipleLevelUps() {
        petData.setLevel(1);
        petData.setExperience(0);
        petData.setSkillPoints(0);

        // Level 1→2: 100 XP
        // Level 2→3: 400 XP
        // Total: 500 XP needed for 2 level-ups
        experienceManager.grantXPDirect(player, petData, 600);

        assertThat(petData.getLevel()).isEqualTo(3);
        assertThat(petData.getExperience()).isEqualTo(100); // 600 - 500 = 100 remaining
        assertThat(petData.getSkillPoints()).isEqualTo(2); // 2 level-ups = 2 skill points
        verify(milestoneManager).checkMilestone(player, petData, 2);
        verify(milestoneManager).checkMilestone(player, petData, 3);
        verify(databaseManager, times(2)).savePetData(petData);
    }

    @Test
    @DisplayName("Should not level up beyond max level")
    void testGrantXPDirectMaxLevel() {
        when(config.getInt("max-level", 100)).thenReturn(10);
        petData.setLevel(10);
        petData.setExperience(0);

        experienceManager.grantXPDirect(player, petData, 10000);

        assertThat(petData.getLevel()).isEqualTo(10);
        assertThat(petData.getExperience()).isEqualTo(0); // XP not added at max level
        verify(milestoneManager, never()).checkMilestone(any(), any(), anyInt());
        verify(databaseManager, never()).savePetData(any());
    }

    @Test
    @DisplayName("Should stop leveling at max level during multi-level chain")
    void testGrantXPDirectStopAtMaxLevel() {
        when(config.getInt("max-level", 100)).thenReturn(3);
        petData.setLevel(1);
        petData.setExperience(0);

        // Enough XP to reach level 5, but should stop at 3
        experienceManager.grantXPDirect(player, petData, 10000);

        assertThat(petData.getLevel()).isEqualTo(3);
        verify(milestoneManager).checkMilestone(player, petData, 2);
        verify(milestoneManager).checkMilestone(player, petData, 3);
        verify(milestoneManager, never()).checkMilestone(player, petData, 4);
    }

    @Test
    @DisplayName("Should award custom skill points per level")
    void testGrantXPDirectCustomSkillPoints() {
        when(config.getInt("skill-points-per-level", 1)).thenReturn(3);
        petData.setLevel(1);
        petData.setExperience(0);
        petData.setSkillPoints(0);

        experienceManager.grantXPDirect(player, petData, 150);

        assertThat(petData.getLevel()).isEqualTo(2);
        assertThat(petData.getSkillPoints()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should accumulate skill points over multiple levels")
    void testGrantXPDirectAccumulateSkillPoints() {
        petData.setLevel(1);
        petData.setExperience(0);
        petData.setSkillPoints(5);

        // Level up twice
        experienceManager.grantXPDirect(player, petData, 600);

        assertThat(petData.getSkillPoints()).isEqualTo(7); // 5 + 2 level-ups
    }

    @Test
    @DisplayName("Should handle exact XP for level up")
    void testGrantXPDirectExactAmount() {
        petData.setLevel(1);
        petData.setExperience(0);

        experienceManager.grantXPDirect(player, petData, 100);

        assertThat(petData.getLevel()).isEqualTo(2);
        assertThat(petData.getExperience()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle partial XP accumulation across multiple grants")
    void testGrantXPDirectPartialAccumulation() {
        petData.setLevel(1);
        petData.setExperience(0);

        experienceManager.grantXPDirect(player, petData, 30);
        assertThat(petData.getLevel()).isEqualTo(1);
        assertThat(petData.getExperience()).isEqualTo(30);

        experienceManager.grantXPDirect(player, petData, 40);
        assertThat(petData.getLevel()).isEqualTo(1);
        assertThat(petData.getExperience()).isEqualTo(70);

        experienceManager.grantXPDirect(player, petData, 50);
        assertThat(petData.getLevel()).isEqualTo(2);
        assertThat(petData.getExperience()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should handle large XP grants")
    void testGrantXPDirectLargeAmount() {
        petData.setLevel(1);
        petData.setExperience(0);
        petData.setSkillPoints(0);

        experienceManager.grantXPDirect(player, petData, 100000);

        // Should level up many times but not exceed max level
        assertThat(petData.getLevel()).isLessThanOrEqualTo(100);
        assertThat(petData.getSkillPoints()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle zero XP grant")
    void testGrantXPDirectZeroAmount() {
        petData.setLevel(5);
        petData.setExperience(100);
        int originalLevel = petData.getLevel();
        long originalXP = petData.getExperience();

        experienceManager.grantXPDirect(player, petData, 0);

        assertThat(petData.getLevel()).isEqualTo(originalLevel);
        assertThat(petData.getExperience()).isEqualTo(originalXP);
    }

    @Test
    @DisplayName("Should persist data on every level up")
    void testGrantXPDirectPersistence() {
        petData.setLevel(1);
        petData.setExperience(0);

        // Level up 3 times
        experienceManager.grantXPDirect(player, petData, 1000);

        // Should save once per level-up
        verify(databaseManager, atLeast(3)).savePetData(petData);
    }

    @Test
    @DisplayName("Should trigger milestone check on level up")
    void testGrantXPDirectMilestoneCheck() {
        petData.setLevel(9);
        petData.setExperience(0);

        // Level up to 10 (milestone level)
        experienceManager.grantXPDirect(player, petData, 10000);

        verify(milestoneManager).checkMilestone(player, petData, 10);
    }

    @Test
    @DisplayName("Should calculate progressive XP requirements")
    void testProgressiveXPRequirements() {
        // Verify quadratic growth
        long xp1 = experienceManager.getRequiredXP(1);
        long xp2 = experienceManager.getRequiredXP(2);
        long xp3 = experienceManager.getRequiredXP(3);
        long xp10 = experienceManager.getRequiredXP(10);

        assertThat(xp2).isGreaterThan(xp1);
        assertThat(xp3).isGreaterThan(xp2);
        assertThat(xp3 - xp2).isGreaterThan(xp2 - xp1); // Growth accelerates
        assertThat(xp10).isEqualTo(10000);
    }

    @Test
    @DisplayName("Should handle high level XP requirements")
    void testHighLevelXPRequirements() {
        long xp99 = experienceManager.getRequiredXP(99);
        long xp100 = experienceManager.getRequiredXP(100);

        assertThat(xp99).isEqualTo(980100); // 99² × 100
        assertThat(xp100).isEqualTo(1000000); // 100² × 100
    }
}
