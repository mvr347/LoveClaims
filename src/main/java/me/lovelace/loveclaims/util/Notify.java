package me.lovelace.loveclaims.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.notify.LoveNotify;
import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Set;

/**
 * Single entry point for event notifications to a player through LoveNotify.
 *
 * <p>LoveCore decides the actual channel (player settings, overflow length) and falls back to chat,
 * so a denied action is never silently swallowed. The service is looked up per call: a neighbour may
 * register it later. LoveCore is only a softdepend, so a missing class means "send directly".</p>
 */
public final class Notify {
    private static final Set<Channel> ACTION_BAR = Set.of(Channel.ACTION_BAR);
    private static final Set<Channel> TITLE = Set.of(Channel.TITLE, Channel.ACTION_BAR);

    private Notify() {}

    /** Short status text; action bar by default. */
    public static void actionBar(Player player, Component message) {
        send(player, message, ACTION_BAR, false);
    }

    /** Headline text; title if the player enabled it, otherwise action bar. */
    public static void title(Player player, Component message) {
        send(player, message, TITLE, false);
    }

    /** Ordinary event (e.g. a protection denial). */
    public static void event(Player player, Component message) {
        send(player, message, ACTION_BAR, false);
    }

    /** Critical event: always duplicated to chat by LoveCore. */
    public static void important(Player player, Component message) {
        send(player, message, ACTION_BAR, true);
    }

    private static void send(Player player, Component message, Set<Channel> channels, boolean important) {
        try {
            var service = LoveCore.service(LoveNotify.class);
            if (service.isPresent()) {
                service.get().notify(player, message, channels, important);
                return;
            }
        } catch (NoClassDefFoundError missingLoveCore) {
            // fall through to direct delivery
        }
        direct(player, message, channels, important);
    }

    private static void direct(Player player, Component message, Set<Channel> channels, boolean important) {
        if (channels.contains(Channel.TITLE)) {
            player.showTitle(Title.title(message, Component.empty(),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))));
        } else {
            player.sendActionBar(message);
        }
        if (important) {
            player.sendMessage(message);
        }
    }
}
