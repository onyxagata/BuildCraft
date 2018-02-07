/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import buildcraft.api.items.BCStackHelper;
import buildcraft.api.lists.ListMatchHandler;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemList_BC8;
import buildcraft.core.list.ContainerList.WidgetListSlot;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.button.GuiImageButton;
import buildcraft.lib.gui.button.IButtonBehaviour;
import buildcraft.lib.gui.button.IButtonClickEventListener;
import buildcraft.lib.gui.button.IButtonClickEventTrigger;
import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.list.ListHandler;
import buildcraft.lib.misc.StackUtil;
import com.google.common.collect.Lists;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiList extends GuiBC8<ContainerList> implements IButtonClickEventListener {
    private static final ResourceLocation TEXTURE_BASE =
            new ResourceLocation("buildcraftcore:textures/gui/list_new.png");
    private static final int SIZE_X = 176, SIZE_Y = 191;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_HIGHLIGHT = new GuiIcon(TEXTURE_BASE, 176, 0, 16, 16);
    private static final GuiIcon ICON_ONE_STACK = new GuiIcon(TEXTURE_BASE, 0, 191, 20, 20);
    private static final int BUTTON_COUNT = 3;

    private final Map<Integer, Map<ListMatchHandler.Type, List<ItemStack>>> exampleCache = new HashMap<>();
    private GuiTextField textField;

    public GuiList(EntityPlayer iPlayer) {
        super(new ContainerList(iPlayer));
        xSize = SIZE_X;
        ySize = SIZE_Y;
    }

    @Override
    public void initGui() {
        super.initGui();

        for (int line = 0; line < container.slots.length; line++) {
            WidgetListSlot[] arr = container.slots[line];
            for (int slot = 0; slot < arr.length; slot++) {
                final WidgetListSlot listSlot = arr[slot];
                GuiRectangle rectangle = new GuiRectangle(8 + slot * 18, 32 + line * 34, 16, 16);

                IGuiArea phantomSlotArea = rectangle.offset(mainGui.rootElement);
                mainGui.shownElements.add(listSlot.new GuiElementPhantomSlot(mainGui, phantomSlotArea) {
                    @Override
                    protected boolean shouldDrawHighlight() {
                        return listSlot.slotIndex == 0 || !GuiList.this.container.lines[listSlot.lineIndex].isOneStackMode();
                    }

                    @Override
                    public void drawBackground(float partialTicks) {
                        if (!shouldDrawHighlight()) {
                            ICON_HIGHLIGHT.drawAt(this);
                        }
                    }

                    @Nullable
                    @Override
                    public ItemStack getStack() {
                        if (shouldDrawHighlight()) {
                            return super.getStack();
                        } else {
                            List<ItemStack> data = GuiList.this.getExamplesList(listSlot.lineIndex,
                                    container.lines[listSlot.lineIndex].getSortingType());
                            if (data.size() >= listSlot.slotIndex) {
                                return data.get(listSlot.slotIndex - 1);
                            } else {
                                return null;
                            }
                        }
                    }

                    @Override
                    public void onMouseClicked(int button) {
                        super.onMouseClicked(button);
                        if (contains(gui.mouse)) {
                            clearExamplesCache(listSlot.lineIndex);
                        }
                    }
                });
            }
        }

        buttonList.clear();

        for (int sy = 0; sy < ListHandler.HEIGHT; sy++) {
            int bOff = sy * BUTTON_COUNT;
            int bOffX = this.guiLeft + 8 + ListHandler.WIDTH * 18 - BUTTON_COUNT * 11;
            int bOffY = this.guiTop + 32 + sy * 34 + 18;

            GuiImageButton buttonPrecise =
                    new GuiImageButton(mainGui, bOff + 0, bOffX, bOffY, 11, TEXTURE_BASE, 176, 16, 176, 28);
            buttonPrecise.setToolTip(ToolTip.createLocalized("gui.list.nbt"));
            buttonPrecise.setBehaviour(IButtonBehaviour.TOGGLE);
            mainGui.shownElements.add(buttonPrecise);

            GuiImageButton buttonType =
                    new GuiImageButton(mainGui, bOff + 1, bOffX + 11, bOffY, 11, TEXTURE_BASE, 176, 16, 185, 28);
            buttonType.setToolTip(ToolTip.createLocalized("gui.list.metadata"));
            buttonType.setBehaviour(IButtonBehaviour.TOGGLE);
            mainGui.shownElements.add(buttonType);

            GuiImageButton buttonMaterial =
                    new GuiImageButton(mainGui, bOff + 2, bOffX + 22, bOffY, 11, TEXTURE_BASE, 176, 16, 194, 28);
            buttonMaterial.setToolTip(ToolTip.createLocalized("gui.list.oredict"));
            buttonMaterial.setBehaviour(IButtonBehaviour.TOGGLE);
            mainGui.shownElements.add(buttonMaterial);
        }

        for (IGuiElement elem : mainGui.shownElements) {
            if (elem instanceof GuiImageButton) {
                GuiImageButton b = (GuiImageButton) elem;
                int id = Integer.parseInt(b.id);
                int lineId = id / BUTTON_COUNT;
                int buttonId = id % BUTTON_COUNT;
                if (container.lines[lineId].getOption(buttonId)) {
                    b.activate();
                }

                b.registerListener(this);
            }
        }

        textField = new GuiTextField(6, this.fontRendererObj, guiLeft + 10, guiTop + 10, 156, 12);
        textField.setMaxStringLength(32);
        textField.setText(BCCoreItems.list.getName(container.getListItemStack()));
        textField.setFocused(false);
    }

    @Override
    protected void drawBackgroundLayer(float partialTicks) {
        ICON_GUI.drawAt(mainGui.rootElement);

        for (int i = 0; i < 2; i++) {
            if (container.lines[i].isOneStackMode()) {
                ICON_ONE_STACK.drawAt(guiLeft + 6, guiTop + 30 + i * 34);
            }
        }
    }

    @Override
    protected void drawForegroundLayer() {
        textField.drawTextBox();
    }

    private boolean isCarryingNonEmptyList() {
        ItemStack stack = mc.player.inventory.getItemStack();
        return !BCStackHelper.isEmpty(stack) && stack.getItem() instanceof ItemList_BC8 && stack.getTagCompound() != null;
    }

    private boolean hasListEquipped() {
        return !BCStackHelper.isEmpty(container.getListItemStack());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (textField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            textField.textboxKeyTyped(typedChar, keyCode);
            container.setLabel(textField.getText());
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int b) throws IOException {
        super.mouseClicked(x, y, b);

        if (isCarryingNonEmptyList() || !hasListEquipped()) {
            return;
        }

        textField.mouseClicked(x, y, b);
    }

    @Override
    public void handleButtonClick(IButtonClickEventTrigger sender, int buttonKey) {
        if (!(sender instanceof GuiImageButton)) {
            return;
        }
        int id = Integer.parseInt(((GuiImageButton) sender).id);
        int buttonId = id % BUTTON_COUNT;
        int lineId = id / BUTTON_COUNT;

        container.switchButton(lineId, buttonId);
        clearExamplesCache(lineId);
    }

    private void clearExamplesCache(int lineId) {
        Map<ListMatchHandler.Type, List<ItemStack>> exampleList = exampleCache.get(lineId);
        if (exampleList != null) {
            exampleList.clear();
        }
    }

    private List<ItemStack> getExamplesList(int lineId, ListMatchHandler.Type type) {
        Map<ListMatchHandler.Type, List<ItemStack>> exampleList =
                exampleCache.computeIfAbsent(lineId, k -> new EnumMap<>(ListMatchHandler.Type.class));

        if (!exampleList.containsKey(type)) {
            List<ItemStack> examples = container.lines[lineId].getExamples();
            ItemStack input = container.lines[lineId].stacks.get(0);
            if (!BCStackHelper.isEmpty(input)) {
                List<ItemStack> repetitions = Lists.newArrayList();
                for (ItemStack is : examples) {
                    if (StackUtil.isMatchingItem(input, is, true, false)) {
                        repetitions.add(is);
                    }
                }
                examples.removeAll(repetitions);
            }
            exampleList.put(type, examples);
        }
        return exampleList.get(type);
    }
}
