/*
 * © 2026 vuducmanh09. Standalone AutoSellVDM mod.
 */
package net.vdm.autosellvdm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bộ chọn vật phẩm bán (tách ra từ màn Auto Bán cũ khi màn chính chuyển sang ClickGUI panel):
 * danh sách mọi item có ô tìm + nút Thêm/Đã thêm, cột phải liệt kê những món whitelist/blacklist
 * đang áp dụng với nút §cX§r để bỏ.
 */
public class GuiItemsVdm extends Screen implements VdmStyledScreen
{
    private final Screen parent;
    private EditBox searchField;
    private ItemList list;

    private final List<Item> allItems = new ArrayList<>();

    private static final int PANEL_W = 230;
    private static final int ROW_H = 18;
    private static final int LIST_TOP = 68;

    public GuiItemsVdm(Screen parent)
    {
        super(Component.literal("Chọn Vật Phẩm Bán"));
        this.parent = parent;

        for (Item item : BuiltInRegistries.ITEM)
        {
            if (item != Items.AIR)
            {
                this.allItems.add(item);
            }
        }
        this.allItems.sort(Comparator.comparing(it -> new ItemStack(it).getHoverName().getString()));
    }

    private boolean showPanel()
    {
        return this.width >= 480;
    }

    private int listWidth()
    {
        int max = this.showPanel() ? this.width - PANEL_W - 16 : this.width - 8;
        return Math.min(380, Math.max(220, max));
    }

    /** Mép trái của khối nội dung — CANH GIỮA cả cụm (danh sách + cột phải) thay vì dồn hết sang trái. */
    private int startX()
    {
        int total = this.listWidth() + (this.showPanel() ? 10 + PANEL_W : 0);
        return Math.max(8, (this.width - total) / 2);
    }

    private int panelX()
    {
        return Math.min(this.startX() + this.listWidth() + 10, this.width - PANEL_W);
    }

    @Override
    protected void init()
    {
        int listWidth = this.listWidth();
        int startX = this.startX();

        this.searchField = new EditBox(this.font, startX + 1, 40, listWidth - 160, 20,
                Component.literal("Tìm vật phẩm..."));
        this.searchField.setHint(Component.literal("Tìm vật phẩm..."));
        this.searchField.setResponder(q -> this.list.refresh(q));
        this.addRenderableWidget(this.searchField);

        this.addRenderableWidget(Button.builder(this.modeLabel(), b ->
        {
            AutoSellVdmConfig cfg = AutoSellVdmConfig.get();
            cfg.autoSellWhitelist = cfg.autoSellWhitelist == false;
            cfg.save();
            b.setMessage(this.modeLabel());
        }).bounds(startX + listWidth - 156, 40, 86, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§cXóa hết"), b ->
        {
            AutoSell.clearListed();
            this.list.refresh(this.searchField.getValue());
        }).bounds(startX + listWidth - 66, 40, 66, 20).build());

        this.list = new ItemList(this.minecraft, listWidth, this.height - LIST_TOP - 36, LIST_TOP, 24);
        this.list.setX(startX);
        this.addRenderableWidget(this.list);
        this.list.refresh(this.searchField.getValue());

        this.addRenderableWidget(Button.builder(Component.literal("Đóng"), b -> this.onClose())
                .bounds(startX + (listWidth - 100) / 2, this.height - 28, 100, 20).build());
    }

    private Component modeLabel()
    {
        // Chỉ tên chế độ — "Chế độ: Whitelist" rộng ~100px, tràn nút 86px;
        // cột phải đã có tiêu đề "Whitelist (n)" nói rõ nghĩa rồi.
        return Component.literal(AutoSellVdmConfig.get().autoSellWhitelist ? "§bWhitelist" : "§bBlacklist");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        VdmTheme.backdrop(this, g);
        VdmTheme.group(g, this.startX() - 4, 32, this.listWidth() + 8, this.height - 66);

        if (this.showPanel())
        {
            VdmTheme.group(g, this.panelX() - 6, 32, PANEL_W + 2, this.height - 66);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta)
    {
        super.render(g, mouseX, mouseY, delta);
        VdmTheme.title(this, g, this.font, this.title.getString(), 8);

        if (this.showPanel() == false)
        {
            return;
        }

        List<Item> items = listedSorted();
        int px = this.panelX();
        int xBtnX = px + PANEL_W - 20;
        int maxVisible = Math.max(1, (this.height - LIST_TOP - 40) / ROW_H);

        String header = (AutoSellVdmConfig.get().autoSellWhitelist ? "Whitelist" : "Blacklist")
                + " (" + items.size() + ")";
        g.drawString(this.font, "§f" + header, px, LIST_TOP - 16, 0xFFFFFFFF);
        g.fill(px, LIST_TOP - 4, px + PANEL_W - 10, LIST_TOP - 3, 0xFF555555);

        if (items.isEmpty())
        {
            g.drawString(this.font, "§7(chưa chọn món nào)", px, LIST_TOP + 4, 0xFF888888);
        }

        for (int i = 0; i < items.size() && i < maxVisible; i++)
        {
            int y = LIST_TOP + i * ROW_H;
            Item item = items.get(i);
            g.renderItem(new ItemStack(item), px, y);

            String name = new ItemStack(item).getHoverName().getString();
            name = this.font.plainSubstrByWidth(name, xBtnX - (px + 20) - 6);
            g.drawString(this.font, name, px + 20, y + 4, 0xFFFFFFFF);

            boolean hov = mouseX >= xBtnX - 2 && mouseX <= xBtnX + 10 && mouseY >= y && mouseY <= y + 14;
            g.drawString(this.font, "X", xBtnX, y + 4, hov ? 0xFFFF3333 : 0xFFAAAAAA);
        }
        if (items.size() > maxVisible)
        {
            g.drawString(this.font, "§7+ " + (items.size() - maxVisible) + " nữa…", px,
                    LIST_TOP + maxVisible * ROW_H, 0xFF888888);
        }
    }

    private static List<Item> listedSorted()
    {
        List<Item> items = AutoSell.listedItems();
        items.sort(Comparator.comparing(it -> new ItemStack(it).getHoverName().getString()));
        return items;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (this.showPanel())
        {
            List<Item> items = listedSorted();
            int xBtnX = this.panelX() + PANEL_W - 20;
            int maxVisible = Math.max(1, (this.height - LIST_TOP - 40) / ROW_H);

            for (int i = 0; i < items.size() && i < maxVisible; i++)
            {
                int y = LIST_TOP + i * ROW_H;
                if (event.x() >= xBtnX - 2 && event.x() <= xBtnX + 10 && event.y() >= y && event.y() <= y + 14)
                {
                    AutoSell.setListed(items.get(i), false);
                    this.list.refresh(this.searchField.getValue());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }

    private class ItemList extends ContainerObjectSelectionList<ItemList.Entry>
    {
        private final int rowWidth;

        ItemList(Minecraft mc, int width, int height, int top, int itemHeight)
        {
            super(mc, width, height, top, itemHeight);
            this.rowWidth = Math.min(340, width - 12);
        }

        void refresh(String query)
        {
            this.clearEntries();
            String q = query.toLowerCase(Locale.ROOT).trim();

            for (Item item : GuiItemsVdm.this.allItems)
            {
                if (q.isEmpty())
                {
                    this.addEntry(new Entry(item));
                    continue;
                }
                String name = new ItemStack(item).getHoverName().getString();
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (name.toLowerCase(Locale.ROOT).contains(q) || id.toString().contains(q))
                {
                    this.addEntry(new Entry(item));
                }
            }
            this.setScrollAmount(0.0);
        }

        @Override
        public int getRowWidth()
        {
            return this.rowWidth;
        }

        private class Entry extends ContainerObjectSelectionList.Entry<Entry>
        {
            private final Item item;
            private final Button add;

            Entry(Item item)
            {
                this.item = item;
                this.add = Button.builder(this.addLabel(), b ->
                {
                    AutoSell.setListed(this.item, AutoSell.isListed(this.item) == false);
                    b.setMessage(this.addLabel());
                }).bounds(0, 0, 70, 20).build();
            }

            private Component addLabel()
            {
                return Component.literal(AutoSell.isListed(this.item) ? "§aĐã thêm" : "§fThêm");
            }

            @Override
            public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float delta)
            {
                int left = this.getContentX();
                int top = this.getContentY();
                int width = this.getContentWidth();

                g.renderItem(new ItemStack(this.item), left + 4, top + 2);
                String name = new ItemStack(this.item).getHoverName().getString();
                name = GuiItemsVdm.this.font.plainSubstrByWidth(name, width - 110);
                g.drawString(GuiItemsVdm.this.font, name, left + 26, top + 6, 0xFFFFFFFF);

                this.add.setMessage(this.addLabel());
                this.add.setX(left + width - 74);
                this.add.setY(top + 1);
                this.add.render(g, mouseX, mouseY, delta);
            }

            @Override
            public List<? extends GuiEventListener> children()
            {
                return Collections.singletonList(this.add);
            }

            @Override
            public List<? extends NarratableEntry> narratables()
            {
                return Collections.singletonList(this.add);
            }
        }
    }
}
