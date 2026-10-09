package com.strawberrysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
import java.util.ArrayList;
import java.util.List;

public final class StrawberrySMP extends JavaPlugin implements Listener {

    private static final String HOME_MENU_TITLE = "Strawberry Homes";
    private static final String AH_MENU_TITLE = "Strawberry Auction House";
    private static final String STATS_MENU_TITLE = "Your Strawberry Stats";
    private static final String RTP_MENU_TITLE = "Random Teleport";

    private Location spawnLocation;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, UUID> teleportRequests = new HashMap<>();
    private final Map<UUID, UUID> lastMessaged = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSpawn();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) updateScoreboard(online);
        }, 1L, 40L);
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


    private double getBalance(UUID uuid) { return getConfig().getDouble("balances." + uuid, 0.0); }
    private void setBalance(UUID uuid, double amount) { getConfig().set("balances." + uuid, amount); saveConfig(); }

    private void openAuctionHouse(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, AH_MENU_TITLE);
        ItemStack filler = homeMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ", " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(49, homeMenuItem(Material.GOLD_INGOT, "Your Balance", "$" + String.format(java.util.Locale.US, "%.2f", getBalance(player.getUniqueId()))));
        inv.setItem(45, homeMenuItem(Material.BOOK, "How to list", "/ah sell <price> while holding an item"));
        inv.setItem(53, homeMenuItem(Material.BARRIER, "Close", "Close this menu"));
        List<String> ids = getConfig().getStringList("auction.listings");
        int slot = 0;
        for (String id : ids) {
            if (slot >= 45) break;
            String path = "auction.items." + id;
            ItemStack item = getConfig().getItemStack(path + ".item");
            if (item == null) continue;
            UUID sellerId;
            try { sellerId = UUID.fromString(getConfig().getString(path + ".seller", "")); } catch (IllegalArgumentException ex) { continue; }
            double price = getConfig().getDouble(path + ".price");
            ItemStack display = item.clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Price: $" + String.format(java.util.Locale.US, "%.2f", price), NamedTextColor.GREEN));
            lore.add(Component.text("Seller: " + Bukkit.getOfflinePlayer(sellerId).getName(), NamedTextColor.GRAY));
            lore.add(Component.text("Click to buy", NamedTextColor.YELLOW));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot++, display);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !AH_MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 53) { player.closeInventory(); return; }
        if (slot >= 45) return;
        List<String> ids = getConfig().getStringList("auction.listings");
        if (slot >= ids.size()) return;
        String id = ids.get(slot);
        String path = "auction.items." + id;
        ItemStack item = getConfig().getItemStack(path + ".item");
        if (item == null) { player.sendMessage(Component.text("That listing is no longer available.", NamedTextColor.RED)); openAuctionHouse(player); return; }
        UUID seller;
        try { seller = UUID.fromString(getConfig().getString(path + ".seller", "")); } catch (IllegalArgumentException ex) { return; }
        double price = getConfig().getDouble(path + ".price");
        if (seller.equals(player.getUniqueId())) { player.sendMessage(Component.text("You cannot buy your own listing.", NamedTextColor.RED)); return; }
        if (getBalance(player.getUniqueId()) < price) { player.sendMessage(Component.text("You don't have enough money. Use /balance.", NamedTextColor.RED)); return; }
        if (player.getInventory().firstEmpty() == -1) { player.sendMessage(Component.text("Make room in your inventory first.", NamedTextColor.RED)); return; }
        setBalance(player.getUniqueId(), getBalance(player.getUniqueId()) - price);
        setBalance(seller, getBalance(seller) + price);
        player.getInventory().addItem(item.clone());
        ids.remove(id); getConfig().set("auction.listings", ids); getConfig().set(path, null); saveConfig();
        player.sendMessage(Component.text("Purchase complete for $" + String.format(java.util.Locale.US, "%.2f", price) + ".", NamedTextColor.GREEN));
        Player sellerOnline = Bukkit.getPlayer(seller);
        if (sellerOnline != null) sellerOnline.sendMessage(Component.text("Your auction item sold for $" + String.format(java.util.Locale.US, "%.2f", price) + ".", NamedTextColor.GREEN));
        openAuctionHouse(player);
    }

    @EventHandler
    public void onAuctionDrag(InventoryDragEvent event) {
        if (AH_MENU_TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }


    private void openStatsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, STATS_MENU_TITLE);
        ItemStack filler = homeMenuItem(Material.RED_STAINED_GLASS_PANE, " ", " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(10, homeMenuItem(Material.GOLD_INGOT, "Money", "$" + String.format(java.util.Locale.US, "%.2f", getBalance(player.getUniqueId()))));
        inv.setItem(12, homeMenuItem(Material.CLOCK, "Playtime", formatDuration(player.getStatistic(Statistic.PLAY_ONE_MINUTE))));
        inv.setItem(14, homeMenuItem(Material.NAME_TAG, "Team", getConfig().getString("teams." + player.getUniqueId(), "Not assigned")));
        inv.setItem(16, homeMenuItem(Material.FEATHER, "Ping", player.getPing() + " ms"));
        inv.setItem(22, homeMenuItem(Material.BARRIER, "Close", "Close this menu"));
        player.openInventory(inv);
    }

    private void openRtpMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, RTP_MENU_TITLE);
        ItemStack filler = homeMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ", " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(11, homeMenuItem(Material.GRASS_BLOCK, "Overworld RTP", "Teleport to a random safe spot in the Overworld"));
        inv.setItem(13, homeMenuItem(Material.NETHERRACK, "Nether RTP", "Teleport to a random safe spot in the Nether"));
        inv.setItem(15, homeMenuItem(Material.END_STONE, "End RTP", "Teleport to a random safe spot in the End"));
        player.openInventory(inv);
    }

    private void randomTeleport(Player player, org.bukkit.World.Environment environment) {
        org.bukkit.World world = null;
        for (org.bukkit.World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == environment) { world = candidate; break; }
        }
        if (world == null) {
            player.sendMessage(Component.text("That dimension is not available on this server.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Finding a safe random location...", NamedTextColor.YELLOW));
        for (int attempt = 0; attempt < 30; attempt++) {
            int range = environment == org.bukkit.World.Environment.NETHER ? 1500 : 5000;
            int x = java.util.concurrent.ThreadLocalRandom.current().nextInt(-range, range + 1);
            int z = java.util.concurrent.ThreadLocalRandom.current().nextInt(-range, range + 1);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) continue;
            org.bukkit.block.Block floor = world.getBlockAt(x, y - 1, z);
            org.bukkit.block.Block feet = world.getBlockAt(x, y, z);
            org.bukkit.block.Block head = world.getBlockAt(x, y + 1, z);
            if (!floor.getType().isSolid() || floor.isLiquid() || !feet.isPassable() || !head.isPassable()) continue;
            String floorName = floor.getType().name();
            if (floorName.contains("LAVA") || floorName.contains("MAGMA") || floorName.contains("CACTUS") || floorName.contains("FIRE") || floorName.contains("CAMPFIRE")) continue;
            Location destination = new Location(world, x + 0.5, y, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleportAsync(destination).thenAccept(success -> {
                if (success) player.sendMessage(Component.text("Random teleport complete!", NamedTextColor.GREEN));
                else player.sendMessage(Component.text("Teleport failed. Please try again.", NamedTextColor.RED));
            });
            return;
        }
        player.sendMessage(Component.text("Couldn't find a safe spot. Please try again.", NamedTextColor.RED));
    }

    @EventHandler
    public void onStatsAndRtpMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (STATS_MENU_TITLE.equals(title)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) player.closeInventory();
            return;
        }
        if (!RTP_MENU_TITLE.equals(title)) return;
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case 11 -> randomTeleport(player, org.bukkit.World.Environment.NORMAL);
            case 13 -> randomTeleport(player, org.bukkit.World.Environment.NETHER);
            case 15 -> randomTeleport(player, org.bukkit.World.Environment.THE_END);
            default -> { }
        }
    }

    @EventHandler
    public void onStatsAndRtpMenuDrag(InventoryDragEvent event) {
        if (STATS_MENU_TITLE.equals(event.getView().getTitle()) || RTP_MENU_TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    @EventHandler
    public void onHomeMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!HOME_MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        String path = "homes." + player.getUniqueId();
        if (slot == 4) {
            Location loc = player.getLocation();
            getConfig().set(path + ".world", loc.getWorld().getName());
            getConfig().set(path + ".x", loc.getX());
            getConfig().set(path + ".y", loc.getY());
            getConfig().set(path + ".z", loc.getZ());
            getConfig().set(path + ".yaw", loc.getYaw());
            getConfig().set(path + ".pitch", loc.getPitch());
            saveConfig();
            player.sendMessage(Component.text("Home saved!", NamedTextColor.GREEN));
            openHomeMenu(player);
            return;
        }
        if (slot == 0 || slot == 9) {
            String worldName = getConfig().getString(path + ".world");
            if (worldName == null || Bukkit.getWorld(worldName) == null) {
                player.sendMessage(Component.text("You haven't set a home yet. Click New Home first.", NamedTextColor.RED));
                return;
            }
            Location home = new Location(Bukkit.getWorld(worldName), getConfig().getDouble(path + ".x"),
                    getConfig().getDouble(path + ".y"), getConfig().getDouble(path + ".z"),
                    (float) getConfig().getDouble(path + ".yaw"), (float) getConfig().getDouble(path + ".pitch"));
            player.closeInventory();
            player.teleport(home);
            player.sendMessage(Component.text("Teleported home.", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onHomeMenuDrag(InventoryDragEvent event) {
        if (HOME_MENU_TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private ItemStack homeMenuItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        meta.lore(java.util.List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private void openHomeMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, HOME_MENU_TITLE);
        ItemStack filler = homeMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ", " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        inventory.setItem(0, homeMenuItem(Material.RED_BED, "Home 1", "Click to teleport to your saved home"));
        inventory.setItem(1, homeMenuItem(Material.BLUE_SHULKER_BOX, "Stash", "Your private home category"));
        inventory.setItem(2, homeMenuItem(Material.WHITE_BED, "Cabin", "Your private home category"));
        inventory.setItem(3, homeMenuItem(Material.ENDER_EYE, "End", "Your private home category"));
        inventory.setItem(4, homeMenuItem(Material.PAPER, "New Home", "Click to save your current location"));
        inventory.setItem(5, homeMenuItem(Material.NAME_TAG, "Team", "Your private home category"));
        String path = "homes." + player.getUniqueId();
        if (getConfig().getString(path + ".world") == null) {
            for (int i = 9; i < inventory.getSize(); i++)
                inventory.setItem(i, homeMenuItem(Material.PAPER, "New Home", "Click to set your home"));
        } else {
            inventory.setItem(9, homeMenuItem(Material.RED_BED, "Home 1", "Click to teleport to your saved home"));
            for (int i = 10; i < inventory.getSize(); i++)
                inventory.setItem(i, homeMenuItem(Material.PAPER, "New Home", "Click to replace your saved home"));
        }
        player.openInventory(inventory);
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

        objective.getScore(ChatColor.GREEN + "$ " + ChatColor.WHITE + "Money: " + ChatColor.GREEN
                + String.format(java.util.Locale.US, "%.2f", getBalance(player.getUniqueId()))).setScore(7);
        objective.getScore(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.WHITE + "Shards: " + ChatColor.LIGHT_PURPLE + "0").setScore(6);
        objective.getScore(ChatColor.RED + "⚔ " + ChatColor.WHITE + "Kills: " + ChatColor.RED
                + player.getStatistic(Statistic.PLAYER_KILLS)).setScore(5);
        objective.getScore(ChatColor.GOLD + "☠ " + ChatColor.WHITE + "Deaths: " + ChatColor.GOLD
                + player.getStatistic(Statistic.DEATHS)).setScore(4);
        objective.getScore(ChatColor.AQUA + "⌁ " + ChatColor.WHITE + "Ping: " + ChatColor.AQUA
                + player.getPing() + "ms").setScore(3);
        objective.getScore(ChatColor.YELLOW + "◷ " + ChatColor.WHITE + "Playtime: " + ChatColor.YELLOW
                + formatDuration(player.getStatistic(Statistic.PLAY_ONE_MINUTE))).setScore(2);
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
        sender.sendMessage("/ah, /ah sell <price>, /ah cancel <id>, /balance");
        sender.sendMessage("/stats (money, playtime, team, ping), /rtp");
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
        if (name.equals("stats") || name.equals("profile")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            openStatsMenu(player); return true;
        }
        if (name.equals("rtp")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (args.length == 0) { openRtpMenu(player); return true; }
            String dimension = args[0].toLowerCase();
            if (dimension.equals("overworld") || dimension.equals("world")) randomTeleport(player, org.bukkit.World.Environment.NORMAL);
            else if (dimension.equals("nether")) randomTeleport(player, org.bukkit.World.Environment.NETHER);
            else if (dimension.equals("end")) randomTeleport(player, org.bukkit.World.Environment.THE_END);
            else { player.sendMessage("Usage: /rtp [overworld|nether|end]"); }
            return true;
        }
        if (name.equals("balance")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            player.sendMessage(Component.text("Balance: $" + String.format(java.util.Locale.US, "%.2f", getBalance(player.getUniqueId())), NamedTextColor.GREEN)); return true;
        }
        if (name.equals("ah")) {
            Player player = requirePlayer(sender); if (player == null) return true;
            if (args.length == 0) { openAuctionHouse(player); return true; }
            if (args[0].equalsIgnoreCase("sell")) {
                if (args.length != 2) { player.sendMessage("Usage: /ah sell <price> (hold the item you want to list)"); return true; }
                double price;
                try { price = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { player.sendMessage(Component.text("Enter a valid price.", NamedTextColor.RED)); return true; }
                if (!Double.isFinite(price) || price <= 0) { player.sendMessage(Component.text("Price must be greater than zero.", NamedTextColor.RED)); return true; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { player.sendMessage(Component.text("Hold the item you want to list.", NamedTextColor.RED)); return true; }
                String id = UUID.randomUUID().toString();
                String path = "auction.items." + id;
                getConfig().set(path + ".seller", player.getUniqueId().toString());
                getConfig().set(path + ".price", price);
                getConfig().set(path + ".item", hand.clone());
                List<String> ids = getConfig().getStringList("auction.listings"); ids.add(id); getConfig().set("auction.listings", ids);
                player.getInventory().setItemInMainHand(null); saveConfig();
                player.sendMessage(Component.text("Listed item for $" + String.format(java.util.Locale.US, "%.2f", price) + ".", NamedTextColor.GREEN)); return true;
            }
            if (args[0].equalsIgnoreCase("cancel")) {
                if (args.length != 2) { player.sendMessage("Usage: /ah cancel <listing-id>"); return true; }
                String id = args[1]; String path = "auction.items." + id;
                if (!player.getUniqueId().toString().equals(getConfig().getString(path + ".seller"))) { player.sendMessage(Component.text("That is not your listing.", NamedTextColor.RED)); return true; }
                ItemStack item = getConfig().getItemStack(path + ".item");
                if (item == null) { player.sendMessage(Component.text("Listing item not found.", NamedTextColor.RED)); return true; }
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) { player.sendMessage(Component.text("Make room in your inventory first.", NamedTextColor.RED)); return true; }
                List<String> ids = getConfig().getStringList("auction.listings"); ids.remove(id); getConfig().set("auction.listings", ids); getConfig().set(path, null); saveConfig();
                player.sendMessage(Component.text("Listing cancelled and item returned.", NamedTextColor.GREEN)); return true;
            }
            player.sendMessage("Usage: /ah [sell <price>|cancel <listing-id>]"); return true;
        }
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
            openHomeMenu(player);
            return true;
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
