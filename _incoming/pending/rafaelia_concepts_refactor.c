// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
#include <stdint.h>
#include <stddef.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define DIM 7
#define ATTRACTOR 42
#define LANGS 8

typedef struct {
    double state[DIM];
    double prev[DIM];
    double ema[DIM];
    double C, H, phi;
    double alpha;
    uint64_t hash;
    uint32_t ticks;
} Node;

static inline double wrap01(double x){ x -= floor(x); return (x < 0.0) ? x + 1.0 : x; }
static inline double clampd(double x, double lo, double hi){ return x < lo ? lo : (x > hi ? hi : x); }
static inline double torus_distance(double a, double b){ double d=fabs(a-b); return d > 0.5 ? 1.0-d : d; }

static uint64_t rotl64(uint64_t v, unsigned k){ return (v << k) | (v >> (64-k)); }
static uint64_t fnv1a64(const uint8_t *restrict p, size_t n){
    uint64_t h=1469598103934665603ULL;
    for(size_t i=0;i<n;i++){ h ^= p[i]; h *= 0x100000001B3ULL; }
    return h;
}
static uint32_t crc32c(const uint8_t *p, size_t n){
    uint32_t crc=~0u;
    for(size_t i=0;i<n;i++){ crc ^= p[i]; for(int j=0;j<8;j++) crc = (crc&1u)?((crc>>1)^0x82F63B78u):(crc>>1); }
    return ~crc;
}
static uint8_t xor_acc(const uint8_t *p, size_t n){ uint8_t a=0; for(size_t i=0;i<n;i++) a ^= p[i]; return a; }

static double entropy_shannon_coarse(const uint8_t *buf, size_t len){
    int bins[16]={0};
    if(!buf || len==0) return 0.0;
    for(size_t i=0;i<len;i++) bins[buf[i]>>4]++;
    double H=0.0, inv = 1.0/(double)len;
    for(int i=0;i<16;i++) if(bins[i]){ double p = bins[i]*inv; H -= p * (log(p)/log(2.0)); }
    return H / 4.0; /* normalize 0..1 */
}

static uint32_t entropy_milli(const uint8_t *buf, size_t len){
    if(!buf || len==0) return 0;
    uint8_t seen[256]={0}; uint32_t unique=0, transitions=0;
    for(size_t i=0;i<len;i++){ if(!seen[buf[i]]){seen[buf[i]]=1; unique++;} if(i && buf[i]!=buf[i-1]) transitions++; }
    return (unique*6000u)/256u + ((len>1)?(transitions*2000u)/(uint32_t)(len-1):0);
}

static void toroidal_map(Node *n, uint64_t h, uint32_t st){
    n->state[0] = wrap01(n->C);
    n->state[1] = wrap01(n->H);
    n->state[2] = wrap01((double)((h>>0) & 0xFFFFu) / 65536.0);
    n->state[3] = wrap01((double)((h>>16)& 0xFFFFu) / 65536.0);
    n->state[4] = wrap01((double)((h>>32)& 0xFFFFu) / 65536.0);
    n->state[5] = wrap01((double)((h>>48)& 0xFFFFu) / 65536.0);
    n->state[6] = wrap01((double)(st % ATTRACTOR) / (double)ATTRACTOR);
}

static double spectral_match(const double *s, const double *h, size_t n){
    double num=0,ns=0,nh=0;
    for(size_t i=0;i<n;i++){ num += s[i]*h[i]; ns += s[i]*s[i]; nh += h[i]*h[i]; }
    return (ns>0 && nh>0) ? num/sqrt(ns*nh) : 0.0;
}

static uint64_t merkle_pair(uint64_t a, uint64_t b){
    uint8_t x[16]; memcpy(x,&a,8); memcpy(x+8,&b,8);
    return fnv1a64(x,16) ^ ((uint64_t)crc32c(x,16)<<32);
}

static void node_step(Node *n, const uint8_t *payload, size_t len, double *sig, const double *cardio, size_t fftn, uint64_t *lang){
    memcpy(n->prev, n->state, sizeof(n->state));
    uint64_t h = fnv1a64(payload,len);
    uint32_t c = crc32c(payload,len);
    double Hs = entropy_shannon_coarse(payload,len);
    double He = (double)entropy_milli(payload,len)/8000.0;
    double Hin = clampd(0.5*Hs + 0.5*He, 0.0, 1.0);
    double Cin = (double)(h & 0xFFFFu)/65535.0;

    n->alpha = clampd(0.05 + 0.4*Hin, 0.05, 0.45);
    n->C = (1.0-n->alpha)*n->C + n->alpha*Cin;
    n->H = (1.0-n->alpha)*n->H + n->alpha*Hin;

    n->hash ^= h ^ ((uint64_t)c<<32);
    for(int i=0;i<DIM;i++){ n->hash ^= (uint64_t)(n->state[i]*1e9); n->hash = rotl64(n->hash, 5) * 0x100000001B3ULL; }

    n->ticks = (n->ticks + 1u) % ATTRACTOR;
    toroidal_map(n, n->hash, n->ticks);

    double mean_phase=0.0, drift=0.0;
    for(int i=0;i<DIM;i++){ mean_phase += n->state[i]; drift += torus_distance(n->prev[i], n->state[i]); n->ema[i] = 0.8*n->ema[i] + 0.2*n->state[i]; }
    mean_phase/=DIM; drift/=DIM;
    double Cgeom = 1.0 - drift;
    n->phi = (1.0 - n->H) * ((n->C + Cgeom)*0.5) * cos(2.0*M_PI*mean_phase);

    for(size_t k=0;k<fftn;k++) sig[k] = 0.3*sig[k] + 0.7*sin((k+1.0)*(n->state[2]+n->state[3]));
    double R = spectral_match(sig, cardio, fftn);
    for(int l=0;l<LANGS;l++) lang[l] = merkle_pair(lang[l], ((uint64_t)(R*1e9) ^ n->hash ^ ((uint64_t)l<<48)));
}

int main(void){
    enum { NODES=16, N=220000, L=256, FFTN=64 };
    Node nodes[NODES]; memset(nodes, 0, sizeof(nodes));
    for(int i=0;i<NODES;i++){ nodes[i].C=0.5; nodes[i].H=0.5; nodes[i].hash=1469598103934665603ULL ^ (uint64_t)i; }

    uint8_t payload[NODES][L];
    for(int n=0;n<NODES;n++) for(size_t i=0;i<L;i++) payload[n][i]=(uint8_t)((i*131u) ^ (n*17u+i));

    double sig[NODES][FFTN], cardio[FFTN]; memset(sig,0,sizeof(sig));
    for(size_t k=0;k<FFTN;k++) cardio[k] = 0.5 + 0.5*cos(2.0*M_PI*k/(double)(FFTN-1));

    uint64_t lang[LANGS]={0};
    clock_t t0=clock();
    double checksum=0.0, pi_max=0.0;

    for(int t=0;t<N;t++){
        for(int n=0;n<NODES;n++){
            node_step(&nodes[n], payload[n], L, sig[n], cardio, FFTN, lang);
            checksum += nodes[n].phi + nodes[n].alpha;
            if(nodes[n].ticks && nodes[n].H > pi_max) pi_max = nodes[n].H;
            payload[n][t % L] ^= (uint8_t)(xor_acc(payload[n], L) + t + n);
        }
    }

    uint64_t root=0; for(int i=0;i<LANGS;i++) root = merkle_pair(root, lang[i]);
    clock_t t1=clock();
    double ms = 1000.0*(double)(t1-t0)/(double)CLOCKS_PER_SEC;

    printf("nodes=%d iter=%d attractor=%d\n", NODES, N, ATTRACTOR);
    printf("time_ms=%.3f ops_per_sec=%.0f\n", ms, (NODES*(double)N)*1000.0/ms);
    printf("node0.C=%.6f node0.H=%.6f node0.alpha=%.6f node0.phi=%.6f\n", nodes[0].C, nodes[0].H, nodes[0].alpha, nodes[0].phi);
    printf("pi_max=%.6f merkle_root=0x%016llx checksum=%.6f\n", pi_max, (unsigned long long)root, checksum);
    return 0;
}
