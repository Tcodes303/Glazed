package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AHSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> sellPrice = sgGeneral.add(new StringSetting.Builder()
        .name("sell-price")
        .defaultValue("30k")
        .build()
    );

    private final Setting<Integer> confirmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-delay")
        .defaultValue(10)
        .min(0)
        .max(40)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableFilter = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-item-filter")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> filterItem = sgGeneral.add(new ItemSetting.Builder()
        .name("filter-item")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    private final Setting<Integer> requiredAmount = sgGeneral.add(new IntSetting.Builder()
        .name("required-amount")
        .description("Only sell if slot contains exactly this amount.")
        .defaultValue(2)
        .min(1)
        .max(64)
        .build()
    );

    private final Setting<Boolean> autoSplit = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-split")
        .description("Automatically create required-amount stacks.")
        .defaultValue(true)
        .build()
    );

    private int delayCounter;
    private boolean awaitingConfirmation;
    private int currentSlot;

    public AHSell() {
        super(GlazedAddon.CATEGORY, "ah-sell", "Sells only exact stacks and auto-splits.");
    }

    @Override
    public void onActivate() {
        currentSlot = 0;
        awaitingConfirmation = false;
        attemptSellCurrentSlot();
    }

    @Override
    public void onDeactivate() {
        awaitingConfirmation = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!awaitingConfirmation || mc.player == null) return;

        if (delayCounter-- > 0) return;

        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh instanceof GenericContainerScreenHandler handler && handler.getRows() == 3) {
            ItemStack confirm = handler.getSlot(15).getStack();
            if (!confirm.isEmpty()) {
                mc.interactionManager.clickSlot(
                    handler.syncId, 15, 0, SlotActionType.PICKUP, mc.player
                );
            }
            awaitingConfirmation = false;
            moveToNextSlot();
        }
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        if (event.getMessage().getString().contains("too many listed")) {
            warning("AH limit reached.");
            toggle();
        }
    }

    private void attemptSellCurrentSlot() {
        if (currentSlot > 8) {
            info("Finished hotbar.");
            toggle();
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        ItemStack stack = mc.player.getInventory().getStack(currentSlot);

        if (stack.isEmpty()) {
            moveToNextSlot();
            return;
        }

        if (enableFilter.get() && !stack.isOf(filterItem.get())) {
            moveToNextSlot();
            return;
        }

        // AUTO SPLIT
        if (autoSplit.get() && stack.getCount() > requiredAmount.get()) {
            int empty = findEmptyHotbarSlot();
            if (empty == -1) {
                error("No empty hotbar slot for splitting.");
                toggle();
                return;
            }

            splitStack(currentSlot, empty, requiredAmount.get());
            currentSlot = empty;
            stack = mc.player.getInventory().getStack(currentSlot);
        }

        // ONLY SELL EXACT AMOUNT
        if (stack.getCount() != requiredAmount.get()) {
            moveToNextSlot();
            return;
        }

        mc.getNetworkHandler().sendChatCommand(
            "ah sell " + requiredAmount.get() + " " + sellPrice.get()
        );

        if (notifications.get()) {
            info("Selling %dx item(s) from slot %d", requiredAmount.get(), currentSlot);
        }

        delayCounter = confirmDelay.get();
        awaitingConfirmation = true;
    }

    private void moveToNextSlot() {
        currentSlot++;
        attemptSellCurrentSlot();
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i <= 8; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private void splitStack(int from, int to, int amount) {
        
        mc.interactionManager.clickSlot(0, from + 36, 0, SlotActionType.PICKUP, mc.player);

       
        for (int i = 0; i < amount; i++) {
            mc.interactionManager.clickSlot(0, from + 36, 1, SlotActionType.PICKUP, mc.player);
        }

       
        mc.interactionManager.clickSlot(0, to + 36, 0, SlotActionType.PICKUP, mc.player);
    }
}
