package com.javabean.paintballctf;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import com.javabean.pbguns.PaintballGuns;

import net.md_5.bungee.api.ChatColor;

public class PBCTFGame{
	private Arena arena;

	//creates instance of inner class GameTimer
	GameTimer gameTimer = new GameTimer();
	
	//the plugin
	private Plugin plugin;
	
	//there is a game going on, players may not join
	private boolean inProgress = false;
	//time elapsed since game started
	int timeElapsed = 0;
	//game length in seconds
	int gameLength = 60;
	
	//map with team name and their data for that game
	private HashMap<String, TeamGameData> teamData = new HashMap<String, TeamGameData>();
	
	//map with player name and their data for that game
	private HashMap<String, PlayerGameData> playerData = new HashMap<String, PlayerGameData>();
	
	public PBCTFGame(Arena a, Plugin p){
		arena = a;
		plugin = p;
		for(String teamName : arena.getTeams().keySet()){
			teamData.put(teamName, new TeamGameData(arena.getTeam(teamName)));
		}
	}
	
	//inner class for game timer
	class GameTimer extends BukkitRunnable{
		
		@Override
		public void run(){
			timeElapsed++;
			//game over
			if(timeElapsed == gameLength){
				makeSoundAtPlayers(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
				//end game
				end();
				//stop this BukkitRunnable
				cancel();
			}
			//5 second countdown
			else if(timeElapsed + 5 >= gameLength){
				notifyPlayers(ChatColor.DARK_AQUA + "" + (gameLength - timeElapsed) + " seconds left.");
				makeSoundAtPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
			}
			else if(timeElapsed + 60 == gameLength){
				notifyPlayers(ChatColor.DARK_AQUA + "1 minute left in arena: " + arena.getName());
				makeSoundAtPlayers(Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 2.0f);
			}
			else if(timeElapsed + 120 == gameLength){
				notifyPlayers(ChatColor.DARK_AQUA + "2 minutes left in arena: " + arena.getName());
				makeSoundAtPlayers(Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 2.0f);
			}
		}
	}
	
	public void setArena(Arena a){
		arena = a;
	}
	
	public Arena getArena(){
		return arena;
	}
	
	@SuppressWarnings("deprecation")
	public void start(){
		inProgress = true;
		//prepare flags for game
		for(String teamName : arena.getTeams().keySet()){
			Team team = arena.getTeam(teamName);
			for(String flagName : team.getFlags().keySet()){
				Flag flag = team.getFlag(flagName);
				team.getFlag(flagName).setBlockMaterial(flag.getLocation().getBlock().getType());
			}
		}
		//prepare players for game
		for(String playerName : playerData.keySet()){
			PlayerGameData playerGameData = playerData.get(playerName);
			Team team = playerGameData.getTeam();
			Player player = playerGameData.getPlayer();
			
			//teleport player to random team spawn
			player.teleport(team.getRandomSpawn().getLocation());
			
			//populate inventory with proper gear
			//	colored top half, iron lower half
			//get team color
			java.awt.Color javaRGB = java.awt.Color.decode(team.getHexColor());
			Color teamColor = Color.fromRGB(javaRGB.getRed(), javaRGB.getGreen(), javaRGB.getBlue());
			
			//give leather helmet
			ItemStack leatherHelm = new ItemStack(Material.LEATHER_HELMET, 1);
			LeatherArmorMeta lam = (LeatherArmorMeta)leatherHelm.getItemMeta();
			lam.setColor(teamColor);
			leatherHelm.setItemMeta(lam);
			player.getInventory().setHelmet(leatherHelm);
			
			//give leather chestplate
			ItemStack leatherChestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
			lam = (LeatherArmorMeta)leatherChestplate.getItemMeta();
			lam.setColor(teamColor);
			leatherChestplate.setItemMeta(lam);
			player.getInventory().setChestplate(leatherChestplate);
			
			//give leather leggings
			ItemStack leatherLeggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
			lam = (LeatherArmorMeta)leatherLeggings.getItemMeta();
			lam.setColor(teamColor);
			leatherLeggings.setItemMeta(lam);
			player.getInventory().setLeggings(leatherLeggings);
			
			//give leather boots
			ItemStack leatherBoots = new ItemStack(Material.LEATHER_BOOTS, 1);
			lam = (LeatherArmorMeta)leatherBoots.getItemMeta();
			lam.setColor(teamColor);
			leatherBoots.setItemMeta(lam);
			player.getInventory().setBoots(leatherBoots);
			
			PaintballGuns.giveGun(player, 3);
			PaintballGuns.giveGun(player, 0);
			
			makeSoundAtPlayers(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
			
			player.setHealth(player.getMaxHealth());
		}
		//runs task immediately and every 20 ticks
		gameTimer.runTaskTimer(plugin, 0, 20);
		notifyPlayers(ChatColor.AQUA + "Game started in arena: " + arena.getName() + " for " + secondsToWords(gameLength));
	}
	
	@SuppressWarnings("deprecation")
	public void end(){
		notifyPlayers(ChatColor.AQUA + "Game ended in arena: " + arena.getName());
		ArrayList<String> avoidModificationWhileIterating = new ArrayList<String>();
		for(String playerName : playerData.keySet()){
			PlayerGameData playerGameData = playerData.get(playerName);
			Player player = playerGameData.getPlayer();
			
			for(PotionEffect effect : player.getActivePotionEffects()){
				player.removePotionEffect(effect.getType());
			}
			
			//notify players it's over and tell them about their team game stats and personal stats
			player.sendMessage(ChatColor.GOLD + "--------------------------------------------");
			player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "              ***Game Stats***");
			player.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.UNDERLINE + "Flags Captured");
			int mostFlags = Integer.MIN_VALUE;
			Team teamMostFlags = null;
			for(String teamName : teamData.keySet()){
				if(teamData.get(teamName).getFlagsCaptured() > mostFlags){
					mostFlags = teamData.get(teamName).getFlagsCaptured();
					teamMostFlags = teamData.get(teamName).getTeam();
				}
				player.sendMessage(ChatColor.GREEN + "" + (teamName.equals(playerGameData.getTeam().getName()) ? ChatColor.BOLD + "You " + ChatColor.GOLD : "") + teamName + ": " + ChatColor.AQUA + teamData.get(teamName).getFlagsCaptured());
			}
			player.sendMessage(ChatColor.LIGHT_PURPLE + teamMostFlags.getName() + " team won!");
			player.sendMessage("");
			player.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.UNDERLINE + "Personal Stats");
			player.sendMessage(ChatColor.GREEN + "Flags Captured:    " + ChatColor.AQUA + playerGameData.getFlagsCaptured());
			player.sendMessage(ChatColor.GREEN + "Flags Stolen:      " + ChatColor.AQUA + playerGameData.getFlagsStolen());
			player.sendMessage(ChatColor.GREEN + "Flags Saved:       " + ChatColor.AQUA + playerGameData.getFlagsSaved());
			player.sendMessage(ChatColor.GREEN + "Flags Intercepted: " + ChatColor.AQUA + playerGameData.getFlagsIntercepted());
			player.sendMessage(ChatColor.GREEN + "Kills: " + ChatColor.AQUA + playerGameData.getKills() + "    " + ChatColor.GREEN + "Deaths: " + ChatColor.AQUA + playerGameData.getDeaths());
			player.sendMessage(String.format(ChatColor.GREEN + "Damage Dealt: " + ChatColor.AQUA + "%.2f", playerGameData.getDamageDealt()));
			player.sendMessage(ChatColor.GOLD + "--------------------------------------------");
			avoidModificationWhileIterating.add(playerName);
			player.setHealth(player.getMaxHealth());
		}
		//kick all players
		for(String playerName : avoidModificationWhileIterating){
			playerLeave(playerData.get(playerName).getPlayer());
		}
	}
	
	public String timeLeft(){
		return secondsToWords(gameLength - timeElapsed);
	}
	
	public String secondsToWords(int seconds){
		StringBuilder sb = new StringBuilder();
		int hours = seconds / 3600;
		if(hours > 0){
			if(hours < 10){
				sb.append("0");
			}
			sb.append(hours + ":");
		}
		seconds %= 3600;
		int minutes = seconds / 60;
		if(minutes < 10){
			sb.append("0");
		}
		sb.append(minutes + ":");
		seconds %= 60;
		if(seconds < 10){
			sb.append("0");
		}
		sb.append(seconds);
		return sb.toString();
	}
	
	public boolean isInProgress(){
		return inProgress;
	}
	
	public void playerJoin(Player player){
		//puts the player on the least populated team
		Team team = getTeamFewestPlayers();
		System.out.println(player.getName() + " joined " + team.getName());
		playerData.putIfAbsent(player.getName(), new PlayerGameData(player, team));
		teamData.get(team.getName()).addPlayer(player);
		
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
		
		PBCTFGameManager.playersInArena.put(player.getName(), arena);
	}
	
	public void playerLeave(Player player){
		String teamName = playerData.get(player.getName()).getTeam().getName();
		//clear inventory
		player.getInventory().clear();
		//put flags back
		playerData.get(player.getName()).dropAllFlags();
		//send player back to location before game
		player.teleport(playerData.get(player.getName()).getLocationBeforeGame());
		teamData.get(teamName).removePlayer(player);
		playerData.remove(player.getName());
		
		PBCTFGameManager.playersInArena.remove(player.getName());
	}
	
	public boolean isPlayerInGame(Player player){
		return playerData.get(player.getName()) != null;
	}
	
	public TeamGameData getTeamGameData(String teamName){
		return teamData.get(teamName);
	}
	
	public PlayerGameData getPlayerGameData(String playerName){
		return playerData.get(playerName);
	}
	
	public int getNumPlayers(){
		return playerData.size();
	}
	
	public void setPlayerTeam(Player player, Team team){
		//remove player from current team
		teamData.get(playerData.get(player.getName()).getTeam().getName()).removePlayer(player);
		//add player to other team
		playerData.get(player.getName()).setTeam(team);
		teamData.get(team.getName()).addPlayer(player);
	}
	
	public Player getPlayer(String playerName){
		return playerData.get(playerName).getPlayer();
	}
	
	//send all players in this game a message
	public void notifyPlayers(String message){
		for(String playerName : playerData.keySet()){
			playerData.get(playerName).getPlayer().sendMessage(message);
		}
	}
	
	//send all players in this game a message
	public void makeSoundAtPlayers(Sound sound, float volume, float pitch){
		for(String playerName : playerData.keySet()){
			Player player = playerData.get(playerName).getPlayer();
			player.playSound(player.getLocation(), sound, volume, pitch);
		}
	}
	
	public Team getTeamFewestPlayers(){
		Team fewestTeam = null;
		int fewest = Integer.MAX_VALUE;
		for(String teamName : teamData.keySet()){
			if(teamData.get(teamName).getNumPlayers() == 0){
				return teamData.get(teamName).getTeam();
			}
			if(teamData.get(teamName).getNumPlayers() < fewest){
				fewest = teamData.get(teamName).getNumPlayers();
				fewestTeam = teamData.get(teamName).getTeam();
			}
		}
		return fewestTeam;
	}
	
	public String getInfo(){
		StringBuilder sb = new StringBuilder();
		for(String teamName : teamData.keySet()){
			sb.append(teamName + "(" + teamData.get(teamName).getNumPlayers() + "), ");
		}
		return sb.substring(0, sb.length() - 2);
	}
}
