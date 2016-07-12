package com.github.games647.changeskin.core;

import com.github.games647.changeskin.core.model.ApiPropertiesModel;
import com.github.games647.changeskin.core.model.McApiProfile;
import com.github.games647.changeskin.core.model.PlayerProfile;
import com.github.games647.changeskin.core.model.RawPropertiesModel;
import com.github.games647.changeskin.core.model.SkinData;
import com.github.games647.changeskin.core.model.mojang.PropertiesModel;
import com.github.games647.changeskin.core.model.mojang.TexturesModel;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Closer;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MojangSkinApi {

    private static final String PROPERTIES_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String MCAPI_SKIN_URL = "https://mcapi.de/api/user/";
    private static final String CHANGE_SKIN_URL = "https://api.mojang.com/user/profile/<uuid>/skin";

    private static final String SKIN_URL = "http://skins.minecraft.net/MinecraftSkins/<name>.png";

    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MCAPI_UUID_URL = "https://mcapi.ca/uuid/player/";

    private static final String VALID_USERNAME = "^\\w{2,16}$";

    private static final int RATE_LIMIT_ID = 429;

    private final Gson gson = new Gson();

    private final ConcurrentMap<Object, Object> requests;
    private final Logger logger;
    private final int rateLimit;
    private final boolean mojangDownload;

    private long lastRateLimit;

    public MojangSkinApi(ConcurrentMap<Object, Object> requests, Logger logger, int rateLimit, boolean mojangDownload) {
        this.requests = requests;

        if (rateLimit > 600) {
            this.rateLimit = 600;
        } else {
            this.rateLimit = rateLimit;
        }

        this.logger = logger;
        this.mojangDownload = mojangDownload;
    }

    public UUID getUUID(String playerName) throws NotPremiumException, RateLimitException {
        logger.log(Level.FINE, "Making UUID->Name request for {0}", playerName);
        if (!playerName.matches(VALID_USERNAME)) {
            return null;
        }

        if (requests.size() >= rateLimit || System.currentTimeMillis() - lastRateLimit < 1_000 * 60 * 10) {
//            logger.fine("STILL WAITING FOR RATE_LIMIT - TRYING SECOND API");
            return getUUIDFromAPI(playerName);
        }

        requests.put(new Object(), new Object());

        Closer closer = Closer.create();
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(UUID_URL + playerName).openConnection();

            if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                throw new NotPremiumException(playerName);
            } else if (httpConnection.getResponseCode() == RATE_LIMIT_ID) {
                logger.info("RATE_LIMIT REACHED - TRYING THIRD-PARTY API");
                lastRateLimit = System.currentTimeMillis();
                return getUUIDFromAPI(playerName);
            }

            InputStreamReader inputReader = closer.register(new InputStreamReader(httpConnection.getInputStream()));
            BufferedReader reader = closer.register(new BufferedReader(inputReader));
            String line = reader.readLine();

            PlayerProfile playerProfile = gson.fromJson(line, PlayerProfile.class);
            return ChangeSkinCore.parseId(playerProfile.getId());
        } catch (IOException | JsonParseException ex) {
            logger.log(Level.SEVERE, "Tried converting player name to uuid", ex);
        } finally {
            try {
                closer.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error closing connection", ex);
            }
        }

        return null;
    }

    public UUID getUUIDFromAPI(String playerName) throws NotPremiumException {
        InputStreamReader inputReader = null;
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(MCAPI_UUID_URL + playerName).openConnection();

            inputReader = new InputStreamReader(httpConnection.getInputStream());
            String line = CharStreams.toString(inputReader);

            PlayerProfile playerProfile = gson.fromJson(line, PlayerProfile[].class)[0];
            String id = playerProfile.getId();
            if (id == null || id.equalsIgnoreCase("null")) {
                throw new NotPremiumException(line);
            }

            return ChangeSkinCore.parseId(id);
        } catch (IOException | JsonParseException ex) {
            logger.log(Level.SEVERE, "Tried converting player name to uuid from third-party api", ex);
        } finally {
            Closeables.closeQuietly(inputReader);
        }

        return null;
    }

    public SkinData downloadSkin(UUID ownerUUID) {
        if (mojangDownload) {
            return downloadSkinFromApi(ownerUUID);
        }

        //unsigned is needed in order to receive the signature
        String uuidString = ownerUUID.toString().replace("-", "") + "?unsigned=false";
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(PROPERTIES_URL + uuidString).openConnection();

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.equals("null")) {
                TexturesModel texturesModel = gson.fromJson(line, TexturesModel.class);

                PropertiesModel[] properties = texturesModel.getProperties();
                if (properties != null && properties.length > 0) {
                    PropertiesModel propertiesModel = properties[0];

                    //base64 encoded skin data
                    String encodedSkin = propertiesModel.getValue();
                    String signature = propertiesModel.getSignature();

                    SkinData skinData = new SkinData(encodedSkin, signature);
                    return skinData;
                }
            }
        } catch (IOException | JsonParseException ex) {
            logger.log(Level.SEVERE, "Tried downloading skin data from Mojang", ex);
        }

        return null;
    }

    public SkinData downloadSkinFromApi(UUID ownerUUID) {
        //unsigned is needed in order to receive the signature
        String uuidStrip = ownerUUID.toString().replace("-", "");
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(MCAPI_SKIN_URL + uuidStrip).openConnection();

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
            String line = reader.readLine();
            McApiProfile profile = gson.fromJson(line, McApiProfile.class);

            ApiPropertiesModel properties = profile.getProperties();
            if (properties != null && properties.getRaw().length > 0) {
                RawPropertiesModel propertiesModel = properties.getRaw()[0];

                //base64 encoded skin data
                String encodedSkin = propertiesModel.getValue();
                String signature = propertiesModel.getSignature();

                SkinData skinData = new SkinData(encodedSkin, signature);
                return skinData;
            }
        } catch (IOException | JsonParseException ex) {
            logger.log(Level.SEVERE, "Tried downloading skin data from Mojang", ex);
        }

        return null;
    }

    public boolean changeSkin(UUID ownerId, UUID accessToken, String sourceUrl, boolean slimModel) {
        String url = CHANGE_SKIN_URL.replace("<uuid>", ownerId.toString().replace('-', ' '));

        Closer closer = Closer.create();
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) new URL(url).openConnection();

            httpConnection.setRequestMethod("POST");
            httpConnection.addRequestProperty("Authorization", "Bearer " + accessToken.toString().replace('-', ' '));

            OutputStream outputStream = closer.register(httpConnection.getOutputStream());
            OutputStreamWriter streamWriter = closer.register(new OutputStreamWriter(outputStream, Charsets.UTF_8));
            BufferedWriter writer = closer.register(new BufferedWriter(streamWriter));

            if (slimModel) {
                writer.write("model=" + URLEncoder.encode("slim", Charsets.UTF_8.name()));
            } else {
                writer.write("model=");
            }

            writer.write("&url=" + URLEncoder.encode(sourceUrl, Charsets.UTF_8.name()));
            writer.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
            logger.fine(reader.readLine());

            return true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Tried downloading skin data from Mojang", ex);
        } finally {
            try {
                closer.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error closing connection", ex);
            }
        }

        return false;
    }

    public String getSkinUrl(UUID ownerId) {
        
    }
}
