package dk.mineconed.discordlink;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Made by Mineconed
 *
 * Logs every link, unlink and role change: to a Discord channel and to link_log.txt.
 */
public final class LinkLog {

    private final DiscordLink pl;
    private final File fil;

    LinkLog(DiscordLink pl) {
        this.pl = pl;
        this.fil = new File(pl.getDataFolder(), "link_log.txt");
    }

    static Color farve(String f) {
        if ("green".equals(f)) {
            return new Color(46, 204, 113);
        }
        if ("red".equals(f)) {
            return new Color(231, 76, 60);
        }
        if ("yellow".equals(f)) {
            return new Color(241, 196, 15);
        }
        if ("blue".equals(f)) {
            return new Color(52, 152, 219);
        }
        if ("orange".equals(f)) {
            return new Color(230, 126, 34);
        }
        return new Color(155, 89, 182);
    }

    private static String nu() {
        return new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date());
    }

    /** Writes one line to link_log.txt. Thread safe. */
    synchronized void fil(String handling, String mcNavn, UUID u, String dcNavn, String dcId, String af) {
        if (!pl.getConfig().getBoolean("logs.file", true)) {
            return;
        }
        String linje = nu() + " | " + handling + " | " + mcNavn + " (" + u + ") | " + dcNavn + " (" + dcId + ")"
                + (af == null || af.isEmpty() ? "" : " | by " + af) + System.lineSeparator();
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(fil, true), StandardCharsets.UTF_8);
            w.write(linje);
        } catch (Exception e) {
            pl.getLogger().warning("Could not write link_log.txt: " + e.getMessage());
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    /** The last lines of link_log.txt, newest first. filter = player name or Discord id, or null. */
    synchronized List<String> historik(String filter, int antal) {
        List<String> ud = new ArrayList<String>();
        if (!fil.exists()) {
            return ud;
        }
        try {
            List<String> alle = Files.readAllLines(fil.toPath(), StandardCharsets.UTF_8);
            for (int i = Math.subtractExact(alle.size(), 1); i >= 0 && ud.size() < antal; i = Math.subtractExact(i, 1)) {
                String l = alle.get(i);
                if (filter == null || l.toLowerCase().contains(filter.toLowerCase())) {
                    ud.add(l);
                }
            }
        } catch (Exception e) {
            pl.getLogger().warning("Could not read link_log.txt: " + e.getMessage());
        }
        return ud;
    }

    /** A Discord embed for a link or unlink. Thread safe. */
    void discord(String titelNoegle, String farve, String mcNavn, UUID u, String dcId, String dcNavn,
                 long oprettetSek, String af, String ekstra) {
        GuildMessageChannel k = pl.kanal(pl.getConfig().getString("logs.channel_id", ""));
        if (k == null) {
            return;
        }
        Beskeder m = pl.msg();
        EmbedBuilder b = new EmbedBuilder();
        b.setAuthor(m.raw(titelNoegle, "{player}", mcNavn), null, "https://mc\u002Dheads.net/avatar/" + u + "/64.png");
        b.setColor(farve(farve));
        b.addField(m.raw("log_field_minecraft"), "**" + DiscordLink.rens(mcNavn) + "**\n`" + u + "`", true);
        b.addField(m.raw("log_field_discord"), "<@" + dcId + ">\n`" + DiscordLink.rens(dcNavn) + "`\n`" + dcId + "`", true);
        if (oprettetSek > 0) {
            String felt = "<t:" + oprettetSek + ":D> (<t:" + oprettetSek + ":R>)";
            int dage = pl.getConfig().getInt("logs.new_account_warning_days", 7);
            long alder = Math.subtractExact(System.currentTimeMillis() / 1000L, oprettetSek) / 86400L;
            if (dage > 0 && alder < dage) {
                felt += "\n" + m.raw("log_new_account", "{days}", String.valueOf(alder));
            }
            b.addField(m.raw("log_field_created"), felt, false);
        }
        if (af != null && !af.isEmpty()) {
            b.addField(m.raw("log_field_by"), DiscordLink.rens(af), true);
        }
        if (ekstra != null && !ekstra.isEmpty()) {
            b.setDescription(ekstra.length() > 3900 ? ekstra.substring(0, 3900) : ekstra);
        }
        b.setFooter(m.raw("log_footer", "{total}", String.valueOf(pl.links().antal())));
        b.setTimestamp(Instant.now());
        k.sendMessageEmbeds(b.build()).queue(null, pl.fejl("log"));
    }

    /** Small embed for role changes and other notes. Thread safe. */
    void kort(String titelNoegle, String farve, String mcNavn, String tekst) {
        GuildMessageChannel k = pl.kanal(pl.getConfig().getString("logs.channel_id", ""));
        if (k == null) {
            return;
        }
        EmbedBuilder b = new EmbedBuilder();
        b.setAuthor(pl.msg().raw(titelNoegle, "{player}", mcNavn), null, "https://mc\u002Dheads.net/avatar/" + mcNavn + "/64.png");
        b.setColor(farve(farve));
        if (tekst != null && !tekst.isEmpty()) {
            b.setDescription(tekst.length() > 3900 ? tekst.substring(0, 3900) : tekst);
        }
        b.setTimestamp(Instant.now());
        k.sendMessageEmbeds(b.build()).queue(null, pl.fejl("log"));
    }
}
