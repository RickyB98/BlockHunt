package nl.Steffion.BlockHunt.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Note.Tone;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import nl.Steffion.BlockHunt.BlockHunt;

public class Arena {
	private UUID		editor;
	private boolean		editorIsRenamingArena;
	private Location	hidersSpawn;
	private Location	lobbyLocation;
	private String		name;
	private List<UUID>	players;
	private BlockHunt	plugin;
	private Objective	scoreboard;
	private Location	seekersSpawn;
	private ArenaState	state;
	private List<Hider>	teamHiders;
	private List<UUID>	teamSeekers;
	private BukkitTask	thread;
	private int			timer;
						
	public Arena() {
		plugin = BlockHunt.getPlugin();
		
		int arenaNumber = 1;
		while (true) {
			if (plugin.getArenas().getConfig().contains("Arena_" + arenaNumber)) {
				arenaNumber++;
				continue;
			}

			name = "Arena_" + arenaNumber;
			break;
		}
		
		players = new ArrayList<UUID>();
		teamHiders = new ArrayList<Hider>();
		teamSeekers = new ArrayList<UUID>();
		scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard().registerNewObjective("BlockHunt",
				"dummy");
		state = ArenaState.WAITING;
	}

	public Arena(String name) {
		plugin = BlockHunt.getPlugin();
		this.name = name;
		players = new ArrayList<UUID>();
		teamHiders = new ArrayList<Hider>();
		teamSeekers = new ArrayList<UUID>();
		scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard().registerNewObjective("BlockHunt",
				"dummy");
		state = ArenaState.WAITING;
	}

	public void addPlayer(Player player) {
		players.add(player.getUniqueId());
		
		plugin.getPlayerHandler().storePlayerData(player);
		plugin.getPlayerHandler().getPlayerData(player).clear();
		player.teleport(lobbyLocation);
		
		startThread();
	}

	public void addSeeker(Player player) {
		player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
		player.getInventory().setItem(1, new ItemStack(Material.BOW));
		player.getInventory().setItem(7, new ItemStack(Material.ARROW, 64));
		
		if (state == ArenaState.PREGAME) {
			player.teleport(seekersSpawn);
		} else {
			player.teleport(hidersSpawn);
		}

		teamSeekers.add(player.getUniqueId());
	}

	public Player getEditor() {
		return plugin.getServer().getPlayer(editor);
	}

	public Hider getHider(Player player) {
		for (Hider hider : teamHiders) {
			if (hider.getPlayer().getUniqueId() == player.getUniqueId()) return hider;
		}

		return null;
	}
	
	public List<Hider> getHiders() {
		List<Hider> hiders = new ArrayList<Hider>();

		for (Hider hider : teamHiders) {
			hiders.add(hider);
		}
		
		return hiders;
	}

	public Location getHidersSpawn() {
		return hidersSpawn;
	}
	
	public Location getLobbyLocation() {
		return lobbyLocation;
	}

	public String getName() {
		return name;
	}
	
	public List<Player> getPlayers() {
		List<Player> players = new ArrayList<Player>();
		
		for (UUID uuid : this.players) {
			players.add(plugin.getServer().getPlayer(uuid));
		}
		
		return players;
	}
	
	public List<Player> getSeekers() {
		List<Player> seekers = new ArrayList<Player>();

		for (UUID uuid : teamSeekers) {
			seekers.add(plugin.getServer().getPlayer(uuid));
		}
		
		return seekers;
	}
	
	public Location getSeekersSpawn() {
		return seekersSpawn;
	}
	
	public ArenaState getState() {
		return state;
	}
	
	public boolean hasStarted() {
		if (state == ArenaState.WAITING) return false;
		if (state == ArenaState.STARTING) return false;
		
		return true;
	}
	
	public boolean isEditorRenamingArena() {
		return editorIsRenamingArena;
	}
	
	public boolean isSetup() {
		if ((hidersSpawn == null) || (lobbyLocation == null) || (seekersSpawn == null)) return false;

		return true;
	}

	public void load() {
		plugin.getArenas().load();
		ConfigurationSection arenas = plugin.getArenas().getConfig();

		if (arenas.getString(name + ".hidersSpawn.world") != null) {
			hidersSpawn = new Location(Bukkit.getWorld(arenas.getString(name + ".hidersSpawn.world")),
					arenas.getDouble(name + ".hidersSpawn.x"), arenas.getDouble(name + ".hidersSpawn.y"),
					arenas.getDouble(name + ".hidersSpawn.z"));
		}
		
		if (arenas.getString(name + ".lobbyLocation.world") != null) {
			lobbyLocation = new Location(Bukkit.getWorld(arenas.getString(name + ".lobbyLocation.world")),
					arenas.getDouble(name + ".lobbyLocation.x"), arenas.getDouble(name + ".lobbyLocation.y"),
					arenas.getDouble(name + ".lobbyLocation.z"));
		}
		
		if (arenas.getString(name + ".seekersSpawn.world") != null) {
			seekersSpawn = new Location(Bukkit.getWorld(arenas.getString(name + ".seekersSpawn.world")),
					arenas.getDouble(name + ".seekersSpawn.x"), arenas.getDouble(name + ".seekersSpawn.y"),
					arenas.getDouble(name + ".seekersSpawn.z"));
		}
	}
	
	public void removeHider(Player player) {
		for (int i = 0; i < teamHiders.size(); i++) {
			Hider hider = teamHiders.get(i);
			
			if (hider.getPlayer().getUniqueId() == player.getUniqueId()) {
				teamHiders.remove(i);
				break;
			}
		}
		
		DisguiseAPI.undisguiseToAll(player);
	}
	
	public void removePlayer(Player player) {
		players.remove(player.getUniqueId());
		removeHider(player);
		teamSeekers.remove(player.getUniqueId());
		
		plugin.getPlayerHandler().getPlayerData(player).restore();
		player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
	}

	protected void resetArena() {
		state = ArenaState.WAITING;
		teamHiders = new ArrayList<Hider>();
		teamSeekers = new ArrayList<UUID>();
	}
	
	public void resetEditor() {
		editor = null;
		editorIsRenamingArena = false;
	}
	
	public void save() {
		plugin.getArenas().getConfig().set(name, "");

		if (hidersSpawn != null) {
			plugin.getArenas().getConfig().set(name + ".hidersSpawn.world", hidersSpawn.getWorld().getName());
			plugin.getArenas().getConfig().set(name + ".hidersSpawn.x", hidersSpawn.getBlockX());
			plugin.getArenas().getConfig().set(name + ".hidersSpawn.y", hidersSpawn.getBlockY());
			plugin.getArenas().getConfig().set(name + ".hidersSpawn.z", hidersSpawn.getBlockZ());
		}
		
		if (lobbyLocation != null) {
			plugin.getArenas().getConfig().set(name + ".lobbyLocation.world", lobbyLocation.getWorld().getName());
			plugin.getArenas().getConfig().set(name + ".lobbyLocation.x", lobbyLocation.getBlockX());
			plugin.getArenas().getConfig().set(name + ".lobbyLocation.y", lobbyLocation.getBlockY());
			plugin.getArenas().getConfig().set(name + ".lobbyLocation.z", lobbyLocation.getBlockZ());
		}
		
		if (seekersSpawn != null) {
			plugin.getArenas().getConfig().set(name + ".seekersSpawn.world", seekersSpawn.getWorld().getName());
			plugin.getArenas().getConfig().set(name + ".seekersSpawn.x", seekersSpawn.getBlockX());
			plugin.getArenas().getConfig().set(name + ".seekersSpawn.y", seekersSpawn.getBlockY());
			plugin.getArenas().getConfig().set(name + ".seekersSpawn.z", seekersSpawn.getBlockZ());
		}
		
		plugin.getArenas().save();
	}
	
	public void setEditor(Player editor) {
		this.editor = editor.getUniqueId();
	}
	
	public void setEditorRenamingArena(boolean editorRenamingArena) {
		editorIsRenamingArena = editorRenamingArena;
	}
	
	public void setHidersSpawn(Location hidersSpawn) {
		this.hidersSpawn = hidersSpawn;
	}

	public void setLobbyLocation(Location lobbyLocation) {
		this.lobbyLocation = lobbyLocation;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public void setSeekersSpawn(Location seekersSpawn) {
		this.seekersSpawn = seekersSpawn;
	}

	public void startThread() {
		if (thread != null) return;
		thread = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
			
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				if (players.isEmpty()) {
					resetArena();
					stopThread();
				}
				
				if (state == ArenaState.WAITING) {
					if (players.size() >= ((int) plugin.getPluginConfig().get("MINPLAYERS"))) {
						state = ArenaState.STARTING;
						timer = (int) plugin.getPluginConfig().get("LOBBYTIME");
					}
				}

				if (state == ArenaState.STARTING) {
					if (players.size() < ((int) plugin.getPluginConfig().get("MINPLAYERS"))) {
						for (Player player : getPlayers()) {
							player.setExp(0);
						}
						
						state = ArenaState.WAITING;
						return;
					}
					
					timer--;
					
					switch (timer) {
						case 10:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(0, Tone.G));
							}
							break;
						case 5:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(0, Tone.B));
							}
							break;
						case 4:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(0, Tone.B));
							}
							break;
						case 3:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(1, Tone.C));
							}
							break;
						case 2:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(1, Tone.C));
							}
							break;
						case 1:
							for (Player player : getPlayers()) {
								player.playNote(player.getLocation(), Instrument.PIANO, Note.natural(1, Tone.D));
							}
							break;
						case 0:
							for (Player player : getPlayers()) {
								player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1, 1);
							}
							
							state = ArenaState.PREGAME;
							timer = (int) plugin.getPluginConfig().get("SEEKERSWAITTIME");
							
							int seekerAmount = (int) (Math.round(
									players.size() * (double) plugin.getPluginConfig().get("PRECENTAGE_SEEKERS")) + 1);
									
							for (int i = 0; i < seekerAmount; i++) {
								Random random = new Random();
								
								UUID randomSeeker = players.get(random.nextInt(players.size()));
								
								if (teamSeekers.contains(randomSeeker)) {
									i--;
									continue;
								}
								
								addSeeker(plugin.getServer().getPlayer(randomSeeker));
							}
							
							String seekers = "The seekers have been choosen: ";
							for (Player seeker : getSeekers()) {
								seekers += seeker.getName() + ", ";
							}
							
							seekers = seekers.substring(0, seekers.length() - 2);
							
							for (Player player : getPlayers()) {
								player.sendMessage(seekers);
								
								if (!getSeekers().contains(player)) {
									teamHiders.add(new Hider(player));
								}
							}
							
							for (Hider hider : getHiders()) {
								// TODO random from list
								DisguiseAPI.disguiseToAll(hider.getPlayer(),
										new MiscDisguise(DisguiseType.FALLING_BLOCK, 3, 0));
								hider.getPlayer().getInventory().setItem(0, new ItemStack(3));
								hider.setBlock(Material.DIRT);
								hider.getPlayer().teleport(hidersSpawn);
							}
							
							for (Player seeker : getSeekers()) {
								seeker.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
								seeker.teleport(seekersSpawn);
							}
							
							break;
						default:
							break;
					}
					
					for (Player player : getPlayers()) {
						player.setExp((float) timer / ((int) plugin.getPluginConfig().get("LOBBYTIME")));
					}
				}

				if (state == ArenaState.PREGAME) {
					timer--;
					
					if (timer == 0) {
						state = ArenaState.INGAME;
						timer = (int) plugin.getPluginConfig().get("GAMETIME");
						
						for (Player seeker : getSeekers()) {
							seeker.teleport(hidersSpawn);
						}
						
						hidersSpawn.getWorld().strikeLightningEffect(hidersSpawn);
					}
					
					for (Player player : getPlayers()) {
						player.setExp((float) timer / ((int) plugin.getPluginConfig().get("SEEKERSWAITTIME")));
					}
				}

				if (state == ArenaState.INGAME) {
					timer--;
					
					if ((timer == 0) || teamHiders.isEmpty()) {
						resetArena();
						
						for (Player player : getPlayers()) {
							DisguiseAPI.undisguiseToAll(player);
							player.getInventory().setItem(0, null);
							player.getInventory().setItem(1, null);
							player.getInventory().setItem(7, null);
							player.setExp(0);
							player.setHealth(player.getMaxHealth());
							player.teleport(lobbyLocation);
							player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1);
						}
					}
					
					for (Player player : getPlayers()) {
						player.setExp((float) timer / ((int) plugin.getPluginConfig().get("GAMETIME")));
					}
				}

				if ((state == ArenaState.PREGAME) || (state == ArenaState.INGAME)) {
					for (Hider hider : getHiders()) {
						int hiderTimer = hider.getSolidBlockTimer();
						
						hider.setSolidBlockTimer(hiderTimer + 1);

						if (hiderTimer == 3) {
							if (hider.getPlayer().getLocation().getBlock().getType() != Material.AIR) {
								hider.getPlayer().sendMessage("You can't become a block here!");
								hider.setSolidBlockTimer(0);
							} else {
								hider.setHideLocation(hider.getPlayer().getLocation());
								hider.getPlayer().playSound(hider.getPlayer().getLocation(), Sound.ORB_PICKUP, 1, 0);
							
								for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
									if (onlinePlayer.equals(hider.getPlayer())) {
										continue;
									}
									
									onlinePlayer.sendBlockChange(hider.getHideLocation(), hider.getBlock(), (byte) 0);
									onlinePlayer.hidePlayer(hider.getPlayer());
								}
							}
						} else if (hiderTimer > 3) {
							for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
								if (onlinePlayer.equals(hider.getPlayer())) {
									continue;
								}
								
								onlinePlayer.sendBlockChange(hider.getHideLocation(), hider.getBlock(), (byte) 0);
								onlinePlayer.hidePlayer(hider.getPlayer());
							}
						}
						
						hider.getPlayer().setExp(((float) (hiderTimer > 3 ? 3 : hiderTimer) / 3));
					}
				}
				
				updateScoreboard();
			}
			
		}, 0, 20);
	}
	
	public void stopThread() {
		plugin.getServer().getScheduler().cancelTask(thread.getTaskId());
		thread = null;
	}

	protected void updateScoreboard() {
		scoreboard.setDisplaySlot(DisplaySlot.SIDEBAR);
		scoreboard.setDisplayName("§9§lBlockHunt");

		for (String entry : scoreboard.getScoreboard().getEntries()) {
			scoreboard.getScoreboard().resetScores(entry);
		}

		List<String> scoreboardEntries = new ArrayList<String>();

		scoreboardEntries.add("§lArena: §6" + name);
		scoreboardEntries.add("§lPlayers: §6" + players.size() + "/" + plugin.getPluginConfig().get("MAXPLAYERS"));
		
		if (state == ArenaState.WAITING) {
			scoreboardEntries.add("§lMinimum required players: §6" + plugin.getPluginConfig().get("MINPLAYERS"));
			scoreboardEntries.add("§6§lWaiting for players...");
		}

		if (state == ArenaState.STARTING) {
			String startingIn = "§6§lStarting in: §f§l" + timer + " §6seconds";
			if (timer == 1) {
				startingIn = startingIn.substring(0, startingIn.length() - 1);
			}

			scoreboardEntries.add(startingIn);
		}

		if ((state == ArenaState.PREGAME) || (state == ArenaState.INGAME)) {
			scoreboardEntries.add("§c§lHiders: §6" + teamHiders.size());
			scoreboardEntries.add("§b§lSeekers: §6" + teamSeekers.size());
		}

		if (state == ArenaState.PREGAME) {
			scoreboardEntries.add("§6§lSeekers will be free in:");
			
			String hidersFree = "§l" + timer + " §6seconds";
			if (timer == 1) {
				hidersFree = hidersFree.substring(0, hidersFree.length() - 1);
			}

			scoreboardEntries.add(hidersFree);
		}
		
		if (state == ArenaState.INGAME) {
			String timeLeft = "§6§lTime left: §f§l" + timer + " §6seconds";
			if (timer == 1) {
				timeLeft = timeLeft.substring(0, timeLeft.length() - 1);
			}

			scoreboardEntries.add(timeLeft);
		}
		
		for (int i = 0; i < scoreboardEntries.size(); i++) {
			Score scoreboardEntry = scoreboard.getScore(scoreboardEntries.get(i));
			scoreboardEntry.setScore(scoreboardEntries.size() - i - 1);
		}
		
		for (Player player : getPlayers()) {
			player.setScoreboard(scoreboard.getScoreboard());
		}
	}
	
}
