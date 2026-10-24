/*
 * ChaCha20 per RFC 8439 (vectorized quarter-round formulation).
 * Independent implementation, no external crypto dependencies.
 */
#include "chacha20.h"

#include <string.h>

#define ROTL32(v, c) (((v) << (c)) | ((v) >> (32 - (c))))

#define QR(a, b, c, d)              \
    do {                            \
        a += b; d ^= a; d = ROTL32(d, 16); \
        c += d; b ^= c; b = ROTL32(b, 12); \
        a += b; d ^= a; d = ROTL32(d, 8);  \
        c += d; b ^= c; b = ROTL32(b, 7);  \
    } while (0)

static uint32_t load32_le(const uint8_t *p) {
    return (uint32_t)p[0] |
           ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) |
           ((uint32_t)p[3] << 24);
}

static void store32_le(uint8_t *p, uint32_t v) {
    p[0] = (uint8_t)(v & 0xff);
    p[1] = (uint8_t)((v >> 8) & 0xff);
    p[2] = (uint8_t)((v >> 16) & 0xff);
    p[3] = (uint8_t)((v >> 24) & 0xff);
}

static const uint8_t SIGMA[16] = "expand 32-byte k";

void chacha20_block(const uint8_t key[CHACHA20_KEY_BYTES],
                    const uint8_t nonce[CHACHA20_NONCE_BYTES],
                    uint32_t counter,
                    uint8_t block[CHACHA20_BLOCK_BYTES]) {
    uint32_t initial[16];
    uint32_t x[16];
    int i;

    initial[0] = 0x61707865; /* "expa" */
    initial[1] = 0x3320646e; /* "nd 3" */
    initial[2] = 0x79622d32; /* "2-by" */
    initial[3] = 0x6b206574; /* "te k" */

    for (i = 0; i < 8; i++) {
        initial[4 + i] = load32_le(key + 4 * i);
    }
    initial[12] = counter;
    initial[13] = load32_le(nonce + 0);
    initial[14] = load32_le(nonce + 4);
    initial[15] = load32_le(nonce + 8);

    memcpy(x, initial, sizeof(x));

    for (i = 0; i < 10; i++) {
        /* Column rounds */
        QR(x[0], x[4], x[8], x[12]);
        QR(x[1], x[5], x[9], x[13]);
        QR(x[2], x[6], x[10], x[14]);
        QR(x[3], x[7], x[11], x[15]);
        /* Diagonal rounds */
        QR(x[0], x[5], x[10], x[15]);
        QR(x[1], x[6], x[11], x[12]);
        QR(x[2], x[7], x[8], x[13]);
        QR(x[3], x[4], x[9], x[14]);
    }

    /* Serialize (working + initial) per RFC 8439. */
    for (i = 0; i < 16; i++) {
        store32_le(block + 4 * i, x[i] + initial[i]);
    }
}

void chacha20_xor(const uint8_t key[CHACHA20_KEY_BYTES],
                  const uint8_t nonce[CHACHA20_NONCE_BYTES],
                  uint32_t initial_counter,
                  const uint8_t *data,
                  size_t data_len,
                  uint8_t *out) {
    uint8_t keystream[CHACHA20_BLOCK_BYTES];
    uint32_t counter = initial_counter;
    size_t offset = 0;

    while (offset < data_len) {
        size_t chunk = data_len - offset;
        size_t i;
        if (chunk > CHACHA20_BLOCK_BYTES) {
            chunk = CHACHA20_BLOCK_BYTES;
        }
        chacha20_block(key, nonce, counter, keystream);
        for (i = 0; i < chunk; i++) {
            out[offset + i] = data[offset + i] ^ keystream[i];
        }
        offset += chunk;
        counter++;
    }
}
