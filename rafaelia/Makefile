CC ?= cc
CFLAGS ?= -std=c11 -O2 -Wall -Wextra -Wpedantic
LDFLAGS ?=

BUILD_DIR := build
BIN := $(BUILD_DIR)/rafaelia_runner
SRC := src/raf_runtime.c src/rafaelia_runner.c
HDR := include/raf_runtime.h

.PHONY: all run clean

all: $(BIN)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BIN): $(SRC) $(HDR) | $(BUILD_DIR)
	$(CC) $(CFLAGS) -Iinclude $(SRC) -o $(BIN) $(LDFLAGS)

run: $(BIN)
	mkdir -p ../reports
	./$(BIN) .. ../reports/rafaelia_runtime.tsv

clean:
	rm -rf $(BUILD_DIR)
