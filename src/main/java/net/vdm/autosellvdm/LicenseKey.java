/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Cổng key cho AutoSellVDM — HOÀN TOÀN OFFLINE: key người dùng nhập phải băm ra trùng một trong
 * {@link #KEY_HASHES}. Không gọi mạng, không khoá theo máy, nên không bao giờ có chuyện mất mạng là
 * mod ngừng chạy, và cùng một key vẫn dùng được sau khi cài lại hoặc sang máy khác.
 *
 * <p>Key lưu dạng SHA-256 có salt, không bao giờ để plaintext trong jar. Key đã nhập được ghi vào
 * {@code config/autosellvdm-key.json} nên chỉ phải nhập một lần cho mỗi lần cài. Đây là kiểm tra phía
 * client: nó chặn việc chia sẻ key vô tư, không chặn được người quyết tâm patch bytecode.
 *
 * <p>Danh sách hash do {@code tools/genhash.js} sinh ra từ {@code keys.txt}. Sửa key thì chạy
 * {@code node tools/genhash.js --write} rồi build lại, đừng sửa tay khối dưới đây.
 */
public final class LicenseKey
{
    /**
     * SHA-256(SALT + key), hex chữ thường — mỗi dòng một key hợp lệ.
     * Mảng RỖNG = build không cần key (mở tự do), để một bản build thiếu key không tự khoá chính nó.
     */
    private static final String[] KEY_HASHES = {
        // === BEGIN GENERATED HASHES ===
        "98f379dd5948577149cb93e257189d59e8b039554d0a80c7796e857cabdc9f70",
        "2de517947f3fe79934e83f45f96c1b7ed23257cf6824b67de5bf853e0b7251cb",
        "94f5ebcb68f7ddbf850112ecb4c0fd9827e867cab7b344f72934bdf798ee8cb2",
        "12c33cc9efda2af4d66396c33c1e62a5f575b73d4886967675419c586d5e114d",
        "811c487faeef7a854cb738dfa7c4f4d2e0441f842f9bff4181bfdb6b6bbd3552",
        "bf6ab13decd78253f9095c48c2d27bd80cd20d2f3ccb61c3cd10da05c393f8f9",
        "351e907b44591f71f0467f2280c6c93fd2ef666c4cf7fc0dfc8a1e3ab66fcfbc",
        "64f3db3c60684e00814eafec5dc62afd6add06ca8e0c55d5b65e98c58b4af619",
        "8825ea852f08e977f621eef3c7f9497f60e053945475ba903f665d10a837cc67",
        "79cc3695d1f9663007451a18338d793667299cdcf2810435bf1bc5a0044d67af",
        "cb5f94eab67d8cef13c91f4a502ee8daab819d3a38d2c690b8804f218702ea6f",
        "1dfb3fe1aa29265e2e394e19ad20d41dc15984aa887657e1403175dc929767d0",
        "8f8742a7f991d6f2f01e793fa57708c8057f2b6a7275f342f031c6739c318c4d",
        "c2a8aa52b7326c0808908ae003830fceec141a1ea3d884b0c2bf079f01f5c852",
        "7f63bd0b496233c85091b18aa88378e48e7aa1e080563d01aeebd3a72316179e",
        "3174149878ff7b9afbe5c59fa3bf375cb568b08fcfdb0abe734e82be88b7feea",
        "c930008677a2e5d711c2092e1ffa6c6e2c8298862dc4cc3230ec6668ed580d74",
        "cd627b9bd8f61dfaf44498055952a36ce77c8a29889cb2976236c6dc5a59230a",
        "99832a9f0f2670e459ad05fdab3e15b825d0d7000e35d108d437a7e34b281bc3",
        "18098c045d27f0b1da183dd3f33837ada1e89062524873f61b415662c0f563e1",
        "fd698bc8c64103a08876643b6fc53bfde39303a71ee95147755d033d934c2988",
        "c2447b89a73601e7083253a3c0c8c76a108bac31aaf5fa695f2b6cfcf3cdc489",
        "1dfa8bedf325cdaba90e0178eb31a78dd914122f0188ec67d06588945e7bacbd",
        "82d3ddd7ad3533c36cce782772f4746a888c15b873e19c493205f9ed363fd902",
        "82aff7f30de68f2f142196638bdf6e1c3a4ff5e387325c3733722ca03b2302ea",
        "676d2d187d407e24ac73a6be72246f62769054091f5cd7caf9266f089f76b0d8",
        "4a88380e82db208620c2c1e1c9450041332b0abb3db0323a9cf694656fe481fd",
        "96d3b00c8a2fe7da74b30ee4f5fffc1ceeecf8fd74dd66243bb795204f1fce9b",
        "5326ccdf02fd08a3e8d0556f1d1b564a51623412bd61666fa2e47d2b35eb5526",
        "acf0d5ca35cddd22bb8bb479bc68ba725195bb9862072a01991a4343bb634348",
        "73ba0f7ea7adea2e6ba3b47a5711bf3cca93ac9051e8e3f6137a809bd1cb2123",
        "c5b985412a3fbc1c676895e1648d4f2ff73489de7c8a47e06d074161aa74e31e",
        "674c158b3a67af942c9a8718322bbbfa0e67a8e6e891a21f2094c09a64a3d004",
        "c242bb5285bb95823f9a6b49ac183ff7c5f0bcced3589c20fdbe4f124dadbab2",
        "c4a6b78201ac40da5ffdd034b3d59127ca544b3075b69f09a26505c517005e5d",
        "751f23e2276ab8e9d5ece28494bfadc81c67ca3ba959cd708bd912b3880fa301",
        "47de8cd6d4a2ad60b14d6dffb93e74081cf9a722d6e20aa0211e604edca55275",
        "28e4d008d5424f8740780ffe5627b3340caebff8409fe174566658abe3965f9e",
        "a1d431b2d272ab4842a6bb84328eb8ffbdb8bee33ad3baf6b3c12c05f7bccbb8",
        "d084eea8a6d11f6e88e7b8318c9bd1b6d38d8b06bb77e03f8c060971d9e1cd4f",
        "14cf62c2b6d28fde029acc67665c19499957c1d47c04c6000fae6d76cecf16ab",
        "fb99688eca61cdd82d93fafb77752c90baf370da90ee449ec4a7d887ec0a47bc",
        "64648889dc1de9374ba204a1ec492ae16012a25e3caced95b7e421ef2fc5fd8c",
        "2d076dcb1eb1ece1042172e4065ba1372fbede0e706b10a9f740c5fccc24cef6",
        "2de83807134893e749e90ae9e21b6baf038ab7b7c0d6f63f3e6290365d901750",
        "d5d1f3c8cdd65b5d914b05c9e0bc7f621f5799207605679e7908f9ae9a850f1d",
        "d1ea68a714bf049fc053c4a8088645e6c9080f0a4303682323e6a9bfe526e775",
        "90f4bf2c04ab3ea32717763dae1623c43d6368f75fc7dafbaf57c02c7f183f0e",
        "2f37f47ec93a3bdf5e9ce4568cecac72c4266802e06ce3f3013324fbed1ad99a",
        "d7c869243975e615da417bf0032e04051709708e22a229b98755e6291c91f558",
        "2aa3950a0df1552fdbaefffbcac2f12fabac517228d2c2e821be9f34d1fba5df",
        "db46123d0779d5356564a66b0efb56ee31087a826265bb4a6d83c4e7f42cc1e3",
        "0f54b58c830def92e4fa09b1c07fcd614bb60fa80350b08919314eb1de7a23d8",
        "489b163a0da34a674fce1b0be64ecc1be75c966259863f4f46a1c808f87c845f",
        "813562413ee247fa92a31e33c07a36136cd36c8d7bbdcca338668aef3d6badd8",
        "8a96a28a6474d075466b56fab3ccb130f67bd4657337d4c1f4398e2729179d6d",
        "ad122a43a54ed7034154d0c950c6372bf652a2ec4de0a627860f36093ce68a11",
        "107ce2f68ce9fdc4045e54d7a3adfe14969e07f86facd483494ca8ed39c9c252",
        "fb5cb5b9aea580eeb6df225b83e0a4878f78aeb3ddd0f56b4c5e314ba991076f",
        "597f1c1411fd71adfde3afa33aacd3b24bc1a2288725b30dbea37bfc92fd7185",
        "f46e81f48a9bf3f09f988ba1187aec8cbe2248b524f04cce612e1df69e546583",
        "3c754424ff09c0a55934c2002d3c93853669a0a772ae07c1033e0d6df531bbeb",
        "f4c5b7f66552c993f4ae613a59a25edda22d8b9c8e4d276341b8c3aeccbf9999",
        "9cfb6b045e2970a8e8c58fbef65d204bb20568933ebda818a9547de7dbe6738a",
        "7f42fa206616448599d105d6aea36c278887ee4fc3318efb7ee76e07473bb7a8",
        "f01d5503d3055c5bce0e0af93fe79e1965b5dfb2385700e9919fcefbbc5f1bc1",
        "5f4a422ba269d56aa6f06a98e9d96a20e47499c889cf2ac5e2cd3a4bb82f992b",
        "10999bcc8a9298f84642fd1d73af73debd53bb726867a2ed7b42c847c5ddb1ac",
        "af7b9a71ea326ad786329dc983c3908d2d6e1a578c6ee7041dfaf603a22d8bde",
        "2f97b07085c7237429608ba48ad7651df219f4e496c39a54f5759ff6b09d07a0",
        "e754953a675872417dd71724447990920d02c679a7db21ccaaaed7bc7c03a85b",
        "8cb958a1c4e0f35e7f73c7ceb8966059bdee46d8bb8058834358fceecb202611",
        "56a800e5d7600b49e22462f091b59603cda7c3f0f640db80f53cbf2d4c618e05",
        "83cd096eda8e5d2e39ff073380ea425debe2dfca4c09b3e73ba06a2492ad33f4",
        "a189c12e503bcd12dd2e1b81be6cd00693e5f0ee13f5979aca08971a88d56202",
        "7dcee16d2f8be8e672db6bc2e1443c06fe8d670cc3da449b6d4baf27fe692383",
        "654a3e43465ab5797e3b60a5e5bf569feed045e508e6073675bd615aea4b2ab7",
        "7896e70873df9b73865f647dd5015b14ef0cb67b85a0b3829bb53e0cb5c2615e",
        "5743bdd79f44d1276154843bf3c310a3bd7eec66eb23da48ae14de6f314238db",
        "314c087a9c8bf09af86e011dbd5fec997d1002d31582f7d4c13733fa7aef5e84",
        "db6263b5ba643b32204b590d75ab20466f286f74425cf2084b89a1ba028b7606",
        "9543724b207d57cb12a305eb09fc6186042c10878c7901f3f660bc46df426b5a",
        "c09ffd47862a5c3c398fb0d25548bb4e25456868f7a12cd5eb683092a30c56f3",
        "60bd35e3972588b4127617f40b7cb7acfff778f646c36fd14e3fa33fb8601bac",
        "3eb5e6ffd5cb6d7d890523062b0e6f4d31d0d263a842b491a38038b86af76ed8",
        "1162c957a90d6c826091c297028766f0de22f0965e9686d92b74c745b3bfee25",
        "6b79cfb265134b24104c5e3fc8c7d8e8efd780b6f0334e6c53e6a9ab81b04c1f",
        "e2b3412b5946dc70ce30ea0347dfa4fc2cd4840f8446815fa52bd646b22b6940",
        "90675c6ff1df3484e673fd993338220827cfd55658260447f58f0f2b5bafead5",
        "ee6898d742d20db6d07459093fd55cbfb7cff514a1c814a4848060ab68d61669",
        "0df91ec3eed01081ce51bc8bf5c8294fbb6d734af737d6ea3e2d3cf4304e6d40",
        "2a64ff43122fb4cd80d06c5377e48424cd44112f4bda9e35ea6bd54781d42506",
        "fd457ec681d08d0e5e0db74e836617cb0d81bbfbdb40866205661fdc4191541c",
        "619f87ab865803f5dcbd95aced5b94ffa6f032cceac24c627e1f0089189087c8",
        "32d386630fd7feceaad57e736f84e95bc991cace8f01599363402c9744abe743",
        "376d7324f7e2fe4a9a44e92cd379a389ad8ac6cf8042cd96d33d513d64887c1c",
        "73a658d30bf63cf188c9fe764f1d081f56cd511d43ca7f7737acc14667b431ab",
        "61cd8c890667779a13e5107f22756605bcdae9c0e11c52979d156cf7eac0f0aa",
        "a46052ece5cd63858674846d3459b9132a874ae23ad195f535e50a2cb03c9969",
        "7585b987218f578608fffc670a2f618cff37a1bfda851f93e09085cf66e095f6",
        // === END GENERATED HASHES ===
    };

    /** Phải giống y hệt SALT trong tools/genhash.js. Đổi một bên = mọi key đã bán ngừng hoạt động. */
    private static final String SALT = Obf.dec("lU+MYtUDOROXRJI3gEIuAo4G02LeGA==");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("autosellvdm-key.json");

    private static Data data;
    private static Boolean unlockedCache;

    private LicenseKey() {}

    /** Bản build này có yêu cầu key không? */
    public static boolean isConfigured()
    {
        return KEY_HASHES.length > 0;
    }

    /** True khi phải chặn mod: build có yêu cầu key nhưng máy này chưa nhập key hợp lệ. */
    public static boolean locked()
    {
        return isConfigured() && isUnlocked() == false;
    }

    /**
     * Mod được phép chạy chưa? Key chỉ nhập MỘT LẦN rồi nhớ mãi trong
     * {@code config/autosellvdm-key.json}.
     */
    public static boolean isUnlocked()
    {
        if (isConfigured() == false)
        {
            return true;
        }
        if (unlockedCache == null)
        {
            unlockedCache = matches(load().key);
        }
        return unlockedCache;
    }

    /** Câu giải thích hiện trên màn hình nhập key. */
    public static String status()
    {
        return isUnlocked() ? "§aĐã mở khoá." : "§7Chưa nhập key hợp lệ.";
    }

    /** Kiểm tra key vừa nhập; nếu đúng thì lưu lại để không hỏi lại lần sau. */
    public static boolean tryUnlock(String key)
    {
        if (matches(key) == false)
        {
            return false;
        }
        Data d = load();
        d.key = key == null ? "" : key.trim();
        save(d);
        unlockedCache = Boolean.TRUE;
        return true;
    }

    /** Quên key đã lưu (nút "Xoá key"). */
    public static void clear()
    {
        Data d = load();
        d.key = "";
        save(d);
        unlockedCache = null;
    }

    public static String storedKey()
    {
        String key = load().key;
        return key == null ? "" : key;
    }

    private static boolean matches(String key)
    {
        if (key == null || key.isBlank())
        {
            return false;
        }
        String hash = sha256(SALT + key.trim());
        for (String valid : KEY_HASHES)
        {
            if (valid.equalsIgnoreCase(hash))
            {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String text)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest)
            {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private static Data load()
    {
        if (data == null)
        {
            Data loaded = null;
            if (Files.exists(CONFIG_PATH))
            {
                try
                {
                    loaded = GSON.fromJson(Files.readString(CONFIG_PATH), Data.class);
                }
                catch (Exception ignored) { }
            }
            data = loaded != null ? loaded : new Data();
        }
        return data;
    }

    private static void save(Data d)
    {
        data = d;
        try
        {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(d));
        }
        catch (IOException ignored) { }
    }

    private static final class Data
    {
        String key = "";
    }
}
