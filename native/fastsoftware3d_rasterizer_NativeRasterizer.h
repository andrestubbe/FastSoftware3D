#include <jni.h>
#ifndef _Included_fastsoftware3d_rasterizer_NativeRasterizer
#define _Included_fastsoftware3d_rasterizer_NativeRasterizer
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT void JNICALL Java_fastsoftware3d_rasterizer_NativeRasterizer_drawTexturedTriangleNative
  (JNIEnv *, jobject, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat, jintArray, jfloatArray, jintArray, jintArray, jintArray, jintArray, jint, jint, jint, jint);
JNIEXPORT void JNICALL Java_fastsoftware3d_rasterizer_NativeRasterizer_drawTexturedTrianglesNative
  (JNIEnv *, jobject, jfloatArray, jint, jintArray, jfloatArray, jintArray, jintArray, jintArray, jintArray, jint, jint, jint, jint);
#ifdef __cplusplus
}
#endif
#endif