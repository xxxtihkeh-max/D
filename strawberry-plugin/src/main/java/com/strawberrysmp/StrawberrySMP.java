package com.strawberrysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.List;

public final class StrawberrySMP extends JavaPlugin implements Listener {

    private Location spawnLocation;

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
        if (getConfig().contains("spawn.world")) {
            String worldName = getConfig().getString("spawn.world");
            if (worldName != null && Bukkit.getWorld(worldName) != null) {
                spawnLocation = new Location(
                        Bukkit.getWorld(worldName),
                        getConfig().getDouble("spawn.x"),
                        getConfig().getDouble("spawn.y"),
                        getConfig().getDouble("spawn.z"),
                        (float) getConfig().getDouble("spawn.yaw"),
                        (float) getConfig().getDouble("spawn.pitch")
                );
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(Component.text("🍓 ", NamedTextColor.RED)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" joined Strawberry SMP!", NamedTextColor.RED)));

        player.sendMessage(Component.text("🍓 Welcome to Strawberry SMP!", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("Use /rules to see the server rules.", NamedTextColor.WHITE));

        updateScoreboard(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(Component.text("🍓 ", NamedTextColor.RED)
                .append(Component.text(event.getPlayer().getName(), NamedTextColor.WHITE))
                .append(Component.text(" left Strawberry SMP.", NamedTextColor.RED)));
    }

    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
                "strawberry",
                "dummy",
                Component.text("🍓 STRAWBERRY SMP", NamedTextColor.RED).decorate(TextDecoration.BOLD)
        );
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("rules")) {
            sendRules(sender);
            return true;
        }

        if (name.equals("ip")) {
            sender.sendMessage(Component.text("🍓 Strawberry SMP IP", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text(getConfig().getString("server-ip", "strawberrysmp.falix.gg"), NamedTextColor.WHITE));
            return true;
        }

        if (name.equals("spawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /spawn.");
                return true;
            }
            if (spawnLocation == null) {
                player.sendMessage(Component.text("Spawn has not been set yet.", NamedTextColor.RED));
                return true;
            }
            player.teleport(spawnLocation);
            player.sendMessage(Component.text("🍓 Teleported to Strawberry SMP spawn!", NamedTextColor.RED));
            return true;
        }

        if (name.equals("setspawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /setspawn.");
                return true;
            }
            if (!player.hasPermission("strawberry.admin")) {
                player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return true;
            }

            spawnLocation = player.getLocation();
            getConfig().set("spawn.world", player.getWorld().getName());
            getConfig().set("spawn.x", spawnLocation.getX());
            getConfig().set("spawn.y", spawnLocation.getY());
            getConfig().set("spawn.z", spawnLocation.getZ());
            getConfig().set("spawn.yaw", spawnLocation.getYaw());
            getConfig().set("spawn.pitch", spawnLocation.getPitch());
            saveConfig();

            player.sendMessage(Component.text("🍓 Strawberry SMP spawn set!", NamedTextColor.RED));
            return true;
        }

        if (name.equals("strawberry")) {
            sender.sendMessage(Component.text("🍓 Strawberry SMP", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text("/rules - Server rules", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("/ip - Server address", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("/spawn - Teleport to spawn", NamedTextColor.WHITE));
            if (sender.hasPermission("strawberry.admin")) {
                sender.sendMessage(Component.text("/setspawn - Set the server spawn", NamedTextColor.WHITE));
            }
            return true;
        }

        return false;
    }
}
