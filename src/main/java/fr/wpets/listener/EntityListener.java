package fr.wpets.listener;

import fr.wpets.WpetsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.entity.EntityDismountEvent;

import java.util.UUID;

/**
 * Listens to entity events to:
 * <ul>
 *   <li>Award pet XP when the owner kills a hostile mob.</li>
 *   <li>Clean up active-pet tracking when the pet entity itself dies.</li>
 *   <li>Prevent players from damaging pets (both their own and others').</li>
 *   <li>Handle flying pet movement when mounted.</li>
 * </ul>
 */
public class EntityListener implements Listener {

    private final WpetsPlugin plugin;

    public EntityListener(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Awards XP to the pet when a hostile mob is killed by its owner.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();

        // Only award XP for hostile mobs
        if (!(dead instanceof Monster)) return;

        // Only when killed by a player
        Player killer = dead.getKiller();
        if (killer == null) return;

        if (!plugin.getPetManager().hasActivePet(killer.getUniqueId())) return;

        plugin.getExperienceManager().grantXP(killer, "mob-kill");
    }

    /**
     * Cleans up the active-pet tracking when the pet entity itself is killed
     * (e.g. by another player or environmental damage).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        UUID mobUuid = dead.getUniqueId();

        UUID ownerUuid = plugin.getPetManager().getOwnerByMobUuid(mobUuid);
        if (ownerUuid == null) return;

        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            plugin.getMilestoneManager().removePassiveEffects(owner);
            // Remove from tracking maps only – entity already dead, don't call remove()
            plugin.getPetManager().onPetEntityDied(ownerUuid);
        }
    }

    /**
     * Prevents players from damaging pets (both their own and others').
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        // Check if the damaged entity is a pet
        UUID damagedUuid = event.getEntity().getUniqueId();
        UUID ownerUuid = plugin.getPetManager().getOwnerByMobUuid(damagedUuid);

        // If the damaged entity is not a pet, allow the damage
        if (ownerUuid == null) return;

        // Check if the damager is a player (directly or indirectly through projectiles)
        Player damager = null;
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
            }
        }

        // If a player is trying to damage a pet, cancel the event
        if (damager != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents players from being dismounted from their pet when pressing Shift.
     * <p>
     * Voluntary dismounts (triggered by the plugin itself through
     * {@link fr.wpets.manager.PetManager#dismount}) are allowed.
     * Dismounts caused by entity death/removal are also allowed and trigger
     * a clean-up of the mounted state.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Entity dismounted = event.getDismounted();
        UUID ownerUuid = plugin.getPetManager().getOwnerByMobUuid(dismounted.getUniqueId());

        // Not a pet entity
        if (ownerUuid == null) return;

        // Only handle the pet's own owner
        if (!player.getUniqueId().equals(ownerUuid)) return;

        // Allow plugin-initiated (voluntary) dismount
        if (plugin.getPetManager().isVoluntaryDismount(player.getUniqueId())) {
            plugin.getPetManager().clearVoluntaryDismount(player.getUniqueId());
            return;
        }

        // Allow dismount if the pet entity is dead or invalid (nothing to ride)
        if (!dismounted.isValid() || (dismounted instanceof LivingEntity le && le.isDead())) {
            plugin.getPetManager().onExternalDismount(player.getUniqueId(), dismounted);
            return;
        }

        // Otherwise cancel the sneak/shift dismount
        if (plugin.getPetManager().isMounted(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles dismounting from flying pets to restore gravity.
     * This event only fires for entities that implement {@code Vehicle};
     * most MythicMobs pets do not, so the {@code EntityDismountEvent} handler
     * above is the primary guard.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;

        Entity vehicle = event.getVehicle();
        UUID ownerUuid = plugin.getPetManager().getOwnerByMobUuid(vehicle.getUniqueId());

        // Check if this is a pet vehicle
        if (ownerUuid == null) return;

        // Check if it's a flying pet
        String petId = plugin.getPetManager().getActivePetTypeId(ownerUuid);
        if (petId != null) {
            FileConfiguration petsCfg = plugin.getPetsConfig();
            boolean isFlying = petsCfg.getBoolean("pets." + petId + ".flying", false);

            // Restore gravity when dismounting from flying pets
            if (isFlying) {
                vehicle.setGravity(true);
            }
        }
    }
}
