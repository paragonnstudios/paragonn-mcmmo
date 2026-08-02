package com.gmail.nossr50.skills.herbalism;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

public class Herbalism {

    /**
     * Convert blocks affected by the Green Thumb & Green Terra abilities.
     *
     * @param blockState The {@link BlockState} to check ability activation for
     * @return true if the ability was successful, false otherwise
     */
    protected static boolean convertGreenTerraBlocks(BlockState blockState) {
        String typeName = blockState.getType().name();

        if (typeName.equals("COBBLESTONE_WALL")) {
            safeSetType(blockState, "MOSSY_COBBLESTONE_WALL");
            return true;
        } else if (typeName.equals("STONE_BRICKS")) {
            safeSetType(blockState, "MOSSY_STONE_BRICKS");
            return true;
        } else if (typeName.equals("DIRT") || typeName.equals("DIRT_PATH") || typeName.equals("GRASS_PATH")) {
            safeSetType(blockState, "GRASS_BLOCK");
            if (blockState.getType().name().equals(typeName)) {
                safeSetType(blockState, "GRASS");
            }
            return true;
        } else if (typeName.equals("COBBLESTONE")) {
            safeSetType(blockState, "MOSSY_COBBLESTONE");
            return true;
        }

        return false;
    }

    /**
     * Convert blocks affected by the Green Thumb & Green Terra abilities.
     *
     * @param blockState The {@link BlockState} to check ability activation for
     * @return true if the ability was successful, false otherwise
     */
    protected static boolean convertShroomThumb(BlockState blockState) {
        String typeName = blockState.getType().name();

        if (typeName.equals("DIRT") || typeName.equals("GRASS_BLOCK") || typeName.equals("GRASS") || typeName.equals("DIRT_PATH") || typeName.equals("GRASS_PATH")) {
            safeSetType(blockState, "MYCELIUM");
            if (blockState.getType().name().equals(typeName)) {
                safeSetType(blockState, "MYCEL");
            }
            return true;
        }

        return false;
    }

    private static void safeSetType(BlockState blockState, String materialName) {
        try {
            blockState.setType(Material.valueOf(materialName));
        } catch (Exception ignored) {
            // Fallback for very old versions
            if (materialName.equals("GRASS_BLOCK")) safeSetType(blockState, "GRASS");
            if (materialName.equals("MYCELIUM")) safeSetType(blockState, "MYCEL");
        }
    }
}
