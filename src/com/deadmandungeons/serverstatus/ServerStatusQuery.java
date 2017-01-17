package com.deadmandungeons.serverstatus;

import java.util.Collections;
import java.util.List;

public class ServerStatusQuery extends ServerInfo {
	
	private final String mapName;
	private final String serverType;
	private final List<String> plugins;
	
	public ServerStatusQuery(Address address, PlayersList players, Version version, String description, String favicon, String mapName,
			String serverType, List<String> plugins) {
		super(address, players, version, description, favicon);
		this.mapName = mapName;
		this.serverType = serverType;
		this.plugins = plugins;
	}
	
	
	@Override
	public Players getPlayers() {
		return super.getPlayers();
	}
	
	
	public String getMapName() {
		return mapName;
	}
	
	public String getServerType() {
		return serverType;
	}
	
	public List<String> getPlugins() {
		return Collections.unmodifiableList(plugins);
	}
	
	
	public static class PlayersList extends Players {
		
		private final int count;
		private final List<String> list;
		
		public PlayersList(int max, int count, List<String> list) {
			super(max);
			this.count = count;
			this.list = list;
		}
		
		public int getCount() {
			return count;
		}
		
		public List<String> getList() {
			return Collections.unmodifiableList(list);
		}
		
	}
	
}
