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


import appeng.block.AEBaseTileBlock;
import appeng.client.render.FacingToRotation;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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

import javax.vecmath.Matrix4f;
import javax.vecmath.Tuple4f;
import javax.vecmath.Vector4f;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.EnumFacing.DOWN;


public class AutoRotatingModel implements IBakedModel, IResourceManagerReloadListener {

    private final IBakedModel parent;
    private final LoadingCache<AutoRotatingCacheKey, List<BakedQuad>> quadCache;

    public AutoRotatingModel(IBakedModel parent) {
        this.parent = parent;
        // 6 (DUNSWE) * 6 (DUNSWE) * 7 (DUNSWE + null) = 252
        this.quadCache = CacheBuilder.newBuilder().maximumSize(252).build(new CacheLoader<AutoRotatingCacheKey, List<BakedQuad>>() {
            @Override
            public List<BakedQuad> load(AutoRotatingCacheKey key) throws Exception {
                return AutoRotatingModel.this.getRotatedModel(key.getBlockState(), key.getSide(), key.getForward(), key.getUp());
            }
        });
    }

    private List<BakedQuad> getRotatedModel(IBlockState state, EnumFacing side, EnumFacing forward, EnumFacing up) {
        FacingToRotation f2r = FacingToRotation.get(forward, up);
        List<BakedQuad> original = AutoRotatingModel.this.parent.getQuads(state, f2r.resultingRotate(side), 0);
        List<BakedQuad> rotated = new ArrayList<>(original.size());

        final Matrix4f transformMatrix = new Matrix4f();
        final Tuple4f vertexTransformingVec = new Vector4f();

        // Transform the matrix so the top and front facings of the model matches the block in-world
        transformMatrixByFacings(transformMatrix, up, forward);

        for (BakedQuad b : original) {
            EnumFacing newFace = f2r.rotate(b.getFace());
            rotated.add(rebakeQuad(b, newFace, transformMatrix, vertexTransformingVec));
        }
        return rotated;
    }

    private void transformMatrixByFacings(Matrix4f transformMatrix, EnumFacing topFacing, EnumFacing frontFacing) {
        Matrix4f intermediaryMatrix = new Matrix4f();
        transformMatrix.setIdentity();
        moveToPivot(transformMatrix, intermediaryMatrix, true);
        if (topFacing.getAxis() == EnumFacing.Axis.Y) {
            switch (frontFacing) {
                case NORTH:
                    rotateY(transformMatrix, intermediaryMatrix, (float) (0));
                    break;
                case SOUTH:
                    rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI));
                    break;
                case EAST:
                    rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                    break;
                case WEST:
                    rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                    break;
            }
            if (topFacing == DOWN) {
                rotateX(transformMatrix, intermediaryMatrix, (float) (Math.PI));
                rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI));
            }
        } else {
            switch (topFacing) {
                case WEST:
                    rotateZ(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                    switch (frontFacing) {
                        case DOWN:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                            break;
                        case UP:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (+Math.PI / 2));
                            break;
                        case SOUTH:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI));
                    }
                    break;
                case EAST:
                    rotateZ(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                    switch (frontFacing) {
                        case DOWN:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                            break;
                        case UP:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                            break;
                        case SOUTH:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI));
                    }
                    break;
                case NORTH:
                    rotateX(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                    switch (frontFacing) {
                        case DOWN:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI));
                            break;
                        case EAST:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                            break;
                        case WEST:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                    }
                    break;
                default:
                    rotateX(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                    switch (frontFacing) {
                        case UP:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI));
                            break;
                        case EAST:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (-Math.PI / 2));
                            break;
                        case SOUTH:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                            break;
                        case WEST:
                            rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI / 2));
                            break;
                    }
            }
            if (frontFacing.getAxis() == EnumFacing.Axis.Y) {
                //rotateX(transformMatrix, intermediaryMatrix, (float) (Math.PI));
                rotateY(transformMatrix, intermediaryMatrix, (float) (Math.PI));
            }
        }
        moveToPivot(transformMatrix, intermediaryMatrix, false);
    }

    private void moveToPivot(Matrix4f matrix, Matrix4f intermediary, boolean positive) {
        intermediary.setIdentity();
        float pivot = positive ? .5F : -.5F;
        intermediary.m03 = pivot;
        intermediary.m13 = pivot;
        intermediary.m23 = pivot;
        matrix.mul(intermediary);
    }

    private void rotateX(Matrix4f matrix, Matrix4f intermediary, float angle) {
        intermediary.setIdentity();
        intermediary.rotX(angle);
        matrix.mul(intermediary);
    }

    private void rotateY(Matrix4f matrix, Matrix4f intermediary, float angle) {
        intermediary.setIdentity();
        intermediary.rotY(angle);
        matrix.mul(intermediary);
    }

    private void rotateZ(Matrix4f matrix, Matrix4f intermediary, float angle) {
        intermediary.setIdentity();
        intermediary.rotZ(angle);
        matrix.mul(intermediary);
    }

    private BakedQuad rebakeQuad(BakedQuad b, EnumFacing newFacing, Matrix4f transformMatrix, Tuple4f vertexTransformingVec) {
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
        return new BakedQuad(newQuad, b.getTintIndex(), newFacing, b.getSprite(), b.shouldApplyDiffuseLighting(), b.getFormat());
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.parent.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.parent.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.parent.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.parent.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.parent.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.parent.getOverrides();
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        if (!(state instanceof IExtendedBlockState)) {
            return this.parent.getQuads(state, side, rand);
        }

        IExtendedBlockState extState = (IExtendedBlockState) state;

        EnumFacing forward = extState.getValue(AEBaseTileBlock.FORWARD);
        EnumFacing up = extState.getValue(AEBaseTileBlock.UP);

        if (forward == null || up == null) {
            return this.parent.getQuads(state, side, rand);
        }

        // The model has other properties than just forward/up, so it would cause our cache to inadvertendly also cache
        // these
        // additional states, possibly leading to huge isseus if the other extended state properties do not implement
        // equals/hashCode correctly
        if (extState.getUnlistedProperties().size() != 2) {
            return this.getRotatedModel(extState, side, forward, up);
        }

        AutoRotatingCacheKey key = new AutoRotatingCacheKey(extState.getClean(), forward, up, side);

        return this.quadCache.getUnchecked(key);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.quadCache.invalidateAll();
    }

}
