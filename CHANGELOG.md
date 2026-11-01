# Changelog

All notable changes to JUnit Package Tree Suite will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com),
and this project adheres to [Semantic Versioning](https://semver.org).

## Unreleased

### Added

- `@PackageTreeSuite`: an empty class with this annotation runs every JUnit Jupiter test under its package, shown in the test tree nested by subpackage, with each test class under the package it lives in.
- Packages and classes appear in the same order as the IDE's project tree: subpackages first, then classes, each sorted by name, ignoring case and comparing numbers by value.
- Each package node shows the number of tests under it, such as `checkout (42)`. The `showTestCounts` option turns this off.
- Clicking a package node in IntelliJ IDEA opens the first test class in that package.
- The suite runs its own package by default; `value` names another one, and `excludeClassNamePatterns` leaves classes out by name.
- Package runs: with `packagetreesuite.packageRuns.enabled=true` in `junit-platform.properties`, running any package or folder from the IDE gives the nested tree, with no annotated class needed. Off by default.

### Changed

### Fixed

### Removed
