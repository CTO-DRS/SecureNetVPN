/* RFC 8439 ChaCha20 stream cipher - compact, auditable C implementation. */
#ifndef SECURENET_CHACHA20_H
#define SECURENET_CHACHA20_H

#include <stdint.h>
#include <stddef.h>

#define CHACHA20_KEY_BYTES 32
#define CHACHA20_NONCE_BYTES 12
#define CHACHA20_BLOCK_BYTES 64

/* XORs `data` with the ChaCha20 keystream (RFC 8439), in place semantics
 * are avoided: returns the transformed bytes in `out` (may equal `data`). */
void chacha20_xor(const uint8_t key[CHACHA20_KEY_BYTES],
                  const uint8_t nonce[CHACHA20_NONCE_BYTES],
                  uint32_t initial_counter,
                  const uint8_t *data,
                  size_t data_len,
                  uint8_t *out);

/* Generates one raw keystream block (used by the self-test). */
void chacha20_block(const uint8_t key[CHACHA20_KEY_BYTES],
                    const uint8_t nonce[CHACHA20_NONCE_BYTES],
                    uint32_t counter,
                    uint8_t block[CHACHA20_BLOCK_BYTES]);

#endif /* SECURENET_CHACHA20_H */
