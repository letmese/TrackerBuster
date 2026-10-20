package com.omnisentinel.app.features.acoustic

import kotlin.math.*

object FastFourierTransform {

    fun computeMagnitudes(samples: ShortArray, n: Int = 512): FloatArray {
        val size = n.coerceAtMost(samples.size)
        // Ensure size is power of 2
        val pow2Size = 1 shl (31 - Integer.numberOfLeadingZeros(size))

        val real = FloatArray(pow2Size)
        val imag = FloatArray(pow2Size)

        // Apply Hanning Window
        for (i in 0 until pow2Size) {
            val window = 0.5f * (1f - cos(2f * PI.toFloat() * i / (pow2Size - 1)))
            real[i] = (samples[i].toFloat() / 32768.0f) * window
            imag[i] = 0.0f
        }

        // Cooley-Tukey FFT
        fft(real, imag, pow2Size)

        // Return magnitude of the first half (positive frequencies)
        val halfSize = pow2Size / 2
        val magnitudes = FloatArray(halfSize)
        for (i in 0 until halfSize) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitudes[i] = (mag * 2.0f).coerceIn(0.0f, 1.0f)
        }

        return magnitudes
    }

    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Butterfly calculations
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = (-2.0 * PI / len).toFloat()
            val wStepR = cos(angle)
            val wStepI = sin(angle)

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
