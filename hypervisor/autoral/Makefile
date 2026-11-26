# SPDX-License-Identifier: GPL-2.0-only
# Pure C core: no C runtime linkage. Only tests are hosted.
CC ?= cc
NM ?= nm
CFLAGS = -std=c11 -O2 -Wall -Wextra -Werror -Wpedantic -ffreestanding -fno-builtin -fno-stack-protector
OBJ = build/rafhv.o build/rafhv_a32.o
.PHONY: test audit clean
build:
	mkdir -p build
build/rafhv.o: rafhv.c rafhv.h | build
	$(CC) $(CFLAGS) -c $< -o $@
build/rafhv_a32.o: rafhv_a32.c rafhv_a32.h rafhv.h | build
	$(CC) $(CFLAGS) -c $< -o $@
test: $(OBJ)
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -Wpedantic tests/rafhv_selftest.c $(OBJ) -o build/rafhv_selftest
	./build/rafhv_selftest
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -Wpedantic tests/rafhv_a32_selftest.c $(OBJ) -o build/rafhv_a32_selftest
	./build/rafhv_a32_selftest
audit: $(OBJ)
	$(CC) -r $(OBJ) -o build/rafhv_combined.o
	@test -z "$$($(NM) -u build/rafhv_combined.o)" || { $(NM) -u build/rafhv_combined.o; exit 1; }
	@echo "RAFHV V0.1 freestanding object: no unresolved symbols"
clean:
	rm -rf build
