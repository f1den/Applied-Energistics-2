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

package appeng.parts.automation;


import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class PartIdentityAnnihilationPlane extends PartAnnihilationPlane {

    private static final PlaneModels MODELS = new PlaneModels("part/identity_annihilation_plane_", "part/identity_annihilation_plane_on_");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private static final float SILK_TOUCH_FACTOR = 16;

    public PartIdentityAnnihilationPlane(final ItemStack is) {
        super(is);
    }

    @Override
    protected float calculateEnergyUsage(final WorldServer w, final BlockPos pos, final List<ItemStack> items) {
        final float requiredEnergy = super.calculateEnergyUsage(w, pos, items);

        return requiredEnergy * SILK_TOUCH_FACTOR;
    }

    @Override
    protected List<ItemStack> obtainBlockDrops(final WorldServer w, final BlockPos pos) {
        final FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(w);
        final IBlockState state = w.getBlockState(pos);

        final ItemStack prevItem = fakePlayer.getHeldItem(EnumHand.MAIN_HAND);
        final ItemStack stack = getItemStack();
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, stack);

        if (state.getBlock().canSilkHarvest(w, pos, state, fakePlayer) && getEnchantLevel(Enchantments.FORTUNE, stack) <= 0) {
            final List<ItemStack> out = new ArrayList<>(1);
            final Item item = Item.getItemFromBlock(state.getBlock());

            if (item != Items.AIR) {
                int meta = 0;
                if (item.getHasSubtypes()) {
                    meta = state.getBlock().getMetaFromState(state);
                }
                final ItemStack itemstack = new ItemStack(item, 1, meta);
                out.add(itemstack);

                final float chance = ForgeEventFactory.fireBlockHarvesting(out, w, pos, state, 0, 1.0F, true, fakePlayer);
                fakePlayer.setHeldItem(EnumHand.MAIN_HAND, prevItem);
                if (chance == 1.0F) return out;
                return out.stream().filter($ -> w.rand.nextFloat() <= chance).collect(Collectors.toList());
            }
            fakePlayer.setHeldItem(EnumHand.MAIN_HAND, prevItem);
            return out;
        }
        final int fortune = getEnchantLevel(Enchantments.FORTUNE, stack);
        final NonNullList<ItemStack> drops = NonNullList.create();
        state.getBlock().getDrops(drops, w, pos, state, fortune);

        final float chance = ForgeEventFactory.fireBlockHarvesting(drops, w, pos, state, fortune, 1.0F, false, fakePlayer);
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, prevItem);
        if (chance == 1.0F) return drops;
        return drops.stream().filter($ -> w.rand.nextFloat() <= chance).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.getConnections(), this.isPowered(), this.isActive());
    }

    private static int getEnchantLevel(final Enchantment enchantment, final ItemStack stack) {
        if (enchantment == null) return 0;
        return EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
    }
}
