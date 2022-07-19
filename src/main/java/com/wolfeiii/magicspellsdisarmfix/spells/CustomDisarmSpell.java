package com.wolfeiii.magicspellsdisarmfix.spells;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.targeted.DisarmSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.TargetInfo;
import com.wolfeiii.magicspellsdisarmfix.MagicSpellsDisarmFix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CustomDisarmSpell extends TargetedSpell implements TargetedEntitySpell {

    private Map<UUID, ItemStack> currentlyDisarmed;
    private Map<UUID, Integer> disarmSlots;
    private Set<Material> disarmable;
    private Set<UUID> playersWithBlockedHand;
    private Map<Item, UUID> disarmedItems;

    private boolean dontDrop;
    private boolean preventTheft;

    private int disarmDuration;

    private String strInvalidItem;

    public CustomDisarmSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        List<String> disarmableItems = getConfigStringList("disarmable-items", null);
        if (disarmableItems != null && !disarmableItems.isEmpty()) {
            disarmable = new HashSet<>();

            for (String itemName : disarmableItems) {
                Material material = Material.valueOf(itemName.toUpperCase());
                if (material != Material.AIR) disarmable.add(material);
            }
        }

        dontDrop = getConfigBoolean("dont-drop", false);
        preventTheft = getConfigBoolean("prevent-theft", true);

        disarmDuration = getConfigInt("disarm-duration", 100);

        strInvalidItem = getConfigString("str-invalid-item", "Your target could not be disarmed.");

        if (dontDrop) preventTheft = false;
        if (preventTheft) disarmedItems = new HashMap<>();
        currentlyDisarmed = new HashMap<>();
        playersWithBlockedHand = new HashSet<>();
        disarmSlots = new HashMap<>();
    }

    @Override
    public PostCastAction castSpell(Player entity, SpellCastState spellCastState, float power, String[] args) {
        if (spellCastState == SpellCastState.NORMAL) {
            TargetInfo<LivingEntity> target = getTargetedEntity(entity, power);
            if (target == null) return noTarget(entity);

            LivingEntity realTarget = target.getTarget();

            boolean disarmed = disarm(realTarget);
            if (!disarmed) return noTarget(entity, strInvalidItem);

            if (currentlyDisarmed.containsKey(entity.getUniqueId())) return noTarget(entity);

            playSpellEffects(entity, realTarget);
            sendMessages(entity, realTarget);
            return PostCastAction.NO_MESSAGES;
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

    @Override
    public boolean castAtEntity(Player player, LivingEntity livingEntity, float power) {
        if (!validTargetList.canTarget(player, livingEntity) || currentlyDisarmed.containsKey(player.getUniqueId())) return false;
        boolean disarmed = disarm(livingEntity);
        if (disarmed) playSpellEffects(EffectPosition.TARGET, livingEntity);
        return disarmed;
    }

    @Override
    public boolean castAtEntity(LivingEntity livingEntity, float v) {
        if (!validTargetList.canTarget(livingEntity) || currentlyDisarmed.containsKey(livingEntity.getUniqueId())) return false;
        boolean disarmed = disarm(livingEntity);
        if (disarmed) playSpellEffects(EffectPosition.TARGET, livingEntity);
        return disarmed;
    }

    /*

        @Override
        public boolean castAtEntity(Player player, LivingEntity target, float power) {
            if (!validTargetList.canTarget(player, target)) return false;
            boolean disarmed =  disarm(target);
            if (disarmed) playSpellEffects(player, target);
            return disarmed;
        }

         */

    private boolean disarm(LivingEntity target) {
        final ItemStack inHand = getItemInHand(target);
        if (inHand == null) return false;

        if (disarmable != null) {
            Material material = inHand.getType();
            if (material == Material.AIR || !contains(material)) return false;
        }

        if (!dontDrop) {
            setItemInHand(target, null);
            Item item = target.getWorld().dropItemNaturally(target.getLocation(), inHand.clone());
            item.setPickupDelay(disarmDuration);
            if (preventTheft && target instanceof Player) disarmedItems.put(item, target.getUniqueId());
            return true;
        }

        currentlyDisarmed.put(target.getUniqueId(), inHand);

        if (target instanceof Player) {
            for (int i = 0; i < ((Player) target).getInventory().getContents().length; i++) {
                ItemStack itemStack = ((Player) target).getInventory().getContents()[i];
                if (itemStack != null && itemStack.getType() != Material.AIR && itemStack.equals(inHand)) {
                    disarmSlots.put(target.getUniqueId(), i);
                }
            }
        }

        setItemInHand(target, null);
        MagicSpells.scheduleDelayedTask(() -> {
            ItemStack inHand2 = getItemInHand(target);
            if (!currentlyDisarmed.containsKey(target.getUniqueId())) {
                // They died during the disarm process.
                return;
            }

            currentlyDisarmed.remove(target.getUniqueId());

            if (inHand2 == null || inHand2.getType() == Material.AIR) {
                setItemInHand(target, inHand);
            } else if (target instanceof Player) {
                int slot = ((Player) target).getInventory().firstEmpty();
                if (slot >= 0) ((Player) target).getInventory().setItem(slot, inHand);
                else {
                    Integer previousSlot = disarmSlots.get(target.getUniqueId());
                    if (previousSlot != null) {
                        ItemStack itemStack = ((Player) target).getInventory().getItem(previousSlot);
                        ((Player) target).getInventory().setItem(previousSlot, inHand);

                        Item item = target.getWorld().dropItem(target.getLocation(), itemStack);
                        item.setPickupDelay(0);
                    }
                }
            }
        }, disarmDuration);

        return true;
    }

    private boolean contains(Material type) {
        return disarmable.contains(type);
    }

    private ItemStack getItemInHand(LivingEntity entity) {
        EntityEquipment equip = entity.getEquipment();
        if (equip == null) return null;
        return equip.getItemInHand();
    }

    private void setItemInHand(LivingEntity entity, ItemStack item) {
        EntityEquipment equip = entity.getEquipment();
        if (equip == null) return;
        equip.setItemInHand(item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (!preventTheft) return;

        Item item = event.getItem();
        if (!disarmedItems.containsKey(item)) return;
        if (disarmedItems.get(item).equals(event.getPlayer().getUniqueId())) disarmedItems.remove(item);
        else event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();

        if (currentlyDisarmed.containsKey(uuid)) {
            ItemStack itemStack = currentlyDisarmed.get(uuid);
            Player player = event.getEntity();

            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        }

        currentlyDisarmed.remove(uuid);
    }

    @EventHandler(
            ignoreCancelled = true,
            priority = EventPriority.MONITOR
    )
    public void onDisarm(SpellTargetEvent event) {
        if (event.getSpell() instanceof CustomDisarmSpell) {
            if (event.getTarget() instanceof Player) {
                Player player = (Player) event.getTarget();
                this.playersWithBlockedHand.add(player.getUniqueId());
                Bukkit.getScheduler().scheduleSyncDelayedTask(MagicSpellsDisarmFix.getCore(), () -> {
                    this.playersWithBlockedHand.remove(player.getUniqueId());
                }, disarmDuration);
            }
        }
    }

    @EventHandler
    public void onPickUp(PlayerPickupItemEvent event) {

        Player player = event.getPlayer();

        if (this.playersWithBlockedHand.contains(player.getUniqueId())) {
            if (player.getInventory().getHeldItemSlot() == player.getInventory().firstEmpty()) {
                event.setCancelled(true);
            }
        }
    }
}
