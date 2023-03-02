package com.koteinik.chunksfadein.core;

import java.util.List;

import com.koteinik.chunksfadein.MathUtils;
import com.koteinik.chunksfadein.config.Config;
import com.koteinik.chunksfadein.extenstions.ChunkShaderInterfaceExt;
import com.koteinik.chunksfadein.extenstions.RenderSectionExt;

import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public class ChunkFadeInController {
    private final float[] progressArr = new float[RenderRegion.REGION_SIZE];
    private final DataBuffer chunkFadeDatasBuffer = new DataBuffer(RenderRegion.REGION_SIZE, 4);
    private GlMutableBuffer chunkGlFadeDataBuffer;

    private long lastFrameTime = 0L;

    public ChunkFadeInController() {
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++)
            completeChunkFade(i, true);
    }

    public float[] getChunkData(int x, int y, int z) {
        return getChunkData(MathUtils.chunkIdFromGlobal(x, y, z));
    }

    public float[] getChunkData(int chunkId) {
        float x = chunkFadeDatasBuffer.get(chunkId, 0);
        float y = chunkFadeDatasBuffer.get(chunkId, 1);
        float z = chunkFadeDatasBuffer.get(chunkId, 2);
        float w = chunkFadeDatasBuffer.get(chunkId, 3);

        return new float[] { x, y, z, w };
    }

    public void completeChunkFade(int x, int y, int z, boolean completeFade) {
        completeChunkFade(MathUtils.chunkIdFromGlobal(x, y, z), completeFade);
    }

    public void completeChunkFade(int chunkId, boolean completeFade) {
        chunkFadeDatasBuffer.put(chunkId, 0, 0f);
        chunkFadeDatasBuffer.put(chunkId, 1, 0f);
        chunkFadeDatasBuffer.put(chunkId, 2, 0f);
        if (completeFade)
            chunkFadeDatasBuffer.put(chunkId, 3, 1f);
        progressArr[chunkId] = 1;
    }

    public void resetFadeForChunk(int x, int y, int z) {
        resetFadeForChunk(MathUtils.chunkIdFromGlobal(x, y, z));
    }

    public void resetFadeForChunk(int chunkId) {
        chunkFadeDatasBuffer.put(chunkId, 0, 0f);
        chunkFadeDatasBuffer.put(chunkId, 1, -Config.animationInitialOffset);
        chunkFadeDatasBuffer.put(chunkId, 2, 0f);
        chunkFadeDatasBuffer.put(chunkId, 3, Config.isFadeEnabled ? 0f : 1f);
        progressArr[chunkId] = 0;
    }

    public void updateChunksFade(List<RenderSection> chunks, ChunkShaderInterfaceExt shader, CommandList commandList) {
        checkMutableBuffer(commandList);

        final long currentFrameTime = System.nanoTime();
        final long delta = lastFrameTime == 0L ? 0 : (currentFrameTime - lastFrameTime) / 1000000;

        final int chunksSize = chunks.size();
        for (int i = 0; i < chunksSize; i++)
            processChunk(delta, chunks.get(i));

        chunkFadeDatasBuffer.uploadData(commandList, chunkGlFadeDataBuffer);
        shader.setFadeDatas(chunkGlFadeDataBuffer);
        lastFrameTime = currentFrameTime;
    }

    public void delete(CommandList commandList) {
        chunkFadeDatasBuffer.delete();

        if (chunkGlFadeDataBuffer != null)
            commandList.deleteBuffer(chunkGlFadeDataBuffer);
    }

    private void checkMutableBuffer(CommandList commandList) {
        if (chunkGlFadeDataBuffer == null)
            chunkGlFadeDataBuffer = commandList.createMutableBuffer();
    }

    private void processChunk(final long delta, RenderSection chunk) {
        final int chunkId = chunk.getChunkId();

        RenderSectionExt ext = (RenderSectionExt) chunk;

        if (!ext.hasRenderedBefore()) {
            boolean completeAnimation = false;
            if (!Config.animateNearPlayer) {
                MinecraftClient client = MinecraftClient.getInstance();
                final int chunkX = chunk.getChunkX();
                final int chunkY = chunk.getChunkY();
                final int chunkZ = chunk.getChunkZ();
                Entity camera = client.cameraEntity;
                if (camera != null) {
                    final int camChunkX = MathUtils.floor((float) (camera.lastRenderX / 16));
                    final int camChunkY = MathUtils.floor((float) (camera.lastRenderY / 16));
                    final int camChunkZ = MathUtils.floor((float) (camera.lastRenderZ / 16));

                    if (MathUtils.chunkInRange(chunkX, chunkY, chunkZ, camChunkX, camChunkY, camChunkZ, 1))
                        completeAnimation = true;
                }
            }

            if (!completeAnimation)
                resetFadeForChunk(chunk.getChunkId());
            else
                completeChunkFade(chunk.getChunkId(), false);
        }

        ext.setRenderedBefore();

        if (Config.isFadeEnabled) {
            final float fadeCoeffChange = Config.fadeChangePerMs * delta;
            float fadeCoeff = chunkFadeDatasBuffer.get(chunkId, 3);

            if (fadeCoeff != 1f) {
                fadeCoeff += fadeCoeffChange;

                if (fadeCoeff > 1f)
                    fadeCoeff = 1f;

                chunkFadeDatasBuffer.put(chunkId, 3, fadeCoeff);
            }
        } else if (chunkFadeDatasBuffer.get(chunkId, 3) != 1f)
            chunkFadeDatasBuffer.put(chunkId, 3, 1f);

        if (Config.isAnimationEnabled) {
            float y = chunkFadeDatasBuffer.get(chunkId, 1);
            if (y != 0f) {
                float animationProgress = progressArr[chunkId];

                animationProgress += Config.animationChangePerMs * delta;
                if (animationProgress > 1f)
                    animationProgress = 1f;

                progressArr[chunkId] = animationProgress;

                float curved = Config.animationInitialOffset
                        - Config.animationCurve.calculate(animationProgress) * Config.animationInitialOffset;
                curved = -curved;

                if (y <= 0f && curved > 0f) {
                    y = 0f;
                } else
                    y = curved;

                chunkFadeDatasBuffer.put(chunkId, 1, y);
            }
        } else if (chunkFadeDatasBuffer.get(chunkId, 1) != 0f)
            chunkFadeDatasBuffer.put(chunkId, 1, 0f);
    }
}
