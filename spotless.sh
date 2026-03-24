#!/bin/bash

#
# Script that runs spotlessApply for a single file.
# In case no file path is provided, the formatting is applied to the whole project.
#
# Advantage over calling Gradle for a single file directly is identifying a subproject and only running the task there.
# Also, Gradle is only run for certain file extensions (in case this can't be filtered easily when calling the script)
#
# We can't avoid the Gradle configuration phase which takes relatively long due to the number of subprojects,
# But when targeting individual projects we can speed up the process using the on-demand configuration feature
# (see https://docs.gradle.org/current/userguide/multi_project_configuration_and_execution.html).
#
# Configuration as file watcher in IntelliJ:
#
# 1. Install File Watchers plugin
# 2. Settings -> Tools -> File Watchers -> Add
#
# File type:               Any
# Program:                 $ProjectFileDir$/spotless.sh
# Arguments:               $FilePath$
# Output paths to refresh: $FilePath$
# Working directory:       $ProjectFileDir$
#
# Disable
# - auto save on editing running the tool while editing
# - external changes sync to avoid running the tool when files are externally modified
#

set -e

# Detect macOS and set realpath command
if [[ "$(uname)" == "Darwin" ]]; then
  if ! command -v grealpath >/dev/null 2>&1; then
    echo "Error: 'grealpath' not found. Please install coreutils with:"
    echo "  brew install coreutils"
    exit 1
  fi
  realpath_cmd="grealpath"
else
  realpath_cmd="realpath"
fi

# Define the list of allowed file extensions
allowed_extensions=("java" "md" "groovy" "gradle" "kt" "scala" "sc")

# Check if a file path is provided
if [ -z "$1" ]; then
  echo "No file path provided"

  exec ./gradlew spotlessApply --parallel
  exit 0
fi

# Get the file extension
file_extension="${1##*.}"

# Check if the file extension is in the allowed list
if [[ ! "${allowed_extensions[@]}" =~ "${file_extension}" ]]; then
  echo "Error: The file extension .$file_extension is not allowed."
  exit 0
fi

# Get the absolute path of the provided file
file_path=$($realpath_cmd "$1")

# Get the directory containing the file
current_dir=$(dirname "$file_path")

# Get the directory where the script resides
script_dir=$(dirname "$($realpath_cmd "$0")")

# Traverse up the directory tree to find the first parent directory with build.gradle or build.gradle.kts
while [ "$current_dir" != "/" ]; do
  if [ -f "$current_dir/build.gradle" ] || [ -f "$current_dir/build.gradle.kts" ]; then
    # Determine the relative path from the script's directory
    relative_path=$($realpath_cmd --relative-to="$script_dir" "$current_dir")

    # Check if the folder is the same as the script directory
    if [ "$current_dir" == "$script_dir" ]; then
      echo "Found build.gradle(.kts) in the project root: $relative_path"

      exec ./gradlew :spotlessApply "-PspotlessIdeHook=$file_path" --parallel --configure-on-demand
    else
      # Replace / with : in the relative path
      formatted_path=$(echo "$relative_path" | tr '/' ':')
      echo "Found build.gradle(.kts) in a subfolder: $formatted_path"

      exec ./gradlew ":$formatted_path:spotlessApply" "-PspotlessIdeHook=$file_path" --parallel --configure-on-demand
    fi
    exit 0
  fi

  # Stop if we reach the script's directory
  if [ "$current_dir" == "$script_dir" ]; then
    break
  fi

  current_dir=$(dirname "$current_dir")
done

echo "No build.gradle or build.gradle.kts found within the script's directory."
exit 1
