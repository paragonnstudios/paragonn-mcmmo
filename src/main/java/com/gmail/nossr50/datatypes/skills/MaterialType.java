package com.gmail.nossr50.datatypes.skills;

import org.bukkit.Material;

public enum MaterialType {
    STRING,
    LEATHER,
    WOOD,
    STONE,
    IRON,
    COPPER,
    GOLD,
    DIAMOND,
    NETHERITE,
    PRISMARINE,
    OTHER;

    public Material getDefaultMaterial() {
        switch (this) {
            case STRING:
                return safeGetMaterial("STRING", "STRING");

            case LEATHER:
                return Material.LEATHER;

            case WOOD:
                return safeGetMaterial("OAK_PLANKS", "WOOD");

            case STONE:
                return Material.COBBLESTONE;

            case IRON:
                return Material.IRON_INGOT;

            case GOLD:
                return safeGetMaterial("GOLD_INGOT", "GOLD_INGOT");

            case DIAMOND:
                return Material.DIAMOND;

            case NETHERITE:
                return safeGetMaterial("NETHERITE_SCRAP", "DIAMOND");

            case PRISMARINE:
                return safeGetMaterial("PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS");

            case COPPER:
                return safeGetMaterial("COPPER_INGOT", "IRON_INGOT");

            case OTHER:
            default:
                return null;
        }
    }

    private Material safeGetMaterial(String modernName, String fallbackName) {
        Material material = Material.getMaterial(modernName);
        if (material != null) {
            return material;
        }
        return Material.getMaterial(fallbackName);
    }
}
