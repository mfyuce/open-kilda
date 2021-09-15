#!/bin/sh
#
# Copyright 2021 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

set -e

version="$1"
if [ -z "$version" ]; then
  echo "Usage " $(basename "$0") "<apache-storm-version>"
  exit 1
fi

location="apache-storm-$version"
target="$location.tar.gz.sha512"
wget -q -O- "https://archive.apache.org/dist/storm/$location/$target" \
  | awk 'BEGIN { data="" } { data = data $0 } END { gsub("[[:space:]]", "", data); sep=index(data, ":"); print(substr(data, sep + 1)) }'
