# API notes — Minecraft 1.21.11 / NeoForge 21.11.45

Every signature below was copied out of a source file that was actually opened.
Nothing here is from memory or from a tutorial.

**Source of truth** — the NeoForge-patched Minecraft sources:

```
~/.gradle/caches/neoformruntime/intermediate_results/
    sourcesWithNeoForge_91dc011ba160e1ec89fa4e03aca19bd32ce6312d_output.zip
```

Unzip it anywhere; all `source:` paths below are relative to the root of that
archive. It contains both `net/minecraft/**` and `net/neoforged/**`.
Vanilla assets (blockstate/model JSON) come from
`~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.11_client.jar`.

---

## A1 neighbour-change / canSurvive

```java
// the hook that reacts to a neighbour and may destroy the block
protected BlockState updateShape(
    BlockState state,
    LevelReader level,
    ScheduledTickAccess scheduledTickAccess,
    BlockPos pos,
    Direction direction,
    BlockPos neighborPos,
    BlockState neighborState,
    RandomSource random
)

// the support test
protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)

// the *other* neighbour hook — redstone-style, does not return a state
protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                               Block neighborBlock, @Nullable Orientation orientation,
                               boolean movedByPiston)
```

source: `net/minecraft/world/level/block/state/BlockBehaviour.java:166` (updateShape),
`:341` (canSurvive), `:183` (neighborChanged)

notes: A carpet-like block overrides **`updateShape` + `canSurvive`**, not
`neighborChanged`. `CarpetBlock`
(`net/minecraft/world/level/block/CarpetBlock.java:33`) is the minimal template —
verbatim:

```java
@Override
protected BlockState updateShape(BlockState p_152926_, LevelReader p_374550_,
        ScheduledTickAccess p_374188_, BlockPos p_152930_, Direction p_152927_,
        BlockPos p_152931_, BlockState p_152928_, RandomSource p_374375_) {
    return !p_152926_.canSurvive(p_374550_, p_152930_)
        ? Blocks.AIR.defaultBlockState()
        : super.updateShape(p_152926_, p_374550_, p_374188_, p_152930_, p_152927_,
                            p_152931_, p_152928_, p_374375_);
}

@Override
protected boolean canSurvive(BlockState p_152922_, LevelReader p_152923_, BlockPos p_152924_) {
    return !p_152923_.isEmptyBlock(p_152924_.below());
}
```

`SnowLayerBlock` (`net/minecraft/world/level/block/SnowLayerBlock.java:78/97`)
does the identical `updateShape`, only with a richer `canSurvive` (tags
`SNOW_LAYER_CANNOT_SURVIVE_ON` / `SNOW_LAYER_CAN_SURVIVE_ON` plus
`Block.isFaceFull(...)`).

The chain that makes this work: `Level.neighborShapeChanged` →
`NeighborUpdater.executeShapeUpdate` → `blockstate.updateShape(...)` →
`Block.updateOrDestroy(old, new, level, pos, flags, recursionLeft)`; returning
`Blocks.AIR.defaultBlockState()` makes `updateOrDestroy` call
`level.destroyBlock(pos, dropItems, null, recursionLeft)`.
source: `net/minecraft/world/level/redstone/NeighborUpdater.java:34-48`,
`net/minecraft/world/level/block/Block.java:226-242`

Convenience delegates on `BlockState` (for calling, not overriding):
`public BlockState updateShape(LevelReader, ScheduledTickAccess, BlockPos, Direction, BlockPos, BlockState, RandomSource)`
(`BlockBehaviour.java:874`) and `public boolean canSurvive(LevelReader, BlockPos)`
(`BlockBehaviour.java:902`).

---

## A2 entityInside

```java
protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                            InsideBlockEffectApplier applier, boolean intersects)
```

source: `net/minecraft/world/level/block/state/BlockBehaviour.java:415`

The two extra parameters versus older versions:

* `InsideBlockEffectApplier applier` — deferred effects
  (`applier.apply(InsideBlockEffectType.FREEZE)`, `applier.runBefore(type, consumer)`).
* `boolean intersects` — `true` when the entity's (deflated) AABB really overlaps
  this block position, `false` when the block was only swept through during the
  movement step. `BubbleColumnBlock` guards its whole body with `if (intersects)`.

**Side:** runs on **both** sides. Call site is
`Entity.checkInsideBlocks(...)` → `blockstate.entityInside(this.level(), pos, this, stepBasedCollector, flag4)`
(`net/minecraft/world/entity/Entity.java:1244`), which is the generic movement
path executed on client and server alike. Guard yourself.

Vanilla block that spawns particles there and how it guards:
`net/minecraft/world/level/block/PowderSnowBlock.java:61-82`

```java
@Override
protected void entityInside(BlockState p_154263_, Level p_154264_, BlockPos p_154265_,
        Entity p_154266_, InsideBlockEffectApplier p_405853_, boolean p_451759_) {
    if (!(p_154266_ instanceof LivingEntity) || p_154266_.getInBlockState().is(this)) {
        p_154266_.makeStuckInBlock(p_154263_, new Vec3(0.9F, 1.5, 0.9F));
        if (p_154264_.isClientSide()) {                    // <- the side guard
            RandomSource randomsource = p_154264_.getRandom();
            ...
            p_154264_.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, dx, dy, dz);
        }
    }
    ...
}
```

The mirror image (server-only work) is `EyeblossomBlock`
(`net/minecraft/world/level/block/EyeblossomBlock.java:104`): `if (!level.isClientSide() && ...)`.

`Level.addParticle` is client-only in effect; for server-driven particles see A4.

---

## A3 BlockBehaviour.Properties

All in `net/minecraft/world/level/block/state/BlockBehaviour.java`, inner class
`Properties` starting at line 1062.

| want | exact method | line |
|---|---|---|
| no collision | `public BlockBehaviour.Properties noCollision()` | 1179 |
| instant break | `public BlockBehaviour.Properties instabreak()` | 1219 |
| sound type | `public BlockBehaviour.Properties sound(SoundType soundType)` | 1205 |
| no occlusion | `public BlockBehaviour.Properties noOcclusion()` | 1185 |
| push reaction | `public BlockBehaviour.Properties pushReaction(PushReaction pushReaction)` | 1273 |
| map colour | `public BlockBehaviour.Properties mapColor(MapColor mapColor)` | 1169 |
| map colour (dye) | `public BlockBehaviour.Properties mapColor(DyeColor mapColor)` | 1164 |
| map colour (per state) | `public BlockBehaviour.Properties mapColor(Function<BlockState, MapColor> mapColor)` | 1174 |

**The typo is `instabreak()`** — lowercase `b`, not `instaBreak()`. It is just
`return this.strength(0.0F);`.

The historical `noCollission()` (double s) **no longer exists** in 1.21.11; a
grep for `noCollission` over the whole source tree returns nothing. It is
`noCollision()`. Note that `noCollision()` also clears `canOcclude`, so calling
`noOcclusion()` after it is redundant.

Factories: `public static BlockBehaviour.Properties of()` (:1115),
`ofFullCopy(BlockBehaviour)` (:1119), `ofLegacyCopy(BlockBehaviour)` (:1134).
Also present and often needed: `strength(float)` / `strength(float,float)`,
`randomTicks()`, `noLootTable()`, `replaceable()`, `noTerrainParticles()`,
`setId(ResourceKey<Block>)`.

---

## A4 server-side block particles

```java
// ServerLevel — the short overload
public <T extends ParticleOptions> int sendParticles(
    T type, double posX, double posY, double posZ,
    int particleCount, double xOffset, double yOffset, double zOffset, double speed)

// full overload
public <T extends ParticleOptions> int sendParticles(
    T type, boolean overrideLimiter, boolean alwaysShow,
    double posX, double posY, double posZ,
    int particleCount, double xOffset, double yOffset, double zOffset, double speed)

// single-player variant
public <T extends ParticleOptions> boolean sendParticles(
    ServerPlayer player, T particle, boolean overrideLimiter, boolean alwaysShow,
    double posX, double posY, double posZ,
    int count, double xDist, double yDist, double zDist, double maxSpeed)
```

source: `net/minecraft/server/level/ServerLevel.java:1356`, `:1362`, `:1390`

```java
// BlockParticleOption — two constructors
public BlockParticleOption(ParticleType<BlockParticleOption> type, BlockState state)
public BlockParticleOption(ParticleType<BlockParticleOption> type, BlockState state,
                           @Nullable BlockPos pos)   // Neo addition: model data for texture selection
```

source: `net/minecraft/core/particles/BlockParticleOption.java:37`, `:52`

Particle types that take a `BlockParticleOption`
(`net/minecraft/core/particles/ParticleTypes.java`): `BLOCK` (:15),
`BLOCK_MARKER` (:16), `FALLING_DUST` (:53), `DUST_PILLAR` (:147),
`BLOCK_CRUMBLE` (:153).

Vanilla usage, `net/minecraft/world/entity/LivingEntity.java:396`:

```java
serverlevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state, pos),
                          d0, d1, d2, i, 0.0, 0.0, 0.0, 0.15F);
```

Prefer the three-argument constructor with the `BlockPos` — NeoForge uses it to
pick the right texture from model data.

---

## A5 BooleanProperty and createBlockStateDefinition

```java
public static BooleanProperty create(String name)
```

source: `net/minecraft/world/level/block/state/properties/BooleanProperty.java:21`
(the constructor is private; `create` is the only way in)

```java
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
```

source: `net/minecraft/world/level/block/Block.java:568`

Pattern (`net/minecraft/world/level/block/TrapDoorBlock.java:173`):

```java
public static final BooleanProperty MY_FLAG = BooleanProperty.create("my_flag");

public MyBlock(BlockBehaviour.Properties props) {
    super(props);
    this.registerDefaultState(this.stateDefinition.any().setValue(MY_FLAG, false));
}

@Override
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(MY_FLAG);
}
```

`Block.java:247` shows the builder being handed to this method from the `Block`
constructor, so the property constants must be `static final`.

---

## A6 PoiType

```java
public record PoiType(Set<BlockState> matchingStates, int maxTickets, int validRange) {
    public static final Predicate<Holder<PoiType>> NONE = p_218041_ -> false;
    public boolean is(BlockState state) { return this.matchingStates.contains(state); }
}
```

source: `net/minecraft/world/entity/ai/village/poi/PoiType.java:8`

The matching blockstates are simply the first record component. Vanilla builds
them with

```java
private static Set<BlockState> getBlockStates(Block block) {
    return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
}
```
source: `net/minecraft/world/entity/ai/village/poi/PoiTypes.java:84`

NeoForge replaces the set with a `PoiStateSet` inside the canonical constructor
(`PoiType.java:11-15`), so pass any `Set<BlockState>`.

**Registry key:** `Registries.POINT_OF_INTEREST_TYPE`
(`net/minecraft/core/registries/Registries.java:213`,
`ResourceKey<Registry<PoiType>>`).

DeferredRegister factory:
`public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace)`
(`net/neoforged/neoforge/registries/DeferredRegister.java:116`)

```java
public static final DeferredRegister<PoiType> POI_TYPES =
        DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Moleverse.MOD_ID);

public static final DeferredHolder<PoiType, PoiType> MOLE_MOUND = POI_TYPES.register(
        "mole_mound",
        () -> new PoiType(
                ImmutableSet.copyOf(ModBlocks.MOLE_MOUND.get().getStateDefinition().getPossibleStates()),
                1, 1));
```

Ordering is safe: `RegisterEvent` fires in `BuiltInRegistries` declaration order
(`GameData.getRegistrationOrder()`,
`net/neoforged/neoforge/registries/GameData.java:118`), and
`BuiltInRegistries.BLOCK` is declared at line 175 while
`POINT_OF_INTEREST_TYPE` is at line 208 — blocks exist by then.

**You do not call `registerBlockStates` yourself.** NeoForge fills the
blockstate→POI map automatically for modded types via
`NeoForgeRegistryCallbacks.PoiTypeCallbacks.onAdd`
(`net/neoforged/neoforge/registries/NeoForgeRegistryCallbacks.java:107`), which
is registered in `NeoForgeRegistriesSetup.java:86`. The comment in
`PoiTypes.registerBlockStates` says so explicitly.

AE2 does the same thing the old way (`RegisterEvent` + `Registry.register`), see
`_reference/Applied-Energistics-2/src/main/java/appeng/init/InitVillager.java:38-56`.

---

## A7 PoiManager queries

Get it from the level:

```java
public PoiManager getPoiManager() { return this.getChunkSource().getPoiManager(); }
```
source: `net/minecraft/server/level/ServerLevel.java:1577`

Query methods (all in `net/minecraft/world/entity/ai/village/poi/PoiManager.java`):

```java
public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> typePredicate,
        BlockPos pos, int distance, PoiManager.Occupancy status)                     // :95

public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate,
        BlockPos pos, int distance, PoiManager.Occupancy status)                     // :87

public Stream<BlockPos> findAll(Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> posPredicate, BlockPos pos, int distance,
        PoiManager.Occupancy status)                                                 // :109

public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate,
        BlockPos pos, int distance, PoiManager.Occupancy status)                     // :136

public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> posPredicate, BlockPos pos, int distance,
        PoiManager.Occupancy status)                                                 // :150

public long getCountInRange(Predicate<Holder<PoiType>> typePredicate,
        BlockPos pos, int distance, PoiManager.Occupancy status)                     // :79

public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(...)      // :123

public boolean existsAtPosition(ResourceKey<PoiType> type, BlockPos pos)             // :83
public Optional<Holder<PoiType>> getType(BlockPos pos)                               // :191
public @Nullable PoiRecord add(BlockPos pos, Holder<PoiType> type)                   // :71
public void remove(BlockPos pos)                                                     // :75
```

**"All POIs of this type within radius R"** is `getInRange` — `getInSquare`
filtered by `record.getPos().distSqr(pos) <= distance * distance`, so `distance`
is a true Euclidean radius in blocks.

```java
public static enum Occupancy {
    HAS_SPACE(PoiRecord::hasSpace),
    IS_OCCUPIED(PoiRecord::isOccupied),
    ANY(p_27223_ -> true);
    public Predicate<? super PoiRecord> getTest();
}
```
source: `PoiManager.java:306`

`PoiRecord`: `public BlockPos getPos()` (:68), `public Holder<PoiType> getPoiType()`
(:72), `hasSpace()` (:60), `isOccupied()` (:64) —
`net/minecraft/world/entity/ai/village/poi/PoiRecord.java`.

Caveat: the query only sees POI sections that are already loaded. Force them
with

```java
public void ensureLoadedAndValid(LevelReader levelReader, BlockPos pos, int coordinateOffset)
```
source: `PoiManager.java:263`

`ServerLevel` keeps the POI store in sync automatically through
`updatePOIOnBlockStateChange(BlockPos, BlockState oldState, BlockState newState)`
(`ServerLevel.java:1559`) — placing/breaking the block is enough, no manual
`add`/`remove` needed.

---

## A8 does a POI type need anything else

`maxTickets` and `validRange` are the two remaining record components; both are
required, both are plain ints.

* `maxTickets` — how many mobs may claim this POI at once. Checked in
  `PoiRecord.acquireTicket` / `hasSpace` / `isOccupied`
  (`PoiRecord.java:27, 51, 65`). **`0` means the POI can never be claimed** —
  that is what beehives, nether portals and lodestones use, i.e. POIs that are
  only ever *located*, never occupied. If your moles only want to *find* mounds,
  `0` is the right value; a mound that one mole reserves wants `1`.
* `validRange` — pathfinding distance the AI is allowed to use when walking to
  the POI. Read in `AcquirePoi.java:129`, `VillagerMakeLove.java:103`,
  `YieldJobSite.java:83` as `poiType.value().validRange()`. Vanilla uses `1`
  everywhere except `MEETING` (the bell), which uses `6`.

Vanilla registration, `net/minecraft/world/entity/ai/village/poi/PoiTypes.java:121-144`:

```java
public static PoiType bootstrap(Registry<PoiType> registry) {
    register(registry, ARMORER,   getBlockStates(Blocks.BLAST_FURNACE), 1, 1);
    ...
    register(registry, HOME,      BEDS,                                 1, 1);
    register(registry, MEETING,   getBlockStates(Blocks.BELL),         32, 6);
    register(registry, BEEHIVE,   getBlockStates(Blocks.BEEHIVE),       0, 1);
    register(registry, NETHER_PORTAL, getBlockStates(Blocks.NETHER_PORTAL), 0, 1);
    return register(registry, LIGHTNING_ROD, LIGHTNING_RODS,            0, 1);
}
```

Nothing else is needed. Tags (`PoiTypeTags.ACQUIRABLE_JOB_SITE` etc.) only
matter for villager professions.

---

## A9 damage entry point / isInvulnerableTo

```java
// Entity — abstract, this IS the entry point
public abstract boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount);

// Entity — client-side counterpart, default false
public boolean hurtClient(DamageSource damageSource);

// Entity — deprecated convenience wrappers
@Deprecated public final void hurt(DamageSource damageSource, float amount);
@Deprecated public final boolean hurtOrSimulate(DamageSource damageSource, float amount);
```
source: `net/minecraft/world/entity/Entity.java:1868`, `:1870`, `:1857`, `:1864`

Yes — `hurtServer(ServerLevel, DamageSource, float)` is correct.
`LivingEntity` implements it at
`net/minecraft/world/entity/LivingEntity.java:1221`:
`public boolean hurtServer(ServerLevel p_376221_, DamageSource p_376460_, float p_376610_)`.

```java
// LivingEntity
public boolean isInvulnerableTo(ServerLevel level, DamageSource damageSource)
```
source: `net/minecraft/world/entity/LivingEntity.java:4062`

**`isInvulnerableTo` is declared on `LivingEntity`, not on `Entity`.** `Entity`
only has

```java
protected final boolean isInvulnerableToBase(DamageSource damageSource)
```
source: `net/minecraft/world/entity/Entity.java:3041` — `final`, so it cannot be
overridden; it is the one that consults `this.invulnerable`, the fire/fall tags
and `CommonHooks.isEntityInvulnerableTo`.

Overriders to copy: `Warden.java:152`, `Ghast.java:86`, `Breeze.java:267`,
`Player.java:719` — all `public boolean isInvulnerableTo(ServerLevel, DamageSource)`.

---

## A10 setInvulnerable persists to NBT

Yes. The flag is the plain `Entity.invulnerable` field and it is written and
read in the base save/load code, not in `addAdditionalSaveData`:

```java
public static final String TAG_INVULNERABLE = "Invulnerable";                // :173

// Entity.saveWithoutId(ValueOutput output)
output.putBoolean("Invulnerable", this.invulnerable);                        // :2029

// Entity.load(ValueInput input)
this.invulnerable = input.getBooleanOr("Invulnerable", false);               // :2112

public boolean isInvulnerable()                    { return this.invulnerable; }   // :3049
public void setInvulnerable(boolean isInvulnerable){ this.invulnerable = isInvulnerable; } // :3056
```
source: `net/minecraft/world/entity/Entity.java`

Note the NBT layer is `ValueOutput` / `ValueInput`
(`net.minecraft.world.level.storage.*`), not `CompoundTag`, in this version.

---

## A11 noPhysics

```java
public boolean noPhysics;
```
source: `net/minecraft/world/entity/Entity.java:232` — a **public field**, not a
method. No getter or setter exists.

Effect, `Entity.move`:

```java
public void move(MoverType type, Vec3 movement) {
    if (this.noPhysics) {
        this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        this.horizontalCollision = false;
        this.verticalCollision = false;
        this.verticalCollisionBelow = false;
        this.minorHorizontalCollision = false;
    } else {
        ...
    }
}
```
source: `net/minecraft/world/entity/Entity.java:706-712`

So an entity moves ignoring collision simply by setting `this.noPhysics = true`
and calling `move(MoverType.SELF, delta)` — or by calling `setPos` directly.
`noPhysics` additionally makes `Entity.isPushable`-style checks fail
(`:908`, `:1807`) and disables suffocation (`:2194`) and portal handling (`:3429`).

**No vanilla digging mob uses this.** A grep for `noPhysics =` over the whole
tree returns exactly: `RemotePlayer:20`, `AreaEffectCloud:63`, `EnderDragon:101`,
`ArmorStand:166`, `Display:110`, `Interaction:43`, `ItemEntity:138/140`,
`Marker:17`, `Vex:73/75`, `OminousItemSpawner:34`, `Player:246` (spectator),
`AbstractArrow:743`, `ShulkerBullet:47`. Neither `Warden` nor `Sniffer` appears —
the Sniffer's digging is animation plus a block interaction, it never moves
through terrain. The closest behavioural model is `Vex`, which flips the field on
in its constructor and off again after `finalizeSpawn`-time setup.

---

## A12 EntityDataAccessor / synched data

```java
// the key type
public record EntityDataAccessor<T>(int id, EntityDataSerializer<T> serializer)
```
source: `net/minecraft/network/syncher/EntityDataAccessor.java:6`

```java
// creating a key
public static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder> clazz,
                                                 EntityDataSerializer<T> serializer)
```
source: `net/minecraft/network/syncher/SynchedEntityData.java:41`

```java
// the hook you override
protected abstract void defineSynchedData(SynchedEntityData.Builder builder);
```
source: `net/minecraft/world/entity/Entity.java:396`
(called from `Entity`'s constructor at `:300-309`)

```java
// the builder
public static class Builder {
    public Builder(SyncedDataHolder entity);                                  // :163
    public <T> SynchedEntityData.Builder define(EntityDataAccessor<T> accessor, T value); // :168
    public SynchedEntityData build();                                          // :182
}

// access at runtime
public <T> T get(EntityDataAccessor<T> key);                                   // :68
public <T> void set(EntityDataAccessor<T> key, T value);                       // :75
public <T> void set(EntityDataAccessor<T> key, T value, boolean force);        // :79
```
source: `net/minecraft/network/syncher/SynchedEntityData.java`

Serializers (`net/minecraft/network/syncher/EntityDataSerializers.java`):

| type | constant | line |
|---|---|---|
| `Integer` | `EntityDataSerializers.INT` | 53 |
| `Byte` | `EntityDataSerializers.BYTE` | 52 |
| `Boolean` | `EntityDataSerializers.BOOLEAN` | 87 |
| `Float` | `EntityDataSerializers.FLOAT` | 55 |
| `BlockPos` | `EntityDataSerializers.BLOCK_POS` | 93 |
| `Optional<BlockPos>` | `EntityDataSerializers.OPTIONAL_BLOCK_POS` | 94 |
| `BlockState` | `EntityDataSerializers.BLOCK_STATE` | 71 |
| `Optional<BlockState>` | `EntityDataSerializers.OPTIONAL_BLOCK_STATE` | 86 |
| `Direction` | `EntityDataSerializers.DIRECTION` | 97 |
| `Pose` | `EntityDataSerializers.POSE` | 116 |

Note there is **no `Long` alias problem** — `LONG` exists at :54.

Full pattern, verbatim from `net/minecraft/world/entity/animal/sniffer/Sniffer.java:71-96, 152-163`:

```java
private static final EntityDataAccessor<Sniffer.State> DATA_STATE =
        SynchedEntityData.defineId(Sniffer.class, EntityDataSerializers.SNIFFER_STATE);
private static final EntityDataAccessor<Integer> DATA_DROP_SEED_AT_TICK =
        SynchedEntityData.defineId(Sniffer.class, EntityDataSerializers.INT);

@Override
protected void defineSynchedData(SynchedEntityData.Builder p_326082_) {
    super.defineSynchedData(p_326082_);
    p_326082_.define(DATA_STATE, Sniffer.State.IDLING);
    p_326082_.define(DATA_DROP_SEED_AT_TICK, 0);
}

public Sniffer.State getState()          { return this.entityData.get(DATA_STATE); }
private Sniffer setState(Sniffer.State s){ this.entityData.set(DATA_STATE, s); return this; }

@Override
public void onSyncedDataUpdated(EntityDataAccessor<?> p_272936_) {   // client reaction hook
    if (DATA_STATE.equals(p_272936_)) { ... }
    super.onSyncedDataUpdated(p_272936_);
}
```

`public void onSyncedDataUpdated(EntityDataAccessor<?> key)` is at
`net/minecraft/world/entity/Entity.java:3403`.

---

## A13 ServerLevel "is this position ticking entities"

Both candidates exist, with different meanings:

```java
public boolean isPositionEntityTicking(BlockPos pos)                    // :1840
public boolean areEntitiesLoaded(long chunkPos)                         // :1832
public boolean isPositionTickingWithEntitiesLoaded(long chunkPos)       // :1836
public boolean areEntitiesActuallyLoadedAndTicking(ChunkPos chunkPos)   // :1844
```
source: `net/minecraft/server/level/ServerLevel.java`

The one you want is:

```java
public boolean isPositionEntityTicking(BlockPos pos) {
    return this.entityManager.canPositionTick(pos)
        && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(pos));
}
```

There is **no `isPositionTicking(BlockPos)` on `ServerLevel`** —
`isPositionTicking(long)` lives on `ServerChunkCache`
(used inside `isPositionTickingWithEntitiesLoaded`). Also available from
`Level`: `public boolean shouldTickBlocksAt(BlockPos pos)`
(`net/minecraft/world/level/Level.java:616`), which is the *block* ticking test,
not the entity one.

---

## A14 Mob and Leashable

```java
public abstract class Mob extends LivingEntity implements EquipmentUser, Leashable, Targeting
```
source: `net/minecraft/world/entity/Mob.java:95`

So **yes, every `Mob` is `Leashable` by default** in this version, and
`Leashable.canBeLeashed()` defaults to `true`.

```java
default boolean isLeashed() {
    return this.getLeashData() != null && this.getLeashData().leashHolder != null;
}
default boolean mayBeLeashed()  { return this.getLeashData() != null; }
default boolean canBeLeashed()  { return true; }
default @Nullable Entity getLeashHolder();
default void setLeashedTo(Entity leashHolder, boolean broadcastPacket);
default void dropLeash();
default void removeLeash();
```
source: `net/minecraft/world/entity/Leashable.java:47`, `:51`, `:67`, `:324`, `:296`, `:111`, `:115`

**"Currently leashed" is `isLeashed()`.** `mayBeLeashed()` is weaker — it is
`true` while leash data exists but the holder has not been resolved yet (e.g.
right after a chunk load, before `setDelayedLeashHolderId` resolves).

The two abstract members an implementor must provide are
`Leashable.@Nullable LeashData getLeashData()` and
`void setLeashData(Leashable.@Nullable LeashData leashData)` (`:43`, `:45`) —
`Mob` already implements them.

Also useful: `LEASH_TOO_FAR_DIST = 12.0`, `LEASH_ELASTIC_DIST = 6.0`,
`MAXIMUM_ALLOWED_LEASHED_DIST = 16.0` (`Leashable.java:30-32`).

---

## A15 isBaby

```java
// AgeableMob
public boolean isBaby()            // :156
public void setBaby(boolean baby)  // :161
```
source: `net/minecraft/world/entity/AgeableMob.java`

```java
// LivingEntity — the base declaration, returns false
public boolean isBaby()            // :560
```
source: `net/minecraft/world/entity/LivingEntity.java`

Call `livingEntity.isBaby()`; it is already virtual on `LivingEntity`, so no
cast to `AgeableMob`/`Animal` is needed. `Animal` inherits `AgeableMob`'s
implementation unchanged (no override in `Animal.java`).

---

## A16 entity load/save hooks

```java
// Entity — abstract, override both
protected abstract void readAdditionalSaveData(ValueInput input);    // :2160
protected abstract void addAdditionalSaveData(ValueOutput output);   // :2162

// Entity — base save/load, calls the two above
public void saveWithoutId(ValueOutput output);                       // :2015
public void load(ValueInput input);                                  // :2094

// NeoForge additions on Entity (from IEntityExtension)
public final boolean isAddedToLevel();                               // :4185
public void onAddedToLevel();                                        // :4188
public void onRemovedFromLevel();                                    // :4191
```
source: `net/minecraft/world/entity/Entity.java`;
interface at `net/neoforged/neoforge/common/extensions/IEntityExtension.java:110-120`

`onAddedToLevel()` is called from:
`net/minecraft/world/level/entity/PersistentEntitySectionManager.java:119, 126, 252`
(server, including the chunk-reload path),
`net/minecraft/server/level/ServerLevel.java:1013, 1025` and
`net/minecraft/client/multiplayer/ClientLevel.java:436`.

**Which one to use for fixing up a position after a chunk reload:**
`readAdditionalSaveData` runs while the entity is being deserialised — the level
is reachable via `this.level()` but the entity is not in the world yet and
surrounding chunks may not be loaded. `onAddedToLevel()` runs after the entity
has entered the level's ticking list, which is the correct place to touch
neighbouring blocks. Remember to call `super.onAddedToLevel()` — it sets the
`isAddedToLevel` flag.

Example of the save pair, `net/minecraft/world/entity/animal/bee/Bee.java:201-224`:

```java
@Override
protected void addAdditionalSaveData(ValueOutput p_478078_) {
    super.addAdditionalSaveData(p_478078_);
    p_478078_.storeNullable("hive_pos", BlockPos.CODEC, this.hivePos);
    p_478078_.putBoolean("HasNectar", this.hasNectar());
    p_478078_.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
}

@Override
protected void readAdditionalSaveData(ValueInput p_479060_) {
    super.readAdditionalSaveData(p_479060_);
    this.setHasNectar(p_479060_.getBooleanOr("HasNectar", false));
    this.ticksWithoutNectarSinceExitingHive = p_479060_.getIntOr("TicksSincePollination", 0);
    this.hivePos = p_479060_.read("hive_pos", BlockPos.CODEC).orElse(null);
}
```

The `ValueOutput`/`ValueInput` API: `putBoolean/putInt/putShort/putDouble/putString`,
`store(String, Codec<T>, T)`, `storeNullable(String, Codec<T>, T)`,
`getBooleanOr/getIntOr/getShortOr/getDoubleOr(String, default)`,
`read(String, Codec<T>) -> Optional<T>`, `childrenList(String)`.

Spawn-time hook, for completeness:

```java
@Deprecated @ApiStatus.OverrideOnly
public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
        DifficultyInstance difficulty, EntitySpawnReason spawnReason,
        @Nullable SpawnGroupData spawnGroupData)
```
source: `net/minecraft/world/entity/Mob.java:1092` — deprecated only because
callers must go through `EventHooks.finalizeMobSpawn`; overriding it is correct.

---

## A17 datagen: one property selecting models + four random Y rotations

The classes that actually exist:

| class | package | note |
|---|---|---|
| `MultiVariant` | `net.minecraft.client.data.models` | record of a `WeightedList<Variant>` |
| `Variant` | `net.minecraft.client.renderer.block.model` | record `(Identifier modelLocation, Variant.SimpleModelState modelState)` |
| `VariantMutator` | `net.minecraft.client.renderer.block.model` | `UnaryOperator<Variant>` |
| `MultiVariantGenerator` | `net.minecraft.client.data.models.blockstates` | the blockstate emitter |
| `PropertyDispatch<V>` | `net.minecraft.client.data.models.blockstates` | |
| `BlockModelGenerators` | `net.minecraft.client.data.models` | static helpers |

There is **no `withYRot` mutator method to call by hand** — use the ready-made
constants.

```java
// BlockModelGenerators — static helpers
public static Variant       plainModel(Identifier modelLocation)                    // :258
public static MultiVariant  variant(Variant variant)                                // :262
public static MultiVariant  variants(Variant... variants)                           // :266
public static MultiVariant  plainVariant(Identifier id)                             // :270
public static MultiVariant  createRotatedVariants(Variant variant)                  // :382
public static MultiVariant  createRotatedVariants(Variant variant, Variant mirroredVariant) // :386
public static PropertyDispatch<MultiVariant> createBooleanModelDispatch(
        BooleanProperty property, MultiVariant onTrue, MultiVariant onFalse)        // :390

// rotation mutators
public static final VariantMutator UV_LOCK   = VariantMutator.UV_LOCK.withValue(true);       // :122
public static final VariantMutator X_ROT_90  = VariantMutator.X_ROT.withValue(Quadrant.R90); // :123
public static final VariantMutator Y_ROT_90  = VariantMutator.Y_ROT.withValue(Quadrant.R90); // :126
public static final VariantMutator Y_ROT_180 = VariantMutator.Y_ROT.withValue(Quadrant.R180);// :127
public static final VariantMutator Y_ROT_270 = VariantMutator.Y_ROT.withValue(Quadrant.R270);// :128
```
source: `net/minecraft/client/data/models/BlockModelGenerators.java`

`createRotatedVariants` is literally the four-rotation helper:

```java
public static MultiVariant createRotatedVariants(Variant variant) {
    return variants(variant, variant.with(Y_ROT_90), variant.with(Y_ROT_180), variant.with(Y_ROT_270));
}
```

```java
// MultiVariantGenerator
public static MultiVariantGenerator.Empty dispatch(Block block)                       // :84
public static MultiVariantGenerator dispatch(Block block, MultiVariant variants)      // :88
public MultiVariantGenerator with(PropertyDispatch<VariantMutator> propertyDispatch)  // :45
public MultiVariantGenerator with(VariantMutator mutator)                             // :51
// on Empty:
public MultiVariantGenerator with(PropertyDispatch<MultiVariant> propertyDispatch)    // :100
```
source: `net/minecraft/client/data/models/blockstates/MultiVariantGenerator.java`

```java
// PropertyDispatch
public static <T1 extends Comparable<T1>> PropertyDispatch.C1<MultiVariant, T1> initial(Property<T1> property)   // :50
public static <T1 extends Comparable<T1>> PropertyDispatch.C1<VariantMutator, T1> modify(Property<T1> property)  // :78
// on C1:
public PropertyDispatch.C1<V, T1> select(T1 property, V value)   // :162
public PropertyDispatch<V> generate(Function<T1, V> generator)   // :168
```
source: `net/minecraft/client/data/models/blockstates/PropertyDispatch.java`

**The combination you asked for** — `initial(...)` gives `PropertyDispatch<MultiVariant>`,
and each selected value is a `MultiVariant` that may itself carry the four
rotations. Vanilla does exactly this for the sea pickle
(`BlockModelGenerators.java:2836-2853`):

```java
this.blockStateOutput.accept(
    MultiVariantGenerator.dispatch(Blocks.SEA_PICKLE)
        .with(
            PropertyDispatch.initial(BlockStateProperties.PICKLES, BlockStateProperties.WATERLOGGED)
                .select(1, false, createRotatedVariants(plainModel(
                        ModelLocationUtils.decorateBlockModelLocation("dead_sea_pickle"))))
                .select(1, true,  createRotatedVariants(plainModel(
                        ModelLocationUtils.decorateBlockModelLocation("sea_pickle"))))
                ...
        )
);
```

and for the turtle egg with `generate(...)` instead of `select(...)`
(`BlockModelGenerators.java:3121-3130`).

For our case, one `BooleanProperty` selecting between two hand-written models,
each randomly rotated:

```java
blockModels.blockStateOutput.accept(
    MultiVariantGenerator.dispatch(ModBlocks.MOLE_MOUND.get())
        .with(PropertyDispatch.initial(MoleMoundBlock.OPEN)
            .select(false, BlockModelGenerators.createRotatedVariants(
                    BlockModelGenerators.plainModel(Identifier.fromNamespaceAndPath("moleverse", "block/mole_mound_a"))))
            .select(true,  BlockModelGenerators.createRotatedVariants(
                    BlockModelGenerators.plainModel(Identifier.fromNamespaceAndPath("moleverse", "block/mole_mound_b"))))));
```

Random rotation without any property is the shorter
`MultiVariantGenerator.dispatch(block, createRotatedVariants(variant))` —
vanilla's dirt path (`:2337`) and lily pad (`:2495`).

Emitted JSON, verified in the client jar
(`assets/minecraft/blockstates/dirt_path.json`):

```json
{
  "variants": {
    "": [
      { "model": "minecraft:block/dirt_path" },
      { "model": "minecraft:block/dirt_path", "y": 90 },
      { "model": "minecraft:block/dirt_path", "y": 180 },
      { "model": "minecraft:block/dirt_path", "y": 270 }
    ]
  }
}
```

`Quadrant` (`com/mojang/math/Quadrant.java:8`) has only `R0, R90, R180, R270` and
serialises to `0/90/180/270`. `Variant.SimpleModelState` carries `x`, `y`, `z`,
`uvlock`.

---

## A18 hand-written model JSON + block item model

There is **no validation that a referenced model was generated**. `ModelProvider`
only validates that every known block has a blockstate and every known item has
an item model definition (`ModelProvider.BlockStateGeneratorCollector.validate`,
`ItemInfoCollector.finalizeAndValidate`,
`net/minecraft/client/data/models/ModelProvider.java:127` and `:185`).
So pointing at a file you wrote by hand under
`src/main/resources/assets/moleverse/models/block/mole_mound_a.json` just works.

Blockstate pointing at a hand-written model:

```java
blockModels.blockStateOutput.accept(
    BlockModelGenerators.createSimpleBlock(block,
        BlockModelGenerators.plainVariant(
            Identifier.fromNamespaceAndPath("moleverse", "block/mole_mound_a"))));
```
`public static MultiVariantGenerator createSimpleBlock(Block block, MultiVariant variants)` —
`BlockModelGenerators.java:642`.

Block item model pointing at the same model:

```java
public void registerSimpleItemModel(Block block, Identifier model)   // :326
public void registerSimpleItemModel(Item item, Identifier model)     // :322
```
source: `net/minecraft/client/data/models/BlockModelGenerators.java`

```java
blockModels.registerSimpleItemModel(block,
        Identifier.fromNamespaceAndPath("moleverse", "block/mole_mound_a"));
```

It delegates to `this.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(model))`;
`ItemModelUtils.plainModel(Identifier)` returns `new BlockModelWrapper.Unbaked(model, List.of())`
(`net/minecraft/client/data/models/model/ItemModelUtils.java:37`).

The generated item model definition lands in `assets/<ns>/items/<name>.json`
(path provider `"items"`, `ModelProvider.java:49`) and looks like the vanilla
one (`assets/minecraft/items/stone.json` in the client jar):

```json
{ "model": { "type": "minecraft:model", "model": "minecraft:block/stone" } }
```

If you emit **nothing** for a `BlockItem`, `ItemInfoCollector.finalizeAndValidate`
auto-registers `ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(blockitem.getBlock()))`,
i.e. `<ns>:block/<blockname>` (`ModelProvider.java:186-192`). So if the hand-written
model file is named after the block, `registerSimpleItemModel` is optional.
Related helpers: `registerSimpleFlatItemModel(Item)` (:356), `(Block)` (:360),
`(Block, String suffix)` (:367), `registerSimpleTintedItemModel(Block, Identifier, ItemTintSource)` (:330).

---

## A19 RegisterSpawnPlacementsEvent

```java
package net.neoforged.neoforge.event.entity;

public class RegisterSpawnPlacementsEvent extends Event implements IModBusEvent {

    public <T extends Entity> void register(EntityType<T> entityType,
            SpawnPlacements.SpawnPredicate<T> predicate);

    public <T extends Entity> void register(EntityType<T> entityType,
            SpawnPlacements.SpawnPredicate<T> predicate, Operation operation);

    public <T extends Entity> void register(EntityType<T> entityType,
            @Nullable SpawnPlacementType placementType,
            Heightmap.@Nullable Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate,
            Operation operation);

    public enum Operation { AND, OR, REPLACE }
}
```
source: `net/neoforged/neoforge/event/entity/RegisterSpawnPlacementsEvent.java:43, 55, 62, 72, 87`

**Mod event bus** (`implements IModBusEvent`). Fired from
`SpawnPlacements.fireSpawnPlacementEvent()`, which `GameData.postRegisterEvents`
calls after all `RegisterEvent`s (`net/neoforged/neoforge/registries/GameData.java:102`).

For a **new** entity you must use the five-argument overload with non-null
`placementType` and `heightmap` — the class throws `NullPointerException`
otherwise (lines 74-80). `Operation` is irrelevant on first registration.

```java
public interface SpawnPredicate<T extends Entity> {
    boolean test(EntityType<T> entityType, ServerLevelAccessor level,
                 EntitySpawnReason spawnReason, BlockPos pos, RandomSource random);
}
```
source: `net/minecraft/world/entity/SpawnPlacements.java:201`

Land-animal constant: `SpawnPlacementTypes.ON_GROUND`
(`net/minecraft/world/entity/SpawnPlacementTypes.java:24`; the interface also has
`NO_RESTRICTIONS`, `IN_WATER`, `IN_LAVA`).

Standard animal predicate:

```java
public static boolean checkAnimalSpawnRules(EntityType<? extends Animal> entityType,
        LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
    boolean flag = EntitySpawnReason.ignoresLightRequirements(spawnReason)
                || isBrightEnoughToSpawn(level, pos);
    return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && flag;
}
```
source: `net/minecraft/world/entity/animal/Animal.java:110`
(`isBrightEnoughToSpawn` = `level.getRawBrightness(pos, 0) > 8`, `:117`)

Vanilla registers every land animal with the same triple
(`net/minecraft/world/entity/SpawnPlacements.java:121-152`):

```java
register(EntityType.PIG, SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
```

So ours:

```java
@SubscribeEvent   // MOD bus
public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
    event.register(ModEntities.MOLE.get(),
                   SpawnPlacementTypes.ON_GROUND,
                   Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                   Animal::checkAnimalSpawnRules,
                   RegisterSpawnPlacementsEvent.Operation.AND);
}
```

---

## A20 biome modifier `neoforge:add_spawns`

JSON shape, quoted from the Javadoc of the implementing record
(`net/neoforged/neoforge/common/world/BiomeModifiers.java:110-143`):

```json
{
  "type": "neoforge:add_spawns",
  "biomes": "#namespace:biome_tag",
  "spawners": {
    "type": "namespace:entity_type",
    "weight": 100,
    "minCount": 1,
    "maxCount": 4
  }
}
```

`biomes` accepts a biome id, a list of biome ids, or a `#tag`. `spawners`
accepts a single object or an array of them. Field names `minCount`/`maxCount`
are camelCase — verified against the codec
(`net/minecraft/world/level/biome/MobSpawnSettings.java:123-131`):

```java
public record SpawnerData(EntityType<?> type, int minCount, int maxCount)
// codec fields: "type", "minCount" (positive int), "maxCount" (positive int)
```
`weight` comes from the surrounding `WeightedList<SpawnerData>`.

The Java record:

```java
public record AddSpawnsBiomeModifier(HolderSet<Biome> biomes, WeightedList<SpawnerData> spawners)
        implements BiomeModifier {
    public static AddSpawnsBiomeModifier singleSpawn(HolderSet<Biome> biomes, Weighted<SpawnerData> spawner);
}
```
source: `net/neoforged/neoforge/common/world/BiomeModifiers.java:148`, `:156`

Registry: `NeoForgeRegistries.Keys.BIOME_MODIFIERS`, a
`ResourceKey<Registry<BiomeModifier>>` named `neoforge:biome_modifier`
(`net/neoforged/neoforge/registries/NeoForgeRegistries.java:61`), declared as a
datapack registry in `NeoForgeMod.java:562`
(`event.dataPackRegistry(NeoForgeRegistries.Keys.BIOME_MODIFIERS, BiomeModifier.DIRECT_CODEC)`).

Output path: `data/<modid>/neoforge/biome_modifier/<name>.json`. Derived from
`Registries.elementsDirPath` → `CommonHooks.prefixNamespace`, which returns
`namespace + "/" + path` for non-`minecraft` registries
(`net/minecraft/core/registries/Registries.java:311`,
`net/neoforged/neoforge/common/CommonHooks.java:1392`).

**Provider:** yes, `DatapackBuiltinEntriesProvider`.

```java
public DatapackBuiltinEntriesProvider(PackOutput output,
        CompletableFuture<HolderLookup.Provider> registries,
        RegistrySetBuilder datapackEntriesBuilder,
        Set<String> modIds)
```
source: `net/neoforged/neoforge/common/data/DatapackBuiltinEntriesProvider.java:81`
(other overloads at :40, :53, :66, :96, :111 add `ICondition` support)

AE2 wires it up like this
(`_reference/Applied-Energistics-2/src/client/java/appeng/datagen/AE2DataGenerators.java:74`
and `:114`):

```java
pack.addProvider(output -> new DatapackBuiltinEntriesProvider(
        output, event.getLookupProvider(), createDatapackEntriesBuilder(), Set.of(AppEng.MOD_ID)));

private static RegistrySetBuilder createDatapackEntriesBuilder() {
    return new RegistrySetBuilder()
            .add(Registries.DIMENSION_TYPE, InitDimensionTypes::init)
            .add(Registries.BIOME, InitBiomes::init)
            .add(Registries.DAMAGE_TYPE, AEDamageTypes::init);
}
```

AE2 itself registers no biome modifier — verified, `grep AddSpawnsBiomeModifier`
over its `src` is empty. For ours, add another line to that builder:

```java
.add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ctx -> {
    HolderGetter<Biome> biomes = ctx.lookup(Registries.BIOME);
    ctx.register(
        ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                           Identifier.fromNamespaceAndPath("moleverse", "add_mole")),
        new BiomeModifiers.AddSpawnsBiomeModifier(
            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
            WeightedList.of(new Weighted<>(
                new MobSpawnSettings.SpawnerData(ModEntities.MOLE.get(), 2, 4), 10))));
})
```

(the `AddSpawnsBiomeModifier.modify` body at `:161-169` confirms it reads
`spawner.weight()` and `spawner.value()` and calls
`spawns.addSpawn(type.getCategory(), weight, spawnerData)` in `Phase.ADD` —
so the `MobCategory` comes from the `EntityType`, it is not part of the JSON.)

Counterpart `neoforge:remove_spawns` at `:190`.

---

## A21 RegisterCommandsEvent

```java
package net.neoforged.neoforge.event;

public class RegisterCommandsEvent extends Event {
    public CommandDispatcher<CommandSourceStack> getDispatcher();
    public Commands.CommandSelection getCommandSelection();
    public CommandBuildContext getBuildContext();
}
```
source: `net/neoforged/neoforge/event/RegisterCommandsEvent.java:22, 36, 43, 50`

Fired on `NeoForge.EVENT_BUS` (the **game** bus, not the mod bus) whenever
`ReloadableServerResources` is recreated — i.e. server start and `/reload`.

Differences from `RegisterClientCommandsEvent`
(`net/neoforged/neoforge/client/event/RegisterClientCommandsEvent.java:37`), which
this project already uses in
`src/main/java/net/sgeht/moleverse/client/debug/MoleDebugCommand.java`:

| | `RegisterCommandsEvent` | `RegisterClientCommandsEvent` |
|---|---|---|
| package | `net.neoforged.neoforge.event` | `net.neoforged.neoforge.client.event` |
| bus | `NeoForge.EVENT_BUS` | `NeoForge.EVENT_BUS`, client logical side only |
| extra getter | `getCommandSelection()` | — |
| dispatcher | server commands, needs permissions, runs server-side | client commands, no server, no permission level |
| both have | `getDispatcher()`, `getBuildContext()` | same |

Both hand out `CommandDispatcher<CommandSourceStack>`, so the existing
`MoleDebugCommand.register(CommandDispatcher<CommandSourceStack>)` shape works
unchanged for a server command — only the event type and the permission
requirements differ (`Commands.literal("x").requires(src -> src.hasPermission(2))`).

---

## A22 client debug lines in the world

### The event

```java
package net.neoforged.neoforge.client.event;

public abstract class RenderLevelStageEvent extends Event {
    public LevelRenderer     getLevelRenderer();
    public LevelRenderState  getLevelRenderState();
    public PoseStack         getPoseStack();
    public Matrix4f          getModelViewMatrix();
    public Iterable<? extends IRenderableSection> getRenderableSections();
}
```
source: `net/neoforged/neoforge/client/event/RenderLevelStageEvent.java:41, 58, 65, 72, 79, 89`

**There is no `Stage` constant and no `RenderLevelStageEvent.Stage` enum in this
version.** The stages are *sub-event classes* you subscribe to individually, in
this firing order (`:31-38`):

`AfterSky` → `AfterOpaqueBlocks` → `AfterEntities` → `AfterTranslucentBlocks` →
`AfterTripwireBlocks` → `AfterParticles` → `AfterWeather` → `AfterLevel`

```java
@SubscribeEvent   // NeoForge.EVENT_BUS, client only
public static void onRenderLevel(RenderLevelStageEvent.AfterEntities event) { ... }
```

**Use `AfterEntities`** — it is the only stage that gets a real `PoseStack`. The
others are constructed with `null` and the event substitutes a fresh identity
`PoseStack` (`:50`). Verified at the fire sites in
`net/minecraft/client/renderer/LevelRenderer.java`: `:691` passes `posestack`,
while `:654, :722, :726, :768, :824, :1315` and
`net/minecraft/client/renderer/GameRenderer.java:808` all pass `null`.

**There is no `MultiBufferSource` on the event.** Get it yourself — it is the
same object `LevelRenderer` is using at that moment
(`LevelRenderer.java:669`: `this.renderBuffers.bufferSource()`):

```java
MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
```
`public MultiBufferSource.BufferSource bufferSource()` —
`net/minecraft/client/renderer/RenderBuffers.java:64`.

The `PoseStack` from `AfterEntities` is camera-relative (created fresh at
`LevelRenderer.java:668`, entities are submitted into it), so translate by
`-cameraPos` before feeding world coordinates. The camera position is on the
render state: `event.getLevelRenderState().cameraRenderState.pos` (`Vec3`) and
`.blockPos` — `net/minecraft/client/renderer/state/CameraRenderState.java:11-12`.

### The line RenderType

`RenderType` **moved package**: it is now
`net.minecraft.client.renderer.rendertype.RenderType`, and the constants live in
`net.minecraft.client.renderer.rendertype.RenderTypes`.

```java
public static final RenderType LINES;              // :364
public static final RenderType LINES_TRANSLUCENT;  // :371
public static RenderType lines()             { return LINES; }              // :634
public static RenderType linesTranslucent()  { return LINES_TRANSLUCENT; }  // :638
public static RenderType secondaryBlockOutline();                           // :644
```
source: `net/minecraft/client/renderer/rendertype/RenderTypes.java`

Vertex format for lines needs colour, normal and line width:

```java
consumer.addVertex(pose, x, y, z).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(w);
```
as used in the only remaining shape helper,
`net/minecraft/client/renderer/ShapeRenderer.java:12`:

```java
public static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                               double dx, double dy, double dz, int color, float lineWidth)
```

(`LevelRenderer.renderLineBox` no longer exists; `ShapeRenderer.renderShape` is
the whole class.)

### Simpler alternative: the new Gizmos API

1.21.11 ships a first-class debug-drawing API, `net.minecraft.gizmos`. It needs
no event, no `PoseStack` and no buffer handling:

```java
public static GizmoProperties line(Vec3 start, Vec3 end, int color);              // :51
public static GizmoProperties line(Vec3 start, Vec3 end, int color, float width); // :55
public static GizmoProperties arrow(Vec3 start, Vec3 end, int color);             // :59
public static GizmoProperties cuboid(AABB aabb, GizmoStyle style);                // :30
public static GizmoProperties cuboid(BlockPos pos, GizmoStyle style);             // :38
public static GizmoProperties circle(Vec3 pos, float radius, GizmoStyle style);   // :46
public static GizmoProperties point(Vec3 pos, int color, float size);             // :74
public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int line, int color, float scale);
```
source: `net/minecraft/gizmos/Gizmos.java`

```java
public interface GizmoProperties {
    GizmoProperties setAlwaysOnTop();
    GizmoProperties persistForMillis(int millis);
    GizmoProperties fadeOut();
}
```
source: `net/minecraft/gizmos/GizmoProperties.java`

Constraint: `Gizmos.addGizmo` throws `IllegalStateException("Gizmos cannot be
created here! No GizmoCollector has been registered.")` unless a collector is
installed on the current thread (`Gizmos.java:22-27`). The client installs one
around **every client tick and every queued-task run**:

```java
try (Gizmos.TemporaryCollection c = this.collectPerTickGizmos()) { this.tick(); }
```
source: `net/minecraft/client/Minecraft.java:1286` and `:1304`
(`collectPerTickGizmos()` at `:2941`), and the integrated server does the same
around its tick (`net/minecraft/client/server/IntegratedServer.java:102`).

So calling `Gizmos.line(...)` from a `ClientTickEvent` handler works and the
lines are drawn for that frame; `persistForMillis(n)` keeps them longer.
`LevelRenderer.finalizeGizmoCollection()` merges the client's and the integrated
server's gizmos and renders them right after `AfterEntities`
(`net/minecraft/client/renderer/LevelRenderer.java:697-698`), with the
always-on-top set drawn after a depth clear (`:845-850`).

**Not verified:** whether `Gizmos` calls are safe from a dedicated-server thread
for a multiplayer debug overlay. On the integrated server they are (the
collector is installed in `processPacketsAndTick`); a dedicated server installs
none, and `GameTestServer` explicitly sets `GizmoCollector.NOOP`
(`net/minecraft/gametest/framework/GameTestServer.java:166`).
