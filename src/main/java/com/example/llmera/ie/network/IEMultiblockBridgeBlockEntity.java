package com.example.llmera.ie.network;

import com.example.llmera.ie.LLMEraIE;
import com.example.llmera.network.CodeNetworkMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class IEMultiblockBridgeBlockEntity extends BlockEntity implements CodeNetworkMember {
    public static final int CHANNEL_DEMAND = 4;

    public IEMultiblockBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(LLMEraIE.MULTIBLOCK_BRIDGE.get(), pos, state);
    }

    @Override
    public int channelDemand() {
        return CHANNEL_DEMAND;
    }
}
