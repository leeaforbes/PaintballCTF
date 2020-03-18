package com.javabean.paintballctf;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.javabean.pbguns.Gun;

public class PBCTFListener implements Listener{
	
	private PBCTFGameManager gameManager;
	private HashMap<String, Arena> arenaMap = new HashMap<String, Arena>();
	
	//player name, last snowball they were hit by
	private HashMap<String, Snowball> lastSnowballHit = new HashMap<String, Snowball>();
	
	public PBCTFListener(PBCTFGameManager ctfgm, HashMap<String, Arena> am){
		gameManager = ctfgm;
		arenaMap = am;
	}
	
	//prevents the player from losing hunger
	@EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event){
		if(event.getEntityType() == EntityType.PLAYER && gameManager.getPlayerGameArena((Player)event.getEntity()) != null){
			event.setCancelled(true);
		}
    }
	
	//item durability will not decrease on use
	@EventHandler
	public void onPlayerItemDamage(PlayerItemDamageEvent event){
		if(gameManager.getPlayerGameArena(event.getPlayer()) != null){
			event.setCancelled(true);
		}
	}
	
	//TODO going out of bounds kills you
	
	//only allow damage between two players if both in game and not on the same team
	@EventHandler(priority = EventPriority.LOW)
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event){
		if(event.getEntityType() == EntityType.PLAYER){
			Player victim = (Player) event.getEntity();
			Arena victimGameArena = gameManager.getPlayerGameArena(victim);
			if(victimGameArena != null && event.getDamager() instanceof Snowball){
				Player attacker = (Player)(((Snowball)event.getDamager()).getShooter());
//                attacker.sendMessage(ChatColor.GREEN + "You attacked " + victim.getName() + ".");
//                victim.sendMessage(ChatColor.RED + attacker.getName() + " attacked you!");
                Arena attackerGameArena = gameManager.getPlayerGameArena(attacker);
				if(attackerGameArena != null){
					//game not in progress yet
					if(!gameManager.isInProgress(attackerGameArena)){
						event.setCancelled(true);
					}
					//attacker and victim on the same team
					else if(gameManager.getPlayerGameData(attacker, victimGameArena).getTeam().getName().equals(gameManager.getPlayerGameData(victim, victimGameArena).getTeam().getName())){
						event.setCancelled(true);
					}
					//attack deals damage to victim
					else{
						Gun gun = (Gun)(((Snowball)event.getDamager()).getMetadata("gun").get(0).value());
						lastSnowballHit.put(victim.getName(), (Snowball)event.getDamager());
						//this is SUPER inefficient. thank you, Spigot no damage tick bug
						gameManager.getPlayerGameData(attacker, attackerGameArena).dealDamage(calculateDamage(victim, attacker, gun, (Snowball)event.getDamager()));
					}
				}
            }
			if(victimGameArena != null && event.getDamager() instanceof Player){
				event.setCancelled(true);
			}
        }
    }
	
//	**
//	 **
//	  **
//	   **
//	    **
//	     **
	//WARNING: CODE IS RIPPED STRAIGHT FROM PAINTBALLGUNS PLUGIN
	private double calculateDamage(Player victim, Player attacker, Gun attackerGun, Snowball bullet){
		@SuppressWarnings("unchecked")
		ArrayList<Snowball> grouping = (ArrayList<Snowball>)bullet.getMetadata("grouping").get(0).value();
		
		if(grouping.size() == 1){
			if(isAHeadshot(victim, bullet)){
				return attackerGun.getDamage() * 3;
			}
			else{
				return attackerGun.getDamage();
			}
		}
		else{
			double groupingDamage = 0;
			for(Snowball groupBullet : grouping){
				if(isSnowballInHitbox(victim, groupBullet)){
					if(isAHeadshot(victim, groupBullet)){
						groupingDamage += attackerGun.getDamage() * 3;
					}
					groupingDamage += attackerGun.getDamage();
				}
			}
			return groupingDamage;
		}
	}
	
	private boolean isAHeadshot(LivingEntity victim, Snowball bullet){
		Location loc = bullet.getLocation();
		RayTraceResult result = victim.getBoundingBox().expand(0.5).rayTrace(new Vector(loc.getX(), loc.getY(), loc.getZ()), bullet.getVelocity(), 10);
		if(result != null){
			double distance = result.getHitPosition().distance(victim.getEyeLocation().toVector());
			if(distance <= 0.9){
				return true;
			}
		}
		result = victim.getBoundingBox().expand(0.5).rayTrace(new Vector(loc.getX(), loc.getY(), loc.getZ()), bullet.getVelocity(), -10);
		if(result != null){
			double distance = result.getHitPosition().distance(victim.getEyeLocation().toVector());
			if(distance <= 0.9){
				return true;
			}
		}
		return false;
	}
	
	private boolean isSnowballInHitbox(LivingEntity victim, Snowball bullet){
		Location loc = bullet.getLocation();
		return victim.getBoundingBox().expand(0.5).rayTrace(new Vector(loc.getX(), loc.getY(), loc.getZ()), bullet.getVelocity(), 5) != null
				|| victim.getBoundingBox().expand(0.5).rayTrace(new Vector(loc.getX(), loc.getY(), loc.getZ()), bullet.getVelocity(), -5) != null;
	}
//	     **
//	    **
//	   **
//	  **
//	 **
//	**
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event){
		System.out.println(event.getEntity() + " <- " + event.getCause());
		if(event.getEntityType() == EntityType.PLAYER){
			Player victim = (Player) event.getEntity();
			Arena victimGameArena = gameManager.getPlayerGameArena(victim);
			if(event.getCause() == DamageCause.FALL){
	        	if(victimGameArena != null && !gameManager.isInProgress(victimGameArena)){
	        		event.setCancelled(true);
	        	}
	        }
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event){
		if(event.getHand() == EquipmentSlot.HAND && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)){
			flagClickEvent(event);
			signClickEvent(event);
		}
	}
	
	@EventHandler
	public void onBreakBlock(BlockBreakEvent event){
		if(gameManager.getPlayerGameArena(event.getPlayer()) != null){
			//if player is not OP
			if(!event.getPlayer().isOp()){
				event.setCancelled(true);
			}
			//player not in creative mode
			else if(event.getPlayer().getGameMode() != GameMode.CREATIVE){
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event){
		if(gameManager.getPlayerGameArena(event.getPlayer()) != null){
			//if player is not OP
			if(!event.getPlayer().isOp()){
				event.setCancelled(true);
			}
			//player not in creative mode
			else if(event.getPlayer().getGameMode() != GameMode.CREATIVE){
				event.setCancelled(true);
			}
		}
	}
	
	private void flagClickEvent(PlayerInteractEvent event){
		Arena playerArena = gameManager.getPlayerGameArena(event.getPlayer());
		if(playerArena != null && gameManager.isInProgress(playerArena)){
			Block block = event.getClickedBlock();
			Team teamOfBlockHit = playerArena.getTeamOfFlagAt(block.getLocation());
			PlayerGameData playerGameData = gameManager.getPlayerGameData(event.getPlayer(), playerArena);
			Team playerTeam = playerGameData.getTeam();
			if(teamOfBlockHit != null){
				//if they hit their own flag
				if(playerTeam.getName().equals(teamOfBlockHit.getName())){
					//have enemy flag
					if(playerGameData.numFlagsHolding() > 0){
						captureFlags(playerGameData);
						
						//sends firework of team color at flag, put inside block to avoid damage
						Firework fw = (Firework) playerGameData.getPlayer().getWorld().spawnEntity(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5), EntityType.FIREWORK);
						FireworkMeta fwm = fw.getFireworkMeta();
						fwm.setPower(2);
						java.awt.Color javaRGB = java.awt.Color.decode(playerGameData.getTeam().getHexColor());
						Color teamColor = Color.fromRGB(javaRGB.getRed(), javaRGB.getGreen(), javaRGB.getBlue());
						fwm.addEffect(FireworkEffect.builder().withColor(teamColor).withFlicker().withTrail().build());
						fw.setFireworkMeta(fwm);
						fw.detonate();
						
						gameManager.getGame(playerArena).makeSoundAtPlayers(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
					}
					else{
						event.getPlayer().sendMessage(ChatColor.GREEN + "This is your flag! Hit again when you have the enemy flag with you!");
					}
				}
				//if they hit enemy flag
				else{
					Flag flagHit = teamOfBlockHit.getFlagAtLocation(block.getLocation());
					playerGameData.grabFlag(flagHit);
					playerGameData.stealFlag();
					gameManager.notifyPlayers(ChatColor.GOLD + "" + ChatColor.BOLD + playerGameData.getPlayer().getName() + " took " + teamOfBlockHit.getName() + " team's " + flagHit.getName() + " flag!", playerArena);
					event.getPlayer().sendMessage(ChatColor.GREEN + "Bring the flag back to your team's flag!");
					
					//TODO give glowing effect
					event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10000, 1, false, false, false), false);
				}
			}
		}
	}
	
	private void signClickEvent(PlayerInteractEvent event){
		if(Arena.isASign(event.getClickedBlock().getType())){
			BlockState signBlockState = event.getClickedBlock().getState();
			if(signBlockState.hasMetadata("pbctfarena")){
				//send player to join that arena if possible
				String arenaName = signBlockState.getMetadata("pbctfarena").get(0).asString();
				gameManager.attemptPlayerJoin(event.getPlayer(), arenaMap.get(arenaName));
			}
		}
	}
	
	public void captureFlags(PlayerGameData playerGameData){
		Player player = playerGameData.getPlayer();
		Arena playerArena = gameManager.getPlayerGameArena(player);
		for(String flagName : playerGameData.getHoldingFlags().keySet()){
			Flag flag = playerGameData.getHoldingFlags().get(flagName);
			Team teamFlagCaptured = playerArena.getTeamOfFlagAt(flag.getLocation());
			gameManager.notifyPlayers(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName() + " captured " + teamFlagCaptured.getName() + " team's " + flag.getName() + " flag!", playerArena);
			//update player stats for flag capture
			playerGameData.captureFlag();
			//update team stats for flag capture
			gameManager.getGame(playerArena).getTeamGameData(playerGameData.getTeam().getName()).captureFlag();
		}
		//drop all flags
		playerGameData.dropAllFlags();
		
		player.removePotionEffect(PotionEffectType.GLOWING);
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event){
		//force player to leave game if they are in one to avoid combat logging and holding flags when disconnected
		if(gameManager.getPlayerGameArena(event.getPlayer()) != null){
			gameManager.playerLeave(event.getPlayer(), gameManager.getPlayerGameArena(event.getPlayer()));
		}
	}
	
	//drop flag on player death
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event){
		if (event.getEntity() instanceof Player){
            Player victim = (Player)event.getEntity();
            Arena victimArena = gameManager.getPlayerGameArena(victim);
            if(victimArena != null){
            	PlayerGameData victimGameData = gameManager.getPlayerGameData(victim, victimArena);
            	
            	//my custom damage from the snowball
            	if(victim.getLastDamageCause().getCause() == DamageCause.CUSTOM){
            		Snowball snowball = lastSnowballHit.get(victim.getName());
            		Player attacker = ((Gun)(snowball.getMetadata("gun").get(0).value())).getHolder();
            		PlayerGameData attackerGameData = gameManager.getPlayerGameData(attacker, victimArena);
            		gameManager.notifyPlayers(ChatColor.DARK_GREEN + attacker.getName() + " killed " + victim.getName() + ".", victimArena);
            		attackerGameData.killSomeone();
            		victimGameData.die();
            		if(victimGameData.numFlagsHolding() > 0){
            			victimGameData.transferFlagsTo(attackerGameData);
            			gameManager.notifyPlayers(ChatColor.DARK_GREEN + attacker.getName() + " intercepted " + victim.getName() + "'s flags!", victimArena);

            			victim.removePotionEffect(PotionEffectType.GLOWING);
            		}
            		
            		//TODO give some kind of bonus for a kill (see regular CTF)
            	}
            	else{
            		event.setDeathMessage(ChatColor.LIGHT_PURPLE + victim.getName() + " died" + (victimGameData.numFlagsHolding() > 0 ? " and flags were returned" : "") + ".");
            	}
            	//force the player to drop all their flags if haven't already
            	victimGameData.dropAllFlags();
            }
		}
	}
	
	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event){
		//operates under the assumption that the only way they can die is in game
		Player player = event.getPlayer();
		Arena playerArena = gameManager.getPlayerGameArena(player);
		if(playerArena != null){
			Location location = gameManager.getPlayerGameData(player, playerArena).getTeam().getRandomSpawn().getLocation();
			if(location != null){
				event.setRespawnLocation(location);
				event.getPlayer().teleport(location);
			}
		}
	}
}
