/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import net.vdm.autosellvdm.mixin.IMixinPlayerTabOverlay;

/**
 * Reads the player's balance WITHOUT sending any command, by scraping what is already on screen —
 * the sidebar scoreboard, the tab-list header/footer, and the player's own tab entry.
 */
public final class BalanceReader
{
    private static final Pattern MONEY_LINE = Pattern.compile(
            "(số ?dư|so ?du|balance|\\bbal\\b|tiền|tien|money|xu|coin|\\$|₫|vnd)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)*)\\s*([kmbt])?", Pattern.CASE_INSENSITIVE);

    private BalanceReader() {}

    /** @return the balance currently visible on screen, or -1 when nothing money-like was found. */
    public static double read(Minecraft mc)
    {
        for (String line : visibleLines(mc))
        {
            if (line == null || line.isBlank() || MONEY_LINE.matcher(line).find() == false)
            {
                continue;
            }
            double value = parseAmount(line);
            if (value >= 0)
            {
                return value;
            }
        }
        return -1.0;
    }

    /** Vai dong dau tien dang thay tren man (sidebar/tab) — cho ban tu-kham. */
    public static List<String> debugLines(Minecraft mc, int max)
    {
        List<String> out = new ArrayList<>();
        for (String line : visibleLines(mc))
        {
            if (line != null && line.isBlank() == false)
            {
                out.add(line);
                if (out.size() >= max) { break; }
            }
        }
        return out;
    }

    private static List<String> visibleLines(Minecraft mc)
    {
        List<String> out = new ArrayList<>();
        if (mc.level == null)
        {
            return out;
        }

        try
        {
            Scoreboard scoreboard = mc.level.getScoreboard();

            collectObjective(scoreboard, scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR), out);

            if (mc.player != null)
            {
                PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
                if (team != null && team.getColor() != null)
                {
                    DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
                    if (slot != null)
                    {
                        collectObjective(scoreboard, scoreboard.getDisplayObjective(slot), out);
                    }
                }
            }

            if (mc.player != null && mc.player.connection != null)
            {
                PlayerInfo self = mc.player.connection.getPlayerInfo(mc.player.getUUID());
                if (self != null && self.getTabListDisplayName() != null)
                {
                    out.add(self.getTabListDisplayName().getString());
                }
            }
            if (mc.gui != null && mc.gui.getTabList() instanceof IMixinPlayerTabOverlay tab)
            {
                addComponent(out, tab.autosellvdm_getHeader());
                addComponent(out, tab.autosellvdm_getFooter());
            }
        }
        catch (Throwable ignored)
        {
        }
        return out;
    }

    private static void collectObjective(Scoreboard scoreboard, Objective objective, List<String> out)
    {
        if (objective == null)
        {
            return;
        }
        addComponent(out, objective.getDisplayName());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective))
        {
            if (entry.isHidden())
            {
                continue;
            }
            Component name = entry.ownerName();
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            String rendered = team != null
                    ? PlayerTeam.formatNameForTeam(team, name).getString()
                    : name.getString();
            out.add(rendered + " " + entry.value());
        }
    }

    private static void addComponent(List<String> out, Component component)
    {
        if (component != null)
        {
            String s = component.getString();
            if (s != null && s.isBlank() == false)
            {
                for (String line : s.split("\n"))
                {
                    out.add(line);
                }
            }
        }
    }

    /** The largest number on the line, honouring K/M/B/T suffixes. -1 when there is none. */
    public static double parseAmount(String text)
    {
        Matcher m = NUMBER.matcher(text);
        double best = -1.0;
        while (m.find())
        {
            try
            {
                double value = Double.parseDouble(m.group(1).replace(",", ""));
                String suffix = m.group(2);
                if (suffix != null)
                {
                    value *= switch (Character.toLowerCase(suffix.charAt(0)))
                    {
                        case 'k' -> 1_000.0;
                        case 'm' -> 1_000_000.0;
                        case 'b' -> 1_000_000_000.0;
                        case 't' -> 1_000_000_000_000.0;
                        default  -> 1.0;
                    };
                }
                if (value > best)
                {
                    best = value;
                }
            }
            catch (NumberFormatException ignored) { }
        }
        return best;
    }
}
