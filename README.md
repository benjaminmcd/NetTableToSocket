# NetTableToSocket

This project contains a small java application that can be used
to pull data from the WPI Network Tables and write it to a socket.

A single configuration file can be used to define what network
table, what socket(host/port/protocol), and the content of the
message sent to the socket. The message_config.xml file is an
example of this configuration file.

The NetTableToSocket.jar file contnians the NetTableToSocket
classes that are generated when compiling this project.

The WPINetTable.jar file contains the components that interface
with the WPI NetworkTables. This is a stripped down version of
the WPI SmartDashboard.jar with everything removed except the
Network Table related classes. This jar file should only be used
for testing when a WPI provided jar file is not readily available.

To use the application, modify a copy of the configuration file to
set your network interface and to define your message with items
from your Dashboard. Then start the application using your
SmartDashboard.jar file or the provided WPINetTable.jar file.

## Running Jar

If SmartDashboard.jar or WPINetTable.jar is in the same directory you can run:

`java -jar NetTableToSocket.jar message_config.xml`

otherwise you would need to recompile with the path to the jar:

`java -cp c:/wpi/tools/SmartDashboard.jar;NetTableToSocket.jar
      HoloFirst.NetTableToSocket message_config.xml`

or

`java -cp WPINetTable.jar;NetTableToSocket.jar
      HoloFirst.NetTableToSocket message_config.xml`

**Note:** *On non-window machines the path separator is ':'*

## Building Jar

Open project from file system in eclipse and the jar should automatically build and required
files to run will be placed in the "dist" directory.
