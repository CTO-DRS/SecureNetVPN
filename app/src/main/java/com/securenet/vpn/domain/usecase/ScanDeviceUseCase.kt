package com.securenet.vpn.domain.usecase

import com.securenet.vpn.data.local.VpnEventLogger
import com.securenet.vpn.data.model.ScanResult
import com.securenet.vpn.domain.repository.ScannerRepository

/** Runs the full on-device audit and persists a short summary. */
class ScanDeviceUseCase(
    private val scannerRepository: ScannerRepository,
    private val logger: VpnEventLogger
) {
    suspend operator fun invoke(): ScanResult {
        val result = scannerRepository.fullScan()
        logger.appendCustom(
            "scan",
            detail = "level=${result.overallLevel.name}, threats=${result.threats.size}, " +
                "apps=${result.appsScanned}, files=${result.filesScanned}"
        )
        return result
    }
}
