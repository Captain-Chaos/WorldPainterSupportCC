package io.github.opencubicchunks.worldpainterplugin;

import static org.pepsoft.minecraft.Constants.DATA_VERSION_MC_1_12_2;
import static org.pepsoft.minecraft.Constants.TAG_ALLOW_COMMANDS_;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_CENTER_X;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_CENTER_Z;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_DAMAGE_PER_BLOCK;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_SAFE_ZONE;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_SIZE;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_SIZE_LERP_TARGET;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_SIZE_LERP_TIME;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_WARNING_BLOCKS;
import static org.pepsoft.minecraft.Constants.TAG_BORDER_WARNING_TIME;
import static org.pepsoft.minecraft.Constants.TAG_DATA;
import static org.pepsoft.minecraft.Constants.TAG_DATA_VERSION;
import static org.pepsoft.minecraft.Constants.TAG_DIFFICULTY;
import static org.pepsoft.minecraft.Constants.TAG_DIFFICULTY_LOCKED;
import static org.pepsoft.minecraft.Constants.TAG_GAME_TYPE;
import static org.pepsoft.minecraft.Constants.TAG_GENERATOR_NAME_;
import static org.pepsoft.minecraft.Constants.TAG_GENERATOR_OPTIONS_;
import static org.pepsoft.minecraft.Constants.TAG_GENERATOR_VERSION_;
import static org.pepsoft.minecraft.Constants.TAG_HARDCORE_;
import static org.pepsoft.minecraft.Constants.TAG_LAST_PLAYED;
import static org.pepsoft.minecraft.Constants.TAG_LEVEL_NAME;
import static org.pepsoft.minecraft.Constants.TAG_MAP_FEATURES;
import static org.pepsoft.minecraft.Constants.TAG_MAP_HEIGHT;
import static org.pepsoft.minecraft.Constants.TAG_RANDOM_SEED;
import static org.pepsoft.minecraft.Constants.TAG_SPAWN_X;
import static org.pepsoft.minecraft.Constants.TAG_SPAWN_Y;
import static org.pepsoft.minecraft.Constants.TAG_SPAWN_Z;
import static org.pepsoft.minecraft.Constants.TAG_TIME;
import static org.pepsoft.minecraft.Constants.TAG_VERSION;
import static org.pepsoft.minecraft.Constants.TAG_VERSION_;
import static org.pepsoft.minecraft.Constants.VERSION_ANVIL;

import org.jnbt.CompoundTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTOutputStream;
import org.jnbt.Tag;
import org.pepsoft.minecraft.AbstractNBTItem;
import org.pepsoft.minecraft.Dimension;
import org.pepsoft.worldpainter.AccessDeniedException;
import org.pepsoft.worldpainter.Generator;
import org.pepsoft.worldpainter.Platform;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// a copy of Level modified to support cubic chunks worlds
public class CubicLevel extends AbstractNBTItem {

    public CubicLevel(int mapHeight, Platform platform) {
        super(new CompoundTag(TAG_DATA, new HashMap<>()));
        if (!platform.equals(CubicChunksPlatformProvider.CUBICCHUNKS)) {
            throw new IllegalArgumentException("Not a supported platform: " + platform);
        }
        if (mapHeight < 256) {
            throw new IllegalArgumentException("mapHeight " + mapHeight + " can't be lower than 256");
        }
        setBoolean("isCubicWorld", true);
        this.maxHeight = mapHeight;
        extraTags = null;
        setInt(TAG_VERSION_, VERSION_ANVIL);
        setInt(TAG_DATA_VERSION, DATA_VERSION_MC_1_12_2);
        addDimension(0);
    }

    public CubicLevel(CompoundTag tag, int mapHeight) {
        super((CompoundTag) tag.getTag(TAG_DATA));
        if ((mapHeight & (mapHeight - 1)) != 0) {
            throw new IllegalArgumentException("mapHeight " + mapHeight + " not a power of two");
        }
        int version = getInt(TAG_VERSION_);
        if (version != VERSION_ANVIL) {
            throw new IllegalArgumentException("Not a supported version: 0x" + Integer.toHexString(version));
        }
        this.maxHeight = mapHeight;
        if (tag.getValue().size() == 1) {
            // No extra tags
            extraTags = null;
        } else {
            // The root tag contains extra tags, most likely from mods. Preserve them (but filter out the data tag)
            extraTags = new HashSet<>();
            tag.getValue().values().stream()
                    .filter(extraTag -> !extraTag.getName().equals(TAG_DATA))
                    .forEach(extraTags::add);
        }
        addDimension(0);
    }

    public void save(File worldDir) throws IOException {
        if (!worldDir.exists()) {
            if (!worldDir.mkdirs()) {
                throw new AccessDeniedException("Could not create directory " + worldDir);
            }
        }

        // Write session.lock file
        File sessionLockFile = new File(worldDir, "session.lock");
        try (DataOutputStream sessionOut = new DataOutputStream(new FileOutputStream(sessionLockFile))) {
            sessionOut.writeLong(System.currentTimeMillis());
        }

        // Write level.dat file
        File levelDatFile = new File(worldDir, "level.dat");
        // Make it show at the top of the single player map list:
        setLong(TAG_LAST_PLAYED, System.currentTimeMillis());
        try (NBTOutputStream out = new NBTOutputStream(new GZIPOutputStream(new FileOutputStream(levelDatFile)))) {
            out.writeTag(toNBT());
        }
    }

    public Dimension getDimension(int dim) {
        return dimensions.get(dim);
    }

    public void addDimension(int dim) {
        if (dimensions.containsKey(dim)) {
            throw new IllegalStateException("Dimension " + dim + " already exists");
        } else {
            dimensions.put(dim, new Dimension(dim, maxHeight));
        }
    }

    public Dimension removeDimension(int dim) {
        return dimensions.remove(dim);
    }

    public String getName() {
        return getString(TAG_LEVEL_NAME);
    }

    public long getSeed() {
        return getLong(TAG_RANDOM_SEED);
    }

    public int getSpawnX() {
        return getInt(TAG_SPAWN_X);
    }

    public int getSpawnY() {
        return getInt(TAG_SPAWN_Y);
    }

    public int getSpawnZ() {
        return getInt(TAG_SPAWN_Z);
    }

    public long getTime() {
        return getLong(TAG_TIME);
    }

    public int getVersion() {
        return getInt(TAG_VERSION);
    }

    /**
     * 1.12.2: 1343
     * 18w11a: 1478
     */
    public int getDataVersion() {
        return getInt(TAG_DATA_VERSION);
    }

    public boolean isMapFeatures() {
        return getBoolean(TAG_MAP_FEATURES);
    }

    public int getMapHeight() {
        return getInt(TAG_MAP_HEIGHT);
    }

    public int getGameType() {
        return getInt(TAG_GAME_TYPE);
    }

    public boolean isHardcore() {
        return getBoolean(TAG_HARDCORE_);
    }

    public String getGeneratorName() {
        return getString(TAG_GENERATOR_NAME_);
    }

    public int getGeneratorVersion() {
        return getInt(TAG_GENERATOR_VERSION_);
    }

    public Generator getGenerator() {
        if ("FLAT".equals(getGeneratorName()) || "flat".equals(getGeneratorName())) {
            return Generator.FLAT;
        } else if ("largeBiomes".equals(getGeneratorName())) {
            return Generator.LARGE_BIOMES;
        } else if ("DEFAULT".equals(getGeneratorName()) || "default".equals(getGeneratorName())) {
            return Generator.DEFAULT;
        } else {
            return Generator.CUSTOM;
        }
    }

    public String getGeneratorOptions() {
        return getString(TAG_GENERATOR_OPTIONS_);
    }

    public boolean isAllowCommands() {
        return getBoolean(TAG_ALLOW_COMMANDS_);
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getDifficulty() {
        return getByte(TAG_DIFFICULTY);
    }

    public boolean isDifficultyLocked() {
        return getBoolean(TAG_DIFFICULTY_LOCKED);
    }

    public double getBorderCenterX() {
        return getDouble(TAG_BORDER_CENTER_X);
    }

    public double getBorderCenterZ() {
        return getDouble(TAG_BORDER_CENTER_Z);
    }

    public double getBorderSize() {
        return getDouble(TAG_BORDER_SIZE);
    }

    public double getBorderSafeZone() {
        return getDouble(TAG_BORDER_SAFE_ZONE);
    }

    public double getBorderWarningBlocks() {
        return getDouble(TAG_BORDER_WARNING_BLOCKS);
    }

    public double getBorderWarningTime() {
        return getDouble(TAG_BORDER_WARNING_TIME);
    }

    public double getBorderSizeLerpTarget() {
        return getDouble(TAG_BORDER_SIZE_LERP_TARGET);
    }

    public long getBorderSizeLerpTime() {
        return getLong(TAG_BORDER_SIZE_LERP_TIME);
    }

    public double getBorderDamagePerBlock() {
        return getDouble(TAG_BORDER_DAMAGE_PER_BLOCK);
    }

    public void setName(String name) {
        setString(TAG_LEVEL_NAME, name);
    }

    public void setSeed(long seed) {
        setLong(TAG_RANDOM_SEED, seed);
    }

    public void setSpawnX(int spawnX) {
        setInt(TAG_SPAWN_X, spawnX);
    }

    public void setSpawnY(int spawnY) {
        setInt(TAG_SPAWN_Y, spawnY);
    }

    public void setSpawnZ(int spawnZ) {
        setInt(TAG_SPAWN_Z, spawnZ);
    }

    public void setTime(long time) {
        setLong(TAG_TIME, time);
    }

    public void setMapFeatures(boolean mapFeatures) {
        setBoolean(TAG_MAP_FEATURES, mapFeatures);
    }

    public void setGameType(int gameType) {
        setInt(TAG_GAME_TYPE, gameType);
    }

    public void setHardcore(boolean hardcore) {
        setBoolean(TAG_HARDCORE_, hardcore);
    }

    public void setGeneratorName(String generatorName) {
        setString(TAG_GENERATOR_NAME_, generatorName);
    }

    public void setGenerator(Generator generator) {
        switch (generator) {
            case DEFAULT:
                setString(TAG_GENERATOR_NAME_, "default");
                setInt(TAG_GENERATOR_VERSION_, 1);
                break;
            case FLAT:
                setString(TAG_GENERATOR_NAME_, "flat");
                break;
            case LARGE_BIOMES:
                setString(TAG_GENERATOR_NAME_, "largeBiomes");
                setInt(TAG_GENERATOR_VERSION_, 0);
                break;
            default:
                throw new IllegalArgumentException("Use setGeneratorName(String) for generator " + generator);
        }
    }

    public void setGeneratorOptions(String generatorOptions) {
        setString(TAG_GENERATOR_OPTIONS_, generatorOptions);
    }

    public void setAllowCommands(boolean allowCommands) {
        setBoolean(TAG_ALLOW_COMMANDS_, allowCommands);
    }

    public void setDifficulty(int difficulty) {
        setByte(TAG_DIFFICULTY, (byte) difficulty);
    }

    public void setDifficultyLocked(boolean difficultyLocked) {
        setBoolean(TAG_DIFFICULTY_LOCKED, difficultyLocked);
    }

    public void setBorderCenterX(double borderCenterX) {
        setDouble(TAG_BORDER_CENTER_X, borderCenterX);
    }

    public void setBorderCenterZ(double borderCenterZ) {
        setDouble(TAG_BORDER_CENTER_Z, borderCenterZ);
    }

    public void setBorderSize(double borderSize) {
        setDouble(TAG_BORDER_SIZE, borderSize);
    }

    public void setBorderSafeZone(double borderSafeZone) {
        setDouble(TAG_BORDER_SAFE_ZONE, borderSafeZone);
    }

    public void setBorderWarningBlocks(double borderWarningBlocks) {
        setDouble(TAG_BORDER_WARNING_BLOCKS, borderWarningBlocks);
    }

    public void setBorderWarningTime(double borderWarningTime) {
        setDouble(TAG_BORDER_WARNING_TIME, borderWarningTime);
    }

    public void setBorderSizeLerpTarget(double borderSizeLerpTarget) {
        setDouble(TAG_BORDER_SIZE_LERP_TARGET, borderSizeLerpTarget);
    }

    public void setBorderSizeLerpTime(long borderSizeLerpTime) {
        setLong(TAG_BORDER_SIZE_LERP_TIME, borderSizeLerpTime);
    }

    public void setBorderDamagePerBlock(double borderDamagePerBlock) {
        setDouble(TAG_BORDER_DAMAGE_PER_BLOCK, borderDamagePerBlock);
    }

    @Override
    public CompoundTag toNBT() {
        Map<String, Tag> values = new HashMap<>();
        values.put(TAG_DATA, super.toNBT());
        if (extraTags != null) {
            for (Tag extraTag : extraTags) {
                values.put(extraTag.getName(), extraTag);
            }
        }
        return new CompoundTag("", values);
    }

    public static CubicLevel load(File levelDatFile) throws IOException {
        Tag tag;
        try (NBTInputStream in = new NBTInputStream(new GZIPInputStream(new FileInputStream(levelDatFile)))) {
            tag = in.readTag();
        }
        return new CubicLevel((CompoundTag) tag, Integer.MAX_VALUE / 2);
    }

    private final int maxHeight;
    private final Map<Integer, Dimension> dimensions = new HashMap<>();
    private final Set<Tag> extraTags;

    private static final long serialVersionUID = 1L;
}
