#include <jni.h>
#include <cmath>
#include <algorithm>
#include <vector>
#include <thread>
#include "fastsoftware3d_rasterizer_NativeRasterizer.h"
#include "fastsoftware3d_frontend_terminal_Demo3DTerminal.h"

// -------------------------------------------------------------
// Constants
// -------------------------------------------------------------
static const int TEX_SIZE = 256;

// -------------------------------------------------------------
// JNI: drawTexturedTriangleNative
// -------------------------------------------------------------
JNIEXPORT void JNICALL Java_fastsoftware3d_rasterizer_NativeRasterizer_drawTexturedTriangleNative(
    JNIEnv* env, jobject obj,
    jfloat x0, jfloat y0, jfloat z0, jfloat u0, jfloat v0,
    jfloat x1, jfloat y1, jfloat z1, jfloat u1, jfloat v1,
    jfloat x2, jfloat y2, jfloat z2, jfloat u2, jfloat v2,
    jintArray pixelsArray, jfloatArray zBufferArray, jintArray textureArray,
    jint width, jint height, jint texWidth, jint texHeight
) {
    // Pin JVM arrays
    jint* pixels = (jint*)env->GetPrimitiveArrayCritical(pixelsArray, nullptr);
    jfloat* zBuffer = (jfloat*)env->GetPrimitiveArrayCritical(zBufferArray, nullptr);
    jint* texture = (jint*)env->GetPrimitiveArrayCritical(textureArray, nullptr);

    if (pixels && zBuffer && texture) {
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

            // Direct scanline rasterization loop
            for (int y = minY; y <= maxY; y++) {
                int rowOffset = y * width;
                float py = y + 0.5f;
                for (int x = minX; x <= maxX; x++) {
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

                            int texX = (int)(u * (texWidth - 1));
                            int texY = (int)(v * (texHeight - 1));
                            int texColor = texture[texY * texWidth + texX];

                            // Linear depth fog calculation (matching JavaRasterizer)
                            float fogNear = 750.0f;
                            float fogFar = 2100.0f;
                            int fogColor = 0x948D6B;

                            float fogFactor = (fogFar - depth) / (fogFar - fogNear);
                            if (fogFactor < 0.15f) fogFactor = 0.15f; // Max 85% fog at far distance
                            if (fogFactor > 1.0f) fogFactor = 1.0f;

                            if (fogFactor >= 1.0f) {
                                pixels[pixelIndex] = texColor;
                            } else {
                                int rT = (texColor >> 16) & 0xFF;
                                int gT = (texColor >> 8) & 0xFF;
                                int bT = texColor & 0xFF;

                                int rF = (fogColor >> 16) & 0xFF;
                                int gF = (fogColor >> 8) & 0xFF;
                                int bF = fogColor & 0xFF;

                                int rOut = (int)(rT * fogFactor + rF * (1.0f - fogFactor));
                                int gOut = (int)(gT * fogFactor + gF * (1.0f - fogFactor));
                                int bOut = (int)(bT * fogFactor + bF * (1.0f - fogFactor));

                                pixels[pixelIndex] = (rOut << 16) | (gOut << 8) | bOut;
                            }
                        }
                    }
                }
            }
        }
    }

    // Unpin JVM arrays
    if (texture) env->ReleasePrimitiveArrayCritical(textureArray, texture, JNI_ABORT);
    if (zBuffer) env->ReleasePrimitiveArrayCritical(zBufferArray, zBuffer, 0);
    if (pixels) env->ReleasePrimitiveArrayCritical(pixelsArray, pixels, 0);
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
