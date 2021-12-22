cmake_minimum_required (VERSION 3.1)

project (bkex)

add_library(bkexlib bkexlib.cpp)

add_executable(bkexbin bkexbin.cpp)
target_link_libraries(bkexbin bkexlib)

install(TARGETS bkexbin bkexlib
        ARCHIVE DESTINATION lib
        LIBRARY DESTINATION lib
        RUNTIME DESTINATION bin)
install(FILES bkexlib.hpp
        DESTINATION include)
