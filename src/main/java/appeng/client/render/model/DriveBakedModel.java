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

package appeng.client.render.model;

import appeng.block.storage.BlockDrive;
import appeng.block.storage.DriveSlotState;
import appeng.block.storage.DriveSlotsState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Tuple4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DriveBakedModel implements IBakedModel, IResourceManagerReloadListener {
    private final IBakedModel bakedBase;
    private final Map<DriveSlotState, IBakedModel> bakedCells;
    private final Int2ObjectMap<Map<DriveSlotState, List<BakedQuad>>> slot2DSMap = new Int2ObjectOpenHashMap<>();

    public DriveBakedModel(IBakedModel bakedBase, Map<DriveSlotState, IBakedModel> bakedCells) {
        this.bakedBase = bakedBase;
        this.bakedCells = bakedCells;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {

        List<BakedQuad> result = new ArrayList<>(this.bakedBase.getQuads(state, side, rand));

        if (side == null && state instanceof IExtendedBlockState extState) {

            if (!extState.getUnlistedNames().contains(BlockDrive.SLOTS_STATE)) {
                return result;
            }

            DriveSlotsState slotsState = extState.getValue(BlockDrive.SLOTS_STATE);


            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 2; col++) {
                    int slot = row * 2 + col;

                    DriveSlotState slotState = slotsState.getState(slot);

                    slot2DSMap.putIfAbsent(slot, new Object2ObjectOpenHashMap<>());
                    int finalCol = col;
                    int finalRow = row;
                    List<BakedQuad> lbq = slot2DSMap.get(slot).computeIfAbsent(slotState, q -> {
                        List<BakedQuad> res = new ArrayList<>();
                        IBakedModel bakedCell = this.bakedCells.get(slotState);

                        final Matrix4f transformMatrix = new Matrix4f();
                        transformMatrix.setIdentity();
                        final Tuple4f transform = new Vector4f();

                        // Position this drive model copy at the correct slot. The transform is based on
                        // the
                        // cell-model being in slot 0,0 at the top left of the drive.
                        float xOffset = -finalCol * 8 / 16.0f;
                        float yOffset = -finalRow * 3 / 16.0f;

                        transformMatrix.setTranslation(new Vector3f(xOffset, yOffset, 0));

                        for (BakedQuad bakedQuad : bakedCell.getQuads(state, null, rand)) {
                            res.add(rebakeQuad(bakedQuad, transformMatrix, transform));
                        }
                        return res;
                    });
                    result.addAll(lbq);
                }
            }
        }

        return result;
    }

    private BakedQuad rebakeQuad(BakedQuad b, Matrix4f transformMatrix, Tuple4f vertexTransformingVec) {
        int[] newQuad = new int[28];
        int[] quadData = b.getVertexData();
        for (int k = 0; k < 4; ++k) {
            // Getting the offset for the current vertex.
            int vertexIndex = k * 7;
            vertexTransformingVec.x = Float.intBitsToFloat(quadData[vertexIndex]);
            vertexTransformingVec.y = Float.intBitsToFloat(quadData[vertexIndex + 1]);
            vertexTransformingVec.z = Float.intBitsToFloat(quadData[vertexIndex + 2]);
            vertexTransformingVec.w = 1;

            // Transforming it by the model matrix.
            transformMatrix.transform(vertexTransformingVec);

            // Converting the new data to ints.
            int x = Float.floatToRawIntBits(vertexTransformingVec.x);
            int y = Float.floatToRawIntBits(vertexTransformingVec.y);
            int z = Float.floatToRawIntBits(vertexTransformingVec.z);

            // Vertex position data
            newQuad[vertexIndex] = x;
            newQuad[vertexIndex + 1] = y;
            newQuad[vertexIndex + 2] = z;

            newQuad[vertexIndex + 3] = quadData[vertexIndex + 3];

            newQuad[vertexIndex + 4] = quadData[vertexIndex + 4]; //texture
            newQuad[vertexIndex + 5] = quadData[vertexIndex + 5];

            // Vertex brightness
            newQuad[vertexIndex + 6] = 0x81818181;
        }
        return new BakedQuad(newQuad, b.getTintIndex(), b.getFace(), b.getSprite(), b.shouldApplyDiffuseLighting(), b.getFormat());
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.bakedBase.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.bakedBase.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.bakedBase.isGui3d();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.bakedBase.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.bakedBase.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.bakedBase.getOverrides();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        slot2DSMap.clear();
    }
}
