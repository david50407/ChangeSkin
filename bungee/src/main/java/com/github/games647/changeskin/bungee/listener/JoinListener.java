package com.github.games647.changeskin.bungee.listener;

import com.github.games647.changeskin.bungee.ChangeSkinBungee;
import com.github.games647.changeskin.core.NotPremiumException;
import com.github.games647.changeskin.core.RateLimitException;
import com.github.games647.changeskin.core.SkinData;
import com.github.games647.changeskin.core.UserPreferences;

import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import net.md_5.bungee.BungeeCord;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class JoinListener implements Listener {

    protected final ChangeSkinBungee plugin;
    private final Random random = new Random();

    public JoinListener(ChangeSkinBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PostLoginEvent postLoginEvent) {
        ProxiedPlayer player = postLoginEvent.getPlayer();

        PendingConnection connection = player.getPendingConnection();
        UUID playerUuid = player.getUniqueId();
        String playerName = connection.getName();

        final UserPreferences preferences = plugin.getStorage().getPreferences(playerUuid);
//        plugin.getCore().startSession(playerUuid, preferences);
        if (preferences.getTargetSkin() == null && plugin.getConfiguration().getBoolean("restoreSkins")) {
            refetchSkin(preferences, playerName);
        }

        //updates to the chosen one
//        final UserPreferences preferences = plugin.getCore().getLoginSession(player.getUniqueId());
        SkinData targetSkin = preferences.getTargetSkin();
        if (targetSkin == null) {
//            final SkinData skinData = getSkinIfPresent(player);
//            if (skinData == null) {
//                setRandomSkin(preferences, player);
//            } else {
//                plugin.applySkin(player, skinData);
//
//                preferences.setTargetSkin(targetSkin);
//                ProxyServer.getInstance().getScheduler().runAsync(plugin, new Runnable() {
//                    @Override
//                    public void run() {
//                        plugin.getStorage().save(skinData);
//                        plugin.getStorage().save(preferences);
//                    }
//                });
//            }
        } else {
            plugin.applySkin(player, targetSkin);
        }

//        plugin.getCore().endSession(player.getUniqueId());
    }

//    private SkinData getSkinIfPresent(ProxiedPlayer player) {
//        //try to use the existing and put it in the cache so we use it for others
//        InitialHandler initialHandler = (InitialHandler) player.getPendingConnection();
//        LoginResult loginProfile = initialHandler.getLoginProfile();
//        //this is null on offline mode
//        if (loginProfile != null) {
//            Property[] properties = loginProfile.getProperties();
//            for (Property property : properties) {
//                //found a skin
//                return new SkinData(property.getValue(), property.getSignature());
//            }
//        }
//
//        return null;
//    }
//
//    private void setRandomSkin(final UserPreferences preferences, ProxiedPlayer player) {
//        //skin wasn't found and there are no preferences so set a default skin
//        List<SkinData> defaultSkins = plugin.getCore().getDefaultSkins();
//        if (!defaultSkins.isEmpty()) {
//            int randomIndex = random.nextInt(defaultSkins.size());
//
//            final SkinData targetSkin = defaultSkins.get(randomIndex);
//            if (targetSkin != null) {
//                preferences.setTargetSkin(targetSkin);
//                plugin.applySkin(player, targetSkin);
//
//                ProxyServer.getInstance().getScheduler().runAsync(plugin, new Runnable() {
//                    @Override
//                    public void run() {
//                        plugin.getStorage().save(preferences);
//                    }
//                });
//            }
//        }
//    }

      private void refetchSkin(final UserPreferences prefereces, final String playerName) {
//        preLoginEvent.registerIntent(plugin);

//        ProxyServer.getInstance().getScheduler().runAsync(plugin, new Runnable() {
//            @Override
//            public void run() {
//                try {
                    refetch(prefereces, playerName);
//                } finally {
//                    preLoginEvent.completeIntent(plugin);
//                }
//            }
//        });
    }

    private void refetch(UserPreferences preferences, String playerName) {
        UUID ownerUUID = plugin.getCore().getUuidCache().get(playerName);
        if (ownerUUID == null && !plugin.getCore().getCrackedNames().containsKey(playerName)) {
            SkinData skin = plugin.getStorage().getSkin(playerName);
            if (skin != null) {
                plugin.getCore().getUuidCache().put(skin.getName(), skin.getUuid());
                preferences.setTargetSkin(skin);
                save(skin, preferences);
                return;
            }

            try {
                ownerUUID = plugin.getCore().getUUID(playerName);
            } catch (NotPremiumException ex) {
                plugin.getLogger().log(Level.FINE, "Username is not premium on refetch");
                plugin.getCore().getCrackedNames().put(playerName, new Object());
            } catch (RateLimitException ex) {
                plugin.getLogger().log(Level.SEVERE, "Rate limit reached on refetch", ex);
            }
        }

        if (ownerUUID != null) {
            plugin.getCore().getUuidCache().put(playerName, ownerUUID);
            SkinData cachedSkin = plugin.getStorage().getSkin(ownerUUID);
            if (cachedSkin == null) {
                cachedSkin = plugin.getCore().downloadSkin(ownerUUID);
            }

            preferences.setTargetSkin(cachedSkin);
            save(cachedSkin, preferences);
        }
    }

    private void save(final SkinData skin, final UserPreferences preferences) {
        //this can run in the background
        BungeeCord.getInstance().getScheduler().runAsync(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.getStorage().save(skin)) {
                    plugin.getStorage().save(preferences);
                }
            }
        });
    }
}
