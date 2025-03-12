package dev.hamta;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;

public class CommandLogger extends JavaPlugin implements Listener {

    private List<String> playersToTrack;
    private String webhookUrl;
    private String serverPrefix;
    private List<WarnCommand> warnCommands;

    @Override
    public void onEnable() {
        reloadConfigData();
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void reloadConfigData() {
        FileConfiguration config = getConfig();
        playersToTrack = new ArrayList<>(config.getStringList("players-to-track"));
        webhookUrl = config.getString("webhook-url");
        serverPrefix = config.getString("server-prefix", "[Serveur]");

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            getLogger().warning("Aucune URL de webhook n'est définie dans la configuration !");
        }

        warnCommands = loadWarnCommands(config.getConfigurationSection("warncommands"));
        getLogger().info("Configuration rechargée. Préfixe du serveur : " + serverPrefix);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("commandlogger")) {
            sender.sendMessage(ChatColor.GREEN + "Le plugin CommandLogger est actif !");
            return true;
        } else if (command.getName().equalsIgnoreCase("reloadcommandlogger")) {
            reloadConfigData();
            sender.sendMessage(ChatColor.GREEN + "Configuration rechargée avec succès !");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String playerName = event.getPlayer().getName();
        String command = event.getMessage();

        if (playersToTrack.contains(playerName)) {
            sendToDiscord(playerName, "a exécuté la commande: `" + command + "`", null);
            for (WarnCommand warnCommand : warnCommands) {
                if (command.startsWith(warnCommand.getCommand())) {
                    getLogger().info("Commande de " + playerName + ": " + command);
                    sendToDiscord(playerName, "a exécuté la commande: `" + command + "`", warnCommand.getMentionIds());
                    break;
                }
            }
        }
    }

    private void sendToDiscord(String playerName, String message, List<String> mentionIds) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);

                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE);
                    sdf.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
                    String timestamp = sdf.format(new Date());

                    String mentions = "";
                    if (mentionIds != null && !mentionIds.isEmpty()) {
                        for (String mentionId : mentionIds) {
                            mentions += "<@" + mentionId + "> ";
                        }
                    }

                    String jsonPayload = "{\"content\": \"" + serverPrefix + " [" + timestamp + "] **" + playerName + "** " + mentions + message + "\"}";

                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    connection.getResponseCode();
                    connection.disconnect();
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Erreur lors de l'envoi au webhook Discord", e);
                }
            }
        }.runTaskAsynchronously(this);
    }

    private List<WarnCommand> loadWarnCommands(ConfigurationSection configSection) {
        List<WarnCommand> warnCommands = new ArrayList<>();
        if (configSection != null) {
            for (String key : configSection.getKeys(false)) {
                String command = configSection.getString(key + ".command");
                List<String> mentionIds = configSection.getStringList(key + ".mention-ids");
                warnCommands.add(new WarnCommand(command, mentionIds));
            }
        }
        return warnCommands;
    }

    public static class WarnCommand {
        private final String command;
        private final List<String> mentionIds;

        public WarnCommand(String command, List<String> mentionIds) {
            this.command = command;
            this.mentionIds = mentionIds;
        }

        public String getCommand() {
            return command;
        }

        public List<String> getMentionIds() {
            return mentionIds;
        }
    }
}
