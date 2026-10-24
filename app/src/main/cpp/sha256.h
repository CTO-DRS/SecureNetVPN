/* SHA-256 per FIPS 180-4 - compact C implementation for key material ops. */
#ifndef SECURENET_SHA256_H
#define SECURENET_SHA256_H

#include <stdint.h>
#include <stddef.h>

#define SHA256_DIGEST_BYTES 32

void sha256(const uint8_t *data, size_t len, uint8_t digest[SHA256_DIGEST_BYTES]);

#endif /* SECURENET_SHA256_H */
