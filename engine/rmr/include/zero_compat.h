// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_ZERO_COMPAT_H
#define RMR_ZERO_COMPAT_H

#include "zero.h"

#if defined(RMR_BUILD_HOST_TOOLING) && (RMR_BUILD_HOST_TOOLING)
#include "rmr_host_compat.h"
#else
#include "rmr_baremetal_compat.h"
#endif

#include "rmr_corelib.h"

#endif
