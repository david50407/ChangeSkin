package com.github.games647.changeskin.bukkit.commands;

import com.github.games647.changeskin.bukkit.ChangeSkinBukkit;
import com.github.games647.changeskin.bukkit.tasks.SkinUploader;
import com.github.games647.changeskin.core.ChangeSkinCore;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SkinUploadCommand implements CommandExecutor {

    private final ChangeSkinBukkit plugin;

    public SkinUploadCommand(ChangeSkinBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(sender, "upload-noargs");
        } else {
            String url = args[0];
            if (!url.startsWith("http://") || !url.startsWith("https://")) {
                plugin.sendMessage(sender, "no-valid-url");
            } else {
                List<String> accounts = plugin.getConfig().getStringList("upload-accounts");
                if (accounts.isEmpty()) {
                    plugin.sendMessage(sender, "no-accounts");
                } else {
                    String account = accounts.get(0);
                    UUID ownerId = ChangeSkinCore.parseId(account.split(":")[0]);
                    UUID accessToken = ChangeSkinCore.parseId(account.split(":")[1]);
                    
                    SkinUploader skinUploader = new SkinUploader(plugin, sender, ownerId, accessToken, url);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, skinUploader);
                }
            }
        }

        return true;
    }
}
