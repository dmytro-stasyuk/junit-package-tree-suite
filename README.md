# JUnit Package Tree Suite

Run every test under a package and see the results laid out the way your project is laid out: one node per
package, nested by subpackage, with each test class under the package it lives in.

```java
@PackageTreeSuite
public class AllTests {
}
```

Running `AllTests` gives a test tree like this in the IDE:

```
tests (612)
  catalog (88)
    search (28)
      SearchFiltersTest
      SearchRankingTest
    CatalogBrowsingTest
  checkout (502)
    payment (173)
      card_payments (25)
        CardDeclinedTest
        CardExpiredTest
      PaymentRetryTest
    CheckoutFlowTest
  ...
```

## Why it exists

When you run a whole package of tests, IDEs and build tools list every test class at a single level, whatever the
package depth. In a project with a few hundred test classes spread over a deep package tree, the results of a full
run become one long flat list. That makes it hard to see which area of the product a failure belongs to, or how far
through the run you are.

The usual way to get a structured tree is to write an aggregating suite class for each package and nest them by
hand. Those classes have to be kept in step with the package layout as it changes, and a test that two suites both
reach runs twice.

This library builds the tree from the packages themselves. One annotated class gives the whole tree, and the tree
follows the package layout as it changes.

## Benefits

- **Results grouped by area.** Packages usually follow the product's features, so a failure shows up under the
  feature it belongs to.
- **No suite classes to maintain.** New packages and test classes appear in the tree on the next run.
- **Every test runs once.** Tests are not selected through several overlapping suites.
- **Progress you can read.** Each package node shows how many tests it holds, so you can compare the top number with
  the IDE's running count.
- **Your tests behave the same.** Tests are found and run by JUnit Jupiter as usual: extensions, lifecycle methods,
  `@Nested` classes, parameterized tests and display names all work unchanged.

## Features

- One node per package, nested by subpackage, with the test classes under their own package.
- Packages and classes in the same order as the IDE's project tree: subpackages first, then classes, each sorted by
  name, ignoring case and comparing numbers by value (`Item2` before `Item10`).
- A test count on every package node, such as `checkout (502)`. It can be turned off.
- Clicking a package node in IntelliJ IDEA opens the first test class in that package.
- Optional package runs: switch them on once, and running any package or folder from the IDE gives the nested tree,
  with no annotated class needed.
- The suite covers its own package by default, or any package you name.
- Classes can be left out by name pattern.
- IDEs show a run action on the suite class, as they do for JUnit's own `@Suite` classes.

## Getting started

Add the library next to JUnit Jupiter in your test dependencies.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    testImplementation("io.github.dmytro-stasyuk:junit-package-tree-suite:1.0.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.dmytro-stasyuk</groupId>
    <artifactId>junit-package-tree-suite</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

Then add an empty class annotated with `@PackageTreeSuite` at the top of the package tree you want to run, and run
that class from the IDE or the build:

```java
package com.example.tests;

import io.github.dmytrostasyuk.packagetreesuite.PackageTreeSuite;

@PackageTreeSuite
public class AllTests {
}
```

```shell
./gradlew test --tests com.example.tests.AllTests
```

The suite class holds no tests of its own. You can have as many suite classes as you like, for example one at the
top of the project and one per feature area.

## Package runs: no annotation needed

A suite class is the explicit way to get the tree. If you'd rather get it every time you run a package or folder,
switch package runs on. Add this line to `src/test/resources/junit-platform.properties`:

```properties
packagetreesuite.packageRuns.enabled=true
```

From then on, right-clicking any package and choosing Run gives the nested tree, with the package you clicked at
the top. It applies to any run that covers more than one test class. Running a single class or a single method stays
exactly as it was: flat, run by JUnit Jupiter.

Package runs are off by default. Like any JUnit configuration parameter, the setting can also come from a JVM system
property, for example in Gradle:

```kotlin
tasks.test {
    systemProperty("packagetreesuite.packageRuns.enabled", "true")
}
```

| Setting | Default | What it does |
|---|---|---|
| `packagetreesuite.packageRuns.enabled` | `false` | Gives every run that covers more than one test class the nested tree |
| `packagetreesuite.packageRuns.showTestCounts` | `true` | Ends each package node's name with the number of tests under it, in package runs |

## Suite class options

| Option | Default | What it does |
|---|---|---|
| `value` | the suite class's package | The package to run, together with all its subpackages |
| `showTestCounts` | `true` | Ends each package node's name with the number of tests under it |
| `excludeClassNamePatterns` | none | Regular expressions matched against fully qualified class names; matching classes are left out |

```java
@PackageTreeSuite(
        value = "com.example.tests",
        showTestCounts = false,
        excludeClassNamePatterns = ".*IntegrationTest")
public class FastTests {
}
```

## How it works

The library is a JUnit Platform test engine with the ID `package-tree-suite`. It acts in two cases: when a run's
only selection is a class annotated with `@PackageTreeSuite`, and, with package runs switched on, when a run covers
more than one test class. In every other run it reports nothing, so having it on the test classpath changes nothing
else.

The engine asks JUnit Jupiter to find the tests, then moves each test class under a node for its package. Running
the tests is handed back to Jupiter unchanged, so they run exactly as they would without the library.

Each test still runs once. A suite class has no test methods, so Jupiter skips it. In a package run, Jupiter finds
the same classes as the engine; a discovery filter that ships with the library, and that JUnit picks up on its own,
removes Jupiter's copy.

## Requirements

- Java 11 or later
- JUnit Jupiter 5.12 or later

## Known limitations

- **Only JUnit Jupiter tests** are included. Tests run by other engines, such as JUnit 4 (Vintage) or Cucumber, are
  not part of the tree.
- **Clicking a package node opens a class, not the package.** IntelliJ IDEA can open only classes and methods from a
  test result, so a package node opens the first test class in that package. A suite's top node opens the suite
  class.
- **Counts can end slightly higher than shown.** They come from the plan JUnit builds before the run. Tests that a
  parameterized method or test factory generates only appear while it runs.
- **No up-front total when the run goes through Gradle.** The IDE shows "N of M" only for runs it starts with its own
  JUnit runner. Through Gradle it receives results one at a time, which is what the counts on the nodes make up for.
- **Verified with Gradle only so far.** Running a suite class through Gradle, started from IntelliJ IDEA, is tested.
  Maven Surefire has not been verified yet. By default Surefire picks up only classes whose names match `Test*`,
  `*Test`, `*Tests` or `*TestCase`, so a suite class needs a matching name or an `<includes>` entry there.
- **A suite class only takes effect when it is run on its own.** When it is one of many classes in a run, such as a
  package run that happens to include it, it is ignored, so its tests don't run a second time.
- **Package runs inside a JUnit `@Suite` have not been verified.** With package runs switched on, a `@Suite` that
  selects several classes may show them nested too. Verify this in your project before relying on it.
- **Tests run twice if a JUnit `@Suite` covers them in the same run as a suite class.** Run a suite class on its own,
  or keep it out of runs that also select overlapping `@Suite` classes.

## Building

```shell
./mvnw install
```

## Releasing

Releases are published to Maven Central by the GitHub Actions workflow in `.github/workflows/release.yml`. To
release the current `-SNAPSHOT` version, push a tag named `rc` to the commit you want to release:

```shell
git tag rc
git push origin rc
```

The workflow then:

1. Takes `-SNAPSHOT` off the version and moves the `Unreleased` entries in `CHANGELOG.md` under the new version.
2. Builds, tests, signs and publishes the release to Maven Central, then tags it `release_X.Y.Z`.
3. Moves `main` on to the next minor version, `X.(Y+1).0-SNAPSHOT`.
4. Renames the `rc` tag to `rc_X.Y.Z` and creates a GitHub Release with the changelog entries.

Versions follow [Semantic Versioning](https://semver.org). For a patch or a new major version, set the version by
hand before tagging, for example `./mvnw versions:set -DnewVersion=2.0.0-SNAPSHOT -DgenerateBackupPoms=false`.

The workflow needs these repository secrets: `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` (a Central Portal user
token), and `GPG_PRIVATE_KEY` (the signing key, exported with `gpg --armor --export-secret-keys <KEY_ID>`). The key
has no passphrase; if it ever gets one, add a `GPG_PASSPHRASE` secret and pass it to `actions/setup-java` as
`gpg-passphrase`.

## License

MIT. See [LICENSE](LICENSE).
