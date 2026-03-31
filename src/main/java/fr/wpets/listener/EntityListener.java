package fr.wpets.listener;

import fr.wpets.WpetsPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * Listens to entity death events to:
 * <ul>
 *   <li>Award pet XP when the owner kills a hostile mob.</li>
 *   <li>Clean up active-pet tracking when the pet entity itself dies.</li>
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
}
