package com.scarsz.discordsrv;

import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.scarsz.discordsrv.hooks.HerochatHook;
import com.scarsz.discordsrv.hooks.LegendChatHook;
import com.scarsz.discordsrv.hooks.VentureChatHook;
import com.scarsz.discordsrv.listeners.*;
import com.scarsz.discordsrv.objects.Tuple;
import com.scarsz.discordsrv.threads.ChannelTopicUpdater;
import com.scarsz.discordsrv.threads.ServerLogWatcher;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.*;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.utils.AvatarUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.*;

@SuppressWarnings({"Convert2streamapi", "unused", "unchecked"})
public class DiscordSRV extends JavaPlugin {

    public static JDA api;
    public static Plugin plugin;
    public static Long startTime = System.nanoTime();
    public static Gson gson = new Gson();
    public static HashMap<String, String> colors = new HashMap<>();
    public static Boolean updateIsAvailable = false;
    public static Boolean canUsePing = false;
    public static ServerLogWatcher serverLogWatcher;
    public static ChannelTopicUpdater channelTopicUpdater;
    public static List<String> unsubscribedPlayers = new ArrayList<>();

    public static HashMap<String, TextChannel> channels = new HashMap<>();
    public static TextChannel chatChannel;
    public static TextChannel consoleChannel;

    public static Boolean usingHerochat = false;
    public static Boolean usingLegendChat = false;
    public static Boolean usingVentureChat = false;

    public void onEnable() {
        // set static plugin variable for discordsrv methods to use
        plugin = this;

        // load config, create if doesn't exist, update config if old
        if (!new File(getDataFolder(), "colors.json").exists()) saveResource("colors.json", false);
        if (!new File(getDataFolder(), "channels.json").exists()) saveResource("channels.json", false);
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
            reloadConfig();
        }
        if (getConfig().getDouble("ConfigVersion") < Double.parseDouble(getDescription().getVersion()) || !getConfig().isSet("ConfigVersion"))
            try {
                //Files.move(new File(getDataFolder(), "config.yml"), new File(getDataFolder(), "config.yml-build." + getConfig().getDouble("ConfigVersion") + ".old"));
                getLogger().info("Your DiscordSRV config file was outdated; attempting migration...");

                File config = new File(getDataFolder(), "config.yml");
                File oldConfig = new File(getDataFolder(), "config.yml-build." + getConfig().getDouble("ConfigVersion") + ".old");
                Files.move(config, oldConfig);
                if (!new File(getDataFolder(), "config.yml").exists()) saveResource("config.yml", false);

                Scanner s1 = new Scanner(oldConfig);
                ArrayList<String> oldConfigLines = new ArrayList<>();
                while (s1.hasNextLine()) oldConfigLines.add(s1.nextLine());
                s1.close();

                Scanner s2 = new Scanner(config);
                ArrayList<String> newConfigLines = new ArrayList<>();
                while (s2.hasNextLine()) newConfigLines.add(s2.nextLine());
                s2.close();

                Map<String, String> oldConfigMap = new HashMap<>();
                for (String line : oldConfigLines) {
                    if (line.startsWith("#") || line.startsWith("-") || line.isEmpty()) continue;
                    List<String> lineSplit = new ArrayList<>();
                    Collections.addAll(lineSplit, line.split(": +|:"));
                    if (lineSplit.size() < 2) continue;
                    String key = lineSplit.get(0);
                    lineSplit.remove(0);
                    String value = String.join(": ", lineSplit);
                    oldConfigMap.put(key, value);
                }

                Map<String, String> newConfigMap = new HashMap<>();
                for (String line : newConfigLines) {
                    if (line.startsWith("#") || line.startsWith("-") || line.isEmpty()) continue;
                    List<String> lineSplit = new ArrayList<>();
                    Collections.addAll(lineSplit, line.split(": +|:"));
                    if (lineSplit.size() >= 2) newConfigMap.put(lineSplit.get(0), lineSplit.get(1));
                }

                for (String key : oldConfigMap.keySet()) {
                    if (newConfigMap.containsKey(key) && !key.startsWith("ConfigVersion")) {
                        String oldKey = oldConfigMap.get(key);
                        if (key.toLowerCase().equals("bottoken")) oldKey = "OMITTED";
                        getLogger().info("Migrating config option " + key + " with value " + oldKey + " to new config");
                        newConfigMap.put(key, oldConfigMap.get(key));
                    }
                }

                for (String line : newConfigLines) {
                    if (line.startsWith("#") || line.startsWith("ConfigVersion")) continue;
                    String key = line.split(":")[0];
                    if (oldConfigMap.containsKey(key))
                        newConfigLines.set(newConfigLines.indexOf(line), key + ": " + oldConfigMap.get(key));
                }

                BufferedWriter writer = new BufferedWriter(new FileWriter(config));
                for (String line : newConfigLines) writer.write(line + System.lineSeparator());
                writer.flush();
                writer.close();

                getLogger().info("Migration complete. Note: migration does not apply to config options that are multiple-line lists.");
                reloadConfig();
            } catch (IOException ignored) { }
        if (!new File(getDataFolder(), "config.yml").exists()) saveResource("config.yml", false);
        reloadConfig();

        // update check
        if (!plugin.getConfig().getBoolean("UpdateCheckDisabled")) {
            double latest = Double.parseDouble(requestHttp("https://raw.githubusercontent.com/Scarsz/DiscordSRV/master/latestbuild"));
            if (latest > Double.parseDouble(getDescription().getVersion())) {
                getLogger().warning(System.lineSeparator() + System.lineSeparator() + "The current build of DiscordSRV is outdated! Get build " + latest + " at http://dev.bukkit.org/bukkit-plugins/discordsrv/" + System.lineSeparator() + System.lineSeparator());
                updateIsAvailable = true;
            }

            double minimum = Double.parseDouble(requestHttp("https://raw.githubusercontent.com/Scarsz/DiscordSRV/master/minimumbuild"));
            if (minimum > Double.parseDouble(getDescription().getVersion())) {
                getLogger().warning(System.lineSeparator() + System.lineSeparator() + "The current build of DiscordSRV does not meet the minimum! DiscordSRV will not start. Get build " + latest + " at http://dev.bukkit.org/server-mods/discordsrv/" + System.lineSeparator() + System.lineSeparator());
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        // login to discord
        buildJda();
        if (api == null) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // print the things the bot can see
        for (Guild server : api.getGuilds()) {
            getLogger().info("Found guild " + server);
            for (Channel channel : server.getTextChannels()) {
                getLogger().info("- " + channel);
            }
        }

        if (!new File(getDataFolder(), "channels.json").exists()) saveResource("channels.json", false);
        try {
            for (Tuple<String, String> channel : (List<Tuple<String, String>>) gson.fromJson(Files.toString(new File(getDataFolder(), "channels.json"), Charset.defaultCharset()), new TypeToken<List<Tuple<String, String>>>(){}.getType())) {
                TextChannel requestedChannel = api.getTextChannelById(channel.channelId());
                if (requestedChannel == null) continue;
                channels.put(channel.channelName(), requestedChannel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // check & get location info
        chatChannel = getTextChannelFromChannelName(getConfig().getString("DiscordMainChatChannel"));
        consoleChannel = api.getTextChannelById(getConfig().getString("DiscordConsoleChannelId"));

        if (chatChannel == null) getLogger().warning("Specified chat channel from channels.json could not be found (is it's name set to \"" + getConfig().getString("DiscordMainChatChannel") + "\"?)");
        if (consoleChannel == null) getLogger().warning("Specified console channel from config could not be found");
        if (chatChannel == null && consoleChannel == null) {
            getLogger().severe("Chat and console channels are both unavailable, disabling");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // send startup message if enabled
        if (plugin.getConfig().getBoolean("DiscordChatChannelServerStartupMessageEnabled"))
            sendMessage(chatChannel, DiscordSRV.plugin.getConfig().getString("DiscordChatChannelServerStartupMessage"));

        // in-game chat events
        if (checkIfPluginEnabled("herochat") && getConfig().getBoolean("HeroChatHook")) {
            getLogger().info("Enabling Herochat hook");
            getServer().getPluginManager().registerEvents(new HerochatHook(), this);
        } else if (checkIfPluginEnabled("Legendchat") && getConfig().getBoolean("LegendChatHook")) {
            getLogger().info("Enabling Legendchat hook");
            getServer().getPluginManager().registerEvents(new LegendChatHook(), this);
        } else if (checkIfPluginEnabled("VentureChat") && getConfig().getBoolean("VentureChatHook")) {
            getLogger().info("Enabling VentureChatHook hook");
            getServer().getPluginManager().registerEvents(new VentureChatHook(), this);
        } else {
            getLogger().info("No plugin hooks enabled");
            getServer().getPluginManager().registerEvents(new ChatListener(), this);
        }

        // in-game death events
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);

        // console streaming thread & helper
        startServerLogWatcher();

        // channel topic updating thread
        if (channelTopicUpdater == null) {
            channelTopicUpdater = new ChannelTopicUpdater();
            channelTopicUpdater.start();
        }

        // player join/leave message events
        if (getConfig().getBoolean("MinecraftPlayerJoinMessageEnabled"))
            getServer().getPluginManager().registerEvents(new PlayerJoinLeaveListener(), this);

        // player achievement events
        if (getConfig().getBoolean("MinecraftPlayerAchievementMessagesEnabled"))
            getServer().getPluginManager().registerEvents(new AchievementListener(), this);

        // enable metrics
        if (!getConfig().getBoolean("MetricsDisabled"))
            try {
                Metrics metrics = new Metrics(this);
                metrics.start();
            } catch (IOException e) {
                getLogger().warning("Unable to start metrics. Oh well.");
            }

        // - chat channel
        if (getConfig().getBoolean("DiscordChatChannelDiscordToMinecraft") || getConfig().getBoolean("DiscordChatChannelMinecraftToDiscord")) {
            if (!testChannel(chatChannel)) getLogger().warning("Channel " + chatChannel + " was not accessible");
            if (testChannel(chatChannel) && !chatChannel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_WRITE)) getLogger().warning("The bot does not have access to send messages in " + chatChannel);
            if (testChannel(chatChannel) && !chatChannel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_READ)) getLogger().warning("The bot does not have access to read messages in " + chatChannel);
        }
        // - console channel
        if (consoleChannel != null) {
            if (!testChannel(consoleChannel)) getLogger().warning("Channel " + consoleChannel + " was not accessible");
            if (testChannel(consoleChannel) && !consoleChannel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_WRITE)) getLogger().warning("The bot does not have access to send messages in " + consoleChannel);
            if (testChannel(consoleChannel) && !consoleChannel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_READ)) getLogger().warning("The bot does not have access to read messages in " + consoleChannel);
        }

        // load unsubscribed users
        if (new File(getDataFolder(), "unsubscribed.txt").exists())
            try {
                Collections.addAll(unsubscribedPlayers, Files.toString(new File(getDataFolder(), "unsubscribed.txt"), Charset.defaultCharset()).split("\n"));
            } catch (IOException e) {
                e.printStackTrace();
            }

        // load user-defined colors
        if (!new File(getDataFolder(), "colors.json").exists()) saveResource("colors.json", false);
        try {
            LinkedTreeMap<String, String> colorsJson = gson.fromJson(Files.toString(new File(getDataFolder(), "colors.json"), Charset.defaultCharset()), LinkedTreeMap.class);
            for (String key : colorsJson.keySet()) {
                String definition = colorsJson.get(key).toLowerCase();
                key = key.toLowerCase();
                getLogger().info("Defining color " + key + " as \"" + definition + "\"");
                colors.put(key, definition);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // start TPS poller
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Lag(), 100L, 1L);

        // check if server can do pings
        Double thisVersion = Double.valueOf(Bukkit.getBukkitVersion().split("\\.", 2)[1].split("-")[0]);
        canUsePing = thisVersion >= 9.0;
        if (!canUsePing) getLogger().warning("Server version is <1.9, mention sounds are disabled");
    }
    public void onDisable() {
        // kill server log watcher & helper
        if (serverLogWatcher != null && !serverLogWatcher.isInterrupted())
            serverLogWatcher.interrupt();
        serverLogWatcher = null;

        // server shutdown message
        if (chatChannel != null && getConfig().getBoolean("DiscordChatChannelServerShutdownMessageEnabled")) chatChannel.sendMessage(getConfig().getString("DiscordChatChannelServerShutdownMessage"));

        // disconnect from discord
        try { api.shutdown(false); } catch (Exception e) { getLogger().info("Discord shutting down before logged in"); }
        api = null;

        // save unsubscribed users
        if (new File(getDataFolder(), "unsubscribed.txt").exists()) new File(getDataFolder(), "unsubscribed.txt").delete();
        String players = "";
        for (String id : unsubscribedPlayers) players += id + "\n";
        if (players.length() > 0) {
            players = players.substring(0, players.length() - 1);
            try {
				Files.write(players, new File(getDataFolder(), "unsubscribed.txt"), Charset.defaultCharset());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.isOp()) sender.sendMessage("/discord toggle/subscribe/unsubscribe");
            else sender.sendMessage("/discord bcast/setpicture/reload/rebuild/debug/toggle/subscribe/unsubscribe");
            return true;
        }
        if (args[0].equalsIgnoreCase("bcast")) {
            if (!sender.isOp()) {
                sender.sendMessage("Must be OP to use this command");
                return true;
            }
            List<String> messageStrings = Arrays.asList(args);
            String message = String.join(" ", messageStrings.subList(1, messageStrings.size()));
            sendMessage(chatChannel, message);
            return true;
        }
        if (args[0].equalsIgnoreCase("setpicture")) {
            if (!sender.isOp()) {
                sender.sendMessage("Must be OP to use this command");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("Must give URL to picture to set as bot picture");
                return true;
            }
            try {
                sender.sendMessage("Downloading picture...");
                ReadableByteChannel in = Channels.newChannel(new URL(args[1]).openStream());
                FileChannel out = new FileOutputStream(getDataFolder().getAbsolutePath() + "/picture.jpg").getChannel();
                out.transferFrom(in, 0, Long.MAX_VALUE);
                out.close();
            } catch (IOException e) {
                sender.sendMessage("Download failed: " + e.getMessage());
                return true;
            }
            try {
                api.getAccountManager().setAvatar(AvatarUtil.getAvatar(new File(getDataFolder().getAbsolutePath() + "/picture.jpg"))).update();
                sender.sendMessage("Picture updated successfully");
            } catch (UnsupportedEncodingException e) {
                sender.sendMessage("Error setting picture as avatar: " + e.getMessage());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp()) return true;
            reloadConfig();
            sender.sendMessage("DiscordSRV config has been reloaded. Some config options require a restart.");
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (!sender.isOp()) return true;
            FileReader fr = null;
            try {
                fr = new FileReader(new File(new File(".").getAbsolutePath() + "/logs/latest.log").getAbsolutePath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            BufferedReader br = new BufferedReader(fr);

            List<String> discordsrvMessages = new ArrayList<>();
            discordsrvMessages.add(ChatColor.RED + "Lines for DiscordSRV from latest.log:");
            Boolean done = false;
            while (!done)
            {
                String line = null;
                try {
                    line = br.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (line == null) done = true;
                if (line != null && line.toLowerCase().contains("discordsrv")) discordsrvMessages.add(line);
            }
            discordsrvMessages.add(ChatColor.AQUA + "Version: " + ChatColor.RESET + Bukkit.getVersion());
            discordsrvMessages.add(ChatColor.AQUA + "Bukkit version: " + ChatColor.RESET + Bukkit.getBukkitVersion());
            discordsrvMessages.add(ChatColor.AQUA + "OS: " + ChatColor.RESET + System.getProperty("os.name"));
            discordsrvMessages.forEach(sender::sendMessage);
            try { fr.close(); } catch (IOException e) { e.printStackTrace(); }
            return true;
        }
        if (args[0].equalsIgnoreCase("rebuild")) {
            if (!sender.isOp()) return true;
            //buildJda();
            sender.sendMessage("Disabled because no workie");
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player senderPlayer = (Player) sender;
        if (args[0].equalsIgnoreCase("toggle")) {
            Boolean subscribed = getSubscribed(senderPlayer.getUniqueId());
            setSubscribed(senderPlayer.getUniqueId(), !subscribed);

            String subscribedMessage = getSubscribed(senderPlayer.getUniqueId()) ? "subscribed" : "unsubscribed";
            sender.sendMessage(ChatColor.AQUA + "You have been " + subscribedMessage + " to Discord messages.");
        }
        if (args[0].equalsIgnoreCase("subscribe")) {
            setSubscribed(senderPlayer.getUniqueId(), true);
            sender.sendMessage(ChatColor.AQUA + "You have been subscribed to Discord messages.");
        }
        if (args[0].equalsIgnoreCase("unsubscribe")) {
            setSubscribed(senderPlayer.getUniqueId(), false);
            sender.sendMessage(ChatColor.AQUA + "You are no longer subscribed to Discord messages.");
        }
        return true;
    }

    public static void processChatEvent(Boolean isCancelled, Player sender, String message, String channel) {
        // ReportCanceledChatEvents debug message
        if (plugin.getConfig().getBoolean("ReportCanceledChatEvents")) plugin.getLogger().info("Chat message received, canceled: " + isCancelled);

        // return if player doesn't have permission
        if (!sender.hasPermission("discordsrv.chat")) {
            if (plugin.getConfig().getBoolean("EventDebug")) plugin.getLogger().info("User " + sender.getName() + " sent a message but it was not delivered to Discord due to lack of permission");
            return;
        }

        // return if event canceled
        if (plugin.getConfig().getBoolean("DontSendCanceledChatEvents") && isCancelled) return;

        // return if should not send in-game chat
        if (!plugin.getConfig().getBoolean("DiscordChatChannelMinecraftToDiscord")) return;

        // return if user is unsubscribed from Discord and config says don't send those peoples' messages
        if (!getSubscribed(sender.getUniqueId()) && !plugin.getConfig().getBoolean("MinecraftUnsubscribedMessageForwarding")) return;

        // return if doesn't match prefix filter
        if (!message.startsWith(plugin.getConfig().getString("DiscordChatChannelPrefix"))) return;
        
        String userPrimaryGroup = getPrimaryGroup(sender);
        Boolean hasGoodGroup = !"".equals(userPrimaryGroup.replace(" ", ""));
        
        String format = hasGoodGroup ? plugin.getConfig().getString("MinecraftChatToDiscordMessageFormat") : plugin.getConfig().getString("MinecraftChatToDiscordMessageFormatNoPrimaryGroup");
        String discordMessage = format
                .replaceAll("&([0-9a-qs-z])", "")
                .replace("%message%", ChatColor.stripColor(message))
                .replace("%primarygroup%", getPrimaryGroup(sender))
                .replace("%displayname%", ChatColor.stripColor(sender.getDisplayName()))
                .replace("%username%", ChatColor.stripColor(sender.getName()))
                .replace("%time%", new Date().toString());

        discordMessage = convertMentionsFromNames(discordMessage);

        if (channel == null) sendMessage(chatChannel, discordMessage);
        else sendMessage(getTextChannelFromChannelName(channel), discordMessage);
    }

    public static void startServerLogWatcher() {
        // kill server log watcher if it's already started
        if (serverLogWatcher != null && !serverLogWatcher.isInterrupted())
            serverLogWatcher.interrupt();
        serverLogWatcher = null;

        if (consoleChannel != null) {
            serverLogWatcher = new ServerLogWatcher(api);
            serverLogWatcher.start();
        }
    }
    private void buildJda() {
        // shutdown if already started
        if (api != null) try { api.shutdown(false); } catch (Exception e) { e.printStackTrace(); }

        try {
            api = new JDABuilder()
                    .setBotToken(getConfig().getString("BotToken"))
                    .addListener(new DiscordListener())
                    .setAutoReconnect(true)
                    .setAudioEnabled(false)
                    .buildBlocking();
        } catch (LoginException | IllegalArgumentException | InterruptedException e) {
            getLogger().severe(System.lineSeparator() + System.lineSeparator() + "Error building DiscordSRV: " + e.getMessage() + System.lineSeparator() + System.lineSeparator());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // game status
        if (!getConfig().getString("DiscordGameStatus").isEmpty())
            api.getAccountManager().setGame(getConfig().getString("DiscordGameStatus"));
    }
    private static String requestHttp(String requestUrl) {
        String sourceLine = null;

        URL address = null;
        try {
            address = new URL(requestUrl);
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }

        InputStreamReader pageInput = null;
        try {
            pageInput = new InputStreamReader(address.openStream());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        BufferedReader source = new BufferedReader(pageInput);

        try {
            sourceLine = source.readLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return sourceLine;
    }
    private static Boolean testChannel(TextChannel channel) {
        return channel != null;
    }
    public static void sendMessage(TextChannel channel, String message) {
        sendMessage(channel, message, true);
    }
    public static void sendMessage(TextChannel channel, String message, Boolean editMessage) {
        String newMessage = message;

        if (api == null || channel == null || (!channel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_READ) || !channel.checkPermission(api.getSelfInfo(), Permission.MESSAGE_WRITE))) return;
        newMessage = ChatColor.stripColor(newMessage).replace("[m", "").replaceAll("\\[[0-9]{1,2};[0-9]{1,2};[0-9]{1,2}m", "").replaceAll("\\[[0-9]{1,3}m", "").replace("[m", "").replaceAll("\\[[0-9]{1,2};[0-9]{1,2};[0-9]{1,2}m", "").replaceAll("\\[[0-9]{1,3}m", "");

        if (!editMessage)
            for (Object phrase : DiscordSRV.plugin.getConfig().getList("DiscordChatChannelCutPhrases")) {
                if (newMessage.contains((String) phrase)) {
                    newMessage = newMessage.replace((String) phrase, "");
                }
            }

        String overflow = null;
        if (newMessage.length() > 2000) {
            plugin.getLogger().warning("Tried sending message with length of " + newMessage.length() + " (" + (newMessage.length() - 2000) + " over limit)");
            overflow = newMessage.substring(1999);
            newMessage = newMessage.substring(0, 1999);
        }

        channel.sendMessageAsync(newMessage, null);
        if (overflow != null) sendMessage(channel, overflow, editMessage);
    }
    public static void sendMessageToChatChannel(String message) {
        sendMessage(chatChannel, message);
    }
    public static void sendMessageToConsoleChannel(String message) {
        sendMessage(consoleChannel, message);
    }
    private static void verboseWait(long time) {
        if (plugin.getConfig().getBoolean("RateLimitSleepVerbose")) {
            long intervals = time / 4;
            while (time > intervals) {
                System.out.println("Waiting " + time + " ms");
                try { Thread.sleep(intervals); } catch (InterruptedException ignored) {}
                time = time - intervals;
            }
            System.out.println("Waiting " + time + " ms");
        }
        else try { Thread.sleep(time); } catch (InterruptedException ignored) {}
    }
    private static String getPrimaryGroup(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return " ";

        RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> service = DiscordSRV.plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);

        if (service == null) return " ";
        try {
            String primaryGroup = service.getProvider().getPrimaryGroup(player);
            if (!primaryGroup.equals("default")) return primaryGroup;
        } catch (Exception ignored) { }
        return " ";
    }
    public static String getAllRoles(MessageReceivedEvent event) {
        String roles = "";
        for (Role role : event.getGuild().getRolesForUser(event.getAuthor())) {
            roles += role.getName() + DiscordSRV.plugin.getConfig().getString("DiscordToMinecraftAllRolesSeperator");
        }
        if (!roles.isEmpty()) roles = roles.substring(0, roles.length() - DiscordSRV.plugin.getConfig().getString("DiscordToMinecraftAllRolesSeperator").length());
        return roles;
    }
    public static Role getTopRole(MessageReceivedEvent event) {
        Role highestRole = null;
        for (Role role : event.getGuild().getRolesForUser(event.getAuthor())) {
            if (highestRole == null) highestRole = role;
            else if (highestRole.getPosition() < role.getPosition()) highestRole = role;
        }
        return highestRole;
    }
    public static String getRoleName(Role role) {
        return role == null ? "" : role.getName();
    }
    public static List<Player> getOnlinePlayers() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> playersToRemove = new ArrayList<>();
        for (Player player : players) {
            if (VanishedPlayerCheck.checkPlayerIsVanished(player))
                playersToRemove.add(player);
        }
        players.removeAll(playersToRemove);
        return players;
    }
    private static Boolean getSubscribed(UUID uniqueId) {
        return !unsubscribedPlayers.contains(uniqueId.toString());
    }
    private static void setSubscribed(UUID uniqueId, Boolean subscribed) {
        if (subscribed && unsubscribedPlayers.contains(uniqueId.toString())) unsubscribedPlayers.remove(uniqueId.toString());
        if (!subscribed && !unsubscribedPlayers.contains(uniqueId.toString())) unsubscribedPlayers.add(uniqueId.toString());
    }
    public static void broadcastMessageToMinecraftServer(String message, String rawMessage, String channel) {
        Boolean usingChatPlugin = usingLegendChat || usingHerochat || !channel.equalsIgnoreCase(plugin.getConfig().getString("DiscordMainChatChannel"));
        if (!usingChatPlugin) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (getSubscribed(player.getUniqueId())) {
                    player.sendMessage(message);
                    notifyPlayersOfMentions(Collections.singletonList(player), rawMessage);
                }
            }
        } else {
            if (usingHerochat) HerochatHook.broadcastMessageToChannel(channel, message, rawMessage);
            if (usingLegendChat) LegendChatHook.broadcastMessageToChannel(channel, message, rawMessage);
            if (usingVentureChat) VentureChatHook.broadcastMessageToChannel(channel, message, rawMessage);
        }
    }
    public static String convertRoleToMinecraftColor(Role role) {
        if (role == null) return "";
        String colorHex = Integer.toHexString(role.getColor());

        String output = colors.get(colorHex);
        return output != null ? output : "";
    }
    private static String convertMentionsFromNames(String message) {
        if (!message.contains("@")) return message;
        List<String> splitMessage = new ArrayList<>(Arrays.asList(message.split("@| ")));
        for (User user : chatChannel.getUsers())
            for (String segment : splitMessage)
                if (user.getUsername().toLowerCase().equals(segment.toLowerCase()) || (chatChannel.getGuild().getNicknameForUser(user) != null && chatChannel.getGuild().getNicknameForUser(user).toLowerCase().equals(segment.toLowerCase()))) {
                    splitMessage.set(splitMessage.indexOf(segment), user.getAsMention());
                }
        splitMessage.removeAll(Arrays.asList("", null));
        return String.join(" ", splitMessage);
    }
    public static String getDestinationChannelName(TextChannel textChannel) {
        for (String channelName : channels.keySet()) {
            String registeredChannelId = getTextChannelFromChannelName(channelName).getId();
            String paramChannelId = textChannel.getId();
            if (registeredChannelId.equals(paramChannelId)) {
                return channelName;
            }
        }
        return null;
    }
    public static void notifyPlayersOfMentions(List<Player> possiblyPlayers, String parseMessage) {
        if (!canUsePing) return;

        List<String> splitMessage = new ArrayList<>();
        for (String phrase : parseMessage.replaceAll("[^a-zA-Z]", " ").split(" ")) splitMessage.add(phrase.toLowerCase());

        for (Player player : possiblyPlayers) {
            Boolean playerOnline = player.isOnline();
            Boolean phraseContainsName = splitMessage.contains(player.getName().toLowerCase());
            Boolean phraseContainsDisplayName = splitMessage.contains(ChatColor.stripColor(player.getDisplayName()).toLowerCase());
            Boolean shouldDing = phraseContainsName || phraseContainsDisplayName;
            if (playerOnline && shouldDing) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1, 1);
        }
    }
    public static TextChannel getTextChannelFromChannelName(String channelName) {
        if (channels.containsKey(channelName)) return channels.get(channelName);
        if (channels.containsKey(channelName.toLowerCase())) return channels.get(channelName.toLowerCase());
        return null;
    }
    public static Boolean chatChannelIsLinked(String channelName) {
        if (channels.containsKey(channelName) || channels.containsKey(channelName.toLowerCase())) return true;
        return false;
    }
    public static String getDisplayName(Guild guild, User user) {
        String nickname = guild.getNicknameForUser(user);
        return nickname == null ? user.getUsername() : nickname;
    }
    public static Boolean checkIfPluginEnabled(String pluginName) {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().toLowerCase().startsWith(pluginName.toLowerCase())) return true;
        }
        return false;
    }
}
