/*
 * The MIT License
 * 
 * Copyright 2017 Kory3.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
