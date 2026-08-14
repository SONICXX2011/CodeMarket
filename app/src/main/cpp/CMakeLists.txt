cmake_minimum_required(VERSION 3.22.1)
project("codemarket_native")

add_library(codemarket_native SHARED codemarket_native.cpp)
find_library(log-lib log)
target_link_libraries(codemarket_native ${log-lib})