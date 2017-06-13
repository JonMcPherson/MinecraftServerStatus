package com.deadmandungeons.serverstatus;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * A class containing basic server information that is mostly static and should not change often.
 */
public class MinecraftServer {

    private final Address address;
    private final Description description;
    private final Players players;
    private final Version version;
    private final String favicon;

    /**
     * @param address the resolved server address
     * @param description the server description
     * @param players the server players information
     * @throws IllegalArgumentException if address, description, or players is <code>null</code>
     */
    public MinecraftServer(Address address, Description description, Players players) throws IllegalArgumentException {
        this(address, description, players, null, null);
    }

    /**
     * @param address the resolved server address
     * @param description the server description
     * @param players the server players information
     * @param version the server version information, or <code>null</code> if the server version is unknown
     * @throws IllegalArgumentException if address, description, or players is <code>null</code>
     */
    public MinecraftServer(Address address, Description description, Players players, Version version) throws IllegalArgumentException {
        this(address, description, players, version, null);
    }

    /**
     * @param address the resolved server address
     * @param description the server description
     * @param players the server players information
     * @param version the server version information, or <code>null</code> if the server version is unknown
     * @param favicon the server favicon as a base64 encoded png image, or <code>null</code> if the server has no favicon
     * @throws IllegalArgumentException if address, description, or players is <code>null</code>
     */
    public MinecraftServer(Address address, Description description, Players players, Version version, String favicon)
            throws IllegalArgumentException {
        if (address == null) {
            throw new IllegalArgumentException("address cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
        if (players == null) {
            throw new IllegalArgumentException("players cannot be null");
        }
        this.address = address;
        this.description = description;
        this.players = players;
        this.version = version;
        this.favicon = favicon;
    }

    public MinecraftServer(MinecraftServer other) {
        address = new Address(other.address);
        description = new Description(other.description);
        players = new Players(other.players);
        version = new Version(other.version);
        favicon = other.favicon;
    }

    /**
     * @return the resolved server address
     */
    public Address getAddress() {
        return address;
    }

    /**
     * @return the server description
     */
    public Description getDescription() {
        return description;
    }

    /**
     * @return the server players information
     */
    public Players getPlayers() {
        return players;
    }

    /**
     * <b>Note:</b> This should only return <code>null</code> if MinecraftPinger legacy17 was used
     * @return the server version information, or <code>null</code> if the server version is unknown
     */
    public Version getVersion() {
        return version;
    }

    /**
     * <b>Note:</b> This will only exist when using the non-legacy versions of MinecraftPinger because the favicon
     * did not exist in legacy protocol versions.
     * @return the server favicon as a base64 encoded png image, or <code>null</code> if the server has no favicon
     */
    public String getFavicon() {
        return favicon;
    }


    protected String getPrintableFavicon() {
        String favicon = getFavicon();
        if (favicon != null) {
            int iconDataIndex = favicon.indexOf(',');
            if (iconDataIndex > 0) {
                favicon = favicon.substring(0, iconDataIndex);
            }
        }
        return favicon;
    }

    @Override
    public String toString() {
        return "MinecraftServer{address: " + getAddress() + ", players: " + getPlayers() + ", version: " + getVersion() + ", description: " +
                getDescription() + ", favicon: " + getPrintableFavicon() + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAddress(), getPlayers(), getVersion(), getDescription(), getFavicon());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof MinecraftServer)) {
            return false;
        }
        MinecraftServer other = (MinecraftServer) obj;
        return getAddress().equals(other.getAddress()) && getPlayers().equals(other.getPlayers()) && getVersion().equals(other.getVersion()) &&
                getDescription().equals(other.getDescription()) && Objects.equals(getFavicon(), other.getFavicon());
    }


    /**
     * A class containing a syntactically valid Minecraft server address
     */
    public static class Address {

        private static final int MAX_HOST_NAME_LENGTH = 255;
        private static final int DEFAULT_SERVER_PORT = 25565;

        private final String host;
        private final int port;

        /**
         * This will parse the host (or IP) and optional port number from the given address
         * @param address the Minecraft server address
         * @throws URISyntaxException if the given address is syntactically invalid
         */
        public Address(String address) throws URISyntaxException {
            this(new URI("mc://" + address.trim()));
        }

        /**
         * @param host the host (or IP) of the Minecraft server
         * @param port the port of the Minecraft server
         * @throws URISyntaxException if the given host or port is syntactically invalid
         */
        public Address(String host, int port) throws URISyntaxException {
            this(new URI("mc", null, host.trim(), port, null, null, null));
        }

        private Address(URI uri) throws URISyntaxException {
            if (uri.getHost() == null) {
                throw new URISyntaxException(uri.toString().substring(5), "host cannot be undefined");
            }
            if (uri.getHost().length() > MAX_HOST_NAME_LENGTH) {
                throw new URISyntaxException(uri.toString().substring(5), "host cannot exceed " + MAX_HOST_NAME_LENGTH + "characters");
            }
            if (uri.getPort() > 65535) {
                throw new URISyntaxException(uri.toString().substring(5), "port cannot exceed 65535");
            }
            host = uri.getHost().toLowerCase();
            port = (uri.getPort() > 0 ? uri.getPort() : DEFAULT_SERVER_PORT);
        }

        public Address(Address other) {
            host = other.host;
            port = other.port;
        }

        /**
         * @return the host name or IP of this Minecraft server address
         */
        public String getHost() {
            return host;
        }

        /**
         * The default port number is 25565
         * @return the port number of this Minecraft server address
         */
        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            if (getPort() != DEFAULT_SERVER_PORT) {
                return getHost() + ":" + getPort();
            }
            return getHost();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getHost(), getPort());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Address)) {
                return false;
            }
            Address other = (Address) obj;
            return getPort() == other.getPort() && getHost().equals(other.getHost());
        }

    }

    /**
     * A class containing basic Minecraft server player information
     */
    public static class Players {

        private final int max;

        /**
         * @param max the maximum amount of players
         */
        public Players(int max) {
            this.max = max;
        }

        public Players(Players other) {
            max = other.max;
        }

        /**
         * <b>Note:</b> This may not be the real player maximum depending if a server plugin manipulated the response
         * @return the maximum amount of players
         */
        public int getMax() {
            return max;
        }

        @Override
        public String toString() {
            return "-/" + getMax();
        }

        @Override
        public int hashCode() {
            return getMax();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Players)) {
                return false;
            }
            Players other = (Players) obj;
            return getMax() == other.getMax();
        }

    }

    /**
     * A class containing basic Minecraft server version information.
     * <b>Note:</b> This data is not guaranteed to be correct or valid since it is easily modified
     * by server plugins usually for the purpose of changing appearance in the Minecraft server list.
     */
    public static class Version {

        private final String name;
        private final int protocol;

        /**
         * @param name the version name of the Minecraft server software
         * @param protocol the protocol number of the Minecraft server network interface
         */
        public Version(String name, int protocol) {
            this.name = name;
            this.protocol = protocol;
        }

        public Version(Version other) {
            name = other.name;
            protocol = other.protocol;
        }

        /**
         * <b>Note:</b> This may not be the real version name depending if a server plugin manipulated the response
         * @return the version name of the server software
         */
        public String getName() {
            return name;
        }

        /**
         * <b>Note:</b> This may not be the real protocol number depending if a server plugin manipulated the response.
         * And due to a server-side bug, this will incorrectly be 127 if using MinecraftPing legacy47 for a server on version 1.7.x and above.
         * @return the protocol number of the server network interface
         */
        public int getProtocol() {
            return protocol;
        }

        @Override
        public String toString() {
            if (getProtocol() > 0) {
                return getName() + "(" + getProtocol() + ")";
            }
            return getName();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName(), getProtocol());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Version)) {
                return false;
            }
            Version other = (Version) obj;
            return Objects.equals(getName(), other.getName()) && getProtocol() == other.getProtocol();
        }

    }

    /**
     * A class containing the Minecraft server description in two formats.
     * <ul>
     * <li>Legacy Format: {@link #getText()}</li>
     * <li>Component Format: {@link #asComponent()}</li>
     * </ul>
     */
    public static class Description {

        private final TextComponent component;
        private final String text;

        public Description(TextComponent component) {
            this.component = component;
            this.text = component.toLegacyText();
        }

        public Description(String text) {
            BaseComponent[] components = TextComponent.fromLegacyText(text);
            this.component = (components.length > 1 ? new TextComponent(components) : (TextComponent) components[0]);
            this.text = text;
        }

        public Description(Description other) {
            this.component = other.component;
            this.text = other.text;
        }

        /**
         * @return the server description in the legacy Minecraft text format
         * @see <a href="http://minecraft.gamepedia.com/Formatting_codes">Formatting Codes</a>
         */
        public String getText() {
            return text;
        }

        /**
         * @return the server description as a new TextComponent instance
         */
        public TextComponent asComponent() {
            return new TextComponent(component);
        }

        @Override
        public String toString() {
            return getText();
        }

        @Override
        public int hashCode() {
            return getText().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Description)) {
                return false;
            }
            Description other = (Description) obj;
            return getText().equals(other.getText());
        }

    }

}
