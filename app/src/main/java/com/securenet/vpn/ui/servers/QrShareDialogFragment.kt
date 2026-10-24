package com.securenet.vpn.ui.servers

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.securenet.vpn.R
import com.securenet.vpn.databinding.DialogQrShowBinding
import com.securenet.vpn.qr.QrCodeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.5 — Shows a WireGuard profile as a scannable QR code (the exact
 * wg-quick payload official clients accept) and can save it as a PNG into
 * Pictures/SecureNetVPN via MediaStore (no storage permission on API 30+).
 */
class QrShareDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogQrShowBinding? = null
    private val binding get() = _binding!!

    private var bitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQrShowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val payload = requireArguments().getString(ARG_PAYLOAD).orEmpty()
        val title = requireArguments().getString(ARG_TITLE)

        title?.let { binding.tvQrTitle.text = it }

        when (val encoded = QrCodeEngine.encode(payload)) {
            is QrCodeEngine.EncodeResult.Success -> {
                bitmap = bitmapFrom(encoded.matrix)
                binding.imgQr.setImageBitmap(bitmap)
            }
            is QrCodeEngine.EncodeResult.TooLarge -> {
                binding.imgQr.setImageDrawable(null)
                binding.tvQrWarning.text = getString(R.string.qr_error_too_large)
                binding.btnSaveQr.isEnabled = false
            }
        }

        binding.btnSaveQr.setOnClickListener { saveToGallery() }
    }

    /** ZXing BitMatrix → Bitmap (white background, black modules). */
    private fun bitmapFrom(matrix: com.google.zxing.common.BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun saveToGallery() {
        val bmp = bitmap ?: return
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SecureNet_WG_$stamp.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SecureNetVPN")
            }
            val uri = requireContext().contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onSuccess {
            Toast.makeText(requireContext(), R.string.qr_saved_gallery, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), R.string.qr_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bitmap = null
        _binding = null
    }

    companion object {
        private const val ARG_PAYLOAD = "payload"
        private const val ARG_TITLE = "title"

        fun newInstance(payload: String, title: String?): QrShareDialogFragment =
            QrShareDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAYLOAD, payload)
                    putString(ARG_TITLE, title)
                }
            }
    }
}
