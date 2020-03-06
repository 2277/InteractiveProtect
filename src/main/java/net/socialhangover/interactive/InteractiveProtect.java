package net.socialhangover.interactive;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import me.lucko.helper.Commands;
import me.lucko.helper.function.Numbers;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import me.lucko.helper.plugin.ap.PluginDependency;
import me.lucko.helper.protocol.Protocol;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "InteractiveProtect", version = "1.0.0", apiVersion = "1.15", depends = { @PluginDependency(value = "helper"), @PluginDependency(value = "ProtocolLib") })
public class InteractiveProtect extends ExtendedJavaPlugin {

    private final static Pattern TELEPORT_PATTERN = Pattern.compile("\\(x(?<x>-?\\d+)\\/y(?<y>-?\\d+)\\/z(?<z>-?\\d+)(?:\\/(?<world>\\w+))?\\)");
    private final static Pattern PAGINATION_PATTERN = Pattern.compile("Page (\\d+)\\/(\\d+). View older");

    private final static String TELEPORT_COMMAND = "/tppos %x %y %z %world";

    @Override
    protected void enable() {
        Protocol.subscribe(ListenerPriority.MONITOR, Server.CHAT)
                .filter(e -> e.getPlayer().hasPermission("ip.interact"))
                .handler(e -> {
                    PacketContainer packet = e.getPacket();
                    WrappedChatComponent chat = packet.getChatComponents().read(0);

                    if (chat == null) { return; }

                    String json = chat.getJson();
                    if (json == null) { return;}

                    StringBuilder sb = new StringBuilder(json);

                    Matcher matcher = TELEPORT_PATTERN.matcher(json);
                    while (matcher.find()) {
                        int end = matcher.end();
                        String world = matcher.group("world");

                        sb.insert(end + 1, ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + TELEPORT_COMMAND
                                .replace("%x", matcher.group("x"))
                                .replace("%y", matcher.group("y"))
                                .replace("%z", matcher.group("z"))
                                .replace("%world", world == null ? "" : world) + "\"},\"hoverEvent\":{\"action\":\"show_text\", \"value\":\"Click here to teleport\"}");
                    }

                    matcher = PAGINATION_PATTERN.matcher(json);
                    if (matcher.find()) {
                        Number current = Numbers.parseNullable(matcher.group(1));
                        Number last = Numbers.parseNullable(matcher.group(2));

                        if (current != null && last != null) {
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
                    }

                    chat.setJson(sb.toString());
                    e.getPacket().getChatComponents().write(0, chat);
                })
                .bindWith(this);

        Commands.create()
                .assertPlayer()
                .assertPermission("ip.tppos")
                .assertUsage("<x> <y> <z> [world]")
                .handler(c -> c.sender()
                        .teleport(new Location(c.arg(3)
                                .parse(World.class)
                                .orElse(c.sender().getLocation().getWorld()), c.arg(0)
                                .parseOrFail(Double.class), c.arg(1).parseOrFail(Double.class), c.arg(2)
                                .parseOrFail(Double.class)), TeleportCause.PLUGIN))
                .registerAndBind(this, "tppos");
    }

}
