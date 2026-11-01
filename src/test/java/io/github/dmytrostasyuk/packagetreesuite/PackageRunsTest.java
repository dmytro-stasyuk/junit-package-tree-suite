package io.github.dmytrostasyuk.packagetreesuite;

import io.github.dmytrostasyuk.packagetreesuite.fixtures.*;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.onlysub.inner.Inner;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.step_one.StepOne;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.steps.Steps;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.sub.Item10;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.sub.Item2;
import io.github.dmytrostasyuk.packagetreesuite.fixtures.sub.deeper.Deep;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;

/**
 * Runs the fixtures through a real launcher with every engine on the classpath, the way an IDE or build tool does,
 * so the counts show which engine ran each test and that none ran twice.
 */
class PackageRunsTest {

    private static final String FIXTURES_PACKAGE = "io.github.dmytrostasyuk.packagetreesuite.fixtures";

    /* Every fixture class by name, the way Gradle hands a package run to the launcher. */
    private static final List<DiscoverySelector> EVERY_FIXTURE_CLASS = Arrays.asList(
            selectClass(AllFixtures.class), selectClass(ExcludingSuite.class), selectClass(Alpha.class),
            selectClass(Beta.class), selectClass(Skipped.class), selectClass(Item2.class), selectClass(Item10.class),
            selectClass(Deep.class), selectClass(Inner.class), selectClass(StepOne.class), selectClass(Steps.class));

    @Test
    void packageRunsAreOffByDefault() {
        assertEquals(Map.of("junit-jupiter", 10L), testsRunByEngine(false, selectPackage(FIXTURES_PACKAGE)));
    }

    @Test
    void aPackageRunGoesThroughTheEngineOnceWhenSwitchedOn() {
        assertEquals(Map.of("package-tree-suite", 10L), testsRunByEngine(true, selectPackage(FIXTURES_PACKAGE)));
    }

    @Test
    void classesSelectedByNameAreTakenOverTheSameWay() {
        assertEquals(Map.of("package-tree-suite", 10L), testsRunByEngine(true, EVERY_FIXTURE_CLASS));
    }

    @Test
    void aSingleClassRunStaysWithJupiter() {
        assertEquals(Map.of("junit-jupiter", 2L), testsRunByEngine(true, selectClass(Beta.class)));
    }

    @Test
    void aSingleMethodRunStaysWithJupiter() {
        assertEquals(Map.of("junit-jupiter", 1L), testsRunByEngine(true, selectMethod(Beta.class, "test1")));
    }

    @Test
    void aSuiteClassRunOnItsOwnRunsItsPackage() {
        assertEquals(Map.of("package-tree-suite", 10L), testsRunByEngine(false, selectClass(AllFixtures.class)));
    }

    @Test
    void aSuiteClassAmongOtherSelectedClassesDoesNotRunItsTestsAgain() {
        assertEquals(Map.of("junit-jupiter", 10L), testsRunByEngine(false, EVERY_FIXTURE_CLASS));
    }

    private static Map<String, Long> testsRunByEngine(boolean packageRuns, DiscoverySelector selector) {
        return testsRunByEngine(packageRuns, List.of(selector));
    }

    private static Map<String, Long> testsRunByEngine(boolean packageRuns, List<DiscoverySelector> selectors) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .configurationParameter(PackageTreeSuiteEngine.PACKAGE_RUNS_ENABLED, String.valueOf(packageRuns))
                .build();
        Map<String, Long> counts = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                if (!identifier.isTest()) {
                    return;
                }
                if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                    failures.add(identifier.getUniqueId());
                }
                String engine = identifier.getUniqueIdObject().getEngineId().orElse("?");
                counts.merge(engine, 1L, Long::sum);
            }
        });
        assertEquals(List.of(), failures);
        return counts;
    }
}
