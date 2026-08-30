package net.sgeht.moleverse.debug;

/**
 * Whether this process is a development run, and therefore whether the debug
 * instruments may exist at all.
 *
 * <p>The rule they all answer to: a dev instrument must not change the game. Not
 * "must not change it much" and not "is hard to find" - a command that is merely
 * undocumented is one somebody eventually types, and several of the instruments
 * here reach straight into worldgen numbers and mob decisions. So the test lives
 * in one place, every instrument asks it before <em>registering</em> rather than
 * before acting, and a shipped jar carries the code without any way in.</p>
 *
 * <h2>Two properties, and why there are two</h2>
 *
 * <p>{@link #UMBRELLA} is the one that matters and the only one worth setting. It
 * sits in {@code build.gradle}'s {@code configureEach} block, so the client, the
 * server, the data run and the game test server are all development runs by the
 * same line. That it covers {@code runServer} is what lets the server-side command
 * trees be gated at all: {@code tools/soak} drives a scenario through
 * {@code ./gradlew runServer} and needs {@code /moleverse colony} and
 * {@code /moleverse burrow} to exist there.</p>
 *
 * <p>{@link #LEGACY_CLIENT} is {@code moleverse.devPublish}, which the
 * {@code runClient} configuration set for {@code DevWorldPublisher} before the
 * umbrella existed (named and not linked: this is common code and must not reach
 * into the client package). It is still honoured, so that a client started by hand
 * with only the old flag behaves as it always did. Nothing needs it any more, and
 * dropping it would cost nothing but a line - it is kept because a gate that
 * silently stops recognising a flag somebody has in a launcher script is a bad
 * kind of surprise.</p>
 *
 * <p><b>Neither property is set anywhere but Gradle.</b> That is the whole
 * mechanism: a played game never sees either, so every instrument's registration
 * returns before it builds anything, and the shipped classes sit in the jar with
 * no way in.</p>
 *
 * <p>Read once, at class initialisation. System properties arrive on the JVM
 * command line long before any mod class loads, so nothing can change the answer
 * later, and every instrument asking the same constant is what keeps the
 * development surface from drifting apart one class at a time.</p>
 */
public final class DevGate {

    /** Set for every Gradle run, in {@code configureEach}. Nothing outside Gradle sets it. */
    public static final String UMBRELLA = "moleverse.dev";

    /** What {@code runClient} set before the umbrella existed. Still honoured, no longer needed. */
    public static final String LEGACY_CLIENT = "moleverse.devPublish";

    private static final boolean DEVELOPMENT =
            Boolean.getBoolean(UMBRELLA) || Boolean.getBoolean(LEGACY_CLIENT);

    private DevGate() {
    }

    /** Whether the debug instruments may be registered in this process. */
    public static boolean isDevelopmentRun() {
        return DEVELOPMENT;
    }
}
