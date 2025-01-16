cmake_minimum_required(VERSION 3.5)
project(daut)

find_package(ament_cmake REQUIRED)

add_custom_target(
  sbt_publish_local ALL
  COMMAND 
    mkdir -p ${CMAKE_BINARY_DIR}/../../.coursier/cache &&
    ${CMAKE_COMMAND} -E env 
      XDG_RUNTIME_DIR=$ENV{HOME} 
      COURSIER_CACHE=${CMAKE_BINARY_DIR}/../../.coursier/cache 
      sbt
      --batch 
      --sbt-dir ${CMAKE_BINARY_DIR}/../../.sbt
      --sbt-boot ${CMAKE_BINARY_DIR}/../../.sbt/boot
      --sbt-cache ${CMAKE_BINARY_DIR}/../../.sbt/cache
      --ivy ${CMAKE_BINARY_DIR}/../../.ivy2
      -Dsbt.server.forcestart=true 
      publishLocal
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
  COMMENT "Build using SBT and publish to local machine"
)

ament_package()
