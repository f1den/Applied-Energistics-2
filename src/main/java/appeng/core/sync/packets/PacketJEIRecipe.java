/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync.packets;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperInvItemHandler;
import appeng.util.item.AEItemStack;
import appeng.util.prioritylist.IPartitionList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static appeng.helpers.ItemStackHelper.stackFromNBT;


public class PacketJEIRecipe extends AppEngPacket {

    static ItemStack[] emptyArray = {ItemStack.EMPTY};
    private List<ItemStack[]> recipe;
    private List<ItemStack> output;
    private boolean shouldCondense;


    // automatic.
    public PacketJEIRecipe(final ByteBuf stream) throws IOException {
        final ByteArrayInputStream bytes = this.getPacketByteArray(stream);
        bytes.skip(stream.readerIndex());
        final NBTTagCompound comp = CompressedStreamTools.readCompressed(bytes);
        if (comp != null) {
            this.shouldCondense = comp.getBoolean("condense");
            this.recipe = new ArrayList<>();

            for (int x = 0; x < comp.getKeySet().size(); x++) {
                if (comp.hasKey("#" + x)) {
                    final NBTTagList list = comp.getTagList("#" + x, 10);
                    if (list.tagCount() > 0) {
                        this.recipe.add(new ItemStack[list.tagCount()]);
                        for (int y = 0; y < list.tagCount(); y++) {
                            this.recipe.get(x)[y] = stackFromNBT(list.getCompoundTagAt(y));
                        }
                    } else {
                        this.recipe.add(emptyArray);
                    }
                }
            }

            if (comp.hasKey("outputs")) {
                final NBTTagList outputList = comp.getTagList("outputs", 10);
                this.output = new ArrayList<>();
                for (int z = 0; z < outputList.tagCount(); z++) {
                    this.output.add(stackFromNBT(outputList.getCompoundTagAt(z)));
                }
            }
        }
    }

    // api
    public PacketJEIRecipe(final NBTTagCompound recipe) throws IOException {
        final ByteBuf data = Unpooled.buffer();

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream outputStream = new DataOutputStream(bytes);

        data.writeInt(this.getPacketID());

        CompressedStreamTools.writeCompressed(recipe, outputStream);
        data.writeBytes(bytes.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        // oops :)
        if (this.recipe == null) return;

        final EntityPlayerMP pmp = (EntityPlayerMP) player;
        final Container con = pmp.openContainer;
        if (!(con instanceof IContainerCraftingPacket cct)) return;

        final IGridNode node = cct.getNetworkNode();
        if (node == null) return;

        final IGrid grid = node.getGrid();
        if (grid == null) return;

        final IStorageGrid inv = grid.getCache(IStorageGrid.class);
        if (inv == null) return;
        final IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
        if (energy == null) return;

        final boolean hasExtractPermissions;
        final boolean hasInjectPermissions;
        if (grid.getCache(ISecurityGrid.class) instanceof ISecurityGrid security) {
            hasInjectPermissions = security.hasPermission(player, SecurityPermissions.INJECT);
            hasExtractPermissions = security.hasPermission(player, SecurityPermissions.EXTRACT);
        } else {
            hasInjectPermissions = false;
            hasExtractPermissions = false;
        }

        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        final IItemHandler craftMatrix = cct.getInventoryByName("crafting");
        final IItemHandler playerInventory = cct.getInventoryByName("player");

        final Object2LongLinkedOpenHashMap<IAEItemStack> condensedBuffer;
        if (this.shouldCondense) {
            condensedBuffer = new Object2LongLinkedOpenHashMap<>();
        } else {
            condensedBuffer = null;
        }

        final IMEMonitor<IAEItemStack> storage = inv.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        final IPartitionList<IAEItemStack> filter = ItemViewCell.createFilter(cct.getViewCells());

        // First iteration, find what can be used and send everything else into the network.
        for (int matrixSlotIndex = 0; matrixSlotIndex < craftMatrix.getSlots(); matrixSlotIndex++) {
            var currentItem = craftMatrix.getStackInSlot(matrixSlotIndex);
            if (!currentItem.isEmpty()) {
                if (this.canUseInSlot(matrixSlotIndex, currentItem)) continue;

                if (!cct.useRealItems()) {
                    currentItem.setCount(0);
                } else if (hasInjectPermissions) {
                    final var out = Platform.poweredInsert(energy, storage,
                            AEItemStack.fromItemStack(currentItem), cct.getActionSource());

                    currentItem.setCount(out != null ? (int) out.getStackSize() : 0);
                }
            }
        }

        // Second iteration, query AE2 & player inventory for items.
        for (int recipeSlotIndex = 0; recipeSlotIndex < recipe.size(); recipeSlotIndex++) {
            ItemStack currentItem;

            if (recipeSlotIndex < craftMatrix.getSlots()) {
                currentItem = craftMatrix.getStackInSlot(recipeSlotIndex);
            } else if (this.shouldCondense) {
                // If the inputs should be condensed, we can read past the current grid.
                currentItem = ItemStack.EMPTY;
            } else {
                // Otherwise break.
                break;
            }

            if (currentItem.isEmpty() && recipe.get(recipeSlotIndex) != null) {
                // for each variant
                for (int y = 0; y < this.recipe.get(recipeSlotIndex).length && currentItem.isEmpty(); y++) {
                    var recipeStackVariant = this.recipe.get(recipeSlotIndex)[y];
                    final IAEItemStack request = AEItemStack.fromItemStack(recipeStackVariant);
                    if (request != null) {
                        // try ae
                        if ((filter == null || filter.isListed(request)) && hasExtractPermissions) {
                            request.setStackSize(1);
                            IAEItemStack out;

                            if (cct.useRealItems()) {
                                out = Platform.poweredExtraction(energy, storage, request, cct.getActionSource());
                                if (out == null) {
                                    if (request.getItem().isDamageable() || Platform.isGTDamageableItem(request.getItem())) {
                                        Collection<IAEItemStack> outList = inv.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)).getStorageList().findFuzzy(request, FuzzyMode.IGNORE_ALL);
                                        for (IAEItemStack is : outList) {
                                            if (is.getStackSize() == 0) {
                                                continue;
                                            }
                                            if (Platform.isGTDamageableItem(request.getItem())) {
                                                if (!(is.getDefinition().getMetadata() == request.getDefinition().getMetadata())) {
                                                    continue;
                                                }
                                            }
                                            out = Platform.poweredExtraction(energy, storage, is.copy().setStackSize(1), cct.getActionSource());
                                            if (out != null) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Query the crafting grid if there is a pattern providing the item
                                if (!crafting.getCraftingFor(request, null, 0, null).isEmpty()) {
                                    out = request;
                                } else {
                                    // Fall back using an existing item
                                    out = storage.extractItems(request, Actionable.SIMULATE, cct.getActionSource());
                                }
                            }

                            if (out != null) {
                                if (!cct.useRealItems()) {
                                    out.setStackSize(recipeStackVariant.getCount());
                                }
                                currentItem = out.createItemStack();
                            }
                        }

                        // try inventory
                        if (currentItem.isEmpty()) {
                            AdaptorItemHandler ad = new AdaptorItemHandler(playerInventory);

                            if (cct.useRealItems()) {
                                currentItem = ad.removeSimilarItems(1, recipeStackVariant, FuzzyMode.IGNORE_ALL, null);
                            } else {
                                currentItem = ad.simulateSimilarRemove(recipeStackVariant.getCount(), recipeStackVariant, FuzzyMode.IGNORE_ALL, null);
                            }
                        }
                    }
                }

                if (!cct.useRealItems()) {
                    if (currentItem.isEmpty() && recipe.size() > recipeSlotIndex && this.recipe.get(recipeSlotIndex) != null) {
                        currentItem = this.recipe.get(recipeSlotIndex)[0].copy();
                    }
                }
            }

            if (condensedBuffer != null) {
                var aeItemStack = AEItemStack.fromItemStack(currentItem);
                if (aeItemStack != null) {
                    condensedBuffer.compute(aeItemStack, (k, v) -> (v == null ? 0 : v) + k.getStackSize());
                }
            } else {
                ItemHandlerUtil.setStackInSlot(craftMatrix, recipeSlotIndex, currentItem);
            }
        }

        if (condensedBuffer != null) {
            var slotIndex = 0;

            // Fill the craft matrix with items from the condensed buffer.
            for (var entry : condensedBuffer.entrySet()) {
                if (slotIndex >= craftMatrix.getSlots()) break;

                ItemHandlerUtil.setStackInSlot(craftMatrix, slotIndex,
                        entry.getKey().copy().setStackSize(entry.getValue()).createItemStack());
                slotIndex++;
            }

            // Clear the remaining slots.
            for (var i = slotIndex; i < craftMatrix.getSlots(); i++) {
                ItemHandlerUtil.setStackInSlot(craftMatrix, i, ItemStack.EMPTY);
            }
        }

        con.onCraftMatrixChanged(new WrapperInvItemHandler(craftMatrix));

        if (this.output != null && con instanceof ContainerPatternEncoder encoder && !encoder.isCraftingMode()) {
            var outputSlots = cct.getInventoryByName("output");
            for (int i = 0; i < outputSlots.getSlots(); ++i) {
                if (i < this.output.size()) {
                    var outputStack = this.output.get(i);
                    if (outputStack != null && outputStack != ItemStack.EMPTY) {
                        ItemHandlerUtil.setStackInSlot(outputSlots, i, outputStack);
                        continue;
                    }
                }

                ItemHandlerUtil.setStackInSlot(outputSlots, i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * @param slot slot index
     * @param is   itemstack
     * @return is if it can be used, else EMPTY
     */
    private boolean canUseInSlot(int slot, ItemStack is) {
        if (slot >= this.recipe.size()) return false;

        var variants = this.recipe.get(slot);
        if (variants != null) {
            for (ItemStack option : variants) {
                if (ItemStack.areItemStacksEqual(is, option)) {
                    return true;
                }
            }
        }
        return false;
    }

}
