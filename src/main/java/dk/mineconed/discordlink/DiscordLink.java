package dk.mineconed.discordlink;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Made by Mineconed
 *
 * DiscordLink: link Minecraft and Discord accounts, sync ranks and roles, and log every link.
 */
public final class DiscordLink extends JavaPlugin {

    private Beskeder msg;
    private Links links;
    private Sync sync;
    private LinkLog log;
    private JDA jda;
    private volatile boolean klar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        msg = new Beskeder(this);
        log = new LinkLog(this);
        links = new Links(this);
        links.indlaes();
        sync = new Sync(this);
        Bukkit.getPluginManager().registerEvents(links, this);
        sync.start();
        String token = getConfig().getString("bot.token", "");
        if (token == null || token.trim().isEmpty() || token.contains("PUT_YOUR")) {
            getLogger().severe("No bot token! Put your bot token in plugins/DiscordLink/config.yml and restart.");
        } else {
            startBot(token.trim());
        }
        getLogger().info("DiscordLink is enabled with " + links.antal() + " links. Made by Mineconed.");
    }

    @Override
    public void onDisable() {
        klar = false;
        if (jda != null) {
            jda.shutdown();
        }
    }

    // ═══════════════ GETTERS ═══════════════

    Beskeder msg() {
        return msg;
    }

    Links links() {
        return links;
    }

    Sync sync() {
        return sync;
    }

    LinkLog log() {
        return log;
    }

    boolean klar() {
        return jda != null && klar;
    }

    Guild guild() {
        String gid = getConfig().getString("bot.guild_id", "");
        if (jda == null || gid == null || gid.trim().isEmpty()) {
            return null;
        }
        try {
            return jda.getGuildById(gid.trim());
        } catch (Exception e) {
            return null;
        }
    }

    GuildMessageChannel kanal(String id) {
        if (!klar() || id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return jda.getChannelById(GuildMessageChannel.class, Long.parseLong(id.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    Consumer<Throwable> fejl(final String hvad) {
        return new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                getLogger().warning("Discord (" + hvad + "): " + t.getMessage());
            }
        };
    }

    /** Stops @everyone pings and backtick tricks. */
    static String rens(String s) {
        if (s == null) {
            return "";
        }
        return ChatColor.stripColor(s).replace("@", "@​").replace("`", "'");
    }

    // ═══════════════ BOT ═══════════════

    private void startBot(String token) {
        getLogger().info("Connecting to Discord ...");
        try {
            jda = JDABuilder.createLight(token)
                    .setActivity(Activity.playing(getConfig().getString("bot.activity", "Link with /link")))
                    .addEventListeners(new Lytter())
                    .build();
        } catch (Throwable t) {
            getLogger().severe("The bot could not start: " + t.getMessage());
        }
    }

    private void registrer() {
        List<CommandData> l = new ArrayList<CommandData>();
        l.add(Commands.slash("link", msg.raw("slash_link")).addOption(OptionType.STRING, "code", msg.raw("slash_link_code"), true));
        l.add(Commands.slash("unlink", msg.raw("slash_unlink")));
        l.add(Commands.slash("account", msg.raw("slash_account")));
        SlashCommandData whois = Commands.slash("whois", msg.raw("slash_whois"))
                .addOption(OptionType.USER, "user", msg.raw("slash_whois_user"), false)
                .addOption(OptionType.STRING, "player", msg.raw("slash_whois_player"), false);
        if (getConfig().getBoolean("whois.admin_only", true)) {
            whois.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
        }
        l.add(whois);
        l.add(Commands.slash("linkpanel", msg.raw("slash_panel"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)));
        Guild g = guild();
        if (g != null) {
            g.updateCommands().addCommands(l).queue(null, fejl("register slash commands"));
        } else {
            getLogger().warning("bot.guild_id is not set or wrong. Slash commands are added globally (can take up to 1 hour).");
            jda.updateCommands().addCommands(l).queue(null, fejl("register slash commands"));
        }
    }

    private final class Lytter extends ListenerAdapter {

        @Override
        public void onReady(ReadyEvent e) {
            klar = true;
            getLogger().info("Connected to Discord as " + e.getJDA().getSelfUser().getName() + ".");
            registrer();
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
            String n = e.getName();
            if (n.equals("link")) {
                OptionMapping o = e.getOption("code");
                e.reply(links.brugKode(o == null ? "" : o.getAsString(), e.getUser())).setEphemeral(true).queue();
            } else if (n.equals("unlink")) {
                e.reply(links.dcUnlink(e.getUser().getId())).setEphemeral(true).queue();
            } else if (n.equals("account")) {
                e.replyEmbeds(konto(e.getUser())).setEphemeral(true).queue();
            } else if (n.equals("whois")) {
                OptionMapping u = e.getOption("user");
                OptionMapping p = e.getOption("player");
                e.replyEmbeds(hvem(u == null ? null : u.getAsUser(), p == null ? null : p.getAsString()))
                        .setEphemeral(getConfig().getBoolean("whois.hidden_reply", true)).queue();
            } else if (n.equals("linkpanel")) {
                e.reply(msg.raw("dc_panel_sent")).setEphemeral(true).queue();
                sendPanel(e.getChannel().getId());
            }
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent e) {
            String id = e.getComponentId();
            if (id.equals("dl_link")) {
                TextInput kode = TextInput.create("code", msg.raw("modal_label"), TextInputStyle.SHORT)
                        .setPlaceholder("ABC123").setMinLength(6).setMaxLength(6).setRequired(true).build();
                e.replyModal(Modal.create("dl_modal", msg.raw("modal_title")).addActionRow(kode).build()).queue();
            } else if (id.equals("dl_account")) {
                e.replyEmbeds(konto(e.getUser())).setEphemeral(true).queue();
            } else if (id.equals("dl_help")) {
                e.reply(msg.raw("dc_help", "{minutes}", String.valueOf(links.minutter()))).setEphemeral(true).queue();
            }
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent e) {
            if (!e.getModalId().equals("dl_modal")) {
                return;
            }
            String kode = e.getValue("code") == null ? "" : e.getValue("code").getAsString();
            e.reply(links.brugKode(kode, e.getUser())).setEphemeral(true).queue();
        }
    }

    // ═══════════════ EMBEDS ═══════════════

    private static Color blaa() {
        return new Color(88, 101, 242);
    }

    private net.dv8tion.jda.api.entities.MessageEmbed konto(User u) {
        EmbedBuilder b = new EmbedBuilder().setColor(blaa()).setTimestamp(Instant.now());
        List<UUID> l = links.konti(u.getId());
        if (l.isEmpty()) {
            b.setTitle(msg.raw("dc_account_none_title"));
            b.setDescription(msg.raw("dc_account_none"));
            return b.build();
        }
        b.setTitle(msg.raw("dc_account_title"));
        StringBuilder sb = new StringBuilder();
        for (UUID id : l) {
            sb.append("**").append(rens(links.navn(id))).append("** • <t:").append(links.tid(id) / 1000L).append(":R>\n");
        }
        b.setDescription(sb.toString());
        b.setThumbnail("https://mc\u002Dheads.net/avatar/" + l.get(0) + "/64.png");
        return b.build();
    }

    private net.dv8tion.jda.api.entities.MessageEmbed hvem(User u, String spiller) {
        EmbedBuilder b = new EmbedBuilder().setColor(blaa()).setTimestamp(Instant.now());
        List<UUID> l = new ArrayList<UUID>();
        if (u != null) {
            l.addAll(links.konti(u.getId()));
        } else if (spiller != null) {
            UUID id = links.findNavn(spiller.trim());
            if (id != null) {
                l.add(id);
            }
        }
        if (l.isEmpty()) {
            b.setTitle(msg.raw("dc_whois_none_title"));
            b.setDescription(msg.raw("dc_whois_none"));
            return b.build();
        }
        b.setTitle(msg.raw("dc_whois_title"));
        for (UUID id : l) {
            String dc = links.discordId(id);
            b.addField(rens(links.navn(id)), msg.raw("dc_whois_row", "{discord}", "<@" + dc + ">", "{tag}", rens(links.tag(id)),
                    "{id}", dc, "{uuid}", id.toString(), "{time}", "<t:" + (links.tid(id) / 1000L) + ":f>"), false);
        }
        b.setThumbnail("https://mc\u002Dheads.net/avatar/" + l.get(0) + "/64.png");
        return b.build();
    }

    void sendPanel(String kanalId) {
        GuildMessageChannel k = kanal(kanalId);
        if (k == null) {
            getLogger().warning("Panel channel not found: " + kanalId);
            return;
        }
        EmbedBuilder b = new EmbedBuilder().setColor(blaa())
                .setTitle(msg.raw("panel_title", "{server}", getConfig().getString("server_name", "Minecraft")))
                .setDescription(msg.raw("panel_text", "{minutes}", String.valueOf(links.minutter())))
                .setFooter(msg.raw("panel_footer"));
        k.sendMessageEmbeds(b.build()).setComponents(ActionRow.of(
                Button.success("dl_link", msg.raw("button_link")).withEmoji(Emoji.fromUnicode("🔗")),
                Button.secondary("dl_account", msg.raw("button_account")).withEmoji(Emoji.fromUnicode("👤")),
                Button.secondary("dl_help", msg.raw("button_help")).withEmoji(Emoji.fromUnicode("❓"))
        )).queue(null, fejl("send panel"));
    }

    // ═══════════════ MINECRAFT KOMMANDOER ═══════════════

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(final CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase();
        if (c.equals("link")) {
            if (!(s instanceof Player)) {
                s.sendMessage("Only players can use this.");
                return true;
            }
            if (!s.hasPermission("discordlink.link")) {
                msg.send(s, "no_permission");
                return true;
            }
            if (!klar()) {
                msg.send(s, "bot_offline");
                return true;
            }
            links.mcLink((Player) s);
            return true;
        }
        if (c.equals("unlink")) {
            if (!(s instanceof Player)) {
                s.sendMessage("Only players can use this.");
                return true;
            }
            if (!getConfig().getBoolean("link.allow_unlink", true)) {
                msg.send(s, "unlink_off");
                return true;
            }
            links.mcUnlink(s, ((Player) s).getUniqueId(), null);
            return true;
        }
        if (!s.hasPermission("discordlink.admin")) {
            msg.send(s, "no_permission");
            return true;
        }
        String a1 = a.length > 0 ? a[0].toLowerCase() : "";
        if (a1.equals("reload")) {
            reloadConfig();
            msg.laes();
            if (klar()) {
                registrer();
            }
            msg.send(s, "reloaded");
            return true;
        }
        if (a1.equals("stats")) {
            msg.send(s, "stats", "{amount}", String.valueOf(links.antal()), "{status}", klar() ? msg.c("online") : msg.c("offline"));
            return true;
        }
        if (a1.equals("sync")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                sync.synk(p.getUniqueId(), p.getName(), p);
            }
            msg.send(s, "synced");
            return true;
        }
        if (a1.equals("panel")) {
            String id = a.length > 1 ? a[1] : getConfig().getString("panel.channel_id", "");
            if (id == null || id.isEmpty() || kanal(id) == null) {
                msg.send(s, "panel_no_channel");
                return true;
            }
            sendPanel(id);
            msg.send(s, "panel_sent");
            return true;
        }
        if (a1.equals("history")) {
            List<String> l = log.historik(a.length > 1 ? a[1] : null, 10);
            if (l.isEmpty()) {
                msg.send(s, "history_empty");
                return true;
            }
            s.sendMessage(msg.c("history_header"));
            for (String linje : l) {
                s.sendMessage(ChatColor.GRAY + linje);
            }
            return true;
        }
        if (a1.equals("info") && a.length > 1) {
            UUID u = links.findNavn(a[1]);
            if (u == null) {
                OfflinePlayer o = Bukkit.getOfflinePlayer(a[1]);
                u = links.discordId(o.getUniqueId()) != null ? o.getUniqueId() : null;
            }
            if (u == null) {
                msg.send(s, "admin_not_linked");
                return true;
            }
            for (String linje : msg.linjer("info", "{player}", links.navn(u), "{uuid}", u.toString(), "{discord}", links.tag(u),
                    "{id}", links.discordId(u), "{time}", new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm").format(new java.util.Date(links.tid(u))))) {
                s.sendMessage(linje);
            }
            return true;
        }
        if (a1.equals("forceunlink") && a.length > 1) {
            UUID u = links.findNavn(a[1]);
            if (u == null) {
                msg.send(s, "admin_not_linked");
                return true;
            }
            links.mcUnlink(s, u, s.getName());
            return true;
        }
        if (a1.equals("forcelink") && a.length > 2) {
            if (!klar()) {
                msg.send(s, "bot_offline");
                return true;
            }
            final OfflinePlayer o = Bukkit.getOfflinePlayer(a[1]);
            final String navn = o.getName() == null ? a[1] : o.getName();
            final String admin = s.getName();
            final String dcId = a[2].trim();
            try {
                jda.retrieveUserById(dcId).queue(new Consumer<User>() {
                    @Override
                    public void accept(final User user) {
                        Bukkit.getScheduler().runTask(DiscordLink.this, new Runnable() {
                            @Override
                            public void run() {
                                links.gemLink(o.getUniqueId(), navn, user.getId(), user.getName(), user.getTimeCreated().toEpochSecond(), admin);
                                msg.send(s, "admin_linked", "{player}", navn, "{discord}", user.getName());
                            }
                        });
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable t) {
                        msg.send(s, "user_not_found");
                    }
                });
            } catch (Exception e) {
                msg.send(s, "user_not_found");
            }
            return true;
        }
        for (String linje : msg.linjer("usage", "{label}", label)) {
            s.sendMessage(linje);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> valg = new ArrayList<String>();
        if (cmd.getName().equalsIgnoreCase("discordlink") && s.hasPermission("discordlink.admin")) {
            if (a.length == 1) {
                valg.addAll(Arrays.asList("info", "history", "forcelink", "forceunlink", "sync", "panel", "stats", "reload"));
            } else if (a.length == 2 && !a[0].equalsIgnoreCase("panel")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    valg.add(p.getName());
                }
            }
        }
        List<String> ud = new ArrayList<String>();
        String sidste = a.length == 0 ? "" : a[Math.subtractExact(a.length, 1)].toLowerCase();
        for (String v : valg) {
            if (v.toLowerCase().startsWith(sidste)) {
                ud.add(v);
            }
        }
        return ud;
    }
}
