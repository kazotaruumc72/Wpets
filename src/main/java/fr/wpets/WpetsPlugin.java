package fr.wpets;

import fr.wpets.command.PetsCommand;
import fr.wpets.gui.PetContextMenuGUI;
import fr.wpets.gui.PetSelectionGUI;
import fr.wpets.gui.SkillTreeGUI;
import fr.wpets.listener.EntityListener;
import fr.wpets.listener.PlayerListener;
import fr.wpets.manager.*;
import fr.wpets.model.PetData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * Main class for the Wpets plugin.
 *
 * <p>Initialisation order:
 * <ol>
 *   <li>Load configuration files (config.yml, pets.yml, skills.yml, messages.yml).</li>
 *   <li>Initialise the database connection.</li>
 *   <li>Create all manager instances.</li>
 *   <li>Register commands, tab completers, and event listeners.</li>
 *   <li>Start background tasks (auto-save, pet-follow loop).</li>
 * </ol>
 */
public class WpetsPlugin extends JavaPlugin {

    private static WpetsPlugin instance;

    // ── Configuration files ──────────────────────────────────────────────────
    private FileConfiguration messagesConfig;
    private FileConfiguration petsConfig;
    private FileConfiguration skillsConfig;

    // ── Managers ─────────────────────────────────────────────────────────────
    private DatabaseManager databaseManager;
    private PetManager petManager;
    private ExperienceManager experienceManager;
    private SkillManager skillManager;
    private MilestoneManager milestoneManager;
    private HologramManager hologramManager;

    // ── GUI ──────────────────────────────────────────────────────────────────
    private SkillTreeGUI skillTreeGUI;
    private PetSelectionGUI petSelectionGUI;
    private PetContextMenuGUI petContextMenuGUI;

    /**
     * In-memory pet data cache.
     * Key: player UUID → (pet-id → PetData)
     */
    private final Map<UUID, Map<String, PetData>> petCache = new HashMap<>();

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();
        saveDefaultFile("pets.yml");
        saveDefaultFile("skills.yml");
        saveDefaultFile("messages.yml");

        loadCustomConfigs();

        // Database
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialise the database. Disabling Wpets.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        skillManager    = new SkillManager(this);
        petManager      = new PetManager(this);
        experienceManager = new ExperienceManager(this);
        milestoneManager  = new MilestoneManager(this);

        // Initialize HologramManager only if FancyHolograms is available
        if (getServer().getPluginManager().getPlugin("FancyHolograms") != null) {
            hologramManager = new HologramManager(this);
            getLogger().info("FancyHolograms detected - hologram support enabled");
        } else {
            getLogger().info("FancyHolograms not found - hologram support disabled");
        }

        skillManager.load();

        // GUI (also a Listener)
        skillTreeGUI = new SkillTreeGUI(this);
        petSelectionGUI = new PetSelectionGUI(this);
        petContextMenuGUI = new PetContextMenuGUI(this);

        // Commands
        PetsCommand cmd = new PetsCommand(this);
        Objects.requireNonNull(getCommand("pet")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("pet")).setTabCompleter(cmd);
        Objects.requireNonNull(getCommand("wpets")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("wpets")).setTabCompleter(cmd);

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(skillTreeGUI, this);
        getServer().getPluginManager().registerEvents(petSelectionGUI, this);
        getServer().getPluginManager().registerEvents(petContextMenuGUI, this);
        if (hologramManager != null) {
            getServer().getPluginManager().registerEvents(hologramManager, this);
        }

        // Background tasks
        petManager.startFollowTask();
        if (hologramManager != null) {
            hologramManager.startUpdateTask();
        }
        startAutoSaveTask();

        getLogger().info("Wpets v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Save all cached pet data
        for (Map.Entry<UUID, Map<String, PetData>> entry : petCache.entrySet()) {
            for (PetData data : entry.getValue().values()) {
                databaseManager.savePetData(data);
            }
        }
        petCache.clear();

        // Despawn all pets
        if (petManager != null) {
            petManager.despawnAll();
        }

        // Remove all holograms
        if (hologramManager != null) {
            hologramManager.stopUpdateTask();
            hologramManager.removeAll();
        }

        // Close the DB connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("Wpets disabled. All data saved.");
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    /**
     * Reloads all configuration files and skill definitions.
     */
    public void reloadPlugin() {
        reloadConfig();
        loadCustomConfigs();
        skillManager.load();
        getLogger().info("Wpets configuration reloaded.");
    }

    // ── Config Loading ───────────────────────────────────────────────────────

    private void loadCustomConfigs() {
        messagesConfig = loadConfig("messages.yml");
        petsConfig     = loadConfig("pets.yml");
        skillsConfig   = loadConfig("skills.yml");
    }

    private FileConfiguration loadConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveDefaultFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
    }

    // ── Cache Helpers ────────────────────────────────────────────────────────

    /**
     * Loads all pet data for a player from the database into the in-memory cache.
     */
    public void loadPlayerPets(UUID playerUuid) {
        Map<String, PetData> map = petCache.computeIfAbsent(playerUuid, k -> new HashMap<>());
        List<PetData> loaded = databaseManager.loadAllPetsForPlayer(playerUuid);
        for (PetData data : loaded) {
            map.put(data.getPetId(), data);
        }
    }

    /**
     * Saves all pet data for a player and removes the player from the cache.
     */
    public void saveAndUnloadPlayerPets(UUID playerUuid) {
        Map<String, PetData> map = petCache.remove(playerUuid);
        if (map == null) return;
        for (PetData data : map.values()) {
            databaseManager.savePetData(data);
        }
    }

    /**
     * Returns cached pet data, or loads it fresh from the DB if not present.
     */
    public PetData getCachedPetData(UUID playerUuid, String petId) {
        Map<String, PetData> map = petCache.computeIfAbsent(playerUuid, k -> new HashMap<>());
        return map.get(petId);
    }

    /**
     * Returns cached pet data if present, otherwise loads from DB,
     * creates a new entry if still absent, and caches it.
     */
    public PetData getOrCreatePetData(UUID playerUuid, String petId) {
        Map<String, PetData> map = petCache.computeIfAbsent(playerUuid, k -> new HashMap<>());
        if (map.containsKey(petId)) {
            return map.get(petId);
        }

        PetData loaded = databaseManager.loadPetData(playerUuid, petId);
        if (loaded == null) {
            loaded = new PetData(playerUuid, petId);
            databaseManager.savePetData(loaded);
        }
        map.put(petId, loaded);
        return loaded;
    }

    // ── Auto-save Task ───────────────────────────────────────────────────────

    private void startAutoSaveTask() {
        long interval = getConfig().getLong("auto-save-interval", 300) * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.keepAlive();
                int saved = 0;
                for (Map<String, PetData> map : petCache.values()) {
                    for (PetData data : map.values()) {
                        databaseManager.savePetData(data);
                        saved++;
                    }
                }
                if (saved > 0) {
                    getLogger().fine("Auto-saved " + saved + " pet records.");
                }
            }
        }.runTaskTimerAsynchronously(this, interval, interval);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public static WpetsPlugin getInstance() {
        return instance;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getPetsConfig() {
        return petsConfig;
    }

    public FileConfiguration getSkillsConfig() {
        return skillsConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PetManager getPetManager() {
        return petManager;
    }

    public ExperienceManager getExperienceManager() {
        return experienceManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    public SkillTreeGUI getSkillTreeGUI() {
        return skillTreeGUI;
    }

    public PetSelectionGUI getPetSelectionGUI() {
        return petSelectionGUI;
    }

    public PetContextMenuGUI getPetContextMenuGUI() {
        return petContextMenuGUI;
    }

    /**
     * Helper to get pet data from the cache (no auto-creation).
     */
    public PetData getPetData(UUID playerUuid, String petId) {
        return getCachedPetData(playerUuid, petId);
    }

    public HologramManager getHologramManager() {
        return hologramManager; // May be null if FancyHolograms is not available
    }
}
