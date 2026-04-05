package fr.wpets.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SkillNode model class.
 */
@DisplayName("SkillNode Tests")
class SkillNodeTest {

    private SkillNode basicNode;
    private SkillNode passiveNode;
    private SkillNode activeNode;

    @BeforeEach
    void setUp() {
        basicNode = new SkillNode(
            "basic_skill",
            "Basic Skill",
            "DAMAGE",
            "A basic damage skill",
            5,
            Collections.emptyList(),
            null,
            false,
            1,
            0, 0, 0, 0,
            0
        );

        passiveNode = new SkillNode(
            "health_boost",
            "Health Boost",
            "TANK",
            "Increases max health by 20%",
            3,
            Collections.singletonList("basic_skill"),
            null,
            true,
            5,
            20.0, 0, 0, 0,
            0
        );

        activeNode = new SkillNode(
            "fireball",
            "Fireball",
            "DAMAGE",
            "Cast a fireball",
            10,
            Arrays.asList("basic_skill", "fire_mastery"),
            "fireball_spell",
            false,
            10,
            0, 15.0, 0, 0,
            0
        );
    }

    @Test
    @DisplayName("Should create skill node with all properties")
    void testSkillNodeCreation() {
        assertThat(basicNode.getId()).isEqualTo("basic_skill");
        assertThat(basicNode.getDisplayName()).isEqualTo("Basic Skill");
        assertThat(basicNode.getBranch()).isEqualTo("DAMAGE");
        assertThat(basicNode.getDescription()).isEqualTo("A basic damage skill");
        assertThat(basicNode.getCost()).isEqualTo(5);
        assertThat(basicNode.getLevelRequired()).isEqualTo(1);
        assertThat(basicNode.isPassive()).isFalse();
    }

    @Test
    @DisplayName("Should handle null required nodes")
    void testNullRequiredNodes() {
        SkillNode node = new SkillNode(
            "test", "Test", "DAMAGE", "Desc", 1, null, null, false, 1,
            0, 0, 0, 0, 0
        );
        assertThat(node.getRequiredNodes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null mythic skill")
    void testNullMythicSkill() {
        assertThat(basicNode.getMythicSkill()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve mythic skill value")
    void testMythicSkillValue() {
        assertThat(activeNode.getMythicSkill()).isEqualTo("fireball_spell");
    }

    @Test
    @DisplayName("Should track passive flag correctly")
    void testPassiveFlag() {
        assertThat(basicNode.isPassive()).isFalse();
        assertThat(passiveNode.isPassive()).isTrue();
    }

    @Test
    @DisplayName("Should store required nodes")
    void testRequiredNodes() {
        assertThat(basicNode.getRequiredNodes()).isEmpty();
        assertThat(passiveNode.getRequiredNodes()).containsExactly("basic_skill");
        assertThat(activeNode.getRequiredNodes()).containsExactly("basic_skill", "fire_mastery");
    }

    @Test
    @DisplayName("Should store stat bonuses")
    void testStatBonuses() {
        assertThat(passiveNode.getMaxHealthPercent()).isEqualTo(20.0);
        assertThat(passiveNode.getDamagePercent()).isEqualTo(0);
        assertThat(passiveNode.getSpeedPercent()).isEqualTo(0);
        assertThat(passiveNode.getArmorPercent()).isEqualTo(0);

        assertThat(activeNode.getDamagePercent()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Should detect stat bonuses correctly")
    void testHasStatBonus() {
        assertThat(basicNode.hasStatBonus()).isFalse();
        assertThat(passiveNode.hasStatBonus()).isTrue();
        assertThat(activeNode.hasStatBonus()).isTrue();
    }

    @Test
    @DisplayName("Should detect multiple stat bonuses")
    void testMultipleStatBonuses() {
        SkillNode multiStatNode = new SkillNode(
            "tank_master", "Tank Master", "TANK", "Ultimate tank", 15,
            Collections.emptyList(), null, true, 50,
            30.0, 0, 0, 25.0,
            0
        );
        assertThat(multiStatNode.hasStatBonus()).isTrue();
        assertThat(multiStatNode.getMaxHealthPercent()).isEqualTo(30.0);
        assertThat(multiStatNode.getArmorPercent()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Should handle inventory slots")
    void testInventorySlots() {
        assertThat(basicNode.getInventorySlots()).isEqualTo(0);
        assertThat(basicNode.grantsInventory()).isFalse();

        SkillNode inventoryNode = new SkillNode(
            "pack_mule", "Pack Mule", "UTILITY", "Grants inventory", 5,
            Collections.emptyList(), null, true, 1,
            0, 0, 0, 0,
            27
        );
        assertThat(inventoryNode.getInventorySlots()).isEqualTo(27);
        assertThat(inventoryNode.grantsInventory()).isTrue();
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void testToString() {
        String str = basicNode.toString();
        assertThat(str).contains("basic_skill", "DAMAGE", "5");
    }

    @Test
    @DisplayName("Should support all stat types")
    void testAllStatTypes() {
        SkillNode allStatsNode = new SkillNode(
            "ultimate", "Ultimate", "DAMAGE", "All stats", 20,
            Collections.emptyList(), null, true, 100,
            50.0, 25.0, 15.0, 30.0,
            54
        );

        assertThat(allStatsNode.getMaxHealthPercent()).isEqualTo(50.0);
        assertThat(allStatsNode.getDamagePercent()).isEqualTo(25.0);
        assertThat(allStatsNode.getSpeedPercent()).isEqualTo(15.0);
        assertThat(allStatsNode.getArmorPercent()).isEqualTo(30.0);
        assertThat(allStatsNode.getInventorySlots()).isEqualTo(54);
        assertThat(allStatsNode.hasStatBonus()).isTrue();
        assertThat(allStatsNode.grantsInventory()).isTrue();
    }

    @Test
    @DisplayName("Should support different branches")
    void testBranches() {
        SkillNode tankNode = new SkillNode(
            "t1", "Tank", "TANK", "Tank skill", 1, null, null, true, 1,
            0, 0, 0, 0, 0
        );
        SkillNode damageNode = new SkillNode(
            "d1", "Damage", "DAMAGE", "Damage skill", 1, null, null, false, 1,
            0, 0, 0, 0, 0
        );
        SkillNode utilityNode = new SkillNode(
            "u1", "Utility", "UTILITY", "Utility skill", 1, null, null, true, 1,
            0, 0, 0, 0, 0
        );

        assertThat(tankNode.getBranch()).isEqualTo("TANK");
        assertThat(damageNode.getBranch()).isEqualTo("DAMAGE");
        assertThat(utilityNode.getBranch()).isEqualTo("UTILITY");
    }

    @Test
    @DisplayName("Should support different cost values")
    void testCostValues() {
        SkillNode lowCost = new SkillNode(
            "low", "Low", "DAMAGE", "Cheap", 1, null, null, false, 1,
            0, 0, 0, 0, 0
        );
        SkillNode highCost = new SkillNode(
            "high", "High", "DAMAGE", "Expensive", 50, null, null, false, 1,
            0, 0, 0, 0, 0
        );

        assertThat(lowCost.getCost()).isEqualTo(1);
        assertThat(highCost.getCost()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should support level requirements")
    void testLevelRequirements() {
        SkillNode earlySkill = new SkillNode(
            "early", "Early", "DAMAGE", "Early game", 1, null, null, false, 1,
            0, 0, 0, 0, 0
        );
        SkillNode lateSkill = new SkillNode(
            "late", "Late", "DAMAGE", "Late game", 10, null, null, false, 100,
            0, 0, 0, 0, 0
        );

        assertThat(earlySkill.getLevelRequired()).isEqualTo(1);
        assertThat(lateSkill.getLevelRequired()).isEqualTo(100);
    }
}
