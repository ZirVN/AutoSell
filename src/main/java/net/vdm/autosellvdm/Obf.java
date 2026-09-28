/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Tiny string hider: the license SALT is stored XOR+Base64-encoded so a plain decompile /
 * {@code strings} pass doesn't reveal it in cleartext. Not real encryption — it just raises the bar
 * past casual reading (Java bytecode is always decompilable).
 *
 * <p>The key below MUST stay byte-for-byte identical to {@code K} in {@code tools/genhash.js}, which
 * is what produces the Base64 string pasted into {@link LicenseKey}. Change one, change both.
 */
final class Obf
{
    private static final byte[] K = {
        (byte) 0xe3, 0x2b, (byte) 0xe1, 0x52, (byte) 0xec, 0x2e, 0x58, 0x66,
    };

    private Obf() {}

    static String dec(String b64)
    {
        if (b64 == null || b64.isEmpty())
        {
            return "";
        }
        try
        {
            byte[] b = Base64.getDecoder().decode(b64);
            byte[] o = new byte[b.length];
            for (int i = 0; i < b.length; i++)
            {
                o[i] = (byte) (b[i] ^ K[i % K.length]);
            }
            return new String(o, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
