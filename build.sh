#!/bin/sh
cd ./javaagent
javac com/klicki/jagent/*.java
jar cvfm agent.jar manifest.txt ./com/klicki/jagent/*.class
cd ../example
javac Example.java
java -javaagent:../javaagent/agent.jar Example
cd ..
