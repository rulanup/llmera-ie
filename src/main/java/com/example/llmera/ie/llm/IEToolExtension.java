package com.example.llmera.ie.llm;

import com.example.llmera.llm.ToolDefinition;
import com.example.llmera.llm.ToolExtension;
import com.example.llmera.llm.ToolInvocation;
import com.example.llmera.network.IntelligentTransmitterBlockEntity;
import com.example.llmera.network.LlmToolMode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.TemplateMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class IEToolExtension {
    private static final Gson GSON = new Gson();
    private static final int MAX_ARRAY = 256;

    private static final ToolDefinition MULTIBLOCK_LIST = new ToolDefinition(
            "ie_multiblock_list",
            "List all available Immersive Engineering multiblock structures with their names and sizes.",
            Map.of());

    private static final ToolDefinition MULTIBLOCK_INFO = new ToolDefinition(
            "ie_multiblock_info",
            "Get detailed information about a specific IE multiblock: required blocks, dimensions, trigger block, and master position.",
            Map.ofEntries(
                    Map.entry("multiblock_name", Map.of("type", "string", "description", "name of the multiblock (e.g. 'immersiveengineering:crusher', 'immersiveengineering:coke_oven')")),
                    Map.entry("facing", Map.of("type", "string", "description", "optional: facing direction (north/south/east/west), default north")),
                    Map.entry("mirrored", Map.of("type", "boolean", "description", "optional: whether mirrored, default false"))));

    private static final ToolDefinition MULTIBLOCK_BUILD = new ToolDefinition(
            "ie_multiblock_build",
            "Automatically build an IE multiblock structure at the specified position. Places all required blocks. The origin is where the trigger block goes.",
            Map.ofEntries(
                    Map.entry("multiblock_name", Map.of("type", "string", "description", "name of the multiblock (e.g. 'immersiveengineering:crusher')")),
                    Map.entry("x", coordinate("origin x")), Map.entry("y", coordinate("origin y")), Map.entry("z", coordinate("origin z")),
                    Map.entry("facing", Map.of("type", "string", "description", "optional: facing direction (north/south/east/west), default north")),
                    Map.entry("mirrored", Map.of("type", "boolean", "description", "optional: whether mirrored, default false"))));

    private static final String GUIDANCE = """
            Immersive Engineering integration tools:
              - Use ie_multiblock_list to see all available multiblock structures and their sizes.
              - Use ie_multiblock_info to get the required blocks and layout for a specific multiblock before building.
              - Use ie_multiblock_build to automatically place all blocks of a multiblock structure at a location.
              - The origin position is where the trigger block goes. Facing determines the direction the multiblock faces.
              - Common multiblocks: immersiveengineering:crusher, immersiveengineering:coke_oven, immersiveengineering:arc_furnace, immersiveengineering:metal_press, immersiveengineering:assembler, immersiveengineering:tank, immersiveengineering:silo, immersiveengineering:diesel_generator
            """;

    private IEToolExtension() {
    }

    public static ToolExtension extension() {
        return new ToolExtension(List.of(
                new ToolExtension.Tool(MULTIBLOCK_LIST, Set.of(LlmToolMode.BUILD, LlmToolMode.PLAN), IEToolExtension::execute),
                new ToolExtension.Tool(MULTIBLOCK_INFO, Set.of(LlmToolMode.BUILD, LlmToolMode.PLAN), IEToolExtension::execute),
                new ToolExtension.Tool(MULTIBLOCK_BUILD, Set.of(LlmToolMode.BUILD), IEToolExtension::execute)
        ), GUIDANCE);
    }

    private static Map<String, String> coordinate(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static String execute(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        return switch (invocation.name()) {
            case "ie_multiblock_list" -> multiblockList();
            case "ie_multiblock_info" -> multiblockInfo(invocation);
            case "ie_multiblock_build" -> multiblockBuild(transmitter, invocation);
            default -> error("unknown IE tool: " + invocation.name());
        };
    }

    private static String multiblockList() {
        JsonObject out = base("ie_multiblock_list");
        JsonArray multiblocks = new JsonArray();
        for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", mb.getUniqueName().toString());
            entry.addProperty("display_name", mb.getDisplayName().getString());
            Vec3i size = mb.getSize(null);
            entry.add("size", vecJson(size));
            multiblocks.add(entry);
        }
        out.add("multiblocks", multiblocks);
        out.addProperty("count", multiblocks.size());
        return GSON.toJson(out);
    }

    private static String multiblockInfo(ToolInvocation invocation) {
        String name = stringArg(invocation, "multiblock_name");
        if (name == null) return error("multiblock_name is required");

        MultiblockHandler.IMultiblock mb = MultiblockHandler.getByUniqueName(
                net.minecraft.resources.ResourceLocation.tryParse(name));
        if (mb == null) return error("multiblock not found: " + name);

        JsonObject out = base("ie_multiblock_info");
        out.addProperty("name", mb.getUniqueName().toString());
        out.addProperty("display_name", mb.getDisplayName().getString());
        Vec3i size = mb.getSize(null);
        out.add("size", vecJson(size));
        out.add("trigger_offset", vecJson(mb.getTriggerOffset()));

        if (mb instanceof TemplateMultiblock templateMb) {
            List<StructureBlockInfo> structure = templateMb.getStructure(null);
            JsonArray blocks = new JsonArray();
            for (StructureBlockInfo info : structure) {
                if (blocks.size() >= MAX_ARRAY) break;
                JsonObject block = new JsonObject();
                block.add("pos", vecJson(info.pos()));
                block.addProperty("block", BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString());
                block.addProperty("state", info.state().toString());
                blocks.add(block);
            }
            out.add("blocks", blocks);
            out.addProperty("block_count", structure.size());
            // masterFromOrigin is protected, use reflection
            try {
                java.lang.reflect.Field field = TemplateMultiblock.class.getDeclaredField("masterFromOrigin");
                field.setAccessible(true);
                BlockPos masterOffset = (BlockPos) field.get(templateMb);
                out.add("master_offset", vecJson(masterOffset));
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return GSON.toJson(out);
    }

    private static String multiblockBuild(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        if (!(transmitter.getLevel() instanceof ServerLevel level)) return error("transmitter level is not a ServerLevel");

        String name = stringArg(invocation, "multiblock_name");
        if (name == null) return error("multiblock_name is required");

        Integer x = integer(invocation.arguments().get("x"));
        Integer y = integer(invocation.arguments().get("y"));
        Integer z = integer(invocation.arguments().get("z"));
        if (x == null || y == null || z == null) return error("x, y, and z are required");
        BlockPos origin = new BlockPos(x, y, z);

        String facingStr = stringArg(invocation, "facing");
        Direction facing = Direction.NORTH;
        if (facingStr != null) {
            try {
                facing = Direction.valueOf(facingStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return error("invalid facing: " + facingStr + ". Use north/south/east/west");
            }
        }
        if (facing == Direction.UP || facing == Direction.DOWN) {
            return error("facing must be horizontal (north/south/east/west)");
        }

        Boolean mirrored = boolArg(invocation, "mirrored");
        boolean isMirrored = mirrored != null && mirrored;

        MultiblockHandler.IMultiblock mb = MultiblockHandler.getByUniqueName(
                net.minecraft.resources.ResourceLocation.tryParse(name));
        if (mb == null) return error("multiblock not found: " + name);

        if (!(mb instanceof TemplateMultiblock templateMb)) {
            return error("multiblock does not support template-based building: " + name);
        }

        List<StructureBlockInfo> structure = templateMb.getStructure(level);
        if (structure == null || structure.isEmpty()) return error("failed to load multiblock structure");

        // Get master offset via reflection
        BlockPos masterOffset = BlockPos.ZERO;
        try {
            java.lang.reflect.Field field = TemplateMultiblock.class.getDeclaredField("masterFromOrigin");
            field.setAccessible(true);
            masterOffset = (BlockPos) field.get(templateMb);
        } catch (ReflectiveOperationException ignored) {
        }

        net.minecraft.world.level.block.Mirror mirror = isMirrored
                ? net.minecraft.world.level.block.Mirror.FRONT_BACK
                : net.minecraft.world.level.block.Mirror.NONE;
        net.minecraft.world.level.block.Rotation rotation = getRotation(facing);

        BlockPos masterPos = TemplateMultiblock.withSettingsAndOffset(origin, masterOffset, mirror, rotation);

        int placed = 0;
        int failed = 0;
        JsonArray errors = new JsonArray();

        for (StructureBlockInfo info : structure) {
            try {
                BlockPos absolutePos = TemplateMultiblock.withSettingsAndOffset(origin, info.pos(), mirror, rotation);

                if (!level.hasChunkAt(absolutePos)) {
                    failed++;
                    continue;
                }

                BlockState targetState = info.state();
                if (level.setBlock(absolutePos, targetState, 2)) {
                    placed++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                if (errors.size() < 16) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        JsonObject out = base("ie_multiblock_build");
        out.addProperty("multiblock", name);
        out.add("origin", vecJson(origin));
        out.add("master", vecJson(masterPos));
        out.addProperty("facing", facing.getName());
        out.addProperty("mirrored", isMirrored);
        out.add("size", vecJson(templateMb.getSize(level)));
        out.addProperty("blocks_placed", placed);
        out.addProperty("blocks_failed", failed);
        out.addProperty("total_blocks", structure.size());
        if (!errors.isEmpty()) out.add("errors", errors);
        out.addProperty("note", "Multiblock structure placed. Use the IE hammer on the master block to form the multiblock.");
        return GSON.toJson(out);
    }

    private static net.minecraft.world.level.block.Rotation getRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };
    }

    private static JsonObject base(String tool) { JsonObject out = new JsonObject(); out.addProperty("ok", true); out.addProperty("tool", tool); return out; }
    private static String error(String message) { JsonObject out = new JsonObject(); out.addProperty("ok", false); out.addProperty("error", message); return GSON.toJson(out); }
    private static JsonObject vecJson(Vec3i pos) { JsonObject out = new JsonObject(); out.addProperty("x", pos.getX()); out.addProperty("y", pos.getY()); out.addProperty("z", pos.getZ()); return out; }
    private static String stringArg(ToolInvocation invocation, String name) { Object value = invocation.arguments().get(name); return value == null ? null : value.toString().trim(); }
    private static Boolean boolArg(ToolInvocation invocation, String name) { Object value = invocation.arguments().get(name); return value instanceof Boolean b ? b : value == null ? null : Boolean.valueOf(value.toString()); }
    private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : value == null ? null : parseInteger(value.toString()); }
    private static Integer parseInteger(String value) { try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; } }
}
