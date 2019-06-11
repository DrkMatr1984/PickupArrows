package de.dustplanet.pickuparrows;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
// WorldGuard
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

/**
 * PickupArrows for CraftBukkit/Spigot. Handles activities (ProjectileHit)! Refer to the dev.bukkit.org page:
 * https://dev.bukkit.org/projects/pickuparrows
 *
 * @author xGhOsTkiLLeRx thanks to mushroomhostage for the original PickupArrows plugin!
 */

public class PickupArrowsListener implements Listener {
    private PickupArrows plugin;

    public PickupArrowsListener(PickupArrows instance) {
        plugin = instance;
    }

    /**
     * Event called when a projectile lands.
     *
     * @param event a ProjectileHitEvent
     */
    @EventHandler
    public void onProjectileHitEvent(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        // Arrow?
        if (projectile == null || !(projectile instanceof AbstractArrow)) {
            return;
        }
        // Get data
        AbstractArrow arrow = (AbstractArrow) projectile;
        ProjectileSource shooter = projectile.getShooter();

        boolean onFire = onFire(arrow);
        boolean isSpectral = isSpectral(arrow);
        boolean isTipped = isTipped(arrow);
        boolean isTrident = isTrident(arrow);

        String shooterName = "unknown";
        if (shooter instanceof Player) {
            shooterName = "player";
            boolean isCrossbow = ((Player) shooter).getInventory().getItemInMainHand().getType() == Material.CROSSBOW;
            if (isCrossbow) {
                shooterName += ".crossbow";
            }
        } else if (shooter instanceof BlockProjectileSource) {
            shooterName = ((BlockProjectileSource) shooter).getBlock().getType().name().toLowerCase();
        } else if (shooter instanceof LivingEntity) {
            shooterName = ((LivingEntity) shooter).getType().toString().toLowerCase();
        }

        // Return if arrow is creative
        if (plugin.getConfig().getBoolean("ignoreCreativeArrows", false) && getPickup(arrow) == PickupStatus.CREATIVE_ONLY) {
            return;
        }

        // Make WorldGuard check
        if (plugin.isUsingWorldGuard()) {
            RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = regionContainer.createQuery();
            ApplicableRegionSet regionList = query.getApplicableRegions(BukkitAdapter.adapt(arrow.getLocation()));
            // If we use a whitelist and no regions are here, cancel
            if (regionList.size() == 0 && !plugin.isBlacklist()) {
                return;
            }
            // Iterate through the regions
            for (ProtectedRegion region : regionList) {
                String regionName = region.getId();
                // Either it's on the blacklist or not on the whitelist --> cancel
                if (plugin.isBlacklist() && plugin.getRegions().contains(regionName) || !plugin.getRegions().contains(regionName)) {
                    return;
                }
            }
        }

        // First deny it & then check if we can allow it again
        setPickup(arrow, PickupStatus.DISALLOWED);

        // Check if shooterName is in config, otherwise fallback again
        if (!plugin.getConfig().contains("pickupFrom." + shooterName)) {
            shooterName = "unknown";
        }
        if (isTrident) {
            if (plugin.getConfig().getBoolean("pickupFrom." + shooterName + ".trident")) {
                setPickup(arrow, PickupStatus.ALLOWED);
            }
        } else {
            if (plugin.getConfig().getBoolean("pickupFrom." + shooterName + ".fire") && onFire) {
                setPickup(arrow, PickupStatus.ALLOWED);
            } else if (plugin.getConfig().getBoolean("pickupFrom." + shooterName + ".normal") && !onFire && !isSpectral && !isTipped) {
                setPickup(arrow, PickupStatus.ALLOWED);
            } else if (plugin.getConfig().getBoolean("pickupFrom." + shooterName + ".spectral") && isSpectral && !isTipped) {
                setPickup(arrow, PickupStatus.ALLOWED);
            } else if (plugin.getConfig().getBoolean("pickupFrom." + shooterName + ".tipped") && !isSpectral && isTipped) {
                setPickup(arrow, PickupStatus.ALLOWED);
            }
        }
    }

    /**
     * Sets whether the arrow is from a player or not.
     *
     * @param arrow to change
     * @param status PickupStatus (allowed, disallowed, creative only)
     */
    private void setPickup(AbstractArrow arrow, PickupStatus status) {
        arrow.setPickupStatus(status);
    }

    /**
     * Returns the current pickup state of an arrow.
     *
     * @param arrow the arrow
     * @return PickupStatus (allowed, disallowed, creative only)
     */
    private PickupStatus getPickup(AbstractArrow arrow) {
        return arrow.getPickupStatus();
    }

    private boolean onFire(AbstractArrow arrow) {
        boolean onFire = false;
        if (arrow.getFireTicks() > 0) {
            onFire = true;
        }
        return onFire;
    }

    private boolean isTipped(AbstractArrow arrow) {
        return arrow instanceof Arrow && ((Arrow) arrow).getBasePotionData().getType() != PotionType.UNCRAFTABLE;

    }

    private boolean isSpectral(AbstractArrow arrow) {
        return arrow instanceof SpectralArrow;
    }

    private boolean isTrident(AbstractArrow arrow) {
        return arrow instanceof Trident;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDisabledPlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfig().getBoolean("usePermissions", false)) {
            return;
        }
        AbstractArrow arrow = event.getArrow();
        boolean onFire = onFire(arrow);
        boolean isSpectral = isSpectral(arrow);
        boolean isTipped = isTipped(arrow);
        boolean isTrident = isTrident(arrow);

        if (onFire && !player.hasPermission("pickuparrows.allow.fire")) {
            event.setCancelled(true);
        } else if (!onFire && !isSpectral && !isTipped && !player.hasPermission("pickuparrows.allow.normal")) {
            event.setCancelled(true);
        } else if (isSpectral && !isTipped && !player.hasPermission("pickuparrows.allow.spectral")) {
            event.setCancelled(true);
        } else if (!isSpectral && isTipped && !player.hasPermission("pickuparrows.allow.tipped")) {
            event.setCancelled(true);
        } else if (isTrident && !player.hasPermission("pickuparrows.allow.trident")) {
            event.setCancelled(true);
        }
    }
}
