package com.scarsz.discordsrv.util;

import java.util.Set;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import com.scarsz.discordsrv.DiscordSRV;

import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.events.message.MessageReceivedEvent;


public class SingleCommandSender implements ConsoleCommandSender
{
	private ConsoleCommandSender sender;
	private MessageReceivedEvent event;

	public SingleCommandSender(MessageReceivedEvent event, ConsoleCommandSender consoleCommandSender) {
		this.event = event;
		this.sender = consoleCommandSender;
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0)
	{
		return sender.addAttachment(arg0);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, int arg1)
	{
		return sender.addAttachment(arg0, arg1);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, String arg1,
			boolean arg2)
	{
		return sender.addAttachment(arg0, arg1, arg2);
	}

	@Override
	public PermissionAttachment addAttachment(Plugin arg0, String arg1,
			boolean arg2, int arg3)
	{
		return sender.addAttachment(arg0, arg1, arg2, arg3);
	}

	@Override
	public Set<PermissionAttachmentInfo> getEffectivePermissions()
	{
		return sender.getEffectivePermissions();
	}

	@Override
	public boolean hasPermission(String arg0)
	{
		return sender.hasPermission(arg0);
	}

	@Override
	public boolean hasPermission(Permission arg0)
	{
		return sender.hasPermission(arg0);
	}

	@Override
	public boolean isPermissionSet(String arg0)
	{
		return sender.isPermissionSet(arg0);
	}

	@Override
	public boolean isPermissionSet(Permission arg0)
	{
		return sender.isPermissionSet(arg0);
	}

	@Override
	public void recalculatePermissions()
	{
		sender.recalculatePermissions();
	}

	@Override
	public void removeAttachment(PermissionAttachment arg0)
	{
		sender.removeAttachment(arg0);
	}

	@Override
	public boolean isOp()
	{
		return sender.isOp();
	}

	@Override
	public void setOp(boolean arg0)
	{
		sender.setOp(arg0);
	}

	@Override
	public String getName()
	{
		return sender.getName();
	}

	@Override
	public Server getServer()
	{
		return sender.getServer();
	}

	@Override
	public void sendMessage(String arg0)
	{
		TextChannel channel = (TextChannel) event.getChannel();
		DiscordSRV.sendMessage(channel, arg0, false);
	}

	@Override
	public void sendMessage(String [] arg0)
	{
		for (String msg : arg0)
			sendMessage(msg);
	}

	@Override
	public void abandonConversation(Conversation arg0)
	{
		sender.abandonConversation(arg0);
	}

	@Override
	public void abandonConversation(Conversation arg0,
			ConversationAbandonedEvent arg1)
	{
		sender.abandonConversation(arg0, arg1);
	}

	@Override
	public void acceptConversationInput(String arg0)
	{
		sender.acceptConversationInput(arg0);
	}

	@Override
	public boolean beginConversation(Conversation arg0)
	{
		return sender.beginConversation(arg0);
	}

	@Override
	public boolean isConversing()
	{
		return sender.isConversing();
	}

	@Override
	public void sendRawMessage(String arg0)
	{
		sender.sendRawMessage(arg0);
	}
}