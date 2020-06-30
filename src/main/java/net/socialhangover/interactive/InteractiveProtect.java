package net.socialhangover.interactive;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InteractiveProtect extends JavaPlugin implements Listener {

    private static final Map<Player, Boolean> playerPermissions = new WeakHashMap<>();

    private final static Pattern TELEPORT_PATTERN = Pattern.compile("\\(x(?<x>-?\\d+)/y(?<y>-?\\d+)/z(?<z>-?\\d+)(?:/(?<world>\\w+))?\\)");
    private final static Pattern PAGINATION_PATTERN = Pattern.compile("Page (\\d+)/(\\d+). View older");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final String teleportCommand = getConfig().getString("teleport-command", "/tppos %x %y %z %world");

        getServer().getPluginManager().registerEvents(this, this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ListenerPriority.MONITOR, Server.CHAT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (!playerPermissions.getOrDefault(event.getPlayer(), false)) {
                    return;
                }

                PacketContainer packet = event.getPacket();
                WrappedChatComponent chat = packet.getChatComponents().read(0);

                if (chat == null) {
                    return;
                }

                String json = chat.getJson();
                if (json == null) {
                    return;
                }

                StringBuilder sb = new StringBuilder(json);

                Matcher matcher = TELEPORT_PATTERN.matcher(json);
                while (matcher.find()) {
                    int end = matcher.end();
                    String world = matcher.group("world");

                    sb.insert(end + 1, ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + teleportCommand
                            .replace("%x", matcher.group("x"))
                            .replace("%y", matcher.group("y"))
                            .replace("%z", matcher.group("z"))
                            .replace("%world", world == null ? "" : world) + "\"},\"hoverEvent\":{\"action\":\"show_text\", \"value\":\"Click to teleport\"}");
                }

                matcher = PAGINATION_PATTERN.matcher(json);
                if (matcher.find()) {
                    Number current = Integer.parseInt(matcher.group(1));
                    Number last = Integer.parseInt(matcher.group(2));

                    int start = matcher.start();

                    if (current.intValue() >= 1 && current.intValue() < last.intValue()) {
                        sb.insert(start + 4, " \"},{\"text\":\">>>\",\"color\":\"gold\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/co l %page\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to navigate\"}},{\"color\":\"white\", \"text\":\""
                                .replace("%page", String.valueOf(current.intValue() + 1)));
                    }
                    if (current.intValue() > 1) {
                        sb.insert(start - 25, "{\"text\":\"<<< \",\"color\":\"gold\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/co l %page\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to navigate\"}},"
                                .replace("%page", String.valueOf(current.intValue() - 1)));
                    }
                }

                chat.setJson(sb.toString());
                event.getPacket().getChatComponents().write(0, chat);
            }
        });
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // We check permission on join to prevent LuckPerm's verbose (or similar) logging from crashing the server.
        playerPermissions.put(player, player.hasPermission("ip.interact"));
    }
}
