# NetTableToSocket

This project contains a small java application that can be used
to pull data from the WPI Network Tables and write it to a socket.
A single configuration file can be used to define what network 
table, what socket(host/port/protocol), and the content of the 
message sent to the socket.

This project also contains a jar file called WpiNetTable.jar that
is a stripped down version of the WPI SmartDashboard.jar with 
everything removed except the Network Table related classes. This
jar file should only be used for testing when a WPI provided jar
file is not readily available.
