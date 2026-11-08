package pl.pbackup.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.pbackup.PBackup;
import pl.pbackup.utils.Messages;
import pl.pbackup.utils.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Komenda /pbackup &lt;nick_gracza&gt; - otwiera główne menu GUI. */
public final class PBackupCommand implements CommandExecutor, TabCompleter {

    /** Nick: litery, cyfry, podkreślnik i kropka (prefiks graczy Bedrock). */
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_.]{1,32}$");

    private final PBackup plugin;

    public PBackupCommand(PBackup plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, Messages.ONLY_PLAYERS);
            return true;
        }
        if (!player.hasPermission(Permissions.USE)) {
            msg.send(player, Messages.NO_PERMISSION);
            return true;
        }
        if (args.length != 1) {
            msg.send(player, Messages.USAGE);
            return true;
        }
        String name = args[0];
        if (!NICK.matcher(name).matches()) {
            msg.send(player, Messages.INVALID_NICK);
            return true;
        }

        plugin.getNavigator().resolveTarget(name).whenComplete((target, error) -> {
            if (error != null) {
                plugin.getNavigator().fail(player, error);
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            if (target.isEmpty()) {
                msg.send(player, Messages.PLAYER_NOT_FOUND, name);
                return;
            }
            plugin.getNavigator().openMainMenu(player, target.get());
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission(Permissions.USE)) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(online.getName());
            }
        }
        return result;
    }
}
