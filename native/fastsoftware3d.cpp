#include <jni.h>
#include <cmath>
#include <algorithm>
#include <vector>
#include <thread>
#include <immintrin.h>
#include "fastsoftware3d_rasterizer_NativeRasterizer.h"
#include "fastsoftware3d_frontend_terminal_Demo3DTerminal.h"

// -------------------------------------------------------------
// Constants
// -------------------------------------------------------------
static const int TEX_SIZE = 256;

static inline int sampleTexture(float depth, float u, float v,
                                int* mipmapData, int* mipmapOffsets,
                                int* mipmapWidths, int* mipmapHeights,
                                int mipmapLevels, int mipmapMode, int x, int y) {
    if (mipmapMode == 0 || mipmapLevels <= 1) {
        int texW = mipmapWidths[0];
        int texH = mipmapHeights[0];
        int texX = (int)(u * (texW - 1));
        int texY = (int)(v * (texH - 1));
        return mipmapData[texY * texW + texX];
    }

    if (mipmapMode == 1) {
        int mipLevel;
        if (depth < 600.0f) mipLevel = 0;
        else if (depth < 1200.0f) mipLevel = 1;
        else if (depth < 2400.0f) mipLevel = 2;
        else mipLevel = 3;
        if (mipLevel >= mipmapLevels) mipLevel = mipmapLevels - 1;
        if (mipLevel < 0) mipLevel = 0;

        int texW = mipmapWidths[mipLevel];
        int texH = mipmapHeights[mipLevel];
        int offset = mipmapOffsets[mipLevel];
        int texX = (int)(u * (texW - 1));
        int texY = (int)(v * (texH - 1));
        return mipmapData[offset + texY * texW + texX];
    }

    if (mipmapMode == 2) {
        float L;
        if (depth < 400.0f) {
            L = 0.0f;
        } else if (depth < 800.0f) {
            L = (depth - 400.0f) / 400.0f;
        } else if (depth < 1400.0f) {
            L = 1.0f + (depth - 800.0f) / 600.0f;
        } else {
            L = 2.0f + (depth - 1400.0f) / 1000.0f;
        }

        int L_int = (int)L;
        float L_frac = L - L_int;

        static const float bayer4x4[4][4] = {
            { 0.0625f, 0.5625f, 0.1875f, 0.6875f },
            { 0.8125f, 0.3125f, 0.9375f, 0.4375f },
            { 0.25f,   0.75f,   0.125f,  0.625f  },
            { 1.0f,    0.5f,    0.875f,  0.375f  }
        };
        float threshold = bayer4x4[y & 3][x & 3];
        int mipLevel = (L_frac > threshold) ? (L_int + 1) : L_int;

        if (mipLevel >= mipmapLevels) mipLevel = mipmapLevels - 1;
        if (mipLevel < 0) mipLevel = 0;

        int texW = mipmapWidths[mipLevel];
        int texH = mipmapHeights[mipLevel];
        int offset = mipmapOffsets[mipLevel];
        int texX = (int)(u * (texW - 1));
        int texY = (int)(v * (texH - 1));
        return mipmapData[offset + texY * texW + texX];
    }

    float L;
    if (depth < 400.0f) {
        L = 0.0f;
    } else if (depth < 800.0f) {
        L = (depth - 400.0f) / 400.0f;
    } else if (depth < 1400.0f) {
        L = 1.0f + (depth - 800.0f) / 600.0f;
    } else {
        L = 2.0f + (depth - 1400.0f) / 1000.0f;
    }

    int L_int = (int)L;
    float L_frac = L - L_int;

    int mipLevel0 = L_int;
    int mipLevel1 = L_int + 1;
    if (mipLevel0 >= mipmapLevels) mipLevel0 = mipmapLevels - 1;
    if (mipLevel0 < 0) mipLevel0 = 0;
    if (mipLevel1 >= mipmapLevels) mipLevel1 = mipmapLevels - 1;
    if (mipLevel1 < 0) mipLevel1 = 0;

    int texW0 = mipmapWidths[mipLevel0];
    int texH0 = mipmapHeights[mipLevel0];
    int offset0 = mipmapOffsets[mipLevel0];
    int texX0 = (int)(u * (texW0 - 1));
    int texY0 = (int)(v * (texH0 - 1));
    int color0 = mipmapData[offset0 + texY0 * texW0 + texX0];

    if (mipLevel0 == mipLevel1) {
        return color0;
    }

    int texW1 = mipmapWidths[mipLevel1];
    int texH1 = mipmapHeights[mipLevel1];
    int offset1 = mipmapOffsets[mipLevel1];
    int texX1 = (int)(u * (texW1 - 1));
    int texY1 = (int)(v * (texH1 - 1));
    int color1 = mipmapData[offset1 + texY1 * texW1 + texX1];

    int r0 = (color0 >> 16) & 0xFF;
    int g0 = (color0 >> 8) & 0xFF;
    int b0 = color0 & 0xFF;

    int r1 = (color1 >> 16) & 0xFF;
    int g1 = (color1 >> 8) & 0xFF;
    int b1 = color1 & 0xFF;

    int r = (int)(r0 * (1.0f - L_frac) + r1 * L_frac);
    int g = (int)(g0 * (1.0f - L_frac) + g1 * L_frac);
    int b = (int)(b0 * (1.0f - L_frac) + b1 * L_frac);

    return (r << 16) | (g << 8) | b;
}

// -------------------------------------------------------------
// JNI: drawTexturedTriangleNative
// -------------------------------------------------------------
JNIEXPORT void JNICALL Java_fastsoftware3d_rasterizer_NativeRasterizer_drawTexturedTriangleNative(
    JNIEnv* env, jobject obj,
    jfloat x0, jfloat y0, jfloat z0, jfloat u0, jfloat v0,
    jfloat x1, jfloat y1, jfloat z1, jfloat u1, jfloat v1,
    jfloat x2, jfloat y2, jfloat z2, jfloat u2, jfloat v2,
    jintArray pixelsArray, jfloatArray zBufferArray,
    jintArray mipmapDataArray, jintArray mipmapOffsetsArray,
    jintArray mipmapWidthsArray, jintArray mipmapHeightsArray,
    jint mipmapLevels,
    jint mipmapMode,
    jint width, jint height
) {
    // Pin JVM arrays
    jint* pixels = (jint*)env->GetPrimitiveArrayCritical(pixelsArray, nullptr);
    jfloat* zBuffer = (jfloat*)env->GetPrimitiveArrayCritical(zBufferArray, nullptr);
    jint* mipmapData = (jint*)env->GetPrimitiveArrayCritical(mipmapDataArray, nullptr);
    jint* mipmapOffsets = (jint*)env->GetPrimitiveArrayCritical(mipmapOffsetsArray, nullptr);
    jint* mipmapWidths = (jint*)env->GetPrimitiveArrayCritical(mipmapWidthsArray, nullptr);
    jint* mipmapHeights = (jint*)env->GetPrimitiveArrayCritical(mipmapHeightsArray, nullptr);

    if (pixels && zBuffer && mipmapData && mipmapOffsets && mipmapWidths && mipmapHeights) {
        float w0 = 1.0f / z0;
        float w1 = 1.0f / z1;
        float w2 = 1.0f / z2;

        float uOverZ0 = u0 * w0;
        float vOverZ0 = v0 * w0;
        float uOverZ1 = u1 * w1;
        float vOverZ1 = v1 * w1;
        float uOverZ2 = u2 * w2;
        float vOverZ2 = v2 * w2;

        int minX = (int)std::max(0.0f, std::floor(std::min({x0, x1, x2})));
        int maxX = (int)std::min((float)(width - 1), std::ceil(std::max({x0, x1, x2})));
        int minY = (int)std::max(0.0f, std::floor(std::min({y0, y1, y2})));
        int maxY = (int)std::min((float)(height - 1), std::ceil(std::max({y0, y1, y2})));

        float denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (std::abs(denom) >= 0.000001f) {
            float invDenom = 1.0f / denom;

            float dAlpha = (y1 - y2) * invDenom;
            float dBeta  = (y2 - y0) * invDenom;

            // Direct scanline rasterization loop
            for (int y = minY; y <= maxY; y++) {
                int rowOffset = y * width;
                float py = y + 0.5f;

                int x = minX;
                __m256 v_dAlpha = _mm256_set1_ps(dAlpha);
                __m256 v_dBeta  = _mm256_set1_ps(dBeta);
                __m256 v_w0     = _mm256_set1_ps(w0);
                __m256 v_w1     = _mm256_set1_ps(w1);
                __m256 v_w2     = _mm256_set1_ps(w2);
                __m256 v_zero   = _mm256_setzero_ps();
                __m256 v_one    = _mm256_set1_ps(1.0f);
                __m256 v_steps  = _mm256_setr_ps(0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f);

                __m256 v_dAlpha_steps = _mm256_mul_ps(v_dAlpha, v_steps);
                __m256 v_dBeta_steps  = _mm256_mul_ps(v_dBeta, v_steps);

                for (; x <= maxX - 7; x += 8) {
                    float px_base = x + 0.5f;
                    float alpha_base = ((y1 - y2) * (px_base - x2) + (x2 - x1) * (py - y2)) * invDenom;
                    float beta_base  = ((y2 - y0) * (px_base - x2) + (x0 - x2) * (py - y2)) * invDenom;

                    __m256 v_alpha = _mm256_add_ps(_mm256_set1_ps(alpha_base), v_dAlpha_steps);
                    __m256 v_beta  = _mm256_add_ps(_mm256_set1_ps(beta_base), v_dBeta_steps);
                    __m256 v_gamma = _mm256_sub_ps(_mm256_sub_ps(v_one, v_alpha), v_beta);

                    __m256 m_alpha = _mm256_cmp_ps(v_alpha, v_zero, _CMP_GE_OQ);
                    __m256 m_beta  = _mm256_cmp_ps(v_beta, v_zero, _CMP_GE_OQ);
                    __m256 m_gamma = _mm256_cmp_ps(v_gamma, v_zero, _CMP_GE_OQ);
                    __m256 m_inside = _mm256_and_ps(_mm256_and_ps(m_alpha, m_beta), m_gamma);

                    int mask = _mm256_movemask_ps(m_inside);
                    if (mask == 0) continue;

                    __m256 v_interpW = _mm256_add_ps(
                        _mm256_mul_ps(v_alpha, v_w0),
                        _mm256_add_ps(_mm256_mul_ps(v_beta, v_w1), _mm256_mul_ps(v_gamma, v_w2))
                    );

                    __m256 v_depth = _mm256_div_ps(v_one, v_interpW);

                    int pixelIndex = rowOffset + x;
                    __m256 v_zBuf = _mm256_loadu_ps(&zBuffer[pixelIndex]);

                    __m256 m_depth = _mm256_cmp_ps(v_depth, v_zBuf, _CMP_LT_OQ);
                    __m256 m_write = _mm256_and_ps(m_inside, m_depth);

                    int writeMask = _mm256_movemask_ps(m_write);
                    if (writeMask == 0) continue;

                    float depth_arr[8];
                    float alpha_arr[8];
                    float beta_arr[8];
                    float gamma_arr[8];
                    float interpW_arr[8];
                    _mm256_storeu_ps(depth_arr, v_depth);
                    _mm256_storeu_ps(alpha_arr, v_alpha);
                    _mm256_storeu_ps(beta_arr, v_beta);
                    _mm256_storeu_ps(gamma_arr, v_gamma);
                    _mm256_storeu_ps(interpW_arr, v_interpW);

                    for (int j = 0; j < 8; j++) {
                        if (writeMask & (1 << j)) {
                            int idx = pixelIndex + j;
                            float d = depth_arr[j];
                            zBuffer[idx] = d;

                            float interpUOverZ = alpha_arr[j] * uOverZ0 + beta_arr[j] * uOverZ1 + gamma_arr[j] * uOverZ2;
                            float interpVOverZ = alpha_arr[j] * vOverZ0 + beta_arr[j] * vOverZ1 + gamma_arr[j] * vOverZ2;

                            float u = interpUOverZ / interpW_arr[j];
                            float v = interpVOverZ / interpW_arr[j];

                            u = u - std::floor(u);
                            v = v - std::floor(v);

                            int texColor = sampleTexture(d, u, v, mipmapData, mipmapOffsets, mipmapWidths, mipmapHeights, mipmapLevels, mipmapMode, x + j, y);

                            pixels[idx] = texColor;
                        }
                    }
                }

                // Scalar cleanup
                for (; x <= maxX; x++) {
                    float px = x + 0.5f;

                    float alpha = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) * invDenom;
                    float beta  = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) * invDenom;
                    float gamma = 1.0f - alpha - beta;

                    if (alpha >= 0.0f && beta >= 0.0f && gamma >= 0.0f) {
                        float interpolatedW = alpha * w0 + beta * w1 + gamma * w2;
                        float depth = 1.0f / interpolatedW;

                        int pixelIndex = rowOffset + x;
                        if (depth < zBuffer[pixelIndex]) {
                            zBuffer[pixelIndex] = depth;

                            float interpUOverZ = alpha * uOverZ0 + beta * uOverZ1 + gamma * uOverZ2;
                            float interpVOverZ = alpha * vOverZ0 + beta * vOverZ1 + gamma * vOverZ2;

                            float u = interpUOverZ / interpolatedW;
                            float v = interpVOverZ / interpolatedW;

                            u = u - std::floor(u);
                            v = v - std::floor(v);

                            int texColor = sampleTexture(depth, u, v, mipmapData, mipmapOffsets, mipmapWidths, mipmapHeights, mipmapLevels, mipmapMode, x, y);

                            pixels[pixelIndex] = texColor;
                        }
                    }
                }
            }
        }
    }

    // Unpin JVM arrays
    if (mipmapHeights) env->ReleasePrimitiveArrayCritical(mipmapHeightsArray, mipmapHeights, JNI_ABORT);
    if (mipmapWidths) env->ReleasePrimitiveArrayCritical(mipmapWidthsArray, mipmapWidths, JNI_ABORT);
    if (mipmapOffsets) env->ReleasePrimitiveArrayCritical(mipmapOffsetsArray, mipmapOffsets, JNI_ABORT);
    if (mipmapData) env->ReleasePrimitiveArrayCritical(mipmapDataArray, mipmapData, JNI_ABORT);
    if (zBuffer) env->ReleasePrimitiveArrayCritical(zBufferArray, zBuffer, 0);
    if (pixels) env->ReleasePrimitiveArrayCritical(pixelsArray, pixels, 0);
}

JNIEXPORT void JNICALL Java_fastsoftware3d_rasterizer_NativeRasterizer_drawTexturedTrianglesNative(
    JNIEnv* env, jobject obj,
    jfloatArray triangleDataArray, jint triangleCount,
    jintArray pixelsArray, jfloatArray zBufferArray,
    jintArray mipmapDataArray, jintArray mipmapOffsetsArray,
    jintArray mipmapWidthsArray, jintArray mipmapHeightsArray,
    jint mipmapLevels,
    jint mipmapMode,
    jint width, jint height
) {
    jfloat* triangleData = (jfloat*)env->GetPrimitiveArrayCritical(triangleDataArray, nullptr);
    jint* pixels = (jint*)env->GetPrimitiveArrayCritical(pixelsArray, nullptr);
    jfloat* zBuffer = (jfloat*)env->GetPrimitiveArrayCritical(zBufferArray, nullptr);
    jint* mipmapData = (jint*)env->GetPrimitiveArrayCritical(mipmapDataArray, nullptr);
    jint* mipmapOffsets = (jint*)env->GetPrimitiveArrayCritical(mipmapOffsetsArray, nullptr);
    jint* mipmapWidths = (jint*)env->GetPrimitiveArrayCritical(mipmapWidthsArray, nullptr);
    jint* mipmapHeights = (jint*)env->GetPrimitiveArrayCritical(mipmapHeightsArray, nullptr);

    if (triangleData && pixels && zBuffer && mipmapData && mipmapOffsets && mipmapWidths && mipmapHeights) {
        for (int i = 0; i < triangleCount; i++) {
            int offset = i * 15;
            float x0 = triangleData[offset];
            float y0 = triangleData[offset + 1];
            float z0 = triangleData[offset + 2];
            float u0 = triangleData[offset + 3];
            float v0 = triangleData[offset + 4];

            float x1 = triangleData[offset + 5];
            float y1 = triangleData[offset + 6];
            float z1 = triangleData[offset + 7];
            float u1 = triangleData[offset + 8];
            float v1 = triangleData[offset + 9];

            float x2 = triangleData[offset + 10];
            float y2 = triangleData[offset + 11];
            float z2 = triangleData[offset + 12];
            float u2 = triangleData[offset + 13];
            float v2 = triangleData[offset + 14];

            float w0 = 1.0f / z0;
            float w1 = 1.0f / z1;
            float w2 = 1.0f / z2;

            float uOverZ0 = u0 * w0;
            float vOverZ0 = v0 * w0;
            float uOverZ1 = u1 * w1;
            float vOverZ1 = v1 * w1;
            float uOverZ2 = u2 * w2;
            float vOverZ2 = v2 * w2;

            int minX = (int)std::max(0.0f, std::floor(std::min({x0, x1, x2})));
            int maxX = (int)std::min((float)(width - 1), std::ceil(std::max({x0, x1, x2})));
            int minY = (int)std::max(0.0f, std::floor(std::min({y0, y1, y2})));
            int maxY = (int)std::min((float)(height - 1), std::ceil(std::max({y0, y1, y2})));
            float denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
            if (std::abs(denom) >= 0.000001f) {
                float invDenom = 1.0f / denom;

                float dAlpha = (y1 - y2) * invDenom;
                float dBeta  = (y2 - y0) * invDenom;

                for (int y = minY; y <= maxY; y++) {
                    int rowOffset = y * width;
                    float py = y + 0.5f;

                    int x = minX;
                    __m256 v_dAlpha = _mm256_set1_ps(dAlpha);
                    __m256 v_dBeta  = _mm256_set1_ps(dBeta);
                    __m256 v_w0     = _mm256_set1_ps(w0);
                    __m256 v_w1     = _mm256_set1_ps(w1);
                    __m256 v_w2     = _mm256_set1_ps(w2);
                    __m256 v_zero   = _mm256_setzero_ps();
                    __m256 v_one    = _mm256_set1_ps(1.0f);
                    __m256 v_steps  = _mm256_setr_ps(0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f);

                    __m256 v_dAlpha_steps = _mm256_mul_ps(v_dAlpha, v_steps);
                    __m256 v_dBeta_steps  = _mm256_mul_ps(v_dBeta, v_steps);

                    for (; x <= maxX - 7; x += 8) {
                        float px_base = x + 0.5f;
                        float alpha_base = ((y1 - y2) * (px_base - x2) + (x2 - x1) * (py - y2)) * invDenom;
                        float beta_base  = ((y2 - y0) * (px_base - x2) + (x0 - x2) * (py - y2)) * invDenom;

                        __m256 v_alpha = _mm256_add_ps(_mm256_set1_ps(alpha_base), v_dAlpha_steps);
                        __m256 v_beta  = _mm256_add_ps(_mm256_set1_ps(beta_base), v_dBeta_steps);
                        __m256 v_gamma = _mm256_sub_ps(_mm256_sub_ps(v_one, v_alpha), v_beta);

                        __m256 m_alpha = _mm256_cmp_ps(v_alpha, v_zero, _CMP_GE_OQ);
                        __m256 m_beta  = _mm256_cmp_ps(v_beta, v_zero, _CMP_GE_OQ);
                        __m256 m_gamma = _mm256_cmp_ps(v_gamma, v_zero, _CMP_GE_OQ);
                        __m256 m_inside = _mm256_and_ps(_mm256_and_ps(m_alpha, m_beta), m_gamma);

                        int mask = _mm256_movemask_ps(m_inside);
                        if (mask == 0) continue;

                        __m256 v_interpW = _mm256_add_ps(
                            _mm256_mul_ps(v_alpha, v_w0),
                            _mm256_add_ps(_mm256_mul_ps(v_beta, v_w1), _mm256_mul_ps(v_gamma, v_w2))
                        );

                        __m256 v_depth = _mm256_div_ps(v_one, v_interpW);

                        int pixelIndex = rowOffset + x;
                        __m256 v_zBuf = _mm256_loadu_ps(&zBuffer[pixelIndex]);

                        __m256 m_depth = _mm256_cmp_ps(v_depth, v_zBuf, _CMP_LT_OQ);
                        __m256 m_write = _mm256_and_ps(m_inside, m_depth);

                        int writeMask = _mm256_movemask_ps(m_write);
                        if (writeMask == 0) continue;

                        float depth_arr[8];
                        float alpha_arr[8];
                        float beta_arr[8];
                        float gamma_arr[8];
                        float interpW_arr[8];
                        _mm256_storeu_ps(depth_arr, v_depth);
                        _mm256_storeu_ps(alpha_arr, v_alpha);
                        _mm256_storeu_ps(beta_arr, v_beta);
                        _mm256_storeu_ps(gamma_arr, v_gamma);
                        _mm256_storeu_ps(interpW_arr, v_interpW);

                        for (int j = 0; j < 8; j++) {
                            if (writeMask & (1 << j)) {
                                int idx = pixelIndex + j;
                                float d = depth_arr[j];
                                zBuffer[idx] = d;

                                float interpUOverZ = alpha_arr[j] * uOverZ0 + beta_arr[j] * uOverZ1 + gamma_arr[j] * uOverZ2;
                                float interpVOverZ = alpha_arr[j] * vOverZ0 + beta_arr[j] * vOverZ1 + gamma_arr[j] * vOverZ2;

                                float u = interpUOverZ / interpW_arr[j];
                                float v = interpVOverZ / interpW_arr[j];

                                u = u - std::floor(u);
                                v = v - std::floor(v);

                                int texColor = sampleTexture(d, u, v, mipmapData, mipmapOffsets, mipmapWidths, mipmapHeights, mipmapLevels, mipmapMode, x + j, y);

                                    pixels[idx] = texColor;
                            }
                        }
                    }

                    // Scalar cleanup
                    for (; x <= maxX; x++) {
                        float px = x + 0.5f;

                        float alpha = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) * invDenom;
                        float beta  = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) * invDenom;
                        float gamma = 1.0f - alpha - beta;

                        if (alpha >= 0.0f && beta >= 0.0f && gamma >= 0.0f) {
                            float interpolatedW = alpha * w0 + beta * w1 + gamma * w2;
                            float depth = 1.0f / interpolatedW;

                            int pixelIndex = rowOffset + x;
                            if (depth < zBuffer[pixelIndex]) {
                                zBuffer[pixelIndex] = depth;

                                float interpUOverZ = alpha * uOverZ0 + beta * uOverZ1 + gamma * uOverZ2;
                                float interpVOverZ = alpha * vOverZ0 + beta * vOverZ1 + gamma * vOverZ2;

                                float u = interpUOverZ / interpolatedW;
                                float v = interpVOverZ / interpolatedW;

                                u = u - std::floor(u);
                                v = v - std::floor(v);

                                int texColor = sampleTexture(depth, u, v, mipmapData, mipmapOffsets, mipmapWidths, mipmapHeights, mipmapLevels, mipmapMode, x, y);

                                    pixels[pixelIndex] = texColor;
                            }
                        }
                    }
                }
            }
        }
    }

    if (mipmapHeights) env->ReleasePrimitiveArrayCritical(mipmapHeightsArray, mipmapHeights, JNI_ABORT);
    if (mipmapWidths) env->ReleasePrimitiveArrayCritical(mipmapWidthsArray, mipmapWidths, JNI_ABORT);
    if (mipmapOffsets) env->ReleasePrimitiveArrayCritical(mipmapOffsetsArray, mipmapOffsets, JNI_ABORT);
    if (mipmapData) env->ReleasePrimitiveArrayCritical(mipmapDataArray, mipmapData, JNI_ABORT);
    if (zBuffer) env->ReleasePrimitiveArrayCritical(zBufferArray, zBuffer, 0);
    if (pixels) env->ReleasePrimitiveArrayCritical(pixelsArray, pixels, 0);
    if (triangleData) env->ReleasePrimitiveArrayCritical(triangleDataArray, triangleData, JNI_ABORT);
}

// -------------------------------------------------------------
// JNI: downsampleHalfBlocksNative
// -------------------------------------------------------------
JNIEXPORT void JNICALL Java_fastsoftware3d_frontend_terminal_Demo3DTerminal_downsampleHalfBlocksNative(
    JNIEnv* env, jclass clazz,
    jintArray srcArray, jint srcW, jint srcH,
    jintArray codepointArray, jintArray fgArray, jintArray bgArray,
    jint cols, jint rows, jint ssaa
) {
    jint* src = (jint*)env->GetPrimitiveArrayCritical(srcArray, nullptr);
    jint* codepointBuffer = (jint*)env->GetPrimitiveArrayCritical(codepointArray, nullptr);
    jint* fgBuffer = (jint*)env->GetPrimitiveArrayCritical(fgArray, nullptr);
    jint* bgBuffer = (jint*)env->GetPrimitiveArrayCritical(bgArray, nullptr);

    if (src && codepointBuffer && fgBuffer && bgBuffer) {
        int shift = 0;
        if (ssaa == 2) shift = 2;       // 2x2 = 4 pixels, divide by 4 (shift 2)
        else if (ssaa == 4) shift = 4;  // 4x4 = 16 pixels, divide by 16 (shift 4)
        else if (ssaa == 8) shift = 6;  // 8x8 = 64 pixels, divide by 64 (shift 6)
        else if (ssaa == 16) shift = 8; // 16x16 = 256 pixels, divide by 256 (shift 8)

        // Process rows in parallel using simple loop division
        int numThreads = (int)std::thread::hardware_concurrency();
        if (numThreads < 1) numThreads = 1;
        
        std::vector<std::thread> workers;
        int rowsPerThread = (rows + numThreads - 1) / numThreads;

        for (int t = 0; t < numThreads; t++) {
            int startRow = t * rowsPerThread;
            int endRow = std::min(startRow + rowsPerThread, (int)rows);

            if (startRow >= endRow) break;

            workers.push_back(std::thread([=]() {
                for (int row = startRow; row < endRow; row++) {
                    int startYTop = row * 2 * ssaa;
                    int startYBot = (row * 2 + 1) * ssaa;
                    int destRowOffset = row * cols;

                    for (int col = 0; col < cols; col++) {
                        int startX = col * ssaa;

                        // Downsample Top half-block
                        int rSumTop = 0, gSumTop = 0, bSumTop = 0;
                        for (int sy = 0; sy < ssaa; sy++) {
                            int srcRowOffset = (startYTop + sy) * srcW + startX;
                            for (int sx = 0; sx < ssaa; sx++) {
                                int rgb = src[srcRowOffset + sx];
                                rSumTop += (rgb >> 16) & 0xFF;
                                gSumTop += (rgb >> 8) & 0xFF;
                                bSumTop += rgb & 0xFF;
                            }
                        }
                        int topColor;
                        if (shift > 0) {
                            topColor = ((rSumTop >> shift) << 16) | ((gSumTop >> shift) << 8) | (bSumTop >> shift);
                        } else {
                            topColor = (rSumTop << 16) | (gSumTop << 8) | bSumTop;
                        }

                        // Downsample Bottom half-block
                        int rSumBot = 0, gSumBot = 0, bSumBot = 0;
                        for (int sy = 0; sy < ssaa; sy++) {
                            int srcRowOffset = (startYBot + sy) * srcW + startX;
                            for (int sx = 0; sx < ssaa; sx++) {
                                int rgb = src[srcRowOffset + sx];
                                rSumBot += (rgb >> 16) & 0xFF;
                                gSumBot += (rgb >> 8) & 0xFF;
                                bSumBot += rgb & 0xFF;
                            }
                        }
                        int botColor;
                        if (shift > 0) {
                            botColor = ((rSumBot >> shift) << 16) | ((gSumBot >> shift) << 8) | (bSumBot >> shift);
                        } else {
                            botColor = (rSumBot << 16) | (gSumBot << 8) | bSumBot;
                        }

                        int destIndex = destRowOffset + col;
                        codepointBuffer[destIndex] = 0x2580; // '▀'
                        fgBuffer[destIndex] = topColor;
                        bgBuffer[destIndex] = botColor;
                    }
                }
            }));
        }

        for (auto& worker : workers) {
            worker.join();
        }
    }

    if (bgBuffer) env->ReleasePrimitiveArrayCritical(bgArray, bgBuffer, 0);
    if (fgBuffer) env->ReleasePrimitiveArrayCritical(fgArray, fgBuffer, 0);
    if (codepointBuffer) env->ReleasePrimitiveArrayCritical(codepointArray, codepointBuffer, 0);
    if (src) env->ReleasePrimitiveArrayCritical(srcArray, src, JNI_ABORT);
}

// -------------------------------------------------------------
// JNI: applyFxaaNative
// -------------------------------------------------------------
// Simple FXAA approximation for terminal renderer.
// Identifies edges on the input high-resolution src buffer (where srcW=cols, srcH=rows*2)
// and downsamples into upper and lower half-blocks.
JNIEXPORT void JNICALL Java_fastsoftware3d_frontend_terminal_Demo3DTerminal_applyFxaaNative(
    JNIEnv* env, jclass clazz,
    jintArray srcArray,
    jintArray codepointArray, jintArray fgArray, jintArray bgArray,
    jint cols, jint rows
) {
    jint* src = (jint*)env->GetPrimitiveArrayCritical(srcArray, nullptr);
    jint* codepointBuffer = (jint*)env->GetPrimitiveArrayCritical(codepointArray, nullptr);
    jint* fgBuffer = (jint*)env->GetPrimitiveArrayCritical(fgArray, nullptr);
    jint* bgBuffer = (jint*)env->GetPrimitiveArrayCritical(bgArray, nullptr);

    if (src && codepointBuffer && fgBuffer && bgBuffer) {
        int srcW = cols;
        int srcH = rows * 2;

        // Calculate luma helper
        auto getLuma = [&](int x, int y) -> float {
            if (x < 0) x = 0; if (x >= srcW) x = srcW - 1;
            if (y < 0) y = 0; if (y >= srcH) y = srcH - 1;
            int rgb = src[y * srcW + x];
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;
            return 0.299f * r + 0.587f * g + 0.114f * b;
        };

        // Get blended rgb helper
        auto getPixelBlend = [&](int x, int y, float blend, int rgbM) -> int {
            if (blend < 0.05f) return rgbM;
            if (x < 0) x = 0; if (x >= srcW) x = srcW - 1;
            if (y < 0) y = 0; if (y >= srcH) y = srcH - 1;

            // Average of surrounding pixels for local blur
            int sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && nx < srcW && ny >= 0 && ny < srcH) {
                        int c = src[ny * srcW + nx];
                        sumR += (c >> 16) & 0xFF;
                        sumG += (c >> 8) & 0xFF;
                        sumB += c & 0xFF;
                        count++;
                    }
                }
            }
            if (count == 0) return rgbM;
            int blurR = sumR / count;
            int blurG = sumG / count;
            int blurB = sumB / count;

            int mr = (rgbM >> 16) & 0xFF;
            int mg = (rgbM >> 8) & 0xFF;
            int mb = rgbM & 0xFF;

            int outR = (int)(mr * (1.0f - blend) + blurR * blend);
            int outG = (int)(mg * (1.0f - blend) + blurG * blend);
            int outB = (int)(mb * (1.0f - blend) + blurB * blend);

            return (outR << 16) | (outG << 8) | outB;
        };

        int numThreads = (int)std::thread::hardware_concurrency();
        if (numThreads < 1) numThreads = 1;

        std::vector<std::thread> workers;
        int rowsPerThread = (rows + numThreads - 1) / numThreads;

        for (int t = 0; t < numThreads; t++) {
            int startRow = t * rowsPerThread;
            int endRow = std::min(startRow + rowsPerThread, (int)rows);

            if (startRow >= endRow) break;

            workers.push_back(std::thread([=]() {
                for (int row = startRow; row < endRow; row++) {
                    int yTop = row * 2;
                    int yBot = row * 2 + 1;
                    int destRowOffset = row * cols;

                    for (int col = 0; col < cols; col++) {
                        int x = col;

                        auto processFXAAPixel = [&](int px, int py) -> int {
                            int rgbM = src[py * srcW + px];
                            float lumaM = getLuma(px, py);
                            float lumaN = getLuma(px, py - 1);
                            float lumaS = getLuma(px, py + 1);
                            float lumaE = getLuma(px + 1, py);
                            float lumaW = getLuma(px - 1, py);

                            float lumaMin = std::min({lumaM, lumaN, lumaS, lumaE, lumaW});
                            float lumaMax = std::max({lumaM, lumaN, lumaS, lumaE, lumaW});
                            float lumaRange = lumaMax - lumaMin;

                            // Edge detection threshold
                            if (lumaRange < 0.05f) {
                                return rgbM;
                            }

                            float lumaNW = getLuma(px - 1, py - 1);
                            float lumaNE = getLuma(px + 1, py - 1);
                            float lumaSW = getLuma(px - 1, py + 1);
                            float lumaSE = getLuma(px + 1, py + 1);

                            float lumaL = (lumaN + lumaS + lumaE + lumaW) * 0.25f;
                            float rangeL = std::abs(lumaL - lumaM);
                            float blend = std::clamp(rangeL / lumaRange, 0.0f, 1.0f);

                            return getPixelBlend(px, py, blend * 0.5f, rgbM);
                        };

                        int topColor = processFXAAPixel(x, yTop);
                        int botColor = processFXAAPixel(x, yBot);

                        int destIndex = destRowOffset + col;
                        codepointBuffer[destIndex] = 0x2580; // '▀'
                        fgBuffer[destIndex] = topColor;
                        bgBuffer[destIndex] = botColor;
                    }
                }
            }));
        }

        for (auto& worker : workers) {
            worker.join();
        }
    }

    if (bgBuffer) env->ReleasePrimitiveArrayCritical(bgArray, bgBuffer, 0);
    if (fgBuffer) env->ReleasePrimitiveArrayCritical(fgArray, fgBuffer, 0);
    if (codepointBuffer) env->ReleasePrimitiveArrayCritical(codepointArray, codepointBuffer, 0);
    if (src) env->ReleasePrimitiveArrayCritical(srcArray, src, JNI_ABORT);
}
