// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_isorf.h - ISOraf: armazenamento lógico denso com físico esparso (sem compressão) */
#ifndef RMR_ISORF_H
#define RMR_ISORF_H

#include "rmr_types.h"

typedef struct {
  u64 base_bit;
  u32 word_offset;
  u32 word_count;
  u8  used;
} RmR_ISOraf_Page;

typedef struct {
  RmR_ISOraf_Page *pages;
  u32 page_count;
  u64 *data_words;
  u32 data_word_count;
  u32 data_word_used;
  u32 page_bits;
} RmR_ISOraf_Store;

typedef struct {
  u64 logical_bits;
  u64 physical_bits;
  u32 pages_used;
} RmR_ISOraf_Stats;

typedef struct {
  u64 magic;
  u64 identity;
  u64 logical_bits;
  u64 physical_bits;
  u32 page_bits;
  u32 page_count;
  u32 pages_used;
  u32 data_word_used;
} RmR_ISOraf_Manifest;

void RmR_ISOraf_Init(
  RmR_ISOraf_Store *st,
  RmR_ISOraf_Page *pages,
  u32 page_count,
  u64 *data_words,
  u32 data_word_count,
  u32 page_bits
);

u8 RmR_ISOraf_SetBit(RmR_ISOraf_Store *st, u64 bit_index, u8 value);
u8 RmR_ISOraf_GetBit(const RmR_ISOraf_Store *st, u64 bit_index);
void RmR_ISOraf_StatsGet(const RmR_ISOraf_Store *st, RmR_ISOraf_Stats *out);
u64 RmR_ISOraf_Identity(const RmR_ISOraf_Store *st);
u8 RmR_ISOraf_ExportManifest(const RmR_ISOraf_Store *st, RmR_ISOraf_Manifest *out);
u8 RmR_ISOraf_RebuildCheck(const RmR_ISOraf_Store *st, const RmR_ISOraf_Manifest *mf);
u32 RmR_ISOraf_ExportMatrixMap(const RmR_ISOraf_Store *st, u64 *base_bits_out, u32 max_entries);

#endif
