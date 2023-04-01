package com.koteinik.chunksfadein.mixin.entity;

import java.util.Iterator;
import java.util.SortedSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.koteinik.chunksfadein.ChunkUtils;
import com.koteinik.chunksfadein.MathUtils;
import com.koteinik.chunksfadein.config.Config;
import com.koteinik.chunksfadein.core.ChunkAppearedLink;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {
    @Inject(method = "renderTileEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V", shift = Shift.AFTER, remap = true), locals = LocalCapture.CAPTURE_FAILHARD)
    @SuppressWarnings("rawtypes")
    private void modifyRenderTileEntities(MatrixStack matrices, BufferBuilderStorage bufferBuilders,
            Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
            Camera camera, float tickDelta, CallbackInfo ci, Immediate i1, Vec3d v, double d1, double d2, double d3,
            BlockEntityRenderDispatcher d, Iterator i2, BlockEntity entity) {
        if (!Config.isModEnabled)
            return;

        if (!entity.hasWorld() || !Config.isAnimationEnabled)
            return;

        ChunkSectionPos chunkPos = ChunkSectionPos.from(entity.getPos());

        ChunkSection chunk = ChunkUtils.getChunkOn(entity.getWorld(), chunkPos);

        if (chunk == null || chunk.isEmpty())
            return;

        float[] fadeData = ChunkAppearedLink.getChunkData(chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());

        matrices.translate(fadeData[0], fadeData[1], fadeData[2]);
    }

    @Inject(method = "isEntityVisible", at = @At(value = "RETURN"), cancellable = true)
    private void modifyIsEntityVisible(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.isModEnabled)
            return;

        if (cir.getReturnValueZ()) {
            ChunkPos chunkPos = entity.getChunkPos();
            int chunkY = MathUtils.floor((float) entity.getY() / 16f);

            int x = chunkPos.x;
            int y = chunkY;
            int z = chunkPos.z;
            float[] fadeData = ChunkAppearedLink.getChunkData(x, y, z);

            boolean isVisible = !(fadeData[1] == -Config.animationInitialOffset && fadeData[2] == 0f);

            cir.setReturnValue(isVisible);
        }
    }

}
