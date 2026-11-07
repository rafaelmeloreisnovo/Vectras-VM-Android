# SPDX-License-Identifier: GPL-2.0-only
# Pure C core: no C runtime linkage. Only tests are hosted.
CC ?= cc
NM ?= nm
CFLAGS = -std=c11 -O2 -Wall -Wextra -Werror -Wpedantic -ffreestanding -fno-builtin -fno-stack-protector
OBJ = build/rafhv.o
.PHONY: test audit clean
build:
	mkdir -p build
$(OBJ): rafhv.c rafhv.h | build
	$(CC) $(CFLAGS) -c $< -o $@
test: $(OBJ)
	$(CC) -std=c11 -O2 -Wall -Wextra -Werror -Wpedantic tests/rafhv_selftest.c $(OBJ) -o build/rafhv_selftest
	./build/rafhv_selftest
audit: $(OBJ)
	@test -z "$$($(NM) -u $(OBJ))" || { $(NM) -u $(OBJ); exit 1; }
	@echo "RAFHV V0.1 freestanding object: no unresolved symbols"
clean:
	rm -rf build
