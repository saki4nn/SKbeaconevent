package saki4.skbeaconevent;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class SKbeaconevent extends JavaPlugin implements Listener, CommandExecutor {

    private Location spawnLoc;
    private int health;
    private boolean active = false;
    private ArmorStand hologram;
    private long nextSpawnTime;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("beacon").setExecutor(this);
        loadLocation();
        setupTimer();
        startVisualEffects();
    }

    private void loadLocation() {
        if (getConfig().contains("location.world")) {
            World world = Bukkit.getWorld(getConfig().getString("location.world"));
            if (world != null) {
                spawnLoc = new Location(world,
                        getConfig().getDouble("location.x"),
                        getConfig().getDouble("location.y"),
                        getConfig().getDouble("location.z"));
            }
        }
    }

    private void setupTimer() {
        long intervalTicks = getConfig().getLong("delay-minutes", 60) * 1200L;
        nextSpawnTime = System.currentTimeMillis() + (getConfig().getLong("delay-minutes", 60) * 60 * 1000);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    startEvent();
                    nextSpawnTime = System.currentTimeMillis() + (getConfig().getLong("delay-minutes", 60) * 60 * 1000);
                }
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void startVisualEffects() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (active && spawnLoc != null) {
                    World w = spawnLoc.getWorld();
                    w.spawnParticle(Particle.SPELL_WITCH, spawnLoc.clone().add(0.5, 0.5, 0.5), 8, 0.4, 0.4, 0.4, 0.02);
                    w.spawnParticle(Particle.VILLAGER_HAPPY, spawnLoc.clone().add(0.5, 1.0, 0.5), 2, 0.3, 0.3, 0.3, 0);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void startEvent() {
        if (spawnLoc == null) return;
        active = true;
        health = 5;
        spawnLoc.getBlock().setType(Material.BEACON);
        createHologram();
        Bukkit.broadcastMessage(color(getConfig().getString("messages.spawn")));
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f));
    }

    private void createHologram() {
        if (hologram != null) hologram.remove();
        Location holoLoc = spawnLoc.clone().add(0.5, 1.5, 0.5);
        hologram = (ArmorStand) holoLoc.getWorld().spawnEntity(holoLoc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true);
        updateHologram();
    }

    private void updateHologram() {
        if (hologram != null) hologram.setCustomName(color(getConfig().getString("messages.hologram-text").replace("%hp%", String.valueOf(health))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("delay")) {
            if (!sender.hasPermission("beacon.player")) {
                sender.sendMessage(ChatColor.RED + "Нет прав!");
                return true;
            }
            if (active) {
                sender.sendMessage(color(getConfig().getString("messages.status-active")));
                return true;
            }
            long diff = nextSpawnTime - System.currentTimeMillis();
            long min = (diff / 1000) / 60;
            long sec = (diff / 1000) % 60;
            sender.sendMessage(color(getConfig().getString("messages.delay-check")
                    .replace("%min%", String.valueOf(min))
                    .replace("%sec%", String.valueOf(sec))));
            return true;
        }

        if (!sender.hasPermission("beacon.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("setspawn") && sender instanceof Player p) {
                spawnLoc = p.getLocation().getBlock().getLocation();
                getConfig().set("location.world", spawnLoc.getWorld().getName());
                getConfig().set("location.x", spawnLoc.getX());
                getConfig().set("location.y", spawnLoc.getY());
                getConfig().set("location.z", spawnLoc.getZ());
                saveConfig();
                sender.sendMessage("§a[Маяк] Локация сохранена!");
            } else if (args[0].equalsIgnoreCase("start")) {
                startEvent();
            } else if (args[0].equalsIgnoreCase("stop")) {
                stopEvent();
                sender.sendMessage("§c[Маяк] Остановлено.");
            }
        }
        return true;
    }

    private void stopEvent() {
        active = false;
        if (spawnLoc != null) spawnLoc.getBlock().setType(Material.AIR);
        if (hologram != null) { hologram.remove(); hologram = null; }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!active || spawnLoc == null || !e.getBlock().getLocation().equals(spawnLoc)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));

        if (random.nextInt(100) < getConfig().getInt("effect-chance")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("effects");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
                    if (type != null) {
                        p.addPotionEffect(new PotionEffect(type, getConfig().getInt("effects." + key + ".duration") * 20, getConfig().getInt("effects." + key + ".level") - 1));
                    }
                }
                p.sendMessage(color(getConfig().getString("messages.effect-hit")));
            }
        }

        health--;
        updateHologram();
        if (health <= 0) {
            stopEvent();
            Bukkit.broadcastMessage(color(getConfig().getString("messages.win").replace("%player%", p.getName())));
        } else {
            Bukkit.broadcastMessage(color(getConfig().getString("messages.hit-all").replace("%player%", p.getName()).replace("%hp%", String.valueOf(health))));
        }
    }

    @Override
    public void onDisable() { if (hologram != null) hologram.remove(); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}