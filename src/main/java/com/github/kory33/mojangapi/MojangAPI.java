package com.github.kory33.mojangapi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;

/**
 * Wrapper util class for making requests to Mojang API
 * @author kory
 *
 */
public class MojangAPI {
    public static String USERNAME_TO_UUID_ENDPOINT_URL = "https://api.mojang.com/users/profiles/minecraft/";
    
    private static UUID convertRawHexDataToUUID(String hexadecimal) {
        String mostSig = hexadecimal.substring(0, 16);
        String leastSig = hexadecimal.substring(16);
        long mostSigBits = Long.parseUnsignedLong(mostSig, 16);
        long leastSigBits = Long.parseUnsignedLong(leastSig, 16);
        return new UUID(mostSigBits, leastSigBits);
    }
    
    public static UUID getUUIDFromUsername(String userName) {
        URL endpoint = null;
        try {
            endpoint = new URL(USERNAME_TO_UUID_ENDPOINT_URL + userName);
            HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // construct response text
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                JSONObject responseJson = new JSONObject(builder.toString());
                return convertRawHexDataToUUID(responseJson.getString("id"));
            }
            
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static CompletableFuture<UUID> asyncGetUUIDFromUsername(String userName) {
        return CompletableFuture.supplyAsync(() -> MojangAPI.getUUIDFromUsername(userName));
    }
}
