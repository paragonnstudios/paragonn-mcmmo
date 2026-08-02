package com.gmail.nossr50.skills.mining;

import static com.gmail.nossr50.util.ItemUtils.isPickaxe;
import static com.gmail.nossr50.util.Misc.getBlockCenter;

import com.gmail.nossr50.api.ItemSpawnReason;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.runnables.skills.AbilityCooldownTask;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.EventUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.MetadataConstants;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.Probability;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MiningManager extends SkillManager {

    private static final String BUDDING_AMETHYST = "budding_amethyst";
    private static final Collection<Material> BLAST_MINING_BLACKLIST;
    private final static Set<String> INFESTED_BLOCKS = ImmutableSet.of("infested_stone",
            "infested_cobblestone",
            "infested_stone_bricks", "infested_cracked_stone_bricks", "infested_mossy_stone_bricks",
            "infested_chiseled_stone_bricks", "infested_deepslate");

    static {
        Set<Material> blacklist = new HashSet<>();
        addIfPresent(blacklist, "SPAWNER");
        addIfPresent(blacklist, "INFESTED_COBBLESTONE");
        addIfPresent(blacklist, "INFESTED_DEEPSLATE");
        addIfPresent(blacklist, "INFESTED_STONE");
        addIfPresent(blacklist, "INFESTED_STONE_BRICKS");
        addIfPresent(blacklist, "INFESTED_CRACKED_STONE_BRICKS");
        addIfPresent(blacklist, "INFESTED_CHISELED_STONE_BRICKS");
        addIfPresent(blacklist, "INFESTED_MOSSY_STONE_BRICKS");
        BLAST_MINING_BLACKLIST = ImmutableSet.copyOf(blacklist);
    }

    private static void addIfPresent(Set<Material> set, String name) {
        Material material = Material.getMaterial(name);
        if (material != null) {
            set.add(material);
        }
    }

    public MiningManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.MINING);
    }

    public boolean canUseDemolitionsExpertise() {
        if (!RankUtils.hasUnlockedSubskill(getPlayer(),
                SubSkillType.MINING_DEMOLITIONS_EXPERTISE)) {
            return false;
        }

        return getSkillLevel() >= BlastMining.getDemolitionExpertUnlockLevel()
                && Permissions.demolitionsExpertise(getPlayer());
    }

    public boolean canDetonate() {
        Player player = getPlayer();

        return canUseBlastMining()
                && player.isSneaking()
                && (isPickaxe(getPlayer().getInventory().getItemInMainHand()) || isDetonatorInHand(
                player))
                && Permissions.remoteDetonation(player);
    }

    private static boolean isDetonatorInHand(Player player) {
        return player.getInventory().getItemInMainHand().getType() == mcMMO.p.getGeneralConfig()
                .getDetonatorItem();
    }

    public boolean canUseBlastMining() {
        //Not checking permissions?
        return RankUtils.hasUnlockedSubskill(getPlayer(), SubSkillType.MINING_BLAST_MINING);
    }

    public boolean canUseBiggerBombs() {
        if (!RankUtils.hasUnlockedSubskill(getPlayer(), SubSkillType.MINING_BIGGER_BOMBS)) {
            return false;
        }

        return getSkillLevel() >= BlastMining.getBiggerBombsUnlockLevel()
                && Permissions.biggerBombs(getPlayer());
    }

    public boolean canDoubleDrop() {
        return RankUtils.hasUnlockedSubskill(getPlayer(), SubSkillType.MINING_DOUBLE_DROPS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.MINING_DOUBLE_DROPS);
    }

    public boolean canMotherLode() {
        return Permissions.canUseSubSkill(getPlayer(), SubSkillType.MINING_MOTHER_LODE);
    }


    /**
     * Process double drops & XP gain for Mining.
     *
     * @param blockState The {@link BlockState} to check ability activation for
     */
    @Deprecated
    public void miningBlockCheck(BlockState blockState) {
        miningBlockCheck(blockState.getBlock());
    }

    public void miningBlockCheck(Block block) {
        Player player = getPlayer();

        applyXpGain(ExperienceConfig.getInstance().getXp(PrimarySkillType.MINING, block),
                XPGainReason.PVE, XPGainSource.SELF);

        if (!Permissions.isSubSkillEnabled(player, SubSkillType.MINING_DOUBLE_DROPS)) {
            return;
        }

        if (mmoPlayer.getAbilityMode(mcMMO.p.getSkillTools().getSuperAbility(skill))) {
            SkillUtils.handleDurabilityChange(getPlayer().getInventory().getItemInMainHand(),
                    mcMMO.p.getGeneralConfig().getAbilityToolDamage());
        }

        if (!mcMMO.p.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.MINING, block.getType())
                || !canDoubleDrop()) {
            return;
        }

        boolean silkTouch = player.getInventory().getItemInMainHand()
                .containsEnchantment(Enchantment.SILK_TOUCH);

        if (silkTouch && !mcMMO.p.getAdvancedConfig().getDoubleDropSilkTouchEnabled()) {
            return;
        }

        //Mining mastery allows for a chance of triple drops
        if (canMotherLode()) {
            //Triple Drops failed so do a normal double drops check
            if (!processTripleDrops(block)) {
                processDoubleDrops(block);
            }
        } else {
            //If the user has no mastery, proceed with normal double drop routine
            processDoubleDrops(block);
        }
    }

    private boolean processTripleDrops(@NotNull Block block) {
        //TODO: Make this readable
        if (ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.MINING_MOTHER_LODE, mmoPlayer)) {
            BlockUtils.markDropsAsBonus(block, 2);
            return true;
        } else {
            return false;
        }
    }

    private void processDoubleDrops(@NotNull Block block) {
        //TODO: Make this readable
        if (ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.MINING_DOUBLE_DROPS, mmoPlayer)) {
            boolean useTriple = mmoPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER)
                    && mcMMO.p.getAdvancedConfig().getAllowMiningTripleDrops();
            BlockUtils.markDropsAsBonus(block, useTriple);
        }
    }

    /**
     * Detonate TNT for Blast Mining
     */
    public void remoteDetonation() {
        final Player player = getPlayer();
        final Block targetBlock = player.getTargetBlock(BlockUtils.getTransparentBlocks(),
                BlastMining.MAXIMUM_REMOTE_DETONATION_DISTANCE);

        //Blast mining cooldown check needs to be first so the player can be messaged
        if (!blastMiningCooldownOver()
                || targetBlock.getType() != Material.TNT
                || !EventUtils.simulateBlockBreak(targetBlock, player)) {
            return;
        }

        final TNTPrimed tnt = player.getWorld().spawn(targetBlock.getLocation(), TNTPrimed.class);

        NotificationManager.sendPlayerInformation(player, NotificationType.SUPER_ABILITY,
                "Mining.Blast.Boom");

        tnt.setMetadata(MetadataConstants.METADATA_KEY_TRACKED_TNT, mmoPlayer.getPlayerMetadata());
        tnt.setFuseTicks(0);
        // tnt.setSource(player); // setSource might not exist in 1.8.8
        targetBlock.setType(Material.AIR);

        mmoPlayer.setAbilityDATS(SuperAbilityType.BLAST_MINING, System.currentTimeMillis());
        mmoPlayer.setAbilityInformed(SuperAbilityType.BLAST_MINING, false);
        mcMMO.p.getFoliaLib().getScheduler().runAtEntityLater(mmoPlayer.getPlayer(),
                new AbilityCooldownTask(mmoPlayer, SuperAbilityType.BLAST_MINING),
                (long) SuperAbilityType.BLAST_MINING.getCooldown() * Misc.TICK_CONVERSION_FACTOR);
    }

    private boolean isInfestedBlock(String material) {
        return INFESTED_BLOCKS.contains(material.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Handler for explosion drops and XP gain.
     *
     * @param yield The % of blocks to drop
     * @param event The {@link EntityExplodeEvent}
     */
    public void blastMiningDropProcessing(float yield, EntityExplodeEvent event) {
        if (yield == 0) {
            return;
        }

        float increasedYieldFromBonuses = yield + (yield * getOreBonus());
        // Strip out only stuff that gives mining XP
        final List<Block> ores = new ArrayList<>();
        final List<Block> notOres = new ArrayList<>();
        for (Block targetBlock : event.blockList()) {

            if (mcMMO.getUserBlockTracker().isIneligible(targetBlock)) {
                continue;
            }

            if (ExperienceConfig.getInstance().getXp(PrimarySkillType.MINING, targetBlock) != 0) {
                if (BlockUtils.isOre(targetBlock) && !(targetBlock instanceof Container)) {
                    ores.add(targetBlock);
                } else {
                    notOres.add(targetBlock);
                }
            } else {
                notOres.add(targetBlock);
            }
        }

        int xp = 0;
        int dropMultiplier = getDropMultiplier();

        for (Block block : notOres) {
            if (isDropIllegal(block.getType())) {
                continue;
            }

            // In 1.8.8 isItem() might not exist on Material
            if (Probability.ofPercent(10).evaluate()) {
                ItemUtils.spawnItem(getPlayer(),
                        getBlockCenter(block),
                        new ItemStack(block.getType()),
                        ItemSpawnReason.BLAST_MINING_DEBRIS_NON_ORES); // Initial block that would have been dropped
            }
        }

        for (Block block : ores) {
            // currentOreYield only used for drop calculations for ores
            float currentOreYield = Math.min(increasedYieldFromBonuses, 3F);

            if (isDropIllegal(block.getType())) {
                continue;
            }

            // Always give XP for every ore destroyed
            xp += ExperienceConfig.getInstance().getXp(PrimarySkillType.MINING, block);
            while (currentOreYield > 0) {
                if (Probability.ofValue(currentOreYield).evaluate()) {
                    Collection<ItemStack> oreDrops =
                            isPickaxe(mmoPlayer.getPlayer().getInventory().getItemInMainHand())
                                    ? block.getDrops(
                                    mmoPlayer.getPlayer().getInventory().getItemInMainHand())
                                    : ImmutableList.of(new ItemStack(block.getType()));
                    ItemUtils.spawnItems(getPlayer(), getBlockCenter(block),
                            oreDrops, BLAST_MINING_BLACKLIST, ItemSpawnReason.BLAST_MINING_ORES);

                    if (mcMMO.p.getAdvancedConfig().isBlastMiningBonusDropsEnabled()) {
                        if (Probability.ofValue(0.5F).evaluate()) {
                            for (int i = 1; i < dropMultiplier; i++) {
                                ItemUtils.spawnItems(getPlayer(),
                                        getBlockCenter(block),
                                        oreDrops,
                                        BLAST_MINING_BLACKLIST,
                                        ItemSpawnReason.BLAST_MINING_ORES_BONUS_DROP);
                            }
                        }
                    }
                }
                currentOreYield = Math.max(currentOreYield - 1, 0);
            }
        }

        // Replace the event blocklist with the newYield list
        event.setYield(0F);
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
    }

    /**
     * Checks if it would be illegal (in vanilla) to obtain the block Certain things should never
     * drop (such as budding_amethyst, infested blocks or spawners)
     *
     * @param material target material
     * @return true if it's not legal to get the block through normal gameplay
     */
    public boolean isDropIllegal(@NotNull Material material) {
        String key = material.name().toLowerCase(Locale.ROOT);
        return isInfestedBlock(key)
                || key.equalsIgnoreCase(BUDDING_AMETHYST)
                || material == Material.SPAWNER;
    }

    /**
     * Increases the blast radius of the explosion.
     *
     * @param radius to modify
     * @return modified radius
     */
    public float biggerBombs(float radius) {
        return (float) (radius + getBlastRadiusModifier());
    }

    public double processDemolitionsExpertise(double damage) {
        return damage * ((100.0D - getBlastDamageModifier()) / 100.0D);
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public int getBlastMiningTier() {
        return RankUtils.getRank(getPlayer(), SubSkillType.MINING_BLAST_MINING);
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public float getOreBonus() {
        return (float) (mcMMO.p.getAdvancedConfig().getOreBonus(getBlastMiningTier()) / 100F);
    }

    @Deprecated
    public static double getOreBonus(int rank) {
        return mcMMO.p.getAdvancedConfig().getOreBonus(rank);
    }

    public static double getDebrisReduction(int rank) {
        return mcMMO.p.getAdvancedConfig().getDebrisReduction(rank);
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public double getDebrisReduction() {
        return getDebrisReduction(getBlastMiningTier());
    }

    public static int getDropMultiplier(int rank) {
        return mcMMO.p.getAdvancedConfig().getDropMultiplier(rank);
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public int getDropMultiplier() {
        if (!mcMMO.p.getAdvancedConfig().isBlastMiningBonusDropsEnabled()) {
            return 0;
        }

        int tier = getBlastMiningTier();
        switch (tier) {
            case 8:
            case 7:
                return 3;
            case 6:
            case 5:
            case 4:
            case 3:
                return 2;
            case 2:
            case 1:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public double getBlastRadiusModifier() {
        return BlastMining.getBlastRadiusModifier(getBlastMiningTier());
    }

    /**
     * Gets the Blast Mining tier
     *
     * @return the Blast Mining tier
     */
    public double getBlastDamageModifier() {
        return BlastMining.getBlastDamageDecrease(getBlastMiningTier());
    }

    private boolean blastMiningCooldownOver() {
        int timeRemaining = mmoPlayer.calculateTimeRemaining(SuperAbilityType.BLAST_MINING);

        if (timeRemaining > 0) {
            //getPlayer().sendMessage(LocaleLoader.getString("Skills.TooTired", timeRemaining));
            NotificationManager.sendPlayerInformation(getPlayer(),
                    NotificationType.ABILITY_COOLDOWN, "Skills.TooTired",
                    String.valueOf(timeRemaining));
            return false;
        }

        return true;
    }
}
