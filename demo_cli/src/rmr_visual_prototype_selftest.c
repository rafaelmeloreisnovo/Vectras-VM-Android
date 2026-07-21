// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_visual_prototype.h"

#include <stdio.h>
#include <string.h>

#define CHECK(x) do { if (!(x)) { fprintf(stderr, "FAIL:%d: %s\n", __LINE__, #x); return 1; } } while (0)

static void make_gray(uint8_t out[64], unsigned pattern, unsigned bias) {
    for (unsigned y = 0; y < 8; ++y) {
        for (unsigned x = 0; x < 8; ++x) {
            unsigned dark = 0;
            if (pattern == 0) dark = (x < 4);
            if (pattern == 1) dark = (y >= 3 && y <= 4);
            if (pattern == 2) dark = (x == 3 || x == 4 || y < 3);
            out[y * 8 + x] = (uint8_t)(dark ? bias : 230u);
        }
    }
}

static int build_desc(unsigned pattern, unsigned bias, const int16_t *angles, uint32_t count, RmR_VisionDescriptor *out) {
    uint8_t gray[64];
    make_gray(gray, pattern, bias);
    return (int)RmR_Vision_BuildDescriptor(gray, 8, 8, 8, angles, count, out);
}

int main(void) {
    static const int16_t dog_front_angles[] = {0, 0, 45, 315, 0, 315};
    static const int16_t dog_left_angles[] = {90, 90, 135, 45, 90, 135};
    static const int16_t car_angles[] = {0, 0, 0, 180, 180, 180};
    static const int16_t tree_angles[] = {90, 90, 90, 45, 135, 90};

    RmR_VisionDescriptor dog_front, dog_front_lighter, dog_left, car, tree;
    CHECK(build_desc(0, 10, dog_front_angles, 6, &dog_front) == 0);
    CHECK(build_desc(0, 35, dog_front_angles, 6, &dog_front_lighter) == 0);
    CHECK(build_desc(0, 10, dog_left_angles, 6, &dog_left) == 0);
    CHECK(build_desc(1, 10, car_angles, 6, &car) == 0);
    CHECK(build_desc(2, 10, tree_angles, 6, &tree) == 0);

    RmR_VisualPrototype dog, car_p, tree_p;
    CHECK(RmR_VisualPrototype_Init(&dog, "cachorro", 0) == 0);
    CHECK(RmR_VisualPrototype_AddView(&dog, RMR_VISUAL_VIEW_FRONT, RMR_VISUAL_SAMPLE_CONFIRMED, 1,
                                      dog_front.gray_crc32c, &dog_front, 0) == 0);
    CHECK(RmR_VisualPrototype_AddView(&dog, RMR_VISUAL_VIEW_LEFT, RMR_VISUAL_SAMPLE_CONFIRMED, 2,
                                      dog_left.gray_crc32c, &dog_left, 0) == 0);
    CHECK(RmR_VisualPrototype_AddView(&dog, RMR_VISUAL_VIEW_FRONT, RMR_VISUAL_SAMPLE_CONFIRMED, 3,
                                      dog_front_lighter.gray_crc32c, &dog_front_lighter, 0) == RMR_VISUAL_STATUS_DUPLICATE_VIEW);
    CHECK(RmR_VisualPrototype_Seal(&dog) == 0);
    CHECK(RmR_VisualPrototype_Verify(&dog) == 0);

    CHECK(RmR_VisualPrototype_Init(&car_p, "carro", 0) == 0);
    CHECK(RmR_VisualPrototype_AddView(&car_p, RMR_VISUAL_VIEW_FRONT, RMR_VISUAL_SAMPLE_CONFIRMED, 1,
                                      car.gray_crc32c, &car, 0) == 0);
    CHECK(RmR_VisualPrototype_Seal(&car_p) == 0);

    CHECK(RmR_VisualPrototype_Init(&tree_p, "arvore", 0) == 0);
    CHECK(RmR_VisualPrototype_AddView(&tree_p, RMR_VISUAL_VIEW_FRONT, RMR_VISUAL_SAMPLE_CONFIRMED, 1,
                                      tree.gray_crc32c, &tree, 0) == 0);
    CHECK(RmR_VisualPrototype_Seal(&tree_p) == 0);

    RmR_VisualMatch dog_match, car_match;
    CHECK(RmR_VisualPrototype_Compare(&dog, &dog_front_lighter, RMR_VISUAL_VIEW_FRONT, &dog_match) == 0);
    CHECK(RmR_VisualPrototype_Compare(&car_p, &dog_front_lighter, RMR_VISUAL_VIEW_FRONT, &car_match) == 0);
    CHECK(dog_match.score_q16 > car_match.score_q16);

    RmR_VisualPrototype slots[4];
    RmR_VisualStore store;
    CHECK(RmR_VisualStore_Init(&store, slots, 4) == 0);
    CHECK(RmR_VisualStore_Upsert(&store, &dog, NULL) == 0);
    CHECK(RmR_VisualStore_Upsert(&store, &car_p, NULL) == 0);
    CHECK(RmR_VisualStore_Upsert(&store, &tree_p, NULL) == 0);
    RmR_VisualClassification cls;
    CHECK(RmR_VisualStore_Classify(&store, &dog_front_lighter, RMR_VISUAL_VIEW_FRONT,
                                   32768u, 2048u, &cls) == 0);
    CHECK(cls.class_id == dog.class_id);

    uint8_t capsule[4096];
    size_t written = 0, consumed = 0;
    CHECK(RmR_VisualPrototype_Serialize(&dog, capsule, sizeof(capsule), &written) == 0);
    RmR_VisualPrototype restored;
    CHECK(RmR_VisualPrototype_Deserialize(capsule, written, &restored, &consumed) == 0);
    CHECK(consumed == written);
    CHECK(strcmp(restored.label, "cachorro") == 0);
    CHECK(restored.view_count == 2u);
    capsule[40] ^= 1u;
    CHECK(RmR_VisualPrototype_Deserialize(capsule, written, &restored, &consumed) == RMR_VISUAL_STATUS_CRC_MISMATCH);

    printf("rmr_visual_prototype_selftest: OK dog=%u car=%u margin=%u capsule=%zu\n",
           dog_match.score_q16, car_match.score_q16, cls.margin_q16, written);
    return 0;
}
