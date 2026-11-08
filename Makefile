.PHONY: help test test-core test-metrics build clean format verify-all

# Default target
help:
	@echo "Spring Boot Starter Actor - Makefile targets"
	@echo ""
	@echo "  make test          - Run all tests"
	@echo "  make test-core     - Run core module tests only"
	@echo "  make test-metrics  - Run metrics module tests only"
	@echo "  make build        - Clean build with Spotless check"
	@echo "  make clean        - Clean all build artifacts"
	@echo "  make format       - Apply Spotless formatting"
	@echo "  make verify-all   - Run format + build + test (full verification)"
	@echo ""

## Run all tests (recommended: full verification pipeline)
test:
	./gradlew test

## Run tests per module
test-core:
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
