package com.github.sarhatabaot.commandmanager;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author sarhatabaot
 */
public class CommandManager {
    private Map<String, Method> commands;
    private Map<Method, Object> instances;
    private JavaPlugin plugin;
    private Logger logger;

    private ChatColor usageColor = getDefaultColor();
    private ChatColor descriptionColor = getDefaultColor();

    private String baseCommand;

    public CommandManager(JavaPlugin plugin) {
        commands = new HashMap<>();
        instances = new HashMap<>();
        this.logger = plugin.getLogger();
        this.plugin = plugin;
    }

    public CommandManager(JavaPlugin plugin,String baseCommand, ChatColor usageColor, ChatColor descriptionColor) {
        commands = new HashMap<>();
        instances = new HashMap<>();
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        this.baseCommand = baseCommand;
    }

    public ChatColor getUsageColor() {
        return usageColor;
    }

    public void setUsageColor(ChatColor usageColor) {
        this.usageColor = usageColor;
    }

    public ChatColor getDescriptionColor() {
        return descriptionColor;
    }

    public void setDescriptionColor(ChatColor descriptionColor) {
        this.descriptionColor = descriptionColor;
    }

    private ChatColor getDefaultColor() {
        return ChatColor.WHITE;
    }

    /**
     * Register a command.
     * @param cls   Class of the command
     * @param obj   Instance of the command
     */
    public void register(Class<?> cls, Object obj) {
        for (Method method : cls.getMethods()) {
            if (!method.isAnnotationPresent(Command.class)) {
                continue;
            }

            Command command = method.getAnnotation(Command.class);

            for (String alias : command.aliases()) {
                commands.put(alias, method);
            }
            instances.put(method, obj);
        }
    }

    public void showHelpByPermission(CommandSender sender) {
        List<Method> seenMethods = new LinkedList<>();
        sender.sendMessage("\n"+plugin.getName()+" "+plugin.getDescription().getVersion());
        sender.sendMessage("\n");
        for (Map.Entry<String, Method> entry : commands.entrySet()) {
            if (!seenMethods.contains(entry.getValue())) {
                seenMethods.add(entry.getValue());
                Command command = entry.getValue().getAnnotation(Command.class);
                //Only show help if the sender can use the command anyway
                if ((command.onlyPlayers() && !(sender instanceof Player)) || !checkPermission(command, sender)) {
                    continue;
                }
                sender.sendMessage(usageColor + command.usage() +" "+descriptionColor + command.description());
            }
        }
    }

    private boolean checkPermission(Command command, CommandSender sender) {
        boolean hasPermission = false;
        if (command.permissions().length == 0) {
            hasPermission = true;
        }
        for (String permission : command.permissions()) {
            if (sender.hasPermission(permission)) {
                hasPermission = true;
            }
        }
        return hasPermission;
    }

    public void callCommand(String cmdName, CommandSender sender, String[] args) {
        Method method = commands.get(cmdName.toLowerCase());

        if (method == null) {
            sender.sendMessage("Unknown command");
            showHelpByPermission(sender);
            return;
        }
        //Get annotation
        Command command = method.getAnnotation(Command.class);

        //Validate arguments
        if (!(command.min() <= args.length && (command.max() == -1 || command.max() >= args.length))) {
            sender.sendMessage("invalidArguments");
            sender.sendMessage(baseCommand + command.aliases()[0] + command.usage());
            return;
        }

        //Player or console?
        if (command.onlyPlayers() && !(sender instanceof Player)) {
            sender.sendMessage("Not a player");
            return;
        }

        //Permission checks
        if (!checkPermission(command, sender)) {
            sender.sendMessage("No permission");
            return;
        }

        //Run command
        Object[] methodArgs = {sender, args};
        try {
            method.invoke(instances.get(method), methodArgs);
        } catch (IllegalAccessException e) {
            logger.warning(e.getMessage());
            throw new InvalidMethodsRuntimeException("Invalid methods on command!");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                sender.sendMessage("Invalid arguments");
                sender.sendMessage(baseCommand + command.aliases()[0] + command.usage());
            } else {
                logger.warning(e.getMessage());
                throw new InvalidMethodsRuntimeException("Invalid methods on command!");
            }
        }
    }

    public class InvalidMethodsRuntimeException extends RuntimeException {
        public InvalidMethodsRuntimeException(String message) {
            super(message);
        }
    }
}
