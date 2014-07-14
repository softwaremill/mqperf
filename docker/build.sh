#!/bin/sh

function copy_if_changed {
    local path_from="$2/$1"
    local path_to="$3/$1"

    local md5_from=$(md5 -q $path_from)

    if [ -e $path_to ]
    then
        local md5_to=$(md5 -q $path_to)
    else
        local md5_to=""
    fi

    if [ "q$md5_from" = "q$md5_to" ];
    then
        echo "$1 not changed, not copying"
    else
        echo "$1 changed, copying"
        cp $path_from $path_to
    fi
}

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# build jars
cd $DIR/..
sbt assembly assemblyPackageDependency

cd $DIR

# Only copy jars if they are changed, so that the docker cache (which for ADD, looks at timestamps) works.
mkdir -p target
jars=( mqperf-assembly-1.0-deps.jar mqperf-assembly-1.0.jar )
for jar in ${jars[@]}
do
    copy_if_changed $jar ../target/scala-2.10 target
done

#
docker build -t "adamw/mqperf" .
