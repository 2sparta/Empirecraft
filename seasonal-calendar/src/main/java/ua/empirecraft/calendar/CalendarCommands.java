package ua.empirecraft.calendar;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** The actual Brigadier tree is also exercised without creating a Minecraft world. */
public final class CalendarCommands {
    private CalendarCommands() {}
    @FunctionalInterface public interface Editor<S> {
        int edit(S source, CalendarEdit edit) throws CommandSyntaxException;
    }
    private static <S> LiteralArgumentBuilder<S> literal(String name) { return LiteralArgumentBuilder.literal(name); }
    private static <S> RequiredArgumentBuilder<S, Integer> number(String name, int min, int max) {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer(min, max));
    }
    private static <S> RequiredArgumentBuilder<S, String> time() {
        // Use a vanilla wire type: clients must deserialize this tree before they can join.
        // A greedy string also preserves the colon without requiring quotes around HH:mm.
        return RequiredArgumentBuilder.argument("time", StringArgumentType.greedyString());
    }
    private static <S> int timeValue(CommandContext<S> context) throws CommandSyntaxException {
        var reader = new StringReader(StringArgumentType.getString(context, "time"));
        int ticks = new TimeArgument().parse(reader);
        if (reader.canRead()) throw TimeArgument.INVALID.createWithContext(reader);
        return ticks;
    }
    public static <S> void register(CommandDispatcher<S> dispatcher, Predicate<S> admin, Command<S> show, Editor<S> edit) {
        var set = CalendarCommands.<S>literal("set").requires(admin)
            .then(CalendarCommands.<S>literal("day").then(CalendarCommands.<S>number("day", 1, 30)
                .executes(c -> edit.edit(c.getSource(), new CalendarEdit(IntegerArgumentType.getInteger(c, "day"), null, null, null)))))
            .then(CalendarCommands.<S>literal("month").then(CalendarCommands.<S>number("month", 1, 12)
                .executes(c -> edit.edit(c.getSource(), new CalendarEdit(null, IntegerArgumentType.getInteger(c, "month"), null, null)))))
            .then(CalendarCommands.<S>literal("year").then(CalendarCommands.<S>number("year", 1, Integer.MAX_VALUE)
                .executes(c -> edit.edit(c.getSource(), new CalendarEdit(null, null, (long)IntegerArgumentType.getInteger(c, "year"), null)))))
            .then(CalendarCommands.<S>literal("time").then(CalendarCommands.<S>time()
                .executes(c -> edit.edit(c.getSource(), new CalendarEdit(null, null, null, timeValue(c))))))
            .then(CalendarCommands.<S>literal("date").then(CalendarCommands.<S>number("day", 1, 30)
                .then(CalendarCommands.<S>number("month", 1, 12).then(CalendarCommands.<S>number("year", 1, Integer.MAX_VALUE)
                    .executes(c -> edit.edit(c.getSource(), new CalendarEdit(IntegerArgumentType.getInteger(c, "day"),
                        IntegerArgumentType.getInteger(c, "month"), (long)IntegerArgumentType.getInteger(c, "year"), null)))
                    .then(CalendarCommands.<S>time().executes(c -> edit.edit(c.getSource(), new CalendarEdit(
                        IntegerArgumentType.getInteger(c, "day"), IntegerArgumentType.getInteger(c, "month"),
                        (long)IntegerArgumentType.getInteger(c, "year"), timeValue(c)))))))));
        var node = dispatcher.register(CalendarCommands.<S>literal("calendar").executes(show).then(set));
        dispatcher.register(CalendarCommands.<S>literal("календар").executes(show).redirect(node));
    }
    public static final class TimeArgument implements ArgumentType<Integer> {
        private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(
            new LiteralMessage("Use HH:mm (00:00-23:59) or Minecraft ticks (0-23999)"));
        @Override public Integer parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
            String value = reader.getString().substring(start, reader.getCursor());
            if (value.matches("[0-9]{1,2}:[0-9]{2}")) {
                String[] parts = value.split(":");
                int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
                if (hour < 24 && minute < 60) {
                    // Round up so e.g. 00:01 displays as 00:01, not 00:00 after integer tick conversion.
                    long civilTicks = ((hour * 60L + minute) * 24_000 + 1439) / 1440;
                    return (int)Math.floorMod(civilTicks - 6000, 24_000);
                }
            } else if (value.matches("[0-9]{1,5}")) {
                int ticks = Integer.parseInt(value);
                if (ticks < 24_000) return ticks;
            }
            reader.setCursor(start);
            throw INVALID.createWithContext(reader);
        }
        @Override public Collection<String> getExamples() { return List.of("06:00", "12:30", "18000"); }
    }
}
