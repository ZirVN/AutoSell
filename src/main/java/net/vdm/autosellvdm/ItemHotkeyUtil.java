/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

/** Tiny helper: a hotkey is a GLFW key code (-1 = unset). {@link #name} labels it; {@link #isDown} polls it. */
public final class ItemHotkeyUtil
{
    public static final int NONE = -1;

    private ItemHotkeyUtil() {}

    public static String name(int code)
    {
        if (code < 0)
        {
            return "—";
        }
        try
        {
            return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        }
        catch (Exception e)
        {
            return "?";
        }
    }

    public static boolean isDown(int code)
    {
        if (code < 0)
        {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null)
        {
            return false;
        }
        return GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
    }
}
