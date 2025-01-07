cmake_minimum_required(VERSION 3.5)
project(daut)

find_package(ament_cmake REQUIRED)

add_custom_target(
  sbt_publish_local ALL
  COMMAND 
    /usr/bin/cs setup 
      --cache ${CMAKE_INSTALL_PREFIX}/../.coursier/cache 
      --install-dir=${CMAKE_INSTALL_PREFIX}/../bin 
      --yes 
      --apps sbt &&
    ${CMAKE_COMMAND} -E env 
      XDG_RUNTIME_DIR=$ENV{HOME} 
      COURSIER_CACHE=${CMAKE_INSTALL_PREFIX}/../.coursier/cache 
    ${CMAKE_INSTALL_PREFIX}/../bin/sbt
      --batch 
      --sbt-dir ${CMAKE_INSTALL_PREFIX}/../.sbt
      --sbt-boot ${CMAKE_INSTALL_PREFIX}/../.sbt/boot
      --sbt-cache ${CMAKE_INSTALL_PREFIX}/../.sbt/cache
      --ivy ${CMAKE_INSTALL_PREFIX}/../.ivy2
      -Dsbt.server.forcestart=true 
      publishLocal
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
  COMMENT "Build using SBT and publish to local machine"
)

ament_package()
