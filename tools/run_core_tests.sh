#!/bin/bash
# Compile :core and run its tests with a bare kotlinc + JUnit (no Gradle/Maven needed).
# Usage: KOTLINC_HOME=/path/to/kotlinc JUNIT_JARS=/usr/share/java ./tools/run_core_tests.sh
set -e
cd "$(dirname "$0")/.."
K="${KOTLINC_HOME:?set KOTLINC_HOME to the kotlinc directory}"
J="${JUNIT_JARS:-/usr/share/java}"
CP="$K/lib/kotlin-test.jar:$K/lib/kotlin-test-junit.jar:$J/junit4.jar:$J/hamcrest-core.jar"
OUT=$(mktemp -d); STUB=$(mktemp -d)/Serializable.kt
printf 'package kotlinx.serialization\n@Target(AnnotationTarget.CLASS) annotation class Serializable\n' > "$STUB"
# io/DataLoader.kt needs kotlinx.serialization.json, so it is only built by Gradle.
"$K/bin/kotlinc" $(find core/src/commonMain core/src/commonTest -name '*.kt' -not -path '*/io/*') "$STUB" -cp "$CP" -d "$OUT" 2>&1 | grep -v warning || true
CLASSES=$(cd "$OUT" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | tr '\n' ' ')
java -cp "$OUT:$K/lib/kotlin-stdlib.jar:$CP" org.junit.runner.JUnitCore $CLASSES
