# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
project(wolvic)
set(CMAKE_CXX_STANDARD 17)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_subdirectory(src/main/cpp/vrb/src)

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/BrowserWorld.cpp
             src/main/cpp/Cylinder.cpp
             src/main/cpp/Controller.cpp
             src/main/cpp/ControllerContainer.cpp
             src/main/cpp/DeviceUtils.cpp
             src/main/cpp/ElbowModel.cpp
             src/main/cpp/FadeAnimation.cpp
             src/main/cpp/HandMeshRenderer.cpp
             src/main/cpp/Quad.cpp
             src/main/cpp/EngineSurfaceTexture.cpp
             src/main/cpp/ExternalBlitter.cpp
             src/main/cpp/ExternalVR.cpp
             src/main/cpp/GestureDelegate.cpp
             src/main/cpp/JNIUtil.cpp
             src/main/cpp/OneEuroFilter.cpp
             src/main/cpp/Pointer.cpp
             src/main/cpp/Skybox.cpp
             src/main/cpp/SplashAnimation.cpp
             src/main/cpp/TrackedKeyboardRenderer.cpp
             src/main/cpp/VRBrowser.cpp
             src/main/cpp/VRVideo.cpp
             src/main/cpp/VRLayer.cpp
             src/main/cpp/VRLayerNode.cpp
             src/main/cpp/Widget.cpp
             src/main/cpp/WidgetBorder.cpp
             src/main/cpp/WidgetMover.cpp
             src/main/cpp/WidgetPlacement.cpp
             src/main/cpp/WidgetResizer.cpp
           )

if(NOAPI)
target_sources(
    native-lib
    PUBLIC
    ${CMAKE_SOURCE_DIR}/src/noapi/cpp/native-lib.cpp
    ${CMAKE_SOURCE_DIR}/src/noapi/cpp/DeviceDelegateNoAPI.cpp
    )
elseif(VISIONGLASS)
target_sources(
        native-lib
        PUBLIC
        ${CMAKE_SOURCE_DIR}/src/visionglass/cpp/native-lib.cpp
        ${CMAKE_SOURCE_DIR}/src/visionglass/cpp/DeviceDelegateVisionGlass.cpp
)
elseif(HVR)
    target_sources(
            native-lib
            PUBLIC
            ${CMAKE_SOURCE_DIR}/src/hvr/cpp/native-lib.cpp
            ${CMAKE_SOURCE_DIR}/src/main/cpp/BrowserEGLContext.cpp
    )
elseif(AOSP)
target_sources(
        native-lib
        PUBLIC
        ${CMAKE_SOURCE_DIR}/src/main/cpp/native-lib.cpp
        ${CMAKE_SOURCE_DIR}/src/main/cpp/BrowserEGLContext.cpp
)
find_package(game-activity REQUIRED CONFIG)
target_link_libraries(native-lib PRIVATE game-activity::game-activity_static)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -u Java_com_google_androidgamesdk_GameActivity_initializeNativeCode")
else()
target_sources(
    native-lib
    PUBLIC
    ${CMAKE_SOURCE_DIR}/src/main/cpp/native-lib.cpp
    ${CMAKE_SOURCE_DIR}/src/main/cpp/BrowserEGLContext.cpp
    )

include(AndroidNdkModules)
android_ndk_import_module_native_app_glue()
target_link_libraries(native-lib PRIVATE native_app_glue)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -u ANativeActivity_onCreate")
endif()

if(OPENXR)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DXR_USE_PLATFORM_ANDROID -DXR_USE_GRAPHICS_API_OPENGL_ES")
    find_package(OpenXR REQUIRED CONFIG)
    target_link_libraries(native-lib PRIVATE OpenXR::headers)
    include_directories(${CMAKE_SOURCE_DIR}/../app/src/openxr/cpp)
    if (HVR)
        add_custom_command(TARGET native-lib POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy
                ${CMAKE_SOURCE_DIR}/../third_party/hvr/${ANDROID_ABI}/libxr_loader.so
                ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libxr_loader.so
                )
    elseif (SPACES)
        add_custom_command(TARGET native-lib POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy
                ${CMAKE_SOURCE_DIR}/../third_party/spaces/libopenxr_loader.so
                ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopenxr_loader.so
        )
    elseif (PICOXR)
        add_custom_command(TARGET native-lib POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy
            ${CMAKE_SOURCE_DIR}/../third_party/picoxr/libs/android.${ANDROID_ABI}/libopenxr_loader.so
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopenxr_loader.so
        )
    else ()
        target_link_libraries(native-lib PRIVATE OpenXR::openxr_loader)
    endif ()
    target_sources(
            native-lib
            PUBLIC
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/DeviceDelegateOpenXR.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRSwapChain.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRLayers.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRGestureManager.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRInput.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRInputSource.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRActionSet.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRExtensions.cpp
            ${CMAKE_SOURCE_DIR}/src/openxr/cpp/OpenXRPassthroughStrategy.cpp
    )
endif()

if(OCULUSVR)
include_directories(${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Include)
add_custom_command(TARGET native-lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        ${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Android/libs/${ANDROID_ABI}/libovrplatformloader.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libovrplatformloader.so
        )
endif()

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib
              log )

find_library( # Sets the name of the path variable.
              android-lib
              android )

add_library(ovrplatform-lib SHARED IMPORTED)
set_target_properties(ovrplatform-lib PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../third_party/OVRPlatformSDK/Android/libs/${ANDROID_ABI}/libovrplatformloader.so )

add_library(hvr-lib SHARED IMPORTED)
set_target_properties(hvr-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../third_party/hvr/${ANDROID_ABI}/libxr_loader.so)

add_library(picoxr-lib SHARED IMPORTED)
set_target_properties(picoxr-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../third_party/picoxr/libs/android.${ANDROID_ABI}/libopenxr_loader.so)

if (SPACES)
add_library(spaces-lib SHARED IMPORTED)
set_target_properties(spaces-lib PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../third_party/spaces/libopenxr_loader.so)
endif()

# Add dependency on tinygltf library, which is used to load hand models
# from .glb assets. Since it is only a single C++ source and header,
# we include them as native-lib's own build sources.
add_definitions(-DTINYGLTF_ANDROID_LOAD_FROM_ASSETS)
target_sources(native-lib PUBLIC
    ${CMAKE_SOURCE_DIR}/../app/src/main/cpp/tinygltf/tiny_gltf.cc)
include_directories(
    ${CMAKE_SOURCE_DIR}/../app/src/main/cpp/tinygltf)

# Add dependency on KTX-Software
add_subdirectory(${CMAKE_SOURCE_DIR}/src/main/cpp/KTX-Software)
target_link_libraries(native-lib PRIVATE ktx_read)
target_include_directories(native-lib PRIVATE ${CMAKE_SOURCE_DIR}/src/main/cpp/KTX-Software/include)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib PRIVATE
                       vrb

                       # Link VR_SDK_LIB exported from gradle flavors
                       ${VR_SDK_LIB}
                       ${VR_SDK_EXTRA_LIB}

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       ${android-lib}
                       EGL
                       GLESv3
                      )
