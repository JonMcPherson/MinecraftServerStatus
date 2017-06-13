package com.deadmandungeons.serverstatus.ping;

import com.deadmandungeons.serverstatus.InetServerAddress;
import com.deadmandungeons.serverstatus.MinecraftServer;
import com.deadmandungeons.serverstatus.ping.Connection.RequestPacket;
import com.deadmandungeons.serverstatus.ping.Connection.ResponsePacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.chat.TextComponentSerializer;
import net.md_5.bungee.chat.TranslatableComponentSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Pinger} implementation that uses the current SLP protocol for servers on 1.7.x and above.<br>
 * For servers on older versions, use {@link #legacy47()} or {@link #legacy17()}
 * @see <a href="http://wiki.vg/Server_List_Ping#Current">Current SLP protocol</a>
 */
public class MinecraftPinger implements Pinger {

    private static final byte HANDSHAKE_PACKET_ID = 0x00;
    private static final byte STATUS_PACKET_ID = 0x00;
    private static final byte PING_PACKET_ID = 0x01;
    private static final byte PROTOCOL_VERSION = 47;
    private static final byte HANDSHAKE_STATE = 1;
    private static final long PING_TOKEN = 3735928559L; // Arbitrary value

    private final Gson gson = new GsonBuilder().registerTypeAdapter(PingResponse.class, new ResponseDeserializer())
            .registerTypeAdapter(BaseComponent.class, new ComponentSerializer())
            .registerTypeAdapter(TextComponent.class, new TextComponentSerializer())
            .registerTypeAdapter(TranslatableComponent.class, new TranslatableComponentSerializer()).create();

    private final InetServerAddress address;
    private final int timeout;

    /**
     * @param address the address of the Minecraft server to connect with for each ping operation
     * @param timeout the timeout in milliseconds that should be used when connecting or reading from the socket
     */
    public MinecraftPinger(InetServerAddress address, int timeout) {
        this.address = address;
        this.timeout = timeout;
    }

    @Override
    public int ping() throws IOException {
        try (Connection connection = connect()) {
            handshake(connection);

            return ping(connection);
        }
    }

    @Override
    public MinecraftServer pingServer() throws IOException {
        try (Connection connection = connect()) {
            handshake(connection);
            String response = readStatus(connection);

            return new MinecraftServer(parseResponse(response));
        }
    }

    @Override
    public PingResponse pingServerStatus() throws IOException {
        try (Connection connection = connect()) {
            handshake(connection);
            String response = readStatus(connection);

            int latency;
            try {
                latency = ping(connection);
            } catch (IOException e) {
                // Some servers may break the protocol due to nonstandard functionality.
                // So just use the latency from connection handshake as a fallback.
                latency = connection.getLatency();
            }

            return new PingResponse(parseResponse(response), latency);
        }
    }

    /**
     * For servers using a legacy protocol; Versions 47 (1.4.x) to 78 (1.6.x).<br>
     * Most servers are backward compatible and should support this protocol version,
     * but there are some known servers that do not.
     * <p>
     * <b>Note:</b> This protocol excludes the server {@link MinecraftServer#getFavicon() favicon} from the response
     * @return a Pinger instance using the legacy SLP protocol starting with version 47
     * @see <a href="http://wiki.vg/Server_List_Ping#1.6">Legacy SLP protocol</a>
     */
    public Pinger legacy47() {
        return new LegacyPinger47();
    }

    /**
     * For servers using a legacy protocol; Versions 17 (Beta-1.8) to 39 (1.3.x).<br>
     * Most servers are backward compatible and should support this protocol version,
     * but there are some known servers that do not.
     * <p>
     * <b>Note:</b> This protocol excludes the server {@link MinecraftServer#getVersion() version}
     * and {@link MinecraftServer#getFavicon() favicon} from the response.
     * @return a Pinger instance using the legacy SLP protocol starting with version 17
     * @see <a href="http://wiki.vg/Server_List_Ping#Beta_1.8_to_1.3">Legacy SLP protocol</a>
     */
    public Pinger legacy17() {
        return new LegacyPinger17();
    }


    private Connection connect() throws ConnectException {
        return Connection.to(address).timeout(timeout).buffered().tcpNoDelay().connect();
    }

    private void handshake(Connection connection) throws IOException {
        RequestPacket handshakePacket = connection.createPacket();

        handshakePacket.writeVarInt(HANDSHAKE_PACKET_ID);
        handshakePacket.writeVarInt(PROTOCOL_VERSION);
        handshakePacket.writeVarUTF(address.getHost());
        handshakePacket.writeShort(address.getPort());
        handshakePacket.writeVarInt(HANDSHAKE_STATE);

        handshakePacket.send();
    }

    private int ping(Connection connection) throws IOException {
        RequestPacket pingPacket = connection.createPacket();
        pingPacket.writeVarInt(PING_PACKET_ID);
        pingPacket.writeLong(PING_TOKEN);

        long timeSent = pingPacket.send();

        ResponsePacket pongPacket = connection.readPacket();
        int id = pongPacket.readVarInt();
        if (id != PING_PACKET_ID) {
            throw new IOException("Received invalid ping response packet");
        }
        long pongToken = pongPacket.readLong();
        if (pongToken != PING_TOKEN) {
            throw new IOException("Received mangled ping response packet");
        }

        return (int) TimeUnit.NANOSECONDS.toMillis(pongPacket.getTimeReceived() - timeSent);
    }

    private String readStatus(Connection connection) throws IOException {
        RequestPacket request = connection.createPacket();
        request.writeVarInt(STATUS_PACKET_ID);
        request.send();

        ResponsePacket response = connection.readPacket();
        int id = response.readVarInt();
        if (id != STATUS_PACKET_ID) {
            throw new IOException("Received invalid status response packet");
        }
        return response.readVarUTF();
    }

    private PingResponse parseResponse(String response) throws InvalidServerResponse {
        try {
            return gson.fromJson(response, PingResponse.class);
        } catch (JsonParseException e) {
            throw new InvalidServerResponse(e.getMessage());
        }
    }


    /**
     * Thrown to indicate that the status response received from a pinged server is invalid
     */
    public static class InvalidServerResponse extends IOException {

        private InvalidServerResponse(String reason) {
            this(reason, null);
        }

        private InvalidServerResponse(String reason, Throwable cause) {
            super("Received invalid status response: " + reason, cause);
        }

    }


    private class ResponseDeserializer implements JsonDeserializer<PingResponse> {

        @Override
        public PingResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject responseObject = json.getAsJsonObject();

            MinecraftServer.Description description = parseResponseDescription(responseObject, context);

            PingResponse.PlayersStatus players = parseResponsePlayers(responseObject);

            MinecraftServer.Version version = parseResponseVersion(responseObject);

            String favicon = null;
            JsonElement faviconElem = responseObject.get("favicon");
            if (faviconElem != null) {
                if (!faviconElem.isJsonPrimitive() || !faviconElem.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("'favicon' element is not the expected type (string)");
                }
                favicon = faviconElem.getAsString();
            }

            return new PingResponse(address, description, players, version, favicon, -1);
        }

        private MinecraftServer.Description parseResponseDescription(JsonObject responseObject, JsonDeserializationContext context)
                throws JsonParseException {
            JsonElement descriptionElem = responseObject.get("description");
            if (descriptionElem == null) {
                throw new JsonParseException("missing 'description' element");
            }
            if (descriptionElem.isJsonObject()) {
                TextComponent component = context.deserialize(descriptionElem, TextComponent.class);
                return new MinecraftServer.Description(component);
            } else if (descriptionElem.isJsonPrimitive() && descriptionElem.getAsJsonPrimitive().isString()) {
                return new MinecraftServer.Description(descriptionElem.getAsString());
            } else {
                throw new JsonParseException("'description' element is not the expected type (string or object)");
            }
        }

        private MinecraftServer.Version parseResponseVersion(JsonObject responseObject) throws JsonParseException {
            JsonElement versionElem = responseObject.get("version");
            if (versionElem == null) {
                throw new JsonParseException("missing 'version' element");
            }
            if (!versionElem.isJsonObject()) {
                throw new JsonParseException("'version' element is not the expected type (object)");
            }
            JsonObject versionObject = versionElem.getAsJsonObject();

            String versionName = parseStringElement(versionObject, "version", "name");
            int versionProtocol = parseIntElement(versionObject, "version", "protocol");

            return new MinecraftServer.Version(versionName, versionProtocol);
        }

        private PingResponse.PlayersStatus parseResponsePlayers(JsonObject responseObject) throws JsonParseException {
            JsonElement playersElem = responseObject.get("players");
            if (playersElem == null) {
                throw new JsonParseException("missing 'players' element");
            }
            if (!playersElem.isJsonObject()) {
                throw new JsonParseException("'players' element is not the expected type (object)");
            }
            JsonObject playersObject = playersElem.getAsJsonObject();

            int playerMax = parseIntElement(playersObject, "players", "max");
            int playerCount = parseIntElement(playersObject, "players", "online");

            List<PingResponse.Player> playerSample = new ArrayList<>();
            JsonElement playerSampleElem = playersObject.get("sample");
            if (playerSampleElem != null) {
                if (!playerSampleElem.isJsonArray()) {
                    throw new JsonParseException("players 'sample' element is not the expected type (array)");
                }
                for (JsonElement playerElement : playerSampleElem.getAsJsonArray()) {
                    if (!playerElement.isJsonObject()) {
                        throw new JsonParseException("players 'sample' array does not contain the expected type (object)");
                    }
                    JsonObject playerObject = playerElement.getAsJsonObject();

                    String idStr = parseStringElement(playerObject, "player", "id");
                    if (idStr.length() == 32) {
                        idStr = idStr.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                    }
                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException e) {
                        throw new JsonParseException("player 'id' element is not a valid UUID string");
                    }

                    String name = parseStringElement(playerObject, "player", "name");

                    playerSample.add(new PingResponse.Player(id, name));
                }
            }

            return new PingResponse.PlayersStatus(playerMax, playerCount, playerSample);
        }

        private String parseStringElement(JsonObject parentObject, String parentObjectName, String elementName) throws JsonParseException {
            JsonElement element = parentObject.get(elementName);
            if (element == null) {
                throw new JsonParseException("missing " + parentObjectName + " '" + elementName + "' element");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException(parentObjectName + " '" + elementName + "' element is not the expected type (string)");
            }
            return element.getAsString();
        }

        private int parseIntElement(JsonObject parentObject, String parentObjectName, String elementName) throws JsonParseException {
            JsonElement element = parentObject.get(elementName);
            if (element == null) {
                throw new JsonParseException("missing " + parentObjectName + " '" + elementName + "' element");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException(parentObjectName + " '" + elementName + "' element is not the expected type (integer)");
            }
            return element.getAsInt();
        }

    }


    private abstract class LegacyPinger implements Pinger {

        protected static final int STATUS_PACKET_ID = 0xFE;

        @Override
        public int ping() throws IOException {
            try (Connection connection = connect()) {
                return ping(connection.getOutputStream(), connection.getInputStream());
            }
        }

        @Override
        public MinecraftServer pingServer() throws IOException {
            return new MinecraftServer(pingServerStatus());
        }

        @Override
        public PingResponse pingServerStatus() throws IOException {
            try (Connection connection = connect()) {
                DataOutputStream output = connection.getOutputStream();
                DataInputStream input = connection.getInputStream();

                int latency = ping(output, input);

                int length = input.readUnsignedShort();
                if (length <= 0) {
                    throw new IOException("Received invalid status response packet");
                }
                byte[] responseBytes = new byte[length * 2];
                input.readFully(responseBytes);

                String response = new String(responseBytes, StandardCharsets.UTF_16BE);

                return parseResponse(response, latency);
            }
        }


        private Connection connect() throws ConnectException {
            // tcpNoDelay and buffering is unnecessary
            return Connection.to(address).timeout(timeout).connect();
        }

        // There is no ping/pong scheme in legacy protocol so calculate latency from a normal status request and response
        private int ping(DataOutputStream output, DataInputStream input) throws IOException {
            byte[] requestData = getRequestData();
            long timeSent = System.nanoTime();
            output.write(requestData);

            int id = input.readUnsignedByte();
            if (id != 0xFF) {
                throw new IOException("Received invalid status response packet");
            }
            long timeReceived = System.nanoTime();
            return (int) TimeUnit.NANOSECONDS.toMillis(timeReceived - timeSent);
        }


        protected int parseIntField(String field, String fieldName) throws InvalidServerResponse {
            try {
                return Integer.parseInt(field);
            } catch (NumberFormatException e) {
                throw new InvalidServerResponse(fieldName + " field is not the expected type (integer)", e);
            }
        }

        protected abstract byte[] getRequestData() throws IOException;

        protected abstract PingResponse parseResponse(String response, int latency) throws InvalidServerResponse;

    }

    /**
     * For servers on legacy protocol version 47 (1.4.x) and above
     */
    private class LegacyPinger47 extends LegacyPinger {

        private static final byte PROTOCOL_VERSION = 74;
        private static final String REQUEST_STRING_74 = "MC|PingHost";
        private static final String RESPONSE_PREFIX_47 = "\u00A71\0";
        private static final String FIELD_SEPARATOR_47 = "\u0000";

        @Override
        protected byte[] getRequestData() throws IOException {
            ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
            DataOutputStream request = new DataOutputStream(requestBytes);

            int hostLength = address.getHost().length();

            request.writeByte(STATUS_PACKET_ID);
            request.writeByte(0x01); // Server list Ping payload
            request.writeByte(0xFA); // Packet identifier
            request.writeShort(REQUEST_STRING_74.length());
            request.write(REQUEST_STRING_74.getBytes(StandardCharsets.UTF_16BE));
            request.writeShort(7 + (hostLength * 2));
            request.writeByte(PROTOCOL_VERSION);
            request.writeShort(hostLength);
            request.write(address.getHost().getBytes(StandardCharsets.UTF_16BE));
            request.writeInt(address.getPort());

            return requestBytes.toByteArray();
        }

        @Override
        protected PingResponse parseResponse(String response, int latency) throws InvalidServerResponse {
            int prefixLength = RESPONSE_PREFIX_47.length();
            if (!response.startsWith(RESPONSE_PREFIX_47)) {
                throw new InvalidServerResponse("first " + prefixLength + " characters are not the expected prefix values");
            }
            String[] responseFields = response.substring(prefixLength).split(FIELD_SEPARATOR_47);
            if (responseFields.length != 5) {
                throw new InvalidServerResponse("expected 5 null delimited fields");
            }

            int versionProtocol = parseIntField(responseFields[0], "protocol");
            String versionName = responseFields[1];
            MinecraftServer.Version version = new MinecraftServer.Version(versionName, versionProtocol);

            String descriptionText = responseFields[2];
            MinecraftServer.Description description = new MinecraftServer.Description(descriptionText);

            int playerCount = parseIntField(responseFields[3], "player count");
            int playerMax = parseIntField(responseFields[4], "player max");
            PingResponse.PlayersStatus players = new PingResponse.PlayersStatus(playerMax, playerCount);

            return new PingResponse(address, description, players, version, latency);
        }

    }

    private class LegacyPinger17 extends LegacyPinger {

        private static final char FIELD_SEPARATOR_17 = '\u00A7';

        @Override
        protected byte[] getRequestData() throws IOException {
            return new byte[]{(byte) STATUS_PACKET_ID};
        }

        @Override
        protected PingResponse parseResponse(String response, int latency) throws InvalidServerResponse {
            // The first field is the MOTD (description) which may contain the response field separator character...
            // So we split the fields starting from the end and limited to the expected field count
            String[] responseFields = new String[3];
            for (int i = responseFields.length - 1, n = response.length(); i >= 0; i--) {
                if (i > 0) {
                    int separatorIndex = response.lastIndexOf(FIELD_SEPARATOR_17, n - 1);
                    if (separatorIndex == -1) {
                        throw new InvalidServerResponse("expected " + responseFields.length + " fields delimited by " + FIELD_SEPARATOR_17);
                    }
                    responseFields[i] = response.substring(separatorIndex + 1, n);
                    n = separatorIndex;
                } else {
                    responseFields[i] = response.substring(0, n);
                }
            }

            String descriptionText = responseFields[0];
            MinecraftServer.Description description = new MinecraftServer.Description(descriptionText);

            int playerCount = parseIntField(responseFields[1], "player count");
            int playerMax = parseIntField(responseFields[2], "player max");
            PingResponse.PlayersStatus players = new PingResponse.PlayersStatus(playerMax, playerCount);

            return new PingResponse(address, description, players, latency);
        }
    }

}
