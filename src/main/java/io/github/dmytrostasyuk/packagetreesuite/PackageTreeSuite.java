package io.github.dmytrostasyuk.packagetreesuite;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.*;

/**
 * Marks a class whose run executes every JUnit Jupiter test under a package, shown in the test tree nested by
 * subpackage: one node per package, with each test class under the package it lives in.
 * <pre>{@code
 * @PackageTreeSuite
 * public class AllTests {
 * }
 * }</pre>
 * The marked class holds no tests of its own. Jupiter ignores it, so the tests it selects run once, through
 * {@link PackageTreeSuiteEngine} only.
 * <p>
 * Meta-annotated with {@link Testable} so IDEs recognise a marked class as runnable and offer a run action for it,
 * as they do for JUnit's own {@code @Suite} classes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Testable
public @interface PackageTreeSuite {

    /**
     * The package to run, together with all its subpackages. When left empty, the package of the annotated class.
     */
    String value() default "";

    /**
     * Whether each package node's name ends with the number of tests under it, such as {@code checkout (42)}, so
     * progress can be read against the IDE's running count. The numbers come from the plan built before the run,
     * so tests generated while it runs (parameterized methods, test factories) can push the final count higher.
     */
    boolean showTestCounts() default true;

    /**
     * Regular expressions matched against fully qualified class names; a class that matches any of them is left
     * out of the suite. Empty by default.
     */
    String[] excludeClassNamePatterns() default {};
}
