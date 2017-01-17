package com.deadmandungeons.serverstatus;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An extension of {@link ServerInfo} that includes extra player data relating to the
 * current status of the server which is contained in {@link PlayerStatus}
 */
public class ServerStatusPing extends ServerInfo {
	
	public ServerStatusPing(Address address, PlayersStatus players, Version version, String description, String favicon) {
		super(address, players, version, description, favicon);
	}
	
	@Override
	public PlayersStatus getPlayers() {
		return (PlayersStatus) super.getPlayers();
	}
	
	
	/**
	 * An extension of {@link Players} that includes the current online player count
	 * and a sample (possibly incomplete) list of online players.
	 * <b>Note:</b> This data is not guaranteed to be correct or valid since it is only
	 * what the server responded with when it was pinged which is easily modified.
	 */
	public static class PlayersStatus extends Players {
		
		private final int count;
		private final List<Player> sample;
		
		public PlayersStatus(int max, int count, List<Player> sample) {
			super(max);
			this.count = count;
			this.sample = sample;
		}
		
		public int getCount() {
			return count;
		}
		
		public List<Player> getSample() {
			return Collections.unmodifiableList(sample);
		}
		
	}
	
	public static class Player {
		
		private final UUID id;
		private final String name;
		
		public Player(UUID id, String name) {
			this.id = id;
			this.name = name;
		}
		
		public UUID getId() {
			return id;
		}
		
		public String getName() {
			return name;
		}
		
	}
	
}
