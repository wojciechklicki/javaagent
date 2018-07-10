#!/bin/sh
date=$(date "+%y%m%d%H%M%S")
echo $date
mvn -f ./dev-javaagent/pom.xml install
cd "./example"
javac "./com/ibm/domino/xsp/module/nsf/ModuleClassLoader.java"
javac -classpath . "Example.java"
java -javaagent:../dev-javaagent/target/dev-javaagent.jar Example
cd ..
cat XspProfilerOptionsFile.txt | sed "s/JAR_NAME/dev-javaagent-$date.jar/g" | sed "s/WORKSPACE/C:\/Users\/Dysant Workstation\/domino-workspace/g" > XspProfilerOptionsFile_gen.txt
cp XspProfilerOptionsFile_gen.txt ~/shares/apator_domino/XspProfilerOptionsFile.txt
cp ./dev-javaagent/target/dev-javaagent.jar ~/shares/apator_domino/xsp/dev-javaagent-$date.jar
