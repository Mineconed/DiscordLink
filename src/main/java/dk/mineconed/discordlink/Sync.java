package dk.mineconed.discordlink;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Made by Mineconed
 *
 * Rank and role sync both ways, linked role and nickname.
 *   minecraft_to_discord: group (or permission) gives a Discord role
 *   discord_to_minecraft: Discord role gives a group (runs a command)
 */
public final class Sync {

    private final DiscordLink pl;

    Sync(DiscordLink pl) {
        this.pl = pl;
    }

    private Map<String, String> par(String sti) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (!pl.getConfig().getBoolean("sync.enabled", true)) {
            return m;
        }
        for (String s : pl.getConfig().getStringList(sti)) {
            int i = s.lastIndexOf('=');
            if (i > 0) {
                String a = s.substring(0, i).trim();
                String b = s.substring(i + 1).trim();
                if (!a.isEmpty() && !b.isEmpty() && !b.matches("0+")) {
                    m.put(a, b);
                }
            }
        }
        return m;
    }

    /** group or permission = role id */
    Map<String, String> mcTilDc() {
        return par("sync.minecraft_to_discord");
    }

    /** role id = group */
    Map<String, String> dcTilMc() {
        Map<String, String> ud = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : par("sync.discord_to_minecraft").entrySet()) {
            ud.put(e.getKey(), e.getValue());
        }
        return ud;
    }

    private static String perm(String gruppe) {
        return gruppe.contains(".") ? gruppe : "group." + gruppe.toLowerCase();
    }

    /** Only permissions the player really has, not all of them because of OP. */
    static boolean harRigtigt(Player p, String perm) {
        for (PermissionAttachmentInfo i : p.getEffectivePermissions()) {
            if (i.getPermission().equalsIgnoreCase(perm)) {
                return i.getValue();
            }
        }
        return false;
    }

    static Role rolle(Guild g, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return g.getRoleById(id);
        } catch (Exception e) {
            return null;
        }
    }

    void start() {
        long t = Math.max(30, pl.getConfig().getInt("sync.seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            @Override
            public void run() {
                if (!pl.klar()) {
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (pl.links().discordId(p.getUniqueId()) != null) {
                        synk(p.getUniqueId(), p.getName(), p);
                    }
                }
            }
        }, t, t);
    }

    /** Main thread. p can be null when the player is offline (then only the linked role and nickname). */
    void synk(final UUID u, final String navn, Player p) {
        final String id = pl.links().discordId(u);
        final Guild g = pl.guild();
        if (id == null || g == null) {
            return;
        }
        final Map<String, Boolean> skal = new HashMap<String, Boolean>();
        if (p != null) {
            for (Map.Entry<String, String> e : mcTilDc().entrySet()) {
                boolean har = harRigtigt(p, perm(e.getKey()));
                Boolean foer = skal.get(e.getValue());
                skal.put(e.getValue(), (foer != null && foer) || har);
            }
        }
        String lr = pl.getConfig().getString("link.linked_role", "");
        if (lr != null && !lr.trim().isEmpty()) {
            skal.put(lr.trim(), true);
        }
        final Map<String, Boolean> iGruppe = new HashMap<String, Boolean>();
        final Map<String, String> dc = p == null ? new HashMap<String, String>() : dcTilMc();
        if (p != null) {
            for (String gruppe : dc.values()) {
                iGruppe.put(gruppe, harRigtigt(p, perm(gruppe)));
            }
        }
        final String nick = pl.getConfig().getBoolean("link.sync_nickname", false)
                ? pl.getConfig().getString("link.nickname_format", "{player}").replace("{player}", navn) : null;
        g.retrieveMemberById(id).queue(new Consumer<Member>() {
            @Override
            public void accept(Member mb) {
                List<String> til = new ArrayList<String>();
                List<String> fra = new ArrayList<String>();
                for (Map.Entry<String, Boolean> e : skal.entrySet()) {
                    Role r = rolle(g, e.getKey());
                    if (r == null) {
                        continue;
                    }
                    boolean har = mb.getRoles().contains(r);
                    if (e.getValue() && !har) {
                        g.addRoleToMember(mb, r).queue(null, pl.fejl("add role " + r.getName()));
                        til.add(r.getAsMention());
                    } else if (!e.getValue() && har) {
                        g.removeRoleFromMember(mb, r).queue(null, pl.fejl("remove role " + r.getName()));
                        fra.add(r.getAsMention());
                    }
                }
                if (nick != null && !nick.equals(mb.getNickname()) && !mb.isOwner()) {
                    g.modifyNickname(mb, nick.length() > 32 ? nick.substring(0, 32) : nick).queue(null, stille());
                }
                final List<String> kommandoer = new ArrayList<String>();
                List<String> grupper = new ArrayList<String>();
                for (Map.Entry<String, String> e : dc.entrySet()) {
                    Role r = rolle(g, e.getKey());
                    if (r == null) {
                        continue;
                    }
                    boolean harRolle = mb.getRoles().contains(r);
                    Boolean harGruppe = iGruppe.get(e.getValue());
                    if (harRolle && (harGruppe == null || !harGruppe)) {
                        kommandoer.add(pl.getConfig().getString("sync.add_command", "lp user {player} parent add {group}")
                                .replace("{player}", navn).replace("{group}", e.getValue()));
                        grupper.add("✅ " + e.getValue());
                    } else if (!harRolle && harGruppe != null && harGruppe) {
                        kommandoer.add(pl.getConfig().getString("sync.remove_command", "lp user {player} parent remove {group}")
                                .replace("{player}", navn).replace("{group}", e.getValue()));
                        grupper.add("❌ " + e.getValue());
                    }
                }
                if (!kommandoer.isEmpty()) {
                    Bukkit.getScheduler().runTask(pl, new Runnable() {
                        @Override
                        public void run() {
                            for (String c : kommandoer) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                            }
                        }
                    });
                }
                if (pl.getConfig().getBoolean("logs.role_changes", true) && (!til.isEmpty() || !fra.isEmpty() || !grupper.isEmpty())) {
                    StringBuilder b = new StringBuilder("<@" + id + ">");
                    if (!til.isEmpty()) {
                        b.append("\n").append(pl.msg().raw("log_roles_added")).append(" ").append(String.join(", ", til));
                    }
                    if (!fra.isEmpty()) {
                        b.append("\n").append(pl.msg().raw("log_roles_removed")).append(" ").append(String.join(", ", fra));
                    }
                    if (!grupper.isEmpty()) {
                        b.append("\n").append(pl.msg().raw("log_groups")).append(" ").append(String.join(", ", grupper));
                    }
                    pl.log().kort("log_roles", "blue", navn, b.toString());
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                if (t instanceof ErrorResponseException
                        && ((ErrorResponseException) t).getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                        && pl.getConfig().getBoolean("link.unlink_on_leave", false)) {
                    Bukkit.getScheduler().runTask(pl, new Runnable() {
                        @Override
                        public void run() {
                            pl.links().fjernLink(u, "left discord");
                        }
                    });
                }
            }
        });
    }

    /** Main thread. Removes synced roles after an unlink. sidste = no other accounts on this Discord. */
    void fjern(UUID u, String navn, final String id, boolean sidste) {
        if (pl.getConfig().getBoolean("link.unlink_removes_groups", false)) {
            for (String gruppe : dcTilMc().values()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pl.getConfig()
                        .getString("sync.remove_command", "lp user {player} parent remove {group}")
                        .replace("{player}", navn).replace("{group}", gruppe));
            }
        }
        final Guild g = pl.guild();
        if (g == null || !sidste) {
            return;
        }
        final List<String> roller = new ArrayList<String>(mcTilDc().values());
        String lr = pl.getConfig().getString("link.linked_role", "");
        if (lr != null && !lr.trim().isEmpty()) {
            roller.add(lr.trim());
        }
        g.retrieveMemberById(id).queue(new Consumer<Member>() {
            @Override
            public void accept(Member mb) {
                for (String rid : roller) {
                    Role r = rolle(g, rid);
                    if (r != null && mb.getRoles().contains(r)) {
                        g.removeRoleFromMember(mb, r).queue(null, stille());
                    }
                }
            }
        }, stille());
    }

    static Consumer<Throwable> stille() {
        return new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                // member left, missing permission or role above the bot
            }
        };
    }
}
