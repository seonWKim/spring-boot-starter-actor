.PHONY: help test test-core test-core-boot3 test-metrics build clean format verify-all

# Default target
help:
	@echo "Spring Boot Starter Actor - Makefile targets"
	@echo ""
	@echo "  make test          - Run all tests (core, core-boot3 sync, core-boot3, metrics)"
	@echo "  make test-core     - Run core module tests only"
	@echo "  make test-core-boot3 - Run core-boot3 module tests only"
	@echo "  make test-metrics  - Run metrics module tests only"
	@echo "  make build        - Clean build with Spotless check"
	@echo "  make clean        - Clean all build artifacts"
	@echo "  make format       - Apply Spotless formatting"
	@echo "  make verify-all   - Run format + build + test (full verification)"
	@echo ""

## Run all tests (recommended: full verification pipeline)
test:
	./gradlew runTest

## Run tests per module
test-core:
	./gradlew :core:test

test-core-boot3:
	./gradlew :core-boot3:syncAllBoot3Sources
	./gradlew :core-boot3:test

test-metrics:
	./gradlew :metrics:test

## Build (includes Spotless check)
build:
	./gradlew clean build

## Clean build artifacts
clean:
	./gradlew clean

## Apply Spotless code formatting
format:
	./gradlew spotlessApply

## Full verification: format, build, and test
verify-all: format build test
