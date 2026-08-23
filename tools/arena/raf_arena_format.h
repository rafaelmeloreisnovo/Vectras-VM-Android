/* SPDX-License-Identifier: GPL-2.0-only */
#ifndef RAF_ARENA_FORMAT_H
#define RAF_ARENA_FORMAT_H

/*
 * RAF Arena binary contract.
 *
 * V1 is preserved for read/verification compatibility. It never contained a
 * serialized domain_tag despite older comments claiming it did.
 *
 * V2 is the write format. It grows the record to 80 bytes so domain_tag can
 * be represented without overloading any v1 field. Three reserved words keep
 * the fixed record 16-byte aligned and leave room for future schema growth.
 */
#define RAF_MAGIC0 0x46415252U
#define RAF_MAGIC1 0x4C495045U
#define RAF_VERSION_V1 1U
#define RAF_VERSION_V2 2U
#define RAF_HEADER_SIZE 64U
#define RAF_RECORD_V1_SIZE 64U
#define RAF_RECORD_V2_SIZE 80U

#pragma pack(push, 1)
typedef struct {
    unsigned int magic0;
    unsigned int magic1;
    unsigned int version;
    unsigned int record_count;
    unsigned int record_size;
    unsigned int created_ts;
    unsigned int last_ts;
    unsigned int header_crc;
    unsigned int reserved[8];
} RafArenaHeader;

typedef struct {
    unsigned int state[7];
    unsigned int crc_state;
    unsigned int cycle;
    unsigned int formula_ck;
    unsigned int ttl;
    unsigned int payload_off;
    unsigned int payload_len;
    unsigned int payload_crc;
    unsigned int seq;
    unsigned int record_crc;
} RafRecordV1;

typedef struct {
    unsigned int state[7];
    unsigned int crc_state;
    unsigned int cycle;
    unsigned int domain_tag;
    unsigned int formula_ck;
    unsigned int ttl;
    unsigned int payload_off;
    unsigned int payload_len;
    unsigned int payload_crc;
    unsigned int seq;
    unsigned int reserved[3];
    unsigned int record_crc;
} RafRecordV2;
#pragma pack(pop)

_Static_assert(sizeof(RafArenaHeader) == RAF_HEADER_SIZE, "RafArenaHeader layout drift");
_Static_assert(sizeof(RafRecordV1) == RAF_RECORD_V1_SIZE, "RafRecordV1 layout drift");
_Static_assert(sizeof(RafRecordV2) == RAF_RECORD_V2_SIZE, "RafRecordV2 layout drift");

#endif /* RAF_ARENA_FORMAT_H */
