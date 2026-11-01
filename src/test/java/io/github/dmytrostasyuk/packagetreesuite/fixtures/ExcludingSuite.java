package io.github.dmytrostasyuk.packagetreesuite.fixtures;

import io.github.dmytrostasyuk.packagetreesuite.PackageTreeSuite;

/** Fixture: a suite over the same package, named explicitly, with test counts off and one class excluded. */
@PackageTreeSuite(
        value = "io.github.dmytrostasyuk.packagetreesuite.fixtures",
        showTestCounts = false,
        excludeClassNamePatterns = ".*\\.Skipped")
public class ExcludingSuite {
}
