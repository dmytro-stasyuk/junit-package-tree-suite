package io.github.dmytrostasyuk.packagetreesuite;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps Jupiter from running the test classes a package run has taken over, so each test runs once, through
 * {@link PackageTreeSuiteEngine}.
 * <p>
 * Every engine discovers a run's selection on its own, so in a package run Jupiter finds the same classes this
 * library's engine does. The launcher applies post-discovery filters after all engines have discovered, and picks up
 * filters listed in {@code META-INF/services} without any setup, which is what lets this one remove Jupiter's copy.
 * <p>
 * The launcher only asks a filter about nodes with no children — tests, and parameterized or factory methods whose
 * tests appear later — and then drops the class nodes left empty. So the filter decides per test, by the top-level
 * class each one belongs to.
 */
public class PackageRunFilter implements PostDiscoveryFilter {

    private static final String JUPITER_ENGINE_ID = "junit-jupiter";

    /*
     * Written by the engine during discovery, read by this filter right after it: the launcher discovers every
     * engine, then filters, on the same thread. One run per JVM is the norm for both Gradle and IDE runners.
     */
    private static volatile Set<String> takenOverClasses = Set.of();

    static void takeOver(Collection<String> classNames) {
        takenOverClasses = Set.copyOf(classNames);
    }

    static void clearTakenOverClasses() {
        takenOverClasses = Set.of();
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        Set<String> classes = takenOverClasses;
        if (classes.isEmpty() || !isFromJupiter(descriptor)) {
            return FilterResult.included("not part of a package run");
        }
        boolean takenOver = topLevelClassName(descriptor).map(classes::contains).orElse(false);
        return takenOver
                ? FilterResult.excluded("run through the package-tree-suite engine instead")
                : FilterResult.included("not part of a package run");
    }

    private static boolean isFromJupiter(TestDescriptor descriptor) {
        UniqueId.Segment engine = descriptor.getUniqueId().getSegments().get(0);
        return engine.getType().equals("engine") && engine.getValue().equals(JUPITER_ENGINE_ID);
    }

    /* The class node directly under Jupiter's engine root, which is what the engine records for a package run. */
    private static Optional<String> topLevelClassName(TestDescriptor descriptor) {
        TestDescriptor current = descriptor;
        while (current.getParent().isPresent() && current.getParent().get().getParent().isPresent()) {
            current = current.getParent().get();
        }
        return current.getSource()
                .filter(ClassSource.class::isInstance)
                .map(source -> ((ClassSource) source).getClassName());
    }
}
