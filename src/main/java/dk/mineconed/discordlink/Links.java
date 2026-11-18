package dk.mineconed.discordlink;

import net.dv8tion.jda.api.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Made by Mineconed
 *
 * The links between Minecraft players and Discord accounts.
 */
public final class Links implements Listener {

    private static final String TEGN = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** A code waiting to be used. */
    private static final class Kode {
        UUID u;
        String navn;
        long udloeb;
    }

    private final DiscordLink pl;
    private final File fil;
    private YamlConfiguration data;
    private final Map<String, Kode> koder = new ConcurrentHashMap<String, Kode>();
    private final Map<String, List<UUID>> dcTilMc = new ConcurrentHashMap<String, List<UUID>>();
    private final Map<UUID, String> mcTilDc = new ConcurrentHashMap<UUID, String>();
    private final Random rnd = new Random();

    Links(DiscordLink pl) {
        this.pl = pl;
        this.fil = new File(pl.getDataFolder(), "links.yml");
    }

    // ═══════════════ DATA ═══════════════

    void indlaes() {
        data = fil.exists() ? YamlConfiguration.loadConfiguration(fil) : new YamlConfiguration();
        dcTilMc.clear();
        mcTilDc.clear();
        ConfigurationSection cs = data.getConfigurationSection("links");
        if (cs == null) {
            return;
        }
        for (String k : cs.getKeys(false)) {
            String id = cs.getString(k + ".discord");
            if (id == null) {
                continue;
            }
            try {
                UUID u = UUID.fromString(k);
                mcTilDc.put(u, id);
                liste(id).add(u);
            } catch (IllegalArgumentException ignore) {
                // bad line
            }
        }
    }

    private List<UUID> liste(String id) {
        List<UUID> l = dcTilMc.get(id);
        if (l == null) {
            l = new ArrayList<UUID>();
            dcTilMc.put(id, l);
        }
        return l;
    }

    private synchronized void gem() {
        try {
            data.save(fil);
        } catch (IOException e) {
            pl.getLogger().warning("Could not save links.yml: " + e.getMessage());
        }
    }

    String discordId(UUID u) {
        return mcTilDc.get(u);
    }

    List<UUID> konti(String dcId) {
        List<UUID> l = dcTilMc.get(dcId);
        return l == null ? new ArrayList<UUID>() : new ArrayList<UUID>(l);
    }

    String navn(UUID u) {
        return data.getString("links." + u + ".name", "?");
    }

    String tag(UUID u) {
        return data.getString("links." + u + ".tag", "?");
    }

    long tid(UUID u) {
        return data.getLong("links." + u + ".time", 0L);
    }

    int antal() {
        return mcTilDc.size();
    }

    /** Finds a linked player by name (also offline). */
    UUID findNavn(String navn) {
        for (UUID u : mcTilDc.keySet()) {
            if (navn(u).equalsIgnoreCase(navn)) {
                return u;
            }
        }
        return null;
    }

    // ═══════════════ KODER ═══════════════

    int minutter() {
        return Math.max(1, pl.getConfig().getInt("link.code_minutes", 5));
    }

    String kode(UUID u, String navn) {
        long nu = System.currentTimeMillis();
        for (Map.Entry<String, Kode> e : koder.entrySet()) {
            if (e.getValue().u.equals(u) && e.getValue().udloeb > nu) {
                return e.getKey();
            }
        }
        String k;
        do {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                b.append(TEGN.charAt(rnd.nextInt(TEGN.length())));
            }
            k = b.toString();
        } while (koder.containsKey(k));
        Kode ko = new Kode();
        ko.u = u;
        ko.navn = navn;
        ko.udloeb = nu + minutter() * 60000L;
        koder.put(k, ko);
        return k;
    }

    // ═══════════════ MINECRAFT ═══════════════

    void mcLink(Player p) {
        Beskeder m = pl.msg();
        String id = discordId(p.getUniqueId());
        if (id != null) {
            m.send(p, "link_already", "{discord}", tag(p.getUniqueId()));
            return;
        }
        String k = kode(p.getUniqueId(), p.getName());
        for (String linje : m.linjer("link_code", "{code}", k, "{minutes}", String.valueOf(minutter()))) {
            if (linje.contains(k)) {
                Visning.klik(p, linje, m.c("link_code_hover"), "copy", k);
            } else {
                p.sendMessage(linje);
            }
        }
        Visning.lyd(p, "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
    }

    void mcUnlink(CommandSender s, UUID u, String af) {
        String id = discordId(u);
        if (id == null) {
            pl.msg().send(s, af == null ? "unlink_not" : "admin_not_linked");
            return;
        }
        fjernLink(u, af);
        pl.msg().send(s, af == null ? "unlink_done" : "admin_unlinked", "{player}", navn(u));
    }

    // ═══════════════ DISCORD ═══════════════

    /** Uses a code from /link or the panel. Runs on a Discord thread, returns the reply. */
    String brugKode(String raa, final User user) {
        Beskeder m = pl.msg();
        final String kode = raa == null ? "" : raa.trim().toUpperCase();
        final String id = user.getId();
        int maks = Math.max(1, pl.getConfig().getInt("link.max_accounts_per_discord", 1));
        Kode k = koder.get(kode);
        if (k == null || k.udloeb < System.currentTimeMillis()) {
            if (pl.getConfig().getBoolean("logs.wrong_codes", false)) {
                pl.log().kort("log_wrong_code", "orange", user.getName(), "<@" + id + "> `" + DiscordLink.rens(kode) + "`");
            }
            return m.raw("dc_bad_code");
        }
        if (konti(id).size() >= maks) {
            return m.raw("dc_max_accounts", "{max}", String.valueOf(maks), "{player}", navn(konti(id).get(0)));
        }
        koder.remove(kode);
        final UUID u = k.u;
        final String navn = k.navn;
        final String tag = user.getName();
        final long oprettet = user.getTimeCreated().toEpochSecond();
        Bukkit.getScheduler().runTask(pl, new Runnable() {
            @Override
            public void run() {
                gemLink(u, navn, id, tag, oprettet, null);
            }
        });
        return m.raw("dc_link_ok", "{player}", navn);
    }

    /** /unlink in Discord. Runs on a Discord thread. */
    String dcUnlink(final String id) {
        final List<UUID> l = konti(id);
        if (l.isEmpty()) {
            return pl.msg().raw("dc_unlink_not");
        }
        if (!pl.getConfig().getBoolean("link.allow_unlink", true)) {
            return pl.msg().raw("dc_unlink_off");
        }
        final StringBuilder navne = new StringBuilder();
        for (UUID u : l) {
            navne.append(navne.length() > 0 ? ", " : "").append(navn(u));
        }
        Bukkit.getScheduler().runTask(pl, new Runnable() {
            @Override
            public void run() {
                for (UUID u : l) {
                    fjernLink(u, null);
                }
            }
        });
        return pl.msg().raw("dc_unlink_ok", "{player}", navne.toString());
    }

    // ═══════════════ GEM / FJERN ═══════════════

    /** Main thread. af = admin name for forced links, or null. */
    void gemLink(UUID u, String navn, String id, String tag, long oprettet, String af) {
        String gammel = discordId(u);
        if (gammel != null && !gammel.equals(id)) {
            fjernLink(u, af == null ? "relink" : af);
        }
        String b = "links." + u + ".";
        data.set(b + "discord", id);
        data.set(b + "name", navn);
        data.set(b + "tag", tag);
        data.set(b + "time", System.currentTimeMillis());
        data.set(b + "created", oprettet);
        mcTilDc.put(u, id);
        if (!liste(id).contains(u)) {
            liste(id).add(u);
        }
        gem();
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            pl.msg().send(p, "link_done", "{discord}", tag);
            Visning.lyd(p, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
            Visning.titel(p, pl.msg().c("link_title"), pl.msg().c("link_subtitle", "{discord}", tag), 10, 50, 10);
        }
        pl.sync().synk(u, navn, p);
        pl.log().fil(af == null ? "LINK" : "FORCE LINK", navn, u, tag, id, af);
        pl.log().discord(af == null ? "log_link" : "log_force_link", "green", navn, u, id, tag, oprettet, af, null);
        if (pl.getConfig().getBoolean("link.broadcast", false)) {
            Bukkit.broadcastMessage(pl.msg().c("prefix") + pl.msg().c("link_broadcast", "{player}", navn));
        }
    }

    /** Main thread. */
    void fjernLink(UUID u, String af) {
        String id = discordId(u);
        if (id == null) {
            return;
        }
        String navn = navn(u);
        String tag = tag(u);
        long oprettet = data.getLong("links." + u + ".created", 0L);
        data.set("links." + u, null);
        mcTilDc.remove(u);
        List<UUID> l = dcTilMc.get(id);
        if (l != null) {
            l.remove(u);
            if (l.isEmpty()) {
                dcTilMc.remove(id);
            }
        }
        gem();
        pl.sync().fjern(u, navn, id, konti(id).isEmpty());
        Player p = Bukkit.getPlayer(u);
        if (p != null && af != null && !af.equals("relink")) {
            pl.msg().send(p, "unlinked_by_admin");
        }
        String handling = af == null ? "UNLINK" : af.equals("left discord") ? "LEFT DISCORD" : "FORCE UNLINK";
        pl.log().fil(handling, navn, u, tag, id, af);
        pl.log().discord(af == null ? "log_unlink" : af.equals("left discord") ? "log_left" : "log_force_unlink",
                "red", navn, u, id, tag, oprettet, af == null || af.equals("left discord") ? null : af, null);
    }

    // ═══════════════ JOIN ═══════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (!pl.getConfig().getBoolean("link.require_link", false)) {
            return;
        }
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || discordId(e.getUniqueId()) != null) {
            return;
        }
        for (String s : pl.getConfig().getStringList("link.require_link_bypass")) {
            if (s.equalsIgnoreCase(e.getName())) {
                return;
            }
        }
        String k = kode(e.getUniqueId(), e.getName());
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                pl.msg().c("require_link_kick", "{code}", k, "{minutes}", String.valueOf(minutter())));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (discordId(p.getUniqueId()) == null) {
            if (pl.getConfig().getBoolean("link.remind_on_join", true)) {
                Bukkit.getScheduler().runTaskLater(pl, new Runnable() {
                    @Override
                    public void run() {
                        if (p.isOnline() && discordId(p.getUniqueId()) == null) {
                            Visning.klik(p, pl.msg().c("prefix") + pl.msg().c("remind"), pl.msg().c("remind_hover"), "command", "/link");
                        }
                    }
                }, 100L);
            }
            return;
        }
        if (!p.getName().equals(navn(p.getUniqueId()))) {
            data.set("links." + p.getUniqueId() + ".name", p.getName());
            gem();
        }
        Bukkit.getScheduler().runTaskLater(pl, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    pl.sync().synk(p.getUniqueId(), p.getName(), p);
                }
            }
        }, 60L);
    }
}
