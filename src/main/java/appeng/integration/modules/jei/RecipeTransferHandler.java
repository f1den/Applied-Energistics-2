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

package appeng.integration.modules.jei;


import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerWirelessCraftingTerminal;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.util.GTUtility;
import gregtech.integration.jei.multiblock.MultiblockInfoCategory;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static appeng.helpers.ItemStackHelper.stackToNBT;
import static appeng.util.Platform.GTLoaded;


class RecipeTransferHandler<T extends Container> implements IRecipeTransferHandler<T> {

    private final Class<T> containerClass;

    RecipeTransferHandler(Class<T> containerClass) {
        this.containerClass = containerClass;
    }

    @Override
    public @NotNull Class<T> getContainerClass() {
        return this.containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull T container, IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        final String recipeType = recipeLayout.getRecipeCategory().getUid();

        if (recipeType.equals(VanillaRecipeCategoryUid.INFORMATION) || recipeType.equals(VanillaRecipeCategoryUid.FUEL)) {
            return RecipeTransferErrorInternal.INSTANCE;
        }

        if (!doTransfer) {
            if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING) && (container instanceof ContainerCraftingTerm || container instanceof ContainerWirelessCraftingTerminal)) {
                JEIMissingItem error = new JEIMissingItem(container, recipeLayout);

                if (error.errored())
                    return error;
            }

            if (container instanceof ContainerPatternEncoder || container instanceof ContainerCraftingTerm) {
                JEITransferInfo.INSTANCE.setRecipeLayout((RecipeLayout) recipeLayout);
                return JEITransferInfo.INSTANCE;
            }
        }

        if (container instanceof ContainerPatternEncoder) {
            try {
                if (!((ContainerPatternEncoder) container).isCraftingMode() && !maxTransfer) {
                    if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    }
                } else if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING) || maxTransfer) {

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();

        final NBTTagCompound recipe = new NBTTagCompound();
        final NBTTagList outputs = new NBTTagList();

        var slotIndex = 0;
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ingredientEntry : ingredients.entrySet()) {
            IGuiIngredient<ItemStack> ingredient = ingredientEntry.getValue();
            if (!ingredient.isInput()) {
                ItemStack output = ingredient.getDisplayedIngredient();
                if (output != null) {
                    final NBTTagCompound tag = stackToNBT(output);
                    outputs.appendTag(tag);
                }
                continue;
            }

            final NBTTagList tags = new NBTTagList();
            final List<ItemStack> list = new ArrayList<>();
            final ItemStack displayed = ingredient.getDisplayedIngredient();

            // prefer currently displayed item
            if (displayed != null && !displayed.isEmpty()) {
                list.add(displayed);
            }

            // prefer pure crystals.
            for (ItemStack stack : ingredient.getAllIngredients()) {
                if (stack == null) {
                    continue;
                }
                if (Platform.isRecipePrioritized(stack)) {
                    list.add(0, stack);
                } else {
                    list.add(stack);
                }
            }

            for (final ItemStack is : list) {
                final NBTTagCompound tag = stackToNBT(is);
                tags.appendTag(tag);
            }

            recipe.setTag("#" + slotIndex, tags);
            slotIndex++;
        }

        if (outputs.isEmpty() && GTLoaded && recipeLayout.getRecipeCategory() instanceof MultiblockInfoCategory) {
            // JEI doesn't allow getting the recipe wrapper :(
            String controllerName = null;
            for (var ingredient : ingredients.entrySet()) {
                if (!ingredient.getValue().isInput()) continue;

                var ingredientStack = ingredient.getValue().getDisplayedIngredient();
                if (ingredientStack == null) continue;

                var meta = GTUtility.getMetaTileEntity(ingredientStack);
                if (meta == null) continue;

                if (!(meta instanceof IMultiblockController)) continue;

                controllerName = I18n.format(meta.getMetaFullName());
                break;
            }

            if (controllerName != null) {
                var paper = Items.PAPER.getDefaultInstance().copy();
                paper.setStackDisplayName(controllerName);
                outputs.appendTag(stackToNBT(paper));
            }
        }

        recipe.setTag("outputs", outputs);
        recipe.setBoolean("condense", maxTransfer);

        try {
            NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
        } catch (IOException e) {
            AELog.debug(e);
        }

        return null;
    }
}
