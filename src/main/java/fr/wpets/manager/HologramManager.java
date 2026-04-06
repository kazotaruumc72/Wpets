package fr.wpets.manager;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.property.visibility.Visibility;
import fr.wpets.WpetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages holograms displayed above pets showing their name and health.
 * Implements Listener to handle FancyHolograms plugin lifecycle events for stability.
 */
public class HologramManager implements Listener {

    private final WpetsPlugin plugin;

    /** Maps pet entity UUID → hologram instance */
    private final Map<UUID, Hologram> activeHolograms = new HashMap<>();

    /** Reference to the update task for cancellation */
    private BukkitTask updateTask;

    public HologramManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a hologram above a pet entity showing its name and health.
     *
     * @param petEntity The pet entity
     * @param petName   The display name for the pet
     * @return {@code true} if the hologram was created successfully
     */
    public boolean createHologram(Entity petEntity, String petName) {
        if (!isFancyHologramsAvailable()) {
            return false;
        }

        try {
            // Remove any existing hologram for this pet
            removeHologram(petEntity.getUniqueId());

            // Create hologram location (1.5 blocks above the pet)
            Location hologramLoc = petEntity.getLocation().clone().add(0, 1.5, 0);

            // Get health info if the entity is a LivingEntity
            String healthLine = "";
            if (petEntity instanceof LivingEntity living) {
                double health = living.getHealth();
                double maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                        ? living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                        : 20.0;
                healthLine = String.format("<red>❤ %.1f / %.1f", health, maxHealth);
            }

            // Create hologram data
            String hologramName = "wpets_pet_" + petEntity.getUniqueId().toString();
            TextHologramData data = new TextHologramData(hologramName, hologramLoc);
            data.setPersistent(false);
            data.setTextUpdateInterval(20); // Update every second
            data.setVisibility(Visibility.ALL); // Make visible to all players
            data.setVisibilityDistance(32); // Set visibility distance

            // Add lines
            if (!petName.isEmpty()) {
                data.addLine("<aqua><bold>" + petName);
            }
            if (!healthLine.isEmpty()) {
                data.addLine(healthLine);
            }

            // Create and spawn the hologram
            de.oliver.fancyholograms.api.HologramManager fhManager = FancyHologramsPlugin.get().getHologramManager();
            Hologram hologram = fhManager.create(data);
            if (hologram != null) {
                hologram.createHologram();
                // Register with FancyHolograms so it manages visibility and updates
                fhManager.addHologram(hologram);
                // Force-show to all online players immediately
                Bukkit.getOnlinePlayers().forEach(hologram::forceShowHologram);
                activeHolograms.put(petEntity.getUniqueId(), hologram);
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create hologram for pet " + petEntity.getUniqueId(), e);
        }

        return false;
    }

    /**
     * Updates the hologram for a pet entity with current health information.
     *
     * @param petEntityUuid The pet entity UUID
     */
    public void updateHologram(UUID petEntityUuid) {
        if (!isFancyHologramsAvailable()) {
            return;
        }

        Hologram hologram = activeHolograms.get(petEntityUuid);
        if (hologram == null) {
            return;
        }

        try {
            Entity petEntity = Bukkit.getEntity(petEntityUuid);
            if (petEntity == null || !petEntity.isValid()) {
                removeHologram(petEntityUuid);
                return;
            }

            // Update location (follow the pet)
            Location newLoc = petEntity.getLocation().clone().add(0, 1.5, 0);
            hologram.getData().setLocation(newLoc);

            // Update health line if it's a LivingEntity
            if (petEntity instanceof LivingEntity living && hologram.getData() instanceof TextHologramData textData && textData.getText().size() > 1) {
                double health = living.getHealth();
                double maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                        ? living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                        : 20.0;
                String healthLine = String.format("<red>❤ %.1f / %.1f", health, maxHealth);

                textData.getText().set(1, healthLine);
            }

            hologram.forceUpdate();
            hologram.refreshForViewersInWorld();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update hologram for pet " + petEntityUuid, e);
        }
    }

    /**
     * Updates the hologram text (pet name) for a specific pet entity.
     *
     * @param petEntity The pet entity
     * @param newName   The new name to display
     */
    public void updateHologramText(Entity petEntity, String newName) {
        if (!isFancyHologramsAvailable()) {
            return;
        }

        Hologram hologram = activeHolograms.get(petEntity.getUniqueId());
        if (hologram == null) {
            return;
        }

        try {
            if (hologram.getData() instanceof TextHologramData textData && !textData.getText().isEmpty()) {
                // Update the first line (pet name)
                textData.getText().set(0, "<aqua><bold>" + newName);
                hologram.forceUpdate();
                hologram.refreshForViewersInWorld();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update hologram text for pet " + petEntity.getUniqueId(), e);
        }
    }

    /**
     * Removes the hologram for a specific pet entity.
     *
     * @param petEntityUuid The pet entity UUID
     */
    public void removeHologram(UUID petEntityUuid) {
        Hologram hologram = activeHolograms.remove(petEntityUuid);
        if (hologram != null) {
            try {
                hologram.deleteHologram();
                FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove hologram for pet " + petEntityUuid, e);
            }
        }
    }

    /**
     * Removes all active holograms (called on plugin disable).
     */
    public void removeAll() {
        for (UUID petUuid : new HashMap<>(activeHolograms).keySet()) {
            removeHologram(petUuid);
        }
        activeHolograms.clear();
    }

    /**
     * Clears the hologram cache without attempting to remove holograms.
     * Used when FancyHolograms is disabled to prevent errors.
     */
    public void clearHologramCache() {
        activeHolograms.clear();
    }

    /**
     * Starts a periodic task that updates all active holograms.
     */
    public void startUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return; // Already running
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isFancyHologramsAvailable()) {
                    return; // Skip update if plugin unavailable
                }
                for (UUID petUuid : new HashMap<>(activeHolograms).keySet()) {
                    updateHologram(petUuid);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every second
    }

    /**
     * Stops the hologram update task.
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    // ── Event Handlers ───────────────────────────────────────────────────────

    /**
     * Handles FancyHolograms being enabled at runtime.
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("FancyHolograms")) {
            plugin.getLogger().info("FancyHolograms detected and enabled - hologram support active");
            // Recreate holograms for active pets if needed
            recreateActiveHolograms();
        }
    }

    /**
     * Handles FancyHolograms being disabled at runtime.
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("FancyHolograms")) {
            plugin.getLogger().warning("FancyHolograms disabled - hologram support unavailable");
            clearHologramCache();
        }
    }

    /**
     * Recreates holograms for all active pets.
     * Called when FancyHolograms is re-enabled.
     */
    private void recreateActiveHolograms() {
        // Clear existing cache since FancyHolograms was reloaded
        activeHolograms.clear();

        // Recreate holograms for currently active pets
        for (UUID playerUuid : plugin.getPetManager().getActivePetsMap().keySet()) {
            UUID petUuid = plugin.getPetManager().getActivePetEntityUuid(playerUuid);
            if (petUuid != null) {
                Entity petEntity = Bukkit.getEntity(petUuid);
                if (petEntity != null && petEntity.isValid()) {
                    String petTypeId = plugin.getPetManager().getActivePetTypeId(playerUuid);
                    if (petTypeId != null) {
                        // Get pet data to check for custom name
                        fr.wpets.model.PetData petData = plugin.getPetData(playerUuid, petTypeId);
                        String displayName;
                        if (petData != null && petData.getCustomName() != null && !petData.getCustomName().isEmpty()) {
                            displayName = petData.getCustomName();
                        } else {
                            displayName = plugin.getPetsConfig()
                                    .getString("pets." + petTypeId + ".display-name", petTypeId);
                        }
                        createHologram(petEntity, displayName);
                    }
                }
            }
        }
    }

    /**
     * Checks if FancyHolograms plugin is available and enabled.
     */
    private boolean isFancyHologramsAvailable() {
        Plugin fhPlugin = plugin.getServer().getPluginManager().getPlugin("FancyHolograms");
        return fhPlugin != null && fhPlugin.isEnabled();
    }
}
