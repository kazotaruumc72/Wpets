package fr.wpets.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PetData model class.
 */
@DisplayName("PetData Tests")
class PetDataTest {

    private UUID playerUuid;
    private String petId;
    private PetData petData;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        petId = "dragon";
        petData = new PetData(playerUuid, petId);
    }

    @Test
    @DisplayName("Should create new PetData with default values")
    void testDefaultConstructor() {
        assertThat(petData.getPlayerUuid()).isEqualTo(playerUuid);
        assertThat(petData.getPetId()).isEqualTo(petId);
        assertThat(petData.getLevel()).isEqualTo(1);
        assertThat(petData.getExperience()).isEqualTo(0);
        assertThat(petData.getSkillPoints()).isEqualTo(0);
        assertThat(petData.getUnlockedSkills()).isEmpty();
        assertThat(petData.hasLevelTenParticles()).isFalse();
        assertThat(petData.hasSpeedBoost()).isFalse();
        assertThat(petData.getInventorySlots()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create PetData from database values")
    void testDatabaseConstructor() {
        String skillsJson = "[\"skill1\",\"skill2\",\"skill3\"]";
        PetData dbPet = new PetData(playerUuid, petId, 10, 5000L, 15, skillsJson);

        assertThat(dbPet.getPlayerUuid()).isEqualTo(playerUuid);
        assertThat(dbPet.getPetId()).isEqualTo(petId);
        assertThat(dbPet.getLevel()).isEqualTo(10);
        assertThat(dbPet.getExperience()).isEqualTo(5000L);
        assertThat(dbPet.getSkillPoints()).isEqualTo(15);
        assertThat(dbPet.getUnlockedSkills()).containsExactly("skill1", "skill2", "skill3");
    }

    @Test
    @DisplayName("Should handle null skills JSON in database constructor")
    void testDatabaseConstructorWithNullSkills() {
        PetData dbPet = new PetData(playerUuid, petId, 5, 100L, 3, null);
        assertThat(dbPet.getUnlockedSkills()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty skills JSON in database constructor")
    void testDatabaseConstructorWithEmptySkills() {
        PetData dbPet = new PetData(playerUuid, petId, 5, 100L, 3, "");
        assertThat(dbPet.getUnlockedSkills()).isEmpty();
    }

    @Test
    @DisplayName("Should handle invalid skills JSON in database constructor")
    void testDatabaseConstructorWithInvalidSkills() {
        PetData dbPet = new PetData(playerUuid, petId, 5, 100L, 3, "invalid json {");
        assertThat(dbPet.getUnlockedSkills()).isEmpty();
    }

    @Test
    @DisplayName("Should set and get level")
    void testSetLevel() {
        petData.setLevel(50);
        assertThat(petData.getLevel()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should set and get experience")
    void testSetExperience() {
        petData.setExperience(10000L);
        assertThat(petData.getExperience()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should add experience correctly")
    void testAddExperience() {
        petData.setExperience(500L);
        petData.addExperience(300L);
        assertThat(petData.getExperience()).isEqualTo(800L);
    }

    @Test
    @DisplayName("Should handle large experience values")
    void testLargeExperience() {
        petData.addExperience(Long.MAX_VALUE / 2);
        petData.addExperience(100L);
        assertThat(petData.getExperience()).isPositive();
    }

    @Test
    @DisplayName("Should set and get skill points")
    void testSetSkillPoints() {
        petData.setSkillPoints(25);
        assertThat(petData.getSkillPoints()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should add skill points correctly")
    void testAddSkillPoints() {
        petData.setSkillPoints(10);
        petData.addSkillPoints(5);
        assertThat(petData.getSkillPoints()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should spend skill points correctly")
    void testSpendSkillPoints() {
        petData.setSkillPoints(10);
        petData.spendSkillPoints(3);
        assertThat(petData.getSkillPoints()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should not allow negative skill points when spending")
    void testSpendSkillPointsMinZero() {
        petData.setSkillPoints(5);
        petData.spendSkillPoints(10);
        assertThat(petData.getSkillPoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should unlock skill")
    void testUnlockSkill() {
        petData.unlockSkill("fireball");
        assertThat(petData.getUnlockedSkills()).containsExactly("fireball");
        assertThat(petData.hasSkill("fireball")).isTrue();
    }

    @Test
    @DisplayName("Should not duplicate skill when unlocking twice")
    void testUnlockSkillNoDuplicates() {
        petData.unlockSkill("fireball");
        petData.unlockSkill("fireball");
        assertThat(petData.getUnlockedSkills()).containsExactly("fireball");
    }

    @Test
    @DisplayName("Should unlock multiple skills")
    void testUnlockMultipleSkills() {
        petData.unlockSkill("fireball");
        petData.unlockSkill("ice_blast");
        petData.unlockSkill("teleport");
        assertThat(petData.getUnlockedSkills()).containsExactly("fireball", "ice_blast", "teleport");
        assertThat(petData.hasSkill("ice_blast")).isTrue();
        assertThat(petData.hasSkill("unknown")).isFalse();
    }

    @Test
    @DisplayName("Should serialize skills to JSON")
    void testGetUnlockedSkillsJson() {
        petData.unlockSkill("skill1");
        petData.unlockSkill("skill2");
        String json = petData.getUnlockedSkillsJson();
        assertThat(json).contains("skill1", "skill2");
    }

    @Test
    @DisplayName("Should serialize empty skills list to JSON")
    void testGetUnlockedSkillsJsonEmpty() {
        String json = petData.getUnlockedSkillsJson();
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("Should set runtime flags correctly")
    void testRuntimeFlags() {
        petData.setHasLevelTenParticles(true);
        petData.setHasSpeedBoost(true);
        petData.setInventorySlots(27);

        assertThat(petData.hasLevelTenParticles()).isTrue();
        assertThat(petData.hasSpeedBoost()).isTrue();
        assertThat(petData.getInventorySlots()).isEqualTo(27);
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void testToString() {
        petData.setLevel(10);
        petData.setExperience(5000L);
        petData.setSkillPoints(8);
        String str = petData.toString();
        assertThat(str).contains(playerUuid.toString(), petId, "10", "5000", "8");
    }

    @Test
    @DisplayName("Should handle round-trip JSON serialization")
    void testRoundTripSerialization() {
        petData.unlockSkill("skill1");
        petData.unlockSkill("skill2");
        petData.unlockSkill("skill3");

        String json = petData.getUnlockedSkillsJson();
        PetData restored = new PetData(playerUuid, petId, 1, 0L, 0, json);

        assertThat(restored.getUnlockedSkills()).containsExactly("skill1", "skill2", "skill3");
    }
}
