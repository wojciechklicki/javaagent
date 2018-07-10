#!/bin/sh
cd ./dev-javaagent
mvn install
cd "../example"
javac "./com/ibm/domino/xsp/module/nsf/ModuleClassLoader.java"
javac -classpath . "Example.java"
java -javaagent:../dev-javaagent/target/dev-javaagent.jar Example
cd ..
cp ./dev-javaagent/target/dev-javaagent.jar ~/shares/apator_domino/xsp/
cp ./dev-javaagent/target/dev-javaagent.jar ~/shares/domino/xsp/

