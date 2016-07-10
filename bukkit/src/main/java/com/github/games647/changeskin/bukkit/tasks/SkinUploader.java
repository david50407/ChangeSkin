package com.github.games647.changeskin.bukkit.tasks;

import com.github.games647.changeskin.bukkit.ChangeSkinBukkit;
import com.github.games647.changeskin.core.model.SkinData;

import java.util.UUID;

import org.bukkit.command.CommandSender;

public class SkinUploader implements Runnable {

    private final ChangeSkinBukkit plugin;
    private final CommandSender invoker;

    private final UUID ownerId;
    private final UUID accessToken;
    private final String url;

    private final String name;

    public SkinUploader(ChangeSkinBukkit plugin, CommandSender invoker
            , UUID ownerId, UUID accessToken, String url, String name) {
        this.plugin = plugin;
        this.invoker = invoker;
        this.ownerId = ownerId;
        this.accessToken = accessToken;
        this.url = url;
        this.name = name;
    }

    public SkinUploader(ChangeSkinBukkit plugin, CommandSender invoker, UUID ownerId, UUID accessToken, String url) {
        this(plugin, invoker, ownerId, accessToken, url, null);
    }

    @Override
    public void run() {
        String oldSkinUrl = plugin.getCore().getMojangSkinApi().getSkinUrl(ownerId);

        plugin.getCore().getMojangSkinApi().changeSkin(ownerId, accessToken, url, false);

        SkinData newSkin = plugin.getCore().getMojangSkinApi().downloadSkin(ownerId);
        plugin.getStorage().save(newSkin);

        plugin.getCore().getMojangSkinApi().changeSkin(ownerId, accessToken, oldSkinUrl, false);        
        plugin.sendMessage(invoker, "skin-uploaded");
    }  
}
