package net.sgeht.moleverse.test;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.sgeht.moleverse.Moleverse;

/**
 * Where the mod's game tests are declared.
 *
 * <h2>Two registries, two events, because this version is data driven</h2>
 *
 * <p>Game tests stopped being annotated methods. A test is now a
 * {@code GameTestInstance} in the {@code test_instance} datapack registry, and the
 * one vanilla implementation that can hold Java code,
 * {@link FunctionGameTestInstance}, only holds a <em>key</em> into the
 * {@code test_function} registry. So the body and the declaration are registered
 * separately, on two different events:</p>
 *
 * <ul>
 * <li>{@link RegisterEvent} puts the bodies into {@link Registries#TEST_FUNCTION},
 * which is a built-in registry like blocks or items.</li>
 * <li>{@link RegisterGameTestsEvent} puts the declarations into
 * {@code test_instance}. NeoForge fires it from inside
 * {@code RegistryDataLoader.load}, which is why it exists at all - that registry
 * is built from datapacks and is frozen a few lines later.</li>
 * </ul>
 *
 * <p>The bodies are registered unconditionally while the declarations only appear
 * when {@code GameTestHooks.isGametestEnabled()} says so - the event is not even
 * fired otherwise. That asymmetry is deliberate: {@code test_instance} is a
 * synchronised registry, so a declaration that exists on one side and not the
 * other would be a desync, whereas a handful of unreferenced lambdas in a
 * built-in registry cost nothing and are identical on both sides.</p>
 *
 * <h2>Why an environment of our own</h2>
 *
 * <p>An environment is what tests are batched by, and setting one up is the only
 * way to get a {@code Holder} for one from this event - it exposes no lookup. An
 * empty {@code AllOf} changes nothing about the level, which is what these tests
 * want; the batching is a side effect worth having anyway, since it keeps them
 * out of vanilla's batch.</p>
 */
@EventBusSubscriber(modid = Moleverse.MOD_ID)
public final class ModGameTests {

    /**
     * The 1x1x1 structure vanilla ships for tests that bring their own world.
     *
     * <p>Every test here either needs no blocks at all or builds what it needs
     * itself, so there is nothing for a template to place. Note that this is also
     * the whole of the area the runner force loads for a test - anything written
     * outside it has to be force loaded by hand.</p>
     */
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    /** Generous. Every body here finishes inside its first tick. */
    private static final int MAX_TICKS = 20;

    private static final int SETUP_TICKS = 0;

    /** Required, so that a failure comes back as a non-zero exit code from {@code runGameTestServer}. */
    private static final boolean REQUIRED = true;

    private static final ResourceKey<Consumer<GameTestHelper>> GEOMETRY_ROUND_TRIP =
            function("geometry_round_trip");
    private static final ResourceKey<Consumer<GameTestHelper>> LINK_CODEC_ROUND_TRIP =
            function("link_codec_round_trip");
    private static final ResourceKey<Consumer<GameTestHelper>> CARVING_CLEARS_GROUND =
            function("carving_clears_ground");
    private static final ResourceKey<Consumer<GameTestHelper>> CARVING_IS_RECOGNISED =
            function("carving_is_recognised");
    private static final ResourceKey<Consumer<GameTestHelper>> LINK_STORE_ROUND_TRIP =
            function("link_store_round_trip");
    private static final ResourceKey<Consumer<GameTestHelper>> RECONCILER_CARVES_ACROSS_CHUNKS =
            function("reconciler_carves_across_chunks");
    private static final ResourceKey<Consumer<GameTestHelper>> LEDGER_CODEC_ROUND_TRIP =
            function("ledger_codec_round_trip");
    private static final ResourceKey<Consumer<GameTestHelper>> NEST_PLAN_IS_DETERMINISTIC =
            function("nest_plan_is_deterministic");
    private static final ResourceKey<Consumer<GameTestHelper>> LARDER_PLAN_IS_DETERMINISTIC =
            function("larder_plan_is_deterministic");
    private static final ResourceKey<Consumer<GameTestHelper>> NEST_CARVES_ACROSS_CHUNKS =
            function("nest_carves_across_chunks");
    private static final ResourceKey<Consumer<GameTestHelper>> FORTRESS_MOUND_KEEPS_ITS_MOUND =
            function("fortress_mound_keeps_its_mound");

    private ModGameTests() {
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, Moleverse.id(path));
    }

    @SubscribeEvent
    public static void onRegisterTestFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(GEOMETRY_ROUND_TRIP, BurrowGameTests::geometryRoundTrip);
            registry.register(LINK_CODEC_ROUND_TRIP, BurrowGameTests::linkCodecRoundTrip);
            registry.register(CARVING_CLEARS_GROUND, BurrowGameTests::carvingClearsGround);
            registry.register(CARVING_IS_RECOGNISED, BurrowGameTests::carvingIsRecognised);
            registry.register(LINK_STORE_ROUND_TRIP, BurrowGameTests::linkStoreRoundTrip);
            registry.register(RECONCILER_CARVES_ACROSS_CHUNKS, BurrowGameTests::reconcilerCarvesAcrossChunks);
            registry.register(LEDGER_CODEC_ROUND_TRIP, BurrowGameTests::ledgerCodecRoundTrip);
            registry.register(NEST_PLAN_IS_DETERMINISTIC, BurrowGameTests::nestPlanIsDeterministic);
            registry.register(LARDER_PLAN_IS_DETERMINISTIC, BurrowGameTests::larderPlanIsDeterministic);
            registry.register(NEST_CARVES_ACROSS_CHUNKS, BurrowGameTests::nestCarvesAcrossChunks);
            registry.register(FORTRESS_MOUND_KEEPS_ITS_MOUND, BurrowGameTests::fortressMoundKeepsItsMound);
        });
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment =
                event.registerEnvironment(Moleverse.id("burrow"), new TestEnvironmentDefinition.AllOf(List.of()));

        declare(event, environment, GEOMETRY_ROUND_TRIP);
        declare(event, environment, LINK_CODEC_ROUND_TRIP);
        declare(event, environment, CARVING_CLEARS_GROUND);
        declare(event, environment, CARVING_IS_RECOGNISED);
        declare(event, environment, LINK_STORE_ROUND_TRIP);
        declare(event, environment, RECONCILER_CARVES_ACROSS_CHUNKS);
        declare(event, environment, LEDGER_CODEC_ROUND_TRIP);
        declare(event, environment, NEST_PLAN_IS_DETERMINISTIC);
        declare(event, environment, LARDER_PLAN_IS_DETERMINISTIC);
        declare(event, environment, NEST_CARVES_ACROSS_CHUNKS);
        declare(event, environment, FORTRESS_MOUND_KEEPS_ITS_MOUND);
    }

    /** One test, named after the function it runs, so a failure names something findable. */
    private static void declare(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition> environment,
            ResourceKey<Consumer<GameTestHelper>> body) {
        event.registerTest(body.identifier(), new FunctionGameTestInstance(body,
                new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, SETUP_TICKS, REQUIRED)));
    }
}
