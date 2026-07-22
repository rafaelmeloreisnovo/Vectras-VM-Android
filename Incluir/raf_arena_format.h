/* SPDX-License-Identifier: GPL-2.0-only */
/* SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo */
/* ============================================================
 * RAF_ARENA FORMAT — contrato binario compartilhado
 * Usado por: raf_skill_forge.sh (escreve) e raf_arena_tool (le)
 * Autor:   RafCode-Phi / RafaelVerboOmega
 * Layout:  HEADER (64B) + N * RECORD (64B) + N * PAYLOAD (var, 16B-aligned)
 *
 * Regras:
 *  - tudo little-endian, tudo fixed-size na parte fixa
 *  - nenhum campo de texto livre no record; nomes ficam so no PAYLOAD
 *    (o .md/.json original), nunca no binario quente
 *  - arena = append-only; nunca se reescreve um record já commitado
 *  - CRC32c (Castagnoli) cobre cada record E cada payload
 * ============================================================ */
#ifndef RAF_ARENA_FORMAT_H
#define RAF_ARENA_FORMAT_H

#define RAF_MAGIC0   0x46415252U   /* "RRAF" lido como u32 LE */
#define RAF_MAGIC1   0x4C495045U   /* "EPIL" -> junto "RRAFEPIL" = zipraf marker */
#define RAF_VERSION  1U
#define RAF_PERIOD   42U

/* ---- HEADER: primeiros 64 bytes do arquivo de arena ---- */
typedef struct __attribute__((aligned(64), packed)) {
    unsigned int magic0;      /* RAF_MAGIC0 */
    unsigned int magic1;      /* RAF_MAGIC1 */
    unsigned int version;     /* RAF_VERSION */
    unsigned int record_count;/* quantos records ja commitados */
    unsigned int record_size; /* sizeof(RafRecord) = 64 sempre */
    unsigned int created_ts;  /* unix time da criacao da arena */
    unsigned int last_ts;     /* unix time do ultimo append */
    unsigned int header_crc;  /* crc32c dos 60 bytes anteriores */
    unsigned int _pad[8];     /* reservado, zero */
} RafArenaHeader;             /* sizeof = 64 */

/* ---- RECORD: um por skill gerado/executado ---- */
typedef struct __attribute__((aligned(64), packed)) {
    unsigned int state[7];     /* s=(u,v,psi,chi,rho,delta,sigma) Q16.16 */
    unsigned int crc_state;    /* crc32c do state[7] */
    unsigned int cycle;        /* ciclo final (mod 42) */
    unsigned int domain_tag;   /* cksum(domain) */
    unsigned int formula_ck;   /* cksum(formula) */
    unsigned int ttl;          /* ttl final */
    unsigned int payload_off;  /* offset do payload (a partir do fim do header) */
    unsigned int payload_len;  /* tamanho do payload em bytes */
    unsigned int payload_crc;  /* crc32c do payload bruto */
    unsigned int seq;          /* indice sequencial deste record (0-based) */
    unsigned int record_crc;   /* crc32c dos 60 bytes anteriores deste record */
} RafRecord;                   /* sizeof = 64 */

#endif /* RAF_ARENA_FORMAT_H */
