package io.github.dmytrostasyuk.packagetreesuite;

import io.github.dmytrostasyuk.packagetreesuite.fixtures.AllFixtures;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.Alpha;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.ExcludingSuite;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.testkit.engine.EngineTestKit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class PackageTreeSuiteEngineTest {

    private static final String FIXTURES = "io.github.dmytrostasyuk.packagetreesuite.fixtures.";

    @Test
    void nestsTheTestClassesByPackageInProjectTreeOrderWithTestCounts() {
        assertEquals(String.join("\n",
                "fixtures (10)  -> AllFixtures",
                "  onlysub (1)  -> onlysub.inner.Inner",
                "    inner (1)  -> onlysub.inner.Inner",
                "      Inner",
                "  step_one (1)  -> step_one.StepOne",
                "    StepOne",
                "  steps (1)  -> steps.Steps",
                "    Steps",
                "  sub (3)  -> sub.Item2",
                "    deeper (1)  -> sub.deeper.Deep",
                "      Deep",
                "    Item2",
                "    Item10",
                "  Alpha",
                "  Beta",
                "  Skipped"),
                tree(AllFixtures.class));
    }

    @Test
    void leavesOutExcludedClassesAndCountsWhenTurnedOff() {
        assertEquals(String.join("\n",
                "fixtures  -> ExcludingSuite",
                "  onlysub  -> onlysub.inner.Inner",
                "    inner  -> onlysub.inner.Inner",
                "      Inner",
                "  step_one  -> step_one.StepOne",
                "    StepOne",
                "  steps  -> steps.Steps",
                "    Steps",
                "  sub  -> sub.Item2",
                "    deeper  -> sub.deeper.Deep",
                "      Deep",
                "    Item2",
                "    Item10",
                "  Alpha",
                "  Beta"),
                tree(ExcludingSuite.class));
    }

    @Test
    void runsEveryTestUnderTheSuiteOnce() {
        EngineTestKit.engine(PackageTreeSuiteEngine.ENGINE_ID)
                .selectors(selectClass(AllFixtures.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(10).succeeded(10).failed(0));
    }

    @Test
    void reportsPackageNodesAsStartedAndFinishedContainers() {
        EngineTestKit.engine(PackageTreeSuiteEngine.ENGINE_ID)
                .selectors(selectClass(AllFixtures.class))
                .execute()
                .containerEvents()
                /* engine + marker + 6 packages + 9 test classes */
                .assertStatistics(stats -> stats.started(17).succeeded(17).failed(0));
    }

    @Test
    void doesNothingWhenNoSuiteIsSelected() {
        EngineTestKit.engine(PackageTreeSuiteEngine.ENGINE_ID)
                .selectors(selectClass(Alpha.class))
                .execute()
                .allEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    /*
     * The discovered tree down to the class level, one node per line. Package nodes show the class their click
     * opens; class nodes show their name only.
     */
    private static String tree(Class<?> suite) {
        TestDescriptor root = new PackageTreeSuiteEngine().discover(
                LauncherDiscoveryRequestBuilder.request().selectors(selectClass(suite)).build(),
                UniqueId.forEngine(PackageTreeSuiteEngine.ENGINE_ID));
        StringBuilder out = new StringBuilder();
        for (TestDescriptor top : root.getChildren()) {
            print(top, 0, out);
        }
        return out.toString().stripTrailing();
    }

    private static void print(TestDescriptor node, int depth, StringBuilder out) {
        out.append("  ".repeat(depth)).append(node.getDisplayName());
        if (isAddedByTheEngine(node)) {
            node.getSource().map(ClassSource.class::cast).ifPresent(source ->
                    out.append("  -> ").append(source.getClassName().replace(FIXTURES, "")));
            out.append('\n');
            for (TestDescriptor child : node.getChildren()) {
                print(child, depth + 1, out);
            }
        } else {
            out.append('\n');
        }
    }

    /* The suite and package nodes, as opposed to the test class nodes Jupiter created. */
    private static boolean isAddedByTheEngine(TestDescriptor node) {
        String segmentType = node.getUniqueId().getLastSegment().getType();
        return segmentType.equals("package-tree-suite") || segmentType.equals("package");
    }
}
