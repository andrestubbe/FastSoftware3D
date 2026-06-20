#include <jni.h>
#ifndef _Included_fastsoftware3d_frontend_terminal_Demo3DTerminal
#define _Included_fastsoftware3d_frontend_terminal_Demo3DTerminal
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT void JNICALL Java_fastsoftware3d_frontend_terminal_Demo3DTerminal_downsampleHalfBlocksNative
  (JNIEnv *, jclass, jintArray, jint, jint, jintArray, jintArray, jintArray, jint, jint, jint);
JNIEXPORT void JNICALL Java_fastsoftware3d_frontend_terminal_Demo3DTerminal_applyFxaaNative
  (JNIEnv *, jclass, jintArray, jintArray, jintArray, jintArray, jint, jint);
#ifdef __cplusplus
}
#endif
#endif