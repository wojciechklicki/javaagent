#!/bin/sh
cd ./javaagent
javac com/klicki/jagent/*.java
jar cvfm timing_agent.jar manifest.txt ./com/klicki/jagent/*.class
cd ../example
javac Example.java
java -javaagent:../javaagent/timing_agent.jar Example
cd ..
cp ./javaagent/timing_agent.jar ~/shares/xsp
