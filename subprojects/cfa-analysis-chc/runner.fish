#!/usr/bin/fish

for file in ../../../theta-benchmark/cfa/models/svcomp/loops/*.cfa
    echo $file
    timeout 3s java -Djava.library.path="../../lib/" -jar ./build/libs/theta-cfa-analysis-chc-0.0.1-SNAPSHOT-all.jar $file
    echo "STOP"
end