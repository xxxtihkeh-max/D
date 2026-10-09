package com.strawberrysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StrawberrySMP extends JavaPlugin implements Listener {

    private Location spawnLocation;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, UUID> teleportRequests = new HashMap<>();
    private final Map<UUID, UUID> lastMessaged = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSpawn();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Strawberry SMP enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Strawberry SMP disabled.");
    }

    private void loadSpawn() {
        if (!getConfig().contains("spawn.world")) return;
        String worldName = getConfig().getString("spawn.world");
        if (worldName == null || Bukkit.getWorld(worldName) == null) return;
        spawnLocation = new Location(
                Bukkit.getWorld(worldName),
                getConfig().getDouble("spawn.x"),
                getConfig().getDouble("spawn.y"),
                getConfig().getDouble("spawn.z"),
                (float) getConfig().getDouble("spawn.yaw"),
                (float) getConfig().getDouble("spawn.pitch")
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(Component.text("🍓 ", NamedTextColor.RED)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" joined Strawberry SMP!", NamedTextColor.RED)));
        player.sendMessage(Component.text("🍓 Welcome to Strawberry SMP!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("Use /rules to see the server rules.", NamedTextColor.WHITE));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasMetadata("strawberry.vanished") && !player.hasPermission("strawberry.admin")) {
                player.hidePlayer(this, online);
            }
        }
        updateScoreboard(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(Component.text("🍓 ", NamedTextColor.RED)
                .append(Component.text(event.getPlayer().getName(), NamedTextColor.WHITE))
                .append(Component.text(" left Strawberry SMP.", NamedTextColor.RED)));
        teleportRequests.remove(event.getPlayer().getUniqueId());
        lastMessaged.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !event.getPlayer().hasMetadata("strawberry.back-teleport")) {
            lastLocations.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("strawberry", "dummy",
                Component.text("🍓 STRAWBERRY SMP", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore(" ").setScore(6);
        objective.getScore(ChatColor.WHITE + "Player: " + ChatColor.RED + player.getName()).setScore(5);
        objective.getScore("  ").setScore(4);
        objective.getScore(ChatColor.WHITE + "Online: " + ChatColor.RED + Bukkit.getOnlinePlayers().size()).setScore(3);
        objective.getScore("   ").setScore(2);
        objective.getScore(ChatColor.GRAY + "strawberrysmp").setScore(1);
        player.setScoreboard(board);
    }

    private void sendRules(CommandSender sender) {
        sender.sendMessage(Component.text("🍓 Strawberry SMP Rules", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        for (String rule : getConfig().getStringList("rules")) {
            sender.sendMessage(Component.text("• " + rule, NamedTextColor.WHITE));
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("Only players can use this command.");
        return null;
    }

    private Player findPlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
        return target;
    }

    private String formatDuration(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("🍓 Strawberry SMP Commands", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        sender.sendMessage("/help, /rules, /ip, /discord, /spawn");
        sender.sendMessage("/playtime, /ping, /sethome, /home, /back");
        sender.sendMessage("/tpa <player>, /tpaccept, /msg <player> <message>, /reply <message>");
        sender.sendMessage("/fly and /vanish (staff only)");
        if (sender.hasPermission("strawberry.admin")) sender.sendMessage("/setspawn");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("help") || name.equals("strawberry")) {
            sendHelp(sender);
            return true;
        }
        if (name.equals("rules")) { sendRules(sender); return true; }
        if (name.equals("ip")) {
            sender.sendMessage(Component.text("🍓 Strawberry SMP IP", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text(getConfig().getString("server-ip", "strawberrysmp.falix.gg"), NamedTextColor.WHITE));
            return true;
        }
        if (name.equals("discord")) {
            sender.sendMessage(Component.text("Join the Strawberry SMP Discord:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text("https://discord.gg/KdV6Prnzz4", NamedTextColor.WHITE));
            return true;
        }
        if (name.equals("spawn")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (spawnLocation == null) { player.sendMessage(Component.text("Spawn has not been set yet.", NamedTextColor.RED)); return true; }
            player.teleport(spawnLocation);
            player.sendMessage(Component.text("🍓 Teleported to Strawberry SMP spawn!", NamedTextColor.RED));
            return true;
        }
        if (name.equals("setspawn")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (!player.hasPermission("strawberry.admin")) { player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED)); return true; }
            spawnLocation = player.getLocation();
            getConfig().set("spawn.world", player.getWorld().getName());
            getConfig().set("spawn.x", spawnLocation.getX()); getConfig().set("spawn.y", spawnLocation.getY());
            getConfig().set("spawn.z", spawnLocation.getZ()); getConfig().set("spawn.yaw", spawnLocation.getYaw());
            getConfig().set("spawn.pitch", spawnLocation.getPitch()); saveConfig();
            player.sendMessage(Component.text("🍓 Strawberry SMP spawn set!", NamedTextColor.RED));
            return true;
        }
        if (name.equals("playtime")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            player.sendMessage(Component.text("Your playtime: " + formatDuration(player.getStatistic(Statistic.PLAY_ONE_MINUTE)), NamedTextColor.RED));
            return true;
        }
        if (name.equals("ping")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            player.sendMessage(Component.text("Your ping: " + player.getPing() + " ms", NamedTextColor.RED)); return true;
        }
        if (name.equals("sethome")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            String path = "homes." + player.getUniqueId();
            Location loc = player.getLocation();
            getConfig().set(path + ".world", loc.getWorld().getName()); getConfig().set(path + ".x", loc.getX());
            getConfig().set(path + ".y", loc.getY()); getConfig().set(path + ".z", loc.getZ());
            getConfig().set(path + ".yaw", loc.getYaw()); getConfig().set(path + ".pitch", loc.getPitch()); saveConfig();
            player.sendMessage(Component.text("Home set!", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("home")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            String path = "homes." + player.getUniqueId();
            String worldName = getConfig().getString(path + ".world");
            if (worldName == null || Bukkit.getWorld(worldName) == null) { player.sendMessage(Component.text("You haven't set a home yet.", NamedTextColor.RED)); return true; }
            Location home = new Location(Bukkit.getWorld(worldName), getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"),
                    getConfig().getDouble(path + ".z"), (float)getConfig().getDouble(path + ".yaw"), (float)getConfig().getDouble(path + ".pitch"));
            player.teleport(home); player.sendMessage(Component.text("Teleported home.", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("back")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            Location last = lastLocations.get(player.getUniqueId());
            if (last == null || last.getWorld() == null) { player.sendMessage(Component.text("No previous location is saved yet.", NamedTextColor.RED)); return true; }
            player.setMetadata("strawberry.back-teleport", new org.bukkit.metadata.FixedMetadataValue(this, true));
            player.teleport(last);
            player.removeMetadata("strawberry.back-teleport", this);
            player.sendMessage(Component.text("Returned to your previous location.", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("tpa")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (args.length != 1) { player.sendMessage("Usage: /tpa <player>"); return true; }
            Player target = findPlayer(sender, args[0]); if (target == null) return true;
            if (target.equals(player)) { player.sendMessage(Component.text("You can't send a request to yourself.", NamedTextColor.RED)); return true; }
            teleportRequests.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage(Component.text("Teleport request sent to " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text(player.getName() + " wants to teleport to you. Use /tpaccept to accept.", NamedTextColor.YELLOW));
            return true;
        }
        if (name.equals("tpaccept")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            UUID requesterId = teleportRequests.remove(player.getUniqueId());
            Player requester = requesterId == null ? null : Bukkit.getPlayer(requesterId);
            if (requester == null) { player.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED)); return true; }
            requester.teleport(player.getLocation());
            requester.sendMessage(Component.text("Teleport request accepted.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Teleport request accepted.", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("fly")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (!player.hasPermission("strawberry.admin")) { player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED)); return true; }
            boolean enabled = !player.getAllowFlight(); player.setAllowFlight(enabled);
            if (!enabled && player.isFlying()) player.setFlying(false);
            player.sendMessage(Component.text("Flight " + (enabled ? "enabled" : "disabled") + ".", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("vanish")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (!player.hasPermission("strawberry.admin")) { player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED)); return true; }
            boolean vanished = !player.hasMetadata("strawberry.vanished");
            if (vanished) {
                player.setMetadata("strawberry.vanished", new org.bukkit.metadata.FixedMetadataValue(this, true));
                for (Player online : Bukkit.getOnlinePlayers()) if (!online.equals(player) && !online.hasPermission("strawberry.admin")) online.hidePlayer(this, player);
            } else {
                player.removeMetadata("strawberry.vanished", this);
                for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(this, player);
            }
            player.sendMessage(Component.text("Vanish " + (vanished ? "enabled" : "disabled") + ".", NamedTextColor.GREEN)); return true;
        }
        if (name.equals("msg")) {
            Player senderPlayer = requirePlayer(sender); if (senderPlayer == null) return true;
            if (args.length < 2) { senderPlayer.sendMessage("Usage: /msg <player> <message>"); return true; }
            Player target = findPlayer(sender, args[0]); if (target == null) return true;
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            senderPlayer.sendMessage(Component.text("[You -> " + target.getName() + "] " + message, NamedTextColor.LIGHT_PURPLE));
            target.sendMessage(Component.text("[" + senderPlayer.getName() + " -> You] " + message, NamedTextColor.LIGHT_PURPLE));
            lastMessaged.put(senderPlayer.getUniqueId(), target.getUniqueId()); lastMessaged.put(target.getUniqueId(), senderPlayer.getUniqueId());
            return true;
        }
        if (name.equals("reply")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (args.length < 1) { player.sendMessage("Usage: /reply <message>"); return true; }
            UUID targetId = lastMessaged.get(player.getUniqueId());
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            if (target == null) { player.sendMessage(Component.text("Nobody is available to reply to.", NamedTextColor.RED)); return true; }
            String message = String.join(" ", args);
            player.sendMessage(Component.text("[You -> " + target.getName() + "] " + message, NamedTextColor.LIGHT_PURPLE));
            target.sendMessage(Component.text("[" + player.getName() + " -> You] " + message, NamedTextColor.LIGHT_PURPLE));
            lastMessaged.put(player.getUniqueId(), target.getUniqueId()); lastMessaged.put(target.getUniqueId(), player.getUniqueId());
            return true;
        }
        return false;
    }
}