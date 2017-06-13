package com.deadmandungeons.serverstatus.ping;

import com.deadmandungeons.serverstatus.MinecraftServer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An extension of {@link MinecraftServer} that includes extra player data relating to the
 * current status of the server which is contained in {@link PlayersStatus}
 */
public class PingResponse extends MinecraftServer {

    private final int latency;

    public PingResponse(Address address, Description description, PlayersStatus players, int latency) {
        this(address, description, players, null, null, latency);
    }

    public PingResponse(Address address, Description description, PlayersStatus players, Version version, int latency) {
        this(address, description, players, version, null, latency);
    }

    public PingResponse(Address address, Description description, PlayersStatus players, Version version, String favicon, int latency) {
        super(address, description, players, version, favicon);
        this.latency = latency;
    }

    PingResponse(PingResponse other, int latency) {
        this(other.getAddress(), other.getDescription(), other.getPlayers(), other.getVersion(), other.getFavicon(), latency);
    }

    @Override
    public PlayersStatus getPlayers() {
        return (PlayersStatus) super.getPlayers();
    }

    /**
     * @return the latency in milliseconds determined by the ping from this machine to the server and back
     */
    public int getLatency() {
        return latency;
    }


    @Override
    public String toString() {
        return "MinecraftServer{address: " + getAddress() + ", players: " + getPlayers() + ", version: " + getVersion() + ", description: " +
                getDescription() + ", favicon: " + getPrintableFavicon() + ", latency: " + getLatency() + "}";
    }

    /**
     * An extension of {@link Players} that includes the current online player count
     * and a sample (possibly incomplete) list of online players.<p>
     * <b>Note:</b> This data is not guaranteed to be correct or valid since it is easily modified
     * by server plugins usually for the purpose of changing appearance in the Minecraft server list.
     */
    public static class PlayersStatus extends Players {

        private final int count;
        private final List<Player> sample;


        /**
         * Equivalent to {@link #PlayersStatus(int, int, List) PlayersStatus(max, count, null)}
         * @param max the maximum amount of players
         * @param count the current player count
         */
        public PlayersStatus(int max, int count) throws IllegalArgumentException {
            this(max, count, null);
        }

        /**
         * @param max the maximum amount of players
         * @param count the current player count
         * @param sample a list of online players which may be incomplete
         */
        public PlayersStatus(int max, int count, List<Player> sample) throws IllegalArgumentException {
            super(max);
            this.count = count;
            this.sample = (sample != null ? sample : Collections.<Player>emptyList());
        }

        /**
         * <b>Note:</b> This may not be the real player count depending if a server plugin manipulated the response
         * @return the player count
         */
        public int getCount() {
            return count;
        }

        /**
         * @return an unmodifiable list of online players which may be incomplete
         */
        public List<Player> getSample() {
            return Collections.unmodifiableList(sample);
        }

        @Override
        public String toString() {
            return getCount() + "/" + getMax();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getMax(), getCount(), getSample());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof PlayersStatus)) {
                return false;
            }
            PlayersStatus other = (PlayersStatus) obj;
            return getMax() == other.getMax() && getCount() == other.getCount() && getSample().equals(other.getSample());
        }

    }

    /**
     * A class containing the UUID and name of a sample player from a server ping response.
     */
    public static class Player {

        private final UUID id;
        private final String name;

        /**
         * @param id the UUID of the sample player
         * @param name the name of the sample player
         * @throws IllegalArgumentException if id is null
         */
        public Player(UUID id, String name) throws IllegalArgumentException {
            if (id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            this.id = id;
            this.name = (name != null ? name : "");
        }

        /**
         * <b>Note:</b> This may not be a real player UUID depending if a server plugin manipulated the response
         * @return the UUID of this sample player
         */
        public UUID getId() {
            return id;
        }

        /**
         * <b>Note:</b> This may not be a real player name depending if a server plugin manipulated the response
         * @return the name of this sample player
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            if (!getName().isEmpty()) {
                return getId() + "[" + getName() + "]";
            }
            return getId().toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId(), getName());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Player)) {
                return false;
            }
            Player other = (Player) obj;
            return getId().equals(other.getId()) && getName().equals(other.getName());
        }

    }

}
