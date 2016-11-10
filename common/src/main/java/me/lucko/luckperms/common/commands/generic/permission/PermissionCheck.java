/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.generic.permission;

import me.lucko.luckperms.common.LuckPermsPlugin;
import me.lucko.luckperms.common.commands.*;
import me.lucko.luckperms.common.commands.generic.SharedSubCommand;
import me.lucko.luckperms.common.constants.Permission;
import me.lucko.luckperms.common.core.NodeBuilder;
import me.lucko.luckperms.common.core.PermissionHolder;
import me.lucko.luckperms.common.utils.Predicates;

import java.util.List;

public class PermissionCheck extends SharedSubCommand {
    public PermissionCheck() {
        super("check", "Checks to see if the object has a certain permission node", Permission.USER_PERM_CHECK,
                Permission.GROUP_PERM_CHECK, Predicates.notInRange(1, 3),
                Arg.list(
                        Arg.create("node", true, "the permission node to check for"),
                        Arg.create("server", false, "the server to check on"),
                        Arg.create("world", false, "the world to check on")
                )
        );
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, PermissionHolder holder, List<String> args) throws CommandException {
        String node = ArgumentUtils.handleNodeWithoutCheck(0, args);
        String server = ArgumentUtils.handleServer(1, args);
        String world = ArgumentUtils.handleWorld(2, args);

        switch (ContextHelper.determine(server, world)) {
            case NONE:
                Util.sendTristate(sender, node, holder.hasPermission(new NodeBuilder(node).build()));
                break;
            case SERVER:
                Util.sendTristate(sender, node, holder.hasPermission(new NodeBuilder(node).setServer(server).build()));
                break;
            case SERVER_AND_WORLD:
                Util.sendTristate(sender, node, holder.hasPermission(new NodeBuilder(node).setServer(server).setWorld(world).build()));
                break;
        }

        return CommandResult.SUCCESS;
    }
}
