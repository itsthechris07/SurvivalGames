package com.christian34.survivalgames.commands.arguments;

import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.jetbrains.annotations.NotNull;

/**
 * An invalid argument: the command handler shows the message of messages.yml to the sender.
 *
 * @author Christian34
 */
public class MessageException extends IllegalArgumentException implements ComponentMessageThrowable {
    private final transient Component message;

    public MessageException(LangText langText, Object... arguments) {
        this(I.i18n(langText, arguments));
    }

    private MessageException(Component message) {
        super(I.plain(message));
        this.message = message;
    }

    @Override
    public @NotNull Component componentMessage() {
        return message;
    }

}
