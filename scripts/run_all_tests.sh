#!/usr/bin/env bash
#
# Runs the Android and/or iOS unit test suites and writes a combined
# Markdown summary to test-results/latest.md (git-ignored — see README.md
# "Testing" section).
#
# Usage:
#   scripts/run_all_tests.sh                # run both platforms (default)
#   scripts/run_all_tests.sh android         # Android only
#   scripts/run_all_tests.sh ios             # iOS only
#   scripts/run_all_tests.sh all
#   scripts/run_all_tests.sh android --parse-only
#   scripts/run_all_tests.sh ios --parse-only
#
# --parse-only skips invoking the test command and just parses whatever
# result files are already on disk (Android's JUnit XML under
# android/app/build/test-results/testDebugUnitTest/, or the iOS
# xcodebuild log at test-results/ios-xcodebuild.log). This is what CI
# uses: the existing test steps in .github/workflows/ci.yml already ran
# the tests, so the report step just reads the results rather than
# re-running the (slower, especially on iOS) suite a second time.
#
# Exit code is non-zero if either requested platform had a test failure
# (or its results couldn't be found at all).

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="$REPO_ROOT/test-results"
IOS_LOG="$RESULTS_DIR/ios-xcodebuild.log"

PLATFORM="all"
PARSE_ONLY=false

for arg in "$@"; do
  case "$arg" in
    android|ios|all) PLATFORM="$arg" ;;
    --parse-only) PARSE_ONLY=true ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: $0 [android|ios|all] [--parse-only]" >&2
      exit 1
      ;;
  esac
done

RUN_ANDROID=false
RUN_IOS=false
case "$PLATFORM" in
  android) RUN_ANDROID=true ;;
  ios) RUN_IOS=true ;;
  all) RUN_ANDROID=true; RUN_IOS=true ;;
esac

mkdir -p "$RESULTS_DIR"

GEN_ARGS=()

if $RUN_ANDROID; then
  ANDROID_DIR="$REPO_ROOT/android"
  ANDROID_XML_DIR="$ANDROID_DIR/app/build/test-results/testDebugUnitTest"

  if ! $PARSE_ONLY; then
    echo "==> Running Android unit tests (./gradlew testDebugUnitTest)"
    (cd "$ANDROID_DIR" && chmod +x gradlew && ./gradlew testDebugUnitTest)
    echo "==> Android gradle invocation exited with $?"
  fi

  GEN_ARGS+=(--android-xml-dir "$ANDROID_XML_DIR")
fi

if $RUN_IOS; then
  IOS_DIR="$REPO_ROOT/ios"

  if ! $PARSE_ONLY; then
    echo "==> Running iOS unit tests (xcodebuild test)"
    if command -v xcodegen >/dev/null 2>&1; then
      (cd "$IOS_DIR" && xcodegen generate)
    fi

    SIM_NAME=$(xcrun simctl list devices available | grep -m1 -E "iPhone (1[4-9]|[2-9][0-9])" | sed -E 's/^[[:space:]]*([^(]+) \(.*/\1/' | sed -E 's/[[:space:]]+$//')
    if [ -z "$SIM_NAME" ]; then
      SIM_NAME="iPhone 16"
    fi
    echo "==> Using simulator: $SIM_NAME"

    set -o pipefail
    (cd "$IOS_DIR" && xcodebuild -scheme ThinkLessScheduleMore -sdk iphonesimulator \
      -destination "platform=iOS Simulator,name=$SIM_NAME" test) | tee "$IOS_LOG"
    echo "==> iOS xcodebuild invocation exited with $?"
  fi

  GEN_ARGS+=(--ios-log "$IOS_LOG")
fi

python3 "$REPO_ROOT/scripts/gen_test_report.py" "${GEN_ARGS[@]}" --out "$RESULTS_DIR/latest.md"
REPORT_EXIT=$?

echo
cat "$RESULTS_DIR/latest.md"

exit $REPORT_EXIT
