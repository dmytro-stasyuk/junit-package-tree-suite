package io.github.dmytrostasyuk.packagetreesuite;

import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.*;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

import java.util.*;

/**
 * Runs Jupiter tests regrouped so the test tree shows one container per subpackage.
 * <p>
 * Acts in two cases. A run whose only selected class is a {@link PackageTreeSuite}-marked class runs every test under
 * the suite's base package. With package runs switched on ({@value #PACKAGE_RUNS_ENABLED} set to {@code true}), any
 * run that covers more than one test class — running a package or folder from the IDE, or several classes by name
 * pattern — runs those classes, regrouped under the deepest package they share; {@link PackageRunFilter} then keeps
 * Jupiter from running them a second time. Any other run gets an empty tree from this engine, so having it on the
 * test classpath changes nothing else.
 * <p>
 * Discovery is delegated to Jupiter; this engine only moves each top-level test class from under the engine root
 * to a package container chain beneath a single node for the marker class. Execution is handed back to Jupiter
 * unchanged — its executor treats descriptors that are not its own nodes as pass-through containers, so the added
 * levels still report started and finished around their children.
 * <p>
 * The marker node carries the marker's {@link ClassSource} on purpose: Gradle's {@code --tests} filter keeps a test
 * when any ancestor's class matches, so running the marker class by name keeps every test beneath it.
 */
public class PackageTreeSuiteEngine implements TestEngine {

    public static final String ENGINE_ID = "package-tree-suite";

    /** Configuration parameter that switches package runs on; off unless set to {@code true}. */
    public static final String PACKAGE_RUNS_ENABLED = "packagetreesuite.packageRuns.enabled";

    /** Configuration parameter for package runs' test counts on package nodes; on unless set to {@code false}. */
    public static final String PACKAGE_RUNS_SHOW_TEST_COUNTS = "packagetreesuite.packageRuns.showTestCounts";

    /*
     * Looked up the way the launcher finds engines; Jupiter's engine class is not on the compile classpath. Chosen
     * by provider type before instantiating, since instantiating every provider would construct this engine again.
     */
    private final TestEngine jupiter = ServiceLoader.load(TestEngine.class).stream()
            .filter(provider -> provider.type().getName().equals("org.junit.jupiter.engine.JupiterTestEngine"))
            .map(ServiceLoader.Provider::get)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("JUnit Jupiter engine not found on the test classpath"));

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        PackageRunFilter.clearTakenOverClasses();
        Optional<Class<?>> marker = findSoleMarkerClass(request);
        if (marker.isPresent()) {
            return discoverSuite(request, uniqueId, marker.get());
        }
        if (request.getConfigurationParameters().getBoolean(PACKAGE_RUNS_ENABLED).orElse(false)) {
            return discoverPackageRun(request, uniqueId);
        }
        return emptyTree(uniqueId);
    }

    private TestDescriptor discoverSuite(EngineDiscoveryRequest request, UniqueId uniqueId, Class<?> marker) {
        PackageTreeSuite settings = marker.getAnnotation(PackageTreeSuite.class);
        String basePackage = settings.value().isEmpty() ? marker.getPackageName() : settings.value();

        LauncherDiscoveryRequestBuilder packageRequestBuilder = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage(basePackage));
        if (settings.excludeClassNamePatterns().length > 0) {
            packageRequestBuilder.filters(
                    ClassNameFilter.excludeClassNamePatterns(settings.excludeClassNamePatterns()));
        }
        LauncherDiscoveryRequest packageRequest = packageRequestBuilder
                .parentConfigurationParameters(request.getConfigurationParameters())
                .outputDirectoryProvider(request.getOutputDirectoryProvider())
                .build();
        TestDescriptor root = jupiter.discover(packageRequest, uniqueId);

        regroupByPackage(root, Optional.of(marker), basePackage, settings.showTestCounts());
        return root;
    }

    /*
     * Discovers the run's own selection through Jupiter and keeps it only when it spans more than one test class:
     * a single class or method run stays with Jupiter, flat, exactly as without this library.
     */
    private TestDescriptor discoverPackageRun(EngineDiscoveryRequest request, UniqueId uniqueId) {
        TestDescriptor root = jupiter.discover(request, uniqueId);
        List<String> classNames = new ArrayList<>();
        for (TestDescriptor classDescriptor : root.getChildren()) {
            classDescriptor.getSource()
                    .filter(ClassSource.class::isInstance)
                    .ifPresent(source -> classNames.add(((ClassSource) source).getClassName()));
        }
        String basePackage = deepestSharedPackage(root.getChildren());
        if (classNames.size() < 2 || basePackage.isEmpty()) {
            return emptyTree(uniqueId);
        }
        boolean showTestCounts = request.getConfigurationParameters()
                .getBoolean(PACKAGE_RUNS_SHOW_TEST_COUNTS).orElse(true);
        regroupByPackage(root, Optional.empty(), basePackage, showTestCounts);
        PackageRunFilter.takeOver(classNames);
        return root;
    }

    private static TestDescriptor emptyTree(UniqueId uniqueId) {
        return new EngineDescriptor(uniqueId, "Package tree suite");
    }

    private static String deepestSharedPackage(Iterable<? extends TestDescriptor> classDescriptors) {
        String shared = null;
        for (TestDescriptor classDescriptor : classDescriptors) {
            Optional<String> packageName = packageOf(classDescriptor);
            if (packageName.isEmpty()) {
                continue;
            }
            shared = shared == null ? packageName.get() : sharedPrefix(shared, packageName.get());
        }
        return shared == null ? "" : shared;
    }

    private static String sharedPrefix(String first, String second) {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        StringBuilder shared = new StringBuilder();
        for (int i = 0; i < Math.min(a.length, b.length) && a[i].equals(b[i]); i++) {
            shared.append(shared.length() == 0 ? "" : ".").append(a[i]);
        }
        return shared.toString();
    }

    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        /* No marker selected: the root is this engine's own empty descriptor, which Jupiter cannot execute. */
        if (root.getClass() == EngineDescriptor.class) {
            request.getEngineExecutionListener().executionStarted(root);
            request.getEngineExecutionListener().executionFinished(root, TestExecutionResult.successful());
            return;
        }
        jupiter.execute(request);
    }

    /*
     * A suite class takes the run only when it is the run's one and only selection. When it is merely one of many
     * classes a package run selects, Jupiter is already running the other classes, and running the suite as well
     * would run every test under it twice.
     */
    private static Optional<Class<?>> findSoleMarkerClass(EngineDiscoveryRequest request) {
        List<DiscoverySelector> selectors = request.getSelectorsByType(DiscoverySelector.class);
        if (selectors.size() != 1 || !(selectors.get(0) instanceof ClassSelector)) {
            return Optional.empty();
        }
        Class<?> candidate = ((ClassSelector) selectors.get(0)).getJavaClass();
        return candidate.isAnnotationPresent(PackageTreeSuite.class) ? Optional.of(candidate) : Optional.empty();
    }

    /*
     * The top node is the suite class's own node when there is one, carrying the suite's ClassSource; in a package
     * run it is the base package's node, and like every other package node it opens the package's first class.
     */
    private static void regroupByPackage(TestDescriptor root, Optional<Class<?>> marker, String basePackage,
                                         boolean showTestCounts) {
        List<TestDescriptor> classDescriptors = new ArrayList<>(root.getChildren());
        /* Names are fixed when a descriptor is created, so every package's count is known before any container is. */
        Map<String, Long> testCounts = showTestCounts ? countTestsByPackage(classDescriptors, basePackage) : null;
        TestDescriptor markerNode = new Container(
                marker.map(suite -> root.getUniqueId().append("package-tree-suite", suite.getName()))
                        .orElseGet(() -> root.getUniqueId().append("package", basePackage)),
                nodeName(basePackage, testCounts),
                basePackage,
                marker.map(ClassSource::from).orElse(null));
        root.addChild(markerNode);

        Map<String, TestDescriptor> containersByPackage = new HashMap<>();
        containersByPackage.put(basePackage, markerNode);
        for (TestDescriptor classDescriptor : classDescriptors) {
            Optional<String> packageName = packageOf(classDescriptor);
            if (packageName.isEmpty()) {
                continue;
            }
            TestDescriptor container = containerFor(packageName.get(), basePackage, markerNode, containersByPackage,
                    testCounts);
            root.removeChild(classDescriptor);
            container.addChild(classDescriptor);
        }
        for (TestDescriptor container : containersByPackage.values()) {
            sortLikeProjectTree(container);
        }
        for (TestDescriptor container : containersByPackage.values()) {
            /* A suite's node keeps the suite's class: Gradle's --tests filter matches tests through it. */
            if (container != markerNode || marker.isEmpty()) {
                ((Container) container).navigateTo(firstClassIn(container));
            }
        }
    }

    /*
     * The class a package node opens when clicked: the first class directly in the package, else the first one in
     * its first subpackage that has any. IntelliJ IDEA can only open classes and methods from a Gradle test row,
     * never a package, so this is the nearest a package node can get to its package.
     */
    private static Optional<ClassSource> firstClassIn(TestDescriptor container) {
        for (TestDescriptor child : container.getChildren()) {
            if (!(child instanceof Container) && child.getSource().filter(ClassSource.class::isInstance).isPresent()) {
                return child.getSource().map(ClassSource.class::cast);
            }
        }
        for (TestDescriptor child : container.getChildren()) {
            if (child instanceof Container) {
                Optional<ClassSource> nested = firstClassIn(child);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    /*
     * Orders a package's children the way an IDE's project tree does: subpackages first, then classes, each group by
     * name — the class's simple name, not its display name. Jupiter runs and reports children in this order.
     */
    private static void sortLikeProjectTree(TestDescriptor container) {
        List<TestDescriptor> children = new ArrayList<>(container.getChildren());
        children.sort(Comparator
                .comparing((TestDescriptor child) -> child instanceof Container ? 0 : 1)
                .thenComparing(PackageTreeSuiteEngine::sortName, NaturalOrder.INSTANCE));
        for (TestDescriptor child : children) {
            container.removeChild(child);
        }
        for (TestDescriptor child : children) {
            container.addChild(child);
        }
    }

    /* A package sorts by its own name, never by a display name that may carry a test count. */
    private static String sortName(TestDescriptor descriptor) {
        if (descriptor instanceof Container) {
            return lastSegment(((Container) descriptor).packageName);
        }
        return descriptor.getSource()
                .filter(ClassSource.class::isInstance)
                .map(source -> ((ClassSource) source).getJavaClass().getSimpleName())
                .orElse(descriptor.getDisplayName());
    }

    /*
     * Counts the tests the plan holds under each package, including its subpackages. The base package also takes
     * classes from outside it, which sit directly under the marker node. Tests a parameterized method or factory
     * generates only appear while it runs, so the run can end with a few more than these up-front counts.
     */
    private static Map<String, Long> countTestsByPackage(List<TestDescriptor> classDescriptors, String basePackage) {
        Map<String, Long> counts = new HashMap<>();
        for (TestDescriptor classDescriptor : classDescriptors) {
            Optional<String> packageName = packageOf(classDescriptor);
            if (packageName.isEmpty()) {
                continue;
            }
            long tests = classDescriptor.getDescendants().stream().filter(TestDescriptor::isTest).count();
            String current = packageName.get().startsWith(basePackage + ".") ? packageName.get() : basePackage;
            while (true) {
                counts.merge(current, tests, Long::sum);
                if (current.equals(basePackage)) {
                    break;
                }
                current = current.substring(0, current.lastIndexOf('.'));
            }
        }
        return counts;
    }

    private static String nodeName(String packageName, Map<String, Long> testCounts) {
        String name = lastSegment(packageName);
        return testCounts == null ? name : name + " (" + testCounts.getOrDefault(packageName, 0L) + ")";
    }

    private static Optional<String> packageOf(TestDescriptor classDescriptor) {
        return classDescriptor.getSource()
                .filter(ClassSource.class::isInstance)
                .map(source -> ((ClassSource) source).getJavaClass().getPackageName());
    }

    private static TestDescriptor containerFor(String packageName, String basePackage, TestDescriptor markerNode,
                                               Map<String, TestDescriptor> containersByPackage,
                                               Map<String, Long> testCounts) {
        TestDescriptor existing = containersByPackage.get(packageName);
        if (existing != null) {
            return existing;
        }
        if (!packageName.startsWith(basePackage + ".")) {
            return markerNode;
        }
        String parentPackage = packageName.substring(0, packageName.lastIndexOf('.'));
        TestDescriptor parent = containerFor(parentPackage, basePackage, markerNode, containersByPackage, testCounts);
        TestDescriptor container = new Container(
                parent.getUniqueId().append("package", lastSegment(packageName)),
                nodeName(packageName, testCounts),
                packageName,
                PackageSource.from(packageName));
        parent.addChild(container);
        containersByPackage.put(packageName, container);
        return container;
    }

    private static String lastSegment(String packageName) {
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private static final class Container extends AbstractTestDescriptor {

        private final String packageName;
        private TestSource source;

        private Container(UniqueId uniqueId, String displayName, String packageName, TestSource source) {
            super(uniqueId, displayName);
            this.packageName = packageName;
            this.source = source;
        }

        /*
         * Set once the tree is sorted, when the package's first class is known. The position is there only to make
         * the source differ from that class's own node: Gradle treats a node whose source equals its parent's as
         * part of the parent, which turns the class row into one IntelliJ IDEA can no longer open. The IDE builds a
         * row's link from the class name alone, so the position changes nothing about where the click lands.
         */
        private void navigateTo(Optional<ClassSource> firstClass) {
            firstClass.ifPresent(classSource -> source = ClassSource.from(classSource.getClassName(),
                    FilePosition.from(1)));
        }

        @Override
        public Optional<TestSource> getSource() {
            return Optional.ofNullable(source);
        }

        @Override
        public Type getType() {
            return Type.CONTAINER;
        }
    }
}
