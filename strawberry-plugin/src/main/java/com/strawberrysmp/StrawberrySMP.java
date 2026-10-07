package com.strawberrysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class StrawberrySMP extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Strawberry SMP enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Strawberry SMP disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("strawberry")) return false;
        sender.sendMessage(Component.text("🍓 Strawberry SMP", NamedTextColor.RED));
        sender.sendMessage(Component.text("The Strawberry SMP plugin is working!", NamedTextColor.WHITE));
        return true;
    }
}
