cmake_minimum_required(VERSION 3.22.1)
project(winehub_native)

add_library(winehub_native SHARED native_bridge.cpp)

find_library(log-lib log)
target_link_libraries(winehub_native ${log-lib})
