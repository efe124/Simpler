package me.efekos.simpler.commands;

import me.efekos.simpler.commands.syntax.Argument;
import me.efekos.simpler.commands.syntax.ArgumentPriority;
import me.efekos.simpler.exception.InvalidAnnotationException;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public abstract class CoreCommand extends Command {
    protected CoreCommand(@NotNull String name) {
        super(name);
    }

    protected CoreCommand(@NotNull String name, @NotNull String description, @NotNull String usageMessage, @NotNull List<String> aliases) {
        super(name, description, usageMessage, aliases);
    }

    /**
     * @return Command name as string.
     */
    @Override
    @NotNull
    public String getName() {
        me.efekos.simpler.annotations.Command command = this.getClass().getAnnotation(me.efekos.simpler.annotations.Command.class);
        if(command!=null)return command.name();
        return super.getName();
    }

    /**
     * @return Permission this command needs to be executed as String, null if this command does not need any permission.
     */
    @Override
    @Nullable
    public String getPermission() {
        me.efekos.simpler.annotations.Command command = this.getClass().getAnnotation(me.efekos.simpler.annotations.Command.class);
        if(command!=null)return command.permission();
        return super.getPermission();
    }

    /**
     * @return A brief description of this command
     */
    @Override
    @NotNull
    public String getDescription() {
        me.efekos.simpler.annotations.Command command = this.getClass().getAnnotation(me.efekos.simpler.annotations.Command.class);
        if(command!=null)return command.description();
        return super.getDescription();
    }

    /**
     * Gets an example usage of this command
     *
     * @return One or more example usages
     */
    @NotNull
    @Override
    public String getUsage() {
        return "/"+getName()+" <sub> <args>";
    }

    @NotNull
    public abstract ArrayList<Class<? extends SubCommand>> getSubs();

    @Nullable
    public Class<? extends SubCommand> getSub(String name){
        for (Class<? extends SubCommand> sub:getSubs()){
            me.efekos.simpler.annotations.Command annotation = sub.getAnnotation(me.efekos.simpler.annotations.Command.class);
            if(annotation.name().equals(name)){
                return sub;
            }
        }
        return null;
    }

    /**
     * @return Is this command or subs of this command can be used by something that is not player?
     */
    public boolean isPlayerOnly(){
        me.efekos.simpler.annotations.Command command = this.getClass().getAnnotation(me.efekos.simpler.annotations.Command.class);
        if(command!=null)return command.playerOnly();
        else return false;
    }

    public abstract void renderHelpList(CommandSender sender,ArrayList<SubCommand> subInstances);

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if(args.length==0){
            sender.sendMessage(ChatColor.RED+"Invalid Usage. Use "+getUsage());
            return true;
        }
        Class<? extends SubCommand> cmd = getSub(args[0]);
        if(cmd ==null){
            ArrayList<SubCommand> subCommands = new ArrayList<>();
            for(Class<? extends SubCommand> sub:getSubs()){
                try {
                    Constructor<? extends SubCommand> constructor = sub.getConstructor(String.class);
                    me.efekos.simpler.annotations.Command commandA = sub.getAnnotation(me.efekos.simpler.annotations.Command.class);
                    SubCommand command = constructor.newInstance(commandA.name());
                    subCommands.add(command);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            renderHelpList(sender,subCommands);
            return true;
        }
        me.efekos.simpler.annotations.Command cmdA = cmd.getAnnotation(me.efekos.simpler.annotations.Command.class);
        if(cmdA==null) {
            try {
                throw new InvalidAnnotationException(cmd.getName() + " Must have a @Command annotation.");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        String[] subArgs = Arrays.copyOfRange(args,1,args.length);

        try{
            if(sender instanceof Player){ //sender is a player
                me.efekos.simpler.annotations.Command command = this.getClass().getAnnotation(me.efekos.simpler.annotations.Command.class);
                Player p = (Player) sender;

                if(!command.permission().equals("")&&!p.hasPermission(command.permission())){ // @Command has a permission and player don't have the permission

                    p.sendMessage(ChatColor.RED+"You do not have permission to do that!");
                    return true;
                }
                // @Command don't have a permission or player has the permission
                    if(getSub(args[0])!=null){
                        if(!cmdA.permission().equals("")&&!p.hasPermission(cmdA.permission())){ // SubCommand's @Command has a permisison and player don't have the permisson
                            p.sendMessage(ChatColor.RED+"You do not have permission to do that!");
                        } else { // SubCommand's @Command don't have a permission or player has the permisson

                            a:{
                                Constructor<? extends SubCommand> constructor = cmd.getConstructor(String.class);
                                constructor.setAccessible(true);
                                SubCommand instance = constructor.newInstance(cmdA.name());

                                for (int i = 0; i < instance.getSyntax().getArguments().size(); i++) {
                                    Argument arg = instance.getSyntax().getArguments().get(i);
                                    if((subArgs.length-1)<i && arg.getPriority()== ArgumentPriority.REQUIRED){
                                        sender.sendMessage(ChatColor.RED+"Invalid usage. Use " +instance.getUsage());
                                        break a;
                                    }

                                    if(!arg.handleCorrection(subArgs[i])){
                                        sender.sendMessage(ChatColor.RED+"Invalid usage. Use " +instance.getUsage());
                                        break a;
                                    }
                                }

                                instance.onPlayerUse(p,subArgs);
                            }
                        }

                    }

            } else if(sender instanceof ConsoleCommandSender){// sender is not a player but the console
                if(!isPlayerOnly()){ // command is not player only

                    a:{
                        if(cmdA.playerOnly()){ // SubCommand's @Command is player only
                            sender.sendMessage(ChatColor.RED+"This command only can be used by a player!");
                        } else { // SubCommand's @Command is not playeronly
                            Constructor<? extends SubCommand> constructor = cmd.getConstructor(String.class);
                            constructor.setAccessible(true);
                            SubCommand instance = constructor.newInstance(cmdA.name());

                            for (int i = 0; i < instance.getSyntax().getArguments().size(); i++) {
                                Argument arg = instance.getSyntax().getArguments().get(i);
                                if((subArgs.length-1)<i && arg.getPriority()== ArgumentPriority.REQUIRED){
                                    sender.sendMessage(ChatColor.RED+"Invalid usage. Use " +instance.getUsage());
                                    break a;
                                }

                                if(!arg.handleCorrection(subArgs[i])){
                                    sender.sendMessage(ChatColor.RED+"Invalid usage. Use " +instance.getUsage());
                                    break a;
                                }
                            }

                            instance.onConsoleUse((ConsoleCommandSender) sender,subArgs);
                        }
                    }

                } else { // command is player only

                    sender.sendMessage(ChatColor.RED+"This command only can be used by a player!");

                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return true;
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if(sender instanceof Player){
            Player p = (Player) sender;

            if(args.length==1){
                ArrayList<String> cmdNames = new ArrayList<>();
                getSubs().forEach(sub->{
                    try {
                        cmdNames.add(sub.getConstructor(String.class).newInstance(args[0]).getName());
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                });
                return cmdNames;

            } else if (args.length>1){
                Class<? extends SubCommand> sub = getSub(args[0]);
                if(sub==null)return new ArrayList<>();

                try {
                    return sub.getConstructor(String.class).newInstance(args[0]).tabComplete(sender,alias,Arrays.copyOfRange(args,1,args.length));
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        return new ArrayList<>();
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args, @org.jetbrains.annotations.Nullable Location location) throws IllegalArgumentException {
        if(sender instanceof Player){
            Player p = (Player) sender;

            if(args.length==1){
                ArrayList<String> cmdNames = new ArrayList<>();
                getSubs().forEach(sub->{
                    try {
                        cmdNames.add(sub.getConstructor(String.class).newInstance(args[0]).getName());
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                });
                return cmdNames;

            } else if (args.length>1){
                Class<? extends SubCommand> sub = getSub(args[0]);
                if(sub==null)return new ArrayList<>();

                try {
                    return sub.getConstructor(String.class).newInstance(args[0]).tabComplete(sender,alias, Arrays.copyOfRange(args,1,args.length),location);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        return new ArrayList<>();
    }
}
