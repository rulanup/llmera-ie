package com.example.llmera.ie;

import com.example.llmera.ie.llm.IEToolExtension;
import com.example.llmera.ie.network.IEMultiblockBridgeBlock;
import com.example.llmera.ie.network.IEMultiblockBridgeBlockEntity;
import com.example.llmera.llm.ToolExtensionRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LLMEraIE.MODID)
public final class LLMEraIE {
    public static final String MODID = "llmera_ie";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<IEMultiblockBridgeBlock> MULTIBLOCK_BRIDGE_BLOCK =
            BLOCKS.register("ie_multiblock_bridge", IEMultiblockBridgeBlock::new);
    public static final DeferredItem<BlockItem> MULTIBLOCK_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem("ie_multiblock_bridge", MULTIBLOCK_BRIDGE_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IEMultiblockBridgeBlockEntity>> MULTIBLOCK_BRIDGE =
            BLOCK_ENTITIES.register("ie_multiblock_bridge", () -> BlockEntityType.Builder.of(
                    IEMultiblockBridgeBlockEntity::new, MULTIBLOCK_BRIDGE_BLOCK.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.llmera_ie"))
                    .icon(() -> new ItemStack(MULTIBLOCK_BRIDGE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(MULTIBLOCK_BRIDGE_ITEM.get());
                    })
                    .build());

    public LLMEraIE(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ToolExtensionRegistry.register(IEToolExtension.extension());
    }
}
