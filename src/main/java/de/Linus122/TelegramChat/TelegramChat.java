package de.Linus122.TelegramChat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import de.Linus122.Handlers.VanishHandler;
import de.myzelyam.api.vanish.VanishAPI;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.emoji.EmojiParser;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;

import de.Linus122.Handlers.BanHandler;
import de.Linus122.Metrics.Metrics;
import de.Linus122.Telegram.Telegram;
import de.Linus122.Telegram.Utils;
import de.Linus122.TelegramComponents.Chat;
import de.Linus122.TelegramComponents.ChatMessageToMc;
import de.Linus122.TelegramComponents.ChatMessageToTelegram;

public class TelegramChat extends JavaPlugin implements Listener {
	private static File datad = new File("plugins/TelegramChat/data.json");
	private static FileConfiguration cfg;

	private static Data data = new Data();
	public static Telegram telegramHook;
	private static TelegramChat instance;
	private static boolean isSuperVanish;

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		cfg = this.getConfig();
		instance = this;
		Utils.cfg = cfg;

		Bukkit.getPluginCommand("telegram").setExecutor(new TelegramCmd());
		Bukkit.getPluginCommand("linktelegram").setExecutor(new LinkTelegramCmd());
		Bukkit.getPluginManager().registerEvents(this, this);

		if (Bukkit.getPluginManager().isPluginEnabled("SuperVanish") || Bukkit.getPluginManager().isPluginEnabled("PremiumVanish")) {
			isSuperVanish = true;
			Bukkit.getPluginManager().registerEvents(new VanishHandler(), this);
		}

		File dir = new File("plugins/TelegramChat/");
		dir.mkdir();
		data = new Data();
		if (datad.exists()) {
			Gson gson = new Gson();
			try {
				FileReader fileReader = new FileReader(datad);
				StringBuilder sb = new StringBuilder();
				int c;
			    while((c = fileReader.read()) !=-1) {
			    	sb.append((char) c);
			    }

				data = (Data) gson.fromJson(sb.toString(), Data.class);
				
				fileReader.close();
			} catch (Exception e) {
				// old method for loading the data.yml file
				try {
					FileInputStream fin = new FileInputStream(datad);
					ObjectInputStream ois = new ObjectInputStream(fin);
					
					data = (Data) gson.fromJson((String) ois.readObject(), Data.class);
					ois.close();
					fin.close();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
				this.getLogger().log(Level.INFO, "Converted old data.yml");
				save();
			}
		}

		telegramHook = new Telegram();
		telegramHook.auth(data.getToken());
		
		// Ban Handler (Prevents banned players from chatting)
		telegramHook.addListener(new BanHandler());
		
		// Console sender handler, allows players to send console commands (telegram.console permission)
		// telegramHook.addListener(new CommandHandler(telegramHook, this));

		Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
			boolean connectionLost = false;
			if (connectionLost) {
				boolean success = telegramHook.reconnect();
				if (success)
					connectionLost = false;
			}
			if (telegramHook.connected) {
				connectionLost = !telegramHook.getUpdate();
			}
		}, 0L, 10L);
		
		// metrics
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> new Metrics(this));
	}

	@Override
	public void onDisable() {
		save();
	}

	public static void save() {
		Gson gson = new Gson();

		try {
			FileWriter fileWriter = new FileWriter(datad);
			fileWriter.write(gson.toJson(data));
			
			fileWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static Data getBackend() {
		return data;
	}

	public static void initBackend() {
		data = new Data();
	}

	public static void sendToMC(ChatMessageToMc chatMsg) {
		sendToMC(chatMsg.getUuid_sender(), chatMsg.getContent(), chatMsg.getChatID_sender());
	}

	public static void sendToDiscord(ChatMessageToMc chatMsg) {
		sendToDiscord(chatMsg.getUuid_sender(), chatMsg.getContent(), chatMsg.getChatID_sender());
	}

	private static void sendToMC(UUID uuid, String msg, long sender_chat) {
		OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
		List<Long> recievers = new ArrayList<Long>();
		recievers.addAll(TelegramChat.data.chat_ids);
		recievers.remove((Object) sender_chat);
		String msgF = Utils.formatMSG("general-message-to-mc", op.getName(), msg)[0];
		for (long id : recievers) {
			telegramHook.sendMsg(id, msgF.replaceAll("§.", ""));
		}
		Bukkit.broadcastMessage(msgF.replace("&", "§"));

	}

	private static void sendToDiscord(UUID uuid, String msg, long sender_chat) {
		OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
		List<Long> recievers = new ArrayList<Long>();
		recievers.addAll(TelegramChat.data.chat_ids);
		recievers.remove((Object) sender_chat);
		String msgF = Utils.formatMSG("general-message-to-discord", op.getName(), msg)[0];
		for (long id : recievers) {
			telegramHook.sendMsg(id, msgF.replaceAll("§.", ""));
		}
		TextChannel textChannel = DiscordSRV.getPlugin().getMainTextChannel();
		textChannel.sendMessage(msgF);
	}

	public static void link(UUID player, long userID) {
		TelegramChat.data.addChatPlayerLink(userID, player);
		OfflinePlayer p = Bukkit.getOfflinePlayer(player);
		telegramHook.sendMsg(userID, "Success! Linked " + p.getName());
	}
	
	public boolean isChatLinked(Chat chat) {
		if (TelegramChat.getBackend().getLinkedChats().containsKey(chat.getId())) {
			return true;
		}

		return false;
	}

	public static String generateLinkToken() {

		Random rnd = new Random();
		int i = rnd.nextInt(9999999);
		String s = i + "";
		String finals = "";
		for (char m : s.toCharArray()) {
			int m2 = Integer.parseInt(m + "");
			int rndi = rnd.nextInt(2);
			if (rndi == 0) {
				m2 += 97;
				char c = (char) m2;
				finals = finals + c;
			} else {
				finals = finals + m;
			}
		}
		return finals;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (!this.getConfig().getBoolean("enable-joinquitmessages"))
			return;

		if(isSuperVanish && VanishAPI.isInvisible(e.getPlayer()))
			return;

		if (telegramHook.connected) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("join-message", e.getPlayer().getName())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		if (!this.getConfig().getBoolean("enable-deathmessages"))
			return;
		if (telegramHook.connected) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("death-message", e.getDeathMessage())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onAdvancement(PlayerAdvancementDoneEvent e) {
				
		if (!this.getConfig().getBoolean("enable-advancement"))
			return;
		
		if(e.getAdvancement() == null ||
				e.getAdvancement().getKey() == null ||
				e.getAdvancement().getKey().getKey() == null ||
				!e.getAdvancement().getKey().getKey().contains("/"))
			return;
		
		String type = e.getAdvancement().getKey().getKey().split("/")[0];
		
		List<String> advancementTypes = Arrays.stream(this.getConfig().getString("advancement-types").split("\\,")) // split on comma
                .map(str -> str.trim()) // remove white-spaces
                .collect(Collectors.toList()); // collect to List
		
		if (telegramHook.connected && advancementTypes.contains(type)) {
			
			String toDisplay = e.getAdvancement().getKey().getKey()
					.replace(type + "/", "")
					.replaceAll("_", " ");
			
			if(toDisplay.equals("root"))
				return;
			
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG(
					"advancement-message", 
					e.getPlayer().getDisplayName(), 
					toDisplay)[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (!this.getConfig().getBoolean("enable-joinquitmessages"))
			return;

		if(isSuperVanish && VanishAPI.isInvisible(e.getPlayer()))
			return;

		if (telegramHook.connected) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("quit-message", e.getPlayer().getName())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent e) {
		if (!this.getConfig().getBoolean("enable-chatmessages"))
			return;
		if (e.isCancelled())
			return;
		if (telegramHook.connected) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = EmojiParser.parseToUnicode(Utils
					.escape(Utils.formatMSG("general-message-to-telegram", e.getPlayer().getName(), e.getMessage())[0])
					.replaceAll("§.", "")
					.replaceAll("\uF801", ""));
			telegramHook.sendAll(chat);
		}
	}

	public static TelegramChat getInstance()
	{
		return instance;
	}

}
